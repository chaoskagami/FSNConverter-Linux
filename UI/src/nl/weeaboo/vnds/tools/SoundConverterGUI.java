package nl.weeaboo.vnds.tools;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

import nl.weeaboo.awt.AwtUtil;
import nl.weeaboo.awt.FileBrowseField;
import nl.weeaboo.awt.Sash;
import nl.weeaboo.vnds.ProgressListener;
import nl.weeaboo.vnds.ProgressRunnable;
import nl.weeaboo.vnds.VNDSProgressDialog;
import nl.weeaboo.vnds.tools.SoundConverter.ConvertType;

/*
 * Changes:
 *
 * 2009/11/08 -- v1.4.2
 * - Code cleanup
 * - Converts input files even if they're already the correct file type
 *
 * 2008/03/03 -- v1.4.1
 * - Added support for changing MP3 quality
 *
 * 2008/10/11 -- v1.4
 * - Added MP3 support
 * - Added a support for changing the number of threads used
 *
 * 2008/09/13 -- v1.3
 * - Bug sometimes caused end of soundfile to become corrupted
 * 
 * 2008/07/25 -- v1.2
 * - Logging was broken, now fixed
 * - Filenames with spaces in them weren't handled properly
 * 
 * 2008/07/24 -- v1.1
 * - ADPCM Support
 * - Logging function
 * 
 * 2008/06/21 -- v1.0
 * - Initial Release
 * 
 */
@SuppressWarnings("serial")
public class SoundConverterGUI extends JFrame {

	private SoundConverter encoder;
	
	private JComboBox modeCombo;
	private JSpinner aacQualitySpinner;
	private JSpinner mp3QualitySpinner;
	private JSpinner volumeSpinner;
	private JCheckBox toUpperCheck;
	private JSpinner threadsSpinner;
	private FileBrowseField browseField;
	private JButton convertButton;
	
	public SoundConverterGUI() {
		encoder = new SoundConverter();
		
		setTitle("VNDS Sound Converter v1.4.2");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		setLayout(new BorderLayout());
		add(createCenterPanel(), BorderLayout.CENTER);
		
		reset();
		
		setMinimumSize(new Dimension(300, 100));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
	}
	
	//Functions
	public static void main(String args[]) {
		AwtUtil.setDefaultLAF();
		
		new SoundConverterGUI();
	}
	
	protected JPanel createCenterPanel() {
		JLabel modeLabel = new JLabel("Output Type");
		modeCombo = new JComboBox(new Object[] {ConvertType.TYPE_AAC, ConvertType.TYPE_ADPCM,
				ConvertType.TYPE_MP3});
		modeCombo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ConvertType mode = (ConvertType)modeCombo.getSelectedItem();
				encoder.setMode(mode);
				aacQualitySpinner.setEnabled(mode == ConvertType.TYPE_AAC);
				mp3QualitySpinner.setEnabled(mode == ConvertType.TYPE_MP3);
			}
		});
		
		JLabel aacQualityLabel = new JLabel("AAC Quality");
		aacQualitySpinner = new JSpinner(new SpinnerNumberModel(70, 5, 995, 5));
		JLabel mp3QualityLabel = new JLabel("MP3 Bitrate");
		mp3QualitySpinner = new JSpinner(new SpinnerNumberModel(96, 8, 128, 16));
		JLabel volumeLabel = new JLabel("Volume (%)");
		volumeSpinner = new JSpinner(new SpinnerNumberModel(100, 10, 990, 10));		
		JLabel toUpperLabel = new JLabel("Filenames to uppercase?");
		toUpperCheck = new JCheckBox();
		JLabel threadsLabel = new JLabel("Threads");
		threadsSpinner = new JSpinner(new SpinnerNumberModel(8, 1, 128, 1));
		
		JPanel panel = new JPanel(new GridLayout(-1, 2, 15, 5));
		panel.add(modeLabel); panel.add(modeCombo);
		panel.add(aacQualityLabel); panel.add(aacQualitySpinner);
		panel.add(mp3QualityLabel); panel.add(mp3QualitySpinner);
		panel.add(volumeLabel); panel.add(volumeSpinner);
		panel.add(toUpperLabel); panel.add(toUpperCheck);
		panel.add(threadsLabel); panel.add(threadsSpinner);
						
		browseField = FileBrowseField.readFolder("Folder", new File(""));
		convertButton = new JButton("Convert");
		convertButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				convert();
			}
		});
		
		JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
		bottomPanel.add(new Sash(Sash.HORIZONTAL), BorderLayout.NORTH);
		bottomPanel.add(browseField, BorderLayout.CENTER);
		bottomPanel.add(convertButton, BorderLayout.EAST);
		
		JPanel panel2 = new JPanel(new BorderLayout(0, 10));
		panel2.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel2.add(panel, BorderLayout.NORTH);
		panel2.add(bottomPanel, BorderLayout.SOUTH);
		return panel2;
	}
	
	public void convert() {
		final File folder = browseField.getFile();
		if (!folder.exists() || !folder.isDirectory()) {
			JOptionPane.showMessageDialog(null, "Invalid directory: \"" + folder + "\"",
					"Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		int mp3_avgb = ((Integer)aacQualitySpinner.getValue()) / 16 * 16; //round down to nearest 16
		int mp3_minb = encoder.getMp3MinBitrate();
		int mp3_maxb = ((mp3_avgb+15) / 16) * 16; //round up to nearest 16
		
		mp3_avgb = Math.min(128, mp3_avgb);
		mp3_maxb = Math.min(128, mp3_maxb);
		
		encoder.setAacQuality((Integer)aacQualitySpinner.getValue());
		encoder.setMp3Quality(mp3_minb, mp3_maxb, mp3_avgb);
		encoder.setVolume((Integer)volumeSpinner.getValue());
		encoder.setConvertNameToUpperCase(toUpperCheck.isSelected());
		encoder.setMaxThreads((Integer)threadsSpinner.getValue());
		
		ProgressListener pl = new ProgressListener() {
			public void onFinished(String message) {
				JOptionPane.showMessageDialog(null, String.format(
						"<html>Conversion finished.</html>"),
						"Finished", JOptionPane.PLAIN_MESSAGE);
			}
			public void onProgress(int value, int max, String message) {
			}
		};
		
		ProgressRunnable task = new ProgressRunnable() {
			public void run(ProgressListener pl) {
				encoder.convertFolder(folder.toString(), pl);
			}
		};
		
		VNDSProgressDialog dialog = new VNDSProgressDialog();
		dialog.showDialog(task, pl);
	}
	
	public void reset() {
		aacQualitySpinner.setValue(encoder.getAacQuality());
		mp3QualitySpinner.setValue(encoder.getMp3AvgBitrate());
		volumeSpinner.setValue(encoder.getVolume());
		toUpperCheck.setSelected(encoder.getConvertNameToUpperCase());
		threadsSpinner.setValue(encoder.getMaxThreads());
	}
	
	//Getters
	
	//Setters
	
}
