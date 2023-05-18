/*
 * Created on 2023-5-18 4:05:01 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity.startup;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;

import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.FileUtils;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.LegacyExternalStorageDataMigrator;
import com.liuzhenlin.videos.dao.AppPrefs;
import com.liuzhenlin.videos.view.activity.MainActivity;
import com.liuzhenlin.videos.view.activity.VideoActivity;
import com.liuzhenlin.videos.web.youtube.YoutubePlaybackService;

public class LaunchProcessor<H extends Activity & LifecycleOwner> extends BaseProcessor<H> {

    @Override
    public boolean process(@NonNull LaunchChain<H> chain) {
        if (!super.process(chain)) {
            AppPrefs appPrefs = AppPrefs.getSingleton(chain.host);
            boolean hasAllFilesAccess = App.getInstance(chain.host).hasAllFilesAccess();

            // Reset user preference to let user redetermine which mode app will be run in
            // the next time All Files Access is denied.
            if (hasAllFilesAccess) {
                appPrefs.edit().setUserPreferRunningAppInRestrictedMode(false).apply();
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    && !appPrefs.isLegacyExternalStorageDataMigrated()) {
                Executors.THREAD_POOL_EXECUTOR.execute(() -> {
                    boolean migrated = true;
                    if (hasAllFilesAccess) {
                        migrated = new LegacyExternalStorageDataMigrator(chain.host).migrate();
                    }
                    if (migrated) {
                        appPrefs.edit().setLegacyExternalStorageDataMigrated(true).apply();
                    }
                });
            }

            Intent intent = chain.host.getIntent();
            String action = intent.getAction();
            try {
                if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action)) {
                    Uri data = intent.getData();
                    String url = null;
                    if (data != null) {
                        url = FileUtils.UriResolver.getPath(chain.host, data);
                    }
                    if (url == null) {
                        url = intent.getStringExtra(Intent.EXTRA_TEXT);
                    }
                    if (url != null
                            && YoutubePlaybackService.startPlaybackIfUrlIsWatchUrl(chain.host, url)) {
                        return true;
                    }

                    intent.setAction(null);
                    intent.setClass(chain.host, VideoActivity.class);
                    chain.host.startActivity(intent);

                } else {
                    chain.host.startActivity(new Intent(chain.host, MainActivity.class));
                }
            } finally {
                chain.pass(this);
            }
        }
        return true;
    }
}
