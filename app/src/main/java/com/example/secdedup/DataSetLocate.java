package com.example.secdedup;

import java.io.File;
import java.util.LinkedList;
import java.util.Queue;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;

public class DataSetLocate extends Thread {

	private static final String BASE_DIR_NAME  = "SecDedup-Dataset";
	
	private static SecDedupActivity mActivity;
	private static String dsBaseDir;
	private static int dsTotalFiles;
	private static long dsTotalSize;
	
	private final String dsFilesDir;
	
	public DataSetLocate (SecDedupActivity mainActivity, String filesDir){
		DataSetLocate.mActivity    = mainActivity;
		DataSetLocate.dsBaseDir = Environment.getExternalStorageDirectory().getAbsolutePath();
		DataSetLocate.dsTotalFiles = 0;
		DataSetLocate.dsTotalSize = 0;
		dsFilesDir = filesDir;
	}

	public void run () {
		boolean dsFounded = false;
		Queue<File> dq = new LinkedList<File>();
		dq.add(new File(dsBaseDir));
		while ((dq.size() > 0) & (!dsFounded)) {
			File[] files = dq.poll().listFiles();
			if (files != null) {
				for (int f = 0, n = files.length; f < n; f++) {
					if(files[f].isDirectory()){
						if (files[f].getName().equals(BASE_DIR_NAME))  {
							dsBaseDir = files[f].getAbsolutePath();
							dsFounded = true;
							dsBasedir.sendEmptyMessage(0);
							break;
						} else {
							dq.add(files[f]);						
						}
					}				
				}
				
			}
		}
		if (dsFounded){
			Queue<File> fq = new LinkedList<File>();
			fq.add(new File(dsBaseDir + File.separator + dsFilesDir));
			while (fq.size() > 0){
				File[] files = fq.poll().listFiles();
				for (int f = 0, n = files.length; f < n; f++) {
					if(files[f].isDirectory()){
						fq.add(files[f]);
					} else {
						if (files[f].length() > 0) {
							dsTotalFiles++;
							dsTotalSize += files[f].length();
							dsInfoHander.sendEmptyMessage(0);
							try {
								Thread.sleep(5);
							} catch (InterruptedException e) {
								e.printStackTrace();
							}
						}
					}				
				}
			}
			dsLocTerminated.sendEmptyMessage(0);
		} else {
			dsProcError.sendEmptyMessage(0);
		}
	}
	

	
	private static final Handler dsProcError = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.showDialog(SecDedupActivity.DATASET_NOT_PRESENT);
        }		
	};

	private static final Handler dsBasedir = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.set_dsBaseDir(dsBaseDir);
        }		
	};

	private static final Handler dsInfoHander = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.updateValue(SecDedupActivity.TOTALFILES, dsTotalFiles);
        	mActivity.updateValue(SecDedupActivity.TOTALSIZE,  dsTotalSize);
        }		
	};

	private static final Handler dsLocTerminated = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.showStart();
        }		
	};
	
}
