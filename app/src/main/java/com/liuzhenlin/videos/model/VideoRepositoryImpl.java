/*
 * Created on 2024-12-23 10:05:28 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.common.utils.AppScope;
import com.liuzhenlin.common.utils.FileUtils;
import com.liuzhenlin.common.utils.JCoroutine;
import com.liuzhenlin.videos.ExtentionsKt;
import com.liuzhenlin.videos.bean.Video;
import com.liuzhenlin.videos.dao.VideoListItemDao;

import kotlinx.coroutines.Dispatchers;

import static com.liuzhenlin.common.Consts.NO_ID;

class VideoRepositoryImpl extends BaseRepository<VideoRepository.Callback>
        implements VideoRepository {

    private Video[] mVideos;
    private int mVideoIndex = -1;

    public VideoRepositoryImpl(@NonNull Context context) {
        super(context);
    }

    @NonNull
    @Override
    public Video getVideoForUri(@NonNull Uri uri, @Nullable String videoTitle) {
        String videoUrl = FileUtils.UriResolver.getPath(mContext, uri);
        if (videoUrl == null) {
            videoUrl = uri.toString();
        }

        Video video = VideoListItemDao.getSingleton(mContext).queryVideoByPath(videoUrl);
        if (video == null) {
            video = new Video();
            video.setId(NO_ID);
            video.setPath(videoUrl);
            if (videoTitle != null) {
                video.setName(videoTitle);
            } else {
                video.setName(FileUtils.getFileNameFromFilePath(videoUrl));
            }
        }
        return video;
    }

    @Override
    public void setVideos(@Nullable Video[] videos, int videoIndex) {
        if (!ExtentionsKt.allEqual(mVideos, videos)) {
            mVideos = videos;
            mVideoIndex = videoIndex;
            if (mCallback != null) {
                mCallback.onVideosChanged(videos, videoIndex);
            }
        } else {
            setVideoIndex(videoIndex);
        }
    }

    @Nullable
    @Override
    public Video[] getVideos() {
        return mVideos;
    }

    @Nullable
    @Override
    public Video getCurrentVideo() {
        return mVideos == null ? null : mVideos[mVideoIndex];
    }

    @Override
    public int getVideoIndex() {
        return mVideoIndex;
    }

    @Override
    public void setVideoIndex(int index) {
        if (mVideos != null) {
            int oldIndex = mVideoIndex;
            if (oldIndex != index) {
                mVideoIndex = index;
                if (mCallback != null) {
                    mCallback.onVideoIndexChanged(oldIndex, index);
                }
            }
        }
    }

    @Override
    public void rewindVideoIndex() {
        if (mVideos != null) {
            final int oldVideoIndex = mVideoIndex;
            if (oldVideoIndex == 0) {
                mVideoIndex = mVideos.length - 1;
            } else {
                --mVideoIndex;
            }
            if (mCallback != null) {
                mCallback.onVideoIndexChanged(oldVideoIndex, mVideoIndex);
            }
        }
    }

    @Override
    public void forwardVideoIndex() {
        if (mVideos != null) {
            final int oldVideoIndex = mVideoIndex;
            if (oldVideoIndex == mVideos.length - 1) {
                mVideoIndex = 0;
            } else {
                ++mVideoIndex;
            }
            if (mCallback != null) {
                mCallback.onVideoIndexChanged(oldVideoIndex, mVideoIndex);
            }
        }
    }

    @Override
    public int getVideoProgressFromDB(@NonNull Video video) {
        return VideoListItemDao.getSingleton(mContext).getVideoProgress(video.getId());
    }

    @Override
    public void setVideoProgress(@NonNull Video video, int progress, boolean updateDB) {
        video.setProgress(progress);
        if (updateDB) {
            final long id = video.getId();
            if (id != NO_ID) {
                JCoroutine.launch(AppScope.INSTANCE, Dispatchers.getIO(),
                        () -> VideoListItemDao.getSingleton(mContext).setVideoProgress(id, progress));
            }
        }
    }
}
