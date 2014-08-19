package nl.weeaboo.vnds.tools;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.EmptyBorder;

import nl.weeaboo.awt.AwtUtil;
import nl.weeaboo.awt.DirectValidatingField;
import nl.weeaboo.awt.FileBrowseField;
import nl.weeaboo.awt.Sash;
import nl.weeaboo.vnds.Log;
import nl.weeaboo.vnds.ProgressListener;
import nl.weeaboo.vnds.ProgressRunnable;
import nl.weeaboo.vnds.VNDSProgressDialog;
import nl.weeaboo.vnds.tools.ImageConverter.ConvertType;
import nl.weeaboo.vnds.tools.ImageConverter.DitheringType;
import nl.weeaboo.vnds.tools.ImageConverter.ScalingType;

/*
 * Changes:
 *
 * 2009/11/08 -- v1.0.5
 * - Code cleanup
 * - Converts input files even if they're already the correct file type
 *
 * 2009/05/01 -- v1.0.4
 * - Added RAW_RGB256 mode
 *
 * 2009/02/28 -- v1.0.3
 * - Using external programs to create better compressed images that also
 *   actually work on vnds. (cjpeg, pngquant, pngnq, pngcrush)
 *
 * 2009/02/17 -- v1.0.2
 * - Fixed bug in Floyd-Steinberg dithering
 * - Maybe fixed JPG's not working in vnds
 *
 * 2008/12/20 -- v1.0.1
 * - Drag images into the window to convert
 *
 * 2008/11/25 -- v1.0
 * - Initial Release
 * 
 */
@SuppressWarnings("serial")
public class ImageConverterGUI extends JFrame {

	private ImageConverter converter;
	
	private JComboBox modeCombo;
	private JComboBox scalingCombo;
	private JTextField widthField;
	private JTextField heightField;
	private JSpinner qualitySpinner;
	private JComboBox ditheringCombo;
	private JCheckBox loggingCheck;
	private JSpinner threadsSpinner;
	private FileBrowseField browseField;
	private JButton convertButton;
	
