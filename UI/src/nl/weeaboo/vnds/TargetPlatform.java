package nl.weeaboo.vnds;

public enum TargetPlatform {
	NINTENDO_DS("Nintendo DS", 256, 192, false),
	ANDROID_QVGA("Android QVGA", 320, 240, true),
	ANDROID_HVGA("Android HVGA", 480, 320, true),
	ANDROID_WVGA("Android WVGA", 800, 480, true),
	ANDROID_WSVGA("Android WSVGA", 1024, 600, true),
	ANDROID_WXGA("Android WXGA", 1280, 800, true);
	
	private final String label;
	private final int w, h;
	private final boolean android;
	
	private TargetPlatform(String label, int w, int h, boolean android) {
		this.label = label;
		this.w = w;
		this.h = h;
		this.android = android;
	}
	
	public int getWidth() { return w; }
	public int getHeight() { return h; }
	public boolean isAndroid() { return android; }
	
	public String toString() {
		return String.format("%s (%dx%d)", label, w, h);
	}
}
