package nl.weeaboo.krkr;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipOutputStream;

import nl.weeaboo.io.FileUtil;
import nl.weeaboo.vnds.FileMapper;
import nl.weeaboo.vnds.Log;

public class Packer {
	
	public Packer() {
	}
	
	//Functions
	
	public void process(File currentFolder, File targetFolder) {
		cleanAction(targetFolder);
		targetFolder.mkdirs();		
		
		System.out.println("Copying files...");

		copyAction(currentFolder, targetFolder);
		
		try {
			FileMapper mapper = new FileMapper();
			mapper.load(currentFolder.getAbsolutePath()+"/_info/filenames.txt");
			modifyNameMapping(mapper);
			
			int t = 0;
			for (Entry<String, String> entry : mapper) {
				if (processEntry(currentFolder, targetFolder, entry.getKey(), entry.getValue())) {
					t++;
					if ((t & 0xFF) == 0) {
						System.out.printf("Files Copied: %d\n", t);
					}
				}
			}

			System.out.println("Zipping files...");

			zip(new File(targetFolder + "/foreground"), new File(targetFolder + "/foreground.zip"));
			zip(new File(targetFolder + "/background"), new File(targetFolder + "/background.zip"));
			zip(new File(targetFolder + "/script"), new File(targetFolder + "/script.zip"));
			zip(new File(targetFolder + "/sound"), new File(targetFolder + "/sound.zip"));
		} catch (IOException ioe) {
			ioe.printStackTrace();
		}
	}

	protected void modifyNameMapping(FileMapper mapper) {
	}
	
	protected void cleanAction(File targetFolder) {
		if (targetFolder.exists()) {
			FileUtil.deleteFolder(targetFolder);
			if (targetFolder.exists()) {
				throw new RuntimeException("Unable to delete to target folder");
			}
		}
	}
	
	protected void copyAction(File currentFolder, File targetFolder) {
		copy(new File(currentFolder + "/foreground/special"), new File(targetFolder.getAbsolutePath() + "/foreground/"));
		copy(new File(currentFolder + "/background/special"), new File(targetFolder.getAbsolutePath() + "/background/"));
		copy(new File(currentFolder + "/sound/special"), new File(targetFolder.getAbsolutePath() + "/sound/"));
		copy(new File(currentFolder + "/script"), targetFolder);
		
		new File(targetFolder.getAbsolutePath() + "/save/").mkdirs();
		copy(new File(currentFolder + "/save"), targetFolder);

		copy(new File(currentFolder + "/default.ttf"), targetFolder);
		copy(new File(currentFolder + "/icon.png"), targetFolder);
		copy(new File(currentFolder + "/thumbnail.png"), targetFolder);
		copy(new File(currentFolder + "/icon-high.png"), targetFolder);
		copy(new File(currentFolder + "/thumbnail-high.png"), targetFolder);
		copy(new File(currentFolder + "/icon-high.jpg"), targetFolder);
		copy(new File(currentFolder + "/thumbnail-high.jpg"), targetFolder);
		copy(new File(currentFolder + "/info.txt"), targetFolder);
		
		File imgIniF = new File(currentFolder + "/img.ini");
		if (imgIniF.exists()) {
			copy(imgIniF, targetFolder);
		}
	}
	
	private boolean processEntry(File srcDir, File dstDir, String hash, String original) {
		String relpath = original.substring(0, original.lastIndexOf('/')+1) + hash;
		File src = new File(srcDir + File.separator + original);
		File dst = new File(dstDir + File.separator + relpath);
		if (src.exists()) {
			try {
				FileUtil.copyFile(src, dst);
			} catch (IOException e) {
				Log.w("Error copying file: " + src + " to " + dst, e);
			}
		}
		return false;
	}
	
	public static File copy(File src, File dstFolder) {
		if (!src.exists()) {
			return null;
		}
		
		if (src.isDirectory()) {
			for (String s : src.list()) {
				copy(new File(src.getAbsolutePath() + File.separator + s), new File(dstFolder.getAbsolutePath() + File.separator + src.getName()));
			}
			return null;
		} else {		
			File dst = new File(dstFolder.getAbsolutePath() + File.separator + src.getName());
			try {
				FileUtil.copyFile(src, dst);
			} catch (IOException e) {
				Log.w("Error copying file: " + src + " to " + dst, e);
			}
			return dst;
		}
	}
	
	protected void zip(File folder, File zipFile) throws IOException {
		zip(new File[] {folder}, zipFile);
	}
	protected void zip(File folders[], File zipFile) throws IOException {
		for (File folder : folders) {
			if (!folder.exists() || folder.listFiles().length == 0) {
				continue;
			}
			
			try {
				ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(zipFile));		
				zout.setMethod(ZipOutputStream.STORED);
				
				addToZip(zout, folder, "", true);
				
				zout.flush();
				zout.close();
			} catch (ZipException ze) {
				Log.v("Empty ZIP file: " + zipFile);
				zipFile.delete();
			}
		}
	}
	protected void addToZip(ZipOutputStream zout, File file, String prefix, boolean deleteWhenDone) throws IOException {
		addToZip(zout, file, prefix, file.getName(), deleteWhenDone);
	}
	protected void addToZip(ZipOutputStream zout, File file, String prefix, String filename, boolean deleteWhenDone) throws IOException {
		if (!file.exists()) {
			return;
		}
		
		if (prefix.length() > 0) {
			prefix += '/';
		}
		prefix += filename;
		
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				addToZip(zout, f, prefix, deleteWhenDone);
			}
		} else {
			if (!filename.endsWith(".mp3")) {	
				//System.out.println("ZIP: " + file.getName());
				
	            //Read file contents and delete file afterwards
	            byte[] buffer = FileUtil.readBytes(file);
	            if (deleteWhenDone) {
	            	file.delete();
	            }
				
	            //Create ZIP Entry
				ZipEntry entry = new ZipEntry(prefix);				
				entry.setSize(buffer.length);
				entry.setCompressedSize(buffer.length);
	            CRC32 crc = new CRC32();
	            crc.update(buffer);
	            entry.setCrc(crc.getValue());
	            zout.putNextEntry(entry);
	
				//Write File contents to ZIP
				zout.write(buffer);
				
				zout.flush();
				zout.closeEntry();
			}
		}
	}
	
	//Getters
	
	//Setters
	
}
