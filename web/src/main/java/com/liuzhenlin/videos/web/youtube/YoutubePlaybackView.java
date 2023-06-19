/*
 * Created on 2022-2-17 2:47:13 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Looper;
import android.os.MessageQueue;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.compat.LooperCompat;
import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.common.utils.Synthetic;
import com.liuzhenlin.common.utils.Utils;
import com.liuzhenlin.videos.web.AndroidWebView;
import com.liuzhenlin.videos.web.Configs;
import com.liuzhenlin.videos.web.player.Constants;
import com.liuzhenlin.videos.web.player.PlayerWebView;
import com.liuzhenlin.videos.web.player.WebPlayer;

import java.lang.ref.WeakReference;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;

import static com.liuzhenlin.videos.web.youtube.Youtube.Util.getPlaylistIdFromWatchOrShareUrl;
import static com.liuzhenlin.videos.web.youtube.Youtube.Util.getVideoIdFromWatchUrl;
import static com.liuzhenlin.videos.web.youtube.Youtube.Util.getVideoIndexFromWatchOrShareUrl;
import static com.liuzhenlin.videos.web.youtube.Youtube.Util.getVideoStartMsFromWatchOrShareUrl;

@NonNullApi
/*package*/ class YoutubePlaybackView extends PlayerWebView {

    @SuppressWarnings("NotNullFieldNotInitialized")
    @Synthetic ChromeClient mChromeClient;

    @Synthetic boolean mVisible;
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
    public void loadPlaylist(
            String playlistId, @Nullable String videoId, int videoIndex, long videoStartMs) {
        @Nullable WebPlayer player = getWebPlayer();
        if (player instanceof YoutubeIFramePlayer) {
            loadDataWithBaseURL(
                    Youtube.URLs.PLAYER_API,
                    Youtube.getPlayListHTML(playlistId, videoId, videoStartMs),
                    "text/html", null, null);
        } else if (player != null) {
            player.loadPlaylist(playlistId, videoId, videoIndex, videoStartMs);
        }
    }

    @Override
    public void loadVideo(String videoId, long videoStartMs) {
        @Nullable WebPlayer player = getWebPlayer();
        if (player instanceof YoutubeIFramePlayer) {
            loadDataWithBaseURL(Youtube.URLs.PLAYER_API, Youtube.getVideoHTML(videoId, videoStartMs),
                    "text/html", null, null);
        } else if (player != null) {
            player.loadVideo(videoId, videoStartMs);
        }
    }

    @Override
    protected void setup(WebSettings settings) {
        super.setup(settings);
        addJavascriptInterface(new YoutubeJsInterface(mContext), YoutubeJsInterface.NAME);
    }

    @Override
    protected AndroidWebView.Client createWebViewClient() {
        return new Client();
    }

    @Override
    protected AndroidWebView.ChromeClient createWebChromeClient() {
        return mChromeClient = new ChromeClient();
    }

    @Override
    public void setWebPlayer(@Nullable WebPlayer player) {
        super.setWebPlayer(player);
        WebSettings settings = getSettings();
        settings.setUserAgentString(
                player instanceof YoutubeIFramePlayer ?
                        UserAgent.getUaDesktop(settings) : UserAgent.getUa(settings));
        addOrRemoveIdleHandler();
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
        MessageQueue msgQueue = LooperCompat.getQueue(Looper.getMainLooper());
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

            String url = v.getUrl().split("#", 2)[0];
            if (!url.equals(v.mWatchUrl) && Youtube.REGEX_WATCH_URL.matches(url)) {
                @Nullable String playlistId = getPlaylistIdFromWatchOrShareUrl(url);
                @Nullable String videoId = getVideoIdFromWatchUrl(url);
                if ((playlistId != null && !playlistId.isEmpty() || videoId != null && !videoId.isEmpty())
                        && (!TextUtils.equals(playlistId, getPlaylistIdFromWatchOrShareUrl(v.mWatchUrl))
                                || !TextUtils.equals(videoId, getVideoIdFromWatchUrl(v.mWatchUrl)))) {
                    boolean fullscreen = v.mChromeClient.mCustomView != null;
                    int videoIndex = getVideoIndexFromWatchOrShareUrl(url);
                    long videoStartMs = getVideoStartMsFromWatchOrShareUrl(url);
                    // FIXME: make this design more reasonable and generic, e.g., maybe in rare
                    //        cases that the idler gets called before the playback url is loaded
                    //        and this takes no effect for the YoutubeIFramePlayer in my tests.
                    if (v.mVisible) {
                        // Player will probably not be ready to load the video from the new watch url
                        // immediately after this view goes back, at which point the player will be
                        // being reloaded.
                        YoutubePlaybackService.peekIfNonnullThenDo(
                                service -> service.mPlayerReady = false);
                        // Goes back to avoid loading a page same as the current loading one
                        // through the url that we'll generate and that may be composed of
                        // not quite the same args as the current url's, but only if this view
                        // is visible to the user, in case this idler has a chance to be called
                        // before the view loads the playback url, which otherwise will cause
                        // the video from the previous url to be played again.
                        v.goBack();
                    }
                    YoutubePlaybackService.startPlayback(
                            v.mContext, playlistId, videoId, videoIndex, videoStartMs, true);
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

    private final class Client extends AndroidWebView.Client {
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
            return super.shouldOverrideUrlLoading(view, url);
        }

        @Override
        public void onPageStarted(WebView view, String url, @Nullable Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (view.canGoBack() && !Youtube.Prefs.get(mContext).retainHistoryVideoPages()) {
                if (getWebPlayer() instanceof YoutubeIFramePlayer) {
                    if (url.equals(Youtube.URLs.PLAYER_API)) {
                        view.clearHistory();
                    }
                } else {
                    if (Youtube.REGEX_WATCH_URL.matches(url)) {
                        view.clearHistory();
                    }
                }
            }
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            @Nullable WebPlayer player = getWebPlayer();
            if (player instanceof YoutubePlayer) {
                ((YoutubePlayer) player).attachListeners();
            }
        }
    }

    private final class ChromeClient extends AndroidWebView.ChromeClient {
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

                mPageListeners.forEach(listener -> listener.onEnterFullscreen(YoutubePlaybackView.this));
            }
        }

        @Override
        public void onHideCustomView() {
            if (mCustomView != null) {
                removeView(mCustomView);
                if (mScrollX != 0 || mScrollY != 0) {
                    Utils.runOnLayoutValid(
                            YoutubePlaybackView.this, () -> scrollTo(mScrollX, mScrollY));
                }
                mCustomView = null;
                mCustomViewCallback = null;

                mPageListeners.forEach(listener -> listener.onExitFullscreen(YoutubePlaybackView.this));
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
