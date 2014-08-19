package nl.weeaboo.vnds.installer;

import java.io.File;
import java.io.IOException;

import nl.weeaboo.vnds.Log;
import nl.weeaboo.xml.XmlElement;
import nl.weeaboo.xml.XmlReader;

import org.xml.sax.SAXException;

public class AddCommand extends PackCommand {

	private String xmlPath;
	
	public AddCommand(String xmlPath, String outputFolder) {
		super(outputFolder);
		
		this.xmlPath = xmlPath;
	}
	
	//Functions
	public void execute() {
		try {
			File file = new File(xmlPath);
			XmlReader xmlReader = new XmlReader();
			XmlElement componentE = xmlReader.read(file).getChild("component");
			
			Component c = Component.fromXml(file.getParentFile().getAbsolutePath(), componentE);
			c.save(new File(getOutputFolder(), file.getName()));
		} catch (SAXException e) {
			Log.e("Exception executing add command: " + xmlPath, e);
		} catch (IOException ioe) {
			Log.e("Exception executing add command: " + xmlPath, ioe);
		}
	}
	
	//Getters
	
	//Setters
	
}
