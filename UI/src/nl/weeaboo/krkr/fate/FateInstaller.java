package nl.weeaboo.krkr.fate;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import nl.weeaboo.awt.AwtUtil;
import nl.weeaboo.awt.DirectoryChooser;
import nl.weeaboo.awt.Sash;
import nl.weeaboo.vnds.ProgressListener;
import nl.weeaboo.vnds.ProgressRunnable;
import nl.weeaboo.vnds.VNDSProgressDialog;
import nl.weeaboo.vnds.installer.Installer;

/*
 * Changes:
 *
 * 2009/03/01 -- v1.1
 * - Moved xml config files to a subfolder
 *
 * 2008/11/03 -- v1.0
 * - Initial Release
 * 
 */
@SuppressWarnings("serial")
public class FateInstaller extends JFrame {

	private ComponentCheckBox coreCheck;
	private ComponentCheckBox prologueCheck;
	private ComponentCheckBox route1Check;
	private ComponentCheckBox route2Check;
	private ComponentCheckBox route3Check;
	
	public FateInstaller() {		
		setTitle("Fate/Stay Night Installer v1.1");
		
		add(createCenterPanel());
		
		pack();
		setResizable(false);
		setLocationRelativeTo(null);
		setVisible(true);
	}
	
	//Functions
	public static void main(String args[]) {
		AwtUtil.setDefaultLAF();
		
		FateInstaller fi = new FateInstaller();
		fi.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);		
	}
	
	private JPanel createCenterPanel() {
		coreCheck = new ComponentCheckBox("Core", "_installer/core.xml");
		coreCheck.setEnabled(false);
		prologueCheck = new ComponentCheckBox("Prologue", "_installer/prologue.xml");
		route1Check = new ComponentCheckBox("Route 1: Fate", "_installer/route01-fate.xml");
		route2Check = new ComponentCheckBox("Route 2: UBW", "_installer/route02-ubw.xml");
		route3Check = new ComponentCheckBox("Route 3: HF", "_installer/route03-hf.xml");
		
		JPanel panel2 = new JPanel(new GridLayout(-1, 1, 5, 5));
		panel2.add(coreCheck);
		panel2.add(prologueCheck);
		panel2.add(route1Check);
		panel2.add(route2Check);
		panel2.add(route3Check);

		JButton installButton = new JButton("Install");
		installButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				DirectoryChooser dc = new DirectoryChooser(true);
				if (!dc.showDialog(FateInstaller.this, "Choose a folder to install to...")) {
					return;
				}
				final String installFolder = dc.getSelectedDirectory().getAbsolutePath()+"/fate/";				
				final List<String> files = new ArrayList<String>();
				
				if (coreCheck.isSelected()) files.add(coreCheck.getPath());
				if (prologueCheck.isSelected()) files.add(prologueCheck.getPath());
				if (route1Check.isSelected()) files.add(route1Check.getPath());
				if (route2Check.isSelected()) files.add(route2Check.getPath());
				if (route3Check.isSelected()) files.add(route3Check.getPath());
				
				ProgressListener pl = new ProgressListener() {
					public void onFinished(String message) {
						JOptionPane.showMessageDialog(null, String.format(
								"<html>Installation finished.<br>Installed to: %s</html>", installFolder),
								"Finished", JOptionPane.PLAIN_MESSAGE);
					}
					public void onProgress(int value, int max, String message) {
					}
				};
				
				ProgressRunnable task = new ProgressRunnable() {
					public void run(ProgressListener pl) {
						final String errors = Installer.install(installFolder, pl, files.toArray(new String[0]));
						
						if (errors.trim().length() > 0) {
							SwingUtilities.invokeLater(new Runnable() {
								public void run() {
									JOptionPane.showMessageDialog(null,
											String.format("<html>%s</html>", errors.replaceAll("\\\n", "<br>")),
											"Error", JOptionPane.ERROR_MESSAGE);
								}
							});
						}
					}
				};				
				VNDSProgressDialog dialog = new VNDSProgressDialog();
				dialog.showDialog(task, pl);
			}
		});
		
		JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
		buttonPanel.add(installButton);
		
		JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
		bottomPanel.add(new Sash(Sash.HORIZONTAL), BorderLayout.NORTH);
		bottomPanel.add(buttonPanel, BorderLayout.CENTER);
		
		JPanel panel = new JPanel(new BorderLayout(10, 10));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.add(panel2, BorderLayout.CENTER);
		panel.add(bottomPanel, BorderLayout.SOUTH);
		return panel;
	}
	
	//Getters
	
	//Setters
	
	//Inner Classes
	private static class ComponentCheckBox extends JPanel {
	
		private String path;
		private JCheckBox check;
		
		public ComponentCheckBox(String labelString, String path) {
			this.path = path;
			
			JLabel label = new JLabel(labelString);
			label.setPreferredSize(new Dimension(100, 20));
			check = new JCheckBox();
			check.setSelected(true);
			
			setLayout(new BorderLayout(10, 0));
			add(label, BorderLayout.WEST);
			add(check, BorderLayout.CENTER);
		}
		
		public boolean isSelected() {
			return check.isSelected();
		}
		public String getPath() {
			return path;
		}
		
		public void setEnabled(boolean e) {
			super.setEnabled(e);
			check.setEnabled(e);
		}
	}
}
