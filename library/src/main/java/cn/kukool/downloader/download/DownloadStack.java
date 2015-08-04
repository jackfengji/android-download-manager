package cn.kukool.downloader.download;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

import cn.kukool.downloader.Config;
import cn.kukool.downloader.util.Log;
import cn.kukool.downloader.util.Util;

/**
 * User: jackfengji
 * Date: 15/6/25
 */
public class DownloadStack {
    private static final String TAG = "DownloadStack";

    private static final float MIN_SEND_PERCENTAGE = 10; // the minimum percentage between sending broadcast

    public static final int STATUS_NOT_EXIST = -1;
    public static final int STATUS_DOWNLOADED = 0;
    public static final int STATUS_LOADING = 1;
    public static final int STATUS_WAITING = 2;
    public static final int STATUS_STOP = 3;
    public static final int STATUS_ERROR = 4;

    /**
     * timestamp of this intent
     */
    public static final String EXTRA_TIMESTAMP = "timestamp";

    /**
     * uid of the download
     */
    public static final String EXTRA_UID = "uid";

    /**
     * percentage as float
     */
    public static final String EXTRA_PROGRESS = "progress";

    /**
     * status as defined in getDownloadItemStatus
     */
    public static final String EXTRA_STATUS = "status";

    /**
     * the file path for the downloaded item
     */
    public static final String EXTRA_FILE_PATH = "path";

    /**
     * error code
     */
    public static final String EXTRA_ERROR_CODE = "errorCode";

    /**
     * <p>broadcast sent when a task is added.</p>
     * <p/>
     * <p>
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_TIMESTAMP},
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_UID} and
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_PROGRESS}
     * are sent.
     * </p>
     */
    public static final String BROADCAST_ACTION_ADD = "cn.kukool.downloader.task.add";

    /**
     * <p>broadcast sent when a task is readded.</p>
     * <p/>
     * <p>
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_TIMESTAMP},
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_UID},
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_STATUS} and
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_PROGRESS}
     * are sent.
     * </p>
     */
    public static final String BROADCAST_ACTION_READD = "cn.kukool.downloader.task.readd";

    /**
     * <p>broadcast sent when a task is started.</p>
     * <p/>
     * <p>
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_TIMESTAMP},
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_UID} and
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_PROGRESS}
     * are sent.
     * </p>
     */
    public static final String BROADCAST_ACTION_START = "cn.kukool.downloader.task.start";

    /**
     * <p>broadcast sent when the base info (file name, file size) is retrieved.</p>
     * <p/>
     * <p>
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_TIMESTAMP},
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_UID} and
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_PROGRESS}
     * are sent.
     * </p>
     */
    public static final String BROADCAST_ACTION_BASE_INFO = "cn.kukool.downloader.task.get_base_info";

    /**
     * <p>broadcast sent when a task is paused.</p>
     * <p/>
     * <p>
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_TIMESTAMP},
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_UID} and
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_PROGRESS}
     * are sent.
     * </p>
     */
    public static final String BROADCAST_ACTION_PAUSE = "cn.kukool.downloader.task.pause";

    /**
     * <p>broadcast sent for task's progress. This is only sent when significant progress has been made.
     * By default, it's sent every 10%. You can change this value in {@link DownloadStack#MIN_SEND_PERCENTAGE}</p>
     * <p/>
     * <p>
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_TIMESTAMP},
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_UID},
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_STATUS} and
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_PROGRESS}
     * are sent.
     * </p>
     */
    public static final String BROADCAST_ACTION_PROGRESS = "cn.kukool.downloader.task.progress";

    /**
     * <p>broadcast sent when a task is finished.</p>
     * <p/>
     * <p>
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_TIMESTAMP},
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_UID} and
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_FILE_PATH}
     * are sent.
     * </p>
     */
    public static final String BROADCAST_ACTION_FINISH = "cn.kukool.downloader.task.finish";

