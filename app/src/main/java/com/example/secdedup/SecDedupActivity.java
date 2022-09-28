package com.example.secdedup;

import java.io.IOException;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Chronometer;
import android.widget.ProgressBar;
import android.widget.TextView;


public class SecDedupActivity extends Activity {
	
	protected static final int STARTPROC    = 0;
	protected static final int TOTALFILES   = 1;
	protected static final int PROCFILES    = 2;
	protected static final int PROCCHUNCK   = 3;
	protected static final int PROCSIZE     = 4;
	protected static final int DUPCHUNCK    = 5;
	protected static final int TOTALSIZE    = 6;
	protected static final int UPLOADED     = 7;
	protected static final int DATASET_PATH = 8;
	protected static final int SERVER_ADDR  = 9;
	
	static final int DATASET_NOT_PRESENT = 1;
	static final int BATTERY_CHARGING    = 5;
	static final int NO_NETWORK          = 6;
	static final int SERVER_NOT_FOUND    = 7;
	static final int IO_ERROR            = 8;
	static final int NO_SERVER			 = 9;
	
	private final String FILES_DIR_NAME = "FILES1";
	private String serverAddress = null;
	private String runMode = "1";
	
	private int dedupLevel = 1;
	private int totalFiles = 0;
	private long totalSize = 0;
	private long procSize  = 0;
	private boolean processing = false;
	private boolean batCharging = false;
	private static int batLevel = -1;
	private String dsBaseDir;

	private DatasetProcess dsProc;
	private SecDedupIO io = null;
	private static Chronometer chrono;
	private SecDedupResults results;
	private SharedPreferences sPrefs;
	
	private TextView tvDedupLevel;
	private TextView tvStartTime;
	private TextView tvEndTime;
	private TextView tvTotalFiles;
	private TextView tvProcFiles;
	private TextView tvFilesPrct;
	private TextView tvChuncks;
	private TextView tvDupChuncks;
	private TextView tvChuncksPrct;
	private TextView tvTotalSize;
	private TextView tvTotalSizeUnit;
	private TextView tvUploaded;
	private TextView tvUploadedUnit;
	private TextView tvUploadPrct;
	private TextView tvCharging;
	private TextView tvBatStart;
	private TextView tvBatEnd;
	private TextView tvBatConsumption;
	private TextView tvDevice;
	private TextView tvOSVersion;
	private TextView tvDedupType;
	private TextView tvNetworkType;
	private TextView tvStart;
	private TextView tvServerAddr;
	private ProgressBar pbProcessed;
	TextView[] leds;
		
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
//		setContentView(R.layout.secdedup_activity);
//		sPrefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
//		sPrefs.registerOnSharedPreferenceChangeListener(prefListener);
		buildUI();
    	
		chrono.setText(getResources().getString(R.string.strZeroTime));			
		results = new SecDedupResults();
		registerReceiver(batInfoReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); 
		getNetworkType();
		
