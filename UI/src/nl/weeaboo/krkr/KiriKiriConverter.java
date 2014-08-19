package nl.weeaboo.krkr;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import nl.weeaboo.common.Dim;
import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.settings.INIFile;
import nl.weeaboo.vnds.FileExts;
import nl.weeaboo.vnds.FileMapper;
import nl.weeaboo.vnds.Log;
import nl.weeaboo.vnds.Patcher;

public class KiriKiriConverter {
	
	private String sourceFileEncoding = "UTF-16";
	protected boolean showOutput = false;
	protected boolean ignoreText = false;
	
	//Patching
	protected Patcher patcher;
	protected Map<String, String> appendMap;
	protected Map<String, Map<Integer, String>> patchPreMap;
	protected Map<String, Map<Integer, String>> patchPostMap;
	
	private File scriptFolder;
	private File outFolder;
	private File infoFolder;
	
	private int imageW, imageH;
	protected MacroParser macroParser;
	protected final FileExts fileExts;
	
	private FileMapper filenameMapper;
	private Set<File> scriptFilesWritten;
	private Set<String> unhandledTextMacros;
	private Set<String> unhandledMacros;
	private List<String> parseErrors;
	private List<String> layeringErrors;
		
	public KiriKiriConverter(File srcF, File scriptF, File dstF) {
		this.scriptFolder = scriptF;
		this.outFolder = new File(dstF, "script");
		this.infoFolder = new File(dstF, "_info");

		Dim d = new Dim(256, 192);
		try {
			INIFile imgIni = new INIFile();
			imgIni.read(new File(dstF, "img.ini"));
			d = new Dim(imgIni.getInt("width", d.w),
					imgIni.getInt("height", d.h));
		} catch (IOException ioe) {
			Log.w("Error reading img.ini", ioe);
		}		
		imageW = d.w;
		imageH = d.h;
		
		FileExts exts;
		try {
			exts = FileExts.fromFile(new File(dstF, "exts.ini"));
		} catch (IOException e) {
			//Ignore
			exts = new FileExts();
		}
		fileExts = exts;
		
		macroParser = new MacroParser(this);
	}
	public void convert() {		
		float scale = Math.min(256f/imageW, 192f/imageH);
		macroParser.setScale(scale);

		outFolder.mkdirs();
		infoFolder.mkdirs();
		
		appendMap = new HashMap<String, String>();
		patchPreMap = new HashMap<String, Map<Integer, String>>();
		patchPostMap = new HashMap<String, Map<Integer, String>>();
		
		patcher = createPatcher();
		patcher.patchPre(patchPreMap);
		patcher.patchPost(patchPostMap);
		patcher.fillAppendMap(appendMap);
		
		filenameMapper = new FileMapper();
		scriptFilesWritten = new TreeSet<File>();
		unhandledTextMacros = new TreeSet<String>();
		unhandledMacros = new TreeSet<String>();
		parseErrors = new ArrayList<String>();
		layeringErrors = new ArrayList<String>();
						
		try {
			filenameMapper.load(infoFolder+"/filenames.txt");
		} catch (IOException e) { }
		
		Map<String, File> scriptFolderContents = new TreeMap<String, File>();
		FileUtil.collectFiles(scriptFolderContents, scriptFolder, false);
		for (int n = 1; n <= getNumberOfPasses(); n++) {
			for (Entry<String, File> entry : scriptFolderContents.entrySet()) {
				scriptConvert(n, entry.getKey(), entry.getValue());
			}
		}
		
		//Generate script files for each file in the append map, so appending to a non-existing file
		//generates a new file.
		for (Entry<String, String> entry : appendMap.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			if (value == null) {
				continue;
			}
			
			if (!scriptFilesWritten.contains(new File(createOutputPath(key)))) {
				try {
					LinkedList<String> list = new LinkedList<String>();
					list.add(value);
					writeScript(list, key);
				} catch (IOException ioe) {
					ioe.printStackTrace();
				}
			}
		}

		try { filenameMapper.save(infoFolder+"/filenames.txt"); } catch (IOException e) { Log.w("Exception", e); }
		try { saveStringSet(unhandledTextMacros, infoFolder+"/unhandled_textmacro.txt"); } catch (IOException e) { Log.w("Exception", e); }
		try { saveStringSet(unhandledMacros, infoFolder+"/unhandled_macro.txt"); } catch (IOException e) { Log.w("Exception", e); }
		try { saveStringSet(parseErrors, infoFolder+"/parse_errors.txt"); } catch (IOException e) { Log.w("Exception", e); }
		try { saveStringSet(layeringErrors, infoFolder+"/layering_errors.txt"); } catch (IOException e) { Log.w("Exception", e); }
	}
	
