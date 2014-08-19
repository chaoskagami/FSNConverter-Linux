package nl.weeaboo.krkr.fate;

import static nl.weeaboo.krkr.MacroParser.R_BACKGROUND;
import static nl.weeaboo.krkr.MacroParser.R_FOREGROUND;
import static nl.weeaboo.krkr.MacroParser.R_SOUND;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import nl.weeaboo.krkr.MacroHandler;
import nl.weeaboo.krkr.MacroParser;
import nl.weeaboo.krkr.Sprite;
import nl.weeaboo.vnds.FileExts;

public class FateMacroHandler extends MacroHandler {
		
	public static final int DEFAULT_Z = 1000;

	protected final FileExts fileExts;
	
	protected FateScriptConverter fc;
	protected Sprite sprites[];
	protected boolean spriteFlushNeeded;
	protected int soundPlayingLength;
	
	public FateMacroHandler(FateScriptConverter fc, MacroParser mp, FileExts exts) {
		super(fc, mp);
		
		this.fc = fc;		
		this.fileExts = exts;
		
		String ignore[] = new String[] {
				//Inline
				"aero", "atlas", "keraino", "troya", "margos", "heart",
				"l", "r",
				
				//Normal
				"backlay", "broadencombo", "broadencomboT",
				"canseeStatusMenu", "canSeeStatusMenu",
				"cinescoT", "cinesco_offT", "clickskip",
				"cm", //Called on the start of each day
				"condoff", "condoffT",
				"contrast", "contrastT", "contrastoff", "contrastoffT",
				"darken", "darkenoff", "darkenT", "darkenoffT",
				"dash", "dashcombo", "dashcomboT",
				"defocus", "delay", "displayedoff", "displayedon",
				"encountServant", "erasestaffroll",
				"flicker", "flickerT", "flushcombo", "foldcombo", "foldcomboT",
				"font", "haze", "hazeTrans", "hazetrans", "hearttonecombo",
				"image", "imageex", "image4demo", //Used for animations only
				"initabsolute", //???				
				"interlude_end", "interlude_in", "interlude_in_", "interlude_out", "interlude_out_", "interlude_start",
				"knowMasterName", "knowTrueName", "large", "layopt",
				"monocro", "monocroT", "move", "nega", "negaT",
				"nohaze_next",
				"noise", "noise_back", "noise_noback", "noiseT", "stopnoise", "stopnoiseT", //Overlays animated white noise on the background
				/*"pasttime", "pasttime_long",*/ //What do these do? <-- Some kind of background transition it seems
				"pgnl", "pgtg", "prickT", "quad",
				"quake", "quake_max", "quakeT",
				"r", //Inserts a newline
				"rclick", "redraw", "resetfont", "resetwait", "return", "rf",
				"sepia", "sepiaT", "shock", "shockT",
				"slideclosecomboT", /*"slideopencomboT",*/ "small",
				"smudge", "smudgeT", "smudgeoff", "smudgeoffT",
				"splinemovecombo", "splinemovecomboT", "staffrollsetting",
				"stophaze", "superpose", "superpose_off", "textoff", "texton",
				"tiger_start", "tiger_end", //Change font-style etc. to tiger-dojo style and back
				"touchimages", "trans", "transex_w", /*"turnaround",*/ "tvoffcomboT", "useSkill",
				"useSpecial", "useWeapon",
				"wait", "waitT", "waitn",
				"waveT", "whaze", "wm", "wq", "wshock", "wstaffroll", "wt", "zoomming"				
		};
		
		for (String s : ignore) {
			ignore(s);
		}
	}

	//Functions
	public void reset() {
		sprites = mp.getSlotsCopy();
		spriteFlushNeeded = false;
	}
	
