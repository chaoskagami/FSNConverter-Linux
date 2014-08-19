package nl.weeaboo.vnds;

public interface ProgressListener {

	//Functions
	
	/**
	 * @param max Maximum progress, <code>-1</code> if unknown.
	 */
	public void onProgress(int value, int max, String message);
	
	public void onFinished(String message);
	
	//Getters
	
	//Setters
	
}
