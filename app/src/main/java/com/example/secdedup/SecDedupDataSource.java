package com.example.secdedup;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

public class SecDedupDataSource {
	
	private SQLiteDatabase db;
	private final SQLDatabase dbHelper;
	private boolean isOpen; 
	
	public SecDedupDataSource (Context context, String dbPath){
		isOpen = false;
		dbHelper = new SQLDatabase(context, dbPath);
	}

	public void open() throws SQLException {
		db = dbHelper.getWritableDatabase();
		isOpen = true;
	}

	public void reset() throws SQLException {
		db = dbHelper.getWritableDatabase();
		dbHelper.clearDatabase(db);
		isOpen = true;
	}	
	
	public void close() {
		dbHelper.close();
		isOpen = false;
	}
	
	public void addHash (String hash){
		ContentValues values = new ContentValues();
		values.put(SQLDatabase.COLUMN_SESSION, 1);
		values.put(SQLDatabase.COLUMN_HASH, hash);
		db.insert(SQLDatabase.TABLE_HASHS, null, values);
	}
	
	public boolean hashExists (String hash) {
		if (isOpen) {
			String[] Columns = {SQLDatabase.COLUMN_HASH};
			String[] Values  = {hash};
			Cursor c = db.query(SQLDatabase.TABLE_HASHS, Columns, SQLDatabase.COLUMN_HASH+"=?", Values, null, null, null);
			int rows = c.getCount();
			c.close();
			return rows > 0;
		} else {
			return false;
		}
	}

	public boolean rolHashExists (long rolHash) {
		String[] Columns = {SQLDatabase.COLUMN_ROLHASH};
		String[] Values  = {String.valueOf(rolHash)};
		Cursor c = db.query(SQLDatabase.TABLE_HASHS, Columns, SQLDatabase.COLUMN_ROLHASH+"=?", Values, null, null, null);
		int rows = c.getCount();
		c.close();
		return rows > 0;
	}

	public boolean isOpen() {
		return isOpen;
	}

	public void add2Hash(String hash, long rolHash) {
		ContentValues values = new ContentValues();
		values.put(SQLDatabase.COLUMN_SESSION, 1);
		values.put(SQLDatabase.COLUMN_HASH, hash);
		values.put(SQLDatabase.COLUMN_ROLHASH, String.valueOf(rolHash));
		db.insert(SQLDatabase.TABLE_HASHS, null, values);
	}

}
