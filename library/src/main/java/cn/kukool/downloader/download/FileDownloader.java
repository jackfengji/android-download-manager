package cn.kukool.downloader.download;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import cn.kukool.downloader.Config;
import cn.kukool.downloader.util.Log;
import cn.kukool.downloader.util.Util;

public class FileDownloader implements DownloadThread.IDownloadThreadListener {
    private static final String TAG = "FileDownloader";
    private static final MediaType FORM = MediaType.parse("application/x-www-form-urlencoded; charset=utf-8");

    public static class Header {
        public static final String AUTHORIZATION = "Authorization";
        public static final String LOCATION = "Location";
        public static final String CONTENT_ENCODING = "Content-Encoding";
        public static final String CONTENT_LENGTH = "Content-Length";
        public static final String CONTENT_RANGE = "Content-Range";
        public static final String TRANSFER_ENCODING = "Transfer-Encoding";
        public static final String CONTENT_TYPE = "Content-Type";
        public static final String CONTENT_DISPOSITION = "Content-Disposition";
        public static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
        public static final String LAST_MODIFIED = "Last-Modified";
        public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
        public static final String ETAG = "Etag";
        public static final String IF_NONE_MATCH = "If-None-Match";
        public static final String RANGE = "Range";
        public static final String ACCEPTRANGE = "Accept-Ranges";
    }

    public final static String LOG_START = "ST";
    public final static String LOG_PAUSE = "PE";
    public final static String LOG_RESUME = "RE";
    public final static String LOG_ERROR = "ER";
    public final static String LOG_SUCCESS = "SS";
    public final static String LOG_DELETE = "DE";

    // the destination file is not found (this should not happen)
    public static final int ERROR_CODE_FILE_NOT_FOUND = 1000;
    // there's error when opening destination file
    public static final int ERROR_CODE_FILE_OPEN_ERROR = 1001;
    // there's error when getting base infomation about download like file size, file name, can range download
    public static final int ERROR_CODE_GET_BASE_INFO_ERROR = 1002;
    // there's error in full downloading, we will try three times for downloading
    public static final int ERROR_CODE_FULL_DOWNLOAD_FAIL = 1003;
    // there's error in range downloading, we will try three times for downloading
    // ( this may be sent multiple times for one download, you need to handle it).
    public static final int ERROR_CODE_RANGE_DOWNLOAD_FAIL = 1004;

    private int mStatus = DownloadStack.STATUS_WAITING;
    private int mErrorCode = -1;
    private DownloaderDatabase mDownloaderDb;

    /* downloaded size */
    private int mDownloadSize = 0;

    /**
     * real file size
     * 0: file size not gotten yet
     * -1: error when getting file size
     */
    private int mFileSize = 0;

    /* download threads */
    private DownloadThread[] mDownloadThreads;

    /* local target file */
    private File mSaveFile;

    /* last download position for all threads */
    private Map<Integer, Integer> mThreadStartPosMap =
            new ConcurrentHashMap<Integer, Integer>();

    /**
     * block size of each thread
     * 0: not get block; -1: single thread download
     */
    private int mThreadBlockSize;

    private boolean mRequestStop = false;
    private String mDownloadUid;
    private String mDownloadUrl;
    private String mPostData;
    private String mFileName;
    private String mFileSaveDir;

    private boolean mCanRangeDownload;
    private long mDownloadedDate;
    private int mMaxThreadId;

    private Context mContext;
    private OkHttpClient mHttpClient;

    private String mDownloadInfo = "";

    private IFileDownloaderListener mFileDownloaderListener;

    public interface IFileDownloaderListener {
        void onGetBaseInfo(FileDownloader downloader);

        void onDownloadProgress(FileDownloader downloader);

        void onDownloadFinish(FileDownloader downloader);

        void onError(FileDownloader downloader, int errorCode);
    }

    private void init() {
        mHttpClient = new OkHttpClient();
        mHttpClient.setFollowRedirects(true);
        mHttpClient.setFollowSslRedirects(false);
        mHttpClient.setRetryOnConnectionFailure(true);
        mHttpClient.setConnectTimeout(Config.CONNECT_TIMEOUT, TimeUnit.SECONDS);
        mHttpClient.setReadTimeout(Config.READ_TIMEOUT, TimeUnit.SECONDS);
        mHttpClient.getDispatcher().setMaxRequests(Config.MAX_THREAD_CNT);
    }

