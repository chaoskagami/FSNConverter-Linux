package nl.weeaboo.krkr.fate;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import nl.weeaboo.awt.AwtUtil;
import nl.weeaboo.awt.FileBrowseField;
import nl.weeaboo.common.Dim;
import nl.weeaboo.krkr.fate.FateScriptConverter.Language;
import nl.weeaboo.vnds.AbstractConversionGUI;

@SuppressWarnings("serial")
public class ConversionGUI extends AbstractConversionGUI {

	private static final String version = "1.2.5";

	/*
	 * Changes:
	 * 
	 * 2013/03/19 -- v1.2.5
	 * - Colored background effects didn't scale to higher resolutions properly.
	 * 
	 * 2012/12/08 -- v1.2.4
	 * - Sprite scaling was incorrect
	 * 
	 * 2012/04/29 -- v1.2.3
	 * - Fixed Heaven's Feel unlock flag not getting set
	 * 
	 * 2011/09/22 -- v1.2.2
	 * - Dumb typo
	 *
	 * 2011/09/22 -- v1.2.1
	 * - Android conversion uses Ogg-Vorbis for all audio
	 * - Support for pre-installed voice data without using a Realta Nua disc
	 * 
	 * 2011/04/03 -- v1.2.0
	 * - Support for Android and high-res output
	 */
	
	protected final FileBrowseField realtaNuaField;
	protected final JComboBox languageCombo;
	
	private Language lang;
	
	public ConversionGUI() {
		super("Fate/Stay Night -> VNDS Conversion GUI v" + version,
				ConversionGUI.class.getResource("res/icon.png"),
				new File(""),
				new File(""),
				"fate",
				true,
				new Dim(800, 600));

		realtaNuaField = FileBrowseField.writeFolder("", new File(""));

		languageCombo = new JComboBox(Language.values());
		languageCombo.setSelectedItem(Language.EN);
	}

	public static void main(String args[]) {
		AwtUtil.setDefaultLAF();
		System.setProperty("line.separator", "\n");

		new ConversionGUI().create();
	}
	
	@Override
	protected void fillPathsPanel(JPanel panel) {
		super.fillPathsPanel(panel);
		
		panel.add(new JLabel("Realta Nua (Optional)")); panel.add(realtaNuaField);		
	}

	@Override
	protected void fillSettingsPanel(JPanel panel) {
		panel.add(new JLabel("Language")); panel.add(languageCombo);		
		
		super.fillSettingsPanel(panel);		
	}
	
	@Override
	protected boolean preConvertCheck(File gameFolder, File outputFolder) {
		lang = (Language)languageCombo.getSelectedItem();
		
		return super.preConvertCheck(gameFolder, outputFolder);
	}	
	
	@Override
	protected void callResourceConverter(String templateFolder, String srcFolder,
			String dstFolder, String... args)
	{
		String[] merged = new String[args.length+2];
		merged[0] = srcFolder;
		merged[1] = dstFolder;
		System.arraycopy(args, 0, merged, merged.length-args.length, args.length);
		FateResourceConverter.main(merged);
	}
	
	@Override
	protected void callScriptConverter(String srcFolder, String dstFolder) {
		List<String> list = new ArrayList<String>();
		list.add(srcFolder);
		list.add(dstFolder);
		list.add(lang.name());
		FateScriptConverter.main(list.toArray(new String[0]));
	}

	@Override
	protected void callPacker(String srcFolder, String dstFolder) {
		FatePacker.main(new String[] {srcFolder, dstFolder});
	}

}
