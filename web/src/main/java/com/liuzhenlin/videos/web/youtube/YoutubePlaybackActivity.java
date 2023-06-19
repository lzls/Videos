package com.liuzhenlin.videos.web.youtube;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Rational;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegateWrapper;
import androidx.appcompat.app.PlatformPendingTransitionOverrides;

import com.liuzhenlin.common.Configs;
import com.liuzhenlin.common.Consts;
import com.liuzhenlin.common.observer.OnOrientationChangeListener;
import com.liuzhenlin.common.utils.ActivityUtils;
import com.liuzhenlin.common.utils.PictureInPictureHelper;
import com.liuzhenlin.common.utils.ScreenUtils;
import com.liuzhenlin.common.utils.Synthetic;
import com.liuzhenlin.common.utils.SystemBarUtils;
import com.liuzhenlin.common.view.AspectRatioFrameLayout;
import com.liuzhenlin.common.view.SwipeRefreshLayout;
import com.liuzhenlin.videos.web.AndroidWebView;
import com.liuzhenlin.videos.web.R;
import com.liuzhenlin.videos.web.bean.Video;
import com.liuzhenlin.videos.web.player.PlayerWebView;
import com.liuzhenlin.videos.web.player.WebPlayer;

import java.lang.reflect.Field;
import java.util.Locale;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_FAST_FORWARD;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_FAST_REWIND;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_PAUSE;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_PLAY;

@SuppressLint("SourceLockedOrientationActivity")
public class YoutubePlaybackActivity extends AppCompatActivity implements PlayerListener {

    @SuppressLint("StaticFieldLeak")
    private static YoutubePlaybackActivity sInstance;
    private AppCompatDelegateWrapper mDelegate;

    private AspectRatioFrameLayout mPlaybackViewContainer;
    @Synthetic PlayerWebView mPlaybackView;

    @Synthetic YoutubePlaybackService mService;

    private AndroidWebView.PageListener mWebPageListener;

    private boolean mShowSystemBars;

    private ImageView mLockUnlockOrientationButton;
    private OnOrientationChangeListener mOnOrientationChangeListener;
    private boolean mOrientationLocked;

    private String mLockOrientation;
    private String mUnlockOrientation;

    @Synthetic Handler mHandler;
    private final Runnable mHideLockUnlockOrientationButtonRunnable =
            this::hideLockUnlockOrientationButton;
    private static final int DELAY_TIME_HIDE_LOCK_UNLOCK_ORIENTATION_BUTTON = 2500;

    private ProgressBar mVideoProgressInPiP;
    private RefreshVideoProgressInPiPTask mRefreshVideoProgressInPiPTask;
    private final class RefreshVideoProgressInPiPTask implements Runnable {
        RefreshVideoProgressInPiPTask() {
        }

        @Override
        public void run() {
            WebPlayer player = mService.getWebPlayer();
            if (player != null) {
                player.requestGetVideoInfo(false);
            }
        }

        void execute() {
            cancel();
            run();
        }

        void cancel() {
            if (mHandler != null) {
                mHandler.removeCallbacks(this);
            }
        }
    }

    /*package*/ void onGetVideoInfo(Video video) {
        if (mRefreshVideoProgressInPiPTask == null || mService == null) {
            return;
        }
        if (video == null) {
            video = YoutubePlaybackService.EMPTY_VIDEO;
        }
        long progress = video.getCurrentPosition();
        if (mService.isPlaying()) {
            mRefreshVideoProgressInPiPTask.cancel();
            getHandler().postDelayed(mRefreshVideoProgressInPiPTask, 1000 - progress % 1000);
        }
        mVideoProgressInPiP.setMax((int) video.getDuration());
        mVideoProgressInPiP.setSecondaryProgress((int) video.getBufferedPosition());
        mVideoProgressInPiP.setProgress((int) progress);
    }

    private Handler getHandler() {
        if (mHandler == null) {
            mHandler = getWindow().getDecorView().getHandler();
            if (mHandler == null) {
                mHandler = Consts.getMainThreadHandler();
            }
        }
        return mHandler;
    }

    private String getLockOrientationStr() {
        if (mLockOrientation == null) {
            mLockOrientation = getString(R.string.lockScreenOrientation);
        }
        return mLockOrientation;
    }

