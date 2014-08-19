package nl.weeaboo.vnds;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileCollectFilter;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.zip.ZipUtil;
import nl.weeaboo.zip.ZipUtil.Compression;

public abstract class AbstractPacker {

	protected final File srcF;
	protected final File dstF;
	protected ResourcesUsed resUsed;
	
	public AbstractPacker(File srcF, File dstF) {
		this.srcF = srcF;
		this.dstF = dstF;
				
		resUsed = new ResourcesUsed();
		resUsed.load(srcF, false);
	}
	
	//Functions
	
	public void pack() {
		Log.v("Packing " + dstF.getName() + "...");
		
		FileUtil.deleteFolder(dstF);
		dstF.mkdirs();
		
		Map<String, File> files = new TreeMap<String, File>();
		
		//Foreground
		Log.v("Sprites...");
		final File srcForegroundF = new File(srcF, "foreground");
		final File dstForegroundF = new File(dstF, "foreground");
		dstForegroundF.mkdirs();
		FileUtil.collectFiles(files, srcForegroundF, false, false, new FileCollectFilter() {
			public boolean accept(String relpath, File f) {
				return f.isDirectory() || relpath.startsWith("special") || resUsed.isForegroundUsed(relpath);
			}
		});
		resourceZip(new File(dstF, "foreground.zip"), files);
		files.clear();
		
		//Background
		Log.v("Backgrounds...");
		final File srcBackgroundF = new File(srcF, "background");
		final File dstBackgroundF = new File(dstF, "background");
		dstBackgroundF.mkdirs();
		FileUtil.collectFiles(files, srcBackgroundF, false, false, new FileCollectFilter() {
			public boolean accept(String relpath, File f) {
				return f.isDirectory() || relpath.startsWith("special") || resUsed.isBackgroundUsed(relpath);
			}
		});
		resourceZip(new File(dstF, "background.zip"), files);
		files.clear();

		//Script
		Log.v("Scripts...");
		final File srcScriptF = new File(srcF, "script");
		final File dstScriptF = new File(dstF, "script");
		dstScriptF.mkdirs();
		FileUtil.collectFiles(files, srcScriptF, false, false, new FileCollectFilter() {
			public boolean accept(String relpath, File file) {
				return file.isDirectory() || relpath.endsWith(".scr");
			}
		});
		for (Entry<String, File> entry : files.entrySet()) {
			String relpath = entry.getKey();
			File a = entry.getValue();
			File b = new File(dstScriptF, relpath);
			b.getParentFile().mkdirs();
			try {
				optimizeScript(relpath, a, b);
			} catch (IOException e) {
				Log.w("Error copying file: " + a + " to " + b);
			}
		}		
		files.clear();
		
		FileUtil.collectFiles(files, dstScriptF, false);
		resourceZip(new File(dstF, "script.zip"), files);
		for (File file : files.values()) {
			file.delete();
		}
		files.clear();
		
		//Sound
		Log.v("Music...");
		final File srcSoundF = new File(srcF, "sound");
		final File dstSoundF = new File(dstF, "sound");
		dstSoundF.mkdirs();
		FileUtil.collectFiles(files, srcSoundF, false, false, new FileCollectFilter() {
			public boolean accept(String relpath, File f) {
				if (f.isDirectory()) return true;
				
				if (relpath.endsWith(".mp3")) {
					//Big fat hack: sneakily copy any music files we encounter, but don't
					//include them in the ZIP
					tryCopy(srcSoundF, relpath, dstSoundF);
					return false;
				}
				
				return relpath.startsWith("special/")
						|| resUsed.isSoundUsed(relpath)
						|| resUsed.isMusicUsed(relpath);
			}
		});

		Log.v("Sound...");
		resourceZip(new File(dstF, "sound.zip"), files);
		files.clear();
				
		//Other
		tryCopy(srcF, "img.ini", dstF);
		tryCopy(srcF, "info.txt", dstF);
		tryCopy(srcF, "default.ttf", dstF);
		
		Map<String, File> icons = new HashMap<String, File>();
		FileUtil.collectFiles(icons, srcF, false, false, new FileCollectFilter() {
			@Override
			public boolean accept(String relpath, File file) {
				String fext = StringUtil.getExtension(relpath);
				return fext.equalsIgnoreCase("jpg") || fext.equalsIgnoreCase("png");
			}
		});
		for (Entry<String, File> entry : icons.entrySet()) {
			tryCopy(srcF, entry.getKey(), dstF);
		}
		
		try {
			packMore();
		} catch (IOException e) {
			Log.e("Error in packMore()", e);
		}
		
		Log.v("Done");
	}
	
	protected void packMore() throws IOException {		
	}
	
	protected void resourceZip(File dst, Map<String, File> files) {
		String prefix = StringUtil.stripExtension(dst.getName()) + '/';
		
		Map<String, File> newFiles = new HashMap<String, File>();
		for (Entry<String, File> entry : files.entrySet()) {
			newFiles.put(prefix+entry.getKey(), entry.getValue());
		}
		
		if (!newFiles.isEmpty()) {
			try {
				ZipUtil.zip(dst, newFiles, Compression.NONE);
			} catch (IOException ioe) {
				Log.w("Error zipping file: " + dst, ioe);
			}
		}
	}
	
	protected void optimizeScript(String relpath, File src, File dst) throws IOException {
		if (relpath.equals("main.scr") || relpath.startsWith("special/")) {
			FileUtil.copyFile(src, dst);
		} else {
			StringBuilder sb = new StringBuilder((int)src.length());
			
			BufferedReader in = new BufferedReader(new InputStreamReader(
					new FileInputStream(src), "UTF-8"));
			try {
				String line;
				while ((line = in.readLine()) != null) {
					if (line.startsWith("#") || line.trim().isEmpty()) {
						continue;
					}
					
					if (line.startsWith("text ")) {
						line = optimizeTextLine(line);
						if (line != null && line.length() > 0) {
							sb.append(line);
							if (!line.endsWith("\n")) {
								sb.append('\n');
							}
						}
					} else {
						sb.append(line);
						sb.append('\n');
					}
				}
			} finally {
				in.close();
			}
			
			FileUtil.write(dst, sb.toString());
		}
	}
	
	protected String optimizeTextLine(String line) {
		return line.replaceAll("\\s+", " ");
	}

	protected static void deleteFiles(Map<String, File> files) {
		for (File file : files.values()) {
			if (!file.isDirectory()) {
				file.delete();
			}
		}
	}
	
	protected static boolean tryCopy(File srcFolder, String relpath, File dstFolder) {
		File srcF = new File(srcFolder, relpath);
		if (!srcF.exists()) return false;
		
		File dstF = new File(dstFolder, relpath);
		dstF.getParentFile().mkdirs();
		try {
			FileUtil.copyFile(srcF, dstF);
			return true;
		} catch (FileNotFoundException e) {
			//Ignore
			//Log.w("Error copying file: " + srcF + " to " + dstF);
		} catch (IOException e) {
			Log.w("Error copying file: " + srcF + " to " + dstF);
		}
		return false;
	}
	
	//Getters
	
	//Setters
	
}
