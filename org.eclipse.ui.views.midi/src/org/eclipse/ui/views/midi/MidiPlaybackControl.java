package org.eclipse.ui.views.midi;

import java.text.MessageFormat;

import javax.sound.midi.Sequencer;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Slider;
import org.eclipse.swt.widgets.Text;

public class MidiPlaybackControl extends Composite {

	private int value;
	private int maximumValue;
	private String maxValueString;
	private int mark = -1;
	private Slider slider;
	private Label displayer;
	private final Sequencer sequencer;
	private Button playPause;
	private TempoEditor tempoControl;

	// fields for showing/going to measure
	private Text measure;
	private MidiMeasureAnalyzer analyzer;
	private boolean ignoreNextUpdateMeasure;
	private long lastValue;// negative value indicates no update

	public MidiPlaybackControl(Composite parent, Sequencer sequencer) {
		super(parent, SWT.NONE);
		this.sequencer = sequencer;
		setLayout(new GridLayout(3, false));

		createButtonRow();
		createSliderRow();
		addPaintListener(new PaintListener() {

			@Override
			public void paintControl(PaintEvent e) {
				drawMarker(e);
			}
		});

		this.analyzer = new MidiMeasureAnalyzer(sequencer);
		rewind();
	}

	private void drawMarker(PaintEvent e) {
		if (mark > 0) {
			//how to obtain the platform specific button width?? 
			//OS.GetSystemMetrics (OS.SM_CXHSCROLL);
			int buttonWidth = 17;
			int baseX = slider.getLocation().x + buttonWidth - 4;
			int baseY = slider.getLocation().y - 5;
			int effectiveWidth = slider.getSize().x - 3 * buttonWidth;
			baseX += effectiveWidth * ((float) mark) / (slider.getMaximum() - slider.getMinimum());

			e.gc.setBackground(e.display.getSystemColor(SWT.COLOR_BLACK));
			e.gc.fillPolygon(new int[] { baseX, baseY, baseX + 8, baseY, (baseX + 8 / 2), baseY + 4 });
		}
	}

