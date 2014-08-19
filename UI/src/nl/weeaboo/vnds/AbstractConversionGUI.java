package nl.weeaboo.vnds;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import nl.weeaboo.awt.AwtUtil;
import nl.weeaboo.awt.FileBrowseField;
import nl.weeaboo.awt.IconListCellRenderer;
import nl.weeaboo.awt.Sash;
import nl.weeaboo.common.Dim;
import nl.weeaboo.common.ScaleUtil;
import nl.weeaboo.common.StringUtil;
import nl.weeaboo.io.FileUtil;
import nl.weeaboo.vnds.tools.ImageConverter.ConvertType;
import nl.weeaboo.vnds.tools.SoundConverter;

@SuppressWarnings("serial")
public abstract class AbstractConversionGUI extends JFrame implements Runnable {

	protected final String gameFolderName;
	protected final boolean hasVoices;
	private Dim originalSize;
	
	private JPanel propertyPanel;
	protected final FileBrowseField gameFolderField;
	protected final FileBrowseField outputFolderField;
	protected final JComboBox targetCombo;
	protected final JCheckBox cleanTempFilesButton;
	protected final JSpinner threadsSpinner;
	protected final JComboBox pngQuantCombo;
	protected final JSpinner musicVolumeSpinner;
	protected final JSpinner sfxVolumeSpinner;
	protected final JCheckBox voiceCheck;
	
	private final JSpinner jpgQualitySpinner;
	private final JLabel mp3AverageBitrateLabel;
	private final JSpinner mp3AverageBitrateSpinner;
	private final JLabel mp3MaxBitrateLabel;
	private final JSpinner mp3MaxBitrateSpinner;
	private final JLabel aacQualityLabel;
	private final JSpinner aacQualitySpinner;
	private final JLabel vorbisQualityLabel;
	private final JComboBox vorbisQualityCombo;
	
	private JTextArea outputField;
	private JScrollPane outputFieldScrollPane;
	private JButton startButton;
	
