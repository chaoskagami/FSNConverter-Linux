package nl.weeaboo.vnds;

import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import javax.imageio.ImageIO;

import nl.weeaboo.awt.ImageUtil;
import nl.weeaboo.image.ImageInfo;
import nl.weeaboo.image.ImageInfo.Result;

public final class VNImageUtil {

	private VNImageUtil() {		
	}
	
	public static int checkHugeImage(File file) throws IOException {
		ImageInfo ii = new ImageInfo();
		Result r = null;
		try {
			r = ii.readHeader(file);
		} catch (IOException ioe) {
			//Ignore
		}
		
		if (r == null) {
			return -1;
		}

		if (r.width > 4096 || r.height > 4096) {
			String msg = String.format("Detected huge image (%d x %d) %s",
					r.width, r.height, file.getAbsolutePath());
			Log.w(msg);
			return 1;
		}		
		return 0;
	}
	
	public static BufferedImage readBMP(File file) throws IOException {
		final int headerSize = 0x36;
		if (file.length() <= headerSize) {
			return ImageIO.read(file);			
		}
		
		int bpp = 0;
		
		//Convert 32-bit bitmaps to a more sensible format
		FileInputStream fin = new FileInputStream(file);
		FileChannel fc = fin.getChannel();
		try {
			fc.position(0x12);
			byte temp[] = new byte[4];
			fin.read(temp);
			int width = ((temp[3]&0xFF)<<24)|((temp[2]&0xFF)<<16)|((temp[1]&0xFF)<<8)|(temp[0]&0xFF);
			fin.read(temp);
			int height = ((temp[3]&0xFF)<<24)|((temp[2]&0xFF)<<16)|((temp[1]&0xFF)<<8)|(temp[0]&0xFF);
			fc.position(fc.position()+2);
			fin.read(temp);
			bpp = ((temp[3]&0xFF)<<24)|((temp[2]&0xFF)<<16)|((temp[1]&0xFF)<<8)|(temp[0]&0xFF);
			
			if (bpp != 32) {
				fc.position(0);
				return ImageIO.read(new BufferedInputStream(fin));
			}
			
			fc.position(headerSize);
			BufferedInputStream bin = new BufferedInputStream(fin, 4096);
			int argb[] = new int[width*height];
			for (int y = height-1; y >= 0; y--) {
				int t = width * y;
				for (int x = 0; x < width; x++) {
					bin.read(temp);
					argb[t++] = ((temp[3]&0xFF)<<24)|((temp[2]&0xFF)<<16)|((temp[1]&0xFF)<<8)|(temp[0]&0xFF);
				}
			}
			
			return ImageUtil.createBufferedImage(width, height, argb, false);
		} catch (EOFException eof) {
			Log.w("Unreadable bitmap (filename="+file.getName()+", bpp="+bpp+")");
			throw eof;
		} catch (IOException ioe) {
			throw ioe;
		} finally {
			fc.close();
			fin.close();
		}
	}
	
	protected static boolean rowEmpty(int argb[], int iw, int ih, int y) {
		int offset = y * iw;
		for (int x = 0; x < iw; x++) {
			if (((argb[offset]>>24) & 0xFF) >= 16) {
				return false;
			}
			offset++;
		}
		return true;
	}
	
	protected static boolean colEmpty(int argb[], int iw, int ih, int x) {
		int offset = x;
		for (int y = 0; y < ih; y++) {
			if (((argb[offset]>>24) & 0xFF) >= 16) {
				return false;
			}
			offset += iw;
		}
		return true;
	}
	
	public static void calculateImageInsets(int[] argb, int iw, int ih, Insets i) {
		for (int x = 0; x < iw && colEmpty(argb, iw, ih, x); x++) i.left++;
		for (int x = iw-1; x >= 0 && colEmpty(argb, iw, ih, x); x--) i.right++;
		for (int y = 0; y < ih && rowEmpty(argb, iw, ih, y); y++) i.top++;
		for (int y = ih-1; y >= 0 && rowEmpty(argb, iw, ih, y); y--) i.bottom++;		
	}

}
