package nl.weeaboo.krkr.fate;

import java.io.File;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedSet;
import java.util.TreeSet;

import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.krkr.XP3Extractor;
import nl.weeaboo.string.StringUtil2;
import nl.weeaboo.vnds.Log;
import nl.weeaboo.vnds.ProgressListener;

public class FateExtractor {
	
	private String realtaNuaPath;
	
	public FateExtractor() {
		
	}
	
	//Functions
	protected static void printUsage() {
		System.err.println("Usage: java -jar FateExtractor.jar <game-folder> <output-folder> <flags>\nflags:"
				+ "\n\t-rn <realta-nua-disc-path>"
				+ "\n\t-novoice"
				+ "\n\t-cleanTempFiles"
				);		
	}
	
	public static void main(String args[]) {
		if (args.length < 2) {
			printUsage();
			return;
		}
				
		boolean extractVoice = true;
		boolean cleanTempFiles = false;
		FateExtractor ex = new FateExtractor();		
		try {
			for (int n = 2; n < args.length; n++) {
				if (args[n].startsWith("-rn")) {
					ex.realtaNuaPath = args[++n];
				} else if (args[n].startsWith("-novoice")) {
					extractVoice = false;
				} else if (args[n].startsWith("-cleanTempFiles")) {
					cleanTempFiles = true;
				}
			}
		} catch (RuntimeException re) {
			printUsage();
			return;
		}

		SortedSet<String> archives = new TreeSet<String>(StringUtil2.getStringComparator());
		
		File src = new File(args[0]);
		File dst = new File(args[1]);
		if (src.isDirectory()) {
			for (File file : src.listFiles()) {
				if ("xp3".equalsIgnoreCase(StringUtil.getExtension(file.getName()))) {
					if (file.getName().equals("patch6.xp3")) {
						if (extractVoice &&
							(ex.realtaNuaPath == null || !new File(ex.realtaNuaPath).exists()))
						{
							archives.add(file.getAbsolutePath());
						}
					} else {
						archives.add(file.getAbsolutePath());
					}
				}
			}
		} else {
			archives.add(src.getAbsolutePath());
		}	

		Log.v("Extracting...");
		//Extract <name>.xp3 to outFolder/<name>
		for (String arc : archives) {
			ProgressListener pl = new ProgressListener() {
				@Override
				public void onProgress(int value, int max, String message) {
					Log.v(String.format("%s/%s %s...", StringUtil.formatMemoryAmount(value),
							StringUtil.formatMemoryAmount(max), message));
				}
				@Override
				public void onFinished(String message) {
					Log.v(message);
				}
			};
			
			try {
				ex.extractXP3(arc, dst.getAbsolutePath(), pl);
			} catch (IOException e) {
				Log.e("Exception extracting XP3 archive", e);
			}
		}
		
		if (ex.realtaNuaPath != null && extractVoice) {
			RealtaNuaSoundExtractor rne = new RealtaNuaSoundExtractor();
			try {
				rne.extract(ex.realtaNuaPath, dst.getAbsolutePath()+"/patch6");
			} catch (IOException e) {
				Log.w("Unable to extract voice data from Realta Nua: " + e);
			}
		}
		
		Log.v("Flattening Folders...");
		for (File folder : dst.listFiles()) {
			if (folder.isDirectory()) {
				Map<String, File> fileMap = new Hashtable<String, File>();
				FileUtil.collectFiles(fileMap, folder, false);
				
				for (Entry<String, File> entry : fileMap.entrySet()) {
					File file = entry.getValue();
					File target = new File(folder.getAbsolutePath()+'/'+file.getName().toLowerCase());
					
					if (!file.equals(target) &&
						!file.getAbsolutePath().equalsIgnoreCase(target.getAbsolutePath()))
					{
						target.delete();
					}
					file.renameTo(target);
				}
				
				FileUtil.deleteEmptyFolders(folder);
			}
		}
		
		Log.v("Patching...");
		
		//Create patch list
		SortedSet<String> patchFolders = new TreeSet<String>(StringUtil2.getStringComparator());
		for (File folder : dst.listFiles()) {
			if (folder.isDirectory() && folder.getName().toLowerCase().startsWith("patch")) {
				patchFolders.add(folder.getAbsolutePath());
			}
		}

		Map<String, File> patchMap = new Hashtable<String, File>();
		for (String folder : patchFolders) {
			//Iterate the sorted list of patches so patch2 gets overwritten by patch6
			FileUtil.collectFiles(patchMap, new File(folder), false);
		}
		
		//Apply patches to each archive separately
		for (File folder : dst.listFiles()) {
			if (folder.isDirectory() && !folder.getName().toLowerCase().startsWith("patch")) {
				Map<String, File> fileMap = new Hashtable<String, File>();
				FileUtil.collectFiles(fileMap, folder, false);
				
				for (Entry<String, File> patchEntry : patchMap.entrySet()) {
					File file = fileMap.get(patchEntry.getKey());
					if (file != null) {
						//Overwrite if exists
						try {
							FileUtil.copyFile(patchEntry.getValue(), file);
						} catch (IOException e) {
							Log.w("Error trying to copy file: " + patchEntry.getValue() + " to " + file, e);
						}
					}
				}
			}
		}
		
		if (cleanTempFiles) {
			Log.v("Cleaning temp files...");
			
			String delete[] = new String[] {"image", "patch", "patch2", "patch3", "patch4", "patch5",
					"patch", "system", "version", "video"};
			for (String d : delete) {
				FileUtil.deleteFolder(new File(dst.getAbsolutePath()+'/'+d));
			}
		}
		Log.v("Done.");
	}
	
	public void extractXP3(String archive, String outFolder, ProgressListener pl) throws IOException {
		XP3Extractor xp3ex = new XP3Extractor();
		xp3ex.extract(archive, outFolder+"/"+StringUtil.stripExtension(archive.substring(archive.replace('\\', '/').lastIndexOf('/'))), pl);

		/*
		if (archive.endsWith("patch6.xp3")) {
			//Weird, uncompressed xp3, crass doesn't like that
			XP3Extractor xp3ex = new XP3Extractor();
			xp3ex.extract(archive, outFolder+"/"+FileUtil.stripExtension(archive.substring(archive.replace('\\', '/').lastIndexOf('/'))));
			return;
		}

		ProcessOutputReader pr = new ProcessOutputReader();
		String cmd = String.format("crage -p \"%s\" -o \"%s\" -O game=FSN", archive, outFolder);
		System.out.println(cmd);
		Process p = SystemUtil.execInDir(cmd, "tools/crass-0.4.13.0");
		
		String output = pr.read(p).trim();
		if (output.length() > 0) {
			System.err.println(output);
		}
		*/
	}
	
	//Getters
	
	//Setters
	
}
