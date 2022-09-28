package com.example.secdedup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import android.os.Handler;
import android.os.Message;

public class DatasetProcess extends Thread {
	
	static final int FBUFFERSIZE               = 1024*4;
	private static final int FIXEDBLOCKSIZE    = 1024*4;
	private static final int SLIDESIZE         = 32; 
	private static final int KEYSIZE           = 16;

	private static SecDedupActivity mActivity;
	private final SecDedupDataSource dataSource;

	private final File dsPath;
	private final File tmpPath;
	private static int  dsProcFiles;
	private static long dsProcChuncks;
	private static long dsDupChuncks;
	private static long dsProcSize;
	private static long dsUpload;
	private final int procDedupLevel;
	private boolean canceled;
	private SecDedupIO io;
	private final TimeControl timeCtrl;
	
	
	public DatasetProcess (SecDedupActivity mainActivity, String dsBaseDir, String dsFilesDir) {
		DatasetProcess.mActivity    = mainActivity;
		this.tmpPath		= new File(dsBaseDir + File.separator + "TEMP"); 
		this.dsPath	        = new File(dsBaseDir + File.separator + dsFilesDir);		
		this.procDedupLevel = DatasetProcess.mActivity.getDedupLevel();
		this.canceled       = false;
		DatasetProcess.dsUpload = 0;
		dataSource = new SecDedupDataSource(mainActivity, this.tmpPath.getAbsolutePath());
		timeCtrl = new TimeControl(DatasetProcess.mActivity ); 

	}
	
	public void run () {
		if (! dataSource.isOpen()){
			dataSource.reset();
		}
		if (! canceled)  {
			dsProcFiles   = 0;
			dsProcChuncks = 0;
			dsDupChuncks  = 0;
			dsProcSize    = 0;
			dsUpload      = 0;
			Queue<File> fq = new LinkedList<File>();
			dsProcFile.sendEmptyMessage(0);
			dsProcUpload.sendEmptyMessage(0);
			fq.add(dsPath);
			dsProcStarted.sendEmptyMessage(0);
			while (fq.size() > 0){
				File[] files = fq.poll().listFiles();
				for (int f = 0, n = files.length; f < n; f++) {
					if (canceled) break;
					if(files[f].isDirectory()){
						fq.add(files[f]);
					} else {
						if (files[f].length() > 0) {
							if (procDedupLevel == 1) {
								dsProcFile(files[f]);
								dsProcChuncks++;
								dsProcSize += files[f].length();
							} else if (procDedupLevel == 2) {
								dsProcFileFSB(files[f]);
							} else if (procDedupLevel == 3) {
								dsProcFileVSB(files[f]);
							}
							dsProcFiles++;
							dsProcFile.sendEmptyMessage(0);							
						}
					}				
				}
			}
		}
		if (! canceled)  {
			dataSource.close();
			dsProcTerminated.sendEmptyMessage(0);
		}
	}
	
	private String toHex(byte[] digest){
		if (! canceled) {
		    StringBuilder sb = new StringBuilder(digest.length*2);
		    for(byte b: digest)
		      sb.append(Integer.toHexString(b+0x800).substring(1));
			return sb.toString();
		} else {
			return "";
		}
	}
	
	public void cancel() {
		canceled = true;
		dataSource.close();
		timeCtrl.resetTimers();
		io.procCancel();
		dsProcCanceled.sendEmptyMessage(0);
	}

/*
 * File deduplication level
 */
	
	private void dsProcFile(File file) {
		byte[] clearFileHash = null;
		byte[] encryptedFileHash = null;
		if (! canceled){
			timeCtrl.startTimer(TimeControl.CLEAR_HASH_TIMER);
			clearFileHash = getFileHash (file);
			timeCtrl.stopTimer();
			String hexDigest = toHex(clearFileHash);
			timeCtrl.startTimer(TimeControl.DBASE_TIMER);				
			if (! dataSource.hashExists(hexDigest)){
				timeCtrl.startTimer(TimeControl.CRIPTO_TIMER);
				File encryptedFile = encryptFile(file,clearFileHash);
				timeCtrl.startTimer(TimeControl.CRIPTO_HASH_TIMER);
				encryptedFileHash = getFileHash(encryptedFile);
				timeCtrl.startTimer(TimeControl.COMM_TIMER);				
				sendFile(encryptedFile,encryptedFileHash);
				timeCtrl.stopTimer();
				if (! canceled){
					timeCtrl.startTimer(TimeControl.DBASE_TIMER);				
					dataSource.addHash(hexDigest);
					timeCtrl.stopTimer();
				}
			} else {
				timeCtrl.stopTimer();
				dsDupChuncks++;
			}
		}
	}
	