	public ImageConverterGUI() {
		converter = new ImageConverter();
		
		setTitle("VNDS Image Converter v1.0.5");
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
		
		if (args.length > 0) {
			try {
				ImageConverter.main(args);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			new ImageConverterGUI();
		}
	}
	
	protected JPanel createCenterPanel() {
		JLabel modeLabel = new JLabel("Output Type");
		modeCombo = new JComboBox(ImageConverter.ConvertType.values());
		modeCombo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ConvertType mode = (ConvertType)modeCombo.getSelectedItem();
				converter.setMode(mode);
				qualitySpinner.setEnabled(mode == ConvertType.TYPE_JPG);
			}
		});

		JLabel scalingLabel = new JLabel("Scaling Mode");
		scalingCombo = new JComboBox(ImageConverter.ScalingType.values());
		scalingCombo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				ScalingType s = (ScalingType)scalingCombo.getSelectedItem();
				converter.setScalingType(s);
			}
		});
		
		JLabel sizeLabel = new JLabel("Scaling Mode Size");
		widthField = new DirectValidatingField() {
			public boolean isValid(String text) {
				try {
					int i = Integer.parseInt(text);
					return (i > 0 && i <= 2048);
				} catch (NumberFormatException nfe) {					
				}
				return false;
			}
			protected void onValidTextEntered(String text) {
				int w = Integer.parseInt(text);
				converter.setSourceScreenSize(w, converter.getSourceScreenSize().height);
			}
		};
		heightField = new DirectValidatingField() {
			public boolean isValid(String text) {
				try {
					int i = Integer.parseInt(text);
					return (i > 0 && i <= 2048);
				} catch (NumberFormatException nfe) {					
				}
				return false;
			}
			protected void onValidTextEntered(String text) {
				int h = Integer.parseInt(text);
				converter.setSourceScreenSize(converter.getSourceScreenSize().width, h);
			}
		};
		JLabel qualityLabel = new JLabel("JPEG Quality");
		qualitySpinner = new JSpinner(new SpinnerNumberModel(98, 0, 100, 1));
		qualitySpinner.setEnabled(false);

		JLabel ditheringLabel = new JLabel("Dithering");
		ditheringCombo = new JComboBox(new Object[] {DitheringType.NONE,
				DitheringType.FLOYD_STEINBERG, DitheringType.RANDOM});
		ditheringCombo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				DitheringType dt = (DitheringType)ditheringCombo.getSelectedItem();
				converter.setDitheringType(dt);
			}
		});

		JLabel loggingLabel = new JLabel("Write logfile");
		loggingCheck = new JCheckBox();
		JLabel threadsLabel = new JLabel("Threads");
		threadsSpinner = new JSpinner(new SpinnerNumberModel(8, 1, 128, 1));
		
		JPanel sizePanel = new JPanel(new GridLayout(-1, 3, 5, 5));
		sizePanel.add(widthField);
		sizePanel.add(new JLabel(" x ", JLabel.CENTER));
		sizePanel.add(heightField);
		
		JPanel panel = new JPanel(new GridLayout(-1, 2, 15, 5));
		panel.add(modeLabel); panel.add(modeCombo);
		panel.add(scalingLabel); panel.add(scalingCombo);
		panel.add(sizeLabel); panel.add(sizePanel);
		panel.add(qualityLabel); panel.add(qualitySpinner);
		panel.add(ditheringLabel); panel.add(ditheringCombo);
		panel.add(loggingLabel); panel.add(loggingCheck);
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

		new DropTarget(panel2, new DropTargetListener() {
			public void dragEnter(DropTargetDragEvent dtde) {
			}
			public void dragExit(DropTargetEvent dte) {
			}
			public void dragOver(DropTargetDragEvent dtde) {
				for (DataFlavor df : dtde.getCurrentDataFlavorsAsList()) {
					if (df.isFlavorJavaFileListType()) {
						dtde.acceptDrag(DnDConstants.ACTION_COPY_OR_MOVE);
						break;
					}
				}
			}
			@SuppressWarnings("unchecked")
			public void drop(DropTargetDropEvent dtde) {
				dtde.acceptDrop(DnDConstants.ACTION_COPY_OR_MOVE);
				
				Transferable t = dtde.getTransferable();				
				for (DataFlavor df : t.getTransferDataFlavors()) {
					if (df.isFlavorJavaFileListType()) {
						try {
							List<File> list = (List<File>)t.getTransferData(df);
							convert(list);
						} catch (UnsupportedFlavorException e) {
							Log.w("Drag&Drop Exception", e);
						} catch (IOException e) {
							Log.w("Drag&Drop Exception", e);
						}						
					}					
				}
			}
			public void dropActionChanged(DropTargetDragEvent dtde) {
			}
		});

		return panel2;
	}
	
	public void convert() {
		convert(null);
	}		
	public void convert(final List<File> files) {
		final File folder = browseField.getFile();
		if (files == null) {
			if (!folder.exists() || !folder.isDirectory()) {
				JOptionPane.showMessageDialog(null, "Invalid directory: \"" + folder + "\"",
						"Error", JOptionPane.ERROR_MESSAGE);
				return;
			}
		}

		converter.setMode((ConvertType)modeCombo.getSelectedItem());
		converter.setScalingType((ScalingType)scalingCombo.getSelectedItem());
		converter.setQuality((Integer)qualitySpinner.getValue());
		converter.setLogging(loggingCheck.isSelected());
		converter.setMaxThreads((Integer)threadsSpinner.getValue());
		
		ProgressListener pl = new ProgressListener() {
			public void onFinished(String message) {
				String logPath = "";
				String logPart = "";
				
				try {
					if (converter.isLogging()) {
						logPath = converter.dumpLog("conversion.log").getAbsolutePath();
						logPart = String.format("<br><br> Log dumped to %s", logPath);
					}
					
					JOptionPane.showMessageDialog(null, String.format(
							"<html>Conversion finished.%s</html>", logPart),
							"Finished", JOptionPane.PLAIN_MESSAGE);
				} catch (IOException e) {
					AwtUtil.showError(e);
				}				
			}
			public void onProgress(int value, int max, String message) {
			}
		};
		
		ProgressRunnable task = new ProgressRunnable() {
			public void run(ProgressListener pl) {
				if (files == null) {
					try {
						converter.convertFolder(folder.toString(), pl);
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else {
					for (File f : files) {
						converter.convertFile(f);
					}
				}
			}
		};
		
		VNDSProgressDialog dialog = new VNDSProgressDialog();
		dialog.showDialog(task, pl);
	}
	
	public void reset() {
		widthField.setText("800");
		heightField.setText("600");
		qualitySpinner.setValue(converter.getQuality());
		threadsSpinner.setValue(converter.getMaxThreads());
	}
	
	//Getters
	
	//Setters
	
}
