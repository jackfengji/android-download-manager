package cn.kukool.downloader.download;

import android.os.Handler;
import android.os.Message;

import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.ref.WeakReference;

import cn.kukool.downloader.Config;
import cn.kukool.downloader.util.Log;

public class DownloadThread implements Callback {
    private static final String TAG = "DownloadThread";


    private RandomAccessFile mSaveFile;
    private int mBlockSize;

    private int mThreadId = -1;
    private int mStartPosition;
    private int mDownloadLength;
    private int mTotalSize;
    private int mThreadDownloadSize = -1;
    private boolean mFinish = false;
    private FileDownloader mFileDownloader;
    private OkHttpClient mHttpClient;
    private DownloadHandler mHandler;
    private Call mCall;
    private int mRetryCount;
    private boolean mRequestStop;
    private IDownloadThreadListener mListener;

    public interface IDownloadThreadListener {
        void onFinish(DownloadThread thread);

        void onError(DownloadThread thread, int errorCode);
    }

    private static final int MSG_WAIT = 0;
    private static final int MSG_FINISH = 1;
    private static final int MSG_ERROR = 2;

    static class DownloadHandler extends Handler {
        private WeakReference<DownloadThread> mThread;

        public DownloadHandler(DownloadThread thread) {
            super(DownloadStack.getLooper());

            mThread = new WeakReference<>(thread);
        }

        @Override
        public void handleMessage(Message msg) {
            final DownloadThread thread = mThread.get();

            if (thread == null) return ;

            switch (msg.what) {
                case MSG_WAIT:
                    thread.download();
                    break;

                case MSG_FINISH:
                    thread.mListener.onFinish(thread);

                    try {
                        thread.mSaveFile.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    break;

                case MSG_ERROR:
                    thread.mListener.onError(thread, msg.arg1);
                    break;
            }
        }
    }

    public DownloadThread(FileDownloader downloader,
                          OkHttpClient httpClient,
                          File saveFile, int blockSize, int startPos,
                          int threadId, int totalSize, IDownloadThreadListener listener) {
        mFileDownloader = downloader;
        mHttpClient = httpClient;
        mHandler = new DownloadHandler(this);

        mBlockSize = blockSize;
        mStartPosition = startPos;
        mThreadId = threadId;

        mDownloadLength = startPos - (blockSize * (threadId - 1));
        mListener = listener;
        mTotalSize = totalSize;

        if (blockSize != -1) {
            //range download
            if (blockSize * threadId > totalSize) {
                mThreadDownloadSize = totalSize - startPos;
            } else {
                mThreadDownloadSize = blockSize;
            }

            Log.debug(TAG, "blockSize = " + blockSize +
                    ", startPosition=" + startPos +
                    ", threadDownloadSize=" + mThreadDownloadSize +
                    ", threadId=" + threadId +
                    ", totalSize=" + totalSize);
        }

        if (listener == null) {
            throw new NullPointerException("mListener should never be null in download thread");
        }

        try {
            this.mSaveFile = new RandomAccessFile(saveFile, "rw");
        } catch (FileNotFoundException e) {
            Log.error(TAG, "cannot create save file in download thread", e);
            listener.onError(this, FileDownloader.ERROR_CODE_FILE_NOT_FOUND);
        }

        Log.debug(TAG, "new DownloadThread mThreadId: " + threadId + " thread id: " + getThreadId());
    }

    public void requestStop() {
        mRequestStop = true;

        mHandler.removeMessages(MSG_WAIT);
        mHandler.removeMessages(MSG_ERROR);
        mHandler.removeMessages(MSG_FINISH);

        if (mCall != null) {
            mCall.cancel();
            mCall = null;
        }
    }

    public int getThreadId() {
        return mThreadId;
    }

    public void start() {
        Log.assertLog(mCall == null || mCall.isCanceled(), TAG, "Start a download thread when it's already in downloading");

        mRetryCount = 0;
        mDownloadLength = 0;

        download();
    }

