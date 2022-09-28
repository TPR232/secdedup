package com.example.secdedup;

public class BatteryStatus {
	private final int batteryLevel;
	private final long elapsedTime;

	public BatteryStatus (int batLevel, long eTime){
		this.batteryLevel = batLevel;
		this.elapsedTime  = eTime;
	}
	
	public int getBatteryLevel() {
		return batteryLevel;
	}
	
	public long getElapsedTime() {
		return elapsedTime;
	}

}
