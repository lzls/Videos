/*
 * Created on 2017/09/24.
 * Copyright © 2017–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Rational;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatDelegateWrapper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.util.Synthetic;
import com.google.android.material.snackbar.Snackbar;
import com.liuzhenlin.common.observer.OnOrientationChangeListener;
import com.liuzhenlin.common.observer.RotationObserver;
import com.liuzhenlin.common.utils.DisplayCutoutManager;
import com.liuzhenlin.common.utils.FileUtils;
import com.liuzhenlin.common.utils.PictureInPictureHelper;
import com.liuzhenlin.common.utils.SystemBarUtils;
import com.liuzhenlin.common.utils.UiUtils;
import com.liuzhenlin.common.utils.Utils;
import com.liuzhenlin.swipeback.SwipeBackLayout;
import com.liuzhenlin.texturevideoview.ExoVideoPlayer;
import com.liuzhenlin.texturevideoview.IVideoPlayer;
import com.liuzhenlin.texturevideoview.IjkVideoPlayer;
import com.liuzhenlin.texturevideoview.TextureVideoView;
import com.liuzhenlin.texturevideoview.VideoPlayer;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.Consts;
import com.liuzhenlin.videos.Files;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.bean.Video;
import com.liuzhenlin.videos.presenter.IVideoPresenter;
import com.liuzhenlin.videos.utils.VideoUtils2;
import com.liuzhenlin.videos.web.youtube.WebService;

import java.io.File;
import java.lang.ref.WeakReference;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_FAST_FORWARD;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_FAST_REWIND;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_PAUSE;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_PLAY;
import static com.liuzhenlin.texturevideoview.utils.Utils.canUseExoPlayer;

/**
 * @author 刘振林
 */
public class VideoActivity extends BaseActivity implements IVideoView,
        DisplayCutoutManager.OnNotchSwitchListener {

    private static WeakReference<VideoActivity> sActivityInPiP;

    private View mStatusBarView;
    @Synthetic TextureVideoView mVideoView;
    private ImageView mLockUnlockOrientationButton;

    @Synthetic IVideoPlayer mVideoPlayer;
    @Synthetic final IVideoPresenter mPresenter = IVideoPresenter.newInstance();

    @Synthetic int mVideoWidth;
    @Synthetic int mVideoHeight;

    @Synthetic int mPrivateFlags;

    private static final int PFLAG_SCREEN_NOTCH_HIDDEN = 1;

    private static final int PFLAG_DEVICE_SCREEN_ROTATION_ENABLED = 1 << 1;
    private static final int PFLAG_SCREEN_ORIENTATION_LOCKED = 1 << 2;
    private static final int PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE = 1 << 3;

    private static final int PFLAG_LAST_VIDEO_LAYOUT_IS_FULLSCREEN = 1 << 4;

    private static final int PFLAG_STOPPED = 1 << 5;

    private static int sStatusHeight;
    private static int sStatusHeightInLandscapeOfNotchSupportDevice;
    private DisplayCutoutManager mDisplayCutoutManager;

    private RotationObserver mRotationObserver;
    @Synthetic OnOrientationChangeListener mOnOrientationChangeListener;
    @Synthetic int mDeviceOrientation = SCREEN_ORIENTATION_PORTRAIT;
    @Synthetic int mScreenOrientation = SCREEN_ORIENTATION_PORTRAIT;

    @Synthetic Handler mHandler;

    private final Runnable mHideLockUnlockOrientationButtonRunnable =
            () -> showLockUnlockOrientationButton(false);
    private static final int DELAY_TIME_HIDE_LOCK_UNLOCK_ORIENTATION_BUTTON = 2500;

    private String mLockOrientation;
    private String mUnlockOrientation;

    @Synthetic ProgressBar mVideoProgressInPiP;

    @Synthetic RefreshVideoProgressInPiPTask mRefreshVideoProgressInPiPTask;

    private final class RefreshVideoProgressInPiPTask {
        RefreshVideoProgressInPiPTask() {
        }

        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                final int progress = mVideoPlayer.getVideoProgress();
                if (mVideoPlayer.isPlaying()) {
                    getHandler().postDelayed(this, 1000 - progress % 1000);
                }
                mVideoProgressInPiP.setSecondaryProgress(mVideoPlayer.getVideoBufferProgress());
                mVideoProgressInPiP.setProgress(progress);
            }
        };

        void execute() {
            cancel();
            runnable.run();
        }

        void cancel() {
            if (mHandler != null) {
                mHandler.removeCallbacks(runnable);
            }
        }
    }

    @Synthetic Handler getHandler() {
        Handler handler = mHandler;
        if (handler == null) {
            handler = getWindow().getDecorView().getHandler();
            if (handler == null) {
                handler = com.liuzhenlin.common.Consts.getMainThreadHandler();
            }
            mHandler = handler;
        }
        return handler;
    }

    @Synthetic PictureInPictureHelper getPipHelper() {
        AppCompatDelegateWrapper delegate = getDelegate();
        PictureInPictureHelper pipHelper = delegate.getPipHelper();
        if (pipHelper == null) {
            pipHelper = new PictureInPictureHelper(this);
            pipHelper.setAdapter(new PictureInPictureHelper.Adapter() {
                @Override
                public boolean shouldOnlySupportResizablePiP() {
                    return true;
                }

                @Override
                public View getVideoView() {
                    return mVideoView;
                }

                @Override
                public int getVideoWidth() {
                    return mVideoWidth;
                }

                @Override
                public int getVideoHeight() {
                    return mVideoHeight;
                }

                @Override
                public void onTapPlay() {
                    mVideoPlayer.play(true);
                }

                @Override
                public void onTapPause() {
                    mVideoPlayer.pause(true);
                }

                @Override
                public void onTapFastForward() {
                    mVideoPlayer.fastForward(true);
                }

                @Override
                public void onTapFastRewind() {
                    mVideoPlayer.fastRewind(true);
                }
            });
            delegate.setPipHelper(pipHelper);
        }
        return pipHelper;
    }

    private DisplayCutoutManager getDisplayCutoutManager() {
        if (mDisplayCutoutManager == null) {
            mDisplayCutoutManager = new DisplayCutoutManager(getWindow());
        }
        return mDisplayCutoutManager;
    }

    @Nullable
    @Override
    public Activity getPreviousActivity() {
        return MainActivity.this$;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        mLockOrientation = getString(R.string.lockScreenOrientation);
        mUnlockOrientation = getString(R.string.unlockScreenOrientation);
        if (sStatusHeight == 0) {
            sStatusHeight = App.getInstance(newBase).getStatusHeightInPortrait();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPresenter.attachToView(this);
        if (mPresenter.initPlaylist(savedInstanceState, getIntent())) {
            setRequestedOrientation(mScreenOrientation);
            setContentView(R.layout.activity_video);
            initViews(savedInstanceState);
        } else {
            Activity preactivity = getPreviousActivity();
            if (preactivity == null) {
                showToast(this, R.string.cannotPlayThisVideo, Toast.LENGTH_LONG);
            } else {
                UiUtils.showUserCancelableSnackbar(preactivity.getWindow().getDecorView(),
                        R.string.cannotPlayThisVideo, Snackbar.LENGTH_LONG);
            }
            scrollToFinish();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (mPresenter.initPlaylistAndRecordCurrentVideoProgress(null, intent)) {
            super.onNewIntent(intent);
            setIntent(intent);

            final boolean needPlaylist = mPresenter.getPlaylistSize() > 1;
            //noinspection rawtypes
            TextureVideoView.PlayListAdapter adapter = mVideoView.getPlayListAdapter();
            if (needPlaylist && adapter != null) {
                //noinspection NotifyDataSetChanged
                adapter.notifyDataSetChanged();
            }
            //noinspection unchecked
            mVideoView.setPlayListAdapter(needPlaylist
                    ? adapter == null ? mPresenter.newPlaylistAdapter() : adapter
                    : null);
            mVideoView.setCanSkipToPrevious(needPlaylist);
            mVideoView.setCanSkipToNext(needPlaylist);

            mPresenter.playCurrentVideo();
        }
    }

    private void initViews(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mStatusBarView = findViewById(R.id.view_statusBar);
            ViewGroup.LayoutParams lp = mStatusBarView.getLayoutParams();
            if (lp.height != sStatusHeight) {
                lp.height = sStatusHeight;
                mStatusBarView.setLayoutParams(lp);
            }

            mVideoProgressInPiP = findViewById(R.id.pbInPiP_videoProgress);
        }

        mVideoView = findViewById(R.id.videoview);
        VideoPlayer videoPlayer = canUseExoPlayer()
                ? new ExoVideoPlayer(this) : new IjkVideoPlayer(this);
        mVideoPlayer = videoPlayer;
        videoPlayer.setVideoView(mVideoView);
        mVideoView.setVideoPlayer(videoPlayer);

        mLockUnlockOrientationButton = findViewById(R.id.btn_lockUnlockOrientation);
        mLockUnlockOrientationButton.setOnClickListener(v ->
                setScreenOrientationLocked((mPrivateFlags & PFLAG_SCREEN_ORIENTATION_LOCKED) == 0));

        if (mPresenter.getPlaylistSize() > 1) {
            mVideoView.setPlayListAdapter(mPresenter.newPlaylistAdapter());
            mVideoView.setCanSkipToPrevious(true);
            mVideoView.setCanSkipToNext(true);
        }
        // Ensures the list scrolls to the position of the video to be played
        final int position = mPresenter.getCurrentVideoPositionInList();
        if (savedInstanceState == null && position != 0) {
            notifyPlaylistSelectionChanged(0, position, true);
        }
        mPresenter.playCurrentVideo();
        videoPlayer.addVideoListener(new IVideoPlayer.VideoListener() {
            @Override
            public void onVideoStarted() {
                mPresenter.onCurrentVideoStarted();

                if (mVideoWidth == 0 && mVideoHeight == 0) {
                    mVideoWidth = mVideoPlayer.getVideoWidth();
                    mVideoHeight = mVideoPlayer.getVideoHeight();
                }

                if (isInPictureInPictureMode()) {
                    // We are playing the video now. In PiP mode, we want to show several
                    // action items to fast rewind, pause and fast forward the video.
                    //noinspection NewApi
                    getPipHelper().updatePictureInPictureActions(PIP_ACTION_FAST_REWIND
                            | PIP_ACTION_PAUSE | PIP_ACTION_FAST_FORWARD);

                    if (mRefreshVideoProgressInPiPTask != null) {
                        mRefreshVideoProgressInPiPTask.execute();
                    }
                }
            }

            @Override
            public void onVideoStopped() {
                // The video stopped or reached the playlist end. In PiP mode, we want to show some
                // action items to fast rewind, play and fast forward the video.
                if (isInPictureInPictureMode()) {
                    int actions = PIP_ACTION_FAST_REWIND | PIP_ACTION_PLAY;
                    if (!(mVideoPlayer.getPlaybackState() == IVideoPlayer.PLAYBACK_STATE_COMPLETED
                            && !mVideoView.canSkipToNext())) {
                        actions |= PIP_ACTION_FAST_FORWARD;
                    }
                    //noinspection NewApi
                    getPipHelper().updatePictureInPictureActions(actions);
                }
            }

            @Override
            public void onVideoDurationChanged(int duration) {
                if (mVideoProgressInPiP != null && mVideoProgressInPiP.getMax() != duration) {
                    mVideoProgressInPiP.setMax(duration);
                }
            }

            @SuppressLint("SourceLockedOrientationActivity")
            @Override
            public void onVideoSizeChanged(int width, int height) {
                mVideoWidth = width;
                mVideoHeight = height;

                if (width == 0 && height == 0) return;
                if (width > height) {
                    mPrivateFlags &= ~PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE;
                    if (mScreenOrientation == SCREEN_ORIENTATION_PORTRAIT
                            && mVideoView.isInFullscreenMode()) {
                        mScreenOrientation = mDeviceOrientation == SCREEN_ORIENTATION_PORTRAIT
                                ? SCREEN_ORIENTATION_LANDSCAPE : mDeviceOrientation;
                        setRequestedOrientation(mScreenOrientation);
                        setFullscreenMode(true);
                    }
                } else {
                    mPrivateFlags |= PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE;
                    if (mScreenOrientation != SCREEN_ORIENTATION_PORTRAIT) {
                        mScreenOrientation = SCREEN_ORIENTATION_PORTRAIT;
                        setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
                        setFullscreenMode(true);
                    }
                }
            }
        });
        videoPlayer.setOnSkipPrevNextListener(new VideoPlayer.OnSkipPrevNextListener() {
            @Override
            public void onSkipToPrevious() {
                mPresenter.skipToPreviousVideo();
            }

            @Override
            public void onSkipToNext() {
                mPresenter.skipToNextVideo();
            }
        });
        mVideoView.setEventListener(new TextureVideoView.EventListener() {

            @Override
            public void onPlayerChange(@Nullable VideoPlayer videoPlayer) {
                mVideoPlayer = videoPlayer;
            }

            @Override
            public void onReturnClicked() {
                if (mVideoView.isInFullscreenMode()) {
                    setFullscreenModeManually(false);
                } else {
                    scrollToFinish();
                }
            }

            @Override
            public void onBackgroundPlaybackControllerClose() {
                scrollToFinish();
            }

            public void onViewModeChange(int oldMode, int newMode, boolean layoutMatches) {
                switch (newMode) {
                    case TextureVideoView.VIEW_MODE_MINIMUM:
                        if (!layoutMatches
                                && getPipHelper().supportsPictureInPictureMode()
                                && mVideoWidth != 0 && mVideoHeight != 0) {
                            WebService.bind(VideoActivity.this, webService -> {
                                if (isFinishing()) {
                                    return;
                                }
                                try {
                                    webService.finishYoutubePlaybackActivityIfItIsInPiP();
                                } catch (RemoteException e) {
                                    e.printStackTrace();
                                }
                                finishOtherInstanceInPiP();
                                //noinspection NewApi
                                enterPictureInPictureMode(
                                        getPipHelper().getPipParamsBuilder()
                                                .setAspectRatio(new Rational(mVideoWidth, mVideoHeight))
                                                .build());
                            });
                        }
                        break;
                    case TextureVideoView.VIEW_MODE_DEFAULT:
                        setFullscreenModeManually(false);
                        break;
                    case TextureVideoView.VIEW_MODE_LOCKED_FULLSCREEN:
                    case TextureVideoView.VIEW_MODE_VIDEO_STRETCHED_LOCKED_FULLSCREEN:
                        showLockUnlockOrientationButton(false);
                    case TextureVideoView.VIEW_MODE_VIDEO_STRETCHED_FULLSCREEN:
                    case TextureVideoView.VIEW_MODE_FULLSCREEN:
                        setFullscreenModeManually(true);
                        break;
                }
            }

            @Override
            public void onShareVideo() {
                Context context = VideoActivity.this;
                mPresenter.shareCurrentVideo(context);
            }

            @Override
            public void onShareCapturedVideoPhoto(@NonNull File photo) {
                Context context = VideoActivity.this;
                mPresenter.shareCapturedVideoPhoto(context, photo);
            }
        });
        mVideoView.setOpCallback(new TextureVideoView.OpCallback() {
            @NonNull
            @Override
            public Window getWindow() {
                return VideoActivity.this.getWindow();
            }

            @NonNull
            @Override
            public Class<? extends Activity> getHostActivityClass() {
                return VideoActivity.this.getClass();
            }

            @NonNull
            @Override
            public String getAppExternalFilesDir() {
                return Files.getAppExternalFilesDir().getPath();
            }
        });
    }

    @Override
    public void setVideoToPlay(@NonNull Video video) {
        mVideoPlayer.setVideoPath(video.getPath());
        mVideoView.setTitle(FileUtils.getFileTitleFromFileName(video.getName()));
    }

    @Override
    public void seekPositionOnVideoStarted(int position) {
        // Restores the playback position saved at the last time
        mVideoPlayer.seekTo(position, false);
    }

    @Override
    public int getPlayingVideoProgress() {
        return mVideoPlayer.getVideoProgress();
    }

    @Override
    protected void onStart() {
        super.onStart();
        finishOtherInstanceInPiP();
    }

    @Synthetic void finishOtherInstanceInPiP() {
        if (sActivityInPiP != null) {
            VideoActivity activity = sActivityInPiP.get();
            if (activity != this) {
                sActivityInPiP.clear();
                sActivityInPiP = null;
                if (activity != null) {
                    activity.finish();
                }
            }
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        mPrivateFlags &= ~PFLAG_STOPPED;

        if (!isInPictureInPictureMode()) {
            observeNotchSwitch(true);
            setAutoRotationEnabled(true);
        }

        mVideoPlayer.openVideo();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        finishOtherInstanceInPiP();

        Window window = getWindow();
        View decorView = window.getDecorView();
        boolean inPictureInPictureMode = isInPictureInPictureMode();

        getDisplayCutoutManager().setLayoutInDisplayCutout(true);
        if (!inPictureInPictureMode) {
            observeNotchSwitch(true);
        }

        if (Utils.isLayoutRtl(decorView)) {
            getSwipeBackLayout().setEnabledEdges(SwipeBackLayout.EDGE_RIGHT);
        }
        setFullscreenMode(mVideoView.isInFullscreenMode());
        if (inPictureInPictureMode) {
            mStatusBarView.setVisibility(View.GONE);
        }

        mRotationObserver = new RotationObserver(getHandler(), this) {
            @Override
            public void onRotationChange(boolean selfChange, boolean enabled) {
                //noinspection DoubleNegation
                if (enabled != ((mPrivateFlags & PFLAG_DEVICE_SCREEN_ROTATION_ENABLED) != 0)) {
                    mPrivateFlags ^= PFLAG_DEVICE_SCREEN_ROTATION_ENABLED;
                }
            }
        };
        mOnOrientationChangeListener = new OnOrientationChangeListener(this, mDeviceOrientation) {
            @Override
            public void onOrientationChange(int orientation) {
                if (orientation != SCREEN_ORIENTATION_REVERSE_PORTRAIT) {
                    if (mVideoWidth == 0 && mVideoHeight == 0) {
                        mOnOrientationChangeListener.setOrientation(mDeviceOrientation);
                        return;
                    }
                    mDeviceOrientation = orientation;
                    changeScreenOrientationIfNeeded(orientation);
                }
            }
        };
        if (!inPictureInPictureMode) {
            setAutoRotationEnabled(true);
        }
    }

    private void observeNotchSwitch(boolean observe) {
        if (mDisplayCutoutManager != null) {
            if (observe) {
                mDisplayCutoutManager.addOnNotchSwitchListener(this);
                if (mDisplayCutoutManager.isNotchHidden()
                        ^ ((mPrivateFlags & PFLAG_SCREEN_NOTCH_HIDDEN) != 0)) {
                    mPrivateFlags ^= PFLAG_SCREEN_NOTCH_HIDDEN;
                    resizeVideoView();
                }
            } else {
                mDisplayCutoutManager.removeOnNotchSwitchListener(this);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPrivateFlags |= PFLAG_STOPPED;

        // Saves the video progress when current Activity is put into background
        if (!isFinishing()) {
            mPresenter.recordCurrVideoProgress();
        }

        if (!isInPictureInPictureMode()) {
            observeNotchSwitch(false);
            setAutoRotationEnabled(false);
        }

        mVideoPlayer.closeVideo();
    }

    @Override
    public void finish() {
        mPresenter.recordCurrVideoProgressAndSetResult();
        super.finish();
    }

    @Override
    public void onBackPressed() {
        if (!mVideoView.onBackPressed()) {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mPresenter.detachFromView(this);
        if (mHandler != null) {
            mHandler.removeCallbacks(mHideLockUnlockOrientationButtonRunnable, null);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
                && sStatusHeightInLandscapeOfNotchSupportDevice == 0) {
            sStatusHeightInLandscapeOfNotchSupportDevice = SystemBarUtils.getStatusHeight(this);
            if (mVideoView.isInFullscreenMode()) {
                mVideoView.setFullscreenMode(true, sStatusHeightInLandscapeOfNotchSupportDevice);
            }
        }
    }

    private void setScreenOrientationLocked(boolean locked) {
        if (locked) {
            mPrivateFlags |= PFLAG_SCREEN_ORIENTATION_LOCKED;
            mLockUnlockOrientationButton.setImageResource(R.drawable.ic_unlock);
            mLockUnlockOrientationButton.setContentDescription(mUnlockOrientation);
        } else {
            mPrivateFlags &= ~PFLAG_SCREEN_ORIENTATION_LOCKED;
            mLockUnlockOrientationButton.setImageResource(R.drawable.ic_lock);
            mLockUnlockOrientationButton.setContentDescription(mLockOrientation);
            // Unlock to set the screen orientation to the current device orientation
            changeScreenOrientationIfNeeded(mDeviceOrientation);
        }
    }

    @Synthetic void showLockUnlockOrientationButton(boolean show) {
        mLockUnlockOrientationButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setAutoRotationEnabled(boolean enabled) {
        if (mRotationObserver == null || mOnOrientationChangeListener == null) {
            return;
        }
        if (enabled) {
            mRotationObserver.startObserver();
            mOnOrientationChangeListener.setEnabled(true);
        } else {
            mOnOrientationChangeListener.setEnabled(false);
            mRotationObserver.stopObserver();
        }
    }

    @Synthetic void changeScreenOrientationIfNeeded(int orientation) {
        switch (mScreenOrientation) {
            case SCREEN_ORIENTATION_PORTRAIT:
                if ((mPrivateFlags & PFLAG_DEVICE_SCREEN_ROTATION_ENABLED) != 0
                        && !mVideoView.isLocked()) {
                    if ((mPrivateFlags & PFLAG_SCREEN_ORIENTATION_LOCKED) != 0) {
                        final boolean fullscreen = orientation != SCREEN_ORIENTATION_PORTRAIT;
                        if (fullscreen == mVideoView.isInFullscreenMode()) {
                            return;
                        }
                        break;
                    }

                    if ((mPrivateFlags & PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE) != 0) {
                        final boolean fullscreen = orientation != SCREEN_ORIENTATION_PORTRAIT;
                        if (fullscreen == mVideoView.isInFullscreenMode()) {
                            return;
                        }
                        setFullscreenMode(fullscreen);
                    } else {
                        if (orientation == SCREEN_ORIENTATION_PORTRAIT) {
                            return;
                        }
                        mScreenOrientation = orientation;
                        setRequestedOrientation(orientation);
                        setFullscreenMode(true);
                    }
                    break;
                }
                return;
            case SCREEN_ORIENTATION_LANDSCAPE:
            case SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                if (mScreenOrientation == orientation) {
                    return;
                }
                if (mVideoView.isLocked()) {
                    if (orientation == SCREEN_ORIENTATION_PORTRAIT) {
                        return;
                    }
                } else if ((mPrivateFlags & PFLAG_DEVICE_SCREEN_ROTATION_ENABLED) != 0) {
                    if ((mPrivateFlags & PFLAG_SCREEN_ORIENTATION_LOCKED) != 0
                            && orientation == SCREEN_ORIENTATION_PORTRAIT) {
                        break;
                    }
                } else if (orientation == SCREEN_ORIENTATION_PORTRAIT) {
                    return;
                }

                mScreenOrientation = orientation;
                setRequestedOrientation(orientation);

                final boolean fullscreen = orientation != SCREEN_ORIENTATION_PORTRAIT;
                if (fullscreen == mVideoView.isInFullscreenMode()) {
                    DisplayCutoutManager displayCutoutManager = getDisplayCutoutManager();
                    //@formatter:off
                    if (!displayCutoutManager.isNotchSupport()
                            || displayCutoutManager.isNotchSupportOnEMUI()
                                    && displayCutoutManager.isNotchHidden()) {
                    //@formatter:on
                        if (mVideoView.isControlsShowing()) {
                            mVideoView.showControls(true, false);
                        }
                        return;
                    }
                    setFullscreenMode(fullscreen);
                    return;
                }
                setFullscreenMode(fullscreen);
                break;
        }

        showLockUnlockOrientationButton(true);
        if (mHandler != null) {
            mHandler.removeCallbacks(mHideLockUnlockOrientationButtonRunnable);
        }
        getHandler().postDelayed(mHideLockUnlockOrientationButtonRunnable,
                DELAY_TIME_HIDE_LOCK_UNLOCK_ORIENTATION_BUTTON);
    }

    @Synthetic void setFullscreenMode(boolean fullscreen) {
        // Disable 'swipe back' in full screen mode
        getSwipeBackLayout().setGestureEnabled(!fullscreen);

        final boolean lastFullscreen = mVideoView.isInFullscreenMode();

        showSystemBars(!fullscreen);
        //@formatter:off
        mVideoView.setFullscreenMode(fullscreen,
            fullscreen && (
                   !getDisplayCutoutManager().isNotchSupport()
                || (mPrivateFlags & PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE) == 0
                          ) ? getDisplayCutoutManager().isNotchSupport() ?
                                sStatusHeightInLandscapeOfNotchSupportDevice : sStatusHeight
                            : 0);
        //@formatter:on
        if (lastFullscreen != fullscreen || mVideoView.isControlsShowing()) {
            mVideoView.showControls(true, false);
        }
        resizeVideoView();

        mPrivateFlags = mPrivateFlags & ~PFLAG_LAST_VIDEO_LAYOUT_IS_FULLSCREEN
                | (fullscreen ? PFLAG_LAST_VIDEO_LAYOUT_IS_FULLSCREEN : 0);
    }

    @Synthetic void setFullscreenModeManually(boolean fullscreen) {
        if (mVideoView.isInFullscreenMode() == fullscreen) {
            return;
        }
        if ((mPrivateFlags & PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE) == 0) {
            mScreenOrientation = fullscreen ?
                    mDeviceOrientation == SCREEN_ORIENTATION_PORTRAIT
                            ? SCREEN_ORIENTATION_LANDSCAPE : mDeviceOrientation
                    : SCREEN_ORIENTATION_PORTRAIT;
            setRequestedOrientation(mScreenOrientation);
        }
        setFullscreenMode(fullscreen);
    }

    @Override
    public void onNotchChange(boolean hidden) {
        mPrivateFlags = (mPrivateFlags &~ PFLAG_SCREEN_NOTCH_HIDDEN)
                | (hidden ? PFLAG_SCREEN_NOTCH_HIDDEN : 0);
        resizeVideoView();
    }

    @Synthetic void resizeVideoView() {
        if (isInPictureInPictureMode()) {
            setVideoViewSize(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            return;
        }

        DisplayCutoutManager displayCutoutManager = getDisplayCutoutManager();
        switch (mScreenOrientation) {
            case SCREEN_ORIENTATION_PORTRAIT:
                final boolean layoutIsFullscreen = mVideoView.isInFullscreenMode();
                final boolean lastLayoutIsFullscreen =
                        (mPrivateFlags & PFLAG_LAST_VIDEO_LAYOUT_IS_FULLSCREEN) != 0;
                if ((mPrivateFlags & PFLAG_SCREEN_ORIENTATION_PORTRAIT_IMMUTABLE) != 0
                        && layoutIsFullscreen != lastLayoutIsFullscreen) {
                    TransitionManager.beginDelayedTransition(mVideoView, new ChangeBounds());
                }

                if (layoutIsFullscreen) {
                    setVideoViewSize(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                    if (displayCutoutManager.isNotchSupport()) {
                        /*
                         * setPadding () has no effect on DrawerLayout, such as:
                         *
                         * mVideoView.setPadding(0, mNotchHeight, 0, 0);
                         */
                        for (int i = 0, childCount = mVideoView.getChildCount(); i < childCount; i++) {
                            UiUtils.setViewMargins(mVideoView.getChildAt(i),
                                    0, displayCutoutManager.getNotchHeight(), 0, 0);
                        }
                    }
                } else {
                    final int screenWidth = App.getInstance(this).getRealScreenWidthIgnoreOrientation();
                    // portrait w : h = 16 : 9
                    final int minLayoutHeight = Utils.roundFloat((float) screenWidth / 16f * 9f);

                    setVideoViewSize(screenWidth, minLayoutHeight);
                    if (displayCutoutManager.isNotchSupport()) {
//                        mVideoView.setPadding(0, 0, 0, 0);
                        for (int i = 0, childCount = mVideoView.getChildCount(); i < childCount; i++) {
                            UiUtils.setViewMargins(mVideoView.getChildAt(i), 0, 0, 0, 0);
                        }
                    }
                }
                break;
            case SCREEN_ORIENTATION_LANDSCAPE:
                setVideoViewSize(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                if (displayCutoutManager.isNotchSupportOnEMUI()
                        && displayCutoutManager.isNotchHidden()) {
//                    mVideoView.setPadding(0, 0, 0, 0);
                    for (int i = 0, childCount = mVideoView.getChildCount(); i < childCount; i++) {
                        UiUtils.setViewMargins(mVideoView.getChildAt(i), 0, 0, 0, 0);
                    }
                } else if (displayCutoutManager.isNotchSupport()) {
//                    mVideoView.setPadding(mNotchHeight, 0, 0, 0);
                    for (int i = 0, childCount = mVideoView.getChildCount(); i < childCount; i++) {
                        UiUtils.setViewMargins(mVideoView.getChildAt(i),
                                displayCutoutManager.getNotchHeight(), 0, 0, 0);
                    }
                }
                break;
            case SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                setVideoViewSize(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                if (displayCutoutManager.isNotchSupportOnEMUI()
                        && displayCutoutManager.isNotchHidden()) {
//                    mVideoView.setPadding(0, 0, 0, 0);
                    for (int i = 0, childCount = mVideoView.getChildCount(); i < childCount; i++) {
                        UiUtils.setViewMargins(mVideoView.getChildAt(i), 0, 0, 0, 0);
                    }
                } else if (displayCutoutManager.isNotchSupport()) {
//                    mVideoView.setPadding(0, 0, mNotchHeight, 0);
                    for (int i = 0, childCount = mVideoView.getChildCount(); i < childCount; i++) {
                        UiUtils.setViewMargins(mVideoView.getChildAt(i),
                                0, 0, displayCutoutManager.getNotchHeight(), 0);
                    }
                }
                break;
        }
    }

    private void setVideoViewSize(int layoutWidth, int layoutHeight) {
        ViewGroup.LayoutParams lp = mVideoView.getLayoutParams();
        lp.width = layoutWidth;
        lp.height = layoutHeight;
        mVideoView.setLayoutParams(lp);
    }

    private void showSystemBars(boolean show) {
        Window window = getWindow();
        if (show) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                mStatusBarView.setVisibility(View.VISIBLE);

                View decorView = window.getDecorView();
                decorView.setOnSystemUiVisibilityChangeListener(null);
                // Status bar is set to transparent
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    SystemBarUtils.setStatusBackgroundColor(window, Color.TRANSPARENT);
                } else {
                    SystemBarUtils.setTranslucentStatus(window, true);
                }
                decorView.setSystemUiVisibility(
                        (decorView.getSystemUiVisibility()
                                // Makes the content view appear under the status bar
                                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                // Status bar and navigation bar appear
                        ) & ~(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY));
            } else {
                SystemBarUtils.showSystemBars(window, true);
            }
        } else {
            if (mStatusBarView != null) {
                mStatusBarView.setVisibility(View.GONE);
            }

            SystemBarUtils.showSystemBars(window, false);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                final View decorView = window.getDecorView();
                final int visibility = decorView.getSystemUiVisibility();
                decorView.setOnSystemUiVisibilityChangeListener(newVisibility -> {
                    if (newVisibility != visibility) {
                        decorView.setSystemUiVisibility(visibility);
                    }
                });
            }
        }
    }

    @SuppressLint("SwitchIntDef")
    @RequiresApi(PictureInPictureHelper.SDK_VERSION_SUPPORTS_RESIZABLE_PIP)
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);

        mVideoView.onMinimizationModeChange(isInPictureInPictureMode);
        getPipHelper().onPictureInPictureModeChanged(isInPictureInPictureMode);

        if (isInPictureInPictureMode) {
            int actions = PIP_ACTION_FAST_REWIND;
            final int playbackState = mVideoPlayer.getPlaybackState();
            switch (playbackState) {
                case IVideoPlayer.PLAYBACK_STATE_PLAYING:
                    actions |= PIP_ACTION_PAUSE | PIP_ACTION_FAST_FORWARD;
                    break;
                case IVideoPlayer.PLAYBACK_STATE_COMPLETED:
                    actions |= PIP_ACTION_PLAY
                            | (mVideoView.canSkipToNext() ? PIP_ACTION_FAST_FORWARD : 0);
                    break;
                default:
                    actions |= PIP_ACTION_PLAY | PIP_ACTION_FAST_FORWARD;
                    break;
            }
            getPipHelper().updatePictureInPictureActions(actions);

            sActivityInPiP = new WeakReference<>(this);

            observeNotchSwitch(false);
            setAutoRotationEnabled(false);

            mStatusBarView.setVisibility(View.GONE);
            showLockUnlockOrientationButton(false);
            mVideoProgressInPiP.setVisibility(View.VISIBLE);
            mRefreshVideoProgressInPiPTask = new RefreshVideoProgressInPiPTask();
            mRefreshVideoProgressInPiPTask.execute();

            mVideoView.showControls(false, false);
            mVideoView.setClipViewBounds(true);
        } else {
            if (sActivityInPiP != null) {
                sActivityInPiP.clear();
                sActivityInPiP = null;
            }

            mRefreshVideoProgressInPiPTask.cancel();
            mRefreshVideoProgressInPiPTask = null;

            if ((mPrivateFlags & PFLAG_STOPPED) != 0) {
                // This case we have handled in super
                return;
            }

            observeNotchSwitch(true);
            setAutoRotationEnabled(true);

            mStatusBarView.setVisibility(View.VISIBLE);
            mVideoProgressInPiP.setVisibility(View.GONE);

            mVideoView.showControls(true);
            mVideoView.setClipViewBounds(false);
        }
        resizeVideoView();
    }

    @Override
    public void showUserCancelableSnackbar(@NonNull CharSequence text, int duration) {
        UiUtils.showUserCancelableSnackbar(mVideoView, text, duration);
    }

    @Override
    public void notifyPlaylistSelectionChanged(int oldPosition, int position, boolean checkNewItemVisibility) {
        RecyclerView playlist = mVideoView.findViewById(R.id.rv_playlist);
        //noinspection rawtypes
        RecyclerView.Adapter adapter = playlist.getAdapter();
        //noinspection ConstantConditions
        adapter.notifyItemChanged(oldPosition,
                IVideoPresenter.PLAYLIST_ADAPTER_PAYLOAD_VIDEO_PROGRESS_CHANGED
                        | IVideoPresenter.PLAYLIST_ADAPTER_PAYLOAD_HIGHLIGHT_ITEM_IF_SELECTED);
        adapter.notifyItemChanged(position,
                IVideoPresenter.PLAYLIST_ADAPTER_PAYLOAD_VIDEO_PROGRESS_CHANGED
                        | IVideoPresenter.PLAYLIST_ADAPTER_PAYLOAD_HIGHLIGHT_ITEM_IF_SELECTED);
        if (checkNewItemVisibility) {
            RecyclerView.LayoutManager lm = playlist.getLayoutManager();
            if (lm instanceof LinearLayoutManager) {
                LinearLayoutManager llm = (LinearLayoutManager) lm;
                if (llm.findFirstCompletelyVisibleItemPosition() > position
                        || llm.findLastCompletelyVisibleItemPosition() < position) {
                    llm.scrollToPosition(position);
                }
            } else if (lm instanceof StaggeredGridLayoutManager) {
                StaggeredGridLayoutManager sglm = (StaggeredGridLayoutManager) lm;

                int minFirstCompletelyVisiblePosition = 0;
                for (int i : sglm.findFirstCompletelyVisibleItemPositions(null)) {
                    minFirstCompletelyVisiblePosition = Math.min(minFirstCompletelyVisiblePosition, i);
                }
                if (minFirstCompletelyVisiblePosition > position) {
                    sglm.scrollToPosition(position);
                    return;
                }

                int maxLastCompletelyVisiblePosition = 0;
                for (int i : sglm.findLastCompletelyVisibleItemPositions(null)) {
                    maxLastCompletelyVisiblePosition = Math.max(maxLastCompletelyVisiblePosition, i);
                }
                if (maxLastCompletelyVisiblePosition < position) {
                    sglm.scrollToPosition(position);
                }
            }
        }
    }

    @NonNull
    @Override
    public IVideoView.PlaylistViewHolder newPlaylistViewHolder(@NonNull ViewGroup parent) {
        return new PlaylistViewHolder(
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_video_play_list, parent, false));
    }

    private static final class PlaylistViewHolder extends IVideoView.PlaylistViewHolder {
        final ImageView videoImage;
        final TextView videoNameText;
        final TextView videoProgressDurationText;

        PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
            videoImage = itemView.findViewById(R.id.image_video);
            videoNameText = itemView.findViewById(R.id.text_videoName);
            videoProgressDurationText = itemView.findViewById(R.id.text_videoProgressAndDuration);
            VideoUtils2.adjustVideoThumbView(videoImage);
        }

        @Override
        public void loadVideoThumb(@NonNull Video video) {
            VideoUtils2.loadVideoThumbIntoImageView(videoImage, video);
        }

        @Override
        public void cancelLoadingVideoThumb() {
            Glide.with(videoImage.getContext()).clear(videoImage);
        }

        @Override
        public void setVideoTitle(@Nullable String text) {
            videoNameText.setText(text);
        }

        @Override
        public void setVideoProgressAndDurationText(@Nullable String text) {
            videoProgressDurationText.setText(text);
        }

        @Override
        public void highlightItemIfSelected(boolean selected) {
            itemView.setSelected(selected);
            videoNameText.setTextColor(
                    selected ? Consts.COLOR_ACCENT : Consts.TEXT_COLOR_PRIMARY_LIGHT);
            videoProgressDurationText.setTextColor(selected ? Consts.COLOR_ACCENT : 0x80FFFFFF);
        }
    }

    // --------------- Saved Instance State ------------------------

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mPresenter.saveData(outState);
    }
}