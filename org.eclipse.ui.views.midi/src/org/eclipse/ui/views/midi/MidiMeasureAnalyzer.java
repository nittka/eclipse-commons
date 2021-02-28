package org.eclipse.ui.views.midi;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.Sequencer;
import javax.sound.midi.Track;

import org.eclipse.swt.widgets.Text;

class MidiMeasureAnalyzer {

	private Sequencer sequencer;

	// array of microsecond position for each measure (identified by its index in
	// the array)
	private Integer[] measurePositions;
	private String partial = null;
	private Text partialTextField;

	MidiMeasureAnalyzer(Sequencer sequencer, Text partialField) {
		this.sequencer = sequencer;
		this.partialTextField = partialField;
		partialField.addModifyListener(e -> {
			handlePartialChange();
		});
	}

	public int getMeasure(long microsecondsPosition) {
		if (measurePositions != null) {
			for (int i = 0; i < measurePositions.length; i++) {
				if (measurePositions[i] > microsecondsPosition) {
					return i;
				}
			}
			return measurePositions.length;
		} else {
			return -1;
		}
	}

	public int getTime(String measureText) {
		try {
			int bar = parseInt(measureText);
			if (measurePositions == null) {
				return -1;
			} else if (bar <= 0) {
				return 0;
			}
			int barIndex = bar - 1;
			if (measurePositions.length <= barIndex) {
				return (int) sequencer.getMicrosecondLength();
			} else {
				return measurePositions[barIndex];
			}
		} catch (Exception e) {
			// ignore
		}
		return -1;
	}

	private int parseInt(String s) {
		return Integer.parseInt(s.trim());
	}

	public boolean calculateMeasureTimes() {
		long sequencerPosition = sequencer.getTickPosition();
		try {
			int ticksPerQuarter = sequencer.getSequence().getResolution();
			int partialOffset = getPartialOffset(ticksPerQuarter);
			long currentTick = partialOffset;
			long tickLength = sequencer.getTickLength();
			List<Integer> measureMicroSeconds = new ArrayList<>();
			List<MeasureInfo> timeChanges = getTimeChangeInfos(ticksPerQuarter);
			do {
				sequencer.setTickPosition(currentTick);
				measureMicroSeconds.add((int) sequencer.getMicrosecondPosition());
				currentTick = getNextMeasureTick(currentTick, timeChanges);
			} while (currentTick < tickLength);
			measurePositions = measureMicroSeconds.toArray(new Integer[0]);
			return true;
		} catch (Exception e) {
			measurePositions = null;
		} finally {
			// rewind
			sequencer.setTickPosition(sequencerPosition);
		}
		return false;
	}

	private int getPartialOffset(int ticksPerQuarter) {
		try {
			if (partial.length() > 0) {
				String[] split = partial.split("/");
				int number = parseInt(split[0]);
				int base = parseInt(split[1]);
				return ticksPerQuarter * 4 * number / base;
			}
		} catch (Exception e) {
			// ignore illegal format
		}
		return 0;
	}

	private void handlePartialChange() {
		String partialString = partialTextField.getText();
		if (!Objects.equals(partialString, partial)) {
			partial = partialString;
			calculateMeasureTimes();
		}
	}

	private long getNextMeasureTick(long currentTick, List<MeasureInfo> infos) {
		if (infos.size() == 1) {
			return infos.get(0).nextMeasureTick(currentTick);
		} else {
			int infoToUseIndex = 0;
			for (int i = 0; i < infos.size(); i++) {
				MeasureInfo info = infos.get(i);
				if (info.getTick() <= currentTick) {
					infoToUseIndex = i;
				} else {
					break;
				}
			}
			MeasureInfo infoToUse = infos.get(infoToUseIndex);
			long result = infoToUse.nextMeasureTick(currentTick);
			if (infoToUseIndex < infos.size() - 1) {
				MeasureInfo nextInfo = infos.get(infoToUseIndex + 1);
				if (nextInfo.tick < result) {
					return nextInfo.tick;
				}
			}
			return result;
		}
	}

	private List<MeasureInfo> getTimeChangeInfos(int ticksPerQuarter) {
		List<MeasureInfo> result = new ArrayList<>();
		Track[] tracks = sequencer.getSequence().getTracks();
		for (Track track : tracks) {
			for (int i = 0; i < track.size(); i++) {
				MidiEvent event = track.get(i);
				// time change events have meta message with -1, 88 as first bytes
				if (event.getMessage() instanceof MetaMessage) {
					byte[] bytes = event.getMessage().getMessage();
					if (bytes[0] == -1 && bytes[1] == 88) {
						result.add(new MeasureInfo(event, ticksPerQuarter));
					}
				}
			}
		}
		if (result.isEmpty()) {
			throw new IllegalStateException("did not find any time events");
		}
		return result;
	}

	private class MeasureInfo {
		long tick;
		int number;
		int base;
		long ticksPerMeasure;

		public MeasureInfo(MidiEvent event, int ticksPerQuarter) {
			tick = event.getTick();
			byte[] bytes = event.getMessage().getMessage();
			if (bytes[0] == -1 && bytes[1] == 88) {
				number = (int) bytes[3];
				base = (int) Math.pow(2, (int) bytes[4]);
			} else {
				throw new IllegalArgumentException();
			}
			long ticksPerBase = (long) ((double) ticksPerQuarter * 4 / base);
			ticksPerMeasure = number * ticksPerBase;
		}

		private long getTick() {
			return tick;
		}

		public long nextMeasureTick(long currentTick) {
			return currentTick + ticksPerMeasure;
		}

		@Override
		public String toString() {
			return tick + " " + number + "/" + base + " " + ticksPerMeasure;
		}
	}
}