package nl.weeaboo.krkr.fate;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileCollectFilter;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.system.ProcessUtil;
import nl.weeaboo.vnds.AbstractResourceConverter;
import nl.weeaboo.vnds.BatchProcess;
import nl.weeaboo.vnds.FileOp;
import nl.weeaboo.vnds.Log;
import nl.weeaboo.vnds.tools.ImageConverter;
import nl.weeaboo.vnds.tools.ImageConverter.ScalingType;
import nl.weeaboo.vnds.tools.SoundConverter;

public class FateResourceConverter extends AbstractResourceConverter {
	
	public FateResourceConverter() {
	}
	
	//Functions
	public static void main(String args[]) {
		System.setProperty("line.separator", "\n");
		
		FateResourceConverter e = new FateResourceConverter();
		try {
			e.parseCommandLine(args, 2);
		} catch (IOException ioe) {
			printUsage(e.getClass());
			return;
		}		
		
		try {
			e.extract(args[0], args[1]);
		} catch (IOException ioe) {
			Log.e("Fatal error during resource conversion", ioe);
		}
	}
	public void extract(String src, String dst) throws IOException {
		File dstF = new File(dst);
		File originalF = new File(dstF, "_original");
		File generatedF = new File(dstF, "_generated");

		//Clean up _original folder
		FileUtil.deleteFolder(originalF);
		originalF.mkdirs();

		//Extract game data
		FateExtractor.main(new String[] {src, originalF.getAbsolutePath()});
		
		//Clean up _generated folder
		initOutputFolder(generatedF);
		
		//Convert

		convertBackground(dstF);
		convertForeground(dstF);
		convertSound(dstF);
		convertMusic(dstF);
		
		//Template
		File templateF = new File("template/fate");
		copyTemplate(templateF, generatedF);
		
		//Done
		Log.v("Done");
	}
	
	public void convertBackground(final File root) {		
		Log.v("Converting backgrounds...");
				
		final ImageConverter ic = createBackgroundConverter();
		
		Map<String, File> files = new HashMap<String, File>();
		FileUtil.collectFiles(files, new File(root, "/_original/bgimage"), false, false, new FileCollectFilter() {
			public boolean accept(String relpath, File file) {
				if (file.isDirectory()) return true;
				return relpath.endsWith("tlg") || relpath.endsWith("bmp");
			}
		});
		
		BatchProcess bp = createBatch();
		try {
			bp.run(files, new FileOp() {
				@Override
				public void execute(String relpath, File file) throws IOException {
					if (relpath.endsWith(".tlg")) {
						file = convertTLG(file);
					}
					ic.convertFile(file, new File(root, "_generated/background"));
				}
			});
		} catch (InterruptedException ie) {
			Log.w("Batch Process Interrupted");
		}
	}
		
	public void convertForeground(final File root) {
		Log.v("Converting sprites...");
		
		final ImageConverter ic = createForegroundConverter();
		ic.setScalingType(ScalingType.SPRITE);
		
		Map<String, File> files = new HashMap<String, File>();
		FileUtil.collectFiles(files, new File(root, "/_original/fgimage"), false, false, new FileCollectFilter() {
			public boolean accept(String relpath, File file) {
				if (file.isDirectory()) return true;
				return relpath.endsWith("tlg") || relpath.endsWith("bmp");
			}
		});
		
		BatchProcess bp = createBatch();
		try {
			bp.run(files, new FileOp() {
				@Override
				public void execute(String relpath, File file) throws IOException {
					if (relpath.endsWith(".tlg")) {
						file = convertTLG(file);
					}
					ic.convertFile(file, new File(root, "_generated/foreground"));
				}
			});
		} catch (InterruptedException ie) {
			Log.w("Batch Process Interrupted");
		}
	}

	protected static File convertTLG(File tlgF) throws IOException {
		String hash = "__" + Long.toHexString(Thread.currentThread().getId()) + "__";
		
		File tempTLG = new File(tlgF.getParentFile(), hash + ".tlg");
		File tempBMP = new File(tlgF.getParentFile(), hash + ".bmp");
		
		File bmpF = new File(StringUtil.replaceExt(tlgF.getAbsolutePath(), "bmp"));
		bmpF.delete();
		
		tlgF.renameTo(tempTLG);
		
		try {
			Process p = ProcessUtil.execInDir(
					String.format("tlg2bmp \"%s\" \"%s\"",
					tempTLG.getAbsolutePath(), tempBMP.getAbsolutePath()),
					"tools/");
			ProcessUtil.waitFor(p);
		} finally {
			tempTLG.delete();
			tempBMP.renameTo(bmpF);			
		}
		
		return bmpF;
	}
	
	public void convertSound(File root) {
		final File targetFolder = new File(root, "_generated/sound");
		
		try {
			Log.v("Converting SFX...");
			final SoundConverter sc = createSFXEncoder();
			
			Map<String, File> files = new HashMap<String, File>();
			FileUtil.collectFiles(files, new File(root, "/_original/sound"), false, false, new FileCollectFilter() {
				public boolean accept(String relpath, File file) {
					if (file.isDirectory()) return true;
					return relpath.endsWith("wav") || relpath.endsWith("ogg");
				}
			});

			BatchProcess bp = createBatch();
			bp.run(files, new FileOp() {
				@Override
				public void execute(String relpath, File file) throws IOException {
					sc.convertFile(file, targetFolder);
				}
			});
		} catch (InterruptedException ie) {
			Log.w("Batch Process Interrupted");
		}
		
		if (convertVoice) {			
			Log.v("Converting Voice...");
			final SoundConverter sc = createVoiceEncoder();
			
			Map<String, File> files = new HashMap<String, File>();
			FileUtil.collectFiles(files, new File(root, "/_original/patch6"), false, false, new FileCollectFilter() {
				public boolean accept(String relpath, File file) {
					if (file.isDirectory()) return true;
					return relpath.endsWith("wav") || relpath.endsWith("ogg");
				}
			});

			BatchProcess bp = createBatch();
			bp.setTaskSize(250);
			try {
				bp.run(files, new FileOp() {
					@Override
					public void execute(String relpath, File file) throws IOException {
						sc.convertFile(file, targetFolder);
					}
				});
			} catch (InterruptedException ie) {
				Log.w("Batch Process Interrupted");
			}
		}
	}

	public void convertMusic(final File root) {
		final File targetFolder = new File(root, "_generated/sound");

		Log.v("Converting music...");
		final SoundConverter sc = createMusicEncoder();
		
		//Convert music		
		Map<String, File> files = new HashMap<String, File>();
		FileUtil.collectFiles(files, new File(root, "/_original/bgm"), false, false, new FileCollectFilter() {
			public boolean accept(String relpath, File file) {
				if (file.isDirectory()) return true;
				return relpath.endsWith("wav") || relpath.endsWith("ogg");
			}
		});

		BatchProcess bp = createBatch();
		bp.setTaskSize(5);
		try {
			bp.run(files, new FileOp() {
				@Override
				public void execute(String relpath, File file) throws IOException {
					sc.convertFile(file, targetFolder);
				}
			});
		} catch (InterruptedException ie) {
			Log.w("Batch Process Interrupted");
		}
	}

	//Getters
	
	//Setters
	
}
