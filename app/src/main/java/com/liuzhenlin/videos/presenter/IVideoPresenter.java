/*
 * Created on 2020-12-10 10:06:27 AM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.texturevideoview.TextureVideoView;
import com.liuzhenlin.videos.view.activity.IVideoView;

import java.io.File;

/**
 * @author 刘振林
 */
public interface IVideoPresenter extends IPresenter<IVideoView> {

    void initPlaylist(@Nullable Bundle savedInstanceState, @NonNull Intent intent);
    void initPlaylistAndRecordCurrentVideoProgress(
            @Nullable Bundle savedInstanceState, @NonNull Intent intent);
    void saveInstanceState(@NonNull Bundle outState);

    void finish(Runnable finisher);

    void playCurrentVideo();
    void playVideoAt(int position);

    void skipToPreviousVideo();
    void skipToNextVideo();

    void onCurrentVideoStarted();

    void shareCurrentVideo(@NonNull Context context);
    void shareCapturedVideoPhoto(@NonNull Context context, @NonNull File photo);

    @NonNull
    TextureVideoView.PlayListAdapter<? extends IVideoView.PlaylistViewHolder> newPlaylistAdapter();

    @NonNull
    static IVideoPresenter newInstance() {
        return new VideoPresenter();
    }
}
