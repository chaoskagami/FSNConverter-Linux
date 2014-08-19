package nl.weeaboo.vnds;

import java.awt.BorderLayout;
import java.awt.Dimension;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import nl.weeaboo.awt.AwtUtil;

public class VNDSProgressDialog {

	private JPanel panel;
	private JLabel messageLabel;
	private JProgressBar progressBar;
	
	public VNDSProgressDialog() {		
		messageLabel = new JLabel("Initializing...");
		progressBar = new JProgressBar();

		panel = new JPanel(new BorderLayout(5, 5));
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.add(messageLabel, BorderLayout.CENTER);
		panel.add(progressBar, BorderLayout.SOUTH);
	}
	
	public void showDialog(final ProgressRunnable task, final ProgressListener pl) {
		final JDialog dialog = new JDialog();
		dialog.setResizable(false);
		dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
		dialog.setAlwaysOnTop(true);
		
		panel.setPreferredSize(new Dimension(400, 60));
		dialog.add(panel);			
		dialog.pack();
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);

		final ProgressListener progressListener = new ProgressListener() {
			public void onProgress(final int value, final int max, final String message) {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						progressBar.setMaximum(max);
						progressBar.setValue(value);						
						messageLabel.setText(message);
						
						if (pl != null) {
							pl.onProgress(value, max, message);
						}
					}
				});
			}
			public void onFinished(final String message) {
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						progressBar.setValue(progressBar.getMaximum());
						
						if (pl != null) {
							pl.onFinished(message);
						}
					}
				});
			}
		};
		
		Thread t = new Thread(new Runnable() {
			public void run() {
				try {
					((ProgressRunnable)task).run(progressListener);
				} catch (Exception e) {
					AwtUtil.showError(e);
				}
				SwingUtilities.invokeLater(new Runnable() {
					public void run() {
						dialog.dispose();
					}
				});
			}
		});
		t.start();
	}
	
	//Functions
	//Getters
	//Setters
}
