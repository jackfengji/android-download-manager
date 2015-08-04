package cn.kukool.downloader.aidl;

import android.os.Bundle;
import cn.kukool.downloader.aidl.IDownloadListener;

interface IDownloader {
    // url is the url of download, limit 1024
    // postData is the post data, if null, then we use get, otherwise we use post method, limit 1024
    // path is the destination of downloaded file, can be null, then it will store the file in /sdcard/Android/data/.downloader folder
    // uid is the unique id of one request, limit 1024
    // icon is the icon of app. maybe null, limit 1024
	boolean startDownload(String uid, String url, String postData, String path);

	// pause the download request by uid
	// return true the download request is paused
	// return false if there's no such downloading request or it's already paused
	boolean pauseDownload(String uid);

	// resume the download request by uid
	// return true the download request is resumed
	// return false if there's no such downloading request or it's already in downloading
	boolean resumeDownload(String uid);

	// remove/stop the download request by uid
	// uid can be either the uid of a request in downloading or already downloaded
	// if withFile is true, then the downloaded file will also be removed
	// return true if the download is removed
	// return false if there's no such download
	boolean removeDownload(String uid, boolean withFile);

	// get download status
	// -1 ---- no such download
	// 0 ----- already downloaded
	// 1 ----- downloading
	// 2 ----- waiting to continue downloading
	// 3 ----- paused download
	// 4 ----- error
	int getDownloadItemStatus(String uid);

	// if there's a such download's status in 1, 2, 3
	// thren return uid, url, icon, filesize, already downloaded size, filename, filedir
	// otherwise will return null
	Bundle getDownloadItemInfo(String uid);

    // get download list
    Bundle[] getDownloadList();

    void setDownloadListener(IDownloadListener listener);
} 
