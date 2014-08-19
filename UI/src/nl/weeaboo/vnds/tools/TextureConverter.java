package nl.weeaboo.vnds.tools;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel.MapMode;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import nl.weeaboo.collections.Tuple2;
import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.system.ProcessUtil;
import nl.weeaboo.vnds.BatchProcess;
import nl.weeaboo.vnds.FileOp;
import nl.weeaboo.vnds.ProgressListener;

public class TextureConverter {
	
	/*
	 * Formats:
	 * [1] A3I5
	 * [2] --
	 * [3] --
	 * [4] RGB256
	 * [5] --
	 * [6] A5I3
	 * [7] RGBA
	 */

	public enum Mode {
		A3I5(1, "A3I5"),
		RGB256(4, "RGB256"),
		A5I3(6, "A5I3"),
		RGBA(7, "RGBA");
		
		private int num;
		private String label;
		
		private Mode(int i, String l) {
			num = i;
			label = l;
		}
		
		public static Mode fromInt(int i) {
			for (Mode m : Mode.values()) {
				if (m.num == i) return m;
			}
			return null;
		}
		public String toString() {
			return num + ". " + label;
		}
	}
	
	public enum Quant {
		NEUQUANT, OCTREE;
	}
	
	private Mode mode = Mode.RGB256;
	private Quant quant = Quant.OCTREE;
	private boolean dithering = true;
	private int threads = Runtime.getRuntime().availableProcessors();
	
	//Functions
	protected static void printUsage() {
		System.err.printf(
				"Usage: java -jar TextureConverter.jar <mode> <flags> file [out-folder]\n"
				+ "\n\tmode:"
				+ "\n\t-1 => A3I5"
				+ "\n\t-4 => RGB256"
				+ "\n\t-6 => A5I3"
				+ "\n\t-7 => RGBA"
				+ "\n\tflags:"
				+ "\n\t-nodither => No dithering"
				+ "\n\t-quant <octree|neuquant>"
				+ "\n\t-threads <num>"
				+ "\n");		
	}
	
	public static void main(String args[]) {		
		TextureConverter tc = new TextureConverter();
		String filename = null;
		String folder = null;
		
		try {
			tc.setMode(Mode.fromInt(Integer.parseInt(args[0].substring(1))));
			
			for (int n = 1; n < args.length; n++) {
				if (args[n].startsWith("-nodither")) {
					tc.setDithering(false);
				} else if (args[n].startsWith("-quant")) {
					if (!args[++n].equals("neuquant")) {
						tc.setQuant(Quant.NEUQUANT);
					} else {
						tc.setQuant(Quant.OCTREE);
					}
				} else if (args[n].startsWith("-threads")) {
					tc.setThreads(Integer.parseInt(args[++n]));
				} else if (filename == null) {
					filename = args[n];
				} else if (folder == null) {
					folder = args[n];
				} else {
					throw new IllegalArgumentException("Error parsing arg: " + args[n]);
				}
			}
		} catch (RuntimeException re) {
			printUsage();
			return;
		}
		if (filename == null) {
			printUsage();
			return;
		}
			
		if (folder == null) {
			folder = filename;
		}
		
		try {			
			tc.convertFile(filename, folder);
		} catch (Exception e) {
			e.printStackTrace();
		}		
	}
	
	public void convertFolder(String folder, ProgressListener pl) throws IOException {
		Map<String, File> files = new HashMap<String, File>();
		final File folderF = new File(folder).getCanonicalFile();
		if (folderF.isDirectory()) {
			for (File file : folderF.listFiles()) {
				if (!file.isDirectory()) files.put(file.getName(), file);
			}
		} else {
			files.put(folderF.getName(), folderF);
		}
							
		BatchProcess bp = new BatchProcess();
		bp.setTaskSize(32);
		bp.setThreads(threads);
		bp.setThreadPriority(Thread.MIN_PRIORITY);
		bp.addProgressListener(pl);
		try {
			bp.run(files, new FileOp() {
				public void execute(String relpath, File file) throws IOException {
					convertFile(file.getAbsolutePath(), folderF.getAbsolutePath());
				}
			});
		} catch (InterruptedException e) {
			e.printStackTrace();
		}		
	}
	
