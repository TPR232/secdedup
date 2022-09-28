package com.example.secdedup;

import java.util.ArrayList;


public class SecDedupResults {
	
	private long totalTime;
	private int procFiles;
	private int procChuncks;
	private int dupChuncks;
	private long totalSize;
	private long totalUpload;
	private int dedupLevel;
	private int networkType;
	private final ArrayList<BatteryStatus> batStatList;

	public SecDedupResults() {
		batStatList = new ArrayList<BatteryStatus>();
		this.dedupLevel = 1;
	}

	public long getTotalTime() {
		return totalTime;
	}

	public void setTotalTime(long totalTime) {
		this.totalTime = totalTime;
	}

	public int getProcFiles() {
		return procFiles;
	}

	public void setProcFiles(int procFiles) {
		this.procFiles = procFiles;
	}

	public int getProcChuncks() {
		return procChuncks;
	}

	public void setProcChuncks(int procChuncks) {
		this.procChuncks = procChuncks;
	}

	public int getDupChuncks() {
		return dupChuncks;
	}

	public void setDupChuncks(int dupChuncks) {
		this.dupChuncks = dupChuncks;
	}

	public long getTotalSize() {
		return totalSize;
	}

	public void setTotalSize(long totalSize) {
		this.totalSize = totalSize;
	}

	public long getTotalUpload() {
		return totalUpload;
	}

	public void setTotalUpload(long totalUpload) {
		this.totalUpload = totalUpload;
	}

	public int getDedupLevel() {
		return dedupLevel;
	}

	public void setDedupLevel(int dedupType) {
		this.dedupLevel = dedupType;
	}

	public int getNetworkType() {
		return networkType;
	}

	public void setNetworkType(int networkType) {
		this.networkType = networkType;
	}

	public void addBatStatus (int batLevel, long runSecs){
		batStatList.add(new BatteryStatus(batLevel, runSecs));
	}
	
	public void resetBatStatus () {
		if (batStatList.size() > 0){
			batStatList.clear();
		}
	}
	
	public int getBatSatusCount(){
		return batStatList.size();
	}

	public BatteryStatus getBatStatus(int i) {
		return batStatList.get(i);
	}

}
