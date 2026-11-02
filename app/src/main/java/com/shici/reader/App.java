package com.shici.reader;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.shici.reader.data.DictionaryRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class App extends Application {

    private static App instance;
    private ExecutorService ioExecutor;
    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        mainHandler = new Handler(Looper.getMainLooper());
        ioExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private int count;

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "shici-io-" + (++count));
                thread.setPriority(Thread.NORM_PRIORITY - 1);
                return thread;
            }
        });
        // Copy the dictionary from the APK while the shelf is still opening, so
        // the first tapped word usually arrives with no extra wait.
        DictionaryRepository.get(this).warmUp();
    }

    public static App get(Context context) {
        Context appContext = context.getApplicationContext();
        if (appContext instanceof App) {
            return (App) appContext;
        }
        return instance;
    }

    public static ExecutorService io() {
        return instance.ioExecutor;
    }

    public static Handler main() {
        return instance.mainHandler;
    }

    public static void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            instance.mainHandler.post(runnable);
        }
    }

    @Override
    public void onTerminate() {
        if (ioExecutor != null) {
            ioExecutor.shutdown();
        }
        super.onTerminate();
    }
}
