package nl.weeaboo.vnds.installer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;

import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.vnds.Log;

public class PatchCommand extends PackCommand {

	private String patchName;
	private String patchedFolder;
	
	public PatchCommand(String patchName, String patchedFolder, String outputFolder) {
		super(outputFolder);
		
		this.patchName = patchName;
		this.patchedFolder = patchedFolder;
	}
	
	//Functions
	public void execute() {
		boolean skipHash = false;
		String baseURL = "patch/" + patchName;
		
		Map<String, File> patchedFiles = new Hashtable<String, File>();
		InstallerPacker.collectFiles(patchedFiles, new File(patchedFolder), false);
		Set<FileListEntry> patchedEntries = InstallerPacker.generateFileList(patchedFiles, baseURL, skipHash);
		
		Map<String, File> baseFiles = new Hashtable<String, File>();
		InstallerPacker.collectFiles(baseFiles, new File(getOutputFolder() + "/base_install"), false);
		Set<FileListEntry> baseEntries = InstallerPacker.generateFileList(baseFiles, baseURL, skipHash);

		List<FileListEntry> changedList  = new ArrayList<FileListEntry>();
		for (FileListEntry entry : patchedEntries) {
			if (!baseEntries.contains(entry)) {
				changedList.add(entry);				
			}
		}
		
		try {
			Component component = new Component(patchName, "");
			String patchFolder = getOutputFolder() + "/" + patchName;
			for (FileListEntry changed : changedList) {
				component.addFile(changed);
	
				File target = new File(patchFolder + '/' + changed.getPath());
				if (StringUtil.getExtension(changed.getFile().getName()).equals("zip")) {
					target = new File(patchFolder + '/' + changed.getPath().substring(0,
							changed.getPath().lastIndexOf(changed.getFile().getName())
							+changed.getFile().getName().length()));
				}
				if (!target.exists() || target.length() != changed.getFile().length()) {
					FileUtil.copyFile(changed.getFile(), target);
				}
			}
		
			component.save(new File(getOutputFolder(), patchName + ".xml"));
		} catch (IOException e) {
			Log.e("Exception executing patch command", e);
		}
	}

	//Getters
	
	//Setters
}