	private void sendFile(File encryptedFile, byte[] encryptedFileHash) {
		if (! canceled){
			try {
				dsUpload += io.sendFile(encryptedFile, encryptedFileHash);
				dsProcUpload.sendEmptyMessage(0);	
			} catch (IOException e) {
				cancel();
				dsProcIOError.sendEmptyMessage(0);
			}
		}
	}

	private File encryptFile(File file, byte[] clearFileHash) {
		byte[] buffer = new byte[FBUFFERSIZE];
		byte[] fKey = new byte[KEYSIZE];
		byte[] fIV = new byte[KEYSIZE];
		int dataRead;
		InputStream fIn;
		OutputStream fOut;
		File encryptedFile = new File(tmpPath.getAbsolutePath() + File.separator +"encrypted");
		if (! canceled){
			System.arraycopy(clearFileHash, 0, fKey, 0, KEYSIZE);
			System.arraycopy(clearFileHash,clearFileHash.length-KEYSIZE, fIV,  0, KEYSIZE);
			
			try {
				fIn = new FileInputStream(file);
				fOut = new FileOutputStream(encryptedFile);
				SecretKeySpec key = new SecretKeySpec(fKey, "AES");
			    IvParameterSpec ivSpec = new IvParameterSpec(fIV);
			    Cipher cipher = Cipher.getInstance("AES/CTR/NoPadding", "BC");
			    cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
			    
			    CipherOutputStream cos = new CipherOutputStream(fOut, cipher);
			    dataRead = 0;
			    while (((dataRead = fIn.read(buffer)) != -1) & (! canceled)) {
			    	cos.write(buffer, 0, dataRead);
				}
			    cos.flush();
			    cos.close();
				fIn.close();
			} catch (NoSuchAlgorithmException e) {
				cancel();
				e.printStackTrace();
			} catch (NoSuchProviderException e) {
				cancel();
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				cancel();
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				cancel();
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				cancel();
				e.printStackTrace();
			} catch (FileNotFoundException e) {
				cancel();
				e.printStackTrace();
			} catch (IOException e) {
				cancel();
				e.printStackTrace();
			}
		}
		return encryptedFile;
	}

	private byte[] getFileHash (File f){
		byte[] buffer = new byte[FBUFFERSIZE];
		MessageDigest hashFile = null;
		byte[] digest = null;
		int dataRead;
		try {
			hashFile = MessageDigest.getInstance("SHA1", "BC");
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			e.printStackTrace();
		}
		InputStream fIn;
		try {
			fIn = new FileInputStream(f);
			fIn = new DigestInputStream(fIn, hashFile);
			dataRead = 0;
			do {
				dataRead = fIn.read(buffer);
			} while ((dataRead != -1) & (! canceled));
			if (! canceled) {
				digest = hashFile.digest();
			}
			fIn.close();
		} catch (FileNotFoundException e) {
			cancel();
			e.printStackTrace();
		} catch (IOException e) {
			cancel();
			e.printStackTrace();
		}
		return digest;
	}
	
/*
 * Fixed size block deduplication level
 */
	
	private void dsProcFileFSB(File file) {
		byte[] buffer = new byte[FIXEDBLOCKSIZE];
		byte[] clearBlockHash = null;
		byte[] encryptedBlockHash = null;
		int dataRead = 0;
		InputStream fIn;
		try {
			fIn = new FileInputStream(file);
			do {		
				dataRead = fIn.read(buffer);
				if (dataRead != -1) {
					timeCtrl.startTimer(TimeControl.CLEAR_HASH_TIMER);
					clearBlockHash = getBlockHash (buffer,dataRead);
					timeCtrl.stopTimer();
					String hexDigest = toHex(clearBlockHash);
					dsProcChuncks++;
					if (! dataSource.hashExists(hexDigest)){
						timeCtrl.startTimer(TimeControl.CRIPTO_TIMER);
						byte[] encryptedBuffer  = encryptBlock(buffer,clearBlockHash,dataRead);
						timeCtrl.startTimer(TimeControl.CRIPTO_HASH_TIMER);
						encryptedBlockHash = getBlockHash (buffer,dataRead);
						timeCtrl.startTimer(TimeControl.COMM_TIMER);
						sendBlock(encryptedBuffer,encryptedBlockHash);
						timeCtrl.stopTimer();
						if (! canceled) {
							timeCtrl.startTimer(TimeControl.DBASE_TIMER);
							dataSource.addHash(hexDigest);
							timeCtrl.stopTimer();
						}
					} else {
						dsDupChuncks++;
					}
					dsProcSize += dataRead;
					dsProcFile.sendEmptyMessage(0);
				}
			} while ((dataRead != -1) & (! canceled));
			fIn.close();
		} catch (FileNotFoundException e) {
			cancel();
			e.printStackTrace();
		} catch (IOException e) {
			cancel();
			e.printStackTrace();
		}
	}

	
/*
 * Variable size block deduplication level
 */
	
	private void dsProcFileVSB(File file) {

		byte[] buffer = new byte[FIXEDBLOCKSIZE+SLIDESIZE];
		byte[] toProc = null;
		long rollingHash;
		byte[] clearBlockHash = null;
		int dataRead = 0;
		int slide = 0;
		int shift = 0;
		boolean dupBlock = false;
		InputStream fIn;
		try {
			fIn = new FileInputStream(file);
			RollingHash rHash = new RollingHash(FIXEDBLOCKSIZE, SLIDESIZE);
			do {		
				dataRead = fIn.read(buffer, shift, FIXEDBLOCKSIZE + SLIDESIZE - shift);
				if (dataRead != -1) {
					if (dataRead >= FIXEDBLOCKSIZE) {
						timeCtrl.startTimer(TimeControl.CLEAR_HASH_TIMER);
						rollingHash = rHash.getBlockHash(buffer);
						timeCtrl.stopTimer();
						slide = 0;
						do {
							if (! canceled){
								timeCtrl.startTimer(TimeControl.DBASE_TIMER);
								if (dataSource.rolHashExists(rollingHash)) {
									timeCtrl.startTimer(TimeControl.CLEAR_HASH_TIMER);
									clearBlockHash = getBlockHash (Arrays.copyOfRange(buffer, slide, FIXEDBLOCKSIZE + slide),FIXEDBLOCKSIZE);
									timeCtrl.stopTimer();
									String hexDigest = toHex(clearBlockHash);
									timeCtrl.startTimer(TimeControl.DBASE_TIMER);
									dupBlock = (dataSource.hashExists(hexDigest));
								}
								timeCtrl.stopTimer();
								if (! dupBlock){
									timeCtrl.startTimer(TimeControl.CLEAR_HASH_TIMER);
									rollingHash=rHash.updateHash();
									timeCtrl.stopTimer();
									slide++;
								}
							}
						} while ((! dupBlock) & (slide < dataRead + shift - FIXEDBLOCKSIZE) & (! canceled));
						if (! canceled) {
							if (dupBlock) {							
								byte[] headBlk = null;
								if (slide > 0){
									headBlk = Arrays.copyOfRange(buffer, 0, slide);
								}
								if ((toProc != null) && (toProc.length == FIXEDBLOCKSIZE)) {
									dsProcessBlock(toProc,toProc.length, null, -1);
									toProc = null;
								}
								if (toProc != null){
									if (headBlk != null) {
										byte[] tmpBuffer = Arrays.copyOfRange(toProc, 0, toProc.length);
										toProc = new byte[tmpBuffer.length+headBlk.length];
										System.arraycopy(tmpBuffer, 0, toProc, 0, tmpBuffer.length);
										System.arraycopy(headBlk, 0, toProc, tmpBuffer.length , headBlk.length);
									}
									dsProcessBlock(toProc, toProc.length, null, -1);
									toProc = null;
								} else {
									if (headBlk != null) {
										dsProcessBlock(headBlk, headBlk.length, null, -1);
									}
								}
								dsProcessBlock(Arrays.copyOfRange(buffer, slide, FIXEDBLOCKSIZE + slide),FIXEDBLOCKSIZE, clearBlockHash, rollingHash);
								if (slide < SLIDESIZE){
									System.arraycopy(buffer, FIXEDBLOCKSIZE + slide, buffer, 0, SLIDESIZE - slide);
								}
								shift = SLIDESIZE - slide;
								slide = 0;
							} else {
								if (toProc != null){
									dsProcessBlock(toProc, toProc.length, null, -1);
								}
								toProc = Arrays.copyOfRange(buffer, 0, FIXEDBLOCKSIZE);
								System.arraycopy(buffer, FIXEDBLOCKSIZE, buffer, 0, SLIDESIZE);
								shift = SLIDESIZE;
								slide = 0;

/*								if (slide > 0){
									if (toProc == null){
										byte[] tmpBuffer = Arrays.copyOfRange(buffer, 0, slide);
										dsProcessBlock(tmpBuffer, tmpBuffer.length);
									} else {
										byte[] tmpBuffer = new byte[toProc.length + slide -1];
										System.arraycopy(toProc, 0, tmpBuffer, 0, toProc.length);
										System.arraycopy(buffer, 0, tmpBuffer, toProc.length , slide);
										dsProcessBlock(tmpBuffer, tmpBuffer.length);
										toProc = null;
									}
								}
								if (slide < SLIDESIZE){
									System.arraycopy(buffer, FIXEDBLOCKSIZE + slide, buffer, 0, SLIDESIZE - slide);
									shift = SLIDESIZE - slide;
									slide = 0;
								}
							} else {
								if (toProc != null){
									dsProcessBlock(toProc, toProc.length);
								}
								toProc = Arrays.copyOfRange(buffer, 0, FIXEDBLOCKSIZE);
								System.arraycopy(buffer, 0, buffer, FIXEDBLOCKSIZE - 1, SLIDESIZE);
								shift = SLIDESIZE;
								slide = 0;*/
							}
						}
					} else {
						if (! canceled) { 
							if (toProc != null){
								if (toProc.length == FIXEDBLOCKSIZE) {
									dsProcessBlock(toProc,toProc.length, null, -1);
									toProc = null;
									dsProcessBlock(Arrays.copyOfRange(buffer, 0, dataRead + shift), dataRead + shift, null , -1);
								} else {
									byte[] tmpBuffer = new byte[toProc.length + dataRead + shift];
									System.arraycopy(toProc, 0, tmpBuffer, 0, toProc.length);
									System.arraycopy(buffer, 0, tmpBuffer, toProc.length , dataRead + shift);
									dsProcessBlock(tmpBuffer, tmpBuffer.length, null , -1);
									toProc = null;
								}
							} else {
								dsProcessBlock(Arrays.copyOfRange(buffer, 0, dataRead + shift), dataRead + shift, null , -1);
							}
						}
					}
				}
				dsProcFile.sendEmptyMessage(0);
			} while ((dataRead != -1) & (! canceled));
			if (toProc != null){
				dsProcessBlock(toProc, toProc.length, null , -1);
			}
			fIn.close();
		} catch (FileNotFoundException e) {
			cancel();
			e.printStackTrace();
		} catch (IOException e) {
			cancel();
			e.printStackTrace();
		}
	}

