package cn.kukool.downloader.download;

import android.content.Context;
import android.database.Cursor;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import cn.kukool.downloader.Config;
import cn.kukool.downloader.R;
import cn.kukool.downloader.util.Log;

public class DownloadHelper implements FileDownloader.IFileDownloaderListener {
    public static final String TAG = DownloadHelper.class.getName();

    private Context mContext;
    private IDownloadHelperListener mDownloadListener;

    private final ConcurrentMap<String, FileDownloader> mDownloadingList = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, FileDownloader> mDownloadedList = new ConcurrentHashMap<>();

    public interface IDownloadHelperListener {
        // called when task added
        void onDownloadTaskAdd(DownloadHelper downloadHelper,
                               FileDownloader fileDownloader);

        // called when task is readded after reboot
        void onDownloadTaskReadd(DownloadHelper downloadHelper,
                                 FileDownloader fileDownloader);

        // called when task starts first time or resumed
        void onDownloadTaskStart(DownloadHelper downloadHelper,
                                 FileDownloader fileDownloader);

        // call when we get the base info like filename and filesize for download
        void onDownloadGetBaseInfo(DownloadHelper downloadHelper,
                                   FileDownloader fileDownloader);

        // called when task paused
        void onDownloadTaskPause(DownloadHelper downloadHelper,
                                 FileDownloader fileDownloader);

        // called when task progress changed
        void onDownloadTaskProgress(DownloadHelper downloadHelper,
                                    FileDownloader fileDownloader);

        // called when task finish
        void onDownloadTaskFinish(DownloadHelper downloadHelper,
                                  FileDownloader fileDownloader);

        // called when task has errors
        void onDownloadTaskError(DownloadHelper downloadHelper,
                                 FileDownloader fileDownloader, int errorCode);

        // called when task has remove
        void onDownloadTaskRemove(DownloadHelper downloadHelper,
                                  FileDownloader fileDownloader, int errorCode);
    }

    public DownloadHelper(Context context) {
        Log.debug(TAG, "DownloadHelper  start....");
        mContext = context;
        assert context != null;

        DownloaderDatabase db = DownloaderDatabase.getInstance(context);
        Cursor cursor = db.getDownloadingList();

        if (cursor != null && cursor.getCount() > 0) {
            cursor.moveToFirst();
            int urlIndex = cursor.getColumnIndex(DownloaderDatabase.COLUMN_URL);
            int postDataIndex = cursor.getColumnIndex(DownloaderDatabase.COLUMN_POSTDATA);
            int pathIndex = cursor.getColumnIndex(DownloaderDatabase.COLUMN_DIR);
            int fileNameIndex = cursor.getColumnIndex(DownloaderDatabase.COLUMN_FILENAME);
            int sizeIndex = cursor.getColumnIndex(DownloaderDatabase.COLUMN_FILESIZE);
            int blockIndex = cursor.getColumnIndex(DownloaderDatabase.COLUMN_BLOCKSIZE);
            int uidIndex = cursor.getColumnIndex(DownloaderDatabase.COLUMN_UID);
            int infoIndex = cursor.getColumnIndex(DownloaderDatabase.COLUMN_INFO);
            String lastUid = null;
            while (!cursor.isAfterLast()) {
                String url = cursor.getString(urlIndex);
                String postData = cursor.getString(postDataIndex);
                String filePath = cursor.getString(pathIndex);
                String fileName = cursor.getString(fileNameIndex);
                String uid = cursor.getString(uidIndex);
                int fileSize = cursor.getInt(sizeIndex);
                int block = cursor.getInt(blockIndex);
                String info = cursor.getString(infoIndex);
                if (lastUid == null || !lastUid.equals(uid)) {
                    FileDownloader downer = new FileDownloader(context,
                            url, postData, uid,
                            filePath, fileName, fileSize,
                            block, info);
                    mDownloadingList.put(uid, downer);
                    onDownloadTaskReadd(this, downer);
                    lastUid = uid;
                }
                cursor.moveToNext();
            }
        }

        Log.debug(TAG, "DownloadHelper  cursor.getCount() = " + (cursor == null ? "null" : cursor.getCount()));

        if (cursor != null) {
            cursor.close();
        }

        cursor = db.getDownloadedList();
        if (cursor != null && cursor.getCount() > 0) {
            int urlIndex = cursor.getColumnIndexOrThrow(DownloaderDatabase.COLUMN_URL);
            int postDataIndex = cursor.getColumnIndexOrThrow(DownloaderDatabase.COLUMN_POSTDATA);
            int dirIndex = cursor.getColumnIndexOrThrow(DownloaderDatabase.COLUMN_DIR);
            int fileNameIndex = cursor.getColumnIndexOrThrow(DownloaderDatabase.COLUMN_FILENAME);
            int fileSizeIndex = cursor.getColumnIndexOrThrow(DownloaderDatabase.COLUMN_SIZE);
            int dateIndex = cursor.getColumnIndexOrThrow(DownloaderDatabase.COLUMN_DATE);
            int uidIndex = cursor.getColumnIndexOrThrow(DownloaderDatabase.COLUMN_UID);
            int infoIndex = cursor.getColumnIndexOrThrow(DownloaderDatabase.COLUMN_INFO);

            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                FileDownloader downer = new FileDownloader(context,
                        cursor.getString(urlIndex), // url
                        cursor.getString(postDataIndex), // post data
                        cursor.getString(uidIndex), // uid
                        cursor.getString(dirIndex), // file dir
                        cursor.getString(fileNameIndex), // file name
                        cursor.getInt(fileSizeIndex), // file szie
                        0, // block size
                        cursor.getString(infoIndex)); // info
                downer.setDownloadedDate(cursor.getLong(dateIndex));
                mDownloadedList.put(cursor.getString(uidIndex), downer);
                cursor.moveToNext();
            }
        }

        if (cursor != null) {
            cursor.close();
        }

        db.close();
    }

    public void setDownloadHelperListener(IDownloadHelperListener listener) {
        mDownloadListener = listener;
    }

    public FileDownloader getDownloading(String uid) {
        return mDownloadingList.get(uid);
    }

    public FileDownloader getDownloaded(String uid) {
        FileDownloader downloader = mDownloadedList.get(uid);
        if (downloader != null) {
            File file = new File(downloader.getFileDir() + "/" + downloader.getFileName());
            if (file.exists() && file.isFile())
                return downloader;
            else {
                delDownloader(uid, false);
                mDownloadedList.remove(uid);
            }
        }

        return null;
    }

    public boolean stopDownloader(String uid) {
        FileDownloader downloader = getDownloading(uid);
        if (downloader != null) {
            boolean flag = downloader.stopDownload();
            onDownloadTaskPause(this, downloader);
            Log.debug(TAG, "stopDownloader");
            downloadNext();
            return flag;
        }

        return false;
    }

    public void stopAllTask() {
        for (FileDownloader loader : mDownloadingList.values()) {
            loader.stopDownload();
            onDownloadTaskPause(this, loader);
        }
    }

    public synchronized void startAllTask() {
        for (FileDownloader downloader : mDownloadingList.values()) {
            continueDownloader(downloader.getDownloadUid());
        }
    }

    public boolean continueDownloader(String uid) {
        FileDownloader downloader = getDownloading(uid);
        if (downloader != null) {
            if (downloader.getStatus() != DownloadStack.STATUS_LOADING) {
                downloader.setStatus(DownloadStack.STATUS_WAITING);
            }
            downloadNext();
            return true;
//            return downloader.continueDownload(DownloadHelper.this);
        }

        return false;
    }

    public boolean delDownloader(String uid, boolean withFile) {
        if (uid == null || uid.length() == 0)
            return false;
        FileDownloader downloader = mDownloadingList.get(uid);
        if (downloader != null) {
            mDownloadingList.remove(uid);
            downloader.stopAndDelete(withFile);
            onDownloadTaskRemove(this, downloader, 1);
            downloadNext();
            return true;
        }

        downloader = mDownloadedList.get(uid);
        if (downloader != null) {
            mDownloadedList.remove(uid);
            DownloaderDatabase db = DownloaderDatabase.getInstance(mContext);
            String path = downloader.getFileDir();
            String fileName = downloader.getFileName();
            db.deleteDownloaded(path, fileName);
            db.close();
            if (withFile) {
                try {
                    File file = new File(path, fileName);
                    file.delete();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return true;
        }

        return false;
    }

    public FileDownloader startNewDownload(String path, String url, String postData, String fileName, String uid) {
        Log.debug(TAG, "add new download url = " + url + " filename = " + fileName);
        if (url == null || uid == null) {
            Message msg = Message.obtain();
            msg.what = TOAST_DOWNLOAD_OTHER;
            msg.obj = "download url error";
            msg.arg1 = R.string.download_url_error;
            mToastHandler.sendMessage(msg);
            return null;
        }

        if (taskExist(uid)) {
            Message msg = Message.obtain();
            msg.what = TOAST_DOWNLOAD_OTHER;
            msg.obj = "download task repeat";
            msg.arg1 = R.string.download_task_repeat;
            mToastHandler.sendMessage(msg);
            return null;
        }

        FileDownloader newLoader = getDownloadBaseInfo(mContext, url, postData, uid, "", null);
        newLoader.setFilePathAndName(path, fileName);
        addTaskToList(newLoader, true);
        return newLoader;
    }

    private boolean taskExist(String uid) {
        return mDownloadingList.get(uid) != null;// || mDownloadedList.get(uid) != null;
    }


    //
    public static String getSdcardPath() {
        return Environment.getExternalStorageDirectory().getAbsolutePath();
    }

    //
    public static boolean externalMemoryAvailable() {
        return android.os.Environment.getExternalStorageState().equals(
                android.os.Environment.MEDIA_MOUNTED);
    }

    private static final int ERROR = -1;

    public static long getAvailableExternalMemorySize(String path) {
        if (externalMemoryAvailable()) {
            StatFs stat = new StatFs(path);
            long blockSize = stat.getBlockSize();
            long availableBlocks = stat.getAvailableBlocks();
            return availableBlocks * blockSize;
        } else {
            return ERROR;
        }
    }

    static int ramMB = 5 * 1024 * 1024;

    public static boolean isSDCardHasMoreMemery(int size) {
        if (size < ramMB) {
            size = ramMB;
        }
        if (externalMemoryAvailable()) {
            return getAvailableExternalMemorySize(getSdcardPath()) > size;
        } else {
            return false;
        }
    }

    private void addTaskToList(FileDownloader loader, boolean showAddToast) {
        loader.createRecord();
        mDownloadingList.put(loader.getDownloadUid(), loader);
        onDownloadTaskAdd(this, loader);

        if (showAddToast) {
            Message msg = Message.obtain();
            msg.what = TOAST_DOWNLOAD_OTHER;
            msg.obj = "task add ok";
            msg.arg1 = R.string.download_task_add_ok;
            mToastHandler.sendMessage(msg);
        }

        downloadNext();
    }

    private FileDownloader getDownloadBaseInfo(Context context,
                                               String url, String postData, String uid,
                                               String path, String fileName) {
        FileDownloader downloader = new FileDownloader(context, url, postData, uid, path, fileName);

        try {
            downloader.getFastName();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return downloader;
    }

    private int getRunningTaskCnt() {
        int count = 0;
        for (FileDownloader loader : mDownloadingList.values()) {
            if (loader.getStatus() == DownloadStack.STATUS_LOADING) {
                count++;
            }
        }
        return count;
    }

    public ConcurrentMap<String, FileDownloader> getDownloadingList() {
        Log.debug(TAG, "DownloadingList size =" + mDownloadingList.size());
        return mDownloadingList;
    }

    public synchronized void downloadNext() {
        for (FileDownloader loader : mDownloadingList.values()) {
            if (loader.getStatus() == DownloadStack.STATUS_WAITING &&
                    getRunningTaskCnt() < Config.MAX_DOWNLOADS) {
                Log.debug(TAG, "downloadNext  start....");
                loader.continueDownload(this);
                onDownloadTaskStart(this, loader);
            }
        }
    }

    private static final int TOAST_DOWNLOAD_FINISH = 1;
    private static final int TOAST_DOWNLOAD_FILE_ERROR = 2;
    private static final int TOAST_DOWNLOAD_ARG_ERROR = 3;
    private static final int TOAST_DOWNLOAD_OTHER = 4;
    private static final int TOAST_DOWNLOAD_PROGRESS = 5;
    private static final int TOAST_DOWNLOAD_REMOVE = 6;
    private static final int TOAST_DOWNLOAD_BASE_INFO = 7;

    private Handler mToastHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case TOAST_DOWNLOAD_BASE_INFO:
                    Log.debug(TAG, "toast download task base info");
                    onDownloadGetBaseInfo(DownloadHelper.this, (FileDownloader) msg.obj);
                    break;

                case TOAST_DOWNLOAD_FINISH:
                    FileDownloader downer = (FileDownloader) msg.obj;
                    mDownloadingList.remove(downer.getDownloadUid());
                    mDownloadedList.put(downer.getDownloadUid(), downer);
                    Log.debug(TAG, "toast download task finished, remaining " + mDownloadingList.size() + " tasks");

                    onDownloadTaskFinish(DownloadHelper.this, downer);
                    downloadNext();
                    break;

                case TOAST_DOWNLOAD_FILE_ERROR:
//                    Toast.makeText(mContext, R.string.download_task_file_error, Toast.LENGTH_SHORT).show();
                    Log.debug(TAG, "toast download task file error, code: " + msg.arg1);
                    onDownloadTaskError(DownloadHelper.this, (FileDownloader) msg.obj, msg.arg1);
                    downloadNext();
                    break;

                case TOAST_DOWNLOAD_ARG_ERROR:
//                    Toast.makeText(mContext, R.string.download_task_arg_error, Toast.LENGTH_SHORT).show();
                    Log.debug(TAG, "toast download task arg error");
                    break;

                case TOAST_DOWNLOAD_OTHER:
//                    Toast.makeText(mContext, msg.arg1, Toast.LENGTH_SHORT).show();
                    Log.debug(TAG, "++++++toast " + msg.arg1);
                    Log.debug(TAG, "++++++toast" + msg.obj);
                    break;

                case TOAST_DOWNLOAD_PROGRESS:
                    onDownloadTaskProgress(DownloadHelper.this, (FileDownloader) msg.obj);
                    break;

                case TOAST_DOWNLOAD_REMOVE:
                    Log.debug(TAG, "toast download task remove: " + msg.arg1);
                    onDownloadTaskRemove(DownloadHelper.this, (FileDownloader) msg.obj, msg.arg1);
                    break;
            }
        }
    };

    @Override
    public void onGetBaseInfo(FileDownloader downloader) {
        mToastHandler.obtainMessage(TOAST_DOWNLOAD_BASE_INFO, downloader).sendToTarget();
    }

    @Override
    public void onDownloadProgress(FileDownloader downloader) {
        mToastHandler.obtainMessage(TOAST_DOWNLOAD_PROGRESS, downloader).sendToTarget();
    }

    @Override
    public void onDownloadFinish(FileDownloader downloader) {
        mToastHandler.obtainMessage(TOAST_DOWNLOAD_FINISH, downloader).sendToTarget();
    }

    @Override
    public void onError(FileDownloader downloader, int errorCode) {
        mToastHandler.obtainMessage(TOAST_DOWNLOAD_FILE_ERROR, errorCode, 0, downloader).sendToTarget();
    }

    public final void onDownloadTaskAdd(DownloadHelper downloadHelper,
                                        FileDownloader fileDownloader) {
        if (mDownloadListener != null)
            mDownloadListener.onDownloadTaskAdd(downloadHelper, fileDownloader);
    }

    public final void onDownloadTaskReadd(DownloadHelper downloadHelper,
                                          FileDownloader fileDownloader) {
        if (mDownloadListener != null)
            mDownloadListener.onDownloadTaskReadd(downloadHelper, fileDownloader);
    }

    public final void onDownloadTaskStart(DownloadHelper downloadHelper,
                                          FileDownloader fileDownloader) {
        if (mDownloadListener != null)
            mDownloadListener.onDownloadTaskStart(downloadHelper, fileDownloader);
    }

    public final void onDownloadGetBaseInfo(DownloadHelper downloadHelper,
                                            FileDownloader fileDownloader) {
        if (mDownloadListener != null) {
            mDownloadListener.onDownloadGetBaseInfo(downloadHelper, fileDownloader);
        }
    }

    public final void onDownloadTaskPause(DownloadHelper downloadHelper,
                                          FileDownloader fileDownloader) {
        if (mDownloadListener != null)
            mDownloadListener.onDownloadTaskPause(downloadHelper, fileDownloader);
    }

    public final void onDownloadTaskProgress(DownloadHelper downloadHelper,
                                             FileDownloader fileDownloader) {
        if (mDownloadListener != null)
            mDownloadListener.onDownloadTaskProgress(downloadHelper, fileDownloader);
    }

    public final void onDownloadTaskFinish(DownloadHelper downloadHelper,
                                           FileDownloader fileDownloader) {
        if (mDownloadListener != null)
            mDownloadListener.onDownloadTaskFinish(downloadHelper, fileDownloader);
    }

    public final void onDownloadTaskError(DownloadHelper downloadHelper,
                                          FileDownloader fileDownloader, int errorCode) {
        if (mDownloadListener != null)
            mDownloadListener.onDownloadTaskError(downloadHelper, fileDownloader, errorCode);

        delDownloader(fileDownloader.getDownloadUid(), true);
    }

    public final void onDownloadTaskRemove(DownloadHelper downloadHelper,
                                           FileDownloader fileDownloader, int retCode) {
        if (mDownloadListener != null)
            mDownloadListener.onDownloadTaskRemove(downloadHelper, fileDownloader, retCode);
    }
}
