package cn.kukool.downloadertest;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.ProgressBar;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import cn.kukool.downloader.aidl.IDownloadListener;
import cn.kukool.downloader.aidl.IDownloader;
import cn.kukool.downloader.download.DownloadStack;
import cn.kukool.downloader.service.DownloadService;

public class MainActivity extends AppCompatActivity implements DownloadStack.IDownloadListener {
    private static boolean USE_SERVICE = false;

    private DownloadStack mStack;

    private IDownloader mDownloader;
    private IDownloadListener.Stub mDownloadListener = new IDownloadListener.Stub() {
        @Override
        public void onDownloadTaskAdd(String uid) throws RemoteException {
            MainActivity.this.onDownloadTaskAdd(uid);
        }

        @Override
        public void onDownloadTaskReadd(String uid, float progress) throws RemoteException {
            MainActivity.this.onDownloadTaskReadd(uid, progress);
        }

        @Override
        public void onDownloadTaskStart(String uid, String filepath, int filesize) throws RemoteException {
            MainActivity.this.onDownloadTaskStart(uid, filepath, filesize);
        }

        @Override
        public void onDownloadTaskBaseInfo(String uid, String filepath, int filesize) throws RemoteException {
            MainActivity.this.onDownloadTaskBaseInfo(uid, filepath, filesize);
        }

        @Override
        public void onDownloadTaskPause(String uid, float progress) throws RemoteException {
            MainActivity.this.onDownloadTaskPause(uid, progress);
        }

        @Override
        public void onDownloadTaskProgress(String uid, float progress) throws RemoteException {
            MainActivity.this.onDownloadTaskProgress(uid, progress);
        }

        @Override
        public void onDownloadTaskFinish(String uid, String filePath) throws RemoteException {
            MainActivity.this.onDownloadTaskFinish(uid, filePath);
        }

        @Override
        public void onDownloadTaskError(String uid, int errorCode) throws RemoteException {
            MainActivity.this.onDownloadTaskError(uid, errorCode);
        }

        @Override
        public void onDownloadTaskRemove(String uid) throws RemoteException {
            MainActivity.this.onDownloadTaskRemove(uid);
        }
    };
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!USE_SERVICE) {
            mStack = new DownloadStack(this);
            mStack.setDownloadListener(this);

            boolean success = mStack.startDownload("test_download_asset",
                    "http://www.westangels.cn/ggbond_resource/download/asset_0622.zip",
                    null, getFilesDir().getAbsolutePath() + File.separator) &&
                    mStack.startDownload("test_download_apk",
                            "http://www.westangels.cn/ggbond_resource/download/zzxzjjz.apk",
                            null, getFilesDir().getAbsolutePath() + File.separator);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (USE_SERVICE) {
            bindService(new Intent(this, DownloadService.class), mConnection, BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (USE_SERVICE && mDownloader != null) {
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

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (!USE_SERVICE && mStack != null) {
            mStack.removeDownload("test_download_asset", true);
            mStack.removeDownload("test_download_apk", true);
            mStack.setDownloadListener(null);
        }
    }

    @Override
    public void onDownloadTaskAdd(String uid) {

    }

    @Override
    public void onDownloadTaskReadd(String uid, float progress) {

    }

    @Override
    public void onDownloadTaskStart(String uid, String filepath, int filesize) {

    }

    @Override
    public void onDownloadTaskBaseInfo(String uid, String filepath, int filesize) {

    }

    @Override
    public void onDownloadTaskPause(String uid, float progress) {

    }

    @Override
    public void onDownloadTaskProgress(String uid, float progress) {
        ((ProgressBar) getWindow().getDecorView().findViewWithTag(uid)).setProgress((int) (progress * 100));
    }

    @Override
    public void onDownloadTaskFinish(String uid, final String filePath) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("MainActivity", "unzip result: " + FileUtils.unzipArchive(new File(filePath), getFilesDir()));
            }
        }, "Unzip #" + uid).start();
    }

    @Override
    public void onDownloadTaskError(String uid, int errorCode) {

    }

    @Override
    public void onDownloadTaskRemove(String uid) {

    }

    public static class FileUtils {
        public static File unzipArchive(File archiveFile, File target) {
            String archiveName = archiveFile.getName();
            int location = archiveName.lastIndexOf(".zip");
            if (location == -1) {
                location = archiveName.lastIndexOf(".apk");
            }
            if (location == -1) return null;

            File targetFolder = new File(target, archiveName.substring(0, location));
            deleteFile(targetFolder);
            if (unzipLocalFile(archiveFile, targetFolder)) {
                return targetFolder;
            } else {
                deleteFile(targetFolder);
                return null;
            }
        }

        private static boolean unzipLocalFile(File filename, File toFolder) {
            boolean ret = createDirIfNotExists(toFolder);
            // using the ZipFile to unzip
            ZipFile zipFile = null;

            try {
                final int BUFFER_SIZE = 1024 * 8;
                byte[] buffer = new byte[BUFFER_SIZE];
                int count;

                zipFile = new ZipFile(filename);
                java.util.Enumeration entries = zipFile.entries();
                while (ret && entries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) entries.nextElement();
                    File nextFile = new File(toFolder, entry.getName());
                    if (entry.isDirectory()) {
                        ret = createDirIfNotExists(nextFile);
                    } else {
                        ret = createDirIfNotExists(nextFile.getParentFile());
                        BufferedInputStream fin = new BufferedInputStream(zipFile.getInputStream(entry));
                        BufferedOutputStream fout = new BufferedOutputStream(new FileOutputStream(nextFile));
                        while ((count = fin.read(buffer, 0, BUFFER_SIZE)) != -1)
                            fout.write(buffer, 0, count);

                        fin.close();
                        fout.close();
                    }
                }
            } catch (ZipException e) {
                e.printStackTrace();
                ret = false;
            } catch (IOException e) {
                e.printStackTrace();
                ret = false;
            } finally {
                if (zipFile != null) {
                    try {
                        zipFile.close();
                    } catch (IOException e) {
                        // ignore
                        e.printStackTrace();
                    }
                }
            }
            return ret;
        }

        public static boolean createDirIfNotExists(File directory) {
            return directory.exists() || directory.mkdirs();
        }

        public static boolean deleteFile(File file) {
            if (file == null || (!file.exists())) {
                return false;
            }

            if (file.isDirectory()) {
                File[] children = file.listFiles();
                for (File child : children) {
                    boolean success = deleteFile(child);
                    if (!success)
                        return false;
                }
            }

            return file.delete();
        }
    }
}
