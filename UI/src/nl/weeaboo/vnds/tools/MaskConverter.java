package nl.weeaboo.vnds.tools;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

public class MaskConverter {

	//Functions
	public static void main(String args[]) throws IOException {
		if (args.length < 1) {
			System.err.println("Usage: MaskConverter <folder>");
			return;
		}
		
		process(new File(args[0]));
	}
	
	private static void process(File file) {
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				if (f.getName().toLowerCase().endsWith("jpg")
					|| f.getName().toLowerCase().endsWith("bmp"))
				{
					process(f);
				}
			}
		} else {
			try {
				BufferedImage image = ImageIO.read(file);
				int w = image.getWidth();
				int w2 = w / 2;
				int h = image.getHeight();
				
				int rgb[] = image.getRGB(0, 0, w, h, new int[w * h], 0, w);
				for (int y = 0; y < h; y++) {
					int offset = y * w;
					for (int x = 0; x < w2; x++) {
						int c = rgb[offset]; 
						int alpha = rgb[offset + w2]; 
						alpha = 255 - (int)(0.30f * ((alpha >> 16) & 0xFF)
								+ 0.59f * ((alpha >> 8) & 0xFF)
								+ 0.11f * (alpha & 0xFF));
						alpha = Math.max(0, Math.min(255, alpha));
						
						rgb[offset] = (alpha<<24) | (c & 0xFFFFFF);
						offset++;
					}
				}
				
				//Write back to an image
				BufferedImage result = new BufferedImage(w2, h, BufferedImage.TYPE_INT_ARGB);
				result.setRGB(0, 0, w2, h, rgb, 0, w);
				
				//Write to disc
				String path = file.getAbsolutePath();
				path = path.substring(0, path.length() - 3) + "png";
				ImageIO.write(result, "png", new File(path));
				
				//Delete original
				file.delete();
				
				System.out.println("Writing: " + path);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	//Getters

	//Setters
	
}
