package nl.weeaboo.vnds;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import nl.weeaboo.common.Dim;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.vnds.tools.ImageConverter;
import nl.weeaboo.vnds.tools.ImageConverter.ConvertType;
import nl.weeaboo.vnds.tools.ImageConverter.DitheringType;
import nl.weeaboo.vnds.tools.ImageConverter.ScalingType;
import nl.weeaboo.vnds.tools.SoundConverter;

public abstract class AbstractResourceConverter {

	private boolean dither = true;
	private int pngQuant = 0; //1=median, 2=neuquant
	private int threads = Runtime.getRuntime().availableProcessors();
	private int jpgQuality = 98;
	private int mp3avgb = 48, mp3minb = 8, mp3maxb = 64;
	private int aacQuality = SoundConverter.AAC_Q_HIGH;
	private int vorbisQuality = SoundConverter.VORBIS_Q_MED;
	private int musicVolume = 100;
	private int sfxVolume = 100;
	
	protected boolean convertVoice = true;	
	protected Dim sourceImageSize = new Dim(800, 600);
	protected Dim targetImageSize = new Dim(256, 192);
	protected FileExts fileExts = new FileExts();
	
	public AbstractResourceConverter() {
	}
	
	//Functions
	protected void parseCommandLine(String[] args, int off) throws IOException {
		try {
			for (int n = off; n < args.length; n++) {
				if (args[n].startsWith("-mp3")) {
					mp3avgb = Integer.parseInt(args[++n]);
					mp3minb = Integer.parseInt(args[++n]);
					mp3maxb = Integer.parseInt(args[++n]);
				} else if (args[n].startsWith("-aac")) {
					aacQuality = Integer.parseInt(args[++n]);
				} else if (args[n].startsWith("-vorbis")) {
					vorbisQuality = Integer.parseInt(args[++n]);
				} else if (args[n].startsWith("-jpg")) {
					jpgQuality = Integer.parseInt(args[++n]);
				} else if (args[n].startsWith("-pngquant")) {
					pngQuant = args[++n].equalsIgnoreCase("median") ? 1 : 2;
				} else if (args[n].startsWith("-novoice")) {
					convertVoice = false;
				} else if (args[n].startsWith("-sfxvol")) {
					sfxVolume = Integer.parseInt(args[++n]);
				} else if (args[n].startsWith("-musicvol")) {
					musicVolume = Integer.parseInt(args[++n]);
				} else if (args[n].startsWith("-sourceSize")) {
					sourceImageSize = new Dim(Integer.parseInt(args[++n]), Integer.parseInt(args[++n]));
				} else if (args[n].startsWith("-targetSize")) {
					targetImageSize = new Dim(Integer.parseInt(args[++n]), Integer.parseInt(args[++n]));
				} else if (args[n].startsWith("-android")) {
					fileExts = FileExts.ANDROID;
					dither = false;
				} else if (args[n].startsWith("-threads")) {
					threads = Integer.parseInt(args[++n]);
				}
			}
		} catch (RuntimeException re) {
			throw new IOException(re);
		}		
	}
	
	protected static void printUsage(Class<?> clazz) {
		System.err.printf("Usage: java %s <template-folder> <game-folder> <dst-folder> <flags>\nflags:"
				+ "\n\t-musicvol <volume%%>"
				+ "\n\t-sfxvol <volume%%>"
				+ "\n\t-sourceSize <width> <height>"
				+ "\n\t-targetSize <width> <height>"
				+ "\n\t-novoice"
				+ "\n\t-android"
				+ "\n\t-threads <num>"
				
				+ "\n\t-pngquant <median|neuquant>"
				+ "\n\t-jpg <quality (0-100)>"
				+ "\n\t-mp3 <avgbitrate> <minbitrate> <maxbitrate>"
				+ "\n\t-aac <quality (default=70)>"
				+ "\n\t-vorbis <quality (default=2)>"
				+ "\n", clazz.getName());		
		
	}
	
	protected void initOutputFolder(File dstF) throws IOException {
		FileUtil.deleteFolder(new File(dstF, "foreground"));
		FileUtil.deleteFolder(new File(dstF, "background"));
		FileUtil.deleteFolder(new File(dstF, "sound"));
		FileUtil.deleteFolder(new File(dstF, "script"));
		dstF.mkdirs();
		
		FileUtil.write(new File(dstF, "img.ini"),
				String.format("width=%d\nheight=%d\n", targetImageSize.w, targetImageSize.h));

		fileExts.write(new File(dstF, "exts.ini"));		
	}
	