	public String process(String macro, Map<String, String> params) throws IOException {
		StringBuilder result = new StringBuilder();
		
		if (macro.equals("pg")) {	
			if (fc.isSoundPlaying()) {
				result.append("sound ~\n");
				fc.setSoundPlaying(false);
			}
		}
		
		if (macro.startsWith("ldall")) {
			result.append(parse_ldall(params));
		} else if (macro.equals("ld") || macro.startsWith("ld_")) {
			result.append(parse_ld(params));
		} else if (macro.equals("cl") || macro.startsWith("cl_")) {
			result.append(parse_cl(params));
		} else {
			mp.setBlackedOut(false);

			if (macro.startsWith("i2") || macro.startsWith("a2") || macro.equals("bg")) {
				result.append(parse_bg(params));
			} else if (macro.equals("rep")) {
				result.append(parse_rep(params));
			} else if (macro.equals("fadein")) {
				result.append(parse_fadein(params));
			} else if (macro.equals("flushover")) {
				result.append(parse_flushover(params));
			} else if (macro.equals("black")) {
				result.append(parse_black(params));
			} else if (macro.startsWith("pasttime")) {
				result.append(parse_pasttime(params));
			} else if (macro.equals("blackout")) {
				result.append(parse_blackout(params));
			} else if (macro.equals("blue") || macro.equals("blueT")) {
				result.append(parse_blue(params));
			} else if (macro.equals("red") || macro.equals("redT")) {
				result.append(parse_red(params));
			} else if (macro.equals("white") || macro.equals("whiteT")) {
				result.append(parse_white(params));
			} else if (macro.equals("green") || macro.equals("greenT")) {
				result.append(parse_green(params));
			} else {
				//Flush sprites whenever a non-sprite, non-bg command is issued.
				
				result.append(flush());
				if (result.length() > 0) result.append("\n");			
			}
		}
		
		if (macro.equals("date_title")) {
			result.append(parse_date_title(params));
		} else if (macro.equals("l")) {
			result.append("text ");
		} else if (macro.equals("edoublecolumn")) {
			result.append(parse_edoublecolumn(params));
		} else if (macro.equals("approachTigerSchool")) {
			result.append(parse_approachTigerSchool(params));
		} else if (macro.equals("slideopencomboT")) {
			result.append(parse_slideopencomboT(params));
		} else if (macro.equals("turnaround")) {
			result.append(parse_turnaround(params));
		} else if (macro.startsWith("hearttonecombo")) {
			Map<String, String> p = new HashMap<String, String>();
			p.put("file", "se028");
			result.append(process("se", p));
		}
		
		//Audio Functions
		if (macro.startsWith("playstop") || macro.startsWith("playpause") || macro.startsWith("playresume")) {
			result.append("music ~");
		} else if (macro.equals("play") || macro.equals("play_")) {
			String filename = krkr.addRes(R_SOUND, params.get("file")+"."+fileExts.music);			
			result.append("music " + filename);		
		} else if (macro.startsWith("seloop")) {			
			String filename = krkr.addRes(R_SOUND, params.get("file")+"."+fileExts.sound);

			//Multiple concurrent music streams aren't supported (yet)
			//return "music " + filename;
			result.append("sound ~\nsound " + filename);		
		} else if (macro.equals("se") || macro.equals("se_")) {
			String filename = krkr.addRes(R_SOUND, params.get("file")+"."+fileExts.sound);			
			result.append(playSFX(params, params.get("file"), filename));
		} else if (macro.startsWith("sestop")) {
			result.append("sound ~");
		} else if (macro.equals("say") || macro.equals("lvoice")) { //@say in EN, JA, @lvoice in CH
			String fp = (macro.equals("say") ? params.get("n") : params.get("file"));
			
			if (macro.equals("say")) {
				String filename = krkr.addRes(MacroParser.R_SOUND, fp + "." + fileExts.voice);			
				result.append(String.format("sound %s", filename));
			} else {
				//Don't check if the file exists in CH version
				//Don't change the filename either
				result.append(String.format("sound %s.%s", fp, fileExts.voice));
			}
			fc.setSoundPlaying(true);		
		}
		
		if (result.length() > 0) {
			return result.toString();
		}
		
		return super.process(macro, params);
	}

	private String addSprite(int pos, Sprite sprite) {
		sprites[pos] = sprite;
		
		spriteFlushNeeded = true;
		return "";
	}
	public String flush() {
		if (spriteFlushNeeded) {
			spriteFlushNeeded = false;
			return mp.restoreSlots(sprites);
		}
		return "";
	}
	private String setBG(String filename) {
		clearSlots();
		return mp.setBG(filename);
	}
	private void clearSlots() {
		mp.clearSlots();
		sprites = mp.getSlotsCopy();
		spriteFlushNeeded = false;
	}
	private String playSFX(Map<String, String> params, String oldFilename, String newFilename) throws IOException {
		File file = new File(krkr.getOutputFolder() + "/../sound/" + oldFilename + "." + fileExts.sound);		
		int waitTime = Math.min(60, Math.max(0, (int)Math.ceil(60f * (file.length()-4) / 11025f)-6));

		int wait = (fc.isSoundPlaying() ? soundPlayingLength : 0);
		soundPlayingLength = waitTime;
		fc.setSoundPlaying(true);
		return String.format("delay %d\nsound %s", wait, newFilename);		
	}