    private String getUnlockOrientationStr() {
        if (mUnlockOrientation == null) {
            mUnlockOrientation = getString(R.string.unlockScreenOrientation);
        }
        return mUnlockOrientation;
    }

    @NonNull
    @Override
    public AppCompatDelegateWrapper getDelegate() {
        if (mDelegate == null) {
            mDelegate = new AppCompatDelegateWrapper(super.getDelegate(), super::finish);
        }
        return mDelegate;
    }

    public static YoutubePlaybackActivity get() {
        return sInstance;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        mService = YoutubePlaybackService.get();
        if (usingYoutubeIFramePlayer()) {
            getDelegate().setPendingTransitionOverrides(new PlatformPendingTransitionOverrides());
        }
        // This avoids the unnecessary recreation for this Activity when day night mode changes.
        getDelegate().setHostCallback(new AppCompatDelegateWrapper.HostCallback() {
            @Override
            public boolean onDayNightAppliedToResourcesConfig(boolean night) {
                return true;
            }

            @Override
            public boolean onLanguageAppliedToResourcesConfig(@NonNull Locale language) {
                return true;
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = this;
        mService = YoutubePlaybackService.get();
        if (mService == null || mService.mView == null) {
            finish();
        } else {
            mService.addPlayerListener(this);
            if (mService.isPlaying()) {
                ScreenUtils.setKeepWindowBright(getWindow(), true);
            }

            boolean fullscreen = mService.mView.isInFullscreen();
            boolean usingYoutubeIFramePlayer = usingYoutubeIFramePlayer();

            setRequestedOrientation(
                    usingYoutubeIFramePlayer ?
                            SCREEN_ORIENTATION_SENSOR_LANDSCAPE : SCREEN_ORIENTATION_PORTRAIT);
            initStatusBar(fullscreen);
            setContentView(R.layout.activity_youtube_playback);

            mVideoProgressInPiP = findViewById(R.id.pbInPiP_videoProgress);

            mLockUnlockOrientationButton = findViewById(R.id.btn_lockUnlockOrientation);
            mLockUnlockOrientationButton.setOnClickListener(
                    v -> setScreenOrientationLocked(!mOrientationLocked));
            mOnOrientationChangeListener = new OnOrientationChangeListener(this) {
                @Override
                protected void onOrientationChange(int orientation) {
                    if (orientation != SCREEN_ORIENTATION_REVERSE_PORTRAIT
                            && usingYoutubePlayer()) {
                        Configuration config = getResources().getConfiguration();
                        boolean toPortrait = orientation == SCREEN_ORIENTATION_PORTRAIT;
                        boolean portrait = config.orientation == Configuration.ORIENTATION_PORTRAIT;
                        if (portrait && !toPortrait || !portrait && toPortrait) {
                            showLockUnlockOrientationButton(true);
                        }
                    }
                }
            };

            mPlaybackView = mService.mView;
            ViewGroup parent = (ViewGroup) mPlaybackView.getParent();
            if (parent != null) {
                parent.removeView(mPlaybackView);
            }
            // Showing Dialog or any kind of child Window from JS will need an Activity instance
            // with a valid Window token.
            setPlaybackViewBaseContext(this);
            mPlaybackViewContainer = findViewById(R.id.videoViewContainer);
            mPlaybackViewContainer.addView(mPlaybackView, 0);

            SwipeRefreshLayout srl = findViewById(R.id.swipeRefreshLayout);
            srl.setColorSchemeResources(Configs.SWIPE_REFRESH_WIDGET_COLOR_SCHEME);
            srl.setOnRefreshListener(() -> mPlaybackView.reload());
            srl.setOnChildScrollUpCallback(
                    (_parent, _child) ->
                            mPlaybackView.getScrollY() > 0
                                    || !usingYoutubeIFramePlayer && mPlaybackView.isInFullscreen()
                                    || Youtube.REGEX_SHORTS_URL.matches(mPlaybackView.getUrl()));

            mPlaybackView.addPageListener(mWebPageListener = new AndroidWebView.PageListener() {
                @Override
                public void onPageStarted(
                        @NonNull WebView view, @NonNull String url, @Nullable Bitmap favicon) {
                    srl.setRefreshing(true);
                }

                @Override
                public void onPageFinished(@NonNull WebView view, @NonNull String url) {
                    srl.setRefreshing(false);
                }

                @Override
                public void onEnterFullscreen(@NonNull WebView view) {
                    enterFullscreen();
                }

                @Override
                public void onExitFullscreen(@NonNull WebView view) {
                    exitFullscreen();
                }
            });
            if (fullscreen) {
                enterFullscreen();
            }
        }
    }

    private void setPlaybackViewBaseContext(Context context) {
        if (mPlaybackView != null) {
            try {
                //noinspection JavaReflectionMemberAccess,DiscouragedPrivateApi
                Field base = ContextWrapper.class.getDeclaredField("mBase");
                base.setAccessible(true);
                base.set(mPlaybackView.getContext(), context);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initStatusBar(boolean inFullscreen) {
        boolean showSystemBars = !inFullscreen || isInMultiWindowMode();
        Window window = getWindow();
        SystemBarUtils.showSystemBars(window, showSystemBars);
        adjustStatusBarColor(inFullscreen);
        mShowSystemBars = showSystemBars;
        window.getDecorView().setOnSystemUiVisibilityChangeListener(
                visibility -> SystemBarUtils.showSystemBars(window, mShowSystemBars));
    }

    private void adjustStatusBarColor(boolean inFullscreen) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // As for YoutubeIFramePlayer, inFullscreen is always true
            if (inFullscreen) {
                SystemBarUtils.setStatusBackgroundColor(
                        getWindow(), Configs.VIDEO_VIEW_BACKGROUND_COLOR);
            } else {
                SystemBarUtils.setStatusBackgroundColorRes(
                        getWindow(), R.color.youtube_watch_page_actionbar_background);
            }
        }
    }

    @Synthetic boolean usingYoutubePlayer() {
        return mService != null && mService.getWebPlayer() instanceof YoutubePlayer;
    }

    private boolean usingYoutubeIFramePlayer() {
        return mService != null && mService.getWebPlayer() instanceof YoutubeIFramePlayer;
    }

    @Synthetic void enterFullscreen() {
        if (usingYoutubePlayer()) {
            SystemBarUtils.showSystemBars(getWindow(), mShowSystemBars = isInMultiWindowMode());
            adjustStatusBarColor(true);
            setRequestedOrientation(SCREEN_ORIENTATION_SENSOR);
            setOnOrientationChangeListenerEnabled(true);
        }
    }

    @Synthetic void exitFullscreen() {
        if (usingYoutubePlayer()) {
            SystemBarUtils.showSystemBars(getWindow(), mShowSystemBars = true);
            adjustStatusBarColor(false);
            setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
            setOnOrientationChangeListenerEnabled(false);
            showLockUnlockOrientationButton(false);
            setScreenOrientationLocked(false);
        }
    }

    private void setOnOrientationChangeListenerEnabled(boolean enabled) {
        if (enabled) {
            enabled = usingYoutubePlayer()
                    && mPlaybackView.isInFullscreen() && !isInPictureInPictureMode();
        }
        if (enabled) {
            mOnOrientationChangeListener.setOrientation(ActivityUtils.getCurrentOrientation(this));
        }
        mOnOrientationChangeListener.setEnabled(enabled);
    }

    private void setScreenOrientationLocked(boolean locked) {
        if (!usingYoutubePlayer()) {
            return;
        }
        mOrientationLocked = locked;
        if (locked) {
            mLockUnlockOrientationButton.setImageResource(R.drawable.ic_unlock);
            mLockUnlockOrientationButton.setContentDescription(getLockOrientationStr());
            if (mPlaybackView.isInFullscreen()) {
                switch (ActivityUtils.getCurrentOrientation(this)) {
                    case SCREEN_ORIENTATION_LANDSCAPE:
                    case SCREEN_ORIENTATION_REVERSE_LANDSCAPE:
                        setRequestedOrientation(SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
                        break;
                    default:
                        setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
                        break;
                }
            } else {
                setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
            }
        } else {
            mLockUnlockOrientationButton.setImageResource(R.drawable.ic_lock);
            mLockUnlockOrientationButton.setContentDescription(getUnlockOrientationStr());
            if (mPlaybackView.isInFullscreen()) {
                setRequestedOrientation(SCREEN_ORIENTATION_SENSOR);
            } else {
                setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
            }
        }
    }

    @Synthetic void showLockUnlockOrientationButton(boolean show) {
        if (mHandler != null) {
            mHandler.removeCallbacks(mHideLockUnlockOrientationButtonRunnable);
        }
        if (show) {
            getHandler().postDelayed(mHideLockUnlockOrientationButtonRunnable,
                    DELAY_TIME_HIDE_LOCK_UNLOCK_ORIENTATION_BUTTON);
            mLockUnlockOrientationButton.setVisibility(View.VISIBLE);
        } else {
            hideLockUnlockOrientationButton();
        }
    }

    private void hideLockUnlockOrientationButton() {
        mLockUnlockOrientationButton.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setOnOrientationChangeListenerEnabled(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        setOnOrientationChangeListenerEnabled(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mService != null) {
            mService.removePlayerListener(this);
        }
        sInstance = null;
        setPlaybackViewBaseContext(getApplicationContext());
        // Removes the player view in case a memory leak as it is part of the view hierarchy
        // and may be still referenced from YoutubePlaybackService.
        if (mPlaybackViewContainer != null) {
            mPlaybackViewContainer.removeView(mPlaybackView);
        }
        // Have the video view exit fullscreen here, to avoid it going fullscreen automatically
        // the next time user selects a video to play.
        if (mPlaybackView != null) {
            mPlaybackView.removePageListener(mWebPageListener);
            mPlaybackView.exitFullscreen();
        }
    }

    @Override
    public void onBackPressed() {
        if (mPlaybackView.canGoBack()) {
            mPlaybackView.goBack();
        } else if (mService != null) {
            mService.stop();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void finish() {
        getDelegate().finish();
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        mShowSystemBars = isInMultiWindowMode || !mPlaybackView.isInFullscreen();
        SystemBarUtils.showSystemBars(getWindow(), mShowSystemBars);
    }

    @Override
    public boolean isInMultiWindowMode() {
        return Consts.SDK_VERSION >= Consts.SDK_VERSION_SUPPORTS_MULTI_WINDOW
                && super.isInMultiWindowMode();
    }

    @Override
    public boolean isInPictureInPictureMode() {
        return getPipHelper().doesSdkVersionSupportPiP() && super.isInPictureInPictureMode();
    }

    @SuppressLint("NewApi")
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);
        getDelegate().onPictureInPictureModeChanged(isInPictureInPictureMode);
        getPipHelper().onPictureInPictureModeChanged(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            updatePictureInPictureActions(mService.mPlayingStatus);
            setOnOrientationChangeListenerEnabled(false);
            showLockUnlockOrientationButton(false);
            if (Build.VERSION.SDK_INT >= PictureInPictureHelper.SDK_VERSION_SUPPORTS_RESIZABLE_PIP) {
                PictureInPictureHelper.Adapter pipHelperAdapter = getPipHelper().getAdapter();
                //noinspection ConstantConditions
                mPlaybackViewContainer.setAspectRatio((float)
                        pipHelperAdapter.getVideoWidth() / pipHelperAdapter.getVideoHeight());
                mVideoProgressInPiP.setVisibility(View.VISIBLE);
                mRefreshVideoProgressInPiPTask = new RefreshVideoProgressInPiPTask();
                mRefreshVideoProgressInPiPTask.execute();
            }
        } else {
            setOnOrientationChangeListenerEnabled(true);
            if (Build.VERSION.SDK_INT >= PictureInPictureHelper.SDK_VERSION_SUPPORTS_RESIZABLE_PIP) {
                mPlaybackViewContainer.setAspectRatio(0); // Unset any aspect ratio
                mVideoProgressInPiP.setVisibility(View.GONE);
                mRefreshVideoProgressInPiPTask.cancel();
                mRefreshVideoProgressInPiPTask = null;
            }
        }
    }

    // Called when the user touches the Home or Recents button to leave the app.
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (mPlaybackView.isInFullscreen()
                && Youtube.Prefs.get(this).enterPipWhenVideoIsFullscreenAndPlaybackSwitchesToBackground()) {
            enterPipMode();
        }
    }

    public void enterPipMode() {
        //noinspection NewApi
        if (getPipHelper().supportsPictureInPictureMode() && !super.isInPictureInPictureMode()) {
            if (Build.VERSION.SDK_INT >= PictureInPictureHelper.SDK_VERSION_SUPPORTS_RESIZABLE_PIP) {
                Rational videoAspectRatio = new Rational(16, 9);
                PictureInPictureParams.Builder params =
                        getPipHelper().getPipParamsBuilder().setAspectRatio(videoAspectRatio);
                enterPictureInPictureMode(params.build());
            } else {
                //noinspection NewApi
                enterPictureInPictureMode();
            }
        }
    }

    @SuppressLint("SwitchIntDef")
    @Override
    public void onPlayerStateChange(@Youtube.PlayingStatus int playingStatus) {
        switch (playingStatus) {
            case Youtube.PlayingStatus.PLAYING:
            case Youtube.PlayingStatus.BUFFERRING:
                ScreenUtils.setKeepWindowBright(getWindow(), true);
                if (mRefreshVideoProgressInPiPTask != null) {
                    mRefreshVideoProgressInPiPTask.execute();
                }
                break;
            case Youtube.PlayingStatus.PAUSED:
            case Youtube.PlayingStatus.ENDED:
                ScreenUtils.setKeepWindowBright(getWindow(), false);
                if (mRefreshVideoProgressInPiPTask != null) {
                    mRefreshVideoProgressInPiPTask.cancel();
                }
                break;
        }
        if (isInPictureInPictureMode()) {
            updatePictureInPictureActions(playingStatus);
        }
    }

    @SuppressLint("NewApi")
    private void updatePictureInPictureActions(int playingStatus) {
        switch (playingStatus) {
            case Youtube.PlayingStatus.PLAYING:
            case Youtube.PlayingStatus.BUFFERRING:
                getPipHelper().updatePictureInPictureActions(PIP_ACTION_PAUSE
                        | PIP_ACTION_FAST_REWIND | PIP_ACTION_FAST_FORWARD);
                break;
            case Youtube.PlayingStatus.UNSTARTED:
            case Youtube.PlayingStatus.VIDEO_CUED:
            case Youtube.PlayingStatus.PAUSED:
                getPipHelper().updatePictureInPictureActions(PIP_ACTION_PLAY
                        | PIP_ACTION_FAST_REWIND | PIP_ACTION_FAST_FORWARD);
                break;
            case Youtube.PlayingStatus.ENDED:
                getPipHelper().updatePictureInPictureActions(PIP_ACTION_PLAY
                        | PIP_ACTION_FAST_REWIND);
                break;
        }
    }

    private PictureInPictureHelper getPipHelper() {
        AppCompatDelegateWrapper delegate = getDelegate();
        PictureInPictureHelper pipHelper = delegate.getPipHelper();
        if (pipHelper == null) {
            pipHelper = new PictureInPictureHelper(this);
            pipHelper.setAdapter(new PictureInPictureHelper.Adapter() {
                @Override
                public void onTapPlay() {
                    WebPlayer player = mService.getWebPlayer();
                    if (player != null) {
                        player.play();
                    }
                }

                @Override
                public void onTapPause() {
                    WebPlayer player = mService.getWebPlayer();
                    if (player != null) {
                        player.pause();
                    }
                }

                @Override
                public void onTapFastForward() {
                    WebPlayer player = mService.getWebPlayer();
                    if (player != null) {
                        player.fastForward();
                    }
                }

                @Override
                public void onTapFastRewind() {
                    WebPlayer player = mService.getWebPlayer();
                    if (player != null) {
                        player.fastRewind();
                    }
                }

                @Nullable
                @Override
                public View getVideoView() {
                    return mPlaybackView;
                }

                @Override
                public int getVideoWidth() {
                    return 16;
                }

                @Override
                public int getVideoHeight() {
                    return 9;
                }
            });
            delegate.setPipHelper(pipHelper);
        }
        return pipHelper;
    }
}