    public FileDownloader(Context context,
                          String downloadUrl, String postData, String uid,
                          String fileSaveDir, String fileName,
                          int fileSize, int blockSize, String info) {
        mContext = context;
        init();

        mDownloadUrl = downloadUrl;
        mPostData = postData;
        mFileSize = fileSize;
        mDownloadUid = uid;
        mDownloaderDb = DownloaderDatabase.getInstance(context);
        mThreadBlockSize = blockSize;
        mCanRangeDownload = (blockSize != -1);
        mFileSaveDir = fileSaveDir;
        mFileName = fileName;
        mDownloadInfo = info == null ? "" : info;
        ensureFileSaveDir();

        if (fileName != null && fileName.length() > 0) {
            mSaveFile = new File(fileSaveDir, fileName);
        }

        if (mThreadBlockSize != 0) { //block size has been initialized
            Map<Integer, Integer> logdata = mDownloaderDb.getData(uid);
            if (logdata.size() > 0) {
                mThreadStartPosMap.putAll(logdata);
            }

            int threadNum = logdata.size();
            this.mDownloadThreads = new DownloadThread[threadNum];
            if (threadNum > 0) {
                Set<Integer> keys = mThreadStartPosMap.keySet();
                mMaxThreadId = 1;
                for (int threadId : keys) {
                    if (threadId > mMaxThreadId)
                        mMaxThreadId = threadId;
                    mDownloadSize += this.mThreadStartPosMap.get(threadId) - (this.mThreadBlockSize * (threadId - 1));
                }

                if (!mCanRangeDownload) {
                    mDownloadSize = 0; //download from begin
                } else {
                    int finishedThreadCnt = mMaxThreadId - keys.size();
                    if (finishedThreadCnt > 0) {
                        mDownloadSize += finishedThreadCnt * mThreadBlockSize;
                    } else if (finishedThreadCnt < 0) {
                        // log("download record error! ");
                    }
                }
            }
        }

        mStatus = DownloadStack.STATUS_STOP;
    }

    public FileDownloader(Context context,
                          String downloadUrl, String postData, String uid,
                          String fileSaveDir, String fileName) {
        mContext = context;
        init();

        mDownloadUrl = downloadUrl;
        mPostData = postData;
        mDownloadUid = uid;
        mDownloaderDb = DownloaderDatabase.getInstance(context);

        mFileSaveDir = fileSaveDir;
        mStatus = DownloadStack.STATUS_WAITING;
        mFileName = fileName == null ? "" : fileName;
        mContext = context;
        updateDlInfo(LOG_START);
        ensureFileSaveDir();
    }

    private String getCurrentFormatDateTime() {
        SimpleDateFormat df = new SimpleDateFormat("yyyyMMddHHmmss");//设置日期格式
        String dateTime = df.format(new Date());
        return dateTime;
    }

    public String getCurrentNetwork() {
        if (mContext == null) {
            return "none";
        }
        ConnectivityManager connectionManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectionManager.getActiveNetworkInfo();
        String str = "none";
        if (networkInfo != null) {
            str = networkInfo.getTypeName();
            if (str == null) {
                str = "none";
            }
        }
        return str;
    }

    public void updateDlInfo(String state) {
        String temp = state + ":" + mDownloadSize + ":" + getCurrentFormatDateTime() + ":" + getCurrentNetwork();
        Log.debug(TAG, "updateDlInfo dlinfo=" + mDownloadInfo + ",temp=" + temp);

        if (mDownloadInfo.length() + temp.length() > 1024) {
            Log.debug(TAG, "updateDlInfo, too long, not add!!");
            return;
        }

        if (mDownloadInfo.length() == 0) {
            mDownloadInfo = temp;
        } else {
            mDownloadInfo += ";" + temp;
        }

        if (mDownloaderDb != null) {
            mDownloaderDb.updateInfo(mDownloadUid, mDownloadInfo);
        }
    }

