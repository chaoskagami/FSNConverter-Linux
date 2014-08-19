package nl.weeaboo.krkr;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import nl.weeaboo.collections.Tuple2;
import nl.weeaboo.vnds.Log;

public class MacroParser {
	
	private static final int maxImageCacheSize = 8;
	private boolean disableSprites = false;

	public static String R_FOREGROUND = "foreground/";
	public static String R_BACKGROUND = "background/";
	public static String R_SOUND = "sound/";

	protected KiriKiriConverter krkr;
	protected float scale;
	protected List<MacroHandler> macroHandlers;
	protected List<MacroHandler> textMacroHandlers;
	
	protected boolean checkForLayeringErrors = true;	
	protected boolean blackedOut;
	protected String currentBG;
	protected Sprite slots[] = new Sprite[10];
	
	private List<Tuple2<String, BufferedImage>> imageCache;

	public MacroParser(KiriKiriConverter krkr) {
		this.krkr = krkr;
		
		scale = 1f;
		macroHandlers = new ArrayList<MacroHandler>();
		textMacroHandlers = new ArrayList<MacroHandler>();
		imageCache = new LinkedList<Tuple2<String, BufferedImage>>();
	}
	
	//Functions
	public void addMacroHandler(MacroHandler mh) {
		macroHandlers.add(mh);
	}
	public void addTextMacroHandler(MacroHandler mh) {
		textMacroHandlers.add(mh);
	}
	
	public String flush(String filename, int lineNumber, String line) {
		List<String> lines = new ArrayList<String>();
		try {
			for (MacroHandler mh : macroHandlers) {
				String s = mh.flush();
				if (s != null && s.length() > 0) lines.add(s);
			}
		} catch (InvalidLayeringException ile) {
			krkr.addLayeringError(String.format("%s:%d ## %s", filename, lineNumber, line));
		}
		
		StringBuilder sb = new StringBuilder();
		for (String s : lines) {
			if (sb.length() > 0) {
				sb.append("\n");
			}
			sb.append(s);
		}
		return sb.toString();
	}
	
	public void reset() {		
		blackedOut = false;
		currentBG = "special/blackout.jpg";
		imageCache.clear();
		clearSlots();
		
		for (MacroHandler mh : macroHandlers) {
			mh.reset();
		}
		for (MacroHandler mh : textMacroHandlers) {
			mh.reset();
		}
	}
	
	public void clearSlots() {
		for (int n = 0; n < slots.length; n++) {
			slots[n] = null;
		}
	}

	public String restoreSlots(Sprite ss[]) {
		return restoreSlots(currentBG, ss);
	}
	public String restoreSlots(String newBG, final Sprite ss[]) {		
		boolean clash = false; //Is it necessary to clear the screen?
		
		/*
		for (int n = 0; n < slots.length; n++) {
			if (slots[n] != null) {
				if (ss[n] == null || !slots[n].equals(ss[n])) {
					clash = true;
					break;
				}			
			} else {
				if (ss[n] != null) {
					//If this sprite should be drawn underneath another sprite -> clash = true
					for (int i = 0; i < slots.length; i++) {
						//If should 
						if (slots[i] != null && ss[n].z < slots[i].z) {
							//Check if ranges on the x-axis overlap
							if ((slots[i].x >= ss[n].x && slots[i].x < ss[n].x + ss[n].w)
								|| (slots[i].x + slots[i].w >= ss[n].x && slots[i].x + slots[i].w < ss[n].x + ss[n].w))
							{
								clash = true;
								break;
							}
						}
					}
				}
			}
		}
		*/
		clash = true;
				
		String pre = (disableSprites ? "#" : "");
		String text = "";				
		
		if (!blackedOut) {
			boolean hasNonNullSprites = false;
			for (Sprite s : ss) {
				if (s != null) {
					hasNonNullSprites = true;
					break;
				}
			}
			
			if (checkForLayeringErrors && currentBG.equals("special/blackout.jpg")
					&& hasNonNullSprites)
			{
				throw new InvalidLayeringException();
			}
			
			if (clash || !newBG.equals(currentBG)) {
				currentBG = newBG;
				text += pre + "bgload " + currentBG;			
			}		
			
			List<Integer> indexMapping = new ArrayList<Integer>();
			for (int n = 0; n < ss.length; n++) {
				indexMapping.add(n);
			}
			Collections.sort(indexMapping, new Comparator<Integer>() {
				public int compare(Integer i1, Integer i2) {
					if (ss[i1] == null) {
						return -1;
					} else if (ss[i2] == null) {
						return 1;
					}
					return (ss[i1].z > ss[i2].z ? 1 : (ss[i1].z == ss[i2].x ? 0 : -1));
				}
			});
			
			for (int x = 0; x < indexMapping.size(); x++) {
				int n = indexMapping.get(x);
				if (n < 0 || n >= slots.length) {
					continue;
				}
				
				if (clash || (ss[n] != null && !ss[n].equals(slots[n]))) {
					Sprite s = ss[n];
					if (s != null) {
						if (text.length() > 0) {
							text += "\n";
						}
						text += pre + "setimg " + s.image + " " + s.x + " " + s.y;
					}
				}
			}
		} else {
			text = "#blackedout";
		}
		
		slots = ss;		
		
		return text;
	}

