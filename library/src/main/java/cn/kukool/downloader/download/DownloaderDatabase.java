package cn.kukool.downloader.download;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.HashMap;
import java.util.Map;

import cn.kukool.downloader.util.ContentValuesFactory;

public class DownloaderDatabase extends SQLiteOpenHelper {
    public final static String DB_NAME = "downloader.db";
    public final static int DB_VERSION = 4;

    public static final String TABLE_DOWNLOADED = "downloaded";
    public static final String TABLE_DOWNLOADING = "downloading";
    private static final String[] TABLE_NAMES = {
            TABLE_DOWNLOADED, TABLE_DOWNLOADING
    };

    private final static String COLUMN_ID = "_id";
    public final static String COLUMN_URL = "url";
    public final static String COLUMN_POSTDATA = "postdata";
    public final static String COLUMN_DIR = "dir";
    public final static String COLUMN_FILENAME = "filename";
    public final static String COLUMN_UID = "uid";

    //downloaded
    public final static String COLUMN_SIZE = "size";
    public final static String COLUMN_DATE = "date";

    //downloading
    public final static String COLUMN_FILESIZE = "filesize";
    public final static String COLUMN_BLOCKSIZE = "blocksize";
    public final static String COLUMN_THREADID = "threadid";
    public final static String COLUMN_THREADPOS = "threadpos";
    public final static String COLUMN_INFO = "info";

    private static DownloaderDatabase instance = null;

    public static synchronized DownloaderDatabase getInstance(Context context) {
        if (instance == null)
            instance = new DownloaderDatabase(context);
        return instance;
    }

    private DownloaderDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String sql = createDownloadedTable();
        db.execSQL(sql);

        // add downloading progress
        sql = createDownloadingTable();
        db.execSQL(sql);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (newVersion != 4) {
            for (String TABLE_NAME : TABLE_NAMES) {
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
            }

            onCreate(db);
        }
    }

    private String createDownloadingTable() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_DOWNLOADING + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY," //_id
                + COLUMN_URL + " nvarchar(1024), "  //url
                + COLUMN_POSTDATA + " nvarchar(1024), " // post data
                + COLUMN_DIR + " nvarchar(256), " // dir
                + COLUMN_FILENAME + " nvarchar(256), " //filename
                + COLUMN_FILESIZE + " INTEGER DEFAULT 0," //filesize
                + COLUMN_BLOCKSIZE + " INTEGER,"  //blocksize
                + COLUMN_THREADID + " INTEGER DEFAULT 0," //threadid
                + COLUMN_THREADPOS + " INTEGER," //threadpos
                + COLUMN_UID + " nvarchar(1024)," // uid
                + COLUMN_INFO + " nvarchar(1024)" // info
                + ")";
    }

    private String createDownloadedTable() {
        return "CREATE TABLE IF NOT EXISTS " + TABLE_DOWNLOADED + "("
                + COLUMN_ID + " INTEGER PRIMARY KEY, " // _id
                + COLUMN_URL + " nvarchar(1024), " // url
                + COLUMN_POSTDATA + " nvarchar(1024), " // post data
                + COLUMN_DIR + " nvarchar(256), " // filepath
                + COLUMN_FILENAME + " nvarchar(256), " // filename
                + COLUMN_SIZE + " INTEGER, " // size
                + COLUMN_DATE + " LONG, " // date
                + COLUMN_UID + " nvarchar(1024)," // uid
                + COLUMN_INFO + " nvarchar(1024)" // info
                + ") ";
    }

    public long addNewDownloaded(String url, String postData, String dir, String fileName, int size, long date, String uid, String info) throws SQLException {
        ContentValues values = new ContentValuesFactory()
                .put(COLUMN_URL, url)
                .put(COLUMN_POSTDATA, postData)
                .put(COLUMN_DIR, dir)
                .put(COLUMN_FILENAME, fileName)
                .put(COLUMN_SIZE, size)
                .put(COLUMN_DATE, date)
                .put(COLUMN_UID, uid)
                .put(COLUMN_INFO, info == null ? "" : info).getValues();
        SQLiteDatabase db = this.getWritableDatabase();
        return db.insertOrThrow(TABLE_DOWNLOADED, null, values);
    }

    public long addNewDownloadingBlock(String url, String postData, String dir, String fileName,
                                       int fileSize, int block, String uid) {
        return addNewDownloadingBlock(url, postData, dir, fileName, fileSize, block, 1, uid);
    }

    public long addNewDownloadingBlock(String url, String postData, String dir, String fileName,
                                       int fileSize, int block, int threadId, String uid) {
        ContentValues values = new ContentValuesFactory()
                .put(COLUMN_URL, url)
                .put(COLUMN_POSTDATA, postData)
                .put(COLUMN_DIR, dir)
                .put(COLUMN_FILENAME, fileName)
                .put(COLUMN_FILESIZE, fileSize)
                .put(COLUMN_BLOCKSIZE, block)
                .put(COLUMN_THREADID, threadId)
                .put(COLUMN_THREADPOS, block * (threadId - 1))
                .put(COLUMN_UID, uid).getValues();
        return getWritableDatabase().insertOrThrow(TABLE_DOWNLOADING, null, values);
    }

    public int renameDownloaded(String dir, String fileName, String newFileName) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_FILENAME, newFileName);

        return db.update(TABLE_DOWNLOADED, values,
                COLUMN_DIR + "=? and " + COLUMN_FILENAME + "=?",
                new String[] { dir, fileName });
    }

    public void deleteDownloaded(String dir, String fileName) {
        getWritableDatabase().delete(TABLE_DOWNLOADED,
                COLUMN_DIR + "=? and " + COLUMN_FILENAME + "=?",
                new String[] { dir, fileName });
    }

    /**
     * @param uid download url
     * @return last download position for all threads
     */
    public Map<Integer, Integer> getData(String uid) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(DownloaderDatabase.TABLE_DOWNLOADING,
                new String[] { DownloaderDatabase.COLUMN_THREADID,
                        DownloaderDatabase.COLUMN_THREADPOS },
                DownloaderDatabase.COLUMN_UID + "=?", new String[] { uid },
                null, null, null, null);
        Map<Integer, Integer> data = new HashMap<Integer, Integer>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            data.put(cursor.getInt(0), cursor.getInt(1));
            cursor.moveToNext();
        }
        cursor.close();
        return data;
    }

    private void updateByUid(String tableName, String uid, ContentValues values) {
        getWritableDatabase().update(tableName, values, COLUMN_UID + "=?", new String[] { uid });
    }

    public void resetFileSize(String uid, int fileSize) {
        updateByUid(TABLE_DOWNLOADING, uid,
                new ContentValuesFactory().put(DownloaderDatabase.COLUMN_FILESIZE, fileSize).getValues());
    }

    public void resetFileName(String uid, String dir, String fileName) {
        updateByUid(TABLE_DOWNLOADING, uid, new ContentValuesFactory()
                .put(DownloaderDatabase.COLUMN_DIR, dir)
                .put(DownloaderDatabase.COLUMN_FILENAME, fileName)
                .getValues());
    }

    public void resetBlock(String uid, int blocksize) {
        updateByUid(TABLE_DOWNLOADING, uid, new ContentValuesFactory()
                .put(DownloaderDatabase.COLUMN_BLOCKSIZE, blocksize)
                .getValues());
    }

    private static final String THREAD_SELECTION =
            DownloaderDatabase.COLUMN_UID + "=? and " +
                    DownloaderDatabase.COLUMN_THREADID + "=?";

    public void resetThreadIdAndStartPos(String uid,
                                         int oldThreadId,
                                         int newThreadId,
                                         int startPos) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValuesFactory()
                .put(DownloaderDatabase.COLUMN_THREADPOS, startPos)
                .put(DownloaderDatabase.COLUMN_THREADID, newThreadId)
                .getValues();
        db.update(TABLE_DOWNLOADING,
                values, THREAD_SELECTION,
                new String[] { uid, String.valueOf(oldThreadId) });
    }

    public void updatePos(String uid, int threadId, int startPos) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues values = new ContentValuesFactory().put(
                COLUMN_THREADPOS, startPos).
                getValues();
        db.update(TABLE_DOWNLOADING,
                values,
                THREAD_SELECTION,
                new String[] { uid, String.valueOf(threadId) });
    }

    private static final String UID_SELECTION =
            DownloaderDatabase.COLUMN_UID + "=?";

    public void updateInfo(String uid, String info) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues values = new ContentValuesFactory()
                .put(DownloaderDatabase.COLUMN_INFO, info)
                .getValues();
        db.update(TABLE_DOWNLOADING,
                values,
                UID_SELECTION,
                new String[] { uid });
    }

    /**
     * Delete a download
     *
     * @param uid download uid
     */
    public void deleteDownloading(String uid) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete(TABLE_DOWNLOADING, UID_SELECTION, new String[] { uid });
    }

    public Cursor getDownloadingList() {
        return getReadableDatabase().query(TABLE_DOWNLOADING,
                null, null, null, null, null, null);
    }

    public Cursor getDownloadedList() {
        return getReadableDatabase().query(TABLE_DOWNLOADED,
                null, null, null, null, null, null);
    }
}

