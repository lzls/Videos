/*
 * Created on 2022-2-17 2:47:13 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.common.utils.Utils;
import com.liuzhenlin.videos.web.Configs;
import com.liuzhenlin.videos.web.player.PlayerWebView;
import com.liuzhenlin.videos.web.player.WebPlayer;

@NonNullApi
public class YoutubePlaybackView extends PlayerWebView {

    @SuppressWarnings("NotNullFieldNotInitialized")
    private ChromeClient mChromeClient;

    public YoutubePlaybackView(Context context) {
        super(context);
    }

    @Override
    public void loadPlaylist(String playlistId, @Nullable String videoId) {
        @Nullable WebPlayer player = getWebPlayer();
        if (player instanceof YoutubeIFramePlayer) {
            loadDataWithBaseURL(Youtube.URLs.PLAYER_API, Youtube.getPlayListHTML(playlistId, videoId),
                    "text/html", null, null);
        } else if (player != null) {
            player.loadPlaylist(playlistId, videoId);
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
    }

    @Override
    protected boolean shouldStopWhenDetachedFromWindow() {
        return false;
    }

    public boolean isInFullscreen() {
        return getWebPlayer() instanceof YoutubeIFramePlayer || mChromeClient.mCustomView != null;
    }

    private final class Client extends WebViewClient {
        Client() {}

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return YoutubePlaybackService.startPlaybackIfUrlIsWatchUrl(view.getContext(), url);
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
                setVisibility(View.GONE);
                ((ViewGroup) getParent()).addView(view, 0);

                @Nullable YoutubePlaybackActivity ytPlaybackActivity = YoutubePlaybackActivity.get();
                if (ytPlaybackActivity != null) {
                    ytPlaybackActivity.enterFullscreen();
                }
            }
        }

        @Override
        public void onHideCustomView() {
            if (mCustomView != null) {
                if (mCustomViewCallback != null) {
                    mCustomViewCallback.onCustomViewHidden();
                    mCustomViewCallback = null;
                }
                if (mCustomView.getParent() != null) {
                    ((ViewGroup) mCustomView.getParent()).removeView(mCustomView);
                    setVisibility(View.VISIBLE);
                }
                mCustomView = null;

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