    private void download() {
        if (mRetryCount > Config.MAX_THREAD_RETRY) {
            mDownloadLength = -1;
            mHandler.obtainMessage(MSG_ERROR, FileDownloader.ERROR_CODE_FULL_DOWNLOAD_FAIL, 0).sendToTarget();
            return;
        }

        if (mRequestStop) {
            Log.debug(TAG, "request stop so no download continue");
            return ;
        }

        if (mBlockSize == -1) {
            fullDownload();
        } else if (mDownloadLength < mBlockSize) {
            rangeDownload();
        }
    }

    private void retryOrFail() {
        if (!mRequestStop) {
            ++mRetryCount;
            Log.debug(TAG, "retryOrFail: " + mRetryCount);
            mHandler.sendEmptyMessageDelayed(MSG_WAIT, mRetryCount * 2000);
        }
    }

    public boolean isFinish() {
        return mFinish;
    }

    @Override
    public void onFailure(Request request, IOException e) {
        Log.error(TAG, "okhttp callback failure", e);
        retryOrFail();
    }

    @Override
    public void onResponse(Response response) throws IOException {
        final ResponseBody body = response.body();

        try {
            if (response.code() / 100 != 2) {
                body.close();
                Log.debug(TAG, "onResponse returned non 200 code: " + response.code());

                retryOrFail();
            } else {
                byte[] buffer = new byte[1024];
                int offset;
                while ((offset = body.byteStream().read(buffer, 0, 1024)) != -1 && !mRequestStop) {
                    mSaveFile.write(buffer, 0, offset);
                    mDownloadLength += offset;
                    mStartPosition += offset;
                    mFileDownloader.append(offset);
                }

                if (mBlockSize != -1) {
                    mFileDownloader.updateLogFile(this.mThreadId, mBlockSize * (mThreadId - 1) + mDownloadLength);
                }

                Log.debug(TAG, "offset: " + offset +
                        ", downloadLength: " + mDownloadLength +
                        ", threadDownloadSize: " + mThreadDownloadSize);

                if (offset == -1 && (mThreadDownloadSize <= 0 || mDownloadLength == mThreadDownloadSize)) {
                    Log.debug(TAG, "download finish threadId: " + mThreadId);
                    mFinish = true;
                    mHandler.obtainMessage(MSG_FINISH).sendToTarget();
                } else {
                    retryOrFail();
                }
            }
        } catch (IOException e) {
            retryOrFail();
            throw e;
        } finally {
            body.close();
        }
    }

    private void fullDownload() {
        try {
            mSaveFile.seek(0);
        } catch (IOException e) {
            e.printStackTrace();
            retryOrFail();
            return ;
        }

        mStartPosition = 0;
        mDownloadLength = 0;

        Request.Builder requestBuilder = mFileDownloader.createRequestBuilder();
        mCall = mHttpClient.newCall(requestBuilder.build());
        mCall.enqueue(this);
    }

    private void rangeDownload() {
        int endPos = Math.min(mTotalSize - 1, mThreadId * mBlockSize - 1);

        if (mStartPosition < endPos) {
            try {
                mSaveFile.seek(mStartPosition);
            } catch (IOException e) {
                e.printStackTrace();
                retryOrFail();
                return ;
            }

            Log.debug(TAG, "startPosition=" + mStartPosition + ", endPos=" + endPos + ", totalSize=" + mTotalSize);

            Request.Builder requestBuilder = mFileDownloader.createRequestBuilder();
            requestBuilder.header("Range", "bytes=" + this.mStartPosition + "-" + endPos);
            mCall = mHttpClient.newCall(requestBuilder.build());
            mCall.enqueue(this);
        } else {
            Log.debug(TAG, "range download finish because start position bigger than end position threadId: " + mThreadId);
            mFinish = true;
            mHandler.obtainMessage(MSG_FINISH).sendToTarget();
        }
    }
}