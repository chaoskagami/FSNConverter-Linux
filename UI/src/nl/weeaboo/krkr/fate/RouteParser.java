package nl.weeaboo.krkr.fate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Map;

import nl.weeaboo.common.StringUtil;

public class RouteParser {
	
	private FateScriptConverter fateConv;
	
	public RouteParser(FateScriptConverter fc) {
		this.fateConv = fc;
	}
	
	//Functions
	public void parse(File file, Map<String, String> appendMap) {
		try {
			BufferedReader in = new BufferedReader(new InputStreamReader(
					new FileInputStream(file), fateConv.getSourceFileEncoding()));

			String filenameNoExt = StringUtil.stripExtension(file.getName());
			if (!isIncludedInThisRun(filenameNoExt)) {
				return;
			}
			
			String text;
			while ((text = in.readLine()) != null) {
				parse(scenarioFileRename(fateConv, filenameNoExt), text, appendMap);
			}
			
			in.close();			
		} catch (Exception e) {
			System.err.println(file.getAbsolutePath());
			e.printStackTrace();
		}
	}
	
	protected void parse(String filenameNoExt, String line, Map<String, String> appendMap) {
		if (line.indexOf(';') < 0) {
			return;
		}
				
		String primeSplit[] = line.split(";");
		int sceneNum = Integer.parseInt(primeSplit[0]);
		String pm[] = primeSplit[1].split("\'");
		
		if (pm[0].equals("SCENE")) {
			StringBuilder sb = new StringBuilder();

			//String title = pm[10];			
			int numberOfOperations = Integer.parseInt(pm[11]);
			
			//First do the operations...
			String last = pm[12 + numberOfOperations];
			if (!last.equals("0")) {
				String pm2[] = last.split(":");
				
				int numberOfOperations2 = Integer.parseInt(pm2[0]);
				for (int j = 0; j < numberOfOperations2; j++) {
					sb.append(new FlowFlag(pm2[1+j], "o").operate()+"\n");
				}
			}
			
			//..and then jump, not the other way around :(
			for (int j = 0; j < numberOfOperations; j++) {
				String parts[] = pm[12+j].split(":");
				
				int numberOfPaths = Integer.parseInt(parts[0]);
				int link = Integer.parseInt(parts[numberOfPaths + 1]); //1=Logical AND, 2=Logical OR
				int target = Integer.parseInt(parts[numberOfPaths + 2]);

				String jumpStr = "jump " + getScript(filenameNoExt, target) + "\n";
				if (link == 1) {
					String spaces = "";
					for (int i = 0; i < numberOfPaths; i++) {							
						sb.append(spaces);
						sb.append("if ");
						sb.append(new FlowFlag(parts[1+i], "d").decide());
						sb.append("\n");
						spaces += "  ";
					}
					sb.append(spaces + jumpStr);
					for (int i = 0; i < numberOfPaths; i++) {
						sb.append(spaces.substring(Math.min(spaces.length(), 2 * (i+1))));
						sb.append("fi\n");
					}						
				} else {
					for (int i = 0; i < numberOfPaths; i++) {
						sb.append("if ");
						sb.append(new FlowFlag(parts[1+i], "d").decide());
						sb.append("\n");
						sb.append("  " + jumpStr + "\n");
						sb.append("fi\n");
					}
				}
			}
			if (numberOfOperations == 0) {
				sb.append("jump main.scr\n");
			}
			
			appendMap.put(getAppendKey(filenameNoExt, sceneNum), sb.toString());
		} else if (pm[0].equals("SELECTER")) {
			//String title = pm[10];
			int numberOfOperations = Integer.parseInt(pm[11]);
			
			String choices[] = new String[numberOfOperations];
			for (int j = 0; j < numberOfOperations; j++) {
				//Choice Display Text|scene number
				choices[j] = pm[12 + j*3 + 2] + "|" + getScript(filenameNoExt, Integer.parseInt(pm[12+j*3]));
			}
			
			String string = "jump main.scr\n";
			if (numberOfOperations > 0) {
				string = fateConv.createJump(choices);
			}
			
			appendMap.put(getAppendKey(filenameNoExt, sceneNum), string);
		} else if (pm[0].equals("OUTERLABEL")) {
			//String title = (pm.length > 12 ? pm[12] : "");
			String file = scenarioFileRename(fateConv, pm[10]);
			
			String jump;
			if (file != null) {			
				int target = Integer.parseInt(pm[11]);			
				jump = "jump " + getScript(file, target) + "\n";
			} else {
				jump = "text <ERROR>\njump special/ulw.scr\n";
				throw new RuntimeException("RouteParser :: Error parsing: " + filenameNoExt + " (" + pm[10] + ")");
			}
			appendMap.put(getAppendKey(filenameNoExt, sceneNum), jump);
		}
	}
	
