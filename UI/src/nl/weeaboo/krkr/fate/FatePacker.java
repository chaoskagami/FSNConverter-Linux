package nl.weeaboo.krkr.fate;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.krkr.Packer;
import nl.weeaboo.krkr.fate.FateScriptConverter.Language;
import nl.weeaboo.vnds.FileMapper;
import nl.weeaboo.vnds.Log;
import nl.weeaboo.vnds.installer.InstallerPacker;

public class FatePacker extends Packer {
	
	private String language;
	
	public FatePacker(String language, String in) {
		this.language = language;
	}

	//Functions
	protected static void printUsage() {
		System.err.println("Usage: java -jar FatePacker.jar <src-folder> <target-folder> <flags>\nflags:"
				+ "\n\t-novoice"
				+ "\n\t-threads <num>"
				+ "\n\t-lang <EN|JA|CH>"		
				+ "\n\t-cleanTempFiles");		
	}

	public static void main(String args[]) {
		if (args.length < 2) {
			printUsage();
			return;
		}

		long startTime = System.currentTimeMillis();
		
		Language language = Language.EN; 
		String srcFolder = args[0];
		String dstFolder = args[1];
		String tempFolder = srcFolder+"/_temp/";
		int threads = 2;
		boolean cleanTempFiles = false;

		try {
			for (int n = 2; n < args.length; n++) {
				if (args[n].startsWith("-lang")) {
					language = Language.valueOf(args[++n]);
				} else if (args[n].startsWith("-threads")) {
					threads = Integer.parseInt(args[++n]);
				} else if (args[n].startsWith("-cleanTempFiles")) {
					cleanTempFiles = true;
				}
			}
		} catch (RuntimeException re) {
			printUsage();
			return;
		}
				
		FatePacker packer;
		
		//Clean install folder
		FileUtil.deleteFolder(new File(dstFolder));		
		new File(dstFolder).mkdirs();
		
		packer = new FatePacker(language.getLangCode(), srcFolder);
		packer.process(new File(srcFolder), new File(tempFolder));		

		System.out.println("Installing "+language+"...");
		InstallerPacker.execute(String.format("create \"%s\" \"%s\"", tempFolder, dstFolder));

		{
			ResourceUsageAnalyzer rua = new ResourceUsageAnalyzer(srcFolder+"/_info", srcFolder);
			rua.analyze(language, threads);
			File depF = new File(srcFolder+"/_info/dependency_analysis");
			File[] files = depF.listFiles();
			if (files != null) {
				for (File f : files) {
					if (f.getName().endsWith(".xml")) {
						try {
							FileUtil.copyFile(f, new File(dstFolder+"/_installer/"+f.getName()));
						} catch (IOException ioe) {
							Log.w("Error trying to copy file: " + f, ioe);
						}
					}
				}
			}
		}
		System.gc();

		File dst = new File(dstFolder);
		try {
			FileUtil.copyFile(new File("template/fate/instructions.txt"), dst);
			FileUtil.copyFile(new File("FSNInstaller.jar"), dst);
			copyFolderNoSVN(new File("lib"), new File(dstFolder+"/lib"));
		} catch (IOException e) {
			Log.e("Error trying to copy files", e);
		}
		
		//Clean tempfolder
		FileUtil.deleteFolder(new File(tempFolder));

		if (cleanTempFiles) {
			Log.v("Cleaning temp files...");
			
			FileUtil.deleteFolder(new File(srcFolder+"/../data"));
			FileUtil.deleteFolder(new File(srcFolder));
		}
		
		//Finished
		Log.v(StringUtil.formatTime(System.currentTimeMillis()-startTime, TimeUnit.MILLISECONDS) + " Finished.");
	}

	@Override
	protected void modifyNameMapping(FileMapper mapper) {
		mapper.put("info.txt", "info-" + language.toLowerCase() + ".txt");		
	}
	
	protected static void copyFolderNoSVN(File src, File dst) throws IOException {
		Map<String, File> fileMap = new HashMap<String, File>();
		FileUtil.collectFiles(fileMap, src, false);
		for (Iterator<Entry<String, File>> i = fileMap.entrySet().iterator(); i.hasNext(); ) {
			Entry<String, File> entry = i.next();
			if (entry.getValue().isDirectory()) {
				continue;
			}
			
			String key = entry.getKey().replace('\\', '/');
			if (key.contains("/.") || key.startsWith(".") || entry.getKey().equals("instructions")) {
				//Remove unix-style hidden files (like .svn folders)
				//Remove instructions.txt (install instructions)
				i.remove();
			}
		}
		
		new File(dst.getAbsolutePath()+'/'+src.getName()).mkdirs();
		FileUtil.copyFiles(fileMap, dst);		
	}
	
	//Getters
	
	//Setters
	
}
