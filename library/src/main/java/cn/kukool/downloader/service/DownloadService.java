package cn.kukool.downloader.service;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import cn.kukool.downloader.aidl.IDownloadListener;
import cn.kukool.downloader.aidl.IDownloader;
import cn.kukool.downloader.download.DownloadStack;
import cn.kukool.downloader.util.NetworkUtil;


public class DownloadService extends Service implements DownloadStack.IDownloadListener {
    private static final String TAG = "DownloadService";

    private DownloadStack mDownloadStack;
    private IDownloadListener mDownloadListener;

    @Override
    public void onCreate() {
        super.onCreate();

        mDownloadStack = new DownloadStack(this);
        mDownloadStack.setDownloadListener(this);
    }

    @Override
    public void onDownloadTaskAdd(String uid) {
        if (mDownloadListener != null) {
            try {
                mDownloadListener.onDownloadTaskAdd(uid);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDownloadTaskReadd(String uid, float progress) {
        if (mDownloadListener != null) {
            try {
                mDownloadListener.onDownloadTaskReadd(uid, progress);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDownloadTaskStart(String uid, String filepath, int filesize) {
        if (mDownloadListener != null) {
            try {
                mDownloadListener.onDownloadTaskStart(uid, filepath, filesize);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDownloadTaskBaseInfo(String uid, String filepath, int filesize) {
        if (mDownloadListener != null) {
            try {
                mDownloadListener.onDownloadTaskBaseInfo(uid, filepath, filesize);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDownloadTaskPause(String uid, float progress) {
        if (mDownloadListener != null) {
            try {
                mDownloadListener.onDownloadTaskPause(uid, progress);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDownloadTaskProgress(String uid, float progress) {
        if (mDownloadListener != null) {
            try {
                mDownloadListener.onDownloadTaskProgress(uid, progress);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDownloadTaskFinish(String uid, String filePath) {
        if (mDownloadListener != null) {
            try {
                mDownloadListener.onDownloadTaskFinish(uid, filePath);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDownloadTaskError(String uid, int errorCode) {
        if (mDownloadListener != null) {
            try {
                mDownloadListener.onDownloadTaskError(uid, errorCode);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onDownloadTaskRemove(String uid) {
        if (mDownloadListener != null) {
            try {
                mDownloadListener.onDownloadTaskRemove(uid);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    private class DownloadBinder extends IDownloader.Stub {
        @Override
        public boolean startDownload(String uid, String url, String postData, String path) throws RemoteException {
            return mDownloadStack.startDownload(uid, url, postData, path);
        }

        @Override
        public boolean pauseDownload(String uid) throws RemoteException {
            return mDownloadStack.pauseDownload(uid);
        }

        @Override
        public boolean resumeDownload(String uid) throws RemoteException {
            return mDownloadStack.resumeDownload(uid);
        }

        @Override
        public boolean removeDownload(String uid, boolean withFile) throws RemoteException {
            return mDownloadStack.removeDownload(uid, withFile);
        }

        @Override
        public int getDownloadItemStatus(String uid) throws RemoteException {
            return mDownloadStack.getDownloadItemStatus(uid);
        }

        @Override
        public Bundle getDownloadItemInfo(String uid) throws RemoteException {
            return mDownloadStack.getDownloadItemInfo(uid);
        }

        @Override
        public Bundle[] getDownloadList() throws RemoteException {
            return mDownloadStack.getDownloadList();
        }

        @Override
        public void setDownloadListener(IDownloadListener listener) throws RemoteException {
            mDownloadListener = listener;
        }
    }

    private DownloadBinder mBinder = null;

    @Override
    public IBinder onBind(Intent t) {
        if (mBinder == null) {
            mBinder = new DownloadBinder();
        }

        NetworkUtil.registerNetworkCheck(this);
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        NetworkUtil.unRegisterNetworkCheck(this);
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
    }
}
