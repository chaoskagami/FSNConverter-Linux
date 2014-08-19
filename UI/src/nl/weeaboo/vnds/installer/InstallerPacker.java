package nl.weeaboo.vnds.installer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import nl.weeaboo.common.StringUtil;
import nl.weeaboo.vnds.HashUtil;
import nl.weeaboo.vnds.Log;

public class InstallerPacker {

	//Functions
	private static void printUsage() {
		System.err.println("Usage: java -jar InstallPacker.jar <command> <output path>\n"
				+ "\t\tcommands:\n"
				+ "\tcreate <path>\n"
				+ "\tpatch <patch-name> <path>\n"
				+ "\tadd <component-xml-path>\n"				
				+ "\n");
	}
	
	public static PackCommand parseCommand(String args[]) {
		if (args[0].equals("create")) {
			return new CreateCommand(args[1], args[2]);
		} else if (args[0].equals("patch")) {
			return new PatchCommand(args[1], args[2], args[3]);
		} else if (args[0].equals("add")) {
			return new AddCommand(args[1], args[2]);
		}
		Log.w("Unknown Command: " + args[0]);
		return null;
	}
	
	public static void main(String args[]) {
		if (args.length < 1) {
			printUsage();
			return;
		}
		
		PackCommand c = parseCommand(args);
		if (c == null) {
			printUsage();
			return;
		}
		
		c.execute();
	}
	
	public static void execute(String command) {
		List<String> args = new ArrayList<String>();
		
		String parts[] = command.split(" ");
		
		boolean inString = false;
		StringBuilder buffer = new StringBuilder();
		for (int n = 0; n < parts.length; n++) {
			parts[n] = parts[n].trim();
			
			if (!inString && parts[n].startsWith("\"")) {
				inString = true;
				parts[n] = parts[n].substring(1);
			}
			
			if (inString) {
				if (buffer.length() > 0) buffer.append(' ');
				buffer.append(parts[n]);
			} else {
				args.add(parts[n]);
			}
			
			if (inString && parts[n].endsWith("\"")) {
				args.add(buffer.substring(0, buffer.length()-1)); //Don't include trailing (")
				buffer.delete(0, buffer.length());
				inString = false;
			}
		}
		
		parseCommand(args.toArray(new String[args.size()])).execute();
	}

	public static void collectFiles(Map<String, File> map, File file, boolean includeRootFolder) {
		if (!file.isDirectory() && !StringUtil.getExtension(file.getName()).equals("zip")) {
			map.put(file.getName(), file);
		} else {
			if (includeRootFolder) {
				collectFiles(map, file, "");
			} else {
				if (file.isDirectory()) {
					for (File f : file.listFiles()) {
						collectFiles(map, f, "");
					}
				} else if (StringUtil.getExtension(file.getName()).equals("zip")) {
					collectZippedFiles(map, file, "");
				}
			}
		}
	}
	public static void collectZippedFiles(Map<String, File> map, File file, String relPath) {
		try {
			ZipFile zipFile = new ZipFile(file);
		
			Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
			while (enumeration.hasMoreElements()) {
				ZipEntry entry = enumeration.nextElement();
				map.put(relPath + '/' + entry.getName(), file);
			}
			
			zipFile.close();
		} catch (ZipException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	private static void collectFiles(Map<String, File> map, File file, String relPath) {
		String path;
		if (relPath.length() > 0) {
			path = relPath + '/' + file.getName();
		} else {
			path = file.getName();
		}		

		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				collectFiles(map, f, path);
			}
		} else if (StringUtil.getExtension(file.getName()).equals("zip")) {
			collectZippedFiles(map, file, path);
		} else {
			map.put(path, file);
		}
	}

	public static Set<FileListEntry> generateFileList(Map<String, File> files, String baseURL, boolean skipHash) {		
		byte readBuffer[] = new byte[10 * 1024 * 1024];
		
		Map<File, ZipFile> zipFiles = new Hashtable<File, ZipFile>();
		Set<FileListEntry> entries = new HashSet<FileListEntry>();
		
		int t = 0;
		for (Entry<String, File> entry : files.entrySet()) {
			t++;
			//System.out.printf("(%d/%d) %s\n", t, files.size(), entry.getKey());
			if ((t & 0xFF) == 0) {
				System.out.printf("Files Hashed: %d/%d\n", t, files.size());
			}
				
			File file = entry.getValue();
			if (!file.exists()) {
				continue;
			}
			
			try {
				String fpath = entry.getKey();
				long fsize = file.length();
				String fhash = null;
				if (StringUtil.getExtension(file.getName()).equals("zip")) {
					try {
						if (!zipFiles.containsKey(file)) {
							zipFiles.put(file, new ZipFile(file));
						}
						ZipFile zip = zipFiles.get(file);
						ZipEntry zipEntry = zip.getEntry(fpath.substring(fpath.indexOf(file.getName())
								+ file.getName().length() + 1));
						
						InputStream in = new BufferedInputStream(zip.getInputStream(zipEntry));
						if (!skipHash) {
							fsize = readFromStream(readBuffer, in);
							fhash = HashUtil.hashToString(HashUtil.generateHash(readBuffer, 0, (int)fsize));
						}
						in.close();						
					} catch (IOException ioe) {
						Log.e("Error hashing file", ioe);
					}
				}
				if (fhash == null) {
					if (!skipHash) {
						BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
						fsize = readFromStream(readBuffer, in);
						fhash = HashUtil.hashToString(HashUtil.generateHash(readBuffer, 0, (int)fsize));
						in.close();
					}
				}
				String furl = baseURL + "/" + fpath;
				
				entries.add(new FileListEntry(fpath, fsize, fhash, furl, file));
			} catch (Exception e) {
				Log.e("Exception generating file list", e);
			}
		}
		
		for (ZipFile file : zipFiles.values()) {
			try {
				file.close();
			} catch (IOException e) {}
		}
		return entries;
	}
	
	protected static int readFromStream(byte[] out, InputStream in) throws IOException {
		int off = 0;
		while (off < out.length) {
			int r = in.read(out, off, out.length-off);
			if (r < 0) break;
			off += r;
		}
		return off;
	}
	
	//Getters
	
	//Setters
	
}