	public void convertFile(File file) throws IOException {
		convertFile(file.getAbsolutePath(), file.getParent());
	}
	public void convertFile(String src, String dst) throws IOException {
		File srcF = new File(src);		
		File dstF = new File(dst);
		
		if (srcF == null || !srcF.exists() || !srcF.isFile() || !srcF.canRead()) {
			throw new FileNotFoundException(srcF.getPath());
		}
		
		if (dstF.exists() && !dstF.isDirectory()) {
			dstF = dstF.getParentFile();
		} else {
			dst = dstF.getAbsolutePath()+File.separator+srcF.getName();
		}
		dstF.mkdirs();
		
		File temp = File.createTempFile("_tc" + Thread.currentThread().hashCode(), ".png", dstF);		
		try {
			FileUtil.copyFile(srcF, temp);
			String tempPath = temp.getAbsolutePath();
			
			if (mode == Mode.A3I5) {
				quantize(tempPath, 32);
				convertToPalette(tempPath, 3, 5);
			} else if (mode == Mode.RGB256) {
				quantize(tempPath, 256);
				convertToPalette(tempPath, 0, 8);
			} else if (mode == Mode.A5I3) {
				quantize(tempPath, 8);
				convertToPalette(tempPath, 5, 3);
			} else if (mode == Mode.RGBA) {
				quantize(tempPath, 256);
				convertToRAW(tempPath);
			} else {
				throw new IllegalArgumentException("Invalid mode: " + mode);
			}
			
			String tempBase = StringUtil.stripExtension(tempPath);
			String dstBase = StringUtil.stripExtension(dst);
			
			//Rename to dst names
			
			File dstDTA = new File(dstBase+".dta");
			File dstPAL = new File(dstBase+".pal");
			File tempDTA = new File(tempBase+".dta");
			File tempPAL = new File(tempBase+".pal");

			dstDTA.delete();
			tempDTA.renameTo(dstDTA);
			tempDTA.delete();

			dstPAL.delete();
			tempPAL.renameTo(dstPAL);
			tempPAL.delete();
		} finally {
			temp.delete();
		}
	}
	
	protected void quantize(String path, int numColors) throws IOException {
		File file = new File(path).getCanonicalFile();
		if (file == null || !file.exists() || !file.isFile() || !file.canRead()) {
			throw new FileNotFoundException(path);
		}
		path = file.getAbsolutePath();
		
		//Dither to DS colors
		BufferedImage image = ImageIO.read(file);
		int iw = image.getWidth();
		int ih = image.getHeight();
		
		int argb[] = image.getRGB(0, 0, iw, ih, null, 0, iw);
		int alpha[] = new int[argb.length];
		for (int n = 0; n < argb.length; n++) {
			alpha[n] = argb[n] >>> 24;
			argb[n] = 0xFF000000 | (argb[n] & 0xFFFFFF);
		}
		
		if (dithering) {
			floydSteinberg(argb, iw, ih);
		}
				
		//Quantize to the number of colors we want
		if (quant == Quant.NEUQUANT) {
			image.setRGB(0, 0, iw, ih, argb, 0, iw);
			ImageIO.write(image, "png", file);

			Process p = ProcessUtil.execInDir(String.format(
					"pngnq \"%s\" -n %d", path, numColors),
					".");
			ProcessUtil.waitFor(p);
			ProcessUtil.kill(p);
			file.delete();
			new File(StringUtil.stripExtension(path)+"-nq8.png").renameTo(file);

			image = ImageIO.read(file);
			image.getRGB(0, 0, image.getWidth(), image.getHeight(),
					argb, 0, image.getWidth());
		} else if (quant == Quant.OCTREE) {
			Octree tree = new Octree();
			tree.quantize(argb, iw, numColors);
		}

		//Re-add alpha channel
		for (int n = 0; n < argb.length; n++) {
			argb[n] = (argb[n] & 0xFFFFFF) | (alpha[n]<<24);
		}
		
		image = new BufferedImage(iw, ih, BufferedImage.TYPE_INT_ARGB);
		image.setRGB(0, 0, iw, ih, argb, 0, iw);
		ImageIO.write(image, "png", file);
		
		//FileUtil.copyFile(file, new File(file.getParent()+"/temp2.png"));
	}
	
	protected void convertToPalette(String path, int bitsA, int bitsC) throws IOException {
		File src = new File(path);		
		BufferedImage image = ImageIO.read(src);
		int argb[] = image.getRGB(0, 0, image.getWidth(), image.getHeight(),
				null, 0, image.getWidth());
		src.delete();

		int maxA = (1<<bitsA) - 1;
		int palette[] = createPalette(argb, (1<<bitsC));
		
		File dstDTA = new File(StringUtil.stripExtension(path)+".dta");
		BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(dstDTA));
		for (int c : argb) {
			int alpha = (c>>24) & 0xFF;
			int idx = lookupPalette(palette, A8R8G8B8_to_A1B5G5R5(c));
			if (alpha == 0) idx = 0;
			
			c = ((Math.round(maxA * alpha / 255f) & maxA) << bitsC) | idx;			
			bout.write(c&0xFF);
		}
		bout.flush();
		bout.close();

