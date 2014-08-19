package nl.weeaboo.vnds;

import static nl.weeaboo.common.StringUtil.stripExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import nl.weeaboo.io.FileUtil;
import nl.weeaboo.system.ProcessUtil;

public final class VNSoundUtil {


	public static String ffmpeg = "ffmpeg";

	private VNSoundUtil() {		
	}
		
	public static File padAudioFile(File src, File dst) throws IOException {
		File tempF = new File(dst.getParent(), stripExtension(dst.getName())+".temp");
		tempF.getParentFile().mkdirs();
		
		try {
			//Decode to raw PCM

			Process p = null;

			redo_transcode: try {
				p = ProcessUtil.execInDir(String.format(
					"%s -y -i \"%s\" -f s16le -acodec pcm_s16le -ac 2 -ar 44100 \"%s\"",
					ffmpeg, src.getAbsolutePath(), tempF.getAbsolutePath()),
					".");
			}
			catch (Exception e) {
				ffmpeg = "avconv";
				break redo_transcode;
			}
			ProcessUtil.waitFor(p);
			ProcessUtil.kill(p);
			
			{
				byte[] bytes = FileUtil.readBytes(tempF);
				boolean isShort = bytes.length <= 10 * (44100*2*2);
				float fadeSeconds = (isShort ? 1 : 3);
				float padSeconds = (isShort ? 3 : 0);
				
				ShortBuffer[] samples = deinterleave(bytes, 2);
				fadeInOut(samples, Math.round(fadeSeconds * 44100),
						Math.round(fadeSeconds * 44100));
				bytes = interleave(samples);
				
				//Pad raw PCM with data				
				FileOutputStream fout = new FileOutputStream(tempF);
				try {
					fout.write(bytes);
					fout.write(new byte[Math.round(padSeconds * 44100 * 2 * 2)]);
					fout.flush();
				} finally {
					fout.close();
				}
			}
			
			//Encode to target format
			p = ProcessUtil.execInDir(String.format(
					"ffmpeg -y -f s16le -acodec pcm_s16le -ac 2 -ar 44100 -i \"%s\" \"%s\"",
					tempF.getAbsolutePath(), dst.getAbsolutePath()),
					"tools");
			ProcessUtil.waitFor(p);
			ProcessUtil.kill(p);
		} finally {
			tempF.delete();
		}
		
		return dst;
	}
	
	public static void fadeInOut(ShortBuffer[] channels, int inDuration, int outDuration) {
		for (ShortBuffer ch : channels) {
			inDuration = Math.min(ch.limit()/2, inDuration);
		}

		for (ShortBuffer ch : channels) {
			outDuration = Math.min(ch.limit()-inDuration, outDuration);
		}

		for (ShortBuffer ch : channels) {
			int a = 0;
			for (int n = 0; n < inDuration; n++) {
				float frac = n / (float)inDuration;
				ch.put(a, (short)Math.round(frac * ch.get(a)));
				a++;
			}

			int b = ch.limit()-1;
			for (int n = 0; n < outDuration; n++) {
				float frac = n / (float)outDuration;
				ch.put(b, (short)Math.round(frac * ch.get(b)));
				b--;
			}
		}
	}
	
	protected static ShortBuffer[] deinterleave(byte[] srcArray, int channels) {
		ByteBuffer src = ByteBuffer.wrap(srcArray);
		src.order(ByteOrder.LITTLE_ENDIAN);
		ShortBuffer ssrc = src.asShortBuffer();
		
		ShortBuffer[] dst = new ShortBuffer[channels];
		for (int n = 0; n < channels; n++) {
			dst[n] = ShortBuffer.allocate(ssrc.limit() / channels);
		}
		
		while (ssrc.remaining() > 0) {
			for (ShortBuffer buf : dst) {
				buf.put(ssrc.get());
			}
		}

		for (ShortBuffer buf : dst) {
			buf.rewind();
		}
		
		return dst;
	}

	protected static byte[] interleave(ShortBuffer[] src) {
		int len = 0;
		int[] oldpos = new int[src.length];
		for (int n = 0; n < oldpos.length; n++) {
			oldpos[n] = src[n].position();
			len += src[n].limit();
		}
		
		ByteBuffer dst = ByteBuffer.allocate(2 * len);
		dst.order(ByteOrder.LITTLE_ENDIAN);
		ShortBuffer sdst = dst.asShortBuffer();
		
		try {
			while (sdst.remaining() > 0) {
				for (ShortBuffer buf : src) {
					sdst.put(buf.get());
				}
			}
		} finally {
			for (int n = 0; n < oldpos.length; n++) {
				src[n].position(oldpos[n]);
			}
		}
		dst.rewind();
		
		return dst.array();
	}

}