	public boolean isIncludedInThisRun(String filename) {
		filename = scenarioFileRename(fateConv.getAllowedRoutes(), filename);
		
		String prefixes[] = new String[] {"prologue", "fate", "ubw", "hf"};
		for (int n = 0; n < fateConv.getAllowedRoutes(); n++) {
			if (filename.startsWith(prefixes[n])) {
				return true;
			}
		}
		return false;
	}
	public static String scenarioFileRename(FateScriptConverter fc, String file) {
		return scenarioFileRename(fc.getAllowedRoutes(), file);
	}
	public static String scenarioFileRename(int allowedRoutes, String file) {
		file = StringUtil.stripExtension(file);
		
		String replace[] = new String[] {
				"プロローグ",       "prologue",
				"セイバールート",    "fate",
				"セイバーエピローグ", "fate-ending",
				"凛ルート",        "ubw",
				"凛エピローグ",     "ubw-ending",
				"桜ルート",        "hf",
				"桜エピローグ",     "hf-ending",
		};
		int L = allowedRoutes;
		if (L >= 4) L++;
		if (L >= 3) L++;
		if (L >= 2) L++;
				
		int n;
		for (n = 0; n < L * 2; n+=2) {
			if (file.startsWith(replace[n])) {
				String r = scenarioFileRename2(file.substring(replace[n].length(), file.length()));
				file = replace[n+1];
				if (r != null) file += r;				
				return file;
			}
		}
		return file;
	}
	private static String scenarioFileRename2(String part) {
		String replace[] = new String[] {
				"1日目", 	"00",
				"2日目", 	"01",
				"3日目", 	"02",
				"一日目",   "01",
				"二日目",   "02",
				"三日目",   "03",
				"四日目",   "04",
				"五日目",   "05",
				"六日目",   "06",
				"七日目",   "07",
				"八日目",   "08",
				"九日目",   "09", 
				"十日目",   "10",
				"十一日目", "11",
				"十二日目", "12",
				"十三日目", "13",
				"十四日目", "14",
				"十五日目", "15",
				"十六日目", "16"
		};
		
		for (int n = replace.length-2; n >= 0; n -= 2) {
			int index = part.indexOf(replace[n]);
			if (index >= 0) {
				part = part.substring(0, index) + replace[n+1] +
					part.substring(index+replace[n].length());
			}
		}
		return part;
	}
	
	//Getters
	private String getAppendKey(String filenameNoExt, int target) {
		return filenameNoExt + "-" + (target<10?"0":"") + target + ".ks";
	}
	private String getScript(String filenameNoExt, int target) {
		return filenameNoExt + "-" + (target<10?"0":"") + target + ".scr";
	}
	
	//Setters
	
	//Inner Classes
	private static class FlowFlag {
		
		String name;
		int value0, value1;

		public FlowFlag(String str, String type) {
			try {
				int pos;
				name = str.substring(0, pos = str.indexOf("//"));
				str = str.substring(pos+2);
				value0 = Integer.parseInt(str.substring(0, pos = str.indexOf("//")));
				str = str.substring(pos+2);
				value1 = Integer.parseInt(str);			
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public String decide() {
			String operators[] = new String[] {"==", "!=", "<", ">", "<=", ">="};
			return name + " " + operators[value0] + " " + value1;
		}

		public String operate() {
			String command = "setvar";
			if (name.startsWith("g")) {
				command = "gsetvar";
			}
			
			if (value1 == 1) { //Add
				return command + " " + name + " + " + value0;
			} else if (value1 == 2) { //Substract
				return command + " " + name + " - " + value0;
			} else if (value1 == 3) { //Set
				return command + " " + name + " = " + value0;
			}
			return "#Error in FlowFlag.operate()";
		}

	}

}
