/*
 * Created on 2023-5-18 4:56:04 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity.startup;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LifecycleOwner;

import com.liuzhenlin.common.utils.Utils;

public class NotificationPermissionProcessor<H extends Activity & LifecycleOwner>
        extends BaseProcessor<H> {

    private static final int REQUEST_CODE_POST_NOTIFICATIONS_PERMISSION = 8;

    @Override
    public boolean process(@NonNull LaunchChain<H> chain) {
        if (!super.process(chain)) {
            if (Utils.getAppTargetSdkVersion(mChain.host) < 33 || Build.VERSION.SDK_INT < 33) {
                chain.pass(this);
                return true;
            }

            String permission = Manifest.permission.POST_NOTIFICATIONS;
            if (ActivityCompat.shouldShowRequestPermissionRationale(chain.host, permission)) {
                if (ActivityCompat.checkSelfPermission(chain.host, permission)
                        == PackageManager.PERMISSION_DENIED) {
                    ActivityCompat.requestPermissions(chain.host, new String[]{permission},
                            REQUEST_CODE_POST_NOTIFICATIONS_PERMISSION);
                    return true;
                }
            }
        }

        chain.pass(this);
        return true;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS_PERMISSION) {
            mChain.pass(this);
        }
    }
}
