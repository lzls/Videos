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

import com.bumptech.glide.Glide;

import java.io.IOException;
import java.io.InputStream;
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
        return new InternetResourceLoadTask<String>(url) {
            @Override
            protected String decodeStream(@NonNull InputStream dataStream) throws IOException {
                String str = IOUtils.decodeStringFromStream(dataStream);
                if (str == null) {
                    throw new IOException("Failed to decode string from " + mUrl);
                }
                return str;
            }
        };
    }
}
