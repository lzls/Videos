/*
 * Created on 2018/12/8 10:07 PM.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.splashscreen.SplashScreen;

import com.liuzhenlin.common.utils.ThemeUtils;
import com.liuzhenlin.common.utils.Utils;
import com.liuzhenlin.videos.view.activity.startup.LaunchChain;
import com.liuzhenlin.videos.view.activity.startup.LaunchProcessor;
import com.liuzhenlin.videos.view.activity.startup.NotificationPermissionProcessor;
import com.liuzhenlin.videos.view.activity.startup.StoragePermissionsInterceptor;
import com.liuzhenlin.videos.view.activity.startup.UsageStatusSharingProcessor;

import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;

/**
 * @author 刘振林
 */
public class BootstrapActivity extends BaseActivity
        implements EasyPermissions.PermissionCallbacks, EasyPermissions.RationaleCallbacks {

    private final LaunchChain<BootstrapActivity> mLaunchChain =
            new LaunchChain.Builder<>(this)
                    .processor(new StoragePermissionsInterceptor<>())
                    .processor(new NotificationPermissionProcessor<>())
                    .processor(new UsageStatusSharingProcessor<>())
                    .processor(new LaunchProcessor<>())
                    .build();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Only fullscreen opaque activities can request fixed orientation on Oreo
        setRequestedOrientation(
                Utils.getAppTargetSdkVersion(this) > Build.VERSION_CODES.O
                        && Build.VERSION.SDK_INT == Build.VERSION_CODES.O
                                ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SplashScreen.installSplashScreen(this);
        }
        super.onCreate(savedInstanceState);
        setAsNonSwipeBackActivity();
        setLightStatus(!ThemeUtils.isNightMode(this));

        mLaunchChain.start();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        mLaunchChain.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        mLaunchChain.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
        mLaunchChain.onPermissionsGranted(requestCode, perms);
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        mLaunchChain.onPermissionsDenied(requestCode, perms);
    }

    @Override
    public void onRationaleAccepted(int requestCode) {
        mLaunchChain.onRationaleAccepted(requestCode);
    }

    @Override
    public void onRationaleDenied(int requestCode) {
        mLaunchChain.onRationaleDenied(requestCode);
    }
}
