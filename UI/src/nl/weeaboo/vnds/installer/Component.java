package nl.weeaboo.vnds.installer;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import nl.weeaboo.io.FileUtil;
import nl.weeaboo.vnds.Log;
import nl.weeaboo.xml.XmlElement;

public class Component {

	private String name;
	private String desc;
	private Set<FileListEntry> files;
	
	public Component(String name, String desc) {
		this.name = name;
		this.desc = desc;
		
		files = new HashSet<FileListEntry>();
	}
	
	//Functions
	public void addFile(FileListEntry file) {
		files.add(file);		
	}
	public void removeFile(FileListEntry file) {
		files.remove(file);		
	}
	
	//Getters
	public Set<FileListEntry> getFiles() {
		return files;
	}
	public String getName() {
		return name;
	}
	public String getDesc() {
		return desc;
	}
	
	//Setters

	//Save Support
	public void save(File file) throws IOException {
		XmlElement componentE = new XmlElement("component");
		componentE.addAttribute("name", name);
		componentE.addAttribute("desc", desc);
		
		for (FileListEntry fle : files) {
			XmlElement fileE = componentE.addChild("file");
			fileE.addAttribute("path", fle.getPath());
			fileE.addAttribute("size", fle.getSize());
			if (fle.getHash() != null) fileE.addAttribute("hash", fle.getHash());
			fileE.addAttribute("url", fle.getURL());
		}

		FileUtil.write(file, componentE.toXmlString());
	}
	
	public static Component fromXml(String baseFolder, XmlElement componentE) {
		Component c = new Component(componentE.getAttribute("name"), componentE.getAttribute("desc"));
		for (XmlElement fileE : componentE.getChildren("file")) {
			try {
				String fpath = fileE.getAttribute("path");
				long fsize = Long.parseLong(fileE.getAttribute("size"));
				String fhash = fileE.getAttribute("hash");
				if (fhash.equals("")) fhash = null;
				String furl = fileE.getAttribute("url");

				String filePath = baseFolder+"/"+furl;
				if (filePath.contains(".zip")) {
					filePath = filePath.substring(0, filePath.indexOf(".zip")+4);
				}
				File file = new File(filePath);
				
				c.addFile(new FileListEntry(fpath, fsize, fhash, furl, file));
			} catch (NumberFormatException nfe) {
				Log.w("Exception parsing installer component", nfe);
			}
		}
		return c;
	}
}
