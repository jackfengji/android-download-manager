### Configurations

In Config.java, you can change the following settings.

```java

public static final int MAX_BLOCK_SIZE = 2 << 20; // max block size for a single thread
public static final int MAX_THREAD_RETRY = 5; // max retry count for a single thread
public static final int MAX_THREAD_CNT = 3; // max thread count for a single downloading
public static final int MAX_DOWNLOADS = 2; // max number of downloading working together

```

### Usage

##### Use DownloadStack directly

```

mStack = new DownloadStack(this);
mStack.setDownloadListener(this);

mStack.startDownload("test_download_asset",
        "http://www.westangels.cn/ggbond_resource/download/asset_0622.zip",

        null, getFilesDir().getAbsolutePath() + File.separator, null);

mStack.startDownload("test_download_apk",
        "http://www.westangels.cn/ggbond_resource/download/zzxzjjz.apk",
        null, getFilesDir().getAbsolutePath() + File.separator, null);


mStack.removeDownload("test_download_asset", true);
mStack.removeDownload("test_download_apk", true);
mStack.setDownloadListener(null);

```


##### Use DownloadService

1. Add DownloadService to your manifest.
```
<service android:name="cn.kukool.downloader.service.DownloadService"
    android:exported="false" android:permission="android.permission.INTERNET">
    <intent-filter>
        <action android:name="cn.kukool.downloader.downloadservice" />
    </intent-filter>
</service>
```

2.
```java
private IDownloader mDownloader;
private ServiceConnection mConnection = new ServiceConnection() {
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mDownloader = IDownloader.Stub.asInterface(service);

        try {
            mDownloader.setDownloadListener(mDownloadListener);

            mDownloader.startDownload("test_download_asset",
                    "http://www.westangels.cn/ggbond_resource/download/asset_0622.zip",
                    null, getFilesDir().getAbsolutePath() + File.separator);

            mDownloader.startDownload("test_download_apk",
                    "http://www.westangels.cn/ggbond_resource/download/zzxzjjz.apk",
                    null, getFilesDir().getAbsolutePath() + File.separator);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mDownloader = null;
    }
};

@Override
protected void onResume() {
    super.onResume();

    bindService(new Intent(this, DownloadService.class), mConnection, BIND_AUTO_CREATE);
}

@Override
protected void onPause() {
    super.onPause();

    if (mDownloader != null) {
        try {
            mDownloader.removeDownload("test_download_asset", true);
            mDownloader.removeDownload("test_download_apk", true);
            mDownloader.setDownloadListener(null);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        unbindService(mConnection);
    }
}
```