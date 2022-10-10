/*
 * Created on 2022-2-28 3:24:47 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.common.utils;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.util.Log;
import android.util.Rational;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.IconCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.liuzhenlin.common.BuildConfig;
import com.liuzhenlin.common.Consts;
import com.liuzhenlin.common.R;

import java.util.LinkedList;
import java.util.List;

public class PictureInPictureHelper {

    private static final String TAG = "PictureInPictureHelper";

    /** The arguments to be used for Picture-in-Picture mode. */
    private PictureInPictureParams.Builder mPipParamsBuilder;

    /** A {@link BroadcastReceiver} to receive action item events from Picture-in-Picture mode. */
    private BroadcastReceiver mReceiver;

    /** Intent action for media controls from Picture-in-Picture mode. */
    private static final String ACTION_MEDIA_CONTROL = "media_control";

    /** Intent extra for media controls from Picture-in-Picture mode. */
    private static final String EXTRA_PIP_ACTION = "PiP_action";

    /** The intent extra value for play action. */
    public static final int PIP_ACTION_PLAY = 1;
    /** The intent extra value for pause action. */
    public static final int PIP_ACTION_PAUSE = 1 << 1;
    /** The intent extra value for pause action. */
    public static final int PIP_ACTION_FAST_FORWARD = 1 << 2;
    /** The intent extra value for pause action. */
    public static final int PIP_ACTION_FAST_REWIND = 1 << 3;

    /** The request code for play action PendingIntent. */
    private static final int REQUEST_PLAY = 1;
    /** The request code for pause action PendingIntent. */
    private static final int REQUEST_PAUSE = 2;
    /** The request code for fast forward action PendingIntent. */
    private static final int REQUEST_FAST_FORWARD = 3;
    /** The request code for fast rewind action PendingIntent. */
    private static final int REQUEST_FAST_REWIND = 4;

    public static final int SDK_VERSION_SUPPORTS_RESIZABLE_PIP = Build.VERSION_CODES.O;
    public static final int SDK_VERSION_SUPPORTS_PIP = Build.VERSION_CODES.N;

    @Synthetic final AppCompatActivity mActivity;
    @Nullable @Synthetic Adapter mAdapter;

    private final String mPlay;
    private final String mPause;
    private final String mFastForward;
    private final String mFastRewind;

    private View.OnLayoutChangeListener mOnPipLayoutChangeListener;

    private boolean mActivityManifestDefinedSupportsPipChecked;
    private boolean mActivityManifestDefinedSupportsPiP;

    public PictureInPictureHelper(@NonNull AppCompatActivity activity) {
        mActivity = activity;
        activity.getLifecycle().addObserver((LifecycleEventObserver) (source, event) -> {
            if (event == Lifecycle.Event.ON_DESTROY) {
                if (mReceiver != null) {
                    activity.unregisterReceiver(mReceiver);
                    mReceiver = null;
                }
            }
        });

        mPlay = activity.getString(R.string.play);
        mPause = activity.getString(R.string.pause);
        mFastForward = activity.getString(R.string.fastForward);
        mFastRewind = activity.getString(R.string.fastRewind);
    }

    public void setAdapter(@Nullable Adapter adapter) {
        if (mAdapter != adapter) {
            View.OnLayoutChangeListener listener = mOnPipLayoutChangeListener;
            if (listener != null) {
                removeOnPipLayoutChangeListener(mAdapter);
                setOnPipLayoutChangeListener(adapter, listener);
            }
            mAdapter = adapter;
        }
    }

    @Nullable
    public Adapter getAdapter() {
        return mAdapter;
    }

    @RequiresApi(SDK_VERSION_SUPPORTS_RESIZABLE_PIP)
    @NonNull
    public PictureInPictureParams.Builder getPipParamsBuilder() {
        if (mPipParamsBuilder == null) {
            mPipParamsBuilder = new PictureInPictureParams.Builder();
        }
        return mPipParamsBuilder;
    }

    /**
     * Update the action items in Picture-in-Picture mode.
     */
    @RequiresApi(SDK_VERSION_SUPPORTS_RESIZABLE_PIP)
    public void updatePictureInPictureActions(int pipActions) {
        List<RemoteAction> actions = new LinkedList<>();
        if ((pipActions & PIP_ACTION_FAST_REWIND) != 0) {
            actions.add(
                    createPipAction(R.drawable.ic_fast_rewind_white_24dp, mFastRewind,
                            PIP_ACTION_FAST_REWIND, REQUEST_FAST_REWIND));
        }
        if ((pipActions & PIP_ACTION_PLAY) != 0) {
            actions.add(
                    createPipAction(R.drawable.ic_play_white_24dp, mPlay,
                            PIP_ACTION_PLAY, REQUEST_PLAY));

        } else if ((pipActions & PIP_ACTION_PAUSE) != 0) {
            actions.add(
                    createPipAction(R.drawable.ic_pause_white_24dp, mPause,
                            PIP_ACTION_PAUSE, REQUEST_PAUSE));
        }
        if ((pipActions & PIP_ACTION_FAST_FORWARD) != 0) {
            actions.add(
                    createPipAction(R.drawable.ic_fast_forward_white_24dp, mFastForward,
                            PIP_ACTION_FAST_FORWARD, REQUEST_FAST_FORWARD));
        } else {
            RemoteAction action =
                    createPipAction(R.drawable.ic_fast_forward_darkerlightgray_24dp, mFastForward,
                            PIP_ACTION_FAST_FORWARD, REQUEST_FAST_FORWARD);
            action.setEnabled(false);
            actions.add(action);
        }
        // This is how you can update action items (or aspect ratio) for Picture-in-Picture mode.
        mActivity.setPictureInPictureParams(getPipParamsBuilder().setActions(actions).build());
    }

    /**
     * Create an pip action item in Picture-in-Picture mode.
     *
     * @param iconId      The icon to be used.
     * @param title       The title text.
     * @param pipAction   The type for the pip action. May be {@link #PIP_ACTION_PLAY},
     *                    {@link #PIP_ACTION_PAUSE},
     *                    {@link #PIP_ACTION_FAST_FORWARD},
     *                    or {@link #PIP_ACTION_FAST_REWIND}.
     * @param requestCode The request code for the {@link PendingIntent}.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private RemoteAction createPipAction(
            @DrawableRes int iconId, String title, int pipAction, int requestCode) {
        // This is the PendingIntent that is invoked when a user clicks on the action item.
        // You need to use distinct request codes for play, pause, fast forward, and fast rewind,
        // or the PendingIntent won't be properly updated.
        PendingIntent intent = PendingIntent.getBroadcast(
                mActivity,
                requestCode,
                new Intent(ACTION_MEDIA_CONTROL).putExtra(EXTRA_PIP_ACTION, pipAction),
                Consts.PENDING_INTENT_FLAG_IMMUTABLE);
        Icon icon = IconCompat.createWithResource(mActivity, iconId).toIcon(mActivity);
        return new RemoteAction(icon, title, title, intent);
    }

    @RequiresApi(SDK_VERSION_SUPPORTS_PIP)
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            // Starts receiving events from action items in PiP mode.
            mReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (mAdapter == null
                            || intent == null || !ACTION_MEDIA_CONTROL.equals(intent.getAction())) {
                        return;
                    }

                    // This is where we are called back from Picture-in-Picture action items.
                    final int action = intent.getIntExtra(EXTRA_PIP_ACTION, 0);
                    switch (action) {
                        case PIP_ACTION_PLAY:
                            mAdapter.onTapPlay();
                            break;
                        case PIP_ACTION_PAUSE:
                            mAdapter.onTapPause();
                            break;
                        case PIP_ACTION_FAST_REWIND: {
                            mAdapter.onTapFastRewind();
                        }
                        break;
                        case PIP_ACTION_FAST_FORWARD: {
                            mAdapter.onTapFastForward();
                        }
                        break;
                    }
                }
            };
            mActivity.registerReceiver(mReceiver, new IntentFilter(ACTION_MEDIA_CONTROL));

            setOnPipLayoutChangeListener(mAdapter, mOnPipLayoutChangeListener);
        } else {
            // We are out of PiP mode. We can stop receiving events from it.
            if (mReceiver != null) {
                mActivity.unregisterReceiver(mReceiver);
                mReceiver = null;
            }
            removeOnPipLayoutChangeListener(mAdapter);
        }
    }

    private void setOnPipLayoutChangeListener(Adapter adapter, View.OnLayoutChangeListener listener) {
        if (Build.VERSION.SDK_INT < SDK_VERSION_SUPPORTS_RESIZABLE_PIP
                || adapter == null
                || adapter.getVideoView() == null) {
            return;
        }

        mOnPipLayoutChangeListener = listener;
        if (mOnPipLayoutChangeListener == null) {
            mOnPipLayoutChangeListener = new View.OnLayoutChangeListener() {
                static final float RATIO_TOLERANCE_PIP_LAYOUT_SIZE = 5.0f / 3.0f;

                final float ratioOfProgressHeightToVideoSize;
                final int progressMinHeight;
                final int progressMaxHeight;

                float cachedVideoAspectRatio;
                int cachedSize = -1;

                /* anonymous class initializer */ {
                    // 1dp -> 2.75px (5.5inch  w * h = 1080 * 1920)
                    final float dp = mActivity.getResources().getDisplayMetrics().density;
                    ratioOfProgressHeightToVideoSize = 1.0f / (12121.2f * dp); // 1 : 33333.3 (px)
                    progressMinHeight = Utils.roundFloat(dp * 1.8f); // 5.45px -> 5px
                    progressMaxHeight = Utils.roundFloat(dp * 2.5f); // 7.375px -> 7px
                    if (BuildConfig.DEBUG) {
                        Log.i(TAG, "ratioOfProgressHeightToVideoSize = " + ratioOfProgressHeightToVideoSize
                                + "    " + "progressMinHeight = " + progressMinHeight
                                + "    " + "progressMaxHeight = " + progressMaxHeight);
                    }
                }

                @Override
                public void onLayoutChange(
                        View v,
                        int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    if (mAdapter == null) return;

                    final int videoWidth = mAdapter.getVideoWidth();
                    final int videoHeight = mAdapter.getVideoHeight();

                    if (videoWidth == 0 || videoHeight == 0) return;

                    final float videoAspectRatio = (float) videoWidth / videoHeight;
                    final int width = right - left;
                    final int height = Utils.roundFloat(width / videoAspectRatio);
                    final int size = width * height;
                    final float sizeRatio = (float) size / cachedSize;

                    if (videoAspectRatio != cachedVideoAspectRatio
                            || sizeRatio > RATIO_TOLERANCE_PIP_LAYOUT_SIZE
                            || sizeRatio < 1.0f / RATIO_TOLERANCE_PIP_LAYOUT_SIZE) {
                        final int progressHeight = Math.max(
                                progressMinHeight,
                                Math.min(progressMaxHeight,
                                        Utils.roundFloat(size * ratioOfProgressHeightToVideoSize)));
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG, "sizeRatio = " + sizeRatio
                                    + "    " + "progressHeight = " + progressHeight
                                    + "    " + "size / 1.8dp = " + size / progressMinHeight
                                    + "    " + "size / 2.5dp = " + size / progressMaxHeight);
                        }

                        //noinspection NewApi
                        mActivity.setPictureInPictureParams(
                                getPipParamsBuilder()
                                        .setAspectRatio(new Rational(width, height + progressHeight))
                                        .build());

                        cachedVideoAspectRatio = videoAspectRatio;
                        cachedSize = size;
                    }
                }
            };
        }
        adapter.getVideoView().addOnLayoutChangeListener(mOnPipLayoutChangeListener);
    }

    private void removeOnPipLayoutChangeListener(Adapter adapter) {
        if (mOnPipLayoutChangeListener != null && adapter != null && adapter.getVideoView() != null) {
            adapter.getVideoView().removeOnLayoutChangeListener(mOnPipLayoutChangeListener);
            mOnPipLayoutChangeListener = null;
        }
    }

    public boolean doesSdkVersionSupportPiP() {
        return Build.VERSION.SDK_INT >=
                (mAdapter != null && mAdapter.shouldOnlySupportResizablePiP() ?
                        SDK_VERSION_SUPPORTS_RESIZABLE_PIP : SDK_VERSION_SUPPORTS_PIP);
    }

    @SuppressLint("InlinedApi")
    public boolean supportsPictureInPictureMode() {
        PackageManager pm = mActivity.getPackageManager();
        return doesSdkVersionSupportPiP()
                && pm.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
                && doesActivityManifestDefinedSupportPiP();
    }

    private boolean doesActivityManifestDefinedSupportPiP() {
        if (!mActivityManifestDefinedSupportsPipChecked) {
            final PackageManager pm = mActivity.getPackageManager();
            if (pm == null) {
                // If we don't have a PackageManager, return false. Don't set
                // the checked flag though so we still check again later
                return false;
            }
            try {
                int flags = 0;
                // On newer versions of the OS we need to pass direct boot
                // flags so that getActivityInfo doesn't crash under strict
                // mode checks
                if (Build.VERSION.SDK_INT >= 29) {
                    flags = PackageManager.MATCH_DIRECT_BOOT_AUTO
                            | PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
                } else if (Build.VERSION.SDK_INT >= 24) {
                    flags = PackageManager.MATCH_DIRECT_BOOT_AWARE
                            | PackageManager.MATCH_DIRECT_BOOT_UNAWARE;
                }
                final ActivityInfo info = pm.getActivityInfo(
                        new ComponentName(mActivity, mActivity.getClass()), flags);
                mActivityManifestDefinedSupportsPiP =
                        (info.flags & 0x400000 /* FLAG_SUPPORTS_PICTURE_IN_PICTURE */) != 0;
            } catch (PackageManager.NameNotFoundException e) {
                // This shouldn't happen but let's not crash because of it, we'll just log and
                // return false (since most apps won't be handling it)
                Log.d(TAG, "Exception while getting ActivityInfo", e);
                mActivityManifestDefinedSupportsPiP = false;
            }
        }
        // Flip the checked flag so we don't check again
        mActivityManifestDefinedSupportsPipChecked = true;

        return mActivityManifestDefinedSupportsPiP;
    }

    public static abstract class Adapter {

        public boolean shouldOnlySupportResizablePiP() {
            return false;
        }

        @Nullable
        public View getVideoView() {
            return null;
        }

        public int getVideoWidth() {
            return 0;
        }

        public int getVideoHeight() {
            return 0;
        }

        public void onTapPlay() {
        }

        public void onTapPause() {
        }

        public void onTapFastForward() {
        }

        public void onTapFastRewind() {
        }
    }
}
