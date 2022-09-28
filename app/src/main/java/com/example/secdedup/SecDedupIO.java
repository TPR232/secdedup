package com.example.secdedup;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.UnknownHostException;

public class SecDedupIO {
	
	private static final int TCP_PORT = 9999;
	private final Socket s;
	private final InputStream in;
	private final DataInputStream dis;
	private final OutputStream out;
	private final DataOutputStream dos;
	private int deviceID = -1;

	public SecDedupIO(String serverAddress) throws IOException{
		s = new Socket(serverAddress, TCP_PORT);
		in = s.getInputStream();
		dis = new DataInputStream(in);
		out = s.getOutputStream();
		dos = new DataOutputStream(out);
	}

	public int getDeviceID(String deviceSign) throws IOException {
		byte bBufer;
		dos.writeByte(SDProto.DEVICE_SIGN);
		dos.writeInt(deviceSign.length());
		dos.writeBytes(deviceSign);
		dos.flush();
		bBufer = dis.readByte();
		if (bBufer == SDProto.DEVICE_ID){
			deviceID = dis.readInt();
			return deviceID;
		}
		return -1;
	}
	
	public int sendBlock (byte[] encryptedBlock, byte[] blkHash) throws IOException{
		int uploadSize = 0;
		byte bBufer = 0;
		dos.writeByte(SDProto.BHASH);
		dos.writeInt(deviceID);
		dos.writeInt(blkHash.length);
		dos.write(blkHash);
		dos.flush();
		bBufer = dis.readByte();
		uploadSize = blkHash.length + 9;
		if (bBufer == SDProto.HASH_OK){
			dos.writeByte(SDProto.BLOCK);
			dos.writeInt(deviceID);
			dos.writeInt(encryptedBlock.length);
			dos.write(encryptedBlock);
			dos.flush();
			bBufer = dis.readByte();
			uploadSize += (encryptedBlock.length + 9);						
		}
		return uploadSize;
	}

	public long sendFile (File encryptedFile, byte[] fileHash) throws IOException{
		InputStream fIn;
		byte[] buffer = new byte[DatasetProcess.FBUFFERSIZE];
		long uploadSize = 0;
		byte bBufer = 0;
	    int dataRead;
	    dos.writeByte(SDProto.FHASH);
		dos.writeInt(deviceID);
		dos.writeInt(fileHash.length);
		dos.writeInt(DatasetProcess.FBUFFERSIZE);
		dos.writeLong(encryptedFile.length());
		dos.write(fileHash);
		dos.flush();
		bBufer = dis.readByte();
		uploadSize = fileHash.length + 21;			
		if (bBufer == SDProto.HASH_OK){
			fIn = new FileInputStream(encryptedFile);
			while ((dataRead = fIn.read(buffer)) != -1) {  // CANCELAR
				dos.writeByte(SDProto.FILE_CHUNCK);
				dos.writeInt(deviceID);
				dos.writeInt(dataRead);
				dos.write(buffer, 0, dataRead);
				dos.flush();
				bBufer = dis.readByte(); // CHUNCK_OK
				uploadSize += (dataRead + 9);						
			}
		    fIn.close();
		    bBufer = dis.readByte(); // FILE_OK
		}
		return uploadSize;
	}

	public void sendResults(SecDedupResults results, long[] timers) throws IOException {
		byte bBufer = 0;
	    dos.writeByte(SDProto.RESULTS);
		dos.writeInt(deviceID);
		dos.writeLong(results.getTotalTime());
		dos.writeLong(results.getTotalSize());
		dos.writeInt(results.getProcFiles());
		dos.writeInt(results.getProcChuncks());
		dos.writeInt(results.getDupChuncks());
		dos.writeLong(results.getTotalUpload());
		dos.writeInt(results.getDedupLevel());
		dos.writeInt(results.getNetworkType());
		dos.writeByte(timers.length);
		for (int i = 0; i < timers.length; i++){
			dos.writeLong(timers[i]);
		}
		int b = results.getBatSatusCount();
		dos.writeByte(b);
		for (int i = 0; i < b; i++){
			BatteryStatus bs = results.getBatStatus(i);
			dos.writeInt(bs.getBatteryLevel());
			dos.writeLong(bs.getElapsedTime());
		}
		dos.flush();
		bBufer = dis.readByte();
		if (bBufer == SDProto.RESULTS_OK){
			dos.writeByte(SDProto.PROC_END);
			dos.flush();
			out.close();
			in.close();
			s.close();
		}
	}

	public void procCancel() {
		try {
			dos.writeByte(SDProto.PROC_CANCEL);
			dos.flush();
			out.close();
			in.close();
			s.close();
		} catch (IOException e) {
		}
	}
	
}
