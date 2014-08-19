package nl.weeaboo.krkr.fate;

import java.io.File;

public class FateDirectoryFlattener {

	private static final File root = new File("c:/users/timon/desktop/2/");

	//Functions
	public static void main(String args[]) {		
		process(1, root);		
		process(2, root);
	}
	
	public static void process(int pass, File file) {
		if (file.isDirectory()) {
			File files[] = file.listFiles();
			for (File f : files) {
				process(pass, f);
			}
			
			if (pass == 2) {
				file.delete();
			}
		} else {
			if (pass == 1) {
				String path = file.getAbsolutePath();
				path = path.substring(root.getAbsolutePath().length(), path.length());
				String filename = path.replace(File.separatorChar, '/').replaceAll("\\/", "");
				
				//Uncomment when flattening the scenario folder, this fixes the garbled filenames
				/*try {
					//String newName = new String(filename.getBytes("SJIS"), "CP1252");
					String newName = new String(filename.getBytes("CP1252"), "CP1252");
					newName = newName.replace((char)0xfffd, '?');
					//newName = newName.replaceAll("\\?", "");					
					String converted = RouteParser.scenarioFileRename(4, newName);
					if (converted != null) {
						filename = converted;
					} else {
						System.out.println(filename + " " + newName);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}*/
				
				File f = new File(root.getAbsolutePath() + File.separator + filename);
				file.renameTo(f);			
				System.out.println(file.getAbsolutePath() + " " + f.getAbsolutePath());
			}
		}
	}
	
	//Getters
	
	//Setters
	
}
