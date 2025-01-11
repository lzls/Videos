/*
 * Created on 2020-12-10 10:08:15 AM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.liuzhenlin.common.utils.Coroutines;
import com.liuzhenlin.common.utils.JCoroutine;
import com.liuzhenlin.common.utils.ShareUtils;
import com.liuzhenlin.common.utils.Synthetic;
import com.liuzhenlin.texturevideoview.TextureVideoView;
import com.liuzhenlin.videos.Configs;
import com.liuzhenlin.videos.Consts;
import com.liuzhenlin.videos.Files;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.bean.Video;
import com.liuzhenlin.videos.model.VideoRepository;
import com.liuzhenlin.videos.view.activity.IVideoView;
import com.liuzhenlin.videos.view.fragment.VideoListItemOpsKt;

import java.io.File;
import java.io.Serializable;
import java.util.List;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;

import static com.liuzhenlin.common.Consts.NO_ID;

/**
 * @author 刘振林
 */
class VideoPresenter extends Presenter<IVideoView> implements IVideoPresenter,
        VideoRepository.Callback {

    @Synthetic VideoRepository mVideoRepository;

    private static final String KEY_VIDEO_INDEX = "kvi";

    @Override
    public void attachToView(@NonNull IVideoView view) {
        super.attachToView(view);
        mVideoRepository = VideoRepository.create(mContext);
        mVideoRepository.setCallback(this);
    }

    @Override
    public void detachFromView(@NonNull IVideoView view) {
        mVideoRepository.dispose();
        mVideoRepository = null;
        super.detachFromView(view);
    }

    @Override
    public void initPlaylistAndRecordCurrentVideoProgress(
            @Nullable Bundle savedInstanceState, @NonNull Intent intent) {
        Video video = mVideoRepository == null ? null : mVideoRepository.getCurrentVideo();
        initPlaylist(savedInstanceState, intent,
                () -> {
                    if (video != null) {
                        recordVideoProgress(video);
                    }
                });
    }

    @Override
    public void initPlaylist(@Nullable Bundle savedInstanceState, @NonNull Intent intent) {
        initPlaylist(savedInstanceState, intent, null);
    }

    private void initPlaylist(
            Bundle savedInstanceState, Intent intent, Runnable runOnInitialized) {
        VideoRepository repo = mVideoRepository;
        if (repo == null) return;

        CoroutineScope viewModelScope = Coroutines.getViewModelScope(this);
        JCoroutine.launch(viewModelScope, Dispatchers.getIO(), () -> {
            final boolean stateRestore = savedInstanceState != null;
            Video[] videos = null;
            int videoIndex = -1;
            Video video;
            boolean initSuceess = false;

            Parcelable[] parcelables = intent.getParcelableArrayExtra(Consts.KEY_VIDEOS);
            if (parcelables != null) {
                final int length = parcelables.length;
                if (length > 0) {
                    videos = new Video[length];
                    for (int i = 0; i < length; i++) {
                        video = (Video) parcelables[i];
                        if (stateRestore) {
                            video.setProgress(repo.getVideoProgressFromDB(video));
                        }
                        videos[i] = video;
                    }
                    if (stateRestore) {
                        videoIndex = savedInstanceState.getInt(KEY_VIDEO_INDEX);
                    } else {
                        videoIndex = intent.getIntExtra(Consts.KEY_SELECTION, 0);
                        if (videoIndex < 0 || videoIndex >= length) {
                            videoIndex = 0;
                        }
                    }
                    initSuceess = true;
                }
            }

            if (!initSuceess) {
                video = intent.getParcelableExtra(Consts.KEY_VIDEO);
                if (video != null) {
                    if (stateRestore) {
                        video.setProgress(repo.getVideoProgressFromDB(video));
                    }
                    videos = new Video[]{video};
                    videoIndex = 0;
                    initSuceess = true;
                }
            }

            if (!initSuceess) {
                Parcelable[] videoUriParcels = (Parcelable[])
                        intent.getSerializableExtra(Consts.KEY_VIDEO_URIS);
                Serializable[] videoTitleSerials = (Serializable[])
                        intent.getSerializableExtra(Consts.KEY_VIDEO_TITLES);
                if (videoUriParcels != null) {
                    final int length = videoUriParcels.length;
                    if (length > 0) {
                        videos = new Video[length];
                        for (int i = 0; i < length; i++) {
                            video = repo.getVideoForUri((Uri) videoUriParcels[i],
                                    (String) (videoTitleSerials != null ? videoTitleSerials[i] : null));
                            if (stateRestore && video.getId() != NO_ID) {
                                video.setProgress(repo.getVideoProgressFromDB(video));
                            }
                            videos[i] = video;
                        }
                        if (stateRestore) {
                            videoIndex = savedInstanceState.getInt(KEY_VIDEO_INDEX);
                        } else {
                            videoIndex = intent.getIntExtra(Consts.KEY_SELECTION, 0);
                            if (videoIndex < 0 || videoIndex >= length) {
                                videoIndex = 0;
                            }
                        }
                        initSuceess = true;
                    }
                }
            }

            if (!initSuceess) {
                Uri uri = intent.getData();
                if (uri == null) {
                    uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                    if (uri == null) {
                        CharSequence uriCharSequence = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
                        if (uriCharSequence != null) {
                            uri = Uri.parse(uriCharSequence.toString());
                        }
                    }
                }
                if (uri != null) {
                    video = repo.getVideoForUri(uri, intent.getStringExtra(Consts.KEY_VIDEO_TITLE));
                    if (stateRestore && video.getId() != NO_ID) {
                        video.setProgress(repo.getVideoProgressFromDB(video));
                    }
                    videos = new Video[]{video};
                    videoIndex = 0;
                    initSuceess = true;
                }
            }

            if (initSuceess) {
                final Video[] vs = videos;
                final int vi = videoIndex;
                JCoroutine.launch(viewModelScope, Dispatchers.getMain(), () -> {
                    if (runOnInitialized != null) {
                        runOnInitialized.run();
                    }
                    repo.setVideos(vs, vi);
                });
            } else {
                JCoroutine.launch(viewModelScope, Dispatchers.getMain(), () -> {
                    if (mView != null) {
                        mView.onPlaylistInitializationFail();
                    }
                });
            }
        });
    }

    @Override
    public void saveInstanceState(@NonNull Bundle outState) {
        if (mVideoRepository != null) {
            outState.putInt(KEY_VIDEO_INDEX, mVideoRepository.getVideoIndex());
        }
    }

    @Override
    public void onViewStopped(@NonNull IVideoView view) {
        super.onViewStopped(view);
        // Saves the video progress when current Activity is put into background
        if (!view.isFinishing()) {
            recordCurrVideoProgress();
        }
    }

    @Override
    public void finish(Runnable finisher) {
        recordCurrVideoProgressAndSetResult();
        finisher.run();
    }

    private void recordCurrVideoProgressAndSetResult() {
        Video[] videos = mVideoRepository == null ? null : mVideoRepository.getVideos();
        if (videos != null) {
            recordCurrVideoProgress();
            if (mView != null) {
                if (videos.length == 1) {
                    mView.setResult(Consts.RESULT_CODE_PLAY_VIDEO,
                            new Intent().putExtra(Consts.KEY_VIDEO, videos[0]));
                } else {
                    mView.setResult(Consts.RESULT_CODE_PLAY_VIDEOS,
                            new Intent().putExtra(Consts.KEY_VIDEOS, videos));
                }
            }
        }
    }

    private void recordCurrVideoProgress() {
        Video video = mVideoRepository == null ? null : mVideoRepository.getCurrentVideo();
        if (video != null) {
            recordVideoProgress(video);
        }
    }

    @Synthetic void recordVideoProgress(Video video) {
        if (mVideoRepository != null && mView != null) {
            mVideoRepository.setVideoProgress(video, mView.getPlayingVideoProgress(), true);
        }
    }

    @Override
    public void playCurrentVideo() {
        Video video = mVideoRepository == null ? null : mVideoRepository.getCurrentVideo();
        if (mView != null && video != null) {
            mView.setVideoToPlay(video);
        }
    }

    @Override
    public void playVideoAt(int position) {
        recordCurrVideoProgress();

        VideoRepository repository = mVideoRepository;
        if (repository != null) {
            if (repository.getVideoIndex() == position) {
                playCurrentVideo();
            } else {
                repository.setVideoIndex(position);
            }
        }
    }

    @Override
    public void onVideosChanged(@Nullable Video[] videos, int index) {
        if (mView != null) {
            mView.onPlaylistInitialized(videos == null ? new Video[0] : videos, index);
        }
    }

    @Override
    public void onVideoIndexChanged(int oldIndex, int index) {
        if (mView != null && mVideoRepository != null) {
            mView.setVideoToPlay(mVideoRepository.getVideos()[index]);
            mView.notifyPlaylistSelectionChanged(oldIndex, index, true);
        }
    }

    @Override
    public void skipToPreviousVideo() {
        recordCurrVideoProgress();
        if (mVideoRepository != null) {
            mVideoRepository.rewindVideoIndex();
        }
    }

    @Override
    public void skipToNextVideo() {
        recordCurrVideoProgress();
        if (mVideoRepository != null) {
            mVideoRepository.forwardVideoIndex();
        }
    }

    @Override
    public void onCurrentVideoStarted() {
        Video video = mVideoRepository == null ? null : mVideoRepository.getCurrentVideo();
        if (video != null) {
            int progress = video.getProgress();
            if (progress > 0 && progress < video.getDuration() - Configs.TOLERANCE_VIDEO_DURATION) {
                if (mView != null) {
                    mView.seekPositionOnVideoStarted(progress);
                }
                mVideoRepository.setVideoProgress(video, 0, false);
            }
        }
    }

    @Override
    public void shareCurrentVideo(@NonNull Context context) {
        Video video = mVideoRepository == null ? null : mVideoRepository.getCurrentVideo();
        if (video != null) {
            VideoListItemOpsKt.shareVideo(context, video);
        }
    }

    @Override
    public void shareCapturedVideoPhoto(@NonNull Context context, @NonNull File photo) {
        ShareUtils.shareFile(context, Files.PROVIDER_AUTHORITY, photo, "image/*");
    }

    @NonNull
    @Override
    public TextureVideoView.PlayListAdapter<? extends IVideoView.PlaylistViewHolder> newPlaylistAdapter() {
        return new VideoEpisodesAdapter();
    }

    private final class VideoEpisodesAdapter
            extends TextureVideoView.PlayListAdapter<IVideoView.PlaylistViewHolder> {
        VideoEpisodesAdapter() {
        }

        @NonNull
        @Override
        public IVideoView.PlaylistViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return mView.newPlaylistViewHolder(parent);
        }

        @Override
        public void onBindViewHolder(@NonNull IVideoView.PlaylistViewHolder holder,
                                     int position, @NonNull List<Object> payloads) {
            super.onBindViewHolder(holder, position, payloads);
            holder.bindData(mVideoRepository.getVideos()[position], position,
                    position == mVideoRepository.getVideoIndex(), payloads);
        }

        @Override
        public void loadItemImages(@NonNull IVideoView.PlaylistViewHolder holder) {
            Video video = mVideoRepository.getVideos()[holder.getBindingAdapterPosition()];
            holder.loadVideoThumb(video);
        }

        @Override
        public void cancelLoadingItemImages(@NonNull IVideoView.PlaylistViewHolder holder) {
            holder.cancelLoadingVideoThumb();
        }

        @Override
        public int getItemCount() {
            Video[] videos = mVideoRepository == null ? null : mVideoRepository.getVideos();
            return videos == null ? 0 : videos.length;
        }

        @Override
        public void onItemClick(@NonNull IVideoView.PlaylistViewHolder holder, int position) {
            if (position == mVideoRepository.getVideoIndex()) {
                mView.showUserCancelableSnackbar(R.string.theVideoIsPlaying, Snackbar.LENGTH_SHORT);
            } else {
                playVideoAt(position);
            }
        }
    }
}
