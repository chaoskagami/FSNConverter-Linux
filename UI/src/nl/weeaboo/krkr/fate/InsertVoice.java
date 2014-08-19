package nl.weeaboo.krkr.fate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Hashtable;
import java.util.Map;
import java.util.Map.Entry;

import nl.weeaboo.io.FileUtil;
import nl.weeaboo.vnds.Log;

public class InsertVoice {

	//Functions
	public void patch(String folderJ, String encodingJ, String folderE, String encodingE) {
		Map<String, File> filesJ = new Hashtable<String, File>();
		FileUtil.collectFiles(filesJ, new File(folderJ), false);
		
		Map<String, File> filesE = new Hashtable<String, File>();
		FileUtil.collectFiles(filesE, new File(folderE), false);
		
		for (Entry<String, File> entry : filesE.entrySet()) {
			File jFile = filesJ.get(entry.getKey());
			if (jFile != null) {
				try {
					patchFile(jFile, encodingJ, entry.getValue(), encodingE);
				} catch (IOException e) {
					Log.e("Error patching voices", e);
				}
			}
		}
	}
	
	protected void patchFile(File fileJ, String encodingJ, File fileE, String encodingE) throws IOException {
		BufferedReader japIn = new BufferedReader(new InputStreamReader(new FileInputStream(fileJ), encodingJ));
		BufferedReader engIn = new BufferedReader(new InputStreamReader(new FileInputStream(fileE), encodingE));
		
		
		StringBuilder sb = new StringBuilder();
		
		String lineEng;
		String lineJap;
		
		int engPage = -1;
		int japPage = -1;
		
		while ((lineEng = engIn.readLine()) != null) {
			if (lineEng.startsWith("@say")) {
				//sb.append(l + ":");
				sb.append(lineEng);				
				sb.append('\n');
				//sb.append("------------------------------------------------------\n");					
			} else if (engPage >= japPage) {
				do {
					int t = 0;
					boolean append = false;
					while ((lineJap = japIn.readLine()) != null) {
						japPage = Math.max(japPage, pageFromLine(lineJap));
						
						char firstChar = '\0';
						char lastChar = '\0';
						String temp = stripTags(lineJap);
						if (temp.length() > 0) {						
							firstChar = temp.charAt(0);
							lastChar = temp.charAt(temp.length()-1);
						}
												
						if (t == 0) {
							//sb.append(l + ":");
							sb.append(lineJap);
							sb.append('\n');
						}
	
						if (firstChar == 0x40 || firstChar == 0x2A ) {
							break;
						} else if (t == 0 || (t > 0 && ((firstChar != 0x3000 && firstChar != 0x300C) || append))) {
							if (t > 0) {
								//sb.append(l + ":");
								sb.append(lineJap);
								sb.append('\n');
							}
							
							append = (lastChar == 0x3001);
							japIn.mark(1024);
							t++;
						} else {
							break;
						}
					}
					if (t > 0) {
						japIn.reset();
					}
					//sb.append("------------------------------------------------------\n");					
					//System.out.println(engPage + " " + japPage);
				} while (engPage > japPage);
			}
			engPage = Math.max(engPage, pageFromLine(lineEng));
		}
		
		japIn.close();
		engIn.close();
		
		System.out.println("Inserting Voice Data: " + fileJ.getName());
		//fileJ = new File("e:/temp.txt");
		FileOutputStream fout = new FileOutputStream(fileJ);
		fout.write(sb.toString().getBytes(encodingJ));
		fout.flush();
		fout.close();
	}
	
	protected String stripTags(String s) {		
		return s.replaceAll("\\[[^\\]]\\]", "");
	}
	
	protected int pageFromLine(String line) {
		if (line.startsWith("*page")) {
			int index = 5;
			while (index < line.length() && Character.isDigit(line.charAt(index))) {
				index++;
			}
			try {
				return Integer.parseInt(line.substring(5, index));
			} catch (Exception e) {				
			}
		}
		return -1;
	}
	
	//Getters
	
	//Setters
}
