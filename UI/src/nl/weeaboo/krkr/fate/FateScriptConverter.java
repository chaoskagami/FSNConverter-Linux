package nl.weeaboo.krkr.fate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;

import nl.weeaboo.io.FileUtil;
import nl.weeaboo.krkr.KiriKiriConverter;
import nl.weeaboo.vnds.Log;
import nl.weeaboo.vnds.Patcher;

public class FateScriptConverter extends KiriKiriConverter {
	
	//-----------------------------------------------------
	//Fixed after 1.0 release:
	//-----------------------------------------------------
	// * Removed a few lines where KrKr commands were printed in the .scr files
	// * Blank lines removed from EN edition
	// * Updated English translation to version 3.2
	// - 1.1.4 --------------------------------------------
	// * Reduced XP3 extractor memory usage
	// * UTF-8 support for the conversion GUI log
	// * Other memory reductions
	
	public enum Language {
		EN("en", "UTF-16"), JA("ja", "SJIS"), CH("ch", "UTF-16LE");
		
		private String encoding;
		private String langCode;
		
		private Language(String langCode, String encoding) {
			this.langCode = langCode;
			this.encoding = encoding;
		}
		
		public String getEncoding() { return encoding; }
		public String getLangCode() { return langCode; }
	};
	
	private boolean insertVoiceData;
	private Language lang;
	
	private int allowedRoutes = 4; //1=Prologue, 2=P+Fate, 3=P+F+UBW, 4=P+F+UBW+HF	
	private RouteParser routeParser;
	private boolean soundPlaying;
	
	public FateScriptConverter(File srcF, File dstF, String language, boolean insertVoice) {
		super(srcF, new File(srcF, "data"), dstF);
				
		lang = Language.valueOf(language);
		insertVoiceData = insertVoice; 
		
		if (insertVoiceData) {
			if (lang == Language.CH) {
				System.err.println("Voice support for the Chinese language is not available");
			} else {			
				//Inserts the @say commands into the Japanese version
				//  -- don't run this on the same file more than once
				
				/*new InsertVoice().patch(getRootFolder() + "/scenario-" + lang.getLangCode(),
						lang.getEncoding(), getRootFolder() + "/scenario-" + Language.EN.getLangCode(),
						Language.EN.getEncoding());
				*/
				throw new RuntimeException("TODO: Fix voice insertion");
			}
		}			

		setSourceFileEncoding(lang.getEncoding());			
		
		macroParser.addMacroHandler(new FateMacroHandler(this, macroParser, fileExts));
		macroParser.addTextMacroHandler(new FateTextMacroHandler(this, macroParser));
	}
	
	//Functions
	public static void main(String args[]) {
		File srcF = new File(args[0]);
		File dstF = new File(args[1]);
		String lang = args[2];
		
		Log.v("Converting scripts...");
		FateScriptConverter converter = new FateScriptConverter(srcF, dstF, lang, false);
		converter.convert();
	}
	
	protected Patcher createPatcher() {
		return new FatePatcher(lang);
	}

	protected String createOutputPath(String filename) {
		return super.createOutputPath(RouteParser.scenarioFileRename(this, filename));
	}
	
	public void convert() {
		routeParser = new RouteParser(this);
		
		super.convert();
	}
	
	protected void scriptConvert(int pass, String relpath, File file) {				
		if (pass == 1 && file.getName().endsWith("fcf")) {
			if (!relpath.contains("/")) {
				//If any subfolders exist, it's because the files were extracted on top of
				//an aborted earlier extraction.
				routeParser.parse(file, appendMap);
			}
		} else if (pass == 2 && file.getName().endsWith("ks")) {
			try {
				if (!routeParser.isIncludedInThisRun(file.getName())) {
					return;
				}
				processKSFile(file);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	protected void processKSFile(File file) throws IOException {
		soundPlaying = false;
		
		super.processKSFile(file, RouteParser.scenarioFileRename(this, file.getName())+".ks");
		
		if (lang != Language.EN) {
			//Postprocess: remove blank lines and merge lines that belong to a single sentence

			StringBuilder sb = new StringBuilder();
			StringBuilder string = new StringBuilder();
			
			String path = createOutputPath(file.getName());
			BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
			String line;
			while ((line = in.readLine()) != null) {				
				if (line.startsWith("text ")) {
					String textPart = line.substring(5).trim();					

					if (textPart.length() > 0 && textPart.charAt(0) == 0x3000) {
						if (string.length() > 0) {
							sb.append("text ");
							sb.append(string);
							sb.append("\n");
							string.delete(0, string.length());
						}
					}
					string.append(textPart);
				} else {
					if (string.length() > 0) {
						sb.append("text ");
						sb.append(string);
						sb.append("\n");
						string.delete(0, string.length());
					}
					
					sb.append(line);
					sb.append("\n");
				}
			}
			in.close();
			
			FileUtil.write(new File(path), sb.toString());
		}
	}
	
	protected String parseText(String filename, int lineNumber, String line) {
		String soundAppend = "";
		if (lang == Language.EN && soundPlaying) {
			//soundAppend = "\nsound ~";
			//soundPlaying = false;
		}
		
		String result = macroParser.flush(filename, lineNumber, line);
		return result + (result.length() > 0 ? "\n" : "") + "text "
			+ super.parseText(filename, lineNumber, line) + soundAppend;
	}
	
	//Getters
	public int getAllowedRoutes() { return allowedRoutes; }
	public boolean isSoundPlaying() { return soundPlaying; }
	public Language getLanguage() { return lang; }
	public int getNumberOfPasses() { return 2; }
	
	//Setters
	public void setSoundPlaying(boolean sp) { this.soundPlaying = sp; }
}
