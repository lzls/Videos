/*
 * Created on 2021-12-17 6:14:24 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.crashhandler;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.collection.SimpleArrayMap;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.liuzhenlin.common.utils.HttpRequester;
import com.liuzhenlin.common.utils.IOUtils;
import com.liuzhenlin.common.utils.ResponseNotOKException;
import com.liuzhenlin.common.utils.Utils;
import com.liuzhenlin.videos.BuildConfig;
import com.liuzhenlin.videos.ExtentionsKt;
import com.liuzhenlin.videos.Files;
import com.liuzhenlin.videos.bean.Device;
import com.liuzhenlin.videos.dao.AppPrefs;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;

public abstract class CrashReporter {

    private static final String URL_CRASH_REPORT_BLACKLISTS =
            "https://gitlab.com/lzls/Videos-Server/-/raw/master/crash_report_blacklists.json";

    private static final String KEY_DEVICE_BLACKLIST = "deviceBlacklist";
    private static final String KEY_DEVICE_MODEL_BLACKLIST = "deviceModelBlacklist";

    protected final Context mContext;

    public CrashReporter(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    @WorkerThread
    public final boolean send() {
        File crashLogsDir = Files.getCrashLogsDir(mContext);
        File[] logs = crashLogsDir.listFiles();
        if (logs != null && logs.length > 0) {
            boolean officialApp = false;
            try {
                officialApp = Utils.areAppSignaturesMatch(mContext, BuildConfig.RELEASE_SIGN_MD5);
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            String deviceId = officialApp ? AppPrefs.getSingleton(mContext).getGUID() : null;
            if (!officialApp
                    || isDeviceFiltered(new Device(deviceId, Build.MANUFACTURER, Build.MODEL))) {
                // Don't send crash logs to me if this app is not the officially signed one or
                // the device is included in the remote device blacklist.
                deleteCrashLogs(logs);
            } else {
                boolean ret = sendInternal(logs);
                if (ret) {
                    deleteCrashLogs(logs);
                    return true;
                }
            }
        }
        return false;
    }

    private static void deleteCrashLogs(File[] logs) {
        for (File log : logs) {
            //noinspection ResultOfMethodCallIgnored
            log.delete();
        }
    }

    protected abstract boolean sendInternal(@NonNull File[] logs);

    static boolean isDeviceFiltered(@NonNull Device device) {
        try {
            HttpCallback callback = new HttpCallback();
            HttpRequester.doGet(URL_CRASH_REPORT_BLACKLISTS, null, null, callback);

            String deviceModel = device.getNormalizedDeviceModel();
            String[] deviceModelBlacklist = callback.result.get(KEY_DEVICE_MODEL_BLACKLIST);
            if (deviceModelBlacklist != null) {
                for (String blackDeviceModel : deviceModelBlacklist) {
                    if (ExtentionsKt.equalsOrMatches(deviceModel, blackDeviceModel, true)) {
                        return true;
                    }
                }
            }

            String deviceId = device.getId();
            String[] deviceBlacklist = callback.result.get(KEY_DEVICE_BLACKLIST);
            if (deviceBlacklist != null) {
                for (String blackDeviceId : deviceBlacklist) {
                    if (ExtentionsKt.equalsOrMatches(deviceId, blackDeviceId, true)) {
                        return true;
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private static final class HttpCallback implements HttpRequester.Callback {

        final SimpleArrayMap<String, String[]> result = new SimpleArrayMap<>();

        HttpCallback() {
        }

        @Override
        public void onResult(@NonNull String url, int responseCode, @NonNull InputStream dataStream)
                throws IOException {
            if (responseCode != HttpRequester.RESPONSE_OK) {
                throw new ResponseNotOKException(
                        "url= " + url + "\n" + "responseCode= " + responseCode);
            }

            String json = IOUtils.decodeStringFromStream(dataStream);
            if (json == null) {
                throw new IOException("Failed to fetch json data from " + url);
            }

            JsonObject jsonObj = JsonParser.parseString(json).getAsJsonObject();
            //noinspection SwitchStatementWithTooFewBranches
            switch (url) {
                case URL_CRASH_REPORT_BLACKLISTS: {
                    Gson gson = new Gson();
                    result.put(KEY_DEVICE_BLACKLIST,
                            gson.fromJson(jsonObj.get(KEY_DEVICE_BLACKLIST), String[].class));
                    result.put(KEY_DEVICE_MODEL_BLACKLIST,
                            gson.fromJson(jsonObj.get(KEY_DEVICE_MODEL_BLACKLIST), String[].class));
                    break;
                }
            }
        }
    }
}
