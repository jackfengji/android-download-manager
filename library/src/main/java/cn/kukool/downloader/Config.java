package cn.kukool.downloader;

import cn.kukool.downloader.util.Util;

/**
 * User: jackfengji
 * Date: 15/8/4
 */
public class Config {
    public static final String USER_AGENT = "Kukool/Android-Downloader";

    /**
     * max block size for a single thread
     */
    public static final int MAX_BLOCK_SIZE = 2 << 20;

    /**
     * max retry count for a single thread
     */
    public static final int MAX_THREAD_RETRY = 5;

    /**
     * max thread count for a single downloading
     */
    public static final int MAX_THREAD_CNT = 3;

    /**
     * max number of downloading working together
     */
    public static final int MAX_DOWNLOADS = 2;

    /**
     * default download directory when start a downloading without specifying the download path
     */
    public static final String DEFAULT_DOWNLOAD_DIR = Util.getSdcardPath() + "/Android/data/.downloader";

    /**
     * When set to true, download manager will send broadcasts for actions.
     */
    public static final boolean ENABLE_BROADCAST = false;

    /**
     * Connect timeout in seconds
     */
    public static final int CONNECT_TIMEOUT = 10;

    /**
     * Read timeout in seconds
     */
    public static final int READ_TIMEOUT = 10;
}
