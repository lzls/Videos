/*
 * Created on 2022-2-17 2:47:13 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.MessageQueue;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.compat.LooperCompat;
import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.common.utils.Synthetic;
import com.liuzhenlin.common.utils.Utils;
import com.liuzhenlin.videos.web.Configs;
import com.liuzhenlin.videos.web.player.Constants;
import com.liuzhenlin.videos.web.player.PlayerWebView;
import com.liuzhenlin.videos.web.player.WebPlayer;

import java.lang.ref.WeakReference;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static com.liuzhenlin.videos.web.youtube.Youtube.Util.getPlaylistIdFromWatchOrShareUrl;
import static com.liuzhenlin.videos.web.youtube.Youtube.Util.getVideoIdFromWatchUrl;
import static com.liuzhenlin.videos.web.youtube.Youtube.Util.getVideoIndexFromWatchOrShareUrl;

@NonNullApi
/*package*/ class YoutubePlaybackView extends PlayerWebView {

    @SuppressWarnings("NotNullFieldNotInitialized")
    @Synthetic ChromeClient mChromeClient;

    private boolean mVisible;
    // Always check the current url to see if it is changed even if the view is not visible,
    // just in case it changes in the background where it was not under our control.
    private static final boolean ALWAYS_CHECK_CURRENT_URL = true;

    @Synthetic String mWatchUrl = Constants.URL_BLANK;
    // This should only work for YoutubePlayer
    private final CheckCurrentUrlIdler mCheckCurrentUrlIdler = new CheckCurrentUrlIdler(this);
    private boolean mIdleHandlerAdded;

    public YoutubePlaybackView(Context context) {
        super(context);
    }

    @Override
    public void loadPlaylist(String playlistId, @Nullable String videoId, int videoIndex) {
        @Nullable WebPlayer player = getWebPlayer();
        if (player instanceof YoutubeIFramePlayer) {
            loadDataWithBaseURL(Youtube.URLs.PLAYER_API, Youtube.getPlayListHTML(playlistId, videoId),
                    "text/html", null, null);
        } else if (player != null) {
            player.loadPlaylist(playlistId, videoId, videoIndex);
        }
    }

    @Override
    public void loadVideo(String videoId) {
        @Nullable WebPlayer player = getWebPlayer();
        if (player instanceof YoutubeIFramePlayer) {
            loadDataWithBaseURL(Youtube.URLs.PLAYER_API, Youtube.getVideoHTML(videoId),
                    "text/html", null, null);
        } else if (player != null) {
            player.loadVideo(videoId);
        }
    }

    @Override
    protected void setup(WebSettings settings) {
        super.setup(settings);
        addJavascriptInterface(new YoutubeJsInterface(mContext), YoutubeJsInterface.NAME);
        setWebViewClient(new Client());
        setWebChromeClient(mChromeClient = new ChromeClient());
    }

    @Override
    public void setWebPlayer(@Nullable WebPlayer player) {
        super.setWebPlayer(player);
        WebSettings settings = getSettings();
        settings.setUserAgentString(
                player instanceof YoutubeIFramePlayer
                        ? UserAgent.getUaDesktop(settings) : UserAgent.getUa(settings));
        addOrRemoveIdleHandler();
    }

    @Override
    protected boolean shouldStopWhenDetachedFromWindow() {
        return false;
    }

    @Override
    public boolean isInFullscreen() {
        return getWebPlayer() instanceof YoutubeIFramePlayer || mChromeClient.mCustomView != null;
    }

    @Override
    public boolean canEnterFullscreen() {
        return getWebPlayer() instanceof YoutubePlayer && mChromeClient.mCustomView == null;
    }

    @Override
    public boolean canExitFullscreen() {
        return mChromeClient.mCustomViewCallback != null;
    }

    @Override
    public void exitFullscreen() {
        if (canExitFullscreen()) {
            //noinspection ConstantConditions
            mChromeClient.mCustomViewCallback.onCustomViewHidden();
        }
    }

    @Override
    public void enterFullscreen() {
        if (canEnterFullscreen()) {
            loadUrl(Youtube.JsInterface.requestFullscreen());
        }
    }

    @Override
    public boolean canGoBack() {
        return super.canGoBack() || canExitFullscreen();
    }

    @Override
    public void goBack() {
        if (canExitFullscreen()) {
            //noinspection ConstantConditions
            mChromeClient.mCustomViewCallback.onCustomViewHidden();
        } else {
            super.goBack();
        }
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        if (mVisible != isVisible) {
            mVisible = isVisible;
            addOrRemoveIdleHandler();
        }
    }

    private void addOrRemoveIdleHandler() {
        @Nullable
        MessageQueue msgQueue = LooperCompat.getQueue(Executors.MAIN_EXECUTOR.getLooper());
        if (msgQueue != null) {
            if (ALWAYS_CHECK_CURRENT_URL || (mVisible /*&& getWebPlayer() instanceof YoutubePlayer*/)) {
                if (!mIdleHandlerAdded) {
                    mIdleHandlerAdded = true;
                    msgQueue.addIdleHandler(mCheckCurrentUrlIdler);
                }
            } else {
                if (mIdleHandlerAdded) {
                    mIdleHandlerAdded = false;
                    msgQueue.removeIdleHandler(mCheckCurrentUrlIdler);
                }
            }
        }
    }

    private static final class CheckCurrentUrlIdler implements MessageQueue.IdleHandler {

        final WeakReference<YoutubePlaybackView> mViewRef;

        CheckCurrentUrlIdler(YoutubePlaybackView view) {
            mViewRef = new WeakReference<>(view);
        }

        @Override
        public boolean queueIdle() {
            @Nullable YoutubePlaybackView v = mViewRef.get();
            if (v == null) {
                return false;
            }
            String url = v.getUrl();
            if (!url.split("#")[0].equals(v.mWatchUrl) && url.matches(Youtube.REGEX_WATCH_URL)) {
                @Nullable String playlistId = getPlaylistIdFromWatchOrShareUrl(url);
                @Nullable String videoId = getVideoIdFromWatchUrl(url);
                if ((playlistId != null && !playlistId.isEmpty() || videoId != null && !videoId.isEmpty())
                        && (!TextUtils.equals(playlistId, getPlaylistIdFromWatchOrShareUrl(v.mWatchUrl))
                        || !TextUtils.equals(videoId, getVideoIdFromWatchUrl(v.mWatchUrl)))) {
                    boolean fullscreen = v.mChromeClient.mCustomView != null;
                    int videoIndex = getVideoIndexFromWatchOrShareUrl(url);
                    // Player will probably not be ready to load the video from the new watch url
                    // immediately after this view goes back, at which point the player will be
                    // being reloaded.
                    YoutubePlaybackService.peekIfNonnullThenDo(service -> service.mPlayerReady = false);
                    v.goBack();
                    YoutubePlaybackService.startPlayback(
                            v.mContext, playlistId, videoId, videoIndex, true);
                    if (fullscreen) {
                        YoutubePlaybackService.peekIfNonnullThenDo(
                                service -> service.addPlayerListener(new PlayerListener() {
                                    @Override
                                    public void onPlayerStateChange(int status) {
                                        if (status == Youtube.PlayingStatus.PLAYING) {
                                            // Don't go fullscreen automatically if the user chose
                                            // another related video from this playback view while
                                            // the video is not fullscreen.
                                            if (url.equals(v.mWatchUrl)) {
                                                v.enterFullscreen();
                                            }
                                            service.removePlayerListener(this);
                                        }
                                    }
                                }));
                    }
                    v.mWatchUrl = url;
                }
            }
            return true;
        }
    }

    private final class Client extends WebViewClient {
        Client() {}

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // This can work normally for YoutubeIFramePlayer, but not to YoutubePlayer
            if (!url.contains("#")) {
                if (url.equals(mWatchUrl)
                        || YoutubePlaybackService.startPlaybackIfUrlIsWatchUrl(mContext, url, true)) {
                    mWatchUrl = url;
                    return true;
                }
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            if (view.canGoBack()) {
                if (getWebPlayer() instanceof YoutubeIFramePlayer) {
                    if (url.equals(Youtube.URLs.PLAYER_API)) {
                        view.clearHistory();
                    }
                } else {
                    if (url.matches(Youtube.REGEX_WATCH_URL)) {
                        view.clearHistory();
                    }
                }
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            @Nullable WebPlayer player = getWebPlayer();
            if (player instanceof YoutubePlayer) {
                ((YoutubePlayer) player).attachListeners();
            }
        }
    }

    private final class ChromeClient extends WebChromeClient {
        @Nullable WebChromeClient.CustomViewCallback mCustomViewCallback;
        @Nullable View mCustomView;

        private int mScrollX;
        private int mScrollY;

        ChromeClient() {
        }

        @Override
        public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
            if (mCustomView == null) {
                mCustomViewCallback = callback;
                mCustomView = view;
                mScrollX = getScrollX();
                mScrollY = getScrollY();
                if (mScrollX != 0 || mScrollY != 0) {
                    scrollTo(0, 0);
                }
                addView(view, new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

                @Nullable YoutubePlaybackActivity ytPlaybackActivity = YoutubePlaybackActivity.get();
                if (ytPlaybackActivity != null) {
                    ytPlaybackActivity.enterFullscreen();
                }
            }
        }

        @Override
        public void onHideCustomView() {
            if (mCustomView != null) {
                removeView(mCustomView);
                if (mScrollX != 0 || mScrollY != 0) {
                    Utils.postOnLayoutValid(YoutubePlaybackView.this,
                            () -> scrollTo(mScrollX, mScrollY));
                }
                mCustomView = null;
                mCustomViewCallback = null;

                @Nullable YoutubePlaybackActivity ytPlaybackActivity = YoutubePlaybackActivity.get();
                if (ytPlaybackActivity != null) {
                    ytPlaybackActivity.exitFullscreen();
                }
            }
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
            if (Configs.WEB_LOGGING_ENABLED
                    || consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.WARNING
                    || consoleMessage.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                Utils.logWebConsoleMessage(consoleMessage, Youtube.JS_LOG_TAG);
            }
            return true;
        }
    }
}
