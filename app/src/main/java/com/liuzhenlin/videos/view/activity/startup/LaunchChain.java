/*
 * Created on 2023-5-18 2:52:14 PM.
 * Copyright © 2023 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity.startup;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.common.utils.Synthetic;

import java.util.ArrayList;
import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;

@NonNullApi
public class LaunchChain<H extends Activity & LifecycleOwner>
        implements EasyPermissions.PermissionCallbacks, EasyPermissions.RationaleCallbacks {

    public final H host;
    private final List<Processor<H>> mProcessors;
    private int mIndex;
    private boolean mStarted;

    @Synthetic LaunchChain(H host, List<Processor<H>> processors) {
        this.host = host;
        mProcessors = processors;
    }

    public static class Builder<H extends Activity & LifecycleOwner> {
        private final H mHost;
        private final List<Processor<H>> mProcessors = new ArrayList<>();

        public Builder(H host) {
            mHost = host;
        }

        public Builder<H> processor(Processor<H> processor) {
            mProcessors.add(processor);
            return this;
        }

        public LaunchChain<H> build() {
            return new LaunchChain<>(mHost, mProcessors);
        }
    }

    public void start() {
        if (!mStarted && mIndex < mProcessors.size()) {
            mStarted = true;
            getCurrentProcessor().process(this);
        }
    }

    public void pass(Processor<H> processor) {
        int index = mProcessors.indexOf(processor);
        if (mIndex <= index) {
            if (mIndex < mProcessors.size() - 1) {
                mIndex = index + 1;
                getCurrentProcessor().process(this);
            } else {
                finish();
            }
        }
    }

    public void finish() {
        if (mStarted) {
            mStarted = false;
            mIndex = mProcessors.size();
            host.finish();
        }
    }

    private Processor<H> getCurrentProcessor() {
        return mProcessors.get(mIndex);
    }

    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        getCurrentProcessor().onActivityResult(requestCode, resultCode, data);
    }

    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        getCurrentProcessor().onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onPermissionsGranted(int requestCode, List<String> perms) {
        getCurrentProcessor().onPermissionsGranted(requestCode, perms);
    }

    @Override
    public void onPermissionsDenied(int requestCode, List<String> perms) {
        getCurrentProcessor().onPermissionsDenied(requestCode, perms);
    }

    @Override
    public void onRationaleAccepted(int requestCode) {
        getCurrentProcessor().onRationaleAccepted(requestCode);
    }

    @Override
    public void onRationaleDenied(int requestCode) {
        getCurrentProcessor().onRationaleDenied(requestCode);
    }

    public interface Processor<H extends Activity & LifecycleOwner>
            extends EasyPermissions.PermissionCallbacks, EasyPermissions.RationaleCallbacks {
        boolean process(LaunchChain<H> chain);

        default void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        }

        default void onRequestPermissionsResult(
                int requestCode, String[] permissions, int[] grantResults) {
        }

        @Override
        default void onPermissionsGranted(int requestCode, List<String> perms) {
        }

        @Override
        default void onPermissionsDenied(int requestCode, List<String> perms) {
        }

        @Override
        default void onRationaleAccepted(int requestCode) {
        }

        @Override
        default void onRationaleDenied(int requestCode) {
        }
    }
}
