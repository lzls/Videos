/*
 * Created on 2018/12/8 10:07 PM.
 * Copyright © 2018 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.splashscreen.SplashScreen;

import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.FileUtils;
import com.liuzhenlin.common.utils.ThemeUtils;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.LegacyExternalStorageDataMigrator;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.dao.AppPrefs;
import com.liuzhenlin.videos.web.youtube.YoutubePlaybackService;

import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.EasyPermissions;
import pub.devrel.easypermissions.PermissionRequest;

import static java.util.Collections.singletonList;

/**
 * @author 刘振林
 */
public class BootstrapActivity extends BaseActivity
        implements EasyPermissions.PermissionCallbacks, EasyPermissions.RationaleCallbacks {

    private static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION = 6;
    private static final int REQUEST_CODE_ALL_FILES_ACCESS_PERMISSION = 7;

    // Read-only Fields
    public String cancel;
    public String ok;

    private boolean mStoragePermissionAlreadyPermanentlyDenied;

    private AlertDialog mAskForAllFilesAccessDialog;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        cancel = getString(R.string.cancel);
        ok = getString(R.string.ok);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // Only fullscreen opaque activities can request fixed orientation on Oreo
        setRequestedOrientation(
                Build.VERSION.SDK_INT == Build.VERSION_CODES.O
                        ? ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            SplashScreen.installSplashScreen(this);
        }
        super.onCreate(savedInstanceState);
        setAsNonSwipeBackActivity();
        setLightStatus(!ThemeUtils.isNightMode(this));

        if (App.getInstance(this).hasAllFilesAccess()) {
            // Have All Files Access, do the thing!
            launch(true);
        } else {
            checkStoragePermission();
        }
    }

    private void checkStoragePermission() {
        if (App.getInstance(this).hasStoragePermission()) {
            // Have storage permission, check whether we have All Files Access...
            onStoragePermissionGranted();
        } else {
            mStoragePermissionAlreadyPermanentlyDenied =
                    EasyPermissions.somePermissionPermanentlyDenied(
                            this, singletonList(Manifest.permission.WRITE_EXTERNAL_STORAGE));
            // Ask for one permission
            EasyPermissions.requestPermissions(
                    new PermissionRequest.Builder( //@formatter:off
                                    this,
                                    REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE) //@formatter:on
                            .setTheme(R.style.DialogStyle_MinWidth_NoTitle)
                            .setRationale(R.string.rationale_askExternalStoragePermission)
                            .setNegativeButtonText(cancel)
                            .setPositiveButtonText(ok)
                            .build());
        }
    }

    @AfterPermissionGranted(REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION)
    private void onStoragePermissionGranted() {
        if (App.getInstance(this).hasAllFilesAccess()) {
            launch(true);
        } else if (AppPrefs.getSingleton(this).doesUserPreferRunningAppInRestrictedMode()) {
            launch(false);
        } else {
            mAskForAllFilesAccessDialog =
                    new AlertDialog.Builder(this, R.style.DialogStyle_MinWidth_NoTitle)
                            .setMessage(R.string.rationale_askAllFilesAccessPermission)
                            .setNegativeButton(R.string.runAppInRestrictedMode, null)
                            .setPositiveButton(R.string.goNow, null)
                            .setCancelable(false)
                            .show();
            mAskForAllFilesAccessDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        @SuppressLint("InlinedApi") Intent it =
                                new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                        .setData(Uri.fromParts("package", getPackageName(), null));
                        startActivityForResult(it, REQUEST_CODE_ALL_FILES_ACCESS_PERMISSION);
                    });
            mAskForAllFilesAccessDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setOnClickListener(v -> {
                        AppPrefs.getSingleton(this).edit()
                                .setUserPreferRunningAppInRestrictedMode(true)
                                .apply();
                        launch(false);
                    });
        }
    }

    private void launch(boolean hasAllFilesAccess) {
        AppPrefs appPrefs = AppPrefs.getSingleton(this);

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
                    migrated = new LegacyExternalStorageDataMigrator(this).migrate();
                }
                if (migrated) {
                    appPrefs.edit().setLegacyExternalStorageDataMigrated(true).apply();
                }
            });
        }

        Intent intent = getIntent();
        String action = intent.getAction();
        try {
            if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action)) {
                Uri data = intent.getData();
                String url = null;
                if (data != null) {
                    url = FileUtils.UriResolver.getPath(this, data);
                }
                if (url == null) {
                    url = intent.getStringExtra(Intent.EXTRA_TEXT);
                }
                if (url != null && YoutubePlaybackService.startPlaybackIfUrlIsWatchUrl(this, url)) {
                    return;
                }

                intent.setAction(null);
                intent.setClass(this, VideoActivity.class);
                startActivity(intent);

            } else {
                startActivity(new Intent(this, MainActivity.class));
            }
        } finally {
            finish();
        }
    }

    private void onStoragePermissionDenied() {
        finish();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // EasyPermissions handles the request result.
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        App app = App.getInstance(this);
        switch (requestCode) {
            case REQUEST_CODE_ALL_FILES_ACCESS_PERMISSION:
                if (app.hasAllFilesAccess()) {
                    launch(true);
                }
                break;
            case AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE:
                // Do something after user returned from app settings screen.
                if (app.hasStoragePermission()) {
                    onStoragePermissionGranted();
                } else {
                    onStoragePermissionDenied();
                }
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAskForAllFilesAccessDialog != null) {
            mAskForAllFilesAccessDialog.dismiss();
            mAskForAllFilesAccessDialog = null;
        }
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> perms) {
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        // (Optional) Check whether the user denied any permissions the LAST time
        // the permission request dialog was shown and checked "NEVER ASK AGAIN".
        // This will display a dialog directing them to enable the permission in app settings.
        if (mStoragePermissionAlreadyPermanentlyDenied
                && EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            new AppSettingsDialog.Builder(this)
                    .setThemeResId(R.style.DialogStyle_MinWidth)
                    .setTitle(R.string.permissionsRequired)
                    .setRationale(R.string.rationale_askExternalStoragePermissionAgain)
                    .setNegativeButton(cancel)
                    .setPositiveButton(ok)
                    .build()
                    .show();
        } else {
            onStoragePermissionDenied();
        }
    }

    @Override
    public void onRationaleAccepted(int requestCode) {
    }

    @Override
    public void onRationaleDenied(int requestCode) {
        onStoragePermissionDenied();
    }
}
