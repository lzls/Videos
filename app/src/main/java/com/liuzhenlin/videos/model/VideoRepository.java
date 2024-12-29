/*
 * Created on 2024-12-23 10:01:33 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.videos.bean.Video;

public interface VideoRepository extends Repository<VideoRepository.Callback> {

    @NonNull
    static VideoRepository create(@NonNull Context context) {
        return new VideoRepositoryImpl(context);
    }

    @NonNull Video getVideoForUri(@NonNull Uri uri, @Nullable String videoTitle);

    void setVideos(@Nullable Video[] videos, int videoIndex);
    @Nullable Video[] getVideos();

    @Nullable Video getCurrentVideo();

    int getVideoIndex();
    void setVideoIndex(int index);
    void rewindVideoIndex();
    void forwardVideoIndex();

    int getVideoProgressFromDB(@NonNull Video video);
    void setVideoProgress(@NonNull Video video, int progress, boolean updateDB);

    public interface Callback extends Repository.Callback {
        void onVideosChanged(@Nullable Video[] videos, int index);
        void onVideoIndexChanged(int oldIndex, int index);
    }
}
