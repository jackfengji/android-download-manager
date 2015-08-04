package cn.kukool.downloader.aidl;
/**
 * User: jackfengji
 * Date: 13-1-24
 * 
 */
interface IDownloadListener {
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
