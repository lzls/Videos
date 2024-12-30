/*
 * Created on 2022-2-23 6:44:59 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.AsyncTask;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.AtomicFile;

import com.bumptech.glide.Glide;
import com.liuzhenlin.common.Configs;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;

public abstract class InternetResourceLoadTask<Result> extends AsyncTask<Void, Void, Result> {

    @MainThread
    public static abstract class ResultCallback<Result> {
        public void onCompleted(@Nullable Result result) {}
        public void onCancelled(@Nullable Result result) {}
    }

    @NonNull protected final String mUrl;
    private ResultCallback<Result> mCallback;
    private volatile boolean mCompleted;

    @SuppressLint("StaticFieldLeak")
    protected static Context sContext;

    public InternetResourceLoadTask(@NonNull String url) {
        super();
        mUrl = url;
    }

    public static void setAppContext(@NonNull Context context) {
        sContext = context.getApplicationContext();
    }

    @Override
    protected Result doInBackground(Void... params) {
        try {
            Object[] result = new Object[1];
            HttpRequester.doGet(mUrl, null, null,
                    (url, responseCode, dataStream) -> {
                        if (responseCode == HttpRequester.RESPONSE_OK) {
                            result[0] = decodeStream(dataStream);
                        } else {
                            throw new ResponseNotOKException(
                                    "url= " + url + "\n" + "responseCode= " + responseCode);
                        }
                    });
            //noinspection unchecked
            return (Result) result[0];
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Nullable
    protected abstract Result decodeStream(@NonNull InputStream dataStream) throws IOException;

    @Override
    protected void onPostExecute(Result result) {
        mCompleted = true;
        if (mCallback != null) {
            mCallback.onCompleted(result);
        }
    }

    @Override
    protected void onCancelled(Result result) {
        if (mCallback != null) {
            mCallback.onCancelled(result);
        }
    }

    public InternetResourceLoadTask<Result> onResult(@Nullable ResultCallback<Result> callback) {
        mCallback = callback;
        return this;
    }

    public boolean isCompleted() {
        return mCompleted;
    }

    @NonNull
    public static InternetResourceLoadTask<Bitmap> ofBitmap(@NonNull String url) {
        return new InternetResourceLoadTask<Bitmap>(url) {
            @Override
            protected Bitmap doInBackground(Void... params) {
                try {
                    return Glide.with(sContext).asBitmap().load(url).submit().get();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Nullable
            @Override
            protected Bitmap decodeStream(@NonNull InputStream dataStream) throws IOException {
                return null; // will never be called
            }
        };
    }

    @NonNull
    public static InternetResourceLoadTask<String> ofString(@NonNull String url) {
        return ofString(url, true);
    }

    @NonNull
    public static InternetResourceLoadTask<String> ofString(@NonNull String url, boolean useCaches) {
        return new InternetStringLoadTask(url, useCaches);
    }

    static class InternetStringLoadTask extends InternetResourceLoadTask<String> {

        final boolean mUseCaches;

        InternetStringLoadTask(@NonNull String url, boolean useCaches) {
            super(url);
            mUseCaches = useCaches;
        }

        @Override
        protected String doInBackground(Void... params) {
            String ret;
            File baseCache = null;
            AtomicFile cache = null;

            try {
                String urlSha1 = IOUtils.getDigest(
                        new ByteArrayInputStream(mUrl.getBytes()), "SHA-1");
                File cacheDir = new File(FileUtils.getAppCacheDir(sContext), "data/txt");
                baseCache = new File(cacheDir, urlSha1);
                cache = new AtomicFile(baseCache);

                if (!cacheDir.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    cacheDir.mkdirs();
                }
                if (!baseCache.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    baseCache.createNewFile();
                }

                if (mUseCaches) {
                    FileInputStream baseCacheIn = null;
                    FileLock lock = null;
                    try {
                        baseCacheIn = new FileInputStream(baseCache);
                        FileChannel lockChannel = baseCacheIn.getChannel();
                        do {
                            try {
                                lock = lockChannel.tryLock(0L, Long.MAX_VALUE, true);
                            } catch (OverlappingFileLockException e) {
                                // Another thread within the same process is already acquired
                                // the file read lock and has not released it...
                                // TODO: enable multiple threads in the same process to acquire
                                //       the read lock simultaneously
                            }
                            if (lock == null) Thread.yield();
                        } while (lock == null);
                        ret = IOUtils.decodeStringFromStream(cache.openRead());
                        if (ret != null) {
                            return ret;
                        }
                    } finally {
                        if (lock != null) {
                            lock.release();
                        }
                        IOUtils.closeSilently(baseCacheIn);
                    }
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

            ret = super.doInBackground(params);

            if (ret != null && cache != null) {
                FileOutputStream baseCacheOut = null;
                try {
                    FileOutputStream out = null;
                    Writer writer = null;
                    baseCacheOut = new FileOutputStream(baseCache);
                    FileChannel lockChannel = baseCacheOut.getChannel();
                    FileLock lock = null;
                    do {
                        try {
                            lock = lockChannel.tryLock(0L, Long.MAX_VALUE, false);
                        } catch (OverlappingFileLockException e) {
                            // Another thread within the same process is already acquired
                            // the file write lock and has not released it...
                        }
                        if (lock == null) Thread.yield();
                    } while (lock == null);
                    try {
                        out = cache.startWrite();
                        writer = new OutputStreamWriter(out, Configs.DEFAULT_CHARSET);
                        writer.write(ret);
                        writer.flush();
                        cache.finishWrite(out);
                    } catch (Exception e) {
                        if (out != null) {
                            cache.failWrite(out);
                        }
                        throw e;
                    } finally {
                        IOUtils.closeSilently(writer);
                        lock.release();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } finally {
                    IOUtils.closeSilently(baseCacheOut);
                }
            }

            return ret;
        }

        @Override
        protected String decodeStream(@NonNull InputStream dataStream) throws IOException {
            String str = IOUtils.decodeStringFromStream(dataStream);
            if (str == null) {
                throw new IOException("Failed to decode string from " + mUrl);
            }
            return str;
        }
    }
}
