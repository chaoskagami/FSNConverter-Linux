package nl.weeaboo.krkr.fate;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.krkr.fate.FateScriptConverter.Language;
import nl.weeaboo.string.StringUtil2;
import nl.weeaboo.vnds.FileMapper;
import nl.weeaboo.vnds.HashUtil;
import nl.weeaboo.vnds.Log;

public class ResourceUsageAnalyzer {

	private static final int ROUTE_PRO  = 1;
	private static final int ROUTE_FATE = 2;
	private static final int ROUTE_UBW  = 4;
	private static final int ROUTE_HF   = 8;
	
	private Map<Integer, String> names;
	private String infoFolder;
	private String rootFolder;
	
	public ResourceUsageAnalyzer(String infoFolder, String rootFolder) {
		this.infoFolder = infoFolder;
		this.rootFolder = rootFolder;
		
		names = new HashMap<Integer, String>();
		names.put(0, "core");
		names.put(ROUTE_PRO, "prologue");
		names.put(ROUTE_FATE, "route01-fate");
		names.put(ROUTE_UBW, "route02-ubw");
		names.put(ROUTE_HF, "route03-hf");
	}
	
	//Functions	
	protected Map<Integer, Set<String>> a() {
		Map<String, File> files = new Hashtable<String, File>();
		FileUtil.collectFiles(files, new File(rootFolder+"/script"), false);
		
		System.out.println("Collecting Resources...");
		
		Map<String, FileInfo> resources = new Hashtable<String, FileInfo>();
		for (Entry<String, File> entry : files.entrySet()) {
			FileInfo info = new FileInfo(entry.getKey());
			resources.put(info.getFilename(), info);
					
			try {
				analyzeResources(rootFolder+"/script", info);
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}
		
		System.out.println("Writing Resource Lists to Disk...");

		File outputFolder = new File(infoFolder, "dependency_analysis");
		FileUtil.deleteFolder(outputFolder);
		outputFolder.mkdirs();
		for (FileInfo info : resources.values()) {
			try {
				info.save(outputFolder+StringUtil.stripExtension(info.getFilename())+".txt");
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}

		System.out.println("Graph Analysis...");

		Map<String, GraphNode> nodes = new HashMap<String, GraphNode>();
		for (FileInfo info : resources.values()) {
			nodes.put(info.getFilename(), new GraphNode(info.getFilename()));
		}
		for (FileInfo info : resources.values()) {
			GraphNode in = nodes.get(info.getFilename());
			for (String s : info.jumpsTo) {
				GraphNode out = nodes.get(s);
				if (in != null && out != null) {
					in.outgoing.add(out);
					out.incoming.add(in);
				}
			}
		}
		
		setRoute(resources, nodes, "prologue00.scr", ROUTE_PRO);
		setRoute(resources, nodes, "fate05-00.scr", ROUTE_FATE);
		setRoute(resources, nodes, "ubw03-09.scr", ROUTE_UBW);
		setRoute(resources, nodes, "ubw04-03.scr", ROUTE_UBW);
		setRoute(resources, nodes, "hf04-11.scr", ROUTE_HF);
		
		SortedSet<String> sorted = new TreeSet<String>(StringUtil2.getStringComparator());
		sorted.addAll(resources.keySet());
		
		for (int n = 0; n < 3; n++) {			
			StringBuilder sb = new StringBuilder();
			for (String name : sorted) {
				GraphNode node = nodes.get(name);
				StringBuilder sb2 = new StringBuilder();
				
				String prefix = node.getName().substring(0, Math.min(node.getName().length(), 2));
				int links = 0;
				
				sb2.append(node.getName()+" "+resources.get(name).getRoute());
				sb2.append("\n");
				
				if (n < 2) {
					sb2.append("\tin : ");
					for (GraphNode in : node.incoming) {
						if (n==0 || !in.getName().startsWith(prefix)) {
							sb2.append(in.getName()+" ");
							links++;
						}
					}
					sb2.append("\n");
					
					sb2.append("\tout: ");
					for (GraphNode in : node.outgoing) {
						if (n==0 || !in.getName().startsWith(prefix)) {
							sb2.append(in.getName()+" ");
							links++;
						}
					}
					sb2.append("\n");					
				}
				if (n == 0) {
					sb.append(sb2);
					sb.append("\n\n");
				} else if (n == 2 || links > 0) {
					sb.append(sb2);
				}
			}
			try {
				FileUtil.write(new File(outputFolder+"_graph"+n+".txt"), sb.toString());
			} catch (IOException ioe) {
				ioe.printStackTrace();
			}
		}		
		
		System.out.println("Creating XML files...");

		Map<Integer, Set<String>> res = new HashMap<Integer, Set<String>>();
		for (FileInfo info : resources.values()) {
			Set<String> set = res.get(info.getRoute());
			if (set == null) {
				res.put(info.getRoute(), set = new HashSet<String>());
			}
			
			for (String s : info.background) set.add("background/"+s);
			for (String s : info.foreground) set.add("foreground/"+s);
			for (String s : info.sound) set.add("sound/"+s);
			for (String s : info.music) set.add("music/"+s);
		}
		
		return res;
	}
	
	public void analyze(Language lang, int threads) {
		long tstart = System.currentTimeMillis();
		
		Map<Integer, Set<String>> res = a();
				
		//Generate set of all global resources, including the font, info.txt, etc. 
		Set<String> globalResources = res.get(0);
		globalResources.add("info.txt");
		globalResources.add("default.ttf");
		globalResources.add("thumbnail.png");
		globalResources.add("icon.png");
		globalResources.add("img.ini");
		globalResources.add("save/global.sav");
		addFolder(globalResources, "script", "script");
		addFolder(globalResources, "foreground/special", "foreground/special");
		addFolder(globalResources, "background/special", "background/special");
		addFolder(globalResources, "sound/special", "sound/special");;
				
		FileMapper mapper = new FileMapper();
		mapper.put("info.txt", "info-"+lang.getLangCode()+".txt");
		try {
			mapper.load(infoFolder+"/filenames.txt");
		} catch (IOException ioe) {
			Log.e("Exception loading filename mapping", ioe);
		}
		
		for (Entry<Integer, Set<String>> entry : res.entrySet()) {
			try {
				hashFileSet(names.get(entry.getKey()), entry.getValue(), mapper);
			} catch (IOException e) {
				Log.e("Error hashing file set", e);
			}
		}

		System.out.printf("Took %.2fs\nFinished\n", (System.currentTimeMillis()-tstart)/1000.0);
	}

	protected void hashFileSet(String name, Set<String> set, FileMapper mapper) throws IOException {
		System.out.println("Hashing files ("+set.size()+")...");
		
		String outputFolder = infoFolder+"/dependency_analysis/";
		String outputFilename = outputFolder+name+".xml";
		File outputF = new File(outputFilename);

		PrintWriter pout = new PrintWriter(new OutputStreamWriter(
				new BufferedOutputStream(new FileOutputStream(outputF)), "UTF-8"));
		try {
			pout.println("<component name=\""+name+"\" desc=\"\">\n");

			ExecutorService executor = Executors.newFixedThreadPool(2);
			List<Future<String>> results = new ArrayList<Future<String>>();
			
			int t = 0;
			Iterator<String> i = set.iterator();
			while (t < set.size()) {
				String strings[] = new String[Math.min(set.size()-t, 512)];
				for (int n = 0; n < strings.length; n++) {
					strings[n] = i.next();
				}
				
				Callable<String> task = new FileHashingTask(strings, rootFolder, mapper);
				results.add(executor.submit(task));
				t += strings.length;
			}
			set.clear();
			
			for (Future<String> f : results) {
				try {
					pout.print(f.get());
				} catch (ExecutionException e) {
					Log.w("Exception getting hash results", e);
				}
			}

			executor.shutdown();			
			
			pout.println("</component>\n");
		} catch (InterruptedException e) {
			Log.w("Wait for hashing thread pool interrupted", e);
		} finally {
			pout.close();
		}		
	}
	
	private void addFolder(Set<String> globalResources, String sourceFolder, String targetFolder) {
		File folder = new File(rootFolder+"/"+sourceFolder);
		if (folder.exists()) {
			for (File f : folder.listFiles()) {
				if (f.isDirectory()) {
					addFolder(globalResources, sourceFolder+'/'+f.getName(), targetFolder+'/'+f.getName());
				} else {
					globalResources.add(targetFolder+"/"+f.getName());
				}
			}
		} else {
			Log.w("Folder not found: " + folder.getAbsolutePath());
		}
	}
	
	protected void analyzeResources(String folder, FileInfo info) throws IOException {
		String text = FileUtil.read(new File(folder, info.getFilename()));
		for (String line : text.split("\\\n")) {
			String parts[] = line.trim().split(" ");
			
			if (parts.length >= 2) {
				if (parts[0].equals("bgload")) {
					info.addBackground(parts[1]);
				} else if (parts[0].equals("setimg")) {
					info.addForeground(parts[1]);
				} else if (parts[0].equals("sound")) {
					info.addSound(parts[1]);
				} else if (parts[0].equals("music")) {
					info.addMusic(parts[1]);
				} else if (parts[0].equals("jump")) {
					info.addJump(parts[1]);
				}
			}
		}
	}
	
	//Getters
	
	//Setters
	protected void setRoute(Map<String, FileInfo> resources, Map<String, GraphNode> nodes,
			String filename, int route)
	{
		if (filename.equals("main.scr") || filename.startsWith("special/")) {
			return;
		}
		
		FileInfo info = resources.get(filename);
		GraphNode node = nodes.get(filename);
		
		if (info == null || info.getRoute() == route) {
			return;
		}
		if (info.getRoute() != 0) {
			System.err.println(filename+" route="+info.getRoute()+"+"+route);
		}		
		
		info.setRoute(route);
		for (GraphNode out : node.outgoing) {
			setRoute(resources, nodes, out.getName(), route);
		}
	}
	
	//Inner Classes
	private static class FileInfo {
		
		private int route;
		
		private String filename;
		private Set<String> background;
		private Set<String> foreground;
		private Set<String> sound;
		private Set<String> music;
		private Set<String> jumpsTo;
		
		public FileInfo(String filename) {
			this.filename = filename;
			
			background = new HashSet<String>();
			foreground = new HashSet<String>();
			sound = new HashSet<String>();
			music = new HashSet<String>();
			jumpsTo = new HashSet<String>();
		}
	
		//Functions
		public void addBackground(String filename) {
			if (!filename.startsWith("special/")) {
				background.add(filename);
			}
		}
		public void addForeground(String filename) {
			if (!filename.startsWith("special/")) {
				foreground.add(filename);
			}
		}
		public void addSound(String filename) {
			if (!filename.equals("~") && !filename.startsWith("special/")) {
				sound.add(filename);
			}
		}
		public void addMusic(String filename) {
			if (!filename.equals("~") && !filename.startsWith("special/")) {
				music.add(filename);
			}
		}
		public void addJump(String filename) {
			if (!filename.startsWith("special/")) {
				jumpsTo.add(filename);
			}
		}
		
		//Getters
		public int getRoute() { return route; }
		public String getFilename() { return filename; }
		
		//Setters
		public void setRoute(int r) { this.route = r; }

		//Save Support
		public void save(String filename) throws IOException {
			StringBuilder sb = new StringBuilder();
			
			sb.append(String.format("Filename: %s\n", filename));
			
			sb.append("\n\njumps:\n");
			for (String s : jumpsTo) { sb.append(s); sb.append('\n'); }
			sb.append("\n\nbackgrounds:\n");
			for (String s : background) { sb.append(s); sb.append('\n'); }
			sb.append("\n\nforegrounds:\n");
			for (String s : foreground) { sb.append(s); sb.append('\n'); }
			sb.append("\n\nsounds & voices:\n");
			for (String s : sound) { sb.append(s); sb.append('\n'); }
			sb.append("\n\nmusic:\n");
			for (String s : music) { sb.append(s); sb.append('\n'); }
			
			FileUtil.write(new File(filename), sb.toString());
		}
	}
	
	private static class GraphNode {
		
		private String name;
		
		public Set<GraphNode> incoming;
		public Set<GraphNode> outgoing;
		
		public GraphNode(String name) {
			this.name = name;
			
			incoming = new HashSet<GraphNode>();
			outgoing = new HashSet<GraphNode>();
		}
		
		public String getName() { return name; }
	}
	
	private static class FileHashingTask implements Callable<String> {
		
		private boolean hash = true;
		private String files[];
		private String rootFolder;
		private FileMapper nameMapping;
		
		public FileHashingTask(String files[], String rootFolder, FileMapper nameMapping) {
			this.files = files;
			this.rootFolder = rootFolder;
			this.nameMapping = nameMapping;
		}
		
		@Override
		public String call() throws Exception {
			StringBuilder sb = new StringBuilder(64 << 10);
			for (String file : files) {
				try {
					String hash = hash(file);
					if (hash != null) {
						sb.append(hash);
					}
				} catch (Exception e) {
					Log.w("Exception generating hash: " + file, e);
				}
			}
			return sb.toString();
		}
		
		private String hash(String filename) throws Exception {
			try {
				int fsize = 0;
				String fhash = "";
				if (hash) {
					String rname = nameMapping.getOriginal(filename.substring(filename.lastIndexOf('/')+1));
					if (rname == null) {
						rname = filename;
					}
					
					File file = new File(rootFolder+"/"+rname);
					fsize = (int)file.length();
					fhash = HashUtil.hashToString(HashUtil.generateHash(file));
				}
									
				filename = filename.replaceAll("background/", "background.zip/background/");
				filename = filename.replaceAll("foreground/", "foreground.zip/foreground/");
				filename = filename.replaceAll("sound/", "sound.zip/sound/");
				filename = filename.replaceAll("script/", "script.zip/script/");
				filename = filename.replaceAll("music/", "sound/");
				
				if (hash) {
					return String.format("\t<file url=\"base_install/%s\" size=\"%d\" hash=\"%s\" path=\"%s\"/>\n", filename, fsize, fhash, filename);
				} else {
					return String.format("\t<file url=\"base_install/%s\" path=\"%s\"/>\n", filename, filename);
				}
			} catch (FileNotFoundException fnfe) {
				//System.err.println(fnfe);
			}
			
			return null;
		}

	}
	
}
