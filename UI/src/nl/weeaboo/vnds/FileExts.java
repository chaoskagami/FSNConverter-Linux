package nl.weeaboo.vnds;

import java.io.File;
import java.io.IOException;

import nl.weeaboo.settings.INIFile;

public final class FileExts {
	
	private static String DEFAULT_MUSIC = "mp3";
	private static String DEFAULT_SOUND = "aac";
	private static String DEFAULT_VOICE = "aac";
	
	public static final FileExts ANDROID = new FileExts("ogg", "ogg", "ogg");
	
	public final String music;
	public final String sound;
	public final String voice;

	public FileExts() {
		this(DEFAULT_MUSIC, DEFAULT_SOUND, DEFAULT_VOICE);
	}
	public FileExts(String m, String s, String v) {
		this.music = m;
		this.sound = s;
		this.voice = v;
	}
	
	public static FileExts fromFile(File file) throws IOException {
		INIFile iniFile = new INIFile();
		iniFile.read(file);
		return new FileExts(
			iniFile.getString("music", DEFAULT_MUSIC),
			iniFile.getString("sound", DEFAULT_SOUND),
			iniFile.getString("voice", DEFAULT_VOICE)
		);
	}
	
	public void write(File file) throws IOException {
		INIFile iniFile = new INIFile();
		iniFile.put("music", music);
		iniFile.put("sound", sound);
		iniFile.put("voice", voice);
		iniFile.write(file);
	}
	
}
