package nl.weeaboo.vnds.tools;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

import javax.imageio.ImageIO;

import nl.weeaboo.awt.ImageUtil;
import nl.weeaboo.common.StringUtil;
import nl.weeaboo.system.ProcessUtil;
import nl.weeaboo.tga.TGAUtil;
import nl.weeaboo.vnds.BatchProcess;
import nl.weeaboo.vnds.FileOp;
import nl.weeaboo.vnds.Log;
import nl.weeaboo.vnds.ProgressListener;
import nl.weeaboo.vnds.VNImageUtil;

public class ImageConverter {

	public enum ConvertType {
		TYPE_RAW_RGBA("RAW RGBA", "dta"),
		TYPE_RAW_RGB256("RAW RGB256", "dta"),
		TYPE_JPG("JPG", "jpg"),
		TYPE_PNG("PNG", "png"),
		TYPE_PNG_256_NEUQUANT("PNG 256 (neuquant)", "png"),
		TYPE_PNG_256_MEDIAN("PNG 256 (median)", "png");

		private String label;
		private String fileExt;

		private ConvertType(String label, String fileExt) {
			this.label = label;
			this.fileExt = fileExt;
		}

		public String getFileExt() { return fileExt; }
		public String toString() { return label; }
	}

	public enum ScalingType {
		NONE("None"), BACKGROUND("Background"), SPRITE("Sprite"), STRETCH("Stretch");

		String label;

		private ScalingType(String label) {
			this.label = label;
		}

		public String toString() { return label; }
	}

	public enum DitheringType {
		NONE("None"), RANDOM("Random"), FLOYD_STEINBERG("Floyd-Steinberg");

		String label;

		private DitheringType(String label) {
			this.label = label;
		}

		public String toString() { return label; }
	}

	private int maxThreads = 8;
	private ConvertType mode = ConvertType.TYPE_RAW_RGBA;
	private ScalingType scaling = ScalingType.NONE;
	private Dimension targetScreenSize = new Dimension(256, 192);
	private Dimension srcScreenSize = new Dimension(800, 600); //Matters for sprites only
	private int quality = 98; //JPG Only
	private DitheringType dithering = DitheringType.NONE;
	private boolean log;

	//Temporary vars
	private Map<File, StringBuilder> processLogs;

	//Functions
	protected static void printUsage() {
		System.err.printf("Usage: java -jar ImageConverter.jar <flags> <file>\nflags:"
				+ "\n\t-threads <num>"
				+ "\n\t-raw <RGBA|256>"
				+ "\n\t-png <RGBA|256>"
				+ "\n\t-jpg <quality (0-100)>"
//				+ "\n\t-size <width> <height>"
				+ "\n");
	}