    /**
     * <p>broadcast sent when a task is failed.</p>
     * <p/>
     * <p>
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_TIMESTAMP},
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_UID} and
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_ERROR_CODE}
     * are sent.
     * </p>
     */
    public static final String BROADCAST_ACTION_ERROR = "cn.kukool.downloader.task.error";

    /**
     * <p>broadcast sent when a task is removed.</p>
     * <p/>
     * <p>
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_TIMESTAMP},
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_UID} and
     * {@link cn.kukool.downloader.download.DownloadStack#EXTRA_ERROR_CODE}
     * are sent.
     * </p>
     */
    public static final String BROADCAST_ACTION_REMOVE = "cn.kukool.downloader.task.remove";

    @SuppressWarnings("FieldCanBeLocal")
    private Context mContext;

    private DownloadHelper mHelper;
    private IDownloadListener mDownloadListener;

    @SuppressWarnings("FieldCanBeLocal")
    private ConcurrentHashMap<String, Float> mLastProgress = new ConcurrentHashMap<>();

    private static Looper sLooper;

    public DownloadStack(Context context) {
        mContext = context;
        mHelper = new DownloadHelper(context.getApplicationContext());
        mHelper.setDownloadHelperListener(new DownloadHelper.IDownloadHelperListener() {

            @Override
            public void onDownloadTaskAdd(DownloadHelper downloadHelper,
                                          FileDownloader downloader) {
                if (Config.ENABLE_BROADCAST) {
                    Intent intent = new Intent(BROADCAST_ACTION_ADD);
                    intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
                    intent.putExtra(EXTRA_UID, downloader.getDownloadUid());
                    intent.putExtra(EXTRA_PROGRESS, getDownloadProgress(downloader));
                    Log.debug(TAG, "------> send bcast add time: " + System.currentTimeMillis() +
                            " uid: " + downloader.getDownloadUid());
                    mContext.sendBroadcast(intent);
                }

                if (mDownloadListener != null) {
                    mDownloadListener.onDownloadTaskAdd(downloader.getDownloadUid());
                }
            }

            @Override
            public void onDownloadTaskReadd(DownloadHelper downloadHelper,
                                            FileDownloader downloader) {
                if (Config.ENABLE_BROADCAST) {
                    Intent intent = new Intent(BROADCAST_ACTION_READD);
                    intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
                    String uid = downloader.getDownloadUid();
                    intent.putExtra(EXTRA_UID, uid);
                    intent.putExtra(EXTRA_STATUS, getDownloadItemStatus(uid));
                    intent.putExtra(EXTRA_PROGRESS, getDownloadProgress(downloader));
                    Log.debug(TAG, "------> send bcast readd time: " + System.currentTimeMillis() +
                            " uid: " + downloader.getDownloadUid());
                    mContext.sendBroadcast(intent);
                }

                if (mDownloadListener != null) {
                    mDownloadListener.onDownloadTaskReadd(
                            downloader.getDownloadUid(),
                            getDownloadProgress(downloader));
                }
            }

            @Override
            public void onDownloadTaskStart(DownloadHelper downloadHelper,
                                            FileDownloader downloader) {
                if (Config.ENABLE_BROADCAST) {
                    Intent intent = new Intent(BROADCAST_ACTION_START);
                    intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
                    intent.putExtra(EXTRA_UID, downloader.getDownloadUid());
                    intent.putExtra(EXTRA_PROGRESS, getDownloadProgress(downloader));
                    Log.debug(TAG, "------> send bcast start time: " + System.currentTimeMillis() +
                            " uid: " + downloader.getDownloadUid());
                    mContext.sendBroadcast(intent);
                }

                if (mDownloadListener != null) {
                    mDownloadListener.onDownloadTaskStart(
                            downloader.getDownloadUid(),
                            downloader.getFileDir() + File.separator + downloader.getFileName(),
                            downloader.getFileSize());
                }
            }

            @Override
            public void onDownloadGetBaseInfo(DownloadHelper helper,
                                              FileDownloader downloader) {
                if (Config.ENABLE_BROADCAST) {
                    Intent intent = new Intent(BROADCAST_ACTION_BASE_INFO);
                    intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
                    intent.putExtra(EXTRA_UID, downloader.getDownloadUid());
                    intent.putExtra(EXTRA_PROGRESS, getDownloadProgress(downloader));
                    Log.debug(TAG, "------> send bcast get base info time: " + System.currentTimeMillis() +
                            " uid: " + downloader.getDownloadUid());
                    mContext.sendBroadcast(intent);
                }

                if (mDownloadListener != null) {
                    mDownloadListener.onDownloadTaskBaseInfo(
                            downloader.getDownloadUid(),
                            downloader.getFileDir() + File.separator + downloader.getFileName(),
                            downloader.getFileSize());
                }
            }

            @Override
            public void onDownloadTaskPause(DownloadHelper downloadHelper,
                                            FileDownloader downloader) {
                if (Config.ENABLE_BROADCAST) {
                    Intent intent = new Intent(BROADCAST_ACTION_PAUSE);
                    intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
                    String uid = downloader.getDownloadUid();
                    intent.putExtra(EXTRA_UID, uid);
                    intent.putExtra(EXTRA_PROGRESS, getDownloadProgress(downloader));
                    Log.debug(TAG, "------> send bcast pause time: " + System.currentTimeMillis() +
                            " uid: " + downloader.getDownloadUid());
                    mContext.sendBroadcast(intent);
                }

                if (mDownloadListener != null) {
                    mDownloadListener.onDownloadTaskPause(
                            downloader.getDownloadUid(),
                            getDownloadProgress(downloader));
                }
            }

            @Override
            public void onDownloadTaskProgress(DownloadHelper downloadHelper,
                                               FileDownloader downloader) {
                if (Config.ENABLE_BROADCAST) {
                    Intent intent = new Intent(BROADCAST_ACTION_PROGRESS);
                    intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
                    float progress = downloader.getFileSize() > 0 ?
                            downloader.getDownloadedSize() / (float) downloader.getFileSize() * 100.0f : 0.0f;
                    String uid = downloader.getDownloadUid();
                    if ((!mLastProgress.containsKey(uid)) || (progress - mLastProgress.get(uid) > MIN_SEND_PERCENTAGE)) {
                        intent.putExtra(EXTRA_UID, uid);
                        intent.putExtra(EXTRA_STATUS, getDownloadItemStatus(uid));
                        intent.putExtra(EXTRA_PROGRESS, progress);
                        mLastProgress.put(uid, progress);
                        Log.debug(TAG, "------> send bcast progress time: " + System.currentTimeMillis() +
                                " uid: " + downloader.getDownloadUid());
                        mContext.sendBroadcast(intent);
                    }
                }

                if (mDownloadListener != null) {
                    mDownloadListener.onDownloadTaskProgress(
                            downloader.getDownloadUid(),
                            getDownloadProgress(downloader));
                }
            }

            @Override
            public void onDownloadTaskFinish(DownloadHelper downloadHelper,
                                             FileDownloader downloader) {
                if (Config.ENABLE_BROADCAST) {
                    Intent intent = new Intent(BROADCAST_ACTION_FINISH);
                    intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
                    String uid = downloader.getDownloadUid();
                    intent.putExtra(EXTRA_UID, uid);
                    intent.putExtra(EXTRA_FILE_PATH, downloader.getFileDir() +
                            File.separator + downloader.getFileName());
                    Log.debug(TAG, "------> send bcast finish time: " + System.currentTimeMillis() +
                            " uid: " + downloader.getDownloadUid());
                    mContext.sendBroadcast(intent);
                    mLastProgress.remove(uid);
                }

                if (mDownloadListener != null) {
                    mDownloadListener.onDownloadTaskFinish(
                            downloader.getDownloadUid(),
                            downloader.getFileDir() +
                                    File.separator +
                                    downloader.getFileName());
                }
            }

            @Override
            public void onDownloadTaskError(DownloadHelper downloadHelper,
                                            FileDownloader downloader,
                                            int errorCode) {
                if (Config.ENABLE_BROADCAST) {
                    Intent intent = new Intent(BROADCAST_ACTION_ERROR);
                    intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
                    String uid = downloader.getDownloadUid();
                    intent.putExtra(EXTRA_UID, uid);
                    intent.putExtra(EXTRA_ERROR_CODE, errorCode);
                    Log.debug(TAG, "------> send bcast error time: " + System.currentTimeMillis() +
                            " uid: " + downloader.getDownloadUid() +
                            " error code: " + errorCode);
                    mContext.sendBroadcast(intent);
                    mLastProgress.remove(uid);
                }

                if (mDownloadListener != null) {
                    mDownloadListener.onDownloadTaskError(
                            downloader.getDownloadUid(),
                            errorCode);
                }
            }

            @Override
            public void onDownloadTaskRemove(DownloadHelper downloadHelper,
                                             FileDownloader downloader, int retCode) {
                if (Config.ENABLE_BROADCAST) {
                    Intent intent = new Intent(BROADCAST_ACTION_REMOVE);
                    intent.putExtra(EXTRA_TIMESTAMP, System.currentTimeMillis());
                    String uid = downloader.getDownloadUid();
                    intent.putExtra(EXTRA_UID, uid);
                    intent.putExtra(EXTRA_ERROR_CODE, retCode);
                    Log.debug(TAG, "------> send bcast remove time: " + System.currentTimeMillis() +
                            " uid: " + downloader.getDownloadUid() +
                            " error code: " + retCode);
                    mContext.sendBroadcast(intent);
                    mLastProgress.remove(uid);
                }

                if (mDownloadListener != null) {
                    mDownloadListener.onDownloadTaskRemove(downloader.getDownloadUid());
                }
            }
        });
        DownloaderDatabase.getInstance(context.getApplicationContext());

        if (sLooper == null) {
            final HandlerThread downloadThread = new HandlerThread("DownloadStack", Thread.MIN_PRIORITY);
            downloadThread.start();
            sLooper = downloadThread.getLooper();
        }
    }