    	new DataSetLocate(this,FILES_DIR_NAME).start();
		
	}
	
	private void buildUI() {
		tvDedupLevel     = (TextView)findViewById(R.id.tvDedupLevel);
		tvStartTime      = (TextView)findViewById(R.id.tvStartTime);
		tvEndTime        = (TextView)findViewById(R.id.tvEndTime);
		chrono           = (Chronometer)findViewById(R.id.chChrono);
		tvTotalFiles     = (TextView)findViewById(R.id.tvTotalFiles);
		tvProcFiles      = (TextView)findViewById(R.id.tvProcFiles);
		tvFilesPrct      = (TextView)findViewById(R.id.tvFilesPrct);
		tvChuncks        = (TextView)findViewById(R.id.tvChuncks);
		tvDupChuncks     = (TextView)findViewById(R.id.tvDupChuncks);
		tvChuncksPrct    = (TextView)findViewById(R.id.tvChuncksPrct);
		tvTotalSize      = (TextView)findViewById(R.id.tvTotalSize);
		tvTotalSizeUnit  = (TextView)findViewById(R.id.tvTotalSizeUnit);
		tvUploaded       = (TextView)findViewById(R.id.tvUploaded);
		tvUploadedUnit   = (TextView)findViewById(R.id.tvUploadedUnit);
		tvUploadPrct     = (TextView)findViewById(R.id.tvUploadPrct);
		tvCharging       = (TextView)findViewById(R.id.tvCharging);
		tvBatStart       = (TextView)findViewById(R.id.tvBatStart);
		tvBatEnd         = (TextView)findViewById(R.id.tvBatEnd);
		tvBatConsumption = (TextView)findViewById(R.id.tvBatConsumption);
		tvDevice         = (TextView)findViewById(R.id.tvDevice);
		tvOSVersion      = (TextView)findViewById(R.id.tvOSVersion);	
		tvDedupType      = (TextView)findViewById(R.id.tvDedupType);
		tvNetworkType	 = (TextView)findViewById(R.id.tvNetworkType);
		tvServerAddr	 = (TextView)findViewById(R.id.tvServer);
		tvStart          = (TextView)findViewById(R.id.tvStart);
		pbProcessed		 = (ProgressBar)findViewById(R.id.pbProcessed);
		
		pbProcessed.setProgress(0);	
		if (android.os.Build.DEVICE.equals(android.os.Build.MODEL)) {
			tvDevice.setText(android.os.Build.DEVICE);
		} else {
			tvDevice.setText(android.os.Build.DEVICE + " - " + android.os.Build.MODEL);
		}

		tvOSVersion.setText(android.os.Build.VERSION.RELEASE);
		updateStr(SERVER_ADDR, sPrefs.getString("prefServer", getResources().getString(R.string.strNoServer)));
		runMode = sPrefs.getString("prefRunMode", "1");
		updateDedupType();
		tvStart.setVisibility(View.INVISIBLE);
		
		leds    = new TextView[TimeControl.NR_TIMERS];
		leds[0]	= (TextView)findViewById(R.id.led1);		
		leds[1]	= (TextView)findViewById(R.id.led2);
		leds[2]	= (TextView)findViewById(R.id.led3);
		leds[3]	= (TextView)findViewById(R.id.led4);
		leds[4]	= (TextView)findViewById(R.id.led5);		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.sec_dedup, menu);
		return true;
	}

	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		if (! processing) {
	        switch (item.getItemId()) {
	        case R.id.secdedup_settings:
	            Intent i = new Intent(this, SecDedupSettings.class);
	            startActivity(i);
	            break;
	        }
		}
        return true;
    }
	
	SharedPreferences.OnSharedPreferenceChangeListener prefListener =  new SharedPreferences.OnSharedPreferenceChangeListener() {
	    public void onSharedPreferenceChanged(SharedPreferences prefs,String key) {
			if (key.equals("prefServer")){
				updateStr(SERVER_ADDR, sPrefs.getString("prefServer", getResources().getString(R.string.strNoServer)));
	        }
			if (key.equals("prefRunMode")) {
				runMode = sPrefs.getString("prefRunMode", "1");
				updateDedupType();
			}
	    }
	};
	
	private final BroadcastReceiver batInfoReceiver = new BroadcastReceiver(){
		@Override
		public void onReceive(Context context, Intent i) {
			int level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
			int scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
			int batStatus = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			batCharging = (batStatus == BatteryManager.BATTERY_STATUS_CHARGING);
			if (batCharging) {
				tvCharging.setVisibility(View.VISIBLE);
			} else {
				tvCharging.setVisibility(View.INVISIBLE);
			}
			int batteryPct = (Math.round((level  / (float)scale)*100));
	    	if (batteryPct != batLevel) {
	        	batLevel = batteryPct;
	        	if  (processing){
	        		tvBatEnd.setText(String.valueOf(batteryPct));				
		        	long runSecs = Math.round((float)(SystemClock.elapsedRealtime() - chrono.getBase()) / (float) 1000);
		    		results.addBatStatus(batLevel, runSecs);
		    		int batIni = Integer.parseInt(tvBatStart.getText().toString());
		    		tvBatConsumption.setText(String.valueOf(batIni-batLevel));
		    	}
			}
		}
	};
	
    public void onStartClick(View v){
    	if (! processing) {
//    		dsProc = new DatasetProcess(this, dsBaseDir, FILES_DIR_NAME);
    		if (validStatus()) {
        		processing = ! processing;
        		tvStart.setBackgroundColor(getResources().getColor(R.color.bakgroundColor2));
        		tvStart.setTextColor(getResources().getColor(R.color.foregroundColor2));
        		tvStart.setText(R.string.strCancel);
        		dsProc.start();
    		}
    	} else {
    		if (dsProc.isAlive()){
    			dsProc.cancel();
    			chrono.stop();
    		}
    		processing = ! processing;
    		tvStart.setBackgroundColor(getResources().getColor(R.color.bakgroundColor1));
    		tvStart.setTextColor(getResources().getColor(R.color.foregroundColor1));
    		tvStart.setText(R.string.strStart);    		
    	}
    }
	
	public void onLevelClick (View v){
    	if (! processing) {
        	dedupLevel++;
        	if (dedupLevel > 3) {
        		dedupLevel = 1;
        	}
        	results.setDedupLevel(dedupLevel);
        	tvDedupLevel.setText(String.valueOf(dedupLevel));
        	updateDedupType();
    	}    	
    }

	private void updateDedupType() {
    	String strDedupType;
    	String strRunMode;
		if (dedupLevel == 1) {
			strDedupType = getResources().getString(R.string.strDedup1);
    	} else if (dedupLevel == 2) {
    		strDedupType = getResources().getString(R.string.strDedup2);
		} else {
			strDedupType = getResources().getString(R.string.strDedup3);
		}
		if (runMode.equals("1")) {
			strRunMode = getResources().getString(R.string.strRunMode1);
		} else if (runMode.equals("2")) {
			strRunMode = getResources().getString(R.string.strRunMode2);
		} else {
			strRunMode = getResources().getString(R.string.strRunMode3);
		}
		tvDedupType.setText(strDedupType + " ("+ strRunMode + ")");
	}

	private boolean validStatus() {
		if ((serverAddress == null) | (serverAddress.equals(""))) {
			showDialog(NO_SERVER);
			return false;
		}
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if ((netInfo == null)  || (! netInfo.isConnected())) {
			showDialog(NO_NETWORK);
			return false;
		} else {
			try {
				io = new SecDedupIO(serverAddress);
				if (io.getDeviceID((String)tvDevice.getText()+"-"+tvOSVersion.getText()) < 0){
					showDialog(IO_ERROR);
					return false;
				} else {
					dsProc.setIO(io);
				}
			} catch (UnknownHostException e) {
				showDialog(SERVER_NOT_FOUND);
				return false;
			} catch (IOException e) {
				e.printStackTrace();
				showDialog(IO_ERROR);
				return false;
			}			
		}
		if (! dsProc.isValid()) {
			showDialog(DATASET_NOT_PRESENT);
			return false;
		}
/*		if (batCharging) {
			showDialog(BATTERY_CHARGING);
			return false;
		} */
		return true;
	}
	
	private void getNetworkType() {
		String conType = "";
		ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo netInfo = cm.getActiveNetworkInfo();
		if ((netInfo != null) && (netInfo.isConnected())) {
			if (netInfo.getType() == ConnectivityManager.TYPE_WIFI) {
				WifiManager mainWifi = (WifiManager)getSystemService(Context.WIFI_SERVICE);
				WifiInfo wifiInfo = mainWifi.getConnectionInfo();
				if(wifiInfo.getBSSID()!=null){
					int wifiSpeed=wifiInfo.getLinkSpeed();
					String units=WifiInfo.LINK_SPEED_UNITS;
					results.setNetworkType(1000+wifiSpeed);
					tvNetworkType.setText("WiFi ("+ wifiSpeed +units+")");
				} else {
					results.setNetworkType(1000);
					tvNetworkType.setText("WiFi");
				}
				
			} 
			if(netInfo.getType() == ConnectivityManager.TYPE_MOBILE) {
				int subType = netInfo.getSubtype();			
				results.setNetworkType(2000 + subType);
				switch(subType){
				case TelephonyManager.NETWORK_TYPE_1xRTT: // ~ 50-100 kbps
					conType = "1xRTT";
					break;
				case TelephonyManager.NETWORK_TYPE_CDMA:  // ~ 14-64 kbps
					conType = "CDMA";
					break;
	            case TelephonyManager.NETWORK_TYPE_EDGE:  // ~ 50-100 kbps
					conType = "EDGE";
					break;
	            case TelephonyManager.NETWORK_TYPE_EVDO_0:// ~ 50-100 kbps
					conType = "EVDO 0";
					break;
	            case TelephonyManager.NETWORK_TYPE_EVDO_A:// ~ 600-1400 kbps
					conType = "EVDO A";
					break;
	            case TelephonyManager.NETWORK_TYPE_GPRS:  // ~ 100 kbps
					conType = "GPRS";
					break;
	            case TelephonyManager.NETWORK_TYPE_HSDPA: // ~ 2-14 Mbps
					conType = "HSDPA";
					break;
	            case TelephonyManager.NETWORK_TYPE_HSPA:  // ~ 700-1700 kbps
					conType = "HSPA";
					break;
	            case TelephonyManager.NETWORK_TYPE_HSUPA: // ~ 1-23 Mbps
					conType = "HSUPA";
					break;
	            case TelephonyManager.NETWORK_TYPE_UMTS:  // ~ 400-7000 kbps
					conType = "UMTS";
					break;
	            case TelephonyManager.NETWORK_TYPE_EVDO_B:// ~ 5 Mbps
					conType = "EVDO B";
					break;
	            case TelephonyManager.NETWORK_TYPE_IDEN:  // ~25 kbps
					conType = "IDEN";
					break;
/*	            case TelephonyManager.NETWORK_TYPE_EHRPD: // ~ 1-2 Mbps 
					conType = "EHRPD";
					break;
	            case TelephonyManager.NETWORK_TYPE_LTE:   // ~ 10+ Mbps
					conType = "LTE";
					break;
	            case TelephonyManager.NETWORK_TYPE_HSPAP: // ~ 10-20 Mbps
	                conType = "HSPAP"; 
					break;*/
				}
			tvNetworkType.setText("Mobile ("+conType+")");
			}
		}
	}
	
    private String humanReadeble(float value){
    	int i = 0;
    	DecimalFormat df = new DecimalFormat("###,###.00");
    	String[] valUnit = {"B","KB","MB","GB","TB"};
    	while (value > 1024){
    		value = value / 1024;
    		i++;
    	}
		return "("+df.format(value)+valUnit[i]+")";
    }
    
    public void updateStr (int str2update, String str){
    	if (str2update == SERVER_ADDR) {
    		tvServerAddr.setText(str);
    		serverAddress = str;
    	}
    }
    
	public void updateValue(int val2update, long value) {
		DecimalFormat intFormat = new DecimalFormat("###,###");
		if (val2update == TOTALFILES) {
			tvTotalFiles.setText(intFormat.format(value));
			totalFiles = (int) value;
		} else if (val2update == TOTALSIZE) {
			tvTotalSize.setText(intFormat.format(value));
			tvTotalSizeUnit.setText(humanReadeble(value));
			totalSize = value;
			results.setTotalSize(value);
		} else if (val2update == PROCFILES){
			tvProcFiles.setText(intFormat.format(value));
			tvFilesPrct.setText(intFormat.format(value*100/totalFiles)+"%");
			results.setProcFiles((int)value);
		} else if (val2update == PROCCHUNCK){
			tvChuncks.setText(intFormat.format(value));
			results.setProcChuncks((int)value);
		} else if (val2update == DUPCHUNCK) {
			tvDupChuncks.setText(intFormat.format(value));
			if (results.getProcChuncks() != 0) {
				tvChuncksPrct.setText(intFormat.format(value*100/results.getProcChuncks())+"%");
			}
			results.setDupChuncks((int)value);
		} else if (val2update == PROCSIZE){
			procSize = value;
			pbProcessed.setProgress((int)(value*100/totalSize));
		} else if (val2update == UPLOADED) {
			tvUploaded.setText(intFormat.format(value));
			tvUploadedUnit.setText(humanReadeble(value));
			if (procSize != 0){
				tvUploadPrct.setText(intFormat.format(value*100/procSize)+"%");
			} else {
				tvUploadPrct.setText("0%");
			}
			results.setTotalUpload(value);
		}

	}

	public void startProcessing(){
		tvStartTime.setText(new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()));
		tvEndTime.setText("");
		tvBatStart.setText(String.valueOf(batLevel));
		tvBatEnd.setText("");
		tvBatConsumption.setText("");
		results.resetBatStatus();
		results.addBatStatus(batLevel, 0);
		chrono.setBase(SystemClock.elapsedRealtime());
		chrono.start();
	}
	
	public void terminateProcessing(){
		results.setTotalTime(SystemClock.elapsedRealtime()-chrono.getBase());
		chrono.stop();
		tvEndTime.setText(new SimpleDateFormat("HH:mm:ss").format(System.currentTimeMillis()));
		processing = false;
		tvStart.setBackgroundColor(getResources().getColor(R.color.bakgroundColor1));
		tvStart.setTextColor(getResources().getColor(R.color.foregroundColor1));
		tvStart.setText(R.string.strStart); 
		try {
			io.sendResults(results, dsProc.getTimes());
		} catch (IOException e) {
			showDialog(IO_ERROR);
		}
	}

	public void cancelProcessing(){
		chrono.stop();
		processing = false;
		tvStart.setBackgroundColor(getResources().getColor(R.color.bakgroundColor1));
		tvStart.setTextColor(getResources().getColor(R.color.foregroundColor1));
		tvStart.setText(R.string.strStart);    		
	}

	
	public int getDedupLevel() {
		return dedupLevel;
	}
	
	protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DATASET_NOT_PRESENT:
        	return new AlertDialog.Builder(SecDedupActivity.this)
                .setIcon(R.drawable.ic_launcher_foreground)
                .setTitle(R.string.msg_DatasetNotPresent)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {                        
                    }
                })
                .create();
        case BATTERY_CHARGING:
        	return new AlertDialog.Builder(SecDedupActivity.this)
           		.setIcon(android.R.drawable.ic_dialog_alert)
           		.setTitle(R.string.msg_BatCharging)
           		.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
           			public void onClick(DialogInterface dialog, int whichButton) {                        
           			}
           		})
           		.create();
        case NO_NETWORK:
        	return new AlertDialog.Builder(SecDedupActivity.this)
           		.setIcon(android.R.drawable.ic_dialog_alert)
           		.setTitle(R.string.msg_NoNetwork)
           		.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
           			public void onClick(DialogInterface dialog, int whichButton) {                        
           			}
           		})
           		.create();
        case SERVER_NOT_FOUND:
        	return new AlertDialog.Builder(SecDedupActivity.this)
           		.setIcon(android.R.drawable.ic_dialog_alert)
           		.setTitle(R.string.msg_ServerNotFound)
           		.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
           			public void onClick(DialogInterface dialog, int whichButton) {                        
           			}
           		})
           		.create();
        case IO_ERROR:
        	return new AlertDialog.Builder(SecDedupActivity.this)
           		.setIcon(android.R.drawable.ic_dialog_alert)
           		.setTitle(R.string.msg_IO_Error)
           		.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
           			public void onClick(DialogInterface dialog, int whichButton) {                        
           			}
           		})
           		.create();
        case NO_SERVER:
        	return new AlertDialog.Builder(SecDedupActivity.this)
           		.setIcon(android.R.drawable.ic_dialog_alert)
           		.setTitle(R.string.msg_NoServer)
           		.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
           			public void onClick(DialogInterface dialog, int whichButton) {                        
           			}
           		})
           		.create();
        }
        return null;
    }

	void ledsCtrl(int ledNr){
		for (int i=0; i < TimeControl.NR_TIMERS; i++){
			leds[i].setVisibility(View.INVISIBLE);
		}
		if ((ledNr >= 0) & (ledNr < TimeControl.NR_TIMERS)){
				leds[ledNr].setVisibility(View.VISIBLE);
		}		
	}

	public void set_dsBaseDir(String dsBaseDir) {
		this.dsBaseDir = dsBaseDir;
	}

	public void showStart() {
		tvStart.setVisibility(View.VISIBLE);
	}

	public String getServerAddress() {
		return serverAddress;
	}
}