	public static void main(String args[]) throws IOException {
		ImageConverter ic = new ImageConverter();

		String filename = null;
		try {
			for (int n = 0; n < args.length; n++) {
				if (args[n].startsWith("-jpg")) {
					ic.setMode(ConvertType.TYPE_JPG);
					ic.quality = Integer.parseInt(args[++n]);
				} else if (args[n].startsWith("-png")) {
					if (!args[++n].equals("256")) {
						ic.setMode(ConvertType.TYPE_PNG);
					} else {
						ic.setDitheringType(DitheringType.FLOYD_STEINBERG);
						ic.setMode(ConvertType.TYPE_PNG_256_NEUQUANT);
					}
				} else if (args[n].startsWith("-raw")) {
					if (!args[++n].equals("256")) {
						ic.setMode(ConvertType.TYPE_RAW_RGBA);
					} else {
						ic.setDitheringType(DitheringType.FLOYD_STEINBERG);
						ic.setMode(ConvertType.TYPE_RAW_RGB256);
					}
				} else if (args[n].startsWith("-threads")) {
					ic.maxThreads = Integer.parseInt(args[++n]);
/*
				} else if (args[n].startsWith("-size")) {
					int w = Integer.parseInt(args[++n]);
					int h = Integer.parseInt(args[++n]);
					ic.setBackgroundSize(w, h);
*/
				} else if (filename == null) {
					filename = args[n];
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

		ic.convertFolder(filename, new ProgressListener() {
			public void onFinished(String message) {
				System.out.printf("%s\n", message);
			}
			public void onProgress(int value, int max, String message) {
				System.out.printf("Processing (%d/%d) %s...\n", value, max, message);
			}
		});
	}

	public void convertFolder(String folder, final ProgressListener pl) throws IOException {
		convertFolder(folder, pl, 1);
	}
	public void convertFolder(String folder, final ProgressListener pl, int maxDepth) throws IOException {
		processLogs = new HashMap<File, StringBuilder>();

		Map<String, File> files = new HashMap<String, File>();
		File folderF = new File(folder).getCanonicalFile();
		if (folderF.isDirectory()) {
			for (File file : folderF.listFiles()) {
				if (!file.isDirectory()) files.put(file.getName(), file);
			}
		} else {
			files.put(folderF.getName(), folderF);
		}

		BatchProcess bp = new BatchProcess();
		bp.setTaskSize(32);
		bp.setThreads(maxThreads);
		bp.setThreadPriority(Thread.MIN_PRIORITY);
		bp.addProgressListener(pl);
		try {
			bp.run(files, new FileOp() {
				@Override
				public void execute(String relpath, File file) throws IOException {
					convertFile(file);
				}
			});
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public File convertFile(File file) {
		return convertFile(file, null);
	}
	public File convertFile(File file, File targetFolder) {
		String filenameNoExt = file.getName();
		if (filenameNoExt.lastIndexOf('.') > 0) {
			filenameNoExt = filenameNoExt.substring(0, filenameNoExt.lastIndexOf('.'));
		}

		StringBuilder log = new StringBuilder();
		if (isLogging()) processLogs.put(file, log);

		try {
			BufferedImage result = null;
			try {
				if (file.getName().endsWith(".bmp")) {
					result = VNImageUtil.readBMP(file);
				} else if (file.getName().endsWith(".tga")) {
					result = TGAUtil.readTGA(file);
				} else {
					result = ImageIO.read(file);
				}
			} catch (RuntimeException re) {
				Log.w("Exception while reading image", re);
			}
			if (result == null) {
				Log.e("Unreadable image: " + file.getAbsolutePath());
				return null;
			}

			int w = result.getWidth();
			int h = result.getHeight();

			//Scaling
			if (scaling == ScalingType.STRETCH) {
				w = targetScreenSize.width;
				h = targetScreenSize.height;
				result = ImageUtil.getScaledImage(result, w, h, Image.SCALE_AREA_AVERAGING);
			} else {
				int scaledW = w;
				int scaledH = h;
				float scale = Math.min(targetScreenSize.width / (float)srcScreenSize.width,
						targetScreenSize.height / (float)srcScreenSize.height);
				if (scaling == ScalingType.SPRITE || scaling == ScalingType.BACKGROUND) {
					scaledW = Math.max(1, Math.round(w * scale));
					scaledH = Math.max(1, Math.round(h * scale));
				}

				Image scaled = ImageUtil.getScaledImage(result, scaledW, scaledH, Image.SCALE_AREA_AVERAGING);

				if (scaling == ScalingType.BACKGROUND) {
					w = targetScreenSize.width;
					h = targetScreenSize.height;
				} else {
					w = scaledW;
					h = scaledH;
				}

				result = ImageUtil.createCompatibleImage(w, h, scaled);
				if (mode == ConvertType.TYPE_JPG) {
					result = ImageUtil.asOpaqueImage(result);
				}

				Graphics2D g = (Graphics2D)result.getGraphics();
				if (scaling == ScalingType.BACKGROUND) {
					int sw = Math.round(scale*srcScreenSize.width);
					int sh = Math.round(scale*srcScreenSize.height);
					g.clipRect((w-sw)/2, (h-sh)/2, sw, sh);
				}
				g.drawImage(scaled, (w-scaledW)/2, (h-scaledH)/2, null);
				g.dispose();
			}

			//Dithering
			if (dithering == DitheringType.RANDOM) {
				int[] rgb = result.getRGB(0, 0, w, h, null, 0, w);

				Random rnd = new Random(0x13371337);
				int t = 0;
				for (int y = 0; y < h; y++) {
					for (int x = 0; x < w; x++) {
						int c = rgb[t];

						double r = ((c>>16)&0xFF) * 31.0 / 255.0;
						double g = ((c>>8 )&0xFF) * 31.0 / 255.0;
						double b = ((c    )&0xFF) * 31.0 / 255.0;

						boolean ceil = rnd.nextFloat() < (r-Math.floor(r) + g-Math.floor(g) + b-Math.floor(b)) / 3.0;

						int ri = (int)(ceil ? Math.ceil(r) : Math.floor(r));
						int gi = (int)(ceil ? Math.ceil(g) : Math.floor(g));
						int bi = (int)(ceil ? Math.ceil(b) : Math.floor(b));

						rgb[t] = (c&0xFF000000) | (((ri<<3)&0xFF)<<16) | (((gi<<3)&0xFF)<<8) | ((bi<<3)&0xFF);
						t++;
					}
				}

				result.setRGB(0, 0, w, h, rgb, 0, w);
			} else if (dithering == DitheringType.FLOYD_STEINBERG) {
				int[] rgb = result.getRGB(0, 0, w, h, null, 0, w);
				floydSteinberg(rgb, w, h);
				result.setRGB(0, 0, w, h, rgb, 0, w);
			}

			if (targetFolder == null) {
				targetFolder = file.getParentFile();
				file.delete();
			}

			//Create unique hash (multiple threads are writing temp files in the same folder)
			String threadHash = String.valueOf(hashCode() ^ file.hashCode() ^ Thread.currentThread().hashCode());

			file = new File(String.format("%s/%s.%s",
					targetFolder, filenameNoExt, mode.getFileExt().toLowerCase()));
			if (file.getParentFile() != null) {
				file.getParentFile().mkdirs();
			}

			if (mode.getFileExt().equalsIgnoreCase("jpg")) {
				String bmpFile = String.format("%s/__%s.bmp", targetFolder, threadHash);
				String tmpFile = String.format("%s/__%s.jpg", targetFolder, threadHash);

				ImageIO.write(result, "bmp", new File(bmpFile));

				Process p = ProcessUtil.execInDir(String.format(
						"cconvert -quality %d \"%s\" \"%s\"",
						quality, bmpFile, tmpFile),
						"");
				ProcessUtil.waitFor(p);
				ProcessUtil.kill(p);
				file.delete();
				new File(bmpFile).delete();
				new File(tmpFile).renameTo(file);
			} else if (mode.getFileExt().equalsIgnoreCase("png")) {
				String tmpFile = String.format("%s/__%s.png", targetFolder, threadHash);
				ImageIO.write(result, "png", new File(tmpFile));

				if (mode == ConvertType.TYPE_PNG_256_MEDIAN) {
					Process p = ProcessUtil.execInDir(String.format(
							"pngquant 256 \"%s\"", tmpFile),
							"");
					ProcessUtil.waitFor(p);
					ProcessUtil.kill(p);
					file.delete();
					new File(StringUtil.stripExtension(tmpFile)+"-fs8.png").renameTo(file);
				} else if (mode == ConvertType.TYPE_PNG_256_NEUQUANT) {
					Process p = ProcessUtil.execInDir(String.format(
							"pngnq \"%s\"", tmpFile),
							"");
					ProcessUtil.waitFor(p);
					ProcessUtil.kill(p);
					file.delete();
					new File(StringUtil.stripExtension(tmpFile)+"-nq8.png").renameTo(file);
				} else {
					String crushedName = StringUtil.stripExtension(tmpFile)+".crushed.png";
					Process p = ProcessUtil.execInDir(String.format(
							"pngcrush -fix \"%s\" \"%s\"", tmpFile, crushedName),
							"");
					ProcessUtil.waitFor(p);
					ProcessUtil.kill(p);
					file.delete();
					new File(crushedName).renameTo(file);
				}

				new File(tmpFile).delete();
			} else if (mode.getFileExt().equalsIgnoreCase("dta")) {
				if (mode == ConvertType.TYPE_RAW_RGBA) {
					int[] rgb = result.getRGB(0, 0, w, h, null, 0, w);

					BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(file));
					for (int c : rgb) {
						int a = ((c>>>24) >= 127 ? (1<<15) : 0);
						int r = (c>>19) & 31;
						int g = (c>>11) & 31;
						int b = (c>>3)  & 31;
						c = a | (b<<10) | (g<<5) | (r);

						bout.write(c&0xFF);
						bout.write((c>>8)&0xFF);
					}
					bout.flush();
					bout.close();
				} else if (mode == ConvertType.TYPE_RAW_RGB256) {
					File tmpFile = new File(String.format("%s/__%s.png", targetFolder, threadHash));
					ImageIO.write(result, "png", tmpFile);

					Process p = ProcessUtil.execInDir(String.format(
							"pngnq \"%s\"", tmpFile.getAbsolutePath()),
							"");
					ProcessUtil.waitFor(p);
					ProcessUtil.kill(p);
					tmpFile.delete();
					new File(StringUtil.stripExtension(tmpFile.getAbsolutePath())+"-nq8.png").renameTo(tmpFile);

					result = ImageIO.read(tmpFile);
					IndexColorModel icm = (IndexColorModel)result.getColorModel();

					BufferedOutputStream bout;

					bout = new BufferedOutputStream(new FileOutputStream(
							StringUtil.stripExtension(file.getAbsolutePath())+".pal"));
					for (int n = 0; n < icm.getMapSize(); n++) {
						int a = (1<<15);
						int r = icm.getRed(n) >> 3;
						int g = icm.getGreen(n) >> 3;
						int b = icm.getBlue(n) >> 3;
						int c = a | (b<<10) | (g<<5) | (r);

						bout.write(c&0xFF);
						bout.write((c>>8)&0xFF);
					}
					bout.close();

					bout = new BufferedOutputStream(new FileOutputStream(file));
					int dta[] = new int[result.getWidth() * result.getHeight()];
					result.getRaster().getPixels(0, 0, result.getWidth(), result.getHeight(), dta);
					for (int n = 0; n < dta.length; n++) {
						bout.write(dta[n]);
					}
					bout.close();

					tmpFile.delete();
				}
			} else {
				throw new IllegalArgumentException("Invalid file-ext: " + mode.getFileExt());
			}
			return file;
		} catch (Exception e) {
			Log.w(file.getName() + " " + e);
			log.append(e.toString());
		}

		return null;
	}


	public File dumpLog(String filename) throws IOException {
		File file = new File(filename);
		PrintWriter out = new PrintWriter(new FileWriter(file));

		out.println("----------------------------------------");
		out.println("----------------------------------------");
		for (Entry<File, StringBuilder> entry : getLogs().entrySet()) {
			out.println();
			out.println("Log for file:" + entry.getKey().getAbsolutePath());
			out.println();

			out.println(entry.getValue().toString());

			out.println("----------------------------------------");
			out.println("----------------------------------------");
		}

		out.close();

		return file;
	}

	private static int round(float f) {
		return (int)f;
	}

	private static float saturate255(float f) {
		if (f < 0f)   return 0f;
		if (f > 255f) return 255f;
		return f;
	}

	/** Does dithering for 5-bit colors (equivalent to 8-bit / 8.0) */
	private static void floydSteinberg(int rgb[], int w, int h) {
		int L = w * h;

		float rgba[][] = new float[4][L];
		for (int t = 0; t < L; t++) {
			rgba[0][t] = ((rgb[t]>>>16)&0xFF);
			rgba[1][t] = ((rgb[t]>>>8 )&0xFF);
			rgba[2][t] = ((rgb[t]     )&0xFF);
			rgba[3][t] = ((rgb[t]>>>24)&0xFF);
		}

		final float DIV_8  = .125f;
		final float DIV_16 = .0625f;
		final int RIGHT = 1;
		final int DOWN = w;
		final int DOWN_LEFT = DOWN-1;
		final int DOWN_RIGHT = DOWN+1;

		for (int c = 0; c < 3; c++) {
			float[] p = rgba[c];
			int t = 0;
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					float oldc = p[t];
					float newc = p[t] = round(oldc * DIV_8) << 3;
					float error = oldc - newc;

					if (x+1 < w) {
						p[t+RIGHT] += (7 * error) * DIV_16;
						p[t+RIGHT] = saturate255(p[t+RIGHT]);
					}
					if (y+1 < h) {
						if (x-1 >= 0) {
							p[t+DOWN_LEFT] += (3 * error) * DIV_16;
							p[t+DOWN_LEFT] = saturate255(p[t+DOWN_LEFT]);
						}
						p[t+DOWN] += (5 * error) * DIV_16;
						p[t+DOWN] = saturate255(p[t+DOWN]);
						if (x+1 < w) {
							p[t+DOWN_RIGHT] += error * DIV_16;
							p[t+DOWN_RIGHT] = saturate255(p[t+DOWN_RIGHT]);
						}
					}

					t++;
				}
			}
		}

		int t = 0;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int a = Math.min(255, Math.max(0, (int)Math.round(rgba[3][t])));
				int r = Math.min(255, Math.max(0, (int)Math.round(rgba[0][t])));
				int g = Math.min(255, Math.max(0, (int)Math.round(rgba[1][t])));
				int b = Math.min(255, Math.max(0, (int)Math.round(rgba[2][t])));
				rgb[t] = (a<<24)|(r<<16)|(g<<8)|(b);
				t++;
			}
		}
	}

	// Getters
	public Map<File, StringBuilder> getLogs() { return processLogs; }
	public boolean isLogging() { return log; }
	public ConvertType getMode() { return mode; }
	public ScalingType getScaling() { return scaling; }
	public Dimension getTargetScreenSize() { return new Dimension(targetScreenSize); }
	public Dimension getSourceScreenSize() { return new Dimension(srcScreenSize); }
	public int getQuality() { return quality; }
	public DitheringType getDitheringType() { return dithering; }
	public int getMaxThreads() { return maxThreads; }

	// Setters
	public void setMode(ConvertType mode) { this.mode = mode; }
	public void setScalingType(ScalingType s) { this.scaling = s; }
	public void setLogging(boolean l) { this.log = l; }
	public void setSourceScreenSize(int w, int h) { srcScreenSize.width = w; srcScreenSize.height = h; }
	public void setTargetScreenSize(int w, int h) { targetScreenSize.width = w; targetScreenSize.height = h; }
	public void setQuality(int quality) { this.quality = quality; }
	public void setDitheringType(DitheringType dithering) { this.dithering = dithering; }
	public void setMaxThreads(int mt) { this.maxThreads = mt; }

}
