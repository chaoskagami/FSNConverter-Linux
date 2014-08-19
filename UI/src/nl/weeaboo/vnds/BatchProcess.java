package nl.weeaboo.vnds;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class BatchProcess {

	private int threads;
	private int threadPriority;
	private int taskSize;
	private List<ProgressListener> listeners;
		
	private ExecutorService executor;
	private volatile int progress;
	private int maxProgress;
	
	public BatchProcess() {
		this(1, 8);
	}
	public BatchProcess(int threads, int tasksize) {
		this.threads = threads;
		this.threadPriority = Thread.NORM_PRIORITY;
		this.taskSize = tasksize;
		this.listeners = new ArrayList<ProgressListener>(2);
	}
	
	//Functions
	public void addProgressListener(ProgressListener pl) {
		listeners.add(pl);
	}
	public void removeProgressListener(ProgressListener pl) {
		listeners.remove(pl);
	}
	
	public void run(Map<String, File> fileMap, FileOp op) throws InterruptedException {
		start(fileMap, op);
		stop();
	}

	public void start(Map<String, File> fileMap, FileOp op) {
		try {
			stop();
		} catch (InterruptedException e) {
			
		}
		
		executor = Executors.newFixedThreadPool(threads, new ThreadFactory() {
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setPriority(threadPriority);
				return t;
			}
		});
		
		progress = 0;
		maxProgress = fileMap.size();
		
		int stepSize = taskSize;
		if (stepSize * threads > fileMap.size()) {
			stepSize = Math.max(1, fileMap.size() / threads); 
		}

		ProgressListener ls[] = listeners.toArray(new ProgressListener[listeners.size()]);
		
		Iterator<Entry<String, File>> i = fileMap.entrySet().iterator();
		while (i.hasNext()) {			
			final String relpaths[] = new String[stepSize];
			final File files[] = new File[stepSize];
			
			int t = 0;
			while (t < stepSize && i.hasNext()) {
				Entry<String, File> entry = i.next();
				relpaths[t] = entry.getKey();
				files[t] = entry.getValue();
				t++;
			}
			
			executor.submit(new Task(op, relpaths, files, t, ls));
		}
	}
	
	public void stop() throws InterruptedException {
		if (executor != null) {
			executor.shutdown();
			executor.awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
			
			ProgressListener ls[] = listeners.toArray(new ProgressListener[listeners.size()]);
			for (ProgressListener pl : ls) {
				pl.onFinished("Finished");
			}
			
			executor = null;
		}
	}
		
	//Getters
	public int getThreads() { return threads; }
	public int getThreadPriority() { return threadPriority; }
	public int getTaskSize() { return taskSize; }
	
	//Setters
	public void setThreads(int t) {
		if (t <= 0) {
			t = Runtime.getRuntime().availableProcessors();
		}
		threads = t;
	}
	public void setThreadPriority(int p) {
		threadPriority = p;
	}
	public void setTaskSize(int ts) {
		taskSize = ts;
	}

	//Inner Classes
	private class Task implements Callable<Integer> {

		private final FileOp fop;
		private final String relpaths[];
		private final File files[];
		private final int len;
		private final ProgressListener listeners[];
		
		public Task(FileOp op, String rps[], File fs[], int t, ProgressListener pls[]) {
			fop = op;
			relpaths = rps;
			files = fs;
			len = t;
			listeners = pls;
		}

		@Override
		public Integer call() throws Exception {
			int t = 0;
			try {
				while (t < len) {
					try {
						fop.execute(relpaths[t], files[t]);
					} catch (Throwable e) {
						Log.w("Uncaught exception processing: " + relpaths[t], e);
						//throw e;
					}
					t++;
				}
			} finally {			
				synchronized (BatchProcess.this) {
					progress += len;
					if (listeners.length > 0 && len > 0) {
						for (ProgressListener pl : listeners) {
							pl.onProgress(progress, maxProgress, "");
						}
					}
				}
			}
			return t;
		}
		
	}
	
}