    public void stopAndDelete(boolean withFile) {
        Log.debug(TAG, "stop and delete downloader " + mDownloadUid);

        mRequestStop = true;
        this.mStatus = DownloadStack.STATUS_STOP;
        updateDlInfo(LOG_DELETE);
        mDownloaderDb.deleteDownloading(mDownloadUid);
        mDownloaderDb.deleteDownloaded(mFileSaveDir, mFileName);
        if (withFile) {
            if (mSaveFile != null && mSaveFile.exists()) {
                mSaveFile.delete();
            }
        }
    }

    public boolean stopDownload() {
        if (mStatus == DownloadStack.STATUS_STOP) {
            return false;
        }

        mRequestStop = true;
        mStatus = DownloadStack.STATUS_STOP;
        updateDlInfo(LOG_PAUSE);
        return true;
    }

    public void createRecord() {
        mDownloaderDb.addNewDownloadingBlock(this.mDownloadUrl, this.mPostData, this.mFileSaveDir, this.mFileName, 0, 0, mDownloadUid);
    }

    public void setFilePathAndName(String path, String name) {
        this.mFileSaveDir = path;
        this.mFileName = name;

        ensureFileSaveDir();
    }

    private void ensureFileSaveDir() {
        if (this.mFileSaveDir != null) {
            File dir = new File(this.mFileSaveDir);
            if (dir.exists() && dir.isFile()) {
                dir.delete();
            }

            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
    }

    // call when really need download
    public boolean continueDownload(IFileDownloaderListener listener) {
        if (mStatus == DownloadStack.STATUS_LOADING)
            return false;

        mFileDownloaderListener = listener;
        mRequestStop = false;
        setStatus(DownloadStack.STATUS_LOADING);
        updateDlInfo(LOG_RESUME);

        new Thread(new Runnable() {
            @Override
            public void run() {
                //status maybe changed
                if (mStatus != DownloadStack.STATUS_LOADING) {
                    return;
                }

                if (mRequestStop) {
                    setStatus(DownloadStack.STATUS_STOP);
                    return;
                }

                if (mFileSize == 0 || mFileName == null || mFileName.length() == 0) { //not get file size
                    if (getNetFileBaseInfo()) {
                        initParams();
                        initFileServer();
                        download(mFileDownloaderListener);
                    } else {
                        setStatus(DownloadStack.STATUS_ERROR);
                        updateDlInfo(LOG_ERROR);
                        mFileDownloaderListener.onError(FileDownloader.this, ERROR_CODE_GET_BASE_INFO_ERROR);
                    }
                } else if (mThreadBlockSize == 0) { // not get block
                    initParams();
                    initFileServer();
                    if (!mCanRangeDownload) {
                        mDownloadSize = 0; // reset download size if can't range download
                    }

                    download(mFileDownloaderListener);
                } else {
                    if (!mCanRangeDownload) {
                        mDownloadSize = 0; // reset download size if can't range download
                    }

                    download(mFileDownloaderListener);
                }

                if (mStatus != DownloadStack.STATUS_ERROR) {
                    setStatus(DownloadStack.STATUS_STOP);
                }
            }
        }, "FileDownloader #" + mDownloadUid).start();

        return true;
    }

    Request.Builder createRequestBuilder() {
        Request.Builder requestBuilder = new Request.Builder();
        requestBuilder.url(mDownloadUrl);
        if (mPostData != null) {
            requestBuilder.post(RequestBody.create(FORM, mPostData));
        }
        requestBuilder.header("User-Agent", Config.USER_AGENT);
        requestBuilder.header("Accept-Encoding", "identity"); // range download doesn't support gzip
        return requestBuilder;
    }

    public boolean getNetFileBaseInfo() {
        Request request = createRequestBuilder().header(Header.RANGE, "bytes=0-10").build();
        Log.debug(TAG, "before getNetFileBaseInfo: mRequestStop=" + mRequestStop + ", mFileName =" + mFileName);

        if (mRequestStop) {
            return false;
        }

        Call call = null;
        Response response = null;
        int i;

        for (i = 0; i < Config.MAX_THREAD_RETRY; ++i) {
            call = mHttpClient.newCall(request);

            try {
                response = call.execute();

                if (response.code() / 100 != 2) {
                    Log.error(TAG, "getNetFileBaseInfo: doRequest failure: " + response.code());
                } else {
                    break;
                }
            } catch (IOException e) {
                Log.error(TAG, "getNetFileBaseInfo: doRequest failure", e);
            }

            call.cancel();

            try {
                Thread.sleep((i + 1) * 2000);
            } catch (InterruptedException ignore) {
            }
        }

        if (i == Config.MAX_THREAD_RETRY || response == null) {
            return false;
        }

        // determine file name
        if (mFileName == null || mFileName.length() == 0) {
            setFileName(response);
        }

        String headValue;
        int statusCode = response.code();

        // fileSize
        mFileSize = -1;
        try {
            if (statusCode == 206) {
                headValue = response.header(Header.CONTENT_RANGE);
                if (headValue != null) {
                    int n = headValue.indexOf('/');
                    if (n >= 0 && n < (headValue.length() - 1)) {
                        mFileSize = Integer.parseInt(headValue.substring(n + 1));
                    }
                }
            } else {
                headValue = response.header(Header.CONTENT_TRANSFER_ENCODING);
                if (headValue == null || !headValue.contains("chunked")) {
                    headValue = response.header(Header.CONTENT_LENGTH);
                    if (headValue != null) {
                        mFileSize = Integer.parseInt(headValue);
                    }
                }
            }
        } catch (Exception e) {
            // we still don't need to call the onError
            mFileSize = -1;
            e.printStackTrace();
        }

        Log.debug(TAG, "getNetFileBaseInfo: get File Size = " + mFileSize);

        // judge if can range download
        mCanRangeDownload = false;

        if (this.mFileSize > 0) {
            headValue = response.header(Header.ACCEPTRANGE);

            if (headValue == null && statusCode == 206) {
                headValue = response.header(Header.CONTENT_RANGE);
            }

            if (headValue != null) {
                mCanRangeDownload = headValue.toLowerCase().contains("bytes");
            }
        }

        mDownloaderDb.resetFileSize(mDownloadUid, this.mFileSize);

        call.cancel();
        Log.debug(TAG, "after getNetFileBaseInfo mFileName=" + mFileName);

        if (mFileDownloaderListener != null) {
            mFileDownloaderListener.onGetBaseInfo(this);
        }

        return !mRequestStop;
    }

    private void setFileName(Response response) {
        //determine file name
        String headValue = response.header(Header.CONTENT_DISPOSITION);
        String dispositionName = null;
        boolean bNeedBase64Decode = false;
        boolean bNeedUtf8Decode = false;
        if (headValue != null) {
            int pos = headValue.indexOf("filename=");
            // Sohu MailBox
            // Content-Disposition:attachment;filename=\"=?utf8?q?10676462=5F01a7d2438d=2Ejpg?=\"
            int posutf8 = headValue.indexOf("filename=\"=?utf8?");
            // WAP Hotmail MailBox
            // Content-Disposition:attachment; filename*=utf8'"aaa.txt"
            int poshotmail = headValue.indexOf("filename*=utf8\'\"");

            if (posutf8 >= 0) {
                bNeedBase64Decode = true;
                bNeedUtf8Decode = true;
                pos = posutf8;

                String temp = headValue.substring(pos + "filename=\"=?utf8?".length() + 2);
                int pos2 = temp.indexOf("?=\"");
                if (pos2 >= 0) {
                    dispositionName = temp.substring(0, pos2);
                }
            } else if (poshotmail >= 0) {
                bNeedUtf8Decode = true;
                pos = poshotmail;

                String temp = headValue.substring(pos + "filename*=utf8\'\"".length());
                int pos2 = temp.indexOf("\"");
                if (pos2 >= 0) {
                    dispositionName = temp.substring(0, pos2);
                }
            } else if (pos >= 0) {
                int quotePos = headValue.indexOf("\"");
                if (quotePos > 0) {
                    dispositionName = headValue.substring(quotePos + 1, headValue.length() - 1);
                } else {
                    dispositionName = headValue.substring(pos + 9);
                }
            }

            mFileName = decodeFileName(dispositionName, bNeedBase64Decode, bNeedUtf8Decode);
        }

        if (mFileName == null || mFileName.length() == 0) {
            mFileName = getFileNameFromUrl(response.request().url().toString());
        }

        mFileName = checkRepeatFileName(mFileName, mFileSaveDir);
        Log.debug(TAG, "setFileName mFileName=" + mFileName);
    }

    private String checkRepeatFileName(String name, String dirPath) {
        File file = new File(dirPath, name);
        int dotPos = name.lastIndexOf('.');
        String preName = name;
        String sufName = "";
        if (dotPos > 0) {
            preName = name.substring(0, dotPos);
            sufName = name.substring(dotPos);
        }

        String tempName = name;
        while (file.exists()) {
            tempName = preName + System.currentTimeMillis() + sufName;
            file = new File(dirPath, tempName);
        }

        return tempName;
    }

    private String decodeFileName(String name, boolean bNeedBase64Decode, boolean bNeedUtf8Decode) {

        if (name == null || name.length() == 0)
            return name;

        name = name.trim();
        if (!bNeedBase64Decode && !bNeedUtf8Decode) {
            if (Util.isGB2312(name)) {
                try {
                    name = new String(name.getBytes("ISO-8859-1"), "GBK");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return name;
        }

        String encode = "UTF-8";
        try {
            if (bNeedBase64Decode) {
                encode = "GBK";
            }
            name = new String(name.getBytes("ISO-8859-1"), encode);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return name;
    }

    private void initParams() {
        if (!mCanRangeDownload) {
            mThreadBlockSize = -1;
            mDownloadThreads = new DownloadThread[1];
        } else {
            int threadCnt = Config.MAX_THREAD_CNT;
            int blockSize = (mFileSize + threadCnt - 1) / threadCnt;

            if (blockSize > Config.MAX_BLOCK_SIZE) {
                blockSize = Config.MAX_BLOCK_SIZE;
            }

            mThreadBlockSize = blockSize;
            mDownloadThreads = new DownloadThread[threadCnt];
        }

        //init start pos
        if (this.mThreadStartPosMap.size() != this.mDownloadThreads.length) {
            this.mThreadStartPosMap.clear();
            for (int i = 0; i < this.mDownloadThreads.length; i++) {
                this.mThreadStartPosMap.put(i + 1, this.mThreadBlockSize * i);
            }
        }
        mMaxThreadId = mDownloadThreads.length;
    }

    private void initFileServer() {
        this.mSaveFile = new File(mFileSaveDir, mFileName);
        RandomAccessFile randOut = null;
        try {
            randOut = new RandomAccessFile(this.mSaveFile, "rw");
            if (this.mFileSize > 0)
                randOut.setLength(this.mFileSize);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            setStatus(DownloadStack.STATUS_ERROR);
            if (mFileDownloaderListener != null)
                mFileDownloaderListener.onError(this, ERROR_CODE_FILE_NOT_FOUND);
        } catch (IOException e) {
            e.printStackTrace();
            setStatus(DownloadStack.STATUS_ERROR);
            if (mFileDownloaderListener != null)
                mFileDownloaderListener.onError(this, ERROR_CODE_FILE_OPEN_ERROR);
        } finally {
            if (randOut != null) {
                try {
                    randOut.close();
                } catch (IOException ignore) {
                }
            }
        }

        mDownloaderDb.resetFileName(mDownloadUid, this.mFileSaveDir, this.mFileName);
        mDownloaderDb.resetBlock(mDownloadUid, this.mThreadBlockSize);
        for (int i = 0; i < this.mDownloadThreads.length; i++) {
            mDownloaderDb.addNewDownloadingBlock(this.mDownloadUrl, this.mPostData,
                    this.mFileSaveDir, this.mFileName, this.mFileSize,
                    mThreadBlockSize, i + 1, mDownloadUid);
        }
    }

    private boolean notEnoughThread;

    private int download(IFileDownloaderListener listener) {
        notEnoughThread = true;
        if (this.mCanRangeDownload) {
            notEnoughThread = mThreadBlockSize * mMaxThreadId < mFileSize;
            Set<Integer> keys = mThreadStartPosMap.keySet();
            // copy to local, can NOT direct use keys to find threadid,
            // because keys maybe changed in function replaceFinishThread
            Integer[] threadIds = new Integer[keys.size()];
            threadIds = keys.toArray(threadIds);
            for (int i = 0; i < mDownloadThreads.length; i++) {
                int threadId = threadIds[i];
                int downLength = this.mThreadStartPosMap.get(threadId) -
                        (this.mThreadBlockSize * (threadId - 1));
                if (downLength < this.mThreadBlockSize) {
                    this.mDownloadThreads[i] = new DownloadThread(this, mHttpClient, mSaveFile,
                            this.mThreadBlockSize, this.mThreadStartPosMap.get(threadId), threadId,
                            mFileSize, this);
                    this.mDownloadThreads[i].start();
                } else if (notEnoughThread) {
                    notEnoughThread = replaceFinishThread(i, threadId);
                } else {
                    mDownloadThreads[i] = null;
                }
            }
        } else {
            mDownloadThreads[0] = new DownloadThread(this, mHttpClient, mSaveFile,
                    mThreadBlockSize, 0, 1, mFileSize, this);
            mDownloadThreads[0].start();
            notEnoughThread = false;
        }

        int lastDownloadSize = mDownloadSize;
        boolean notFinish = true;
        while (notFinish && !mRequestStop) {
            Log.debug(TAG, "in while ==== downloadSpeed=" + (mDownloadSize - lastDownloadSize) + ", downloadSIZE=" + mDownloadSize);
            lastDownloadSize = mDownloadSize;

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignore) {
            }

            notFinish = false; // assume download has finished

            for (DownloadThread thread : this.mDownloadThreads) {
                if (mRequestStop) {
                    break;
                }

                if (thread == null) {
                    continue;
                }

                if (!thread.isFinish() || notEnoughThread) {
                    notFinish = true;
                }
            }

            if (listener != null) {
                listener.onDownloadProgress(this);
            }
        }

        if (mRequestStop) {
            Log.debug(TAG, "mRequestStop=" + mRequestStop);

            for (DownloadThread thread : this.mDownloadThreads) {
                if (thread != null) {
                    thread.requestStop();
                }
            }

            if (mStatus == DownloadStack.STATUS_ERROR && mFileDownloaderListener != null) {
                mFileDownloaderListener.onError(this, mErrorCode);
            }
        } else if (!notFinish) { // finished
            Log.debug(TAG, "onfinish filesize:" + mFileSize + " mDownloadSize:" + mDownloadSize);

            if (mFileSize <= 0) {
                mFileSize = mDownloadSize;
            }

            updateDlInfo(LOG_SUCCESS);
            mDownloaderDb.deleteDownloading(mDownloadUid);
            mDownloadedDate = System.currentTimeMillis();
            mDownloaderDb.addNewDownloaded(this.mDownloadUrl, this.mPostData,
                    this.mFileSaveDir, this.mFileName, this.mFileSize,
                    this.mDownloadedDate, mDownloadUid, this.mDownloadInfo);

            if (listener != null) {
                listener.onDownloadFinish(this);
            }
        }

        return this.mDownloadSize;
    }

    private synchronized boolean replaceFinishThread(int threadPos, int oldThreadId) {
        mThreadStartPosMap.remove(oldThreadId);

        int newPos = mThreadBlockSize * mMaxThreadId;
        int newThreadId = mMaxThreadId + 1;
        mDownloaderDb.resetThreadIdAndStartPos(mDownloadUid, oldThreadId, newThreadId, newPos);
        mThreadStartPosMap.put(newThreadId, newPos);

        this.mDownloadThreads[threadPos] = new DownloadThread(this, mHttpClient, mSaveFile,
                this.mThreadBlockSize, newPos, newThreadId, mFileSize, this);

        mMaxThreadId = newThreadId;
        if (!mRequestStop) {
            mDownloadThreads[threadPos].start();
        }

        Log.debug(TAG, "mMaxThreadId:" + mMaxThreadId + " return:" + (mThreadBlockSize * mMaxThreadId < mFileSize));
        return mThreadBlockSize * mMaxThreadId < mFileSize;
    }

    private synchronized boolean replaceFinishThread(DownloadThread thread) {
        Log.debug(TAG, "replaceFinishThread threadId: " + thread.getThreadId() + " thread id =" + thread.getThreadId() + " enter");

        for (int i = 0; i < mDownloadThreads.length; i++) {
            if (mDownloadThreads[i] == thread) {
                return replaceFinishThread(i, thread.getThreadId());
            }
        }

        Log.debug(TAG, "replaceFinishThread threadId: " + thread.getThreadId() + " thread id =" + thread.getThreadId() + " return false");
        return false;
    }

    public void setStatus(int status) {
        mStatus = status;
    }

    public int getStatus() {
        return mStatus;
    }

    public int getFileSize() {
        return mFileSize;

    }

    public int getDownloadedSize() {
        return mDownloadSize;
    }

    protected synchronized void append(int size) {
        mDownloadSize += size;
    }

    public String getFileDir() {
        return mFileSaveDir;
    }

    public String getFileName() {
        return mFileName;
    }

    public void renameFile(String newFileName) {
        mFileName = newFileName;
    }

    public long getDownloadedDate() {
        return mDownloadedDate;
    }

    public void setDownloadedDate(long downloadedDate) {
        mDownloadedDate = downloadedDate;
    }

    protected synchronized void updateLogFile(int threadId, int pos) {
        mThreadStartPosMap.put(threadId, pos);
        mDownloaderDb.updatePos(mDownloadUid, threadId, pos);
    }

    public void getFastName() {
        this.mFileName = getFileNameFromUrl(mDownloadUrl);
    }

    public static String getFileNameFromUrl(String strLocation/*, String dirPath*/) {
        // decode unascii code
        Log.debug(TAG, "before decode name == " + strLocation);
        strLocation = URLDecoder.decode(strLocation);
        //log("decode name == "+strLocation);

        String name = "";
        int npos = 0;
        int nlen = strLocation.length();
        int nhttp = strLocation.indexOf("http://");
        if (nhttp == 0)// start with http://
        {
            name = strLocation.substring(7);
            nlen = name.length();
            npos = name.lastIndexOf('/');
        } else {
            name = strLocation;
            npos = strLocation.lastIndexOf('/');
        }
        int npos2 = name.lastIndexOf('\\');
        if (npos2 >= 0) {
            if (npos2 > npos) {
                npos = npos2;
            }
        }

        if (-1 == npos || (nlen - 1) == npos) {
            name = "index.html";
        } else {
            name = name.substring(npos + 1);
            int querypos = name.indexOf('?');
            if (querypos > 0) {
                name = name.substring(0, querypos);
            }
            int argspos = name.indexOf(';');
            if (argspos > 0) {
                name = name.substring(0, argspos);
            }
            // ***************************
            int andpos = name.indexOf('&');
            if (andpos > 0 && andpos < name.length() - 1) {
                name = name.substring(andpos + 1);
            }
            int equalpos = name.indexOf('#');
            if (equalpos > 0 && equalpos < name.length() - 1) {
                name = name.substring(equalpos + 1);
            }
        }

        if (name == null || name.length() == 0 || name.indexOf(".") < 1) {
            name = "index.html";
        }

        return name;

    }

    @Override
    public void onError(DownloadThread thread, int errorCode) {
        mRequestStop = true;
        setStatus(DownloadStack.STATUS_ERROR);
        mErrorCode = errorCode;
        updateDlInfo(LOG_ERROR);
    }

    @Override
    public void onFinish(DownloadThread thread) {
        Log.debug(TAG, "download thread finish threadId: " + thread.getThreadId() + " thread id: " + thread.getThreadId());

        if (notEnoughThread) {
            notEnoughThread = replaceFinishThread(thread);
        }
    }

    @Override
    public boolean equals(Object o) {
        return o != null && o instanceof FileDownloader && mDownloadUid.equals(((FileDownloader) o).mDownloadUid);
    }

    @Override
    public int hashCode() {
        return mDownloadUid.hashCode();
    }

    public String getDownloadUid() {
        return mDownloadUid;
    }

    public String getDownloadUrl() {
        return this.mDownloadUrl;
    }
}