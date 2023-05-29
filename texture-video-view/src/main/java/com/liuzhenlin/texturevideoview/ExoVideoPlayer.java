/*
 * Created on 2019/11/24 4:11 PM.
 * Copyright © 2019–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.collection.SimpleArrayMap;

import com.bumptech.glide.util.Synthetic;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.SingleSampleMediaSource;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.source.smoothstreaming.SsMediaSource;
import com.google.android.exoplayer2.text.CueGroup;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.ExoTrackSelection;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.util.Util;
import com.google.android.exoplayer2.video.VideoSize;
import com.google.android.material.snackbar.Snackbar;
import com.liuzhenlin.common.compat.AudioManagerCompat;
import com.liuzhenlin.common.receiver.HeadsetEventsReceiver;
import com.liuzhenlin.common.receiver.MediaButtonEventReceiver;
import com.liuzhenlin.common.utils.UiUtils;
import com.liuzhenlin.texturevideoview.bean.AudioTrackInfo;
import com.liuzhenlin.texturevideoview.bean.SubtitleTrackInfo;
import com.liuzhenlin.texturevideoview.bean.TrackInfo;
import com.liuzhenlin.texturevideoview.bean.VideoTrackInfo;
import com.liuzhenlin.texturevideoview.utils.Utils;
import com.liuzhenlin.texturevideoview.utils.VideoUtils;

import java.util.LinkedList;
import java.util.List;

/**
 * A sub implementation class of {@link VideoPlayer} to deal with the audio/video playback logic
 * related to the media player component through an {@link com.google.android.exoplayer2.ExoPlayer}
 * object.
 *
 * @author 刘振林
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class ExoVideoPlayer extends VideoPlayer {

    private static final String TAG = "ExoVideoPlayer";

    private static final int $FLAG_PLAY_WHEN_PREPARED = 1 << 31;

    private String mUserAgent;

    @Synthetic ExoPlayer mExoPlayer;
    private DefaultTrackSelector mTrackSelector;
    private MediaSource.Factory mMediaSourceFactory;
    private MediaSource.Factory mTmpMediaSourceFactory;
    private SimpleArrayMap<Uri, String[]/*{mimeType, language}*/> mSubtitles;
    private static SingleSampleMediaSource.Factory sSubtitleSourceFactory;
    private static DataSource.Factory sDefaultDataSourceFactory;

    private final AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener
            = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                // Audio focus gained
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (mExoPlayer.getVolume() != 1.0f) {
                        mExoPlayer.setVolume(1.0f);
                    }
                    play(false);
                    break;

                // Loss of audio focus of unknown duration.
                // This usually happens when the user switches to another audio/video application
                // that causes our view to stop playing, so the video can be thought of as
                // being paused/closed by the user.
                case AudioManager.AUDIOFOCUS_LOSS:
                    if (mVideoView != null && mVideoView.isInForeground()) {
                        // If the view is still in the foreground, pauses the video only.
                        pause(true);
                    } else {
                        // But if this occurs during background playback, we must close the video
                        // to release the resources associated with it.
                        closeVideoInternal(true);
                    }
                    break;

                // Temporarily lose the audio focus and will probably gain it again soon.
                // Must stop the video playback but no need for releasing the ExoPlayer here.
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    pause(false);
                    break;

                // Temporarily lose the audio focus but the playback can continue.
                // The volume of the playback needs to be turned down.
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    if (mExoPlayer.getVolume() != 0.5f) {
                        mExoPlayer.setVolume(0.5f);
                    }
                    break;
            }
        }
    };
    private final AudioFocusRequest mAudioFocusRequest =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(
                                    sDefaultAudioAttrs.getAudioAttributesV21().audioAttributes)
                            .setOnAudioFocusChangeListener(mOnAudioFocusChangeListener)
                            .setAcceptsDelayedFocusGain(true)
                            .build()
                    : null;

    public ExoVideoPlayer(@NonNull Context context) {
        super(context);
    }

    /**
     * @return the user (the person that may be using this class) specified {@link MediaSource.Factory}
     *         for reading the media content(s)
     */
    @Nullable
    public MediaSource.Factory getMediaSourceFactory() {
        return mMediaSourceFactory;
    }

    /**
     * Sets a MediaSourceFactory for creating {@link com.google.android.exoplayer2.source.MediaSource}s
     * to play the provided media stream content (if any), or `null`, the MediaSourceFactory
     * with {@link DefaultDataSource.Factory} will be created to read the media, based on
     * the corresponding media stream type.
     *
     * @param factory a subclass instance of {@link MediaSource.Factory}
     */
    public void setMediaSourceFactory(@Nullable MediaSource.Factory factory) {
        mMediaSourceFactory = factory;
        if (mMediaSourceFactory != null) {
            mTmpMediaSourceFactory = null;
        }
    }

    private SingleSampleMediaSource.Factory getSubtitleSourceFactory() {
        if (sSubtitleSourceFactory == null) {
            sSubtitleSourceFactory = new SingleSampleMediaSource.Factory(getDefaultDataSourceFactory());
        }
        return sSubtitleSourceFactory;
    }

    /**
     * @return the default {@link DataSource.Factory} created by this class, which will be used for
     *         various of {@link MediaSource.Factory}s (if the user specified one is not set).
     */
    @NonNull
    public DataSource.Factory getDefaultDataSourceFactory() {
        if (sDefaultDataSourceFactory == null) {
            sDefaultDataSourceFactory =
                    new DefaultDataSource.Factory(
                            mContext,
                            new DefaultHttpDataSource.Factory().setUserAgent(getUserAgent()));
//            Cache cache =
//                    new SimpleCache(
//                            new File(getBaseVideoCacheDirectory(), "exo"),
//                            new LeastRecentlyUsedCacheEvictor(DEFAULT_MAXIMUM_CACHE_SIZE),
//                            new StandaloneDatabaseProvider(mContext));
//            DataSource.Factory upstreamFactory =
//                    new DefaultDataSource.Factory(
//                            mContext,
//                            new DefaultHttpDataSource.Factory().setUserAgent(getUserAgent()));
//            DataSource.Factory cacheReadDataSourceFactory = new FileDataSource.Factory();
//            CacheDataSink.Factory cacheWriteDataSourceFactory =
//                    new CacheDataSink.Factory()
//                            .setCache(cache)
//                            .setFragmentSize(CacheDataSink.DEFAULT_FRAGMENT_SIZE)
//                            .setBufferSize(1024);
//            sDefaultDataSourceFactory =
//                    new CacheDataSource.Factory()
//                            .setCache(cache)
//                            .setUpstreamDataSourceFactory(upstreamFactory)
//                            .setCacheReadDataSourceFactory(cacheReadDataSourceFactory)
//                            .setCacheWriteDataSinkFactory(cacheWriteDataSourceFactory)
//                            .setFlags(CacheDataSource.FLAG_BLOCK_ON_CACHE
//                                    | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
        }
        return sDefaultDataSourceFactory;
    }

    /**
     * @return a user agent string based on the application name resolved from the context object
     *         of the view this player is bound to and the `exoplayer-core` library version,
     *         which can be used to create a
     *         {@link com.google.android.exoplayer2.upstream.DataSource.Factory} instance for
     *         the {@link MediaSource.Factory} subclasses.
     */
    @NonNull
    public String getUserAgent() {
        if (mUserAgent == null) {
            if (mVideoView != null) {
                mUserAgent = mVideoView.mExoUserAgent;
            } else {
                mUserAgent = Util.getUserAgent(
                        mContext,
                        mContext.getApplicationInfo()
                                .loadLabel(mContext.getPackageManager()).toString());
            }
        }
        return mUserAgent;
    }

    @Override
    public final void setVideoResourceId(int resId) {
        setVideoPath(resId == 0 ? null : "rawresource:///" + resId);
    }

    @Override
    protected void onVideoUriChanged(@Nullable Uri uri) {
        if (mSubtitles != null) {
            mSubtitles.clear();
        }
        super.onVideoUriChanged(uri);
    }

    @Override
    protected boolean isInnerPlayerCreated() {
        return mExoPlayer != null;
    }

    @Override
    protected void onVideoSurfaceChanged(@Nullable Surface surface) {
        if (mExoPlayer != null) {
            mExoPlayer.setVideoSurface(surface);
        }
    }

    @Override
    protected void openVideoInternal(@Nullable Surface surface) {
        if (mExoPlayer == null && mVideoUri != null
                && !(mVideoView != null && surface == null)
                && (mInternalFlags & $FLAG_VIDEO_PAUSED_BY_USER) == 0) {
            mTrackSelector = new DefaultTrackSelector(mContext);
            mExoPlayer = Utils.newExoPlayer(mContext, mTrackSelector);
            mExoPlayer.setVideoSurface(surface);
            mExoPlayer.setAudioAttributes(sDefaultAudioAttrs, false);
            setPlaybackSpeed(mUserPlaybackSpeed);
            mExoPlayer.setRepeatMode(
                    isSingleVideoLoopPlayback() ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
            mExoPlayer.addListener(new Player.Listener() {
                @SuppressLint("SwitchIntDef")
                @Override
                public void onPlaybackStateChanged(int state) {
                    onVideoBufferingStateChanged(state == Player.STATE_BUFFERING);

                    switch (state) {
                        case Player.STATE_READY:
                            if (getPlaybackState() == PLAYBACK_STATE_PREPARING) {
                                restoreTrackSelections();
                                setPlaybackState(PLAYBACK_STATE_PREPARED);
                                if ((mInternalFlags & $FLAG_PLAY_WHEN_PREPARED) != 0) {
                                    play(false);
                                }
                            }
                            break;

                        case Player.STATE_ENDED:
                            onPlaybackCompleted();
                            break;
                    }
                }

                @Override
                public void onTimelineChanged(@NonNull Timeline timeline, int reason) {
                    // Duration had been changed when new Uri was set and before the player was reset.
                    if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) return;

                    if ((mInternalFlags & $FLAG_VIDEO_DURATION_DETERMINED) == 0) {
                        mInternalFlags |= $FLAG_VIDEO_DURATION_DETERMINED;
                    }
                    final long duration = mExoPlayer.getDuration();
                    onVideoDurationChanged(duration == C.TIME_UNSET ? TIME_UNSET : (int) duration);
                    onVideoSourceUpdate();
                }

                @SuppressWarnings("deprecation")
                @Override
                public void onPositionDiscontinuity(int reason) {
                    if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                        onVideoRepeat();
                    }
                }

                @Override
                public void onPlayerError(@NonNull PlaybackException error) {
                    Log.w(TAG, "playback error", error);
                    // Show video error toast
                    final int stringRes;
                    if (error instanceof ExoPlaybackException
                            && ((ExoPlaybackException) error).type == ExoPlaybackException.TYPE_SOURCE) {
                        stringRes = R.string.failedToLoadThisVideo;
                    } else {
                        stringRes = R.string.unknownErrorOccurredWhenVideoIsPlaying;
                    }
                    if (mVideoView != null) {
                        UiUtils.showUserCancelableSnackbar(mVideoView, stringRes, Snackbar.LENGTH_SHORT);
                    } else {
                        Toast.makeText(mContext, stringRes, Toast.LENGTH_SHORT).show();
                    }

                    final boolean playing = isPlaying();
                    setPlaybackState(PLAYBACK_STATE_ERROR);
                    if (playing) {
                        pauseInternal(false);
                    }
                }

                @Override
                public void onVideoSizeChanged(@NonNull VideoSize videoSize) {
                    final int[] size = VideoUtils.correctedVideoSize(
                            videoSize.width, videoSize.height,
                            videoSize.unappliedRotationDegrees,
                            videoSize.pixelWidthHeightRatio);
                    ExoVideoPlayer.this.onVideoSizeChanged(size[0], size[1]);
                }

                @Override
                public void onCues(@NonNull CueGroup cueGroup) {
                    if (mVideoView != null) {
                        mVideoView.showSubtitles(cueGroup.cues);
                    }
                }
            });
            startVideo(true);

            mHeadsetEventsReceiver = new HeadsetEventsReceiver(mContext) {
                @Override
                public void onHeadsetPluggedOutOrBluetoothDisconnected() {
                    pause(true);
                }
            };
            mHeadsetEventsReceiver.register(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        }
    }

    private void startVideo(boolean playWhenPrepared) {
        if (mVideoView != null) {
            mVideoView.cancelDraggingVideoSeekBar(false);
        }
        if (mVideoUri != null) {
            MediaSource mediaSource =
                    obtainMediaSourceFactory(mVideoUri)
                            .createMediaSource(MediaItem.fromUri(mVideoUri));
            if (mSubtitles != null && !mSubtitles.isEmpty()) {
                int size = mSubtitles.size();
                MediaSource[] mediaSources = new MediaSource[size + 1];
                mediaSources[0] = mediaSource;
                for (int i = 0; i < size; i++) {
                    Uri subtitleUri = mSubtitles.keyAt(i);
                    String[] subtitleData = mSubtitles.valueAt(i);
                    mediaSources[i + 1] = getSubtitleSourceFactory().createMediaSource(
                            new MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                                    .setMimeType(subtitleData[0])
                                    .setLanguage(subtitleData[1])
                                    .build(),
                            C.TIME_UNSET);
                }
                mExoPlayer.setMediaSource(new MergingMediaSource(mediaSources));
            } else {
                mExoPlayer.setMediaSource(mediaSource);
            }
            if (playWhenPrepared) {
                mInternalFlags |= $FLAG_PLAY_WHEN_PREPARED;
            } else {
                mInternalFlags &= ~$FLAG_PLAY_WHEN_PREPARED;
            }
            setPlaybackState(PLAYBACK_STATE_PREPARING);
            mExoPlayer.prepare();
        } else {
            setPlaybackState(PLAYBACK_STATE_IDLE);
        }
    }

    /*package*/ MediaSource.Factory obtainMediaSourceFactory(Uri uri) {
        if (mMediaSourceFactory != null) return mMediaSourceFactory;

        int type = Util.inferContentType(uri);
        //noinspection SwitchIntDef
        switch (type) {
            case C.CONTENT_TYPE_DASH:
                if (mTmpMediaSourceFactory instanceof DashMediaSource.Factory) {
                    return mTmpMediaSourceFactory;
                }
                return mTmpMediaSourceFactory =
                        new DashMediaSource.Factory(getDefaultDataSourceFactory());

            case C.CONTENT_TYPE_SS:
                if (mTmpMediaSourceFactory instanceof SsMediaSource.Factory) {
                    return mTmpMediaSourceFactory;
                }
                return mTmpMediaSourceFactory =
                        new SsMediaSource.Factory(getDefaultDataSourceFactory());

            case C.CONTENT_TYPE_HLS:
                if (mTmpMediaSourceFactory instanceof HlsMediaSource.Factory) {
                    return mTmpMediaSourceFactory;
                }
                return mTmpMediaSourceFactory =
                        new HlsMediaSource.Factory(getDefaultDataSourceFactory());

            case C.CONTENT_TYPE_RTSP:
                if (mTmpMediaSourceFactory instanceof RtspMediaSource.Factory) {
                    return mTmpMediaSourceFactory;
                }
                return mTmpMediaSourceFactory =
                        new RtspMediaSource.Factory().setUserAgent(getUserAgent());

            case C.CONTENT_TYPE_OTHER:
                if (mTmpMediaSourceFactory instanceof ProgressiveMediaSource.Factory) {
                    return mTmpMediaSourceFactory;
                }
                return mTmpMediaSourceFactory =
                        new ProgressiveMediaSource.Factory(getDefaultDataSourceFactory());

            default:
                throw new IllegalStateException("Unsupported media stream type: " + type);
        }
    }

    @Override
    public void restartVideo() {
        restartVideo(true);
    }

    @Override
    protected void restartVideo(boolean restoreTrackSelections) {
        restartVideo(restoreTrackSelections, false, true);
    }

    private void restartVideo(
            boolean restoreTrackSelections, boolean restorePlaybackPosition,
            boolean playWhenPrepared) {
        if (!restorePlaybackPosition) {
            // First, resets mSeekOnPlay to TIME_UNSET in case the ExoPlayer object is released.
            // This ensures the video to be started at its beginning position the next time it resumes.
            mSeekOnPlay = TIME_UNSET;
        }
        if (mExoPlayer != null) {
            if (restorePlaybackPosition
                    && mSeekOnPlay == TIME_UNSET && getPlaybackState() != PLAYBACK_STATE_COMPLETED) {
                mSeekOnPlay = getVideoProgress();
            }
            if (restoreTrackSelections) {
                saveTrackSelections();
            }
            // Not clear the $FLAG_VIDEO_DURATION_DETERMINED flag
            mInternalFlags &= ~$FLAG_VIDEO_PAUSED_BY_USER;
            pause(false);
            resetExoPlayer();
            startVideo(playWhenPrepared);
        }
    }

    private void resetExoPlayer() {
        if (getPlaybackState() != PLAYBACK_STATE_IDLE) {
            resetTracks(true);
            stopExoPlayer(true);
        }
    }

    private void stopExoPlayer(boolean reset) {
        //noinspection deprecation
        mExoPlayer.stop(reset);
    }

    @Override
    public void play(boolean fromUser) {
        final int playbackState = getPlaybackState();

        if (mExoPlayer == null) {
            // Opens the video only if this is a user request
            if (fromUser) {
                // If the video playback finished, skip to the next video if possible
                if (playbackState == PLAYBACK_STATE_COMPLETED && !isSingleVideoLoopPlayback()
                        && skipToNextIfPossible() && mExoPlayer != null) {
                    return;
                }

                mInternalFlags &= ~$FLAG_VIDEO_PAUSED_BY_USER;
                openVideo(true);
            } else {
                Log.e(TAG, "Cannot start playback programmatically before the video is opened.");
            }
            return;
        }

        if (!fromUser && (mInternalFlags & $FLAG_VIDEO_PAUSED_BY_USER) != 0) {
            Log.e(TAG, "Cannot start playback programmatically after it was paused by user.");
            return;
        }

        switch (playbackState) {
            case PLAYBACK_STATE_IDLE: // no video is set
                // Already in the preparing or playing state
            case PLAYBACK_STATE_PREPARING:
            case PLAYBACK_STATE_PLAYING:
                break;

            case PLAYBACK_STATE_ERROR:
                // Retries the failed playback after error occurred
                mInternalFlags |= $FLAG_PLAY_WHEN_PREPARED;
                setPlaybackState(PLAYBACK_STATE_PREPARING);
                mExoPlayer.prepare();
                break;

            case PLAYBACK_STATE_COMPLETED:
                if (!isSingleVideoLoopPlayback()
                        && skipToNextIfPossible() && getPlaybackState() != PLAYBACK_STATE_COMPLETED) {
                    break;
                }
                // Starts the video only if we have prepared it for the player
            case PLAYBACK_STATE_PREPARED:
            case PLAYBACK_STATE_PAUSED:
                final int result =
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                                ? mAudioManager.requestAudioFocus(mAudioFocusRequest)
                                : mAudioManager.requestAudioFocus(mOnAudioFocusChangeListener,
                                        AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                switch (result) {
                    case AudioManager.AUDIOFOCUS_REQUEST_FAILED:
                        Log.w(TAG, "Failed to request audio focus");
                        // Starts to play video even if the audio focus is not gained, but it is
                        // best not to happen.
                    case AudioManager.AUDIOFOCUS_REQUEST_GRANTED:
                        // Ensure the player's volume is at its maximum
                        if (mExoPlayer.getVolume() != 1.0f) {
                            mExoPlayer.setVolume(1.0f);
                        }
                        mExoPlayer.setPlayWhenReady(true);
                        if (mSeekOnPlay != TIME_UNSET) {
                            seekToInternal(mSeekOnPlay);
                            mSeekOnPlay = TIME_UNSET;
                        } else if (playbackState == PLAYBACK_STATE_COMPLETED) {
                            seekToInternal(0);
                        }
                        mInternalFlags &= ~$FLAG_VIDEO_PAUSED_BY_USER;
                        onVideoStarted();

                        // Register MediaButtonEventReceiver every time the video starts, which
                        // will ensure it to be the sole receiver of MEDIA_BUTTON intents
                        MediaButtonEventReceiver.setMediaButtonEventHandler(getMediaButtonEventHandler());
                        AudioManagerCompat.registerMediaButtonEventReceiver(mContext, mAudioManager,
                                sMediaButtonEventReceiverComponent);
                        break;

                    case AudioManager.AUDIOFOCUS_REQUEST_DELAYED:
                        // do nothing
                        break;
                }
                break;
        }
    }

    @Override
    public void pause(boolean fromUser) {
        if (isPlaying()) {
            pauseInternal(fromUser);
        }
    }

    /**
     * Similar to {@link #pause(boolean)}}, but does not check the playback state.
     */
    @Synthetic void pauseInternal(boolean fromUser) {
        mExoPlayer.setPlayWhenReady(false);
        mInternalFlags = mInternalFlags & ~$FLAG_VIDEO_PAUSED_BY_USER
                | (fromUser ? $FLAG_VIDEO_PAUSED_BY_USER : 0);
        onVideoStopped();
    }

    @Override
    protected void closeVideoInternal(boolean fromUser) {
        final boolean innerPlayerCreated = mExoPlayer != null;
        if (mVideoView != null) {
            mVideoView.cancelDraggingVideoSeekBar(innerPlayerCreated);
        }

        if (innerPlayerCreated) {
            final int playbackState = getPlaybackState();
            final boolean playing = playbackState == PLAYBACK_STATE_PLAYING;

            if (playbackState != PLAYBACK_STATE_IDLE) {
                if (mSeekOnPlay == TIME_UNSET && playbackState != PLAYBACK_STATE_COMPLETED) {
                    mSeekOnPlay = getVideoProgress();
                }
                saveTrackSelections();

//                pause(fromUser);
                if (playing) {
                    mExoPlayer.setPlayWhenReady(false);
                    mInternalFlags = mInternalFlags & ~$FLAG_VIDEO_PAUSED_BY_USER
                            | (fromUser ? $FLAG_VIDEO_PAUSED_BY_USER : 0);
                }
                stopExoPlayer(false);
            }
            mExoPlayer.release();
            mExoPlayer = null;
            mTrackSelector = null;
            mTmpMediaSourceFactory = null;
            // Resets the cached playback speed to prepare for the next resume of the video player
            mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;

            abandonAudioFocus();
            mHeadsetEventsReceiver.unregister();
            mHeadsetEventsReceiver = null;

            if (playing) {
                onVideoStopped();
            }
        }
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
        } else {
            mAudioManager.abandonAudioFocus(mOnAudioFocusChangeListener);
        }
    }

    @Override
    public void seekTo(int positionMs, boolean fromUser) {
        if (isPlaying()) {
            seekToInternal(positionMs);
        } else {
            mSeekOnPlay = positionMs;
            play(fromUser);
        }
    }

    /**
     * Similar to {@link #seekTo(int, boolean)}, but without check to the playing state.
     */
    private void seekToInternal(int positionMs) {
        mExoPlayer.seekTo(clampedPositionMs(positionMs));
    }

    @Override
    public int getVideoProgress() {
        return clampedPositionMs(getVideoProgress0());
    }

    private int getVideoProgress0() {
        if (mSeekOnPlay != TIME_UNSET) {
            return mSeekOnPlay;
        }
        if (getPlaybackState() == PLAYBACK_STATE_COMPLETED) {
            // If the video completed and the ExoPlayer object was released, we would get 0.
            return mVideoDuration;
        }
        if (mExoPlayer != null) {
            return (int) mExoPlayer.getCurrentPosition();
        }
        return 0;
    }

    @Override
    public int getVideoBufferProgress() {
        if (mExoPlayer != null) {
            return clampedPositionMs((int) mExoPlayer.getBufferedPosition());
        }
        return 0;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public void setPlaybackSpeed(float speed) {
        if (speed != mPlaybackSpeed) {
            mUserPlaybackSpeed = speed;
            if (mExoPlayer != null) {
                mExoPlayer.setPlaybackParameters(new PlaybackParameters(speed));
                super.setPlaybackSpeed(speed);
            }
        }
    }

    @Override
    public void setSingleVideoLoopPlayback(boolean looping) {
        if (looping != isSingleVideoLoopPlayback()) {
            if (mExoPlayer != null) {
                mExoPlayer.setRepeatMode(looping ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
            }
            super.setSingleVideoLoopPlayback(looping);
        }
    }

    private MappingTrackSelector.MappedTrackInfo getCurrentMappedTrackInfo() {
        if (mTrackSelector != null) {
            return mTrackSelector.getCurrentMappedTrackInfo();
        }
        return null;
    }

    @Override
    public boolean hasTrack(int trackType) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) return false;

        final int rendererType = Utils.getTrackTypeForExoPlayer(trackType);
        if (rendererType == C.TRACK_TYPE_UNKNOWN) return false;

        for (int rendererIndex = 0, rendererCount = mappedTrackInfo.getRendererCount();
             rendererIndex < rendererCount;
             rendererIndex++) {
            if (mappedTrackInfo.getRendererType(rendererIndex) == rendererType) {
                TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
                for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
                    TrackGroup trackGroup = trackGroups.get(groupIndex);
                    if (trackGroup.length > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @NonNull
    @Override
    public TrackInfo[] getTrackInfos() {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) return EMPTY_TRACK_INFOS;

        List<TrackInfo> trackInfos = new LinkedList<>();
        for (int rendererIndex = 0, rendererCount = mappedTrackInfo.getRendererCount();
             rendererIndex < rendererCount;
             rendererIndex++) {
            final int rendererType = mappedTrackInfo.getRendererType(rendererIndex);

            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
            for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {

                TrackGroup trackGroup = trackGroups.get(groupIndex);
                for (int trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {

                    Format trackFormat = trackGroup.getFormat(trackIndex);
                    switch (rendererType) {
                        case C.TRACK_TYPE_VIDEO:
                            final int[] videoSize =
                                    VideoUtils.correctedVideoSize(
                                            trackFormat.width,
                                            trackFormat.height,
                                            trackFormat.rotationDegrees,
                                            1.0f);
                            trackInfos.add(
                                    new VideoTrackInfo(
                                            Utils.getExoTrackShortCodec(trackFormat.codecs),
                                            videoSize[0],
                                            videoSize[1],
                                            trackFormat.frameRate,
                                            trackFormat.bitrate));
                            break;
                        case C.TRACK_TYPE_AUDIO:
                            trackInfos.add(
                                    new AudioTrackInfo(
                                            Utils.getExoTrackShortCodec(trackFormat.codecs),
                                            trackFormat.language,
                                            trackFormat.channelCount,
                                            trackFormat.sampleRate,
                                            trackFormat.bitrate));
                            break;
                        case C.TRACK_TYPE_TEXT:
                            trackInfos.add(new SubtitleTrackInfo(trackFormat.language));
                            break;
                    }
                }
            }
        }
        //noinspection ToArrayCallWithZeroLengthArrayArgument
        return trackInfos.toArray(new TrackInfo[trackInfos.size()]);
    }

    @Override
    public void selectTrack(int index) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) return;

        int rendererIndex = -1;
        int groupIndex = -1;
        int trackIndex = -1;
        int globalTrackIndex = -1;
        outer:
        for (int rendererCount = mappedTrackInfo.getRendererCount();
             rendererIndex < rendererCount - 1; ) {
            rendererIndex++;
            if (isSupportedTrackType(mappedTrackInfo.getRendererType(rendererIndex))) {
                TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
                for (groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
                    TrackGroup trackGroup = trackGroups.get(groupIndex);
                    for (trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                        globalTrackIndex++;
                        if (globalTrackIndex == index) {
                            break outer;
                        }
                    }
                }
            }
        }

        if (globalTrackIndex == index) {
            resetTrack(mappedTrackInfo.getRendererType(rendererIndex), true);
            mTrackSelector.setParameters(
                    mTrackSelector.buildUponParameters() // ParamsBuilder
                            .setSelectionOverride(
                                    rendererIndex,
                                    mappedTrackInfo.getTrackGroups(rendererIndex),
                                    new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex)));
        }
    }

    @Override
    public void deselectTrack(int index) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) return;

        int rendererIndex = -1;
        @SuppressWarnings("UnusedAssignment") int groupIndex = -1;
        @SuppressWarnings("UnusedAssignment") int trackIndex = -1;
        int globalTrackIndex = -1;
        outer:
        for (int rendererCount = mappedTrackInfo.getRendererCount();
             rendererIndex < rendererCount - 1; ) {
            rendererIndex++;
            if (isSupportedTrackType(mappedTrackInfo.getRendererType(rendererIndex))) {
                TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
                for (groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
                    TrackGroup trackGroup = trackGroups.get(groupIndex);
                    for (trackIndex = 0; trackIndex < trackGroup.length; trackIndex++) {
                        globalTrackIndex++;
                        if (globalTrackIndex == index) {
                            break outer;
                        }
                    }
                }
            }
        }

        if (globalTrackIndex == index) {
            resetTrack(mappedTrackInfo.getRendererType(rendererIndex), false);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void resetTracks(boolean enabled) {
        resetTrack(C.TRACK_TYPE_VIDEO, enabled);
        resetTrack(C.TRACK_TYPE_AUDIO, enabled);
        resetTrack(C.TRACK_TYPE_TEXT, enabled);
    }

    private void resetTrack(int trackType, boolean enabled) {
        if (!isSupportedTrackType(trackType)) return;

        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) return;

        DefaultTrackSelector.Parameters.Builder builder = mTrackSelector.buildUponParameters();
        for (int rendererIndex = 0, rendererCount = mappedTrackInfo.getRendererCount();
             rendererIndex < rendererCount;
             rendererIndex++) {
            if (mappedTrackInfo.getRendererType(rendererIndex) == trackType) {
                builder
                        .clearSelectionOverrides(rendererIndex)
                        .setRendererDisabled(rendererIndex, !enabled);
            }
        }
        mTrackSelector.setParameters(builder);
    }

    @Override
    public int getSelectedTrackIndex(int trackType) {
        MappingTrackSelector.MappedTrackInfo mappedTrackInfo = getCurrentMappedTrackInfo();
        if (mappedTrackInfo == null) return INVALID_TRACK_INDEX;

        final int rendererType = Utils.getTrackTypeForExoPlayer(trackType);
        if (rendererType == C.TRACK_TYPE_UNKNOWN) return INVALID_TRACK_INDEX;

        DefaultTrackSelector.Parameters trackSelectorParams = mTrackSelector.getParameters();
        for (int rendererIndex = 0, rendererCount = mappedTrackInfo.getRendererCount();
             rendererIndex < rendererCount;
             rendererIndex++) {
            if (mappedTrackInfo.getRendererType(rendererIndex) != rendererType
                    || trackSelectorParams.getRendererDisabled(rendererIndex)) {
                continue;
            }

            TrackGroupArray trackGroups = mappedTrackInfo.getTrackGroups(rendererIndex);
            DefaultTrackSelector.SelectionOverride override =
                    trackSelectorParams.getSelectionOverride(rendererIndex, trackGroups);
            // No override. Track was fully determined by the inner player engine.
            if (override == null) {
                TrackGroupArray currTrackGroups = mExoPlayer.getCurrentTrackGroups();
                for (int currGroupIndex = 0;
                     currGroupIndex < currTrackGroups.length;
                     currGroupIndex++) {

                    TrackGroup currTrackGroup = currTrackGroups.get(currGroupIndex);

                    final int groupIndex = trackGroups.indexOf(currTrackGroup);
                    if (groupIndex == C.INDEX_UNSET) continue;

                    TrackSelectionArray currTrackSelections = mExoPlayer.getCurrentTrackSelections();
                    for (int currSelectionIndex = 0;
                         currSelectionIndex < currTrackSelections.length;
                         currSelectionIndex++) {
                        // XXX: Uncast currTrackSelection to ExoTrackSelection from TrackSelection
                        ExoTrackSelection currTrackSelection =
                                (ExoTrackSelection) currTrackSelections.get(currSelectionIndex);
                        if (currTrackSelection == null
                                || currTrackSelection.getTrackGroup() != currTrackGroup) {
                            continue;
                        }

                        final int trackIndex =
                                currTrackSelection.getIndexInTrackGroup(
                                        currTrackSelection.getSelectedIndex());

                        return globalTrackIndex(mappedTrackInfo, rendererIndex, groupIndex, trackIndex);
                    }
                }
            } else {
                final int groupIndex = override.groupIndex;
                final int trackIndex = override.tracks[0];

                return globalTrackIndex(mappedTrackInfo, rendererIndex, groupIndex, trackIndex);
            }
        }
        return INVALID_TRACK_INDEX;
    }

    private static int globalTrackIndex(MappingTrackSelector.MappedTrackInfo mappedTrackInfo,
                                        int rendererIndex, int groupIndex, int trackIndex) {
        int globalTrackIndex = -1;
        for (int ri = 0, rendererCount = mappedTrackInfo.getRendererCount(); ri < rendererCount; ri++) {
            if (isSupportedTrackType(mappedTrackInfo.getRendererType(ri))) {
                TrackGroupArray tgs = mappedTrackInfo.getTrackGroups(ri);
                for (int gi = 0; gi < tgs.length; gi++) {
                    TrackGroup tg = tgs.get(gi);
                    for (int ti = 0; ti < tg.length; ti++) {
                        globalTrackIndex++;
                        if (ri == rendererIndex && gi == groupIndex && ti == trackIndex) {
                            return globalTrackIndex;
                        }
                    }
                }
            }
        }
        return globalTrackIndex;
    }

    private static boolean isSupportedTrackType(int trackType) {
        switch (trackType) {
            case C.TRACK_TYPE_VIDEO:
            case C.TRACK_TYPE_AUDIO:
            case C.TRACK_TYPE_TEXT:
                return true;
            default:
                return false;
        }
    }

    @Override
    public void addSubtitleSource(@NonNull Uri uri, @NonNull String mimeType, @Nullable String language) {
        if (mSubtitles == null) {
            mSubtitles = new SimpleArrayMap<>(1);
        }
        mSubtitles.put(uri, new String[]{mimeType, language});

        if (mExoPlayer != null) {
            final int playbackState = getPlaybackState();
            final boolean playWhenPrepared =
                    playbackState == PLAYBACK_STATE_PLAYING
                            || playbackState == PLAYBACK_STATE_PREPARING
                                    && (mInternalFlags & $FLAG_PLAY_WHEN_PREPARED) != 0;
            restartVideo(true, true, playWhenPrepared);
        }
    }

    @Override
    protected boolean onPlaybackCompleted() {
        final boolean closed = super.onPlaybackCompleted();
        if (closed) {
            // Since the playback completion state deters the pause(boolean) method from being called
            // within the closeVideoInternal(boolean) method, we need this extra step to add
            // the $FLAG_VIDEO_PAUSED_BY_USER flag into mInternalFlags to denote that the user pauses
            // (closes) the video.
            mInternalFlags |= $FLAG_VIDEO_PAUSED_BY_USER;
            onVideoStopped();
        }
        return closed;
    }
}