	private String parse_ldall(Map<String, String> params) throws IOException {
		clearSlots();
		spriteFlushNeeded = true;
		
		if (params.containsKey("l")) {
			String name = params.get("l")+".png";
			String newName = krkr.addRes(R_FOREGROUND, name);
			addSprite(3, mp.createSprite(name, newName, 3, readInt(params.get("il"), DEFAULT_Z)));
		}
		if (params.containsKey("lc")) {
			String name = params.get("lc")+".png";
			String newName = krkr.addRes(R_FOREGROUND, name);
			addSprite(1, mp.createSprite(name, newName, 1, readInt(params.get("ilc"), DEFAULT_Z)));
		}
		if (params.containsKey("c")) {
			String name = params.get("c")+".png";
			String newName = krkr.addRes(R_FOREGROUND, name);
			addSprite(0, mp.createSprite(name, newName, 0, readInt(params.get("ic"), DEFAULT_Z)));
		}
		if (params.containsKey("rc")) {
			String name = params.get("rc")+".png";
			String newName = krkr.addRes(R_FOREGROUND, name);
			addSprite(2, mp.createSprite(name, newName, 2, readInt(params.get("irc"), DEFAULT_Z)));
		}
		if (params.containsKey("r")) {
			String name = params.get("r")+".png";
			String newName = krkr.addRes(R_FOREGROUND, name);
			addSprite(4, mp.createSprite(name, newName, 4, readInt(params.get("ir"), DEFAULT_Z)));
		}

		return "";		
	}
	private String parse_ld(Map<String, String> params) throws IOException {
		int pos = mp.parsePosValue(params.get("pos"));
		
		String oldFilename = params.get("file")+".png";
		String filename = krkr.addRes(R_FOREGROUND, oldFilename);
		int z = readInt(params.get("index"), DEFAULT_Z);
		
		return addSprite(pos, mp.createSprite(oldFilename, filename, pos, z));
	}
	private String parse_bg(Map<String, String> params) {
		String filename = params.get("file")+".jpg";
		filename = krkr.addRes(R_BACKGROUND, filename);		
		return setBG(filename);
	}
	private String parse_fadein(Map<String, String> params) {
		//I think it should clear all sprites, not 100% sure though
		clearSlots();
		
		String filename = krkr.addRes(R_BACKGROUND, params.get("file")+".jpg");		
		return setBG(filename);
	}
	private String parse_rep(Map<String, String> params) {
		String filename = krkr.addRes(R_BACKGROUND, params.get("bg")+".jpg");		
		return setBG(filename);
	}
	private String parse_cl(Map<String, String> params) {
		int pos = mp.parsePosValue(params.get("pos"));			
		
		//If pos >= 0 delete 1 sprite, if < 0, delete them all
		if (pos >= 0) {
			sprites[pos] = null;
		} else {
			for (int n = 0; n < sprites.length; n++) {
				sprites[n] = null;
			}
		}
		spriteFlushNeeded = true;
		
		return "";
	}		
	private String parse_pasttime(Map<String, String> params) {
		clearSlots();
		return flash("special/blackout.jpg", 60 * 800 / 1000);
	}
	private String parse_flushover(Map<String, String> params) {
		return setBG("special/whiteout.jpg");
	}
	private String parse_black(Map<String, String> params) {
		return setBG("special/blackout.jpg");
	}
	private String parse_white(Map<String, String> params) {
		return parse_color_t("special/whiteout.jpg", params);
	}
	private String parse_red(Map<String, String> params) {
		return parse_color_t("special/redout.jpg", params);
	}
	private String parse_green(Map<String, String> params) {
		return parse_color_t("special/greenout.jpg", params);
	}
	private String parse_blue(Map<String, String> params) {
		return parse_color_t("special/blueout.jpg", params);
	}
	private String parse_color_t(String filename, Map<String, String> params) {
		if (params.get("time") != null) {
			return flash(filename, Integer.parseInt(params.get("time")) * 60 / 1000);
		} else {
			return setBG(filename);
		}		
	}
	private String flash(String filename, int delay) {
		String oldBG = mp.getCurrentBG();
		Sprite[] oldSlots = (sprites != null ? sprites.clone() : null);
		
		String result = setBG(filename) + "\ndelay " + delay;
		String append = setBG(oldBG);
		result += (append.length() > 0 ? "\n" : "") + append;
		
		sprites = oldSlots;
		spriteFlushNeeded = true;
		
		return result;
	}
	
	private String parse_blackout(Map<String, String> params) {
		mp.setBlackedOut(true);
		return "";
	}
	private String parse_date_title(Map<String, String> params) {
		String date = params.get("date");
		String month = date.substring(0, date.length()-2);
		if (month.length() <= 2) month = "0" + month;
		
		String day = date.substring(date.length()-2, date.length());
		return "text ~\ntext <DATE " + month + "/" + day + ">\ntext ~";
	}
	private String parse_edoublecolumn(Map<String, String> params) {
		String upper = params.get("upper").replaceAll("\\$", "");
		String lower = params.get("lower").replaceAll("\\$", "");
		
		return "text " + upper + "\ntext " + lower;
	}
	private String parse_approachTigerSchool(Map<String, String> params) {
		return "bgload special/blackout.jpg\ntext <BAD END>\n" +
			"bgload special/tigerdojo.jpg\ntext Welcome to Tiger Dojo.";		
	}
	private String parse_slideopencomboT(Map<String, String> params) {
		String filename = params.get("nextimage");
		filename = krkr.addRes(R_BACKGROUND, filename+".jpg");		
		return setBG(filename);
	}
	private String parse_turnaround(Map<String, String> params) {		
		String bg = mp.getCurrentBG();
		String temp = setBG("special/blackout.jpg");
		clearSlots();
		return temp + "\n" + setBG(bg);
	}
	
	//Getters
	
	//Setters
	
}
