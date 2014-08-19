package nl.weeaboo.vnds;

import static nl.weeaboo.string.StringUtil2.*;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileUtil;

public class FileMapper implements Iterable<Entry<String, String>> {

	private Map<String, String> hash2file;
	private Map<String, String> file2hash;
	
	public FileMapper() {
		hash2file = new Hashtable<String, String>();
		file2hash = new Hashtable<String, String>();
	}
	
	//Functions
	public String add(String filename) {
		return add(filename, "");
	}
	public String add(String filename, String hashPrefix) {
		return add(filename, hashPrefix, StringUtil.getExtension(filename));
	}
	public String add(String key, String hashPrefix, String fext) {
		key = key.toLowerCase();
		String hash = getHashed(key);
		if (hash != null) return hash;
				
		Random random = new Random(key.hashCode());
		
		String ext = "." + fext;
		String shortname = getShortname(key);
		if (shortname != null) {
			hash = (hashPrefix + shortname + ext).toLowerCase();
		}
		
		while (hash == null || hash2file.containsKey(hash)) {
			hash = (hashPrefix + getRandomString(5, random) + ext).toLowerCase();
		};
		
		hash2file.put(hash, key);
		file2hash.put(key, hash);
		return hash;
	}
	public String getHashed(String original) {
		return file2hash.get(original.toLowerCase());
	}
	public String getOriginal(String hashed) {
		return hash2file.get(hashed.toLowerCase());
	}
	
	private String getShortname(String filename) {
		filename = StringUtil.stripExtension(filename.substring(filename.lastIndexOf('/')+1));
		filename = filename.replaceAll("[?\\[\\]/\\=+<>:;\",*| ]", "_");
		
		//Remove double file extensions
		int index = filename.indexOf('.');
		if (index > 0) {
			filename = filename.substring(0, index);
		}
		
		//Truncate long filenames
		if (filename.length() > 12) {
			filename = filename.substring(0, 12);
		}
		
		//Return null if the filename is invalid
		if (!FileUtil.isValidFilename(filename)) {
			return null;
		}
		
		//Return null if the filename is non-ascii
		for (int n = 0; n < filename.length(); n++) {
			if (filename.charAt(n) > 127) {
				return null;
			}
		}

		return filename;
	}
	
	public void load(String file) throws IOException {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
			String line;
			while ((line = in.readLine()) != null) {
				String split[] = line.split(" > ");
				hash2file.put(split[0], split[1]);
				file2hash.put(split[1], split[0]);
			}
			in.close();
		} catch (FileNotFoundException fnfe) {			
		}
	}
	public void save(String file) throws IOException {
		OutputStream fout = new BufferedOutputStream(new FileOutputStream(file));
		for (Entry<String, String> entry : hash2file.entrySet()) {
			fout.write(entry.getKey().getBytes("UTF-8"));
			fout.write(" > ".getBytes("UTF-8"));
			fout.write(entry.getValue().getBytes("UTF-8"));
			fout.write('\n');
		}
		fout.close();
	}
	
	public void put(String hash, String original) {
		hash2file.put(hash, original);
		file2hash.put(original, hash);
	}
	
	/** Returns the entries in <hash, file> form */
	public Iterator<Entry<String, String>> iterator() {
		return hash2file.entrySet().iterator();
	}
	
	//Getters
	
	//Setters
	
}