	private void createButtonRow() {
		Composite rowParent = new Composite(this, SWT.NONE);
		rowParent.setLayout(new GridLayout(6, false));
		GridData rowLayoutData = new GridData();
		rowLayoutData.horizontalSpan = 3;
		rowParent.setLayoutData(rowLayoutData);

		Button tempo = new Button(rowParent, SWT.FLAT);
		tempo.setImage(getImage("Tempo"));
		tempo.setToolTipText("Show Tempo Slider");
		tempo.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				if (tempoControl != null && !tempoControl.isDisposed()) {
					tempoControl.dispose();
					tempo.setToolTipText("Show Tempo Slider");
				} else {
					tempoControl = addAndGetTempoControl(MidiPlaybackControl.this);
					tempo.setToolTipText("Hide Tempo Slider");
				}
				focusPlayButton();
				MidiPlaybackControl.this.requestLayout();
			}

		});

		Button resetter = new Button(rowParent, SWT.FLAT);
		resetter.setImage(getImage("Rewind"));
		resetter.setToolTipText(MessageFormat.format("Reset to {0}", display(value)));
		resetter.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				rewind();
				focusPlayButton();
			}

		});

		Button marker = new Button(rowParent, SWT.FLAT);
		marker.setImage(getImage("Mark"));
		marker.setToolTipText("Mark current position");

		Button goToMark = new Button(rowParent, SWT.FLAT);
		goToMark.setImage(getImage("GotoMark"));
		goToMark.setEnabled(false);

		marker.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				mark = getValue();
				goToMark.setEnabled(mark>0);
				goToMark.setToolTipText(MessageFormat.format("Reset to mark ({0})", display(mark)));
				focusPlayButton();
				redraw();//force marker update
			}
		});

		goToMark.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				setValue(mark);
				focusPlayButton();
			}
		});

		playPause = new Button(rowParent, SWT.FLAT);
		playPause.setImage(getImage("Play"));
		playPause.setToolTipText("Play/Pause");
		playPause.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				togglePlayback();
			}
		});

		GridData layoutData = new GridData();
		layoutData.horizontalAlignment = SWT.FILL;
		layoutData.minimumWidth = 60;
		layoutData.grabExcessHorizontalSpace = true;
		measure = new Text(rowParent, SWT.NONE);
		measure.setText("");
		measure.setTextLimit(11);
		measure.setToolTipText("Enter measure number and press return to go to approximate time"
				+ "\n<measure>:<partial>" 
				+ "\nFormat examples '5', '9:3/8', '2:1/4'"
				+ "\nIf the field is empty, the measure number will not be updated when playing");
		measure.setLayoutData(layoutData);
		measure.addKeyListener(new KeyAdapter() {

			@Override
			public void keyReleased(KeyEvent e) {
				if (e.keyCode == SWT.CR || e.keyCode == SWT.KEYPAD_CR) {
					int time = getTimeToSet();
					if (time >= 0) {
						ignoreNextUpdateMeasure = true;
						lastValue = 0;
						setValue(time);
					} else {
						lastValue = -1;
					}
				} else {
					if (measure.getText().trim().isEmpty()) {
						lastValue = -1;
					} else {
						lastValue = 0;
					}
				}
			}
		});
	}

	private int getTimeToSet() {
		try {
			String text = measure.getText();
			if (!text.trim().isEmpty()) {
				String[] split = text.split(":");
				int bar = Integer.parseInt(split[0].trim());
				return analyzer.getTime(bar);
			}
		} catch (Exception e) {
			// ignore
			// e.printStackTrace();
		}
		return -1;
	}

	private void focusPlayButton() {
		playPause.forceFocus();
	}


	private void createSliderRow() {
		slider = new Slider(this, SWT.NONE);
		GridData sliderLayoutData = new GridData();
		sliderLayoutData.horizontalAlignment = SWT.FILL;
		sliderLayoutData.grabExcessHorizontalSpace = true;
		slider.setLayoutData(sliderLayoutData);
		slider.setThumb(1);
		slider.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				setValue(slider.getSelection());
			}

		});

		displayer = new Label(this, SWT.CENTER);
		displayer.setLayoutData( new GridData(80, SWT.DEFAULT)); // XXX proper width
	}

	private Image getImage(String name) {
		return Activator.getImageDescriptor(MidiViewPage.ICON_PATH + name + ".png").createImage();
	}

	private int getValue() {
		return value;
	}

	private void setValue(int value, boolean setSequncerPosition) {
		value = Math.max(0, Math.min(getMaximumValue(), value));
		this.value = value;
		if (slider.isDisposed()) {
			return;
		}
		slider.setSelection(value);
		displayer.setText(MessageFormat.format("{0}/{1}", display(value), maxValueString));
		if (setSequncerPosition) {
			sequencer.setMicrosecondPosition(value);
		}
	}

	void setValue(int value) {
		setValue(value, true);
	}

	private void rewind() {
		setValue(0);
	}

	private void resetValue() {
		if (mark >= 0) {
			setValue(mark);
		} else {
			rewind();
		}
	}

	private int getMaximumValue() {
		return maximumValue;
	}

	public void sequencerContentChanged() {
		this.maximumValue = (int) sequencer.getMicrosecondLength();
		slider.setMaximum(maximumValue + 1);
		slider.setPageIncrement(maximumValue / 10);
		slider.setIncrement(maximumValue / 100);
		maxValueString = display(maximumValue);
		setValue(getValue(), false);
		if (analyzer.calculateMeasureTimes()) {
			lastValue = measure.getText().isEmpty() ? -1 : 0;
		} else {
			measure.setText("");
			lastValue = -1;
		}
	}

	private void play() {
		sequencer.start();
		measure.setEditable(false);
		playPauseImage("Pause");
		Display.getDefault().timerExec(0, new Updater());
	}

	public void pause() {
		if (sequencer.isOpen()) {
			sequencer.stop();
			playPauseImage("Play");
			measure.setEditable(true);
		}
	}

	private void playPauseImage(String image) {
		if (playPause != null && !playPause.isDisposed()) {
			Image oldImage = playPause.getImage();
			if(oldImage!=null && !oldImage.isDisposed()) {
				oldImage.dispose();
			}
			playPause.setImage(getImage(image));
		}
	}

	private boolean isPlaying() {
		return sequencer.isRunning();
	}

	public void togglePlayback() {
		if (isPlaying()) {
			pause();
		} else {
			play();
		}
	}

	private boolean isFinished() {
		return sequencer.getMicrosecondPosition() >= sequencer.getMicrosecondLength();
	}

	private String display(long microseconds) {
		long seconds = microseconds / 1000000;
		updateMeasure(microseconds);
		final int secondsInMinute = 60;
		return MessageFormat.format("{0}:{1,number,00}", seconds / secondsInMinute, seconds % secondsInMinute);
	}

	private void updateMeasure(long microSeconds) {
		if (ignoreNextUpdateMeasure) {
			ignoreNextUpdateMeasure = false;
		} else if (lastValue >= 0 && microSeconds != getMaximumValue() && Math.abs(lastValue - microSeconds) > 10000) {
			lastValue = microSeconds;
			int bar = analyzer.getMeasure(microSeconds);
			if (bar >= 0) {
				String[] split = measure.getText().split(":", 2);
				String suffix = split.length > 1 ? ":" + split[1] : "";
				measure.setText(bar + suffix);
			} else {
				lastValue = -1;
				measure.setText("");
			}
		}
	}

	// Tempo

	private TempoEditor addAndGetTempoControl(Composite parent) {
		TempoEditor result = new TempoEditor(parent, sequencer);
		result.setValue((int)(sequencer.getTempoFactor()*100));
		GridData layoutData = new GridData();
		layoutData.horizontalSpan=3;
		layoutData.horizontalAlignment = SWT.FILL;
		layoutData.grabExcessHorizontalSpace = true;
		result.setLayoutData(layoutData);
		return result;
	}

	private class Updater implements Runnable {

		@Override
		public void run() {
			setValue((int) sequencer.getMicrosecondPosition(), false);
			if (isFinished()) {
				pause();
				resetValue();
			} else if (isPlaying()) {
				final int millisecondsPerSecond = 1000;
				final int framesPerSecond = 25;
				Display.getDefault().timerExec(millisecondsPerSecond / framesPerSecond, this);
			}
		}
	}
}