	private void dsProcessBlock(byte[] block, int blkSize, byte[] clearBlockHash2, long rollingHash2) {
		if (! canceled){
			byte[] clearBlockHash = null;
			byte[] encryptedBlockHash = null;
			long rollingHash = -1;
			if (clearBlockHash2 != null){
				clearBlockHash = Arrays.copyOfRange(clearBlockHash2, 0, clearBlockHash2.length);
			} else {
				timeCtrl.startTimer(TimeControl.CLEAR_HASH_TIMER);
				clearBlockHash = getBlockHash (block,blkSize);
				timeCtrl.stopTimer();
			}
			String hexDigest = toHex(clearBlockHash);
			dsProcChuncks++;
			dsProcSize += blkSize;
			dsProcFile.sendEmptyMessage(0);	
			if (! dataSource.hashExists(hexDigest)){
				if (blkSize == FIXEDBLOCKSIZE) {
					if (rollingHash2 > 0){
						rollingHash = rollingHash2;
					} else {
						RollingHash rHash = new RollingHash(FIXEDBLOCKSIZE, 0);
						rollingHash = rHash.getBlockHash(block);
					}
				}
				timeCtrl.startTimer(TimeControl.CRIPTO_TIMER);
				byte[] encryptedBuffer  = encryptBlock(block,clearBlockHash,blkSize);
				timeCtrl.startTimer(TimeControl.CRIPTO_HASH_TIMER);
				encryptedBlockHash = getBlockHash (encryptedBuffer,encryptedBuffer.length);
				timeCtrl.startTimer(TimeControl.COMM_TIMER);
				sendBlock(encryptedBuffer,encryptedBlockHash);
				if (! canceled) {
					timeCtrl.startTimer(TimeControl.DBASE_TIMER);
					if (rollingHash < 0) {
						dataSource.addHash(hexDigest);					
					} else {
						dataSource.add2Hash(hexDigest, rollingHash);
					}
				}
			} else {
				dsDupChuncks++;
			}
			timeCtrl.stopTimer();
		}
	}

/*
 * 	File block processing
 */

	private void sendBlock(byte[] encryptedBlock, byte[] encryptedBlockHash) {
		if (! canceled) {
			try {
				dsUpload += io.sendBlock(encryptedBlock, encryptedBlockHash);
				dsProcUpload.sendEmptyMessage(0);	

			} catch (IOException e) {
				cancel();
				dsProcIOError.sendEmptyMessage(0);
			}
		}		
	}

	private byte[] encryptBlock(byte[] block, byte[] clearBlockHash, int blockSize) {
		byte[] bKey = new byte[KEYSIZE];
		byte[] bIV  = new byte[KEYSIZE];
		byte[] encryptedBlock = null;
		if (! canceled) {
			System.arraycopy(clearBlockHash, 0, bKey, 0, KEYSIZE);
			System.arraycopy(clearBlockHash,clearBlockHash.length-KEYSIZE, bIV,  0, KEYSIZE);

			SecretKeySpec key = new SecretKeySpec(bKey, "AES");
		    IvParameterSpec ivSpec = new IvParameterSpec(bIV);
		    Cipher cipher;
			try {
				cipher = Cipher.getInstance("AES/CTR/NoPadding", "BC");
			    cipher.init(Cipher.ENCRYPT_MODE, key, ivSpec);
			    encryptedBlock = cipher.doFinal(block, 0, blockSize);		    
			} catch (NoSuchAlgorithmException e) {
				cancel();
				e.printStackTrace();
			} catch (NoSuchProviderException e) {
				cancel();
				e.printStackTrace();
			} catch (NoSuchPaddingException e) {
				cancel();
				e.printStackTrace();
			} catch (InvalidKeyException e) {
				cancel();
				e.printStackTrace();
			} catch (InvalidAlgorithmParameterException e) {
				cancel();
				e.printStackTrace();
			} catch (IllegalBlockSizeException e) {
				cancel();
				e.printStackTrace();
			} catch (BadPaddingException e) {
				cancel();
				e.printStackTrace();
			}
		}		
		return encryptedBlock;
	}

	private byte[] getBlockHash(byte[] block, int blockSize) {
		MessageDigest hashBlock = null;
		try {
			hashBlock = MessageDigest.getInstance("SHA1", "BC");
		} catch (NoSuchAlgorithmException e) {
			cancel();
			e.printStackTrace();
		} catch (NoSuchProviderException e) {
			cancel();
			e.printStackTrace();
		}
		hashBlock.update(block, 0, blockSize);
		return hashBlock.digest();
	}

	
	
/*
 * Message handlers 	
 */

/*	private static Handler dsInfoHander = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.updateValue(SecDedupActivity.TOTALFILES, dsTotalFiles);
        	mActivity.updateValue(SecDedupActivity.TOTALSIZE, dsTotalSize);
        }		
	};*/

	private static final Handler dsProcFile = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.updateValue(SecDedupActivity.PROCFILES, dsProcFiles);
        	mActivity.updateValue(SecDedupActivity.PROCCHUNCK, dsProcChuncks);
        	mActivity.updateValue(SecDedupActivity.DUPCHUNCK, dsDupChuncks);
        	mActivity.updateValue(SecDedupActivity.PROCSIZE, dsProcSize);
        }		
	};

	private static final Handler dsProcUpload = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.updateValue(SecDedupActivity.UPLOADED, dsUpload);
        }		
	};
	
	private static final Handler dsProcTerminated = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.terminateProcessing();
        }
	};

	private static final Handler dsProcCanceled = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.cancelProcessing();
        }
	};

	private static final Handler dsProcStarted = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.startProcessing();
        }
	};

	private static final Handler dsProcIOError = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        	mActivity.showDialog(SecDedupActivity.IO_ERROR);
        }		
	};

	public boolean isValid() {
		return dsPath.exists();
	}

	public void setIO(SecDedupIO sdIO) {
		this.io = sdIO;
		
	}

	public long[] getTimes() {
		return timeCtrl.getTimers();
	}
	
}
