package com.example.secdedup;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class SQLDatabase extends SQLiteOpenHelper {

	
	private static final String DATABASE_NAME    = "SecDedup.db";
	private static final int    DATABASE_VERSION = 1;

	public static final String TABLE_HASHS    = "hashs";
	public static final String COLUMN_ID      = "_id";
	public static final String COLUMN_SESSION = "session";
	public static final String COLUMN_HASH    = "hash";
	public static final String COLUMN_ROLHASH = "rolling_hash";
	public static final String INDEX_HASH     = "hash_idx";
	public static final String INDEX_ROLHASH  = "rolhash_idx";
	
	private static final String DATABASE_CREATE  = "create table "
		    + TABLE_HASHS + "(" 
			+ COLUMN_ID + " integer primary key autoincrement, " 
			+ COLUMN_SESSION + " integer not null, "
		    + COLUMN_HASH + " text not null, " 
			+ COLUMN_ROLHASH + " integer);";
	private static final String CREATE_INDEX_HASH = "create unique index "
			+ INDEX_HASH + " ON "
			+ TABLE_HASHS + "(" + COLUMN_HASH + ")";
	private static final String CREATE_INDEX_ROLHASH = "create index "
			+ INDEX_ROLHASH + " ON "
			+ TABLE_HASHS + "(" + COLUMN_ROLHASH + ")";

	public SQLDatabase(Context context, String dbPath) {
//		super(context, dbPath+File.separator+DATABASE_NAME, null, DATABASE_VERSION);
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}


	@Override
	public void onCreate(SQLiteDatabase db) {
		db.execSQL(DATABASE_CREATE);
		db.execSQL(CREATE_INDEX_HASH);
		db.execSQL(CREATE_INDEX_ROLHASH);
	}


	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		db.execSQL("DROP INDEX IF EXISTS " + INDEX_ROLHASH);
		db.execSQL("DROP INDEX IF EXISTS " + INDEX_HASH);
	    db.execSQL("DROP TABLE IF EXISTS " + TABLE_HASHS);
	    onCreate(db);	
	}

	public void clearDatabase (SQLiteDatabase db) {
		db.execSQL("DELETE FROM " + TABLE_HASHS + " WHERE "+COLUMN_SESSION+"=1");
//		onUpgrade(db,0,0);
	}
	
}
