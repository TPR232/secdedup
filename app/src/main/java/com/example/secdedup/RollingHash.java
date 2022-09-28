package com.example.secdedup;

public class RollingHash { 
	final static int RP = 370248451; 
	final static int R = 256;
	private final int blkSize;
	private long Z;
	private long h;
	private final byte[] blk;
	private int blkPoiter;
	
	public RollingHash (int blockSize, int slideSize){
		blkSize = blockSize;
		blk = new byte[blockSize+slideSize];
		Z = 1;
		for (int i = 1; i <= blkSize -1; i++){
			Z = (Z * R) % RP;
		}
	}
	
	public long getBlockHash (byte[] block){
		h = 0;
		for (int i = 0; i < blkSize; i++){
			h = (h * R + ((int)block[i]&0xFF)) % RP;
		}
		System.arraycopy(block, 0, blk, 0, blk.length);
		blkPoiter=0;
		return h;
	}
	
	public long updateHash (){
		h = (h + RP - Z *  ((int)blk[blkPoiter]&0xFF) % RP) % RP;
		h = (h * R + ((int)blk[blkSize+blkPoiter]&0xFF)) % RP;
		blkPoiter++;
		return h;
	}

}
