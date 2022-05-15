package cn.xyz.threadpool;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class LoggerThreadPoolManager {


    /**
     * 线程池维护线程的最少数量
     */
    private static final int SIZE_CORE_POOL = 100;

    /**
     * 线程池维护线程的最大数量
     */
    private static final int SIZE_MAX_POOL = Integer.MAX_VALUE;

    /**
     * 线程池维护线程的最少数量（六合彩）
     */
    private static final int LH_SIZE_CORE_POOL = 100;

    /**
     * 线程池维护线程的最大数量（六合彩）
     */
    private static final int LH_SIZE_MAX_POOL = Integer.MAX_VALUE;


    private volatile static LoggerThreadPoolManager sendThreadPoolManager  = null;

    private final ThreadPoolExecutor mThreadPool = new ThreadPoolExecutor(SIZE_CORE_POOL, SIZE_MAX_POOL, 15,

            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),new ThreadPoolExecutor.CallerRunsPolicy());

    private final ThreadPoolExecutor mThreadPoolLh = new ThreadPoolExecutor(LH_SIZE_CORE_POOL, LH_SIZE_MAX_POOL, 5,

            TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),new ThreadPoolExecutor.CallerRunsPolicy());

    /*
     * 线程池单例创建方法
     */
    public static LoggerThreadPoolManager getInstance() {
        if(sendThreadPoolManager == null){
            synchronized (LoggerThreadPoolManager.class) {
                if(sendThreadPoolManager == null){
                    sendThreadPoolManager = new LoggerThreadPoolManager();
                }
            }
        }
        return sendThreadPoolManager;
    }

    public ThreadPoolExecutor getThreadPool(){

        return mThreadPool;
    }

    public ThreadPoolExecutor getLhThreadPool(){

        return mThreadPoolLh;
    }
}