	public AbstractConversionGUI(String title, URL iconURL, File defaultSrcF, File defaultDstF,
			String gameFolderName, boolean hasVoices, Dim originalSize)
	{
		this.gameFolderName = gameFolderName;
		this.hasVoices = hasVoices;
		this.originalSize = originalSize;
		
		setTitle(title);
		try {
			BufferedImage icon = ImageIO.read(iconURL);
			AwtUtil.setFrameIcon(this, icon);
		} catch (IOException ioe) {
			Log.w("Exception settings frame icon", ioe);
		}
		
		outputField = new JTextArea(15, 60);
		outputField.setEditable(false);
		outputFieldScrollPane = new JScrollPane(outputField);
		outputFieldScrollPane.setVisible(false);		
		
		startButton = new JButton("Start");
		startButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				if (!preConvertCheck(gameFolderField.getFile(),
						outputFolderField.getFile()))
				{
					return;
				}
				
				OutputStream outstream = new OutputStream() {
					private final int CHUNKS_LEN_MAX = 128 << 10;
					private ArrayDeque<Integer> chunkLengths = new ArrayDeque<Integer>();
					private int chunksLengthsTotal = 0;
					private ByteBuffer buf = ByteBuffer.allocate(1024);
					
					public synchronized void write(int b) throws IOException {
						buf.put((byte)b);
						if (b == '\n' || !buf.hasRemaining()) {
							{
								final String line = StringUtil.fromUTF8(buf.array(), buf.arrayOffset(), buf.position());
								chunkLengths.add(line.length());
								chunksLengthsTotal += line.length();
								buf.rewind();
								
								SwingUtilities.invokeLater(new Runnable() {
									public void run() {
										outputField.append(line);
										
										JScrollBar vertical = outputFieldScrollPane.getVerticalScrollBar();
										if (vertical != null) {
											vertical.setValue(vertical.getMaximum());
										}
									}
								});								
							}

							int removed = 0;
							while (chunkLengths.size() > 1 && chunksLengthsTotal > CHUNKS_LEN_MAX) {
								removed += chunkLengths.removeFirst();
							}
							chunksLengthsTotal -= removed;
							
							final int endpos = removed;
							SwingUtilities.invokeLater(new Runnable() {
								public void run() {
									outputField.replaceRange("", 0, endpos);
								}
							});
						} 
					}
				};
				
				try {
					System.setOut(new PrintStream(outstream, true, "UTF-8"));
					System.setErr(new PrintStream(outstream, true, "UTF-8"));					
				} catch (UnsupportedEncodingException uee) {
					Log.w("Changing sysout and syserr not supported", uee);
					
					System.setOut(new PrintStream(outstream));
					System.setErr(new PrintStream(outstream));
				}
				Log.initDefaultHandler(); //Needs to be done since System.err has changed

				setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
				propertyPanel.setVisible(false);
				outputFieldScrollPane.setVisible(true);
				startButton.setVisible(false);
				pack();
				
				Thread t = new Thread(AbstractConversionGUI.this);
				t.start();
			}
		});
		
		gameFolderField = FileBrowseField.writeFolder("", defaultSrcF);		
		outputFolderField = FileBrowseField.writeFolder("", defaultDstF);

		targetCombo = new JComboBox(TargetPlatform.values());
		targetCombo.setSelectedItem(TargetPlatform.NINTENDO_DS);
		targetCombo.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				onTargetChanged((TargetPlatform)targetCombo.getSelectedItem());
			}
		});
		
		pngQuantCombo = new JComboBox(new Object[] {
				ConvertType.TYPE_PNG, ConvertType.TYPE_PNG_256_NEUQUANT, ConvertType.TYPE_PNG_256_MEDIAN
		});
		pngQuantCombo.setRenderer(new IconListCellRenderer() {
			public String getLabelFor(Object object) {
				if (object instanceof ConvertType) {
					ConvertType ct = (ConvertType)object;
					if (ct == ConvertType.TYPE_PNG) return "Full Color (Default)";
					else if (ct == ConvertType.TYPE_PNG_256_NEUQUANT) return "256 Colors (Neuquant)";
					else if (ct == ConvertType.TYPE_PNG_256_MEDIAN) return "256 Colors (Median)";
				}
				return null;
			}
		});
		//pngQuantCombo.setSelectedItem(ConvertType.TYPE_PNG_256_NEUQUANT);
		pngQuantCombo.setSelectedItem(ConvertType.TYPE_PNG);
		
		jpgQualitySpinner = new JSpinner(new SpinnerNumberModel(97, 0, 100, 1));
		mp3AverageBitrateLabel = new JLabel("MP3 Avg Bitrate");
		mp3AverageBitrateSpinner = new JSpinner(new SpinnerNumberModel(64, 16, 128, 16));
		mp3MaxBitrateLabel = new JLabel("MP3 Max Bitrate");
		mp3MaxBitrateSpinner = new JSpinner(new SpinnerNumberModel(96, 16, 128, 16));
		aacQualityLabel = new JLabel("AAC Quality Factor");
		aacQualitySpinner = new JSpinner(new SpinnerNumberModel(SoundConverter.AAC_Q_HIGH, 10, 200, 5));		
		vorbisQualityLabel = new JLabel("Vorbis Audio Quality");
		vorbisQualityCombo = new JComboBox(new Object[] {SoundConverter.VORBIS_Q_LOW, SoundConverter.VORBIS_Q_MED, SoundConverter.VORBIS_Q_HIGH});
		vorbisQualityCombo.setSelectedItem(SoundConverter.VORBIS_Q_MED);
		vorbisQualityCombo.setRenderer(new IconListCellRenderer() {
			public String getLabelFor(Object object) {
				if (object instanceof Number) {
					int val = ((Number)object).intValue();
					if (val == SoundConverter.VORBIS_Q_LOW)  return "Low (-aq " + val + ")";
					if (val == SoundConverter.VORBIS_Q_MED)  return "Med (-aq " + val + ")";
					if (val == SoundConverter.VORBIS_Q_HIGH) return "High (-aq " + val + ")";
				}
				return super.getLabelFor(object);
			}
		});
		
		musicVolumeSpinner = new JSpinner(new SpinnerNumberModel(100, 10, 500, 25));
		sfxVolumeSpinner = new JSpinner(new SpinnerNumberModel(100, 10, 500, 25));
				
		voiceCheck = new JCheckBox();
		voiceCheck.setSelected(hasVoices);
		
		cleanTempFilesButton = new JCheckBox();
		cleanTempFilesButton.setSelected(true);
		threadsSpinner = new JSpinner(new SpinnerNumberModel(Runtime.getRuntime().availableProcessors(), 1, 8, 1));
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setMinimumSize(new Dimension(375, 300));
		setLayout(new BorderLayout());
	}
	
	//Functions
	public void create() {
		propertyPanel = createPropertyPanel();

		add(propertyPanel, BorderLayout.NORTH);
		add(outputFieldScrollPane, BorderLayout.CENTER);
		add(startButton, BorderLayout.SOUTH);
		
		onTargetChanged((TargetPlatform)targetCombo.getSelectedItem());
		
		pack();
		setLocationRelativeTo(null);
		setVisible(true);		
	}
	
	protected void onTargetChanged(TargetPlatform platform) {
		boolean android = (platform != null && platform.isAndroid());
		
		mp3AverageBitrateLabel.setEnabled(!android);
		mp3AverageBitrateSpinner.setEnabled(!android);
		mp3MaxBitrateLabel.setEnabled(!android);
		mp3MaxBitrateSpinner.setEnabled(!android);
		aacQualityLabel.setEnabled(!android);
		aacQualitySpinner.setEnabled(!android);
		vorbisQualityLabel.setEnabled(android);
		vorbisQualityCombo.setEnabled(android);		
	}
	
	private JPanel createPropertyPanel() {		
		JPanel panel1 = new JPanel(new GridLayout(-1, 2, 5, 5));
		fillPathsPanel(panel1);
		
		JPanel settingsPanel = new JPanel(new GridLayout(-1, 2, 5, 5));
		fillSettingsPanel(settingsPanel);

		JPanel converterSettingsPanel = new JPanel(new GridLayout(-1, 2, 5, 5));
		fillConverterSettingsPanel(converterSettingsPanel);
		
		JPanel otherPanel = new JPanel(new GridLayout(-1, 2, 5, 5));
		otherPanel.add(new JLabel("Clean intermediate files?")); otherPanel.add(cleanTempFilesButton);
		otherPanel.add(new JLabel("Threads")); otherPanel.add(threadsSpinner);
		
		JPanel panel = new JPanel();
		panel.setBorder(new EmptyBorder(10, 10, 10, 10));
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		panel.add(panel1);
		panel.add(Box.createVerticalStrut(10));
		panel.add(new Sash(Sash.HORIZONTAL));
		panel.add(Box.createVerticalStrut(10));
		panel.add(settingsPanel);
		panel.add(Box.createVerticalStrut(15));
		panel.add(converterSettingsPanel);
		panel.add(Box.createVerticalStrut(10));
		panel.add(new Sash(Sash.HORIZONTAL));
		panel.add(Box.createVerticalStrut(10));
		panel.add(otherPanel);
		
		JPanel outerPanel = new JPanel(new BorderLayout());
		outerPanel.setBackground(panel.getBackground().darker());
		outerPanel.add(panel);
		return outerPanel;
	}
	
	protected void fillPathsPanel(JPanel panel) {
		panel.add(new JLabel("Game Folder")); panel.add(gameFolderField);
		panel.add(new JLabel("Output Folder")); panel.add(outputFolderField);
	}
	
	protected void fillSettingsPanel(JPanel panel) {
		panel.add(new JLabel("Target Platform")); panel.add(targetCombo);		
		
		if (hasVoices) {
			panel.add(new JLabel("Enable Voices")); panel.add(voiceCheck);
		}
	}
	
	protected void fillConverterSettingsPanel(JPanel panel) {
		panel.add(new JLabel("Music Volume")); panel.add(musicVolumeSpinner);
		panel.add(new JLabel("SFX Volume")); panel.add(sfxVolumeSpinner);
		panel.add(new JLabel("PNG Type")); panel.add(pngQuantCombo);
		panel.add(new JLabel("JPG Quality %")); panel.add(jpgQualitySpinner);
		panel.add(mp3AverageBitrateLabel); panel.add(mp3AverageBitrateSpinner);
		panel.add(mp3MaxBitrateLabel); panel.add(mp3MaxBitrateSpinner);
		panel.add(aacQualityLabel); panel.add(aacQualitySpinner);
		panel.add(vorbisQualityLabel); panel.add(vorbisQualityCombo);
	}
	
	protected boolean preConvertCheck(File gameFolder, File outputFolder) {
		String gameFolderS = outputFolder.getAbsolutePath();
		for (int n = 0; n < gameFolderS.length(); n++) {
			if (gameFolderS.charAt(n) > 127) {
				JOptionPane.showMessageDialog(AbstractConversionGUI.this,
						"Game folder contains non-ASCII characters, conversion may fail",
						"Warning", JOptionPane.WARNING_MESSAGE);
				break;
			}
		}

		String outputFolderS = outputFolder.getAbsolutePath();
		for (int n = 0; n < outputFolderS.length(); n++) {
			if (outputFolderS.charAt(n) > 127) {
				JOptionPane.showMessageDialog(AbstractConversionGUI.this,
						"Please use an ASCII-only path (like C:\\temp) for the temp folder",
						"Error", JOptionPane.ERROR_MESSAGE);
				return false;
			}
		}
		
		return true;
	}
	
	@Override
	public final void run() {						
		try {
			convert();
		} finally {		
			SwingUtilities.invokeLater(new Runnable() {
				public void run() {
					//propertyPanel.setVisible(true);
					//outputFieldScrollPane.setVisible(true);		
					//startButton.setVisible(true);
					pack();
					setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
				}
			});
		}
	}
	
	protected void convert() {
		File gameFolder = gameFolderField.getFile();
		File outputFolder = outputFolderField.getFile();
		TargetPlatform target = (TargetPlatform)targetCombo.getSelectedItem();
		
		convert(gameFolder, outputFolder, target);
	}
	
	protected void convert(File srcFolder, File dstFolder, TargetPlatform target) {
		long startTime = System.currentTimeMillis();
				
		List<String> list = new ArrayList<String>();
				
		Dim imageSize = ScaleUtil.scaleProp(256, 192, target.getWidth(), target.getHeight());
		if (imageSize.w > originalSize.w || imageSize.h > originalSize.h) {
			imageSize = ScaleUtil.scaleProp(256, 192, originalSize.w, originalSize.h);
		}

		boolean noVoice = !hasVoices || !voiceCheck.isSelected();
		int musicVolume = (Integer)musicVolumeSpinner.getValue();
		int sfxVolume = (Integer)sfxVolumeSpinner.getValue();
		ConvertType pngQuant = (ConvertType)pngQuantCombo.getSelectedItem();
		int jpgQuality = (Integer)jpgQualitySpinner.getValue();
		int mp3_avgb = (Integer)mp3AverageBitrateSpinner.getValue();
		int mp3_minb = 8;
		int mp3_maxb = (Integer)mp3MaxBitrateSpinner.getValue();
		int aacQuality = (Integer)aacQualitySpinner.getValue();
		int vorbisQuality = (Integer)vorbisQualityCombo.getSelectedItem();

		boolean cleanTempFiles = cleanTempFilesButton.isSelected();
		int threads = (Integer)threadsSpinner.getValue();
		
		{ //Extract data from game install
			list.clear();

			if (originalSize != null) {
				list.add("-sourceSize");
				list.add(""+originalSize.w);
				list.add(""+originalSize.h);
			}
			
			if (target != null) {
				list.add("-targetSize");
				list.add(""+imageSize.w);
				list.add(""+imageSize.h);
			}
			
			if (target.isAndroid()) {
				list.add("-android");
			}
			
			if (noVoice) {
				list.add("-novoice");
			}

			list.add("-musicvol");
			list.add(""+musicVolume);

			list.add("-sfxvol");
			list.add(""+sfxVolume);

			if (pngQuant != null && pngQuant != ConvertType.TYPE_PNG) {
				if (pngQuant == ConvertType.TYPE_PNG_256_MEDIAN) {
					list.add("-pngquant");
					list.add("median"); 
				} else if (pngQuant == ConvertType.TYPE_PNG_256_NEUQUANT) {
					list.add("-pngquant");
					list.add("neuquant"); 
				} else {
					Log.w("Unknown PNG quantization type: " + pngQuant);
				}
			}
			
			list.add("-jpg");
			list.add(""+jpgQuality); 

			list.add("-mp3");
			list.add(""+mp3_avgb); 
			list.add(""+mp3_minb); 
			list.add(""+mp3_maxb); 
			
			list.add("-aac");
			list.add(""+aacQuality); 

			list.add("-vorbis");
			list.add(""+vorbisQuality); 

			list.add("-threads");
			list.add(""+threads);
			
			callResourceConverter(
					getTemplateFolder(gameFolderName).getAbsolutePath(),
					srcFolder.getAbsolutePath(),
					dstFolder.getAbsolutePath(), list.toArray(new String[0]));
		}
		
		System.gc();
		
		{ //Convert script
			callScriptConverter(new File(dstFolder, "_original").toString(),
					new File(dstFolder, "_generated").toString());
		}

		System.gc();

		{ //Create installation package
			callPacker(new File(dstFolder, "_generated").toString(),
					getPackerTargetFolder(dstFolder, gameFolderName).toString());
		}
		
		if (cleanTempFiles) {
			Log.v("Cleaning temp files...");
			FileUtil.deleteFolder(new File(dstFolder, "_original"));
			FileUtil.deleteFolder(new File(dstFolder, "_generated"));
		}

		Log.v("========================================");
		Log.v(StringUtil.formatTime(System.currentTimeMillis()-startTime, TimeUnit.MILLISECONDS)
				+ " Entire conversion finished.");		
	}
	
	protected abstract void callResourceConverter(String templateFolder, String srcFolder,
			String dstFolder, String... args);

	protected abstract void callScriptConverter(String srcFolder, String dstFolder);
	
	protected abstract void callPacker(String srcFolder, String dstFolder);
		
	protected File getTemplateFolder(String gameFolderName) {
		return new File("template", gameFolderName);		
	}
	
	protected File getPackerTargetFolder(File base, String gameFolderName) {
		return new File(base, gameFolderName);		
	}
	
	protected Dim getOriginalSize() {
		return originalSize;
	}
	
	protected void setOriginalSize(int w, int h) {
		originalSize = new Dim(w, h);
	}
	
}
