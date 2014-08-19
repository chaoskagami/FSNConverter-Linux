package nl.weeaboo.vnds.installer;

import java.io.File;

public class FileListEntry {

	private final String path;
	private final long size;
	private final String url;
	private String hash;
	
	private File file;
	
	public FileListEntry(String path, long size, String hash, String url) {
		this(path, size, hash, url, null);
	}
	public FileListEntry(String path, long size, String hash, String url, File file) {
		this.path = path;
		this.size = size;
		this.hash = hash;
		this.url = url;
		
		this.file = file;
	}
	
	//Functions
	public String toString() {
		return String.format("%s %d %s %s", path, size, hash, url);
	}
	
	public int hashCode() {
		return path.hashCode();
	}
	
	public boolean equals(Object o) {
		if (o instanceof FileListEntry) {
			return equals((FileListEntry)o);
		}
		return false;
	}
	public boolean equals(FileListEntry entry) {
		return path.equals(entry.getPath()) 
			&& size == entry.getSize()
			&& ((getHash() == null && entry.getHash() == null) || getHash().equals(entry.getHash()));
		//Don't compare URL's
	}
	
	//Getters
	public File getFile() {
		return file;
	}
	
	public String getPath() {
		return path;
	}               
	public long getSize() {
		return size;
	}
	public String getHash() {
		return hash;
	}
	public String getURL() {
		return url;
	}
	
	//Setters
	
}
