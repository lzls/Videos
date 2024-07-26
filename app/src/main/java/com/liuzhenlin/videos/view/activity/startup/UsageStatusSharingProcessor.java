/*
 * Created on 2024-7-18 4:07:35 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity.startup;

import android.app.Activity;
import android.content.DialogInterface;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LifecycleOwner;

import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.dao.AppPrefs;

public class UsageStatusSharingProcessor<H extends Activity & LifecycleOwner>
        extends BaseProcessor<H> {

    @Override
    public boolean process(@NonNull LaunchChain<H> chain) {
        if (!super.process(chain)) {
            AppPrefs appPrefs = AppPrefs.getSingleton(chain.host);
            if (appPrefs.shouldShowUsageStatusSharingDialog()) {
                DialogInterface.OnClickListener listener = (dialog, which) -> {
                    appPrefs.edit().setShowUsageStatusSharingDialog(false).apply();
                    appPrefs.setUsageStatusSharingAgreed(which == DialogInterface.BUTTON_POSITIVE);
                    dialog.dismiss();
                    chain.pass(this);
                };
                new AlertDialog.Builder(chain.host, R.style.DialogStyle_MinWidth)
                        .setTitle(R.string.title_usageStatusSharing)
                        .setMessage(R.string.rationale_usageStatusSharing)
                        .setNegativeButton(R.string.notShare, listener)
                        .setPositiveButton(R.string.share2, listener)
                        .setCancelable(false)
                        .show();
            } else {
                chain.pass(this);
            }
        }
        return true;
    }
}
