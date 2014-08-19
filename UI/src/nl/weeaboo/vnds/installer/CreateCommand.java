package nl.weeaboo.vnds.installer;

import java.io.File;
import java.io.IOException;

import nl.weeaboo.io.FileUtil;
import nl.weeaboo.vnds.Log;

public class CreateCommand extends PackCommand {

	private String baseFolder;
	
	public CreateCommand(String baseFolder, String outputFolder) {
		super(outputFolder);
		
		this.baseFolder = baseFolder;
	}
	
	//Functions
	public void execute() {
		File target = new File(getOutputFolder() + "/base_install");
		if (target.exists()) {
			FileUtil.deleteFolder(target);
		}
		
		target.mkdirs();
		try {
			FileUtil.copyFolderContents(new File(baseFolder), target);
		} catch (IOException e) {
			Log.e("Exception executing create command", e);
		}

		new File(target.getAbsolutePath()+"/background").mkdirs();
		new File(target.getAbsolutePath()+"/foreground").mkdirs();
		new File(target.getAbsolutePath()+"/script").mkdirs();
		new File(target.getAbsolutePath()+"/save").mkdirs();
		new File(target.getAbsolutePath()+"/sound").mkdirs();
	}
	
	//Getters
	public String getBaseFolder() {
		return baseFolder;
	}
	
	//Setters
	
}
