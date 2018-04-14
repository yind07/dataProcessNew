package dataProcessNew;

import java.util.Comparator;

public class WaveData {
	private String time;	// sampling time
	private String type;
	private float val;
	
//	public WaveData() {
//		setValue(0);
//	}
	
	public WaveData(String time, String type, float val) {
		setTime(time);
		setType(type);
		setValue(val);
	}
	
	public String getTime() {
		return time;
	}
	public void setTime(String time) {
		this.time = time;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public float getValue() {
		return val;
	}
	public void setValue(float val) {
		this.val = val;
	}
	// facilitation
	public boolean isECG() {
		return getType().contains("ECG");
	}
	public boolean isPleth() {
		return getType().contains("Pleth");
	}
	
	public String toString() {
		return "time: " +  getTime() + ", value: " + getValue() + ", type: " + getType();
	}

	// for sorting
	public static Comparator<WaveData> comparator = new Comparator<WaveData>() {
		public int compare(WaveData wd1, WaveData wd2) {
			// ascending order
			return wd1.getTime().compareTo(wd2.getTime());
		}
	};
	
	
	
}
