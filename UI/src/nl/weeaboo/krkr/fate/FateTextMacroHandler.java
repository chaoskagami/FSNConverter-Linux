package nl.weeaboo.krkr.fate;

import java.io.IOException;
import java.util.Map;

import nl.weeaboo.krkr.KiriKiriConverter;
import nl.weeaboo.krkr.MacroHandler;
import nl.weeaboo.krkr.MacroParser;

public class FateTextMacroHandler extends MacroHandler {

	//TODO:
	//p    <- Wait for user click, without inserting a newline
	//ruby <- Read-Hint? Red Text?
	//vr   <- ???
	
	public FateTextMacroHandler(KiriKiriConverter krkr, MacroParser mp) {
		super(krkr, mp);
		
		String ignore[] = new String[] {
				"aero", "atlas", "keraino", "troya", "margos", "heart",
				"l", "r", 
		};
		
		for (String s : ignore) {
			ignore(s);
		}
	}

	//Functions
	public String process(String macro, Map<String, String> params) throws IOException {
		if (macro.equals("wrap")) {
			return "";
		} else if (macro.equals("szlig")) {
			return String.valueOf(Character.toChars(0x00DF));
		} else if (macro.equals("XAuml")) {
			return String.valueOf(Character.toChars(0x00C4));
		} else if (macro.equals("XOuml")) {
			return String.valueOf(Character.toChars(0x00D6));
		} else if (macro.equals("XUuml")) {
			return String.valueOf(Character.toChars(0x00DC));
		} else if (macro.equals("auml")) {
			return String.valueOf(Character.toChars(0x00E4));
		} else if (macro.equals("ouml")) {
			return String.valueOf(Character.toChars(0x00F6));
		} else if (macro.equals("uuml")) {
			return String.valueOf(Character.toChars(0x00FC));
		} else if (macro.equals("wacky")) {
			return parse_wacky(params);			
		} else if (macro.equals("block")) {
			return parse_block(params);			
		} else if (macro.startsWith("line")) {
			return parse_line(macro, params);
		} else if (macro.startsWith("ruby")) {
			return parse_ruby(macro, params);
		}
				
		return super.process(macro, params);
	}

	private String parse_block(Map<String, String> params) {
		int len = Integer.parseInt(params.get("len"));		
		return KiriKiriConverter.repeatString("-", len);
	}

	private String parse_wacky(Map<String, String> params) {
		int len = Integer.parseInt(params.get("len"));
		len = Math.min(6, len);
		
		return "R" + KiriKiriConverter.repeatString("O", len)
			+ KiriKiriConverter.repeatString("A", Math.max(1, len/2)) + "R";
	}
	
	private String parse_line(String macro, Map<String, String> params) {
		int num = Integer.parseInt(macro.substring(4));			
		return KiriKiriConverter.repeatString("-", num * 2);		
	}
	
	private String parse_ruby(String macro, Map<String, String> params) {
		return ""; //params.get("text");
	}
	
	//Getters
	
	//Setters
	
}
