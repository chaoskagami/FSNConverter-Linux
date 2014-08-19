package nl.weeaboo.vnds.installer;

abstract class PackCommand {
		
	private String outputFolder;
	
	public PackCommand(String outputFolder) {
		this.outputFolder = outputFolder;
	}
	
	//Functions
	public abstract void execute();
	
	//Getters
	public String getOutputFolder() {
		return outputFolder;
	}
	
	//Setters
	
}