	public String parseTextMacro(String filename, int lineNumber, String macro) {
		return parse(filename, lineNumber, 1, macro.substring(1, macro.length()-1));
	}
	public String parseMacro(String filename, int lineNumber, String line) {
		return parse(filename, lineNumber, 0, line.substring(1));
	}
	protected String parse(String filename, int lineNumber, int parseType, String line) {
		int index = line.indexOf(' ');
		if (index < 0) index = line.length();
		
		String macro = line.substring(0, index);
		
		Map<String, String> params = new HashMap<String, String>();
		
		boolean inQuotes = false;
		int mode = 0;		
		StringBuilder tempName = new StringBuilder();
		StringBuilder tempValue = new StringBuilder();
		for (int n = index + 1; n < line.length(); n++) {
			char c = line.charAt(n);
			
			if (mode == 0) {
				if (c == '=') {
					mode = 1;
				} else {
					tempName.append(c);
				}
			} else if (mode == 1 && !Character.isWhitespace(c)) {
				mode = 2;
			}			
			if (mode == 2) {
				if (inQuotes && c == '\"') {
					inQuotes = false;
				}
				if (tempValue.length() == 0 && c == '\"') {
					inQuotes = true;
				}
				
				if (inQuotes || !Character.isWhitespace(c)) {
					tempValue.append(c);
				}
				if ((!inQuotes && Character.isWhitespace(c)) || n >= line.length() - 1) {
					String value = tempValue.toString().trim();
					if (value.charAt(0) == '\"') {
						value = value.substring(1, value.length()-1);
					}
					
					params.put(tempName.toString().trim(), value);
					tempName.delete(0, tempName.length());
					tempValue.delete(0, tempValue.length());
					mode = 0;
					inQuotes = false;
				}
			}
		}
		
		Collection<MacroHandler> hs = (parseType == 1 ? textMacroHandlers : macroHandlers);
		for (MacroHandler handler : hs) {
			try {
				String result = handler.process(macro, params);
				if (result != null) {
					while (parseType != 1 && result.endsWith("\n")) {
						result = result.substring(0, result.length()-1);
					}
					return result;
				}
			} catch (InvalidLayeringException ile) {
				krkr.addLayeringError(String.format("%s:%d ## %s", filename, lineNumber, line));
				return "#hidden by layering";
			} catch (Exception e) {
				String error = String.format("Error Parsing line (%s:%d) ## %s :: %s", filename, lineNumber, line, e.toString());
				krkr.addParseError(error);
				Log.w(error);
			}
		}
		
		if (parseType == 1) {
			krkr.addUnhandledTextMacro(macro);
			return "";
		} else {
			krkr.addUnhandledMacro(macro);
			return "#" + line;
		}
	}
	
	public Sprite createSprite(String oldName, String newName, int pos, int z) throws IOException {
		oldName = oldName.toLowerCase();
		int x = 0;
		int y = 0;

		BufferedImage image = null;
		for (Tuple2<String, BufferedImage> entry : imageCache) {
			if (entry.x.equals(oldName)) {
				image = entry.y;
				break;
			}
		}
		if (image == null) {
			image = ImageIO.read(new File(krkr.getOutputFolder()+"/../foreground/"+oldName));
			while (imageCache.size() >= maxImageCacheSize) {
				imageCache.remove(0);
			}
			imageCache.add(Tuple2.newTuple(oldName, image));
		}
		
		int iw = Math.round(scale * image.getWidth());
		int ih = (int)Math.floor(scale * image.getHeight());
		//System.out.println(scale + " " + image.getWidth() + "x" + image.getHeight() + " -> " + iw + "x" + ih);
		
		y = 192 - ih;
		
		if (pos == 0) {
			x = (256 - iw) / 2;
		} else if (pos == 1) {
			x = 256*3/10 - iw/2;
		} else if (pos == 2) {
			x = 256*7/10 - iw/2;
		} else if (pos == 3) {
			x = 256/4 - iw/2;
		} else if (pos == 4) {
			x = 256*3/4 - iw/2;
		}					

		return new Sprite(x, y, z, newName, iw);
	}
	
	public int parsePosValue(String value) {
		int pos = 0;			

		if (value.equals("rc") || value.equals("rightcenter")) {
			pos = 2;
		} else if (value.equals("lc") || value.equals("leftcenter")) {
			pos = 1;
		} else if (value.startsWith("r")) {
			pos = 4;
		} else if (value.startsWith("l")) {
			pos = 3;
		} else if (value.equals("all")) {
			pos = -1;
		}
		return pos;
	}
	
	//Getters
	public String getCurrentBG() { return currentBG; }
	public Sprite[] getSlotsCopy() { return slots.clone(); }

	//Setters
	public String setBG(String s) {
		if (currentBG != null && currentBG.equals(s)) {
			return "";
		}
		
		blackedOut = false;		
		currentBG = s;
		clearSlots();
		return "bgload " + s;
	}
	public void setBlackedOut(boolean b) {
		blackedOut = b;
	}
	public void setScale(float s) {
		scale = s;
	}

}
