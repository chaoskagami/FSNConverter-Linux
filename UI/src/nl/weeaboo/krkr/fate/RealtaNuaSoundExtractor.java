package nl.weeaboo.krkr.fate;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;

import nl.weeaboo.io.FileUtil;
import nl.weeaboo.system.ProcessUtil;
import nl.weeaboo.vnds.Log;

public class RealtaNuaSoundExtractor {

	public RealtaNuaSoundExtractor() {
	}

	//Functions
	public static void main(String args[]) {
		RealtaNuaSoundExtractor se = new RealtaNuaSoundExtractor();
		try {
			se.extract(args[0], args[1]);
		} catch (FileNotFoundException fnfe) {
			System.err.println(fnfe);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

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

	public void extract(String discRoot, String outFolder) throws IOException {
		File outFolderFile = new File(outFolder);
		outFolderFile.mkdirs();

		File newExe = new File("bin/ahx2wav");
		FileUtil.copyFile(new File("bin/ahx2wav"), newExe);
		FileInputStream fin = new FileInputStream(discRoot+"/data0.bin");
		FileChannel fc = fin.getChannel();

		Log.v("Extracting AHX sound files...");

		try {
			byte sig[] = new byte[] {(byte)'A', (byte)'F', (byte)'S', (byte)'\0'};
			byte arcsig[] = new byte[4];
			fin.read(arcsig);
			for (int n = 0; n < 4; n++) {
				if (arcsig[n] != sig[n]) throw new IOException("FileFormat Error");
			}

			//0x808      -- file offset/size table offset
			//0x466C3000 -- filename table offset
			//0x00159660 -- filename table length
			//48         -- filename table entry length
			//29747      -- number of files

			int filesL = 29474;
			FileEntry files[] = new FileEntry[filesL];

			fc.position(0x808);
			for (int n = 0; n < filesL; n++) {
				files[n] = new FileEntry();
				files[n].offset = 0x800 + read_s32(fin);
				files[n].length = read_s32(fin);
			}

			byte nameBuffer[] = new byte[32];
			for (int n = 0; n < filesL; n++) {
				fc.position(0x466C3000 + 0x30 * n);
				fin.read(nameBuffer);
				int l = 0;
				while (l < nameBuffer.length && nameBuffer[l] != '\0') l++;
				files[n].filename = new String(nameBuffer, 0, l);
			}

			for (int n = 0; n < filesL; n++) {
				if (n % 256 == 0) {
					Log.v(String.format("(%d/%d) %s...", n+1, filesL, files[n].filename));
				}

				File outFile = new File(outFolder+'/'+files[n].filename);
				FileOutputStream fout = new FileOutputStream(outFile);
				int r = 0;
				while (r < files[n].length) {
					r += fc.transferTo(files[n].offset+r, files[n].length-r, fout.getChannel());
				}
				fout.flush();
				fout.close();

				//Convert to wav
				Process p = ProcessUtil.execInDir(String.format(
						"ahx2wav %s",
						files[n].filename),
						outFolder);
				ProcessUtil.waitFor(p);
				if (p.exitValue() != 0) {
					throw new IOException("Error converting file: " + outFile.getAbsolutePath() + "\nAborting sound extraction.");
				}
				ProcessUtil.kill(p);

				//Delete original
				outFile.delete();
			}

			//Rename a.b.wav to a.wav
			File converted[] = outFolderFile.listFiles();
			for (File f : converted) {
				String filename = f.getName();
				if (filename.endsWith(".wav")) {
					filename = filename.substring(0, filename.indexOf('.')+1) + "wav";
					f.renameTo(new File(outFolder+'/'+filename));
				}
			}

			Log.v("Done...");
		} finally {
			fc.close();
			fin.close();
			newExe.delete();
		}
	}

	//Getters

	//Setters

	//Inner Classes
	private static class FileEntry {
		public String filename;
		public int offset;
		public int length;
	}
}