	//Functions	
	protected static void saveStringSet(Collection<String> strings, String filename) throws IOException {
		BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(filename), 512*1024);
		try {
			for (String s : strings) {
				fout.write(s.getBytes("UTF-8"));
				fout.write('\n');
			}
			fout.flush();
		} finally {
			fout.close();
		}
	}
	
	protected Patcher createPatcher() {
		return new Patcher();
	}
	
	protected void scriptConvert(int pass, String relpath, File file) {				
		if (pass == getNumberOfPasses() && file.getName().endsWith("ks")) {
			try {
				processKSFile(file, file.getName());
			} catch (IOException e) {
				Log.w("Exception during script convert", e);
			}
		}
	}
	protected void processKSFile(File file, String patchMapKey) throws IOException {		
		Map<Integer, String> patchPreMap = this.patchPreMap.remove(patchMapKey);
		if (patchPreMap == null) patchPreMap = new HashMap<Integer, String>();
		Map<Integer, String> patchPostMap = this.patchPostMap.remove(patchMapKey);
		if (patchPostMap == null) patchPostMap = new HashMap<Integer, String>();
		
		List<String> list = new ArrayList<String>();
		BufferedReader in = new BufferedReader(new InputStreamReader(
				new FileInputStream(file), getSourceFileEncoding()));

		macroParser.reset();
		
		String line;
		int t = 1;
		while ((line = in.readLine()) != null) {
			if (patchPreMap.get(t) != null) {
				line = patchPreMap.get(t);
			}
			
			if (patchPostMap.get(t) != null) {
				list.add(macroParser.flush(file.getName(), t, line));
				list.add(patchPostMap.get(t));
			} else if (line.startsWith("@")) {
				//Macro
				String val = macroParser.parseMacro(file.getName(), t, line);
				if (val != null && val.length() > 0) {
					list.add(val);
				}
			} else {				
				if (line.startsWith("*") || line.startsWith(";")) {
					//Label or comment
					list.add("#" + line);
				} else {
					//Text
					String parsed = parseText(file.getName(), t, line);
					if (parsed != null) {
						list.add((ignoreText ? "#" : "") + parsed);
					}
				}
			}						
			t++;
		}
		in.close();

		String appendData = appendMap.remove(patchMapKey);
		//System.out.println(file.getName() + " " + patchMapKey + " " + (appendData != null ? appendData.length() : 0));
		if (appendData != null) {
			list.add(appendData);
		}
		
		//Write to disc
		writeScript(list, file.getName());				
	}

	protected void writeScript(List<String> list, String filename) throws IOException {
		String path = createOutputPath(filename);
		
		File outFile = new File(path);
		scriptFilesWritten.add(outFile);
		outFile.getParentFile().mkdirs();
		
		BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(outFile), 512*1024);
		for (String s : list) {
			byte bytes[] = s.getBytes("UTF-8");
			if (bytes.length > 0 && bytes[0] != '#') {
				int start = 0;
				while (start < bytes.length) {
					if (bytes[start] != ' ' || bytes[start] != '\t' || bytes[start] != '\n') {
						break;
					}
					start++;
				}				
				if (start < bytes.length) {
					int end = bytes.length;
					while (end > start) {
						if (bytes[end-1] != ' ' || bytes[end-1] != '\t' || bytes[end-1] != '\n') {
							break;
						}
						end--;
					}
					
					if (end-start > 0) {
						out.write(bytes, start, end-start);
						out.write('\n');
					}
				}
			}
		}
				
		out.flush();
		out.close();
		
		if (showOutput) {
			System.out.println("Writing: " + path);
		}
	}
	
	protected String parseText(String filename, int lineNumber, String line) {		
		//Save+Remove macros
		int index = 0;
		while ((index = line.indexOf('[')) >= 0) {
			int index2 = index+1;
			boolean inQuotes = false;
			while (index2 < line.length()) {
				if (line.charAt(index2) == '\"') {
					inQuotes = !inQuotes;
				} else if (line.charAt(index2) == '\\') {
					index2++;
				} else if (line.charAt(index2) == ']') {
					if (!inQuotes) {
						index2++;
						break;
					}
				}
				index2++;
			}
			
			String macro = line.substring(index, index2);
			String insert = macroParser.parseTextMacro(filename, lineNumber, macro);

			line = line.substring(0, index) + insert + line.substring(index + macro.length(), line.length());
			index += insert.length();
		}

		//line = line.replaceAll("\\[([^\\[])*?\\]", "");
		return line.trim();
	}

	public String addRes(String prefix, String filename) {
		return filenameMapper.add(prefix+filename.toLowerCase());
	}
	
	protected String createOutputPath(String filename) {
		return new File(outFolder, StringUtil.stripExtension(filename) + ".scr").getAbsolutePath();		
	}
	
	public static String repeatString(String pattern, int times) {
		StringBuilder sb = new StringBuilder();
		for (int n = 0; n < times; n++) {
			sb.append(pattern);
		}
		return sb.toString();
	}
	
	public void addUnhandledTextMacro(String macro) {
		unhandledTextMacros.add(macro);
	}	
	public void addUnhandledMacro(String macro) {
		unhandledMacros.add(macro);
	}	
	public void addParseError(String errorString) {
		parseErrors.add(errorString);
	}
	public void addLayeringError(String errorString) {
		layeringErrors.add(errorString);
	}
	
	//Getters
	public String getSourceFileEncoding() { return sourceFileEncoding; }
	public File getScriptFolder() { return scriptFolder; }
	public File getOutputFolder() { return outFolder; }
	public File getInfoFolder() { return infoFolder; }
	public int getNumberOfPasses() { return 1; }
	public int getImageW() { return imageW; }
	public int getImageH() { return imageH; }

	//Setters
	public void setSourceFileEncoding(String enc) {
		this.sourceFileEncoding = enc;
	}
	
	//Append Map
	public String createJump(String... options) {
		StringBuilder sb1 = new StringBuilder("choice ");
		StringBuilder sb2 = new StringBuilder();
		
		int t = 1;
		for (String s : options) {
			String part[] = s.split("\\|");
			if (t > 1) {
				sb1.append("|");
			}
			sb1.append(part[0]);
			
			sb2.append("if selected == " + t + "\n");
			sb2.append("    jump " + part[1] + "\n");
			sb2.append("fi\n");
			t++;
		}
		return sb1.toString() + "\n" + sb2.toString();
	}
}
