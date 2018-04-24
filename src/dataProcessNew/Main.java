package dataProcessNew;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class Main {

	public static void main(String[] args) {
		//System.out.printf("args.length: %d\n", args.length);
		if (args.length != 1) {
			// directory example: "C:\\work\\data\\2-wave-132314"
			System.out.printf("Usage: java dataProcessNew.Main directory\n");
		} else {
			File dir = new File(args[0]);
			if (dir.isDirectory()) {
				for (File f : dir.listFiles()) {
					if (f.isFile()) {
						//System.out.println(f.getName());
						_readDataByFile(f);
//						if (!dataBP.isEmpty()) {
//							_processBP(_getFilenameBP(dir, f.getName()));
//							dataBP.clear();
//						}
					}
				}
				
				_processNew(_getOutputFilename(dir));
				//_processBP(_getOutputFilenameBP(dir));
			} else {
				System.out.printf("%s is not a directory\n", args[0]);
			}
		}
	}
	
	private static String _getFilenameBP(File dir, String name) {
		return dir.getPath() + "\\" + name + "_BP_info.csv";
	}

	private static void _processBP(String fout) {
		System.out.print("Processing INVP(BP)...");
		
		StringBuffer sbuf = new StringBuffer();
		sbuf.append("Type,SD,CV(%),Mean");
		sbuf.append(System.lineSeparator());
		
		// normal BP
		_calculateSDnCV(dataBP, "BP(All)", sbuf);
		
		// build list of max BPs
		List<WaveData> maxBPList = _getMaxBPList(dataBP, 100);
		_calculateSDnCV(maxBPList, "BP(Max)", sbuf);
		
		// build list of min BPs
		List<WaveData> minBPList = _getMinBPList(dataBP, 100);
		_calculateSDnCV(minBPList, "BP(Min)", sbuf);
		
		try {
			FileWriter fw = new FileWriter(fout);
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(sbuf.toString());
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("\ndone!");
	}

	private static void _calculateSDnCV(List<WaveData> list, String type, StringBuffer sbuf) {
		double mean = _getMean(list);
		double sd = _getSD(list);
		// calculate CV (SD/MN)
		double cv = sd/mean;
		System.out.printf("\n%s: SD: %.5f, CV: %.2f%%, Mean: %.5f, list size: %d",
				type, sd, cv*100, mean, list.size());
		sbuf.append(type + "," + sd + "," + cv*100 + "," + mean);
		sbuf.append(System.lineSeparator());
	}

	private static List<WaveData> _getMaxBPList(List<WaveData> list, int sampleRate) {
		return _getExtremeBPList(list, sampleRate, WaveData.maxValueComp);
	}
	private static List<WaveData> _getMinBPList(List<WaveData> list, int sampleRate) {
		return _getExtremeBPList(list, sampleRate, WaveData.minValueComp);
	}

	private static List<WaveData> _getExtremeBPList(List<WaveData> list, int sampleRate,
			Comparator<WaveData> comp) {
		List<WaveData> extremeBPList = new ArrayList<WaveData>();
		for (int i=0; i<list.size();) {
			WaveData extremeBP = list.get(i);
			int extId = i;	// extremum BP index
			for (int j=i+1; j<i+sampleRate && j<list.size(); ++j) {
				if (comp.compare(extremeBP, list.get(j)) < 0) {
					extremeBP = list.get(j);
					extId = j;
				}
			}
			extremeBPList.add(extremeBP);
			// import: adjust i to the start of the next interval
			i = extId + sampleRate/2;
		}
		return extremeBPList;
	}

	private static double _getMean(List<WaveData> list) {
		double sum = 0;
		for (WaveData wd : list) {
			sum += wd.getValue();
		}
		return sum / list.size();
	}

	// calculate SD
	// precondition: input data is within 1 hour
	private static double _getSD(List<WaveData> list) {
		double mean = _getMean(list);
		double sum = 0;
		for (WaveData wd : list) {
			double diff = wd.getValue() - mean;
			sum += diff*diff;
		}
		//return Math.sqrt(sum/list.size());	// use N
		return Math.sqrt(sum/(list.size()-1));	// use N-1
	}

	private static List<WaveData> dataECG = new ArrayList<WaveData>();
	private static List<WaveData> dataPleth = new LinkedList<WaveData>();
	private static List<WaveData> dataBP = new ArrayList<WaveData>();	// INVP
	
	private static String _getOutputFilename(File dir) {
		return dir.getPath() + "\\" + dir.getName() + ".csv";
	}
	private static String _getOutputFilenameBP(File dir) {
		return dir.getPath() + "\\" + dir.getName() + "BP_info.csv";
	}
	
	

	// Step 4: get time interval by seconds
	private static double _getInterval(String t1, String t2) {
		// Input string example: "0408.13:21:24.385127083"
	    DateFormat df = new SimpleDateFormat("MMdd.HH:mm:ss.S");
	    //int year = Calendar.getInstance().get(Calendar.YEAR);
	    try {
			Date d1 =  df.parse(_getDateString(t1));
			Date d2 =  df.parse(_getDateString(t2));
			long msDiff = d2.getTime() - d1.getTime();
			
			return (double)msDiff / 1000.0;
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return 0;
	}

	// "0408.13:21:24.385127083" => "0408.13:21:24.385"
	private static String _getDateString(String s) {
		return s.substring(0, 17);
	}

	// Step 3: find the peak of Pleth in time interval (t1, t2)
	// Performance improvement:
	//	1) Break loop when index is out of the interval
	//	2) Remove previous data interval after check!
	private static WaveData _getPeakPlethByInterval(List<WaveData> dataPleth, String t1, String t2) {
		WaveData peakPleth = null;
		int iBreak = 0; // first index out of the interval
		for (int i=0; i<dataPleth.size(); ++i) {
			if (dataPleth.get(i).getTime().compareTo(t1) > 0
				&& dataPleth.get(i).getTime().compareTo(t2) < 0) {
				if (peakPleth == null || peakPleth.getValue() < dataPleth.get(i).getValue()) {
					peakPleth = dataPleth.get(i);
				}
			} else if (dataPleth.get(i).getTime().compareTo(t2) >= 0) {
				// 1) Break loop when index is out of the interval
				iBreak = i;
				break;
			}
		}
		// 2) Remove data interval [0, iBreak) from dataPleth
		//int sizeBeforeReduce = dataPleth.size();
		dataPleth.subList(0, iBreak).clear();
		//System.out.printf("Size before: %d, after: %d\n", sizeBeforeReduce, dataPleth.size());
		
		return peakPleth;
	}

//	private static WaveData _getPeakECGByFrequency(LinkedList<WaveData> dataECG, int startId,
//			int frequency) {
//		WaveData peakECG = dataECG.get(startId);
//		int peakId = startId;	// peak index
//		for (int i=startId+1; i<startId+frequency; ++i) {
//			if (peakECG.getValue() < dataECG.get(i).getValue()) {
//				peakECG = dataECG.get(i);
//				peakId = i;
//			}
//		}
//		return peakECG;
//	}

	private static void _readDataByFile(File f) {
		
		try {
			BufferedReader buf = new BufferedReader(new FileReader(f));
			String type = "";
			
			List<WaveData> data = null;
			
	        String readLine = buf.readLine();
	        // process the 1st line
	        if (readLine != null) {
	        	if (readLine.contains("ECG")) {
	        		System.out.println("Found ECG file: " + f.getName());
	        		type = "ECG";
	        		data = dataECG;
	        	} else if (readLine.contains("Pleth")) {
	        		System.out.println("Found Pleth file: " + f.getName());
	        		type = "Pleth";
	        		data = dataPleth;
	        	} else if (readLine.contains("INVP")) {
	        		System.out.println("Found INVP file: " + f.getName());
	        		type = "BP";
	        		data = dataBP;
	        	}
	        	if (!type.isEmpty()) {
	    	        while ((readLine = buf.readLine()) != null) {
	    	        	String[] tokens = readLine.split(",");
	    	    		if (tokens.length > 1) {
	    	    			data.add(new WaveData(tokens[0], type, Float.parseFloat(tokens[1])));
	    	    		}
	    	        }
	    	        //System.out.printf("Data size: %d\n", data.size());
	        	}
	        }
	        buf.close();
		} catch (IOException e) {
            e.printStackTrace();
        }
	}
	
	
	/* Algorithm
	1. Find the 1st peak of ECG
	2. Find the 2nd peak of ECG
	3. During 1-2, find the peak of Pleth
	4. Calculate tPleth - tEcg1, and save result (use tPleth as X-axis)
	5. Loop 1-4 until data are processed
	 */
	// new version using StringBuffer, for performance improvement
	private static void _processNew(String fout) {
		WaveData peakECG1 = null;
		WaveData peakECG2 = null;
		WaveData peakPleth = null;

		System.out.print("Processing(new)");
		int cnt = 0;
		StringBuffer sbuf = new StringBuffer();
		sbuf.append("Time,PTT(sec)");
		sbuf.append(System.lineSeparator());
		
		// scan dataECG by initial peak interval 300: TODO
		int interval = 200;	// peak interval
		int intervalSum = interval;		// sum of interval
		int peakId1 = 0;	// id for peakECG1
		int peakId2 = 0;	// id for peakECG2
		
		for (int i=0; i<dataECG.size();) {
			// get peakECG and related index by frequency
			WaveData peakECG = dataECG.get(i);
			int peakId = i;	// peak index
			
			for (int j=i+1; j<i+interval; ++j) {
				if (j >= dataECG.size()) break;
				if (peakECG.getValue() < dataECG.get(j).getValue()
					&& !_isNoise(dataECG, j)
					&& _isPeak(dataECG, j)) {
					peakECG = dataECG.get(j);
					peakId = j;
				}
			}
			
			// import: adjust i to the start of the next interval
			i = _getNextScanId(peakId, interval);
			
			if (peakECG1 == null) {
				peakECG1 = peakECG;
				peakId1 = peakId;
			//} else if (peakECG2 == null && _isNormalChange(peakECG, peakECG1)) {
			} else if (peakECG2 == null) {
				peakECG2 = peakECG;
				peakId2 = peakId;
				// Step 3
				peakPleth = _getPeakPlethByInterval(dataPleth, peakECG1.getTime(), peakECG2.getTime());
				_debugPrint(peakECG1, peakECG2, peakPleth, peakId1, peakId2);
				
				// Step 4
				if (peakPleth != null) {
					// for debug
					// "0409.10:21:20.960796980"
					// "0409.11:01:14.766985886"
//					if (peakPleth.getTime().equals("0413.08:31:50.416107096")
//						|| peakPleth.getTime().equals("0413.08:31:49.118790284")
//						|| peakPleth.getTime().equals("0413.08:31:51.078384387")) {
//						_debugPrint(peakECG1, peakECG2, peakPleth, peakId1, peakId2);
//					}
					
					double tDiff = _getInterval(peakECG1.getTime(), peakPleth.getTime());
					//System.out.println("tDiff: " + tDiff);
					// write result to file
					sbuf.append(peakPleth.getTime() + "," + tDiff);
					sbuf.append(System.lineSeparator());
					
					// UI stuff
					if ((cnt+1) % 10 == 0) {
						System.out.print(".");	// print dot per 10 times
					}
					if ((cnt+1) % 1000 == 0) {
						System.out.println();	// print newline per 100 dots
					}
					cnt++;
				}
				// reset
				peakECG1 = peakECG2;
				peakECG2 = null;
				peakPleth = null;
				
				// import: adjust peak interval for more accurate scan in the next round!
				//interval = peakId2-peakId1+1;
				intervalSum += (peakId2-peakId1+1);
				interval = intervalSum/(cnt+1);	// try to amortize the interval
				peakId1 = peakId2;
			}
		}
		
		//System.out.println("\nStart saving to file...");
		try {
			FileWriter fw = new FileWriter(fout);
			BufferedWriter bw = new BufferedWriter(fw);
			bw.write(sbuf.toString());
			bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("done!");
		System.out.println("Data size: " + cnt);
	}

	// experience assumption:
	// select as peak only if both sides ratio < 0.95
	private static boolean _isPeak(List<WaveData> data, int id) {
		double ratioLevel = 0.95;	// experience value
		if (id < 0 || id >= data.size()) {	// incorrect id
			return false;
		} else if (id == 0) {	// special id: first one, no previous one
			return _getRatio(data.get(id), data.get(id+1)) < ratioLevel;
		} else if (id == data.size()-1) {	// special id: last one, no following one
			return _getRatio(data.get(id-1), data.get(id)) < ratioLevel;
		} else {	// id > 0 && id < data.size()-1
			return (data.get(id-1).getValue() <= data.get(id).getValue()
					&& data.get(id+1).getValue() <= data.get(id).getValue())
					&& (_getRatio(data.get(id), data.get(id+1)) < ratioLevel
					&& _getRatio(data.get(id-1), data.get(id)) < ratioLevel)
				||	(_getRatio(data.get(id), data.get(id+1)) == 1
					&& _getRatio(data.get(id-1), data.get(id)) < ratioLevel)
				||	(_getRatio(data.get(id), data.get(id+1)) < ratioLevel
					&& _getRatio(data.get(id-1), data.get(id)) == 1);
		}
	}

	// simple rule to signal noise
	// use the experience of _isNormalChange: data change shouldn't be extreme!
	private static boolean _isNoise(List<WaveData> data, int id) {
	
		if (id < 0 || id >= data.size()) {	// incorrect id
			return true;
		} else if (id == 0) {	// special id: first one, no previous one
			return !_isNormalChange(data.get(id), data.get(id+1));
		} else if (id == data.size()-1) {	// special id: last one, no following one
			return !_isNormalChange(data.get(id-1), data.get(id));
		} else {	// id > 0 && id < data.size()-1
			return !_isNormalChange(data.get(id-1), data.get(id))
					|| !_isNormalChange(data.get(id), data.get(id+1));
		}
	}

	// Note: adjust this function to get best scan result!
	private static int _getNextScanId(int peakId, int lastInterval) {
		return peakId + lastInterval/2;
	}

	// enhancement by experience: the ratio of small/big >= 0.4 ?
	private static boolean _isNormalChange(WaveData wd1, WaveData wd2) {
		return _getRatio(wd1, wd2) >= 0.4;
	}
	
	private static double _getRatio(WaveData wd1, WaveData wd2) {
		float small, big;
		if (wd1.getValue() <= wd2.getValue()) {
			small = wd1.getValue();
			big = wd2.getValue();
		} else {
			small = wd2.getValue();
			big = wd1.getValue();
		}
		return Math.abs(small/big);
	}
	
	private static void _debugPrint(WaveData peakECG1, WaveData peakECG2, WaveData peakPleth,
			int peakId1, int peakId2) {
		System.out.println("\npeakECG1: " + peakECG1 + ", id: " + peakId1);
		System.out.println("peakPleth: " + peakPleth);
		System.out.println("peakECG2: " + peakECG2 + ", id: " + peakId2);
		System.out.println("interval: " + (peakId2-peakId1));
	}
	
}
