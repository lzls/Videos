/*
 * Created on 2023-5-18 3:28:28 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity.startup;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.liuzhenlin.common.utils.Synthetic;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.Consts;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.dao.AppPrefs;

import java.util.List;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.AppSettingsDialog;
import pub.devrel.easypermissions.AppSettingsDialogHolderActivity2;
import pub.devrel.easypermissions.EasyPermissions;
import pub.devrel.easypermissions.PermissionRequest;

public class StoragePermissionsInterceptor<H extends Activity & LifecycleOwner>
        extends BaseProcessor<H> {

    private static final int REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION = 6;
    private static final int REQUEST_CODE_ALL_FILES_ACCESS_PERMISSION = 7;

    private boolean mStoragePermissionRationaleShown;

    private AlertDialog mAskForAllFilesAccessDialog;

    @Override
    public boolean process(@NonNull LaunchChain<H> chain) {
        if (!super.process(chain)) {
            if (App.getInstance(chain.host).hasAllFilesAccess()) {
                // Have All Files Access, do the thing!
                chain.pass(this);
            } else {
                checkStoragePermission();
            }
        }
        return true;
    }

    private void checkStoragePermission() {
        if (App.getInstance(mChain.host).hasStoragePermission()) {
            // Have storage permission, check whether we have All Files Access...
            onStoragePermissionGranted();
        } else {
            for (String perm : App.STORAGE_PERMISSION) {
                boolean show = ActivityCompat.shouldShowRequestPermissionRationale(mChain.host, perm);
                if (show) {
                    mStoragePermissionRationaleShown = true;
                    break;
                }
            }
            // Ask for storage permissions
            EasyPermissions.requestPermissions(
                    new PermissionRequest.Builder( //@formatter:off
                                    mChain.host,
                                    REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION,
                                    App.STORAGE_PERMISSION) //@formatter:on
                            .setTheme(R.style.DialogStyle_MinWidth_NoTitle)
                            .setRationale(R.string.rationale_askExternalStoragePermission)
                            .setNegativeButtonText(R.string.cancel)
                            .setPositiveButtonText(R.string.ok)
                            .build());
        }
    }

    @AfterPermissionGranted(REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION)
    private void onStoragePermissionGranted() {
        if (App.getInstance(mChain.host).hasAllFilesAccess()) {
            mChain.pass(this);
        } else if (AppPrefs.getSingleton(mChain.host).doesUserPreferRunningAppInRestrictedMode()) {
            mChain.pass(this);
        } else {
            mAskForAllFilesAccessDialog =
                    new AlertDialog.Builder(mChain.host, R.style.DialogStyle_MinWidth_NoTitle)
                            .setMessage(R.string.rationale_askAllFilesAccessPermission)
                            .setNegativeButton(R.string.runAppInRestrictedMode, null)
                            .setPositiveButton(R.string.goNow, null)
                            .setCancelable(false)
                            .show();
            mAskForAllFilesAccessDialog.getButton(DialogInterface.BUTTON_POSITIVE)
                    .setOnClickListener(v -> {
                        @SuppressLint("InlinedApi") Intent it =
                                new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        it.setData(Uri.fromParts("package", mChain.host.getPackageName(), null));
                        mChain.host.startActivityForResult(
                                it, REQUEST_CODE_ALL_FILES_ACCESS_PERMISSION);
                    });
            mAskForAllFilesAccessDialog.getButton(DialogInterface.BUTTON_NEGATIVE)
                    .setOnClickListener(v -> {
                        AppPrefs.getSingleton(mChain.host).edit()
                                .setUserPreferRunningAppInRestrictedMode(true)
                                .apply();
                        dismissAllFilesAccessDialog();
                        mChain.pass(this);
                    });
            mChain.host.getLifecycle().addObserver(new DefaultLifecycleObserver() {
                @Override
                public void onDestroy(@NonNull LifecycleOwner owner) {
                    dismissAllFilesAccessDialog();
                }
            });
        }
    }

    @Synthetic void dismissAllFilesAccessDialog() {
        if (mAskForAllFilesAccessDialog != null) {
            mAskForAllFilesAccessDialog.dismiss();
            mAskForAllFilesAccessDialog = null;
        }
    }

    private void onStoragePermissionDenied() {
        Toast.makeText(mChain.host, R.string.storagePermissionsNotGranted, Toast.LENGTH_SHORT).show();
        mChain.finish();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        App app = App.getInstance(mChain.host);
        switch (requestCode) {
            case REQUEST_CODE_ALL_FILES_ACCESS_PERMISSION:
                if (app.hasAllFilesAccess()) {
                    dismissAllFilesAccessDialog();
                    mChain.pass(this);
                }
                break;
            case AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE:
                // Do something after user returned from app settings screen.
                if (data.getBooleanExtra(Consts.KEY_FROM_APP_DETAILS_SETTING_SCREEN, false)) {
                    if (app.hasStoragePermission()) {
                        onStoragePermissionGranted();
                    } else {
                        checkStoragePermission();
                    }
                    // User clicked the negative button on AppSettingsDialog
                } else {
                    onStoragePermissionDenied();
                }
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // EasyPermissions handles the request result.
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> perms) {
        if (requestCode == REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION) {
            // (Optional) Check whether the user denied any permissions the LAST time
            // the permission request dialog was shown and checked "NEVER ASK AGAIN". This
            // will display a dialog directing them to enable the permission in app settings.
            if (!mStoragePermissionRationaleShown
                    && EasyPermissions.somePermissionPermanentlyDenied(mChain.host, perms)) {
                AppSettingsDialog asd = new AppSettingsDialog.Builder(mChain.host)
                        .setThemeResId(R.style.DialogStyle_MinWidth)
                        .setTitle(R.string.permissionsRequired)
                        .setRationale(R.string.rationale_askExternalStoragePermissionAgain)
                        .setNegativeButton(R.string.cancel)
                        .setPositiveButton(R.string.ok)
                        .build();
                //noinspection RestrictedApi
                mChain.host.startActivityForResult(
                        AppSettingsDialogHolderActivity2.createShowDialogIntent(mChain.host, asd),
                        AppSettingsDialog.DEFAULT_SETTINGS_REQ_CODE);
            } else {
                onStoragePermissionDenied();
            }
        }
    }

    @Override
    public void onRationaleDenied(int requestCode) {
        if (requestCode == REQUEST_CODE_WRITE_EXTERNAL_STORAGE_PERMISSION) {
            onStoragePermissionDenied();
        }
    }
}
