package com.example.secdedup;

import java.util.Arrays;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;

public class TimeControl {
	
	static final int CLEAR_HASH_TIMER  = 1;
	static final int CRIPTO_TIMER      = 2;
	static final int CRIPTO_HASH_TIMER = 3;
	static final int DBASE_TIMER       = 4;
	static final int COMM_TIMER        = 5;
	
	static final int NR_TIMERS = 5;
	private static SecDedupActivity mActivity;

	
	private final long[] timers;
	private static int activeTimer;
	
	public TimeControl(SecDedupActivity mainActivity) {
		TimeControl.mActivity    = mainActivity;
		timers = new long[NR_TIMERS+1];
		resetTimers();
	}

	public void startTimer(int nrTimer){
		if (activeTimer != 0) {
			stopTimer();
		}
		long newTime = SystemClock.elapsedRealtime();
		timers[0] = newTime;
		activeTimer = nrTimer;
		tcLedOn.sendEmptyMessage(0);
	}
	
	public void stopTimer()  {
		if (activeTimer != 0) {
			long newTime = SystemClock.elapsedRealtime();
			timers[activeTimer] += (newTime - timers[0]);
		}
		timers[0] = 0;
		activeTimer = 0;
		tcLedOff.sendEmptyMessage(0);

	}
	
	public void resetTimers () {
		activeTimer = 0;
		for (int i=0; i <= NR_TIMERS; i++){
			timers[i] = 0;
		}
		tcLedOff.sendEmptyMessage(0);
	}

	public long[] getTimers(){
		return Arrays.copyOfRange(timers, 1, NR_TIMERS+1);
	}
	
	private static final Handler tcLedOn = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.ledsCtrl(activeTimer-1);        	
        }		
	};

	private static final Handler tcLedOff = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.ledsCtrl(activeTimer-1);
        }		
	};

}
