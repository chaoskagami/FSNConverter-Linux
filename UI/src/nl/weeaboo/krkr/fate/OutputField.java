package nl.weeaboo.krkr.fate;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import nl.weeaboo.string.HtmlUtil;

@SuppressWarnings("serial")
public class OutputField extends JPanel {

	private JLabel textArea;
	private JScrollPane scrollPane;
	
	public OutputField() {
		textArea = createTextArea();
		
		scrollPane = new JScrollPane(textArea);
		scrollPane.getViewport().setBackground(new Color(241, 241, 241));

		setPreferredSize(new Dimension(400, 150));
		setLayout(new BorderLayout(2, 2));
		add(scrollPane, BorderLayout.CENTER);
	}
	
	protected JLabel createTextArea() {
		JLabel label = new JLabel();
		label.setBorder(new EmptyBorder(5, 5, 5, 5));
		label.setVerticalTextPosition(JLabel.TOP);
		label.setVerticalAlignment(JLabel.TOP);
		return label;
	}
	
	protected JProgressBar createGlobalProgressBar() {
		JProgressBar bar = new JProgressBar(0, 1000);
		return bar;
	}
	protected JProgressBar createLocalProgressBar() {
		JProgressBar bar = new JProgressBar(0, 100);
		return bar;
	}
	
	//Functions
	public void setText(String t) {
		textArea.setText("<html>" + HtmlUtil.escapeHtml(t) + "</html>");
		textArea.validate();

		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				JScrollBar bar = scrollPane.getVerticalScrollBar();
				if (bar != null) {
					bar.setValue(bar.getMaximum());
				}
			}
		});
		
		repaint();		
	}
	
	//Getters
	
	//Setters
	
}
