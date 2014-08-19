package nl.weeaboo.vnds.installer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.vnds.Log;
import nl.weeaboo.vnds.ProgressListener;
import nl.weeaboo.xml.XmlElement;
import nl.weeaboo.xml.XmlReader;

public class Installer {

	//Functions
	public static void main(String args[]) {
		if (args.length < 2) {
			System.err.println("Usage: java -jar Installer.jar <output-path> <component-xml-1> <component-xml-2>");
			System.exit(1);
		}
		
		String paths[] = Arrays.copyOfRange(args, 1, args.length);
		install(args[0], null, paths);
	}
	
	public static String install(String outputFolder, ProgressListener pl, String... componentPaths) {
		StringBuilder sb = new StringBuilder();
		List<FileListEntry> files = new LinkedList<FileListEntry>();
		
		int t = 0;
		for (String path : componentPaths) {
			if (pl != null) {
				t++;
				pl.onProgress(t, componentPaths.length, String.format("Reading %s...", path));
			}					

			try {
				File file = new File(path);
				//String parentPath = file.getAbsoluteFile().getParent();
				
				XmlReader xmlReader = new XmlReader();
				XmlElement componentE = xmlReader.read(file).getChild("component");
				Component component = Component.fromXml(new File(".").getAbsolutePath(), componentE);
				
				for (Iterator<FileListEntry> i = files.iterator(); i.hasNext(); ) {
					FileListEntry f = i.next();
					for (FileListEntry nf : component.getFiles()) {
						if (f.getPath().equals(nf.getPath())) {
							i.remove(); //Overwrite old file
							break;
						}
					}
				}
				
				files.addAll(component.getFiles());
			} catch (Exception e) {
				e.printStackTrace();
				sb.append(e);
				sb.append('\n');
			}
		}
		
		installFileList(outputFolder, pl, files);
		
		if (pl != null) pl.onFinished("");
		return sb.toString();
	}
	
	public static void installFileList(String outputFolder, ProgressListener pl,
			Collection<FileListEntry> files)
	{
		FileUtil.deleteFolder(new File(outputFolder));
		
		new File(outputFolder+"/foreground").mkdirs();
		new File(outputFolder+"/background").mkdirs();
		new File(outputFolder+"/script").mkdirs();
		new File(outputFolder+"/save").mkdirs();
		new File(outputFolder+"/sound").mkdirs();
		
		byte readBuffer[] = new byte[10 * 1024 * 1024];		
		Map<File, ZipFile> zipFiles = new Hashtable<File, ZipFile>();
		Map<File, ZipOutputStream> zipOutStreams = new Hashtable<File, ZipOutputStream>();
		
		int t = 0;
		for (FileListEntry entry : files) {
			t++;
			
			try {
				File file = entry.getFile();
				String path = entry.getPath();
				if (pl != null) {
					pl.onProgress(t, files.size(), String.format("Installing %s...", path));
				}

				if (StringUtil.getExtension(file.getName()).equals("zip")) {
					try {
						ZipFile inZip = zipFiles.get(file);
						if (inZip == null) {
							zipFiles.put(file, inZip = new ZipFile(file));
						}
						ZipOutputStream zout = zipOutStreams.get(file);
						if (zout == null) {
							String zipPath = outputFolder+"/"+path.substring(0, path.lastIndexOf(".zip")+4);
							zipOutStreams.put(file, zout = new ZipOutputStream(new BufferedOutputStream(
									new FileOutputStream(zipPath))));
							zout.setMethod(ZipOutputStream.STORED);
						}								

						String entryName = path.substring(path.indexOf(file.getName())+file.getName().length()+1);
						
						int fsize = 0;
						{
							//Read file data							
							ZipEntry zipEntry = inZip.getEntry(entryName);
							if (zipEntry == null) continue;							
							InputStream in = new BufferedInputStream(inZip.getInputStream(zipEntry));

							int r;
							while ((r = in.read()) >= 0) {
								readBuffer[fsize++] = (byte)r;
							}
							
							in.close();						
						}
						{
							//Write file data
							
				            //Create ZIP Entry
							ZipEntry zipEntry = new ZipEntry(entryName);				
							zipEntry.setSize(fsize);
							zipEntry.setCompressedSize(fsize);
				            CRC32 crc = new CRC32();
				            crc.update(readBuffer, 0, fsize);
				            zipEntry.setCrc(crc.getValue());
				            zout.putNextEntry(zipEntry);
				
							//Write File contents to ZIP
							zout.write(readBuffer, 0, fsize);
							
							zout.flush();
							zout.closeEntry();
						}
					} catch (IOException ioe) {
						Log.e("Exception during installation", ioe);
					}
				} else {
					FileUtil.copyFile(file, new File(outputFolder+"/"+path));
				}
			} catch (Exception e) {
				Log.e("Exception during installation", e);
			}
		}
		
		//Close open files
		for (ZipFile file : zipFiles.values()) {
			try { file.close(); } catch (IOException e) {}
		}
		for (ZipOutputStream zout : zipOutStreams.values()) {
			try { zout.close(); } catch (IOException e) {}
		}
	}
	
	//Getters
	
	//Setters
	
}
