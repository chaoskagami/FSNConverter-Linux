package nl.weeaboo.krkr;

public class Sprite {
	
	public final int x;
	public final int y;
	public final int z;
	public final String image;
	public final int w;
	
	public Sprite(int x, int y, int z, String image, int w) {
		this.x = x;
		this.y = y;
		this.z = z;
		this.image = image;
		this.w = w;
	}
	
	public boolean equals(Object o) {
		return (o instanceof Sprite ? equals((Sprite)o) : false);
	}
	public boolean equals(Sprite s) {
		return s != null && s.image.equals(image) && s.x == x && s.y == y && s.z == z && s.w == w;
	}
}
