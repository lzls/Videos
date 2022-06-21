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

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static com.liuzhenlin.videos.web.youtube.Youtube.Util.getPlaylistIdFromWatchOrShareUrl;
import static com.liuzhenlin.videos.web.youtube.Youtube.Util.getVideoIdFromWatchUrl;
import static com.liuzhenlin.videos.web.youtube.Youtube.Util.getVideoIndexFromWatchOrShareUrl;

@NonNullApi
public class YoutubePlaybackView extends PlayerWebView {

    @SuppressWarnings("NotNullFieldNotInitialized")
    private ChromeClient mChromeClient;

    private boolean mVisible;

    @Synthetic String mWatchUrl = Constants.URL_BLANK;
    // This should only work for YoutubePlayer
    private final MessageQueue.IdleHandler mCheckCurrentUrlIdleHandler = () -> {
        String url = getUrl();
        if (!url.split("#")[0].equals(mWatchUrl) && url.matches(Youtube.REGEX_WATCH_URL)) {
            @Nullable String playlistId = getPlaylistIdFromWatchOrShareUrl(url);
            @Nullable String videoId = getVideoIdFromWatchUrl(url);
            if ((playlistId != null && !playlistId.isEmpty() || videoId != null && !videoId.isEmpty())
                    && (!TextUtils.equals(playlistId, getPlaylistIdFromWatchOrShareUrl(mWatchUrl))
                            || !TextUtils.equals(videoId, getVideoIdFromWatchUrl(mWatchUrl)))) {
                boolean fullscreen = mChromeClient.mCustomView != null;
                int videoIndex = getVideoIndexFromWatchOrShareUrl(url);
                // Player will probably not be ready to load the video from the new watch url
                // immediately after this view goes back, at which point the player will be
                // being reloaded.
                YoutubePlaybackService.peekIfNonnullThenDo(service -> service.mPlayerReady = false);
                goBack();
                YoutubePlaybackService.startPlayback(mContext, playlistId, videoId, videoIndex, true);
                if (fullscreen) {
                    YoutubePlaybackService.peekIfNonnullThenDo(
                            service -> service.addPlayerListener(new PlayerListener() {
                                @Override
                                public void onPlayerStateChange(int status) {
                                    if (status == Youtube.PlayingStatus.PLAYING) {
                                        // Don't go fullscreen automatically if the user chose
                                        // another related video from this playback view while
                                        // the video is not fullscreen.
                                        if (url.equals(mWatchUrl)) {
                                            enterFullscreen();
                                        }
                                        service.removePlayerListener(this);
                                    }
                                }
                            }));
                }
                mWatchUrl = url;
            }
        }
        return true;
    };
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
            if (mVisible /*&& getWebPlayer() instanceof YoutubePlayer*/) {
                if (!mIdleHandlerAdded) {
                    mIdleHandlerAdded = true;
                    msgQueue.addIdleHandler(mCheckCurrentUrlIdleHandler);
                }
            } else {
                if (mIdleHandlerAdded) {
                    mIdleHandlerAdded = false;
                    msgQueue.removeIdleHandler(mCheckCurrentUrlIdleHandler);
                }
            }
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

        ChromeClient() {
        }

        @Override
        public void onShowCustomView(View view, WebChromeClient.CustomViewCallback callback) {
            if (mCustomView == null) {
                mCustomViewCallback = callback;
                mCustomView = view;
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
