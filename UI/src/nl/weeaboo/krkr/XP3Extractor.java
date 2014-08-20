package nl.weeaboo.krkr;

import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.vnds.Log;
import nl.weeaboo.vnds.ProgressListener;

public class XP3Extractor {

	//Temporary buffers for unzip
    private static final byte infBuffer[] = new byte[64 * 1024];
    private static final byte readBuffer[] = new byte[64 * 1024];

	public XP3Extractor() {
	}

	//Functions
	public static final int read_s32(InputStream in) throws IOException {
		return (int)readLE(in, 4);
	}
	public static final long read_s64(InputStream in) throws IOException {
		return readLE(in, 8);
	}
	public static final long readLE(InputStream in, int bytes) throws IOException {
		int result = 0;
		for (int n = 0; n < bytes; n++) {
			result += (in.read() << (8 * n));
		}
		return result;
	}

	/**
	 * Warning: dst should use ascii-only in its pathname
	 */
	public void extract(String archive, String dst, ProgressListener pl) throws IOException {
		Log.v("Extracting " + archive);

		FileInputStream fin = new FileInputStream(archive);
		FileChannel fc = fin.getChannel();

		int origSize;
		File uncompressedFile = new File(dst+"/__temp__.dat");
		uncompressedFile.getParentFile().mkdirs();

		{
			byte signature[] = new byte[] {(byte)'X', (byte)'P', (byte)'3',
					(byte)0x0D, (byte)0x0A, (byte) ' ', (byte)0x0A,
					(byte)0x1A, (byte)0x8B, (byte)0x67, (byte)0x01 };
			byte tempsig[] = new byte[signature.length];
			fin.read(tempsig);
			for (int n = 0; n < tempsig.length; n++) {
				if (signature[n] != tempsig[n]) {
					throw new IOException("FileFormat error");
				}
			}

			int indexOffset = (int)read_s64(fin);
			if (indexOffset > fc.size()) throw new IOException("FileFormat error");
			fc.position(indexOffset);

			boolean compression = readLE(fin, 1) != 0;
			if (compression) {
				int compSize = (int)read_s64(fin);
				origSize = (int)read_s64(fin);

				if (indexOffset+compSize+17 != fc.size()) throw new IOException("FileFormat error");

				int uncompressedL = -1;
				BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(uncompressedFile));
				try {
					uncompressedL = unzip(fin, bout, compSize);
				} finally {
					bout.close();
				}

				if (uncompressedL != origSize) throw new IOException("FileFormat error");
			} else {
				origSize = (int)read_s64(fin);
				BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(uncompressedFile));
				int read = 0;
				byte buffer[] = new byte[256*1024];
				while (read < origSize) {
					int r = fin.read(buffer, 0, buffer.length);
					if (r < 0) break;
					bout.write(buffer, 0, r);
				}
				bout.close();
			}
		}

		FileInputStream uncompressedIn = new FileInputStream(uncompressedFile);
		FileChannel uncompressedC = uncompressedIn.getChannel();

		byte out[] = new byte[1024 * 1024];
		int outL = 0;

		int t = 0;
		int read = 0;
		while (uncompressedC.position() < origSize) {
			Entry entry = readEntry(uncompressedIn);

			File outFile = new File(String.format("%s/%s", dst, entry.file));
			outFile.getParentFile().mkdirs();

			outL = 0;
			t++;

			if (pl != null && (t & 0xFF) == 0) {
				pl.onProgress(read, (int)fc.size(), "");
			}

			//Log.verbose("[write] " + outFile.getAbsolutePath());
			//Benchmark.tick();

			//Write segments to seperate files
			int totalSize = 0;
			for (Segment segment : entry.segments) {
				totalSize += segment.origSize;
			}
			if (out.length < totalSize) {
				out = new byte[totalSize];
			}

			for (Segment segment : entry.segments) {
				fc.position(segment.offset);

				if (segment.compressed) {
					outL += unzip(fin, out, segment.compSize);
				} else {
					outL += fin.read(out, outL, segment.compSize);
				}

				read += segment.compSize;
			}

			//Decrypt
			if (entry.encrypted) {
				decrypt(outFile.getName(), out, outL);
			}

			try {
				if (outFile.getName().endsWith(".tlg")) {
					if (out.length >= 2 && out[0] == 'B' && out[1] == 'M') {
						//Bitmap with TLG extension, lolwut
						File bmpF = new File(StringUtil.stripExtension(outFile.getAbsolutePath())+".bmp");
						FileUtil.writeBytes(bmpF, out, 0, outL);
					} else {
						/*
						String tlgTemp = dst+"/__temp__.tlg";
						String bmpTemp = dst+"/__temp__.bmp";
						FileUtil.writeBytes(new File(tlgTemp), out, 0, outL);
						Process p = ProcessUtil.execInDir(
								String.format("tlg2bmp \"%s\" \"%s\"",
								tlgTemp, bmpTemp),
								"");
						ProcessUtil.waitFor(p);
						ProcessUtil.kill(p);

						outFile.delete();
						new File(tlgTemp).delete();
						new File(bmpTemp).renameTo(new File(StringUtil.stripExtension(outFile.getAbsolutePath())+".bmp"));
						*/

						FileUtil.writeBytes(outFile, out, 0, outL);
					}
				} else {
					FileUtil.writeBytes(outFile, out, 0, outL);
				}
			} catch (IOException ioe) {
				if (outFile.getName().length() <= 128) {
					//Don't warn about long (garbage?) filenames that may be used as padding
					Log.w(ioe.toString());
				}
			}

			//Benchmark.tock(outFile.getName() + " %s");
		}

		uncompressedC.close();
		uncompressedIn.close();

		fc.close();
		fin.close();

		uncompressedFile.delete();

		if (pl != null) pl.onFinished(archive + " fully extracted");
	}

	static synchronized int unzip(InputStream in, byte out[], int inL) throws IOException {
		Inflater inf = new Inflater();

		int read = 0;
	    int inflated = 0;
	    try {
			while (true) {
				int i = inf.inflate(out, inflated, out.length-inflated);
				if (i > 0) {
					inflated += i;
				} else if (inf.finished() || inf.needsDictionary()) {
					return inflated;
				} else {
					int readLeft = readBuffer.length;
					if (inL >= 0 && inL-read < readLeft) {
						readLeft = inL-read;
					}
					int r = in.read(readBuffer, 0, readLeft);
					if (r == -1) {
					    throw new EOFException("Unexpected end of ZLIB input stream");
					}
					read += r;
					inf.setInput(readBuffer, 0, r);
				}
			}
		} catch (DataFormatException e) {
			throw new IOException(e);
		}
	}

	static synchronized int unzip(InputStream in, OutputStream out, int inL) throws IOException {
		Inflater inf = new Inflater();

		int read = 0;
	    int inflated = 0;
	    try {
			while (true) {
				int i = inf.inflate(infBuffer, 0, infBuffer.length);
				if (i > 0) {
					inflated += i;
					out.write(infBuffer, 0, i);
				} else if (inf.finished() || inf.needsDictionary()) {
					return inflated;
				} else {
					int readLeft = readBuffer.length;
					if (inL >= 0 && inL-read < readLeft) {
						readLeft = inL-read;
					}
					int r = in.read(readBuffer, 0, readLeft);
					if (r == -1) {
					    throw new EOFException("Unexpected end of ZIP input stream");
					}
					read += r;
					inf.setInput(readBuffer, 0, r);
				}
			}
		} catch (DataFormatException e) {
			throw new IOException(e);
		}
	}

	@SuppressWarnings("unused")
	protected Entry readEntry(InputStream in) throws IOException {
		Entry entry = new Entry();

		byte temp[] = new byte[4];

		in.read(temp);
		if (!new String(temp).equals("File")) throw new IOException("FileFormat error :: " + new String(temp));
		int entryLength = (int)read_s64(in);

		in.read(temp);
		if (!new String(temp).equals("info")) throw new IOException("FileFormat error");
		int infoLength = (int)read_s64(in);

		entry.encrypted = read_s32(in) != 0;
		int origSize = (int)read_s64(in);
		int compSize = (int)read_s64(in);

		int filenameL = (int)readLE(in, 2);
		//System.err.println(origSize + " " + compSize + " " + new String(temp) + " " + filenameL);
		if (infoLength != filenameL*2+22) throw new IOException("FileFormat error");

		char filename[] = new char[filenameL];
		for (int n = 0; n < filenameL; n++) {
			filename[n] = (char)readLE(in, 2);
		}
		entry.file = new String(filename);

		in.read(temp);
		if (!new String(temp).equals("segm")) throw new IOException("FileFormat error");
		int numSegments = ((int)read_s64(in)) / 28;
		entry.segments = new Segment[numSegments];
		for (int n = 0; n < numSegments; n++) {
			Segment s = new Segment();
			s.compressed = read_s32(in) != 0;
			s.offset = (int)read_s64(in);
			s.origSize = (int)read_s64(in);
			s.compSize = (int)read_s64(in);

			entry.segments[n] = s;
		}

		in.read(temp);
		//System.err.println(new String(temp));
		if (read_s64(in) != 4) throw new IOException("FileFormat error");
		int adler = read_s32(in);

		return entry;
	}

	public void decrypt(String filename, byte data[], int dataL) {
		if (dataL > 0x13) {
			data[0x13] ^= 1;
		}
		if (dataL > 0x2ea29) {
			data[0x2ea29] ^= 3;
		}

		for (int n = 0; n < dataL; n++) {
			data[n] ^= 0x36;
		}
	}

	//Getters

	//Setters

	//Inner Classes
	private static class Entry {
		public String file;
		public boolean encrypted;
		public Segment segments[];
	}
	private static class Segment {
		public boolean compressed;
		public int offset;
		public int origSize;
		public int compSize;
	}

}
