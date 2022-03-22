/*
 * Created on 2022-2-17 2:47:13 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.content.Context;
import android.graphics.Bitmap;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.videos.web.player.PlayerWebView;

@NonNullApi
public class YoutubePlaybackView extends PlayerWebView {

    public YoutubePlaybackView(Context context) {
        super(context);
    }

    @Override
    public void loadPlaylist(String playlistId, @Nullable String videoId) {
        loadDataWithBaseURL(Youtube.URLs.PLAYER_API, Youtube.getPlayListHTML(playlistId, videoId),
                "text/html", null, null);
    }

    @Override
    public void loadVideo(String videoId) {
        loadDataWithBaseURL(Youtube.URLs.PLAYER_API, Youtube.getVideoHTML(videoId),
                "text/html", null, null);
    }

    @Override
    protected void setup(WebSettings settings) {
        super.setup(settings);
        settings.setUserAgentString(UserAgent.getUaDesktop(settings));
        addJavascriptInterface(new YoutubeJsInterface(mContext), YoutubeJsInterface.NAME);
        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return YoutubePlaybackService.startPlaybackIfUrlIsWatchUrl(view.getContext(), url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (url.equals(Youtube.URLs.PLAYER_API) && view.canGoBack()) {
                    view.clearHistory();
                }
            }
        });
    }

    @Override
    protected boolean shouldStopWhenDetachedFromWindow() {
        return false;
    }
}
