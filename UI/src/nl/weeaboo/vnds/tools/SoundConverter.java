package nl.weeaboo.vnds.tools;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.string.StringUtil2;
import nl.weeaboo.system.ProcessUtil;
import nl.weeaboo.vnds.BatchProcess;
import nl.weeaboo.vnds.FileOp;
import nl.weeaboo.vnds.ProgressListener;

public class SoundConverter {

	public enum ConvertType {
		TYPE_AAC("AAC"), TYPE_ADPCM("ADPCM"), TYPE_MP3("MP3"), TYPE_OGG("OGG");
		
		private String label;
		
		private ConvertType(String label) {
			this.label = label;
		}
		
		public String toString() { return label; }
		
		public static ConvertType fromExt(String string) {
			string = string.toLowerCase();
			if (string.equals("aac")) {
				return TYPE_AAC;
			} else if (string.equals("adpcm")) {
				return TYPE_ADPCM;
			} else if (string.equals("mp3")) {
				return TYPE_MP3;
			} else if (string.equals("ogg")) {
				return TYPE_OGG;
			}
			
			throw new IllegalArgumentException("Unsupported conversion type: " + string);
		}
	}
	
	public static final int AAC_Q_LOW  = 35;
	public static final int AAC_Q_MED  = 50;
	public static final int AAC_Q_HIGH = 70;
	
	public static final int VORBIS_Q_LOW  = 0;
	public static final int VORBIS_Q_MED  = 2;
	public static final int VORBIS_Q_HIGH = 4;
	
	private String workingDir = "tools/";
	private int maxThreads = 8;
	private ConvertType mode = ConvertType.TYPE_AAC;
	private int aac_quality = AAC_Q_HIGH;
	private int mp3_minb = 8, mp3_maxb = 128, mp3_avgb = 96;
	private int vorbis_quality = VORBIS_Q_MED;
	private int volume = 100;		
	private boolean nameToUpperCase;
	private boolean log;
	
	public SoundConverter() {
	}
	
	// Functions
	public static void main(String args[]) throws IOException {
		SoundConverter ve = new SoundConverter();
		
		//Fate/Stay Night
		//ve.convertFolder("foldername/");
		
		//Narcissu
		//ve.setVolume(800);
		//ve.setConvertNameToUpperCase(true);
		//ve.convertFolder("foldername/", null);
		
		ve.setMode(ConvertType.TYPE_OGG);
		ve.convertFile(new File("z:/temp.mp3"));
	}
	
