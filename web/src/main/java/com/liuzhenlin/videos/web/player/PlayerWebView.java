package com.liuzhenlin.videos.web.player;

import android.content.Context;
import android.webkit.WebSettings;

import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.NonNullApi;
import com.liuzhenlin.videos.web.BackgroundbleWebView;

@NonNullApi
public abstract class PlayerWebView extends BackgroundbleWebView {

    @Nullable private WebPlayer mPlayer;

    public PlayerWebView(Context context) {
        super(context);
    }

    @Override
    protected void setup(WebSettings settings) {
        super.setup(settings);
        // 缩放操作
        settings.setSupportZoom(true); // 支持缩放，默认为true。是下面那个的前提。
        settings.setBuiltInZoomControls(true); // 设置内置的缩放控件。若为false，则该WebView不可缩放
        settings.setDisplayZoomControls(false); // 隐藏原生的缩放控件
    }

    public void setWebPlayer(@Nullable WebPlayer player) {
        mPlayer = player;
    }

    @Nullable
    public WebPlayer getWebPlayer() {
        return mPlayer;
    }

    public boolean isInFullscreen() {
        return false;
    }

    public abstract void loadPlaylist(String playlistId, @Nullable String videoId);

    public abstract void loadVideo(String videoId);
}