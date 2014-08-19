package nl.weeaboo.krkr;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;


public class MacroHandler {

	protected KiriKiriConverter krkr;
	protected MacroParser mp;	
	protected Set<String> macroIgnoreList;
	
	public MacroHandler(KiriKiriConverter krkr, MacroParser mp) {
		this.krkr = krkr;
		this.mp = mp;
		
		macroIgnoreList = new HashSet<String>();
	}
	
	//Functions
	public void reset() {		
	}
	public String flush() {
		return "";
	}
	
	protected void ignore(String macro) {
		macroIgnoreList.add(macro);
	}
	
	public String process(String macro, Map<String, String> params) throws IOException {		
		if (macroIgnoreList.contains(macro)) {
			return "";
		}
		return null;
	}
	
	protected String macroToString(String macro, Map<String, String> params) {
		StringBuilder sb = new StringBuilder(macro);
		for (Entry<String, String> entry : params.entrySet()) {
			sb.append(" " + entry.getKey() + "=" + entry.getValue());
		}
		return sb.toString();
	}
	
	public int readInt(String s, int defaultValue) {
		try {
			return Integer.parseInt(s);
		} catch (Exception e) { }
		return defaultValue;
	}

	//Getters
	
	//Setters
	
}
