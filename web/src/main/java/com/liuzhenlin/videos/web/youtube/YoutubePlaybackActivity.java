package com.liuzhenlin.videos.web.youtube;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Rational;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegateWrapper;
import androidx.appcompat.app.PlatformPendingTransitionOverrides;

import com.liuzhenlin.common.utils.PictureInPictureHelper;
import com.liuzhenlin.common.utils.Synthetic;
import com.liuzhenlin.common.utils.SystemBarUtils;
import com.liuzhenlin.videos.web.R;

import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_FAST_FORWARD;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_FAST_REWIND;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_PAUSE;
import static com.liuzhenlin.common.utils.PictureInPictureHelper.PIP_ACTION_PLAY;

public class YoutubePlaybackActivity extends AppCompatActivity {

    @SuppressLint("StaticFieldLeak")
    private static YoutubePlaybackActivity sInstance;
    private AppCompatDelegateWrapper mDelegate;

    private ViewGroup mContentView;
    private WebView mPlaybackView;

    @Synthetic YoutubePlaybackService mService;

    @NonNull
    @Override
    public AppCompatDelegateWrapper getDelegate() {
        if (mDelegate == null) {
            mDelegate = new AppCompatDelegateWrapper(super.getDelegate());
        }
        return mDelegate;
    }

    public static YoutubePlaybackActivity get() {
        return sInstance;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        getDelegate().setPendingTransitionOverrides(new PlatformPendingTransitionOverrides());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sInstance = this;

        setContentView(R.layout.activity_youtube_playback);
        mService = YoutubePlaybackService.get();
        if (mService == null || mService.mView == null) {
            finish();
        } else {
            SystemBarUtils.showSystemBars(getWindow(), false);

            mPlaybackView = mService.mView;
            ViewGroup parent = (ViewGroup) mPlaybackView.getParent();
            if (parent != null) {
                parent.removeView(mPlaybackView);
            }
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            mContentView = findViewById(R.id.content);
            mContentView.addView(mPlaybackView, lp);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sInstance = null;
        // Removes the player view in case a memory leak as it is part of the view hierarchy
        // and may be still referenced from YoutubePlaybackService.
        if (mContentView != null) {
            mContentView.removeView(mPlaybackView);
        }
    }

    @Override
    public void finish() {
        if (getPipHelper().supportsPictureInPictureMode()) {
            // finish() does not remove the activity in PIP mode from the recents stack.
            // Only finishAndRemoveTask() does this.
            //noinspection NewApi
            finishAndRemoveTask();
        } else {
            super.finish();
        }
        getDelegate().onFinished();
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
        }
    }

    // Called when the user touches the Home or Recents button to leave the app.
    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        enterPipMode();
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

    /*package*/ void onPlayingStatusChange(int playingStatus) {
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
                    YoutubePlayer player = mService.getWebPlayer();
                    if (player != null) {
                        player.play();
                    }
                }

                @Override
                public void onTapPause() {
                    YoutubePlayer player = mService.getWebPlayer();
                    if (player != null) {
                        player.pause();
                    }
                }

                @Override
                public void onTapFastForward() {
                    YoutubePlayer player = mService.getWebPlayer();
                    if (player != null) {
                        player.fastForward();
                    }
                }

                @Override
                public void onTapFastRewind() {
                    YoutubePlayer player = mService.getWebPlayer();
                    if (player != null) {
                        player.fastRewind();
                    }
                }
            });
            delegate.setPipHelper(pipHelper);
        }
        return pipHelper;
    }
}