    public static Looper getLooper() {
        return sLooper;
    }

    private float getDownloadProgress(FileDownloader downloader) {
        float size = downloader.getFileSize();
        return size > 0 ? downloader.getDownloadedSize() / size : 0.0f;
    }

    /**
     * Start download
     *
     * @param uid      unique id of this download
     * @param url      url to download
     * @param postData post data or null if it's a get request
     * @param filePath file path to store the downloaded file. If path is ended with a '/', then this is the folder of downloaded file and file name is determined by download response.
     * @return the download is added or not. It could fail because uid already exists (failed or in downloading). Or the file path specified could not be written.
     */
    @CheckResult
    public boolean startDownload(@NonNull String uid,
                                 @NonNull String url,
                                 @Nullable String postData,
                                 @Nullable String filePath) {
        Log.debug(TAG, "startDownload: uid=" + uid + ", url=" + url + ", path=" + filePath);

        String dir;
        String fileName;

        if (filePath != null) {
            int index = filePath.lastIndexOf("/");
            if (index == -1)
                return false;
            fileName = filePath.substring(index + 1);
            dir = filePath.substring(0, index);
        } else {
            dir = Config.DEFAULT_DOWNLOAD_DIR;
            fileName = "";
            if (!Util.isSdcardOK() || !Util.checkSdcard()) {
                return false;
            }
        }

        return mHelper.startNewDownload(dir, url, postData, fileName, uid) != null;
    }

    /**
     * Pause the download. You can resume it by calling {@link DownloadStack#resumeDownload(String)} later.
     *
     * @param uid uid you used in {@link DownloadStack#startDownload(String, String, String, String)}
     * @return false if the download doesn't exist or it has finished or failed
     */
    public boolean pauseDownload(@NonNull String uid) {
        return mHelper.stopDownloader(uid);
    }

    /**
     * Resume the download which is paused before.
     *
     * @param uid uid you used in {@link DownloadStack#startDownload(String, String, String, String)}
     * @return false if the download doesn't exist or it has finished or failed
     */
    public boolean resumeDownload(@NonNull String uid) {
        return mHelper.continueDownloader(uid);
    }

    /**
     * Remove the download.
     *
     * @param uid      uid you used in {@link DownloadStack#startDownload(String, String, String, String)}
     * @param withFile remove the downloaded file or not
     * @return false if the download doesn't exist
     */
    public boolean removeDownload(@NonNull String uid, boolean withFile) {
        return mHelper.delDownloader(uid, withFile);
    }

    /**
     * Get download status.
     *
     * @param uid uid you used in {@link DownloadStack#startDownload(String, String, String, String)}
     * @return one of {@link DownloadStack#STATUS_NOT_EXIST}, {@link DownloadStack#STATUS_DOWNLOADED},
     * {@link DownloadStack#STATUS_WAITING}, {@link DownloadStack#STATUS_LOADING},
     * {@link DownloadStack#STATUS_STOP}, {@link DownloadStack#STATUS_ERROR}
     */
    public int getDownloadItemStatus(@NonNull String uid) {
        FileDownloader downloader = mHelper.getDownloading(uid);
        if (downloader != null) {
            return downloader.getStatus();
        }

        downloader = mHelper.getDownloaded(uid);
        return downloader != null ? STATUS_DOWNLOADED : STATUS_NOT_EXIST;
    }

    private boolean isDownloading(@NonNull FileDownloader downloader) {
        int status = downloader.getStatus();
        return status == DownloadStack.STATUS_LOADING ||
                status == DownloadStack.STATUS_WAITING ||
                status == DownloadStack.STATUS_STOP ||
                status == DownloadStack.STATUS_ERROR;
    }

    private Bundle getDownloadItemInfo(@NonNull FileDownloader downloader) {
        Log.assertLog(downloader != null, TAG, "get info of a null downloader");
        Log.assertLog(isDownloading(downloader), TAG, "get info of a not downloading item");

        Bundle data = new Bundle();

        data.putString("uid", downloader.getDownloadUid());
        data.putString("url", downloader.getDownloadUrl());
        data.putInt("filesize", downloader.getFileSize());
        data.putString("filedir", downloader.getFileDir());
        data.putString("filename", downloader.getFileName());
        data.putInt("downloadedsize", downloader.getDownloadedSize());
        data.putInt("status", downloader.getStatus());

        return data;
    }

    public Bundle getDownloadItemInfo(String uid) {
        FileDownloader downloader = mHelper.getDownloading(uid);
        if (downloader != null && isDownloading(downloader)) {
            return getDownloadItemInfo(downloader);
        }

        return null;
    }

    public Bundle[] getDownloadList() {
        ArrayList<Bundle> result = new ArrayList<>();
        for (FileDownloader downloader : mHelper.getDownloadingList().values()) {
            if (isDownloading(downloader)) {
                result.add(getDownloadItemInfo(downloader));
            }
        }

        Bundle[] array = new Bundle[result.size()];
        return result.toArray(array);
    }

    public void setDownloadListener(IDownloadListener listener) {
        mDownloadListener = listener;
    }

    public interface IDownloadListener {
        void onDownloadTaskAdd(String uid);

        void onDownloadTaskReadd(String uid, float progress);

        void onDownloadTaskStart(String uid, String filepath, int filesize);

        void onDownloadTaskBaseInfo(String uid, String filepath, int filesize);

        void onDownloadTaskPause(String uid, float progress);

        void onDownloadTaskProgress(String uid, float progress);

        void onDownloadTaskFinish(String uid, String filePath);

        void onDownloadTaskError(String uid, int errorCode);

        void onDownloadTaskRemove(String uid);
    }

}
