/*
 * Created on 2021-9-28 8:52:26 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.multidex.MultiDex;

import com.liuzhenlin.common.utils.Utils;
import com.taobao.sophix.SophixApplication;
import com.taobao.sophix.SophixEntry;
import com.taobao.sophix.SophixManager;
import com.taobao.sophix.listener.PatchLoadStatusListener;

import java.util.ArrayList;
import java.util.List;

public class SophixAppStub extends SophixApplication {

    private static final boolean DEBUG = false;

    @Keep
    @SophixEntry(App.class) // 只有这里改成自己的Application类，下面static不要改
    static class RealApplicationStub {
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
        if (Configs.isSophixPatchSupported()) {
            SophixAppLibrary.throwIfNotAvailable();
            initSophix();
        }
    }

    private void initSophix() {
        List<String> tags = new ArrayList<>();
        if (DEBUG) {
            tags.add("test");
        }
        tags.add("production");
        SophixManager.getInstance()
                .setContext(this)
                .setAppVersion(Utils.getAppVersionName(this))
                .setTags(tags)
                .setSecretMetaData(nGetIdSecret(this), nGetAppSecret(this), nGetRsaSecret(this))
                .setEnableDebug(DEBUG)
                .setPatchLoadStatusStub(new PatchLoadListener())
                .initialize();
    }

    private static final class PatchLoadListener implements PatchLoadStatusListener {

        static final String TAG = "SophixPatchLoadListener";

        PatchLoadListener() {
        }

        @Override
        public void onLoad(int mode, int code, String info, int handlePatchVersion) {
            if (DEBUG) {
                Log.i(TAG, "code=" + code + "; info='" + info
                        + "'; handlePatchVersion=" + handlePatchVersion);
            }
        }
    }

    private static native String nGetIdSecret(Context context);
    private static native String nGetAppSecret(Context context);
    private static native String nGetRsaSecret(Context context);
}
