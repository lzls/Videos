/*
 * Created on 2021-12-17 6:27:44 PM.
 * Copyright © 2021 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.crashhandler;

import android.content.Context;

import androidx.annotation.NonNull;

import com.liuzhenlin.videos.utils.MailUtil;

import java.io.File;

public class CrashMailReporter extends CrashReporter {

    public CrashMailReporter(@NonNull Context context) {
        super(context);
    }

    @Override
    protected boolean sendInternal(@NonNull File[] logs) {
        String[] logPaths = new String[logs.length];
        for (int i = 0; i < logs.length; i++) {
            logPaths[i] = logs[i].getPath();
        }
        return MailUtil.sendMailSync(mContext, "[视频奔溃上报]", "", null, logPaths);
    }
}
