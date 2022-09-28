package com.example.secdedup;

public interface SDProto {
	
	byte DEVICE_SIGN  = 1;
	byte DEVICE_ID    = 2;
	byte BHASH	  	   = 3;
	byte FHASH	  	   = 4;
	byte HASH_OK  	   = 5;
	byte HASH_DUP 	   = 6;
	byte FILE_CHUNCK  = 7;
	byte CHUNCK_OK    = 8;
	byte CHUNCK_ERROR = 9;
	byte FILE_OK      = 10;
	byte FILE_ERROR   = 11;
	byte BLOCK		   = 12;
	byte BLOCK_OK 	   = 13;
	byte BLOCK_ERROR  = 14;
	byte RESULTS      = 97;
	byte RESULTS_OK   = 98;
	byte PROC_CANCEL  = 99;
	byte PROC_END     = 100;

}