	public void convertFolder(String folder, final ProgressListener pl) {
		Map<String, File> files = new HashMap<String, File>();
		for (File file : new File(folder).listFiles()) {
			if (!file.isDirectory()) files.put(file.getName(), file);
		}
							
		BatchProcess bp = new BatchProcess();
		bp.setTaskSize(32);
		bp.setThreads(maxThreads);
		bp.setThreadPriority(Thread.MIN_PRIORITY);
		bp.addProgressListener(pl);
		try {
			bp.run(files, new FileOp() {
				public void execute(String relpath, File file) throws IOException {
					convertFile(file);
				}
			});
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	protected static void collectFiles(Set<File> files, File file, int depth) {
		if (file.isDirectory()) {
			if (depth == 0 && !file.getName().equals("special")) {
				for (File f : file.listFiles()) {
					collectFiles(files, f, depth + 1);
				}
			}
		} else if (!file.getName().endsWith(".aac")) {
			files.add(file);
		}
	}
	
	protected static File[] createTempFiles(File file, File tempFolder, String... exts)
		throws IOException
	{
		File[] result = new File[exts.length + 1];
		
		String path = file.getAbsolutePath();

		//Check if entire path consists of ASCII characters
		boolean ascii = true;
		for (int n = 0; n < path.length(); n++) {
			if (path.charAt(n) > 127) {
				ascii = false;
				break;
			}
		}
		
		if (ascii) {
			result[0] = file;
		} else {		
			File dst = null;
			String pre = String.format("temp-" + Thread.currentThread().hashCode());
			String post = StringUtil.getExtension(file.getName()).toLowerCase();
			while (dst == null || dst.exists()) {
				dst = new File(tempFolder, pre + "-" + StringUtil2.getRandomString(4) + "." + post);
			}
			FileUtil.copyFile(file, dst);
			result[0] = dst;
		}
		
		for (int n = 0; n < exts.length; n++) {
			String p = StringUtil.replaceExt(result[n].getAbsolutePath(), exts[n]);
			File f = new File(p);

			int t = 1;
			while (f.exists()) {
				t++;
				f = new File(StringUtil.stripExtension(p) + "-" + t + "." + StringUtil.getExtension(p));				
			}
			result[n+1] = f;
		}
		
		return result;
	}
	
	public void convertFile(File file) throws IOException {
		convertFile(file, mode, null);
	}
	public File convertFile(File srcF, File dstFolder) throws IOException {
		return convertFile(srcF, mode, dstFolder);
	}
	public File convertFile(File srcF, ConvertType mode, File dstFolder) throws IOException {
		if (dstFolder == null) {
			dstFolder = srcF.getParentFile();
		}			
		dstFolder.mkdirs();

		String filters = "";
		if (volume != 100) {
			filters += " -vol " + volume;
		}		
		
		File[] temp;
		String[] cmds;
		switch (mode) {
		case TYPE_AAC:
			temp = createTempFiles(srcF, dstFolder, "wav", "aac");
			
			cmds = new String[] {
				String.format("ffmpeg -y -i \"%s\" -vn -ac 1 -ar 22050 %s \"%s\"",
					temp[0], filters, temp[1]),
				String.format("faac -q %d -o \"%s\" \"%s\"",
					aac_quality, temp[2], temp[1])
			};
			break;
		case TYPE_MP3:
			temp = createTempFiles(srcF, dstFolder, "wav", "mp3");
			
			String bitrateFlags = String.format("--abr %d -b %d -B %d",
					mp3_avgb, mp3_minb, mp3_maxb);
			
			cmds = new String[] {
				String.format("ffmpeg -y -i \"%s\" -vn -ac 2 -ar 32000 %s \"%s\"",
					temp[0], filters, temp[1]),
				String.format("lame --resample 32 %s \"%s\" \"%s\"",
					bitrateFlags, temp[1], temp[2])
			};
			break;
		case TYPE_ADPCM:
			temp = createTempFiles(srcF, dstFolder, "wav", "wav.raw");

			cmds = new String[] {
				String.format("ffmpeg -y -i \"%s\" -vn -acodec adpcm_ima_wav -ac 1 -ar 22050 %s \"%s\"",
					temp[0], filters, temp[1]),
				String.format("ima2raw \"%s\"",
					temp[1])
			};
			break;
		case TYPE_OGG:
			temp = createTempFiles(srcF, dstFolder, "ogg");
			
			cmds = new String[] {
				String.format("ffmpeg -y -i \"%s\" -vn -acodec libvorbis -aq %d %s \"%s\"",
					temp[0], vorbis_quality, filters, temp[1])
			};
			break;
		default:
			throw new IllegalArgumentException("Illegal mode: " + mode);
		}
		
		File dstF = new File(dstFolder, StringUtil.replaceExt(srcF.getName(),
				StringUtil.getExtension(temp[temp.length-1].getName()).toLowerCase()));
		try {
			for (String cmd : cmds) {
				Process p = ProcessUtil.execInDir(cmd, workingDir);
				//System.out.println(ProcessUtil.read(p));
				ProcessUtil.waitFor(p);
			}			
		} finally {
			//Rename final result to dst
			if (temp[temp.length-1].exists()) {
				temp[temp.length-1].renameTo(dstF);
			}
			
			//Delete temp files
			for (File file : temp) {
				if (!file.equals(srcF) && !file.equals(dstF)) {
					file.delete();
				}
			}
		}
		
		return dstF;
	}
	
	// Getters
	public boolean isLogging() { return log; }
	public ConvertType getMode() { return mode; }
	public int getAacQuality() { return aac_quality; }
	public int getVorbisQuality() { return vorbis_quality; }
	public int getMp3MinBitrate() { return mp3_minb; }
	public int getMp3MaxBitrate() { return mp3_maxb; }
	public int getMp3AvgBitrate() { return mp3_avgb; }
	public int getVolume() { return volume; }
	public boolean getConvertNameToUpperCase() { return nameToUpperCase; }
	public int getMaxThreads() { return maxThreads; }
	public String getWorkingDir() { return workingDir; }
	
	// Setters
	public void setMode(ConvertType mode) { this.mode = mode; }
	public void setVorbisQuality(int quality) { this.vorbis_quality = quality; }
	public void setAacQuality(int quality) { this.aac_quality = quality; }
	public void setMp3Quality(int min, int max, int avg) {
		mp3_minb = min;
		mp3_maxb = max;
		mp3_avgb = avg;
	}
	public void setVolume(int volume) { this.volume = volume; }
	public void setConvertNameToUpperCase(boolean nameToUpperCase) { this.nameToUpperCase = nameToUpperCase; }
	public void setMaxThreads(int mt) { this.maxThreads = mt; }
	public void setWorkingDir(String dir) { this.workingDir = dir; }
	
}
