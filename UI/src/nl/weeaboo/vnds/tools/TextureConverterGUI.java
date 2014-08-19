package nl.weeaboo.vnds.tools;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Image;
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

import javax.swing.ImageIcon;
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
import nl.weeaboo.vnds.Log;
import nl.weeaboo.vnds.ProgressListener;
import nl.weeaboo.vnds.ProgressRunnable;
import nl.weeaboo.vnds.VNDSProgressDialog;
import nl.weeaboo.vnds.tools.TextureConverter.Mode;

/*
 * Changes:
 *
 * 2009/11/?? -- v1.0
 * - Initial release
 * 
 */
@SuppressWarnings("serial")
public class TextureConverterGUI extends JFrame {

	private TextureConverter converter;
	
	private JComboBox modeCombo;
	private JCheckBox ditheringCheck;
	private JSpinner threadsSpinner;
	private FileBrowseField browseField;
	private JButton convertButton;
	
	public TextureConverterGUI() {
		converter = new TextureConverter();
		
		setTitle("NDS Texture Converter v1.0");
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				
		setLayout(new BorderLayout());
		add(createCenterPanel(), BorderLayout.CENTER);
		
		setMinimumSize(new Dimension(300, 100));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
	}
	
	//Functions
	public static void main(String args[]) {
		AwtUtil.setDefaultLAF();
		
		if (args.length > 0) {
			TextureConverter.main(args);
		} else {
			new TextureConverterGUI();
		}
	}
	
	protected JPanel createCenterPanel() {
		JLabel modeLabel = new JLabel("Format");
		modeCombo = new JComboBox(TextureConverter.Mode.values());
		modeCombo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				Mode mode = (Mode)modeCombo.getSelectedItem();
				converter.setMode(mode);
			}
		});
		modeCombo.setSelectedItem(converter.getMode());

		JLabel ditheringLabel = new JLabel("Dithering");
		ditheringCheck = new JCheckBox();
		ditheringCheck.setSelected(converter.isDithering());

		JLabel threadsLabel = new JLabel("Threads");
		threadsSpinner = new JSpinner(new SpinnerNumberModel(converter.getThreads(), 1, 128, 1));
		
		JPanel panel = new JPanel(new GridLayout(-1, 2, 15, 5));
		panel.add(modeLabel); panel.add(modeCombo);
		panel.add(ditheringLabel); panel.add(ditheringCheck);
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

		converter.setMode((Mode)modeCombo.getSelectedItem());
		converter.setDithering(ditheringCheck.isSelected());
		converter.setThreads((Integer)threadsSpinner.getValue());
		
		ProgressListener pl = new ProgressListener() {
			public void onFinished(String message) {
				JOptionPane.showMessageDialog(null, "<html>Conversion finished.",
						"Finished", JOptionPane.PLAIN_MESSAGE);
			}
			public void onProgress(int value, int max, String message) {
			}
		};
		
		ProgressRunnable task = new ProgressRunnable() {
			public void run(ProgressListener pl) {
				try {
					if (files == null) {
						converter.convertFolder(folder.toString(), pl);
					} else {
						for (File f : files) {
							converter.convertFile(f);
						}
						if (files.size() == 1) {
							Image image = converter.readImage(files.get(0).getAbsolutePath());
							JLabel label = new JLabel(new ImageIcon(image));
							
							JFrame frame = new JFrame();
							frame.getContentPane().add(label);
							frame.pack();
							frame.setLocationRelativeTo(null);
							frame.setVisible(true);
						}
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		};
		
		VNDSProgressDialog dialog = new VNDSProgressDialog();
		dialog.showDialog(task, pl);
	}
	
	//Getters
	
	//Setters
	
}