		File dstPAL = new File(StringUtil.stripExtension(path)+".pal");
		bout = new BufferedOutputStream(new FileOutputStream(dstPAL));
		for (int c : palette) {			
			bout.write(c&0xFF);
			bout.write((c>>8)&0xFF);
		}
		bout.flush();
		bout.close();
	}

	
	protected void convertToRAW(String path) throws IOException {
		File src = new File(path);		
		BufferedImage image = ImageIO.read(src);
		int argb[]  = image.getRGB(0, 0, image.getWidth(), image.getHeight(),
				null, 0, image.getWidth());
		A8R8G8B8_to_A1B5G5R5(argb);
		src.delete();
		
		File dst = new File(StringUtil.stripExtension(path)+".dta");		
		BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(dst));
		for (int c : argb) {
			bout.write(c&0xFF);
			bout.write((c>>8)&0xFF);
		}
		bout.flush();
		bout.close();
	}
	
	protected int A8R8G8B8_to_A1B5G5R5(int c) {
		int a = ((c>>>24) >= 127 ? (1<<15) : 0);
		int r = Math.round(((c>>16)&0xFF) / 8f);
		r = Math.max(0, Math.min(31, r));
		int g = Math.round(((c>>8)&0xFF) / 8f);
		g = Math.max(0, Math.min(31, g));
		int b = Math.round(((c)&0xFF) / 8f);		
		b = Math.max(0, Math.min(31, b));
		return a | (b<<10) | (g<<5) | (r);
	}
	protected void A8R8G8B8_to_A1B5G5R5(int data[]) {
		for (int n = 0; n < data.length; n++) {
			data[n] = A8R8G8B8_to_A1B5G5R5(data[n]);
		}
	}
	
	protected int[] createPalette(int argb[], int maxC) {
		int colorHistogram[] = new int[0x8000];		
		for (int c : argb) {
			colorHistogram[A8R8G8B8_to_A1B5G5R5(c) & 0x7FFF]++;
		}
		
		TreeSet<Tuple2<Integer, Integer>> set = new TreeSet<Tuple2<Integer, Integer>>(
			new Comparator<Tuple2<Integer, Integer>>() {
				public int compare(Tuple2<Integer, Integer> t1, Tuple2<Integer, Integer> t2) {
					int c = t1.y.compareTo(t2.y);
					if (c == 0) c = t1.x.compareTo(t2.x);
					return -c;
				}
			}
		);
		
		for (int n = 0; n < colorHistogram.length; n++) {
			if (colorHistogram[n] > 0) {
				set.add(Tuple2.newTuple(n, colorHistogram[n]));
			}
		}
		
		int result[] = new int[maxC];
		int t = 1;
		for (Tuple2<Integer, Integer> tuple : set) {
			if (t >= result.length) break;
			
			result[t++] = tuple.x;
		}
		
		return result;
	}
	
	protected int lookupPalette(int palette[], int c0) {
		int b0 = (c0>>10) & 31;
		int g0 = (c0>>5)  & 31;
		int r0 = (c0)     & 31;
		
		int best = 0;
		int bestDist = Integer.MAX_VALUE;
		
		for (int idx = 1; idx < palette.length; idx++) {
			int c1 = palette[idx];
			int b1 = (c1>>10) & 31;
			int g1 = (c1>>5)  & 31;
			int r1 = (c1)     & 31;
						
			int dist = Math.abs(r1-r0) + Math.abs(g1-g0) + Math.abs(b1-b0);
			if (dist < bestDist) {
				best = idx;
				bestDist = dist;
			}
		}
		
		return best;
	}
	
	/** Does dithering for 5-bit colors (equivalent to 8-bit / 8.0) */
	private static void floydSteinberg(int rgb[], int w, int h) {
		double rgba[][][] = new double[w][h][4];
		int t = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				rgba[x][y][0] = ((rgb[t]>>>16)&0xFF);
				rgba[x][y][1] = ((rgb[t]>>>8 )&0xFF);
				rgba[x][y][2] = ((rgb[t]     )&0xFF);
				rgba[x][y][3] = ((rgb[t]>>>24)&0xFF);
				
				t++;
			}
		}

		for (int x = 0; x < w; x++) {
			for (int y = 0; y < h; y++) {
				for (int c = 0; c < 3; c++) {
					double oldc = rgba[x][y][c];
					double newc = rgba[x][y][c] = Math.round(oldc / 8.0) * 8.0;
					double error = oldc - newc;

					if (x+1 < w) {
						rgba[x+1][y][c] += (7.0 * error) / 16.0;
						rgba[x+1][y][c] = Math.min(255.0, Math.max(0.0, rgba[x+1][y][c]));
					}
					if (y+1 < h) {
						if (x-1 >= 0) {
							rgba[x-1][y+1][c] += (3.0 * error) / 16.0;
							rgba[x-1][y+1][c] = Math.min(255.0, Math.max(0.0, rgba[x-1][y+1][c]));
						}
						rgba[x][y+1][c] += (5.0 * error) / 16.0;
						rgba[x][y+1][c] = Math.min(255.0, Math.max(0.0, rgba[x][y+1][c]));
						if (x+1 < w) {
							rgba[x+1][y+1][c] += (1.0 * error) / 16.0;
							rgba[x+1][y+1][c] = Math.min(255.0, Math.max(0.0, rgba[x+1][y+1][c]));
						}
					}
				}				
			}
		}
		
		t = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int a = Math.min(255, Math.max(0, (int)Math.round(rgba[x][y][3])));
				int r = Math.min(255, Math.max(0, (int)Math.round(rgba[x][y][0])));
				int g = Math.min(255, Math.max(0, (int)Math.round(rgba[x][y][1])));
				int b = Math.min(255, Math.max(0, (int)Math.round(rgba[x][y][2])));
				rgb[t] = (a<<24)|(r<<16)|(g<<8)|(b);
				t++;
			}
		}
	}
	
	public Image readImage(String origPath) throws IOException {
		File origF = new File(origPath);
		BufferedImage orig = ImageIO.read(origF);
		int w = orig.getWidth();
		int h = orig.getHeight();
		
		String base = StringUtil.stripExtension(origPath);

		File convDTA = new File(base + ".dta");
		FileInputStream dtaIn = new FileInputStream(convDTA);
		
		try {
			ByteBuffer dtaBuf = dtaIn.getChannel().map(MapMode.READ_ONLY, 0, convDTA.length());
			dtaBuf.order(ByteOrder.LITTLE_ENDIAN);
			
			int bpp = (mode == Mode.RGBA ? 2 : 1);
			int pixels[] = new int[((int)convDTA.length()) / bpp];
			for (int n = 0; n < pixels.length; n++) {
				if (bpp == 2) pixels[n] = dtaBuf.getShort();
				else pixels[n] = dtaBuf.get();
			}
			dtaIn.close();

			if (mode != Mode.RGBA) {
				File convPAL = new File(base + ".pal");
				FileInputStream palIn = new FileInputStream(convPAL);
				try {				
					ByteBuffer palBuf = palIn.getChannel().map(MapMode.READ_ONLY, 0, convPAL.length());
					palBuf.order(ByteOrder.LITTLE_ENDIAN);
					int palette[] = new int[((int)convPAL.length()) / 2];
					for (int n = 0; n < palette.length; n++) {
						palette[n] = palBuf.getShort();
					}
					
					int colorMask = 0xFF;
					if (mode == Mode.A3I5) colorMask = 31;
					else if (mode == Mode.A5I3) colorMask = 7;
					
					for (int n = 0; n < pixels.length; n++) {
						int c = pixels[n];
							
						int alpha = 255;
						if (mode == Mode.A3I5) alpha = (c>>5)*32+16;
						else if (mode == Mode.A5I3) alpha = (c>>3)*8+4;
						else if (mode == Mode.RGB256) alpha = (c == 0 ? 0 : 255);
						
						c = palette[c & colorMask];
						c = (((c&31)*8+4)<<16) | ((((c>>5)&31)*8+4)<<8) | (((c>>10)&31)*8+4);
						pixels[n] = (alpha<<24) | c;
					}
				} finally {
					palIn.close();					
				}
			} else {
				for (int n = 0; n < pixels.length; n++) {
					int c = pixels[n];
					int alpha = (c>>15 != 0 ? 255 : 0);
					c = (((c&31)*8+4)<<16) | ((((c>>5)&31)*8+4)<<8) | (((c>>10)&31)*8+4);
					pixels[n] = (alpha<<24) | c;
				}
			}
			
			BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			image.setRGB(0, 0, w, h, pixels, 0, w);
			return image;
		} finally {
			dtaIn.close();
		}
	}
	
	//Getters
	public Mode getMode() { return mode; }
	public Quant getQuant() { return quant; }
	public boolean isDithering() { return dithering; }
	public int getThreads() { return threads; }
	
	//Setters
	public void setMode(Mode mode) { this.mode = mode; }
	public void setQuant(Quant quant) { this.quant = quant; }
	public void setDithering(boolean dithering) { this.dithering = dithering; }
	public void setThreads(int threads) { this.threads = threads; }
	
}