	protected BatchProcess createBatch() {
		BatchProcess bp = new BatchProcess();
		bp.setTaskSize(100);
		bp.setThreads(threads);
		bp.setThreadPriority(Thread.MIN_PRIORITY);
		bp.addProgressListener(new ProgressListener() {
			public void onFinished(String message) {
			}
			public void onProgress(int value, int max, String message) {
				Log.v(String.format("Processing (%d/%d) %s", value, max, message));
			}
		});
		
		return bp;
	}
	
	private ImageConverter createImageConverter() {
		ImageConverter ic = new ImageConverter();
		ic.setMaxThreads(threads);
		ic.setQuality(jpgQuality);
		ic.setSourceScreenSize(sourceImageSize.w, sourceImageSize.h);
		ic.setTargetScreenSize(targetImageSize.w, targetImageSize.h);
		return ic;
	}
	protected ImageConverter createBackgroundConverter() {
		ImageConverter ic = createImageConverter();
		if (dither) {
			ic.setDitheringType(ImageConverter.DitheringType.FLOYD_STEINBERG);
		} else {
			ic.setDitheringType(DitheringType.NONE);			
		}
		ic.setScalingType(ScalingType.BACKGROUND);
		ic.setMode(ImageConverter.ConvertType.TYPE_JPG);
		return ic;
	}
	protected ImageConverter createForegroundConverter() {
		ImageConverter ic = createImageConverter();
		ic.setDitheringType(DitheringType.NONE);
		ic.setScalingType(ScalingType.NONE); //ic.setScalingType(ScalingType.SPRITE);
		ic.setMode(ConvertType.TYPE_PNG);
		if (pngQuant == 1) {
			ic.setMode(ConvertType.TYPE_PNG_256_MEDIAN);
		} else if (pngQuant == 2) {
			ic.setMode(ConvertType.TYPE_PNG_256_NEUQUANT);
		}
		return ic;
	}
	
	private SoundConverter createSoundEncoder() {
		SoundConverter sc = new SoundConverter();
		sc.setMaxThreads(threads);
		sc.setAacQuality(aacQuality);
		sc.setMp3Quality(mp3minb, mp3maxb, mp3avgb);
		sc.setVorbisQuality(vorbisQuality);
		return sc;
	}
	protected SoundConverter createMusicEncoder() {
		SoundConverter sc = createSoundEncoder();
		sc.setVolume(musicVolume);
		sc.setMode(SoundConverter.ConvertType.fromExt(fileExts.music));
		return sc;
	}
	protected SoundConverter createSFXEncoder() {
		SoundConverter sc = createSoundEncoder();
		sc.setVolume(sfxVolume);
		sc.setMode(SoundConverter.ConvertType.fromExt(fileExts.sound));
		return sc;
	}
	protected SoundConverter createVoiceEncoder() {
		SoundConverter sc = createSoundEncoder();
		sc.setVolume(sfxVolume);
		sc.setMode(SoundConverter.ConvertType.fromExt(fileExts.voice));
		return sc;
	}
	
	protected void copyTemplate(File templateF, File dstF) throws IOException {
		FileUtil.copyFolderContents(templateF, dstF, false, null);
		
		Map<String, File> scripts = new HashMap<String, File>();
		FileUtil.collectFiles(scripts, new File(dstF, "script"), false);
		for (File f : scripts.values()) {
			fixScriptFileExts(f);
		}
		scaleSpecialImages(dstF);
	}
	
	protected void fixScriptFileExts(File file) throws IOException {
		String text = FileUtil.read(file);
		text = text.replaceAll("music ([^\\.\\s]+)\\.\\S+", "music $1." + fileExts.music);
		text = text.replaceAll("sound ([^\\.\\s]+)\\.\\S+", "sound $1." + fileExts.sound);
		FileUtil.write(file, text);
	}
	
	protected void scaleSpecialImages(File root) {
		ImageConverter ic = createForegroundConverter();
		
		Map<String, File> files = new HashMap<String, File>();
		FileUtil.collectFiles(files, new File(root, "background/special"), false, false, false);
		ic.setScalingType(ScalingType.STRETCH);
		for (File file : files.values()) {
			ic.setMode(file.getName().endsWith(".jpg") ? ConvertType.TYPE_JPG : ConvertType.TYPE_PNG);
			ic.convertFile(file);
		}
		files.clear();
		
		FileUtil.collectFiles(files, new File(root, "foreground/special"), false, false, false);
		ic.setScalingType(ScalingType.SPRITE);
		for (File file : files.values()) {
			ic.setMode(file.getName().endsWith(".jpg") ? ConvertType.TYPE_JPG : ConvertType.TYPE_PNG);
			ic.convertFile(file);
		}
		files.clear();
	}	
	
	//Getters
	
	//Setters
	
}
