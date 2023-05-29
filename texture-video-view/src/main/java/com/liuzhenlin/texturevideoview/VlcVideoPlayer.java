/*
 * Created on 2020-3-5 8:44:30 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.texturevideoview;

import android.content.Context;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RawRes;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;

import com.bumptech.glide.util.Synthetic;
import com.google.android.material.snackbar.Snackbar;
import com.liuzhenlin.common.compat.AudioManagerCompat;
import com.liuzhenlin.common.receiver.HeadsetEventsReceiver;
import com.liuzhenlin.common.receiver.MediaButtonEventReceiver;
import com.liuzhenlin.common.utils.URLUtils;
import com.liuzhenlin.common.utils.UiUtils;
import com.liuzhenlin.texturevideoview.bean.AudioTrackInfo;
import com.liuzhenlin.texturevideoview.bean.SubtitleTrackInfo;
import com.liuzhenlin.texturevideoview.bean.TrackInfo;
import com.liuzhenlin.texturevideoview.bean.VideoTrackInfo;
import com.liuzhenlin.texturevideoview.utils.VideoUtils;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;
import org.videolan.libvlc.interfaces.IVLCVout;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * A sub implementation class of {@link VideoPlayer} to deal with the audio/video playback logic
 * related to the media player component through a {@link org.videolan.libvlc.MediaPlayer} object.
 *
 * @author 刘振林
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
public class VlcVideoPlayer extends VideoPlayer {

    private static final String TAG = "VlcVideoPlayer";

    /**
     * If true, MediaPlayer is moving the media to some specified time position
     */
    private static final int $FLAG_SEEKING = 1 << 31;

    /**
     * If true, MediaPlayer is temporarily pausing playback internally in order to buffer more data.
     */
    private static final int $FLAG_BUFFERING = 1 << 30;

    private LibVLC mLibVLC;
    @Synthetic MediaPlayer mMediaPlayer;

    /**
     * How much of the network-based video has been buffered from the media stream received
     * through progressive HTTP download.
     */
    private int mBuffering;

    private final AudioManager.OnAudioFocusChangeListener mOnAudioFocusChangeListener
            = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                // Audio focus gained
                case AudioManager.AUDIOFOCUS_GAIN:
                    if (mMediaPlayer.getVolume() != 100) {
                        mMediaPlayer.setVolume(100);
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
                // Must stop the video playback but no need for releasing the MediaPlayer here.
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    pause(false);
                    break;

                // Temporarily lose the audio focus but the playback can continue.
                // The volume of the playback needs to be turned down.
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    if (mMediaPlayer.getVolume() != 50) {
                        mMediaPlayer.setVolume(50);
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

    private final View.OnLayoutChangeListener mOnVideoLayoutChangeListener =
            new OnVideoLayoutChangeListener(this);

//    private static HttpProxyCacheServer sCacheServer;

//    private HttpProxyCacheServer getCacheServer() {
//        if (sCacheServer == null) {
//            sCacheServer = new HttpProxyCacheServer.Builder(mContext)
//                    .cacheDirectory(new File(getBaseVideoCacheDirectory(), "vlc"))
//                    .maxCacheSize(DEFAULT_MAXIMUM_CACHE_SIZE)
//                    .build();
//        }
//        return sCacheServer;
//    }

    public VlcVideoPlayer(@NonNull Context context) {
        super(context);
    }

    @Override
    public void setVideoView(@Nullable AbsTextureVideoView videoView) {
        if (mVideoView != videoView) {
            View oldVideoDisplayer = getVideoDisplayerFromVideoView(mVideoView);
            if (oldVideoDisplayer != null) {
                oldVideoDisplayer.removeOnLayoutChangeListener(mOnVideoLayoutChangeListener);
            }

            View videoDisplayer = getVideoDisplayerFromVideoView(videoView);
            if (videoDisplayer != null) {
                videoDisplayer.addOnLayoutChangeListener(mOnVideoLayoutChangeListener);
                if (mMediaPlayer != null) {
                    mMediaPlayer.getVLCVout().setWindowSize(
                            videoDisplayer.getWidth(), videoDisplayer.getHeight());
                }
            }

            super.setVideoView(videoView);
        }
    }

    private View getVideoDisplayerFromVideoView(AbsTextureVideoView videoView) {
        if (videoView != null) {
            return videoView.findViewById(R.id.textureView);
        }
        return null;
    }

    @Override
    public void setVideoResourceId(@RawRes int resId) {
        Log.e(TAG,
                "",
                new UnsupportedOperationException("Play via raw resource id is not yet supported"));
    }

    @Override
    protected boolean isInnerPlayerCreated() {
        return mMediaPlayer != null;
    }

    @Override
    protected void onVideoSurfaceChanged(@Nullable Surface surface) {
        if (mMediaPlayer != null) {
            IVLCVout vlcVout = mMediaPlayer.getVLCVout();
            vlcVout.detachViews();
            if (surface != null) {
                vlcVout.setVideoSurface(surface, null);
//                vlcVout.setSubtitlesSurface(surface, null);
                vlcVout.attachViews();
            }
        }
    }

    @Override
    protected void openVideoInternal(@Nullable Surface surface) {
        if (mMediaPlayer == null && mVideoUri != null
                && !(mVideoView != null && surface == null)
                && (mInternalFlags & $FLAG_VIDEO_PAUSED_BY_USER) == 0) {
            ArrayList<String> options = new ArrayList<>(1);
            options.add("-vvv");
            mLibVLC = new LibVLC(mContext, options);

            mMediaPlayer = new MediaPlayer(mLibVLC);
            IVLCVout vout = mMediaPlayer.getVLCVout();

            View videoDisplayer = getVideoDisplayerFromVideoView(mVideoView);
            if (videoDisplayer != null) {
                vout.setWindowSize(videoDisplayer.getWidth(), videoDisplayer.getHeight());
            }
            mMediaPlayer.setAspectRatio(null);
            mMediaPlayer.setScale(0);
            mMediaPlayer.setVideoScale(MediaPlayer.ScaleType.SURFACE_BEST_FIT);
            vout.setVideoSurface(surface, null);
//            vout.setSubtitlesSurface(surface, null);
            vout.attachViews();

//            mMediaPlayer.setLooping(isSingleVideoLoopPlayback());
            setPlaybackSpeed(mUserPlaybackSpeed);

            mMediaPlayer.setEventListener(event -> {
                switch (event.type) {
                    case MediaPlayer.Event.Opening:
                        onVideoBufferingStateChanged(true);
                        break;

                    case MediaPlayer.Event.Buffering:
                        if (event.getBuffering() >= 100.0f) {
                            if (getPlaybackState() == PLAYBACK_STATE_PREPARING) {
                                restoreTrackSelections();
                                setPlaybackState(PLAYBACK_STATE_PREPARED);
                                play(false);
                            }

                            mInternalFlags &= ~$FLAG_BUFFERING;
                            if ((mInternalFlags & $FLAG_SEEKING) == 0) {
                                onVideoBufferingStateChanged(false);
                            }
                        } else {
                            mInternalFlags |= $FLAG_BUFFERING;
                            if ((mInternalFlags & $FLAG_SEEKING) == 0) {
                                onVideoBufferingStateChanged(true);
                            }
                        }
                        break;

                    case MediaPlayer.Event.Playing:
                    case MediaPlayer.Event.Paused:
                    case MediaPlayer.Event.Stopped:
                        break;

                    case MediaPlayer.Event.EndReached:
                        if (isSingleVideoLoopPlayback()) {
                            mMediaPlayer.stop();
                            mMediaPlayer.play();
                            onVideoRepeat();
                        } else {
                            onPlaybackCompleted();
                        }
                        break;

                    case MediaPlayer.Event.EncounteredError:
                        showVideoErrorToast(org.videolan.libvlc.media.MediaPlayer.MEDIA_ERROR_UNKNOWN);

                        onVideoBufferingStateChanged(false);
                        final boolean playing = isPlaying();
                        setPlaybackState(PLAYBACK_STATE_ERROR);
                        if (playing) {
                            pauseInternal(false);
                        }
                        break;

                    case MediaPlayer.Event.TimeChanged:
                        if ((mInternalFlags & $FLAG_SEEKING) != 0
                                && (mInternalFlags & $FLAG_BUFFERING) == 0) {
                            mInternalFlags &= ~$FLAG_SEEKING;
                            onVideoBufferingStateChanged(false);
                        }
                        break;

                    case MediaPlayer.Event.PositionChanged:
                        if (mVideoWidth == 0 && mVideoHeight == 0) {
                            IMedia.VideoTrack vtrack = mMediaPlayer.getCurrentVideoTrack();
                            if (vtrack == null) break;

                            final int unappliedRotationDegrees;
                            switch (vtrack.orientation) {
                                case IMedia.VideoTrack.Orientation.LeftBottom:
                                    unappliedRotationDegrees = 90;
                                    break;
                                case IMedia.VideoTrack.Orientation.RightTop:
                                    unappliedRotationDegrees = 270;
                                    break;
                                default:
                                    unappliedRotationDegrees = 0;
                                    break;
                            }
                            final float pixelWidthHeightRatio = (float) vtrack.sarNum / vtrack.sarDen;

                            final int[] videoSize =
                                    VideoUtils.correctedVideoSize(
                                            vtrack.width, vtrack.height,
                                            unappliedRotationDegrees,
                                            pixelWidthHeightRatio);
                            onVideoSizeChanged(videoSize[0], videoSize[1]);
                        }
                        break;

                    case MediaPlayer.Event.LengthChanged:
                        if ((mInternalFlags & $FLAG_VIDEO_DURATION_DETERMINED) == 0) {
                            mInternalFlags |= $FLAG_VIDEO_DURATION_DETERMINED;
                        }
                        final int duration = (int) event.getLengthChanged();
                        onVideoDurationChanged(duration == 0 ? TIME_UNSET : duration);
                        break;
                }
            });
//            mMediaPlayer.setOnVideoSizeChangedListener(
//                    (mp, width, height) -> onVideoSizeChanged(width, height));
//            mMediaPlayer.setOnBufferingUpdateListener(
//                    (mp, percent) -> mBuffering = Utils.roundFloat(mVideoDuration * percent / 100f));

            startVideo();

            mHeadsetEventsReceiver = new HeadsetEventsReceiver(mContext) {
                @Override
                public void onHeadsetPluggedOutOrBluetoothDisconnected() {
                    pause(true);
                }
            };
            mHeadsetEventsReceiver.register(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private void showVideoErrorToast(int errorType) {
        final int stringRes;
        switch (errorType) {
            case org.videolan.libvlc.media.MediaPlayer.MEDIA_ERROR_IO:
                stringRes = R.string.failedToLoadThisVideo;
                break;
            case org.videolan.libvlc.media.MediaPlayer.MEDIA_ERROR_UNSUPPORTED:
            case org.videolan.libvlc.media.MediaPlayer.MEDIA_ERROR_MALFORMED:
                stringRes = R.string.videoInThisFormatIsNotSupported;
                break;
            default:
                stringRes = R.string.unknownErrorOccurredWhenVideoIsPlaying;
                break;
        }
        if (mVideoView != null) {
            UiUtils.showUserCancelableSnackbar(mVideoView, stringRes, Snackbar.LENGTH_SHORT);
        } else {
            Toast.makeText(mContext, stringRes, Toast.LENGTH_SHORT).show();
        }
    }

    private void startVideo() {
        if (mVideoView != null) {
            mVideoView.cancelDraggingVideoSeekBar(false);
        }
        if (mVideoUri != null) {
            final String url = mVideoUri.toString();
            final IMedia media;
            if (URLUtils.isNetworkUrl(url)) {
                media = new Media(mLibVLC, mVideoUri);
            } else {
                media = new Media(mLibVLC, url);
            }
            setPlaybackState(PLAYBACK_STATE_PREPARING);
            // mMediaPlayer.prepareAsync()
            media.addOption(":video-paused");
            mMediaPlayer.play(media);
        } else {
            mMediaPlayer.play("");
            mMediaPlayer.stop();
            setPlaybackState(PLAYBACK_STATE_IDLE);
        }
    }

    @Override
    public void restartVideo() {
        restartVideo(true);
    }

    @Override
    protected void restartVideo(boolean restoreTrackSelections) {
        // First, resets mSeekOnPlay to TIME_UNSET in case the MediaPlayer object is released.
        // This ensures the video to be started at its beginning position the next time it resumes.
        mSeekOnPlay = TIME_UNSET;
        if (mMediaPlayer != null) {
            if (restoreTrackSelections) {
                restoreTrackSelections();
            }
            // Not clear the $FLAG_VIDEO_DURATION_DETERMINED flag
            mInternalFlags &= ~($FLAG_VIDEO_PAUSED_BY_USER
                    | $FLAG_SEEKING
                    | $FLAG_BUFFERING);
            pause(false);
            // Resets below to prepare for the next resume of the video player
            mBuffering = 0;
            resetVlcPlayer();
            startVideo();
        }
    }

    private void resetVlcPlayer() {
        if (getPlaybackState() != PLAYBACK_STATE_IDLE) {
            mMediaPlayer.stop();
//            mMediaPlayer.reset();
            onVideoBufferingStateChanged(false);
        }
    }

    @Override
    public void play(boolean fromUser) {
        final int playbackState = getPlaybackState();

        if (mMediaPlayer == null) {
            // Opens the video only if this is a user request
            if (fromUser) {
                // If the video playback finished, skip to the next video if possible
                if (playbackState == PLAYBACK_STATE_COMPLETED && !isSingleVideoLoopPlayback()
                        && skipToNextIfPossible() && mMediaPlayer != null) {
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
                mInternalFlags &= ~($FLAG_SEEKING | $FLAG_BUFFERING);
                mBuffering = 0;
                if (mSeekOnPlay == TIME_UNSET) {
                    // Record the current playback position only if there is no external program code
                    // requesting a position seek in this case.
                    mSeekOnPlay = getVideoProgress();
                }
                saveTrackSelections();
                resetVlcPlayer();
                startVideo();
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
                        if (mMediaPlayer.getVolume() != 100) {
                            mMediaPlayer.setVolume(100);
                        }
                        mMediaPlayer.play();
                        // Position seek each time works correctly only if the player engine is started
                        if (mSeekOnPlay != TIME_UNSET) {
                            seekToInternal(mSeekOnPlay);
                            mSeekOnPlay = TIME_UNSET;
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
    private void pauseInternal(boolean fromUser) {
        mMediaPlayer.pause();
        mInternalFlags = mInternalFlags & ~$FLAG_VIDEO_PAUSED_BY_USER
                | (fromUser ? $FLAG_VIDEO_PAUSED_BY_USER : 0);
        onVideoStopped();
    }

    @Override
    protected void closeVideoInternal(boolean fromUser) {
        final boolean innerPlayerCreated = mMediaPlayer != null;
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
                    mMediaPlayer.pause();
                    mInternalFlags = mInternalFlags & ~$FLAG_VIDEO_PAUSED_BY_USER
                            | (fromUser ? $FLAG_VIDEO_PAUSED_BY_USER : 0);
                }
            }
            releaseMediaPlayer();
            // Not clear the $FLAG_VIDEO_DURATION_DETERMINED flag
            mInternalFlags &= ~($FLAG_SEEKING | $FLAG_BUFFERING);
            // Resets below to prepare for the next resume of the video player
            mPlaybackSpeed = DEFAULT_PLAYBACK_SPEED;
            mBuffering = 0;

            abandonAudioFocus();
            mHeadsetEventsReceiver.unregister();
            mHeadsetEventsReceiver = null;

            if (playing) {
                onVideoStopped();
            }
        }
    }

    private void releaseMediaPlayer() {
        mMediaPlayer.getVLCVout().detachViews();
        if (getPlaybackState() != PLAYBACK_STATE_IDLE) {
            mMediaPlayer.stop();
        }
        mMediaPlayer.release();
        mMediaPlayer = null;
        mLibVLC.release();
        mLibVLC = null;
        onVideoBufferingStateChanged(false);
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
        // Unable to seek while streaming live content
        if (mVideoDuration != TIME_UNSET) {
            mInternalFlags |= $FLAG_SEEKING;
            if ((mInternalFlags & $FLAG_BUFFERING) == 0) {
                onVideoBufferingStateChanged(true);
            }
            positionMs = clampedPositionMs(positionMs);
            mMediaPlayer.setTime(positionMs);
        }
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
            // If the video completed and the MediaPlayer object was released, we would get 0.
            return mVideoDuration;
        }
        if (mMediaPlayer != null) {
            return (int) mMediaPlayer.getTime();
        }
        return 0;
    }

    @Override
    public int getVideoBufferProgress() {
        return clampedPositionMs(mBuffering);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Override
    public void setPlaybackSpeed(float speed) {
        if (speed != mPlaybackSpeed) {
            mUserPlaybackSpeed = speed;
            if (mMediaPlayer != null) {
                mMediaPlayer.setRate(speed);
                super.setPlaybackSpeed(speed);
            }
        }
    }

//    @Override
//    public void setSingleVideoLoopPlayback(boolean looping) {
//        if (looping != isSingleVideoLoopPlayback()) {
//            if (mMediaPlayer != null) {
//                mMediaPlayer.setLooping(looping);
//            }
//            super.setSingleVideoLoopPlayback(looping);
//        }
//    }

    @Override
    public boolean hasTrack(int trackType) {
        if (mMediaPlayer != null) {
            switch (trackType) {
                case TrackInfo.TRACK_TYPE_VIDEO:
                    return mMediaPlayer.getVideoTracksCount() > 0;
                case TrackInfo.TRACK_TYPE_AUDIO:
                    return mMediaPlayer.getAudioTracksCount() > 0;
                case TrackInfo.TRACK_TYPE_SUBTITLE:
                    return mMediaPlayer.getSpuTracksCount() > 0;
            }
        }
        return false;
    }

    @NonNull
    @Override
    public TrackInfo[] getTrackInfos() {
        if (mMediaPlayer != null) {
            IMedia media = mMediaPlayer.getMedia();
            if (media == null) return EMPTY_TRACK_INFOS;

            final int trackCount = media.getTrackCount();
            if (trackCount == 0) return EMPTY_TRACK_INFOS;

            MediaPlayer.TrackDescription[] vtds = mMediaPlayer.getVideoTracks();
            if (vtds != null) {
                for (MediaPlayer.TrackDescription vtd : vtds) {
                    System.out.println("VideoTrackDescription's id = " + vtd.id);
                }
            }
            MediaPlayer.TrackDescription[] atds = mMediaPlayer.getAudioTracks();
            if (atds != null) {
                for (MediaPlayer.TrackDescription atd : atds) {
                    System.out.println("AudioTrackDescription's id = " + atd.id);
                }
            }
            MediaPlayer.TrackDescription[] stds = mMediaPlayer.getSpuTracks();
            if (stds != null) {
                for (MediaPlayer.TrackDescription std : stds) {
                    System.out.println("SubtitleTrackDescription's id = " + std.id);
                }
            }

            List<TrackInfo> trackInfos = new LinkedList<>();
            for (int i = 0; i < trackCount; i++) {
                IMedia.Track track = media.getTrack(i);
                if (track == null) continue;

                TrackInfo trackInfo;
                switch (track.type) {
                    case IMedia.Track.Type.Video:
                        IMedia.VideoTrack vtrack = (IMedia.VideoTrack) track;

                        final int unappliedRotationDegrees;
                        switch (vtrack.orientation) {
                            case IMedia.VideoTrack.Orientation.LeftBottom:
                                unappliedRotationDegrees = 90;
                                break;
                            case IMedia.VideoTrack.Orientation.RightTop:
                                unappliedRotationDegrees = 270;
                                break;
                            default:
                                unappliedRotationDegrees = 0;
                                break;
                        }
                        final int[] videoSize = VideoUtils.correctedVideoSize(
                                vtrack.width, vtrack.height, unappliedRotationDegrees, 1.0f);

                        final float fps =
                                vtrack.frameRateNum > 0 && vtrack.frameRateDen > 0 ?
                                        (float) vtrack.frameRateNum / vtrack.frameRateDen : 0;

                        trackInfo = new VideoTrackInfo(
                                track.codec,
                                videoSize[0], videoSize[1],
                                fps,
                                track.bitrate);

                        System.out.println("VideoTrack's id = " + track.id);
                        break;

                    case IMedia.Track.Type.Audio:
                        IMedia.AudioTrack atrack = (IMedia.AudioTrack) track;
                        trackInfo = new AudioTrackInfo(
                                track.codec,
                                track.language,
                                atrack.channels,
                                atrack.rate,
                                track.bitrate);

                        System.out.println("AudioTrack's id = " + track.id);
                        break;

                    case IMedia.Track.Type.Text:
                        trackInfo = new SubtitleTrackInfo(track.language);

                        System.out.println("SubtitleTrack's id = " + track.id);
                        break;

                    default:
                        continue;
                }
                trackInfos.add(trackInfo);
            }
            //noinspection ToArrayCallWithZeroLengthArrayArgument
            return trackInfos.toArray(new TrackInfo[trackInfos.size()]);
        }
        return EMPTY_TRACK_INFOS;
    }

    // FIXME: mMediaPlayer.set*Track actually only accepts MediaPlayer.TrackDescription's id as
    //        its parameter, which may not be equal to the IMedia.Track's id ('libIndex').
    @Override
    public void selectTrack(int index) {
        if (mMediaPlayer != null) {
            IMedia media = mMediaPlayer.getMedia();
            if (media == null) return;

            int libIndex = externalIndexToLibIndex(index);
            if (libIndex < 0) return;

            IMedia.Track track = media.getTrack(libIndex);
            if (track == null) return;

            switch (track.type) {
                case IMedia.Track.Type.Video:
                    mMediaPlayer.setVideoTrack(libIndex);
                    break;
                case IMedia.Track.Type.Audio:
                    mMediaPlayer.setAudioTrack(libIndex);
                    break;
                case IMedia.Track.Type.Text:
                    mMediaPlayer.setSpuTrack(libIndex);
                    break;
            }
        }
    }

    @Override
    public void deselectTrack(int index) {
        if (mMediaPlayer != null) {
            switch (getTrackTypeAt(index)) {
                case IMedia.Track.Type.Video:
                    mMediaPlayer.setVideoTrack(-1);
                    break;
                case IMedia.Track.Type.Audio:
                    mMediaPlayer.setAudioTrack(-1);
                    break;
                case IMedia.Track.Type.Text:
                    mMediaPlayer.setSpuTrack(-1);
                    break;
            }
        }
    }

    private int getTrackTypeAt(int index) {
        if (mMediaPlayer != null) {
            IMedia media = mMediaPlayer.getMedia();
            if (media != null) {
                int libIndex = externalIndexToLibIndex(index);
                if (libIndex >= 0) {
                    IMedia.Track track = media.getTrack(libIndex);
                    if (track != null) {
                        return track.type;
                    }
                }
            }
        }
        return IMedia.Track.Type.Unknown;
    }

    private int externalIndexToLibIndex(int index) {
        if (index >= 0 && mMediaPlayer != null) {
            IMedia media = mMediaPlayer.getMedia();
            if (media != null) {
                int usableTrackCount = 0;
                for (int i = 0, trackCount = media.getTrackCount();
                     i < trackCount && usableTrackCount < index + 1; i++) {
                    IMedia.Track track = media.getTrack(i);
                    if (track != null && isSupportedTrackType(track.type)) {
                        ++usableTrackCount;
                    }
                }
                return usableTrackCount - 1;
            }
        }
        return -1;
    }

    private int libIndexToExternalIndex(int index) {
        if (index >= 0 && mMediaPlayer != null) {
            IMedia media = mMediaPlayer.getMedia();
            if (media != null) {
                int usableTrackCount = 0;
                for (int i = 0; i <= index; i++) {
                    IMedia.Track track = media.getTrack(i);
                    if (track != null && isSupportedTrackType(track.type)) {
                        ++usableTrackCount;
                    }
                }
                return usableTrackCount - 1;
            }
        }
        return -1;
    }

    private static boolean isSupportedTrackType(int trackType) {
        switch (trackType) {
            case IMedia.Track.Type.Video:
            case IMedia.Track.Type.Audio:
            case IMedia.Track.Type.Text:
                return true;
            default:
                return false;
        }
    }

    // FIXME: mMediaPlayer.get*Track returns the id of MediaPlayer.TrackDescription, which may not
    //        be equal to IMedia.Track's id (the index of the media track array in lib).
    @Override
    public int getSelectedTrackIndex(int trackType) {
        int trackIndex = -1;
        if (mMediaPlayer != null) {
            IMedia media = mMediaPlayer.getMedia();
            if (media != null) {
                switch (trackType) {
                    case TrackInfo.TRACK_TYPE_VIDEO:
                        trackIndex = mMediaPlayer.getVideoTrack();
                        break;
                    case TrackInfo.TRACK_TYPE_AUDIO:
                        trackIndex = mMediaPlayer.getAudioTrack();
                        break;
                    case TrackInfo.TRACK_TYPE_SUBTITLE:
                        trackIndex = mMediaPlayer.getSpuTrack();
                        break;
                }
                trackIndex = libIndexToExternalIndex(trackIndex);
            }
        }
        return trackIndex >= 0 ? trackIndex : INVALID_TRACK_INDEX;
    }

    @Override
    public void addSubtitleSource(@NonNull Uri uri, @NonNull String mimeType, @Nullable String language) {
        // Currently unsupported
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

    private static final class OnVideoLayoutChangeListener implements View.OnLayoutChangeListener {
        final WeakReference<VlcVideoPlayer> vlcPlayerRef;

        OnVideoLayoutChangeListener(VlcVideoPlayer vlcPlayer) {
            vlcPlayerRef = new WeakReference<>(vlcPlayer);
        }

        @Override
        public void onLayoutChange(
                View v,
                int left, int top, int right, int bottom,
                int oldLeft, int oldTop, int oldRight, int oldBottom) {
            VlcVideoPlayer vlcPlayer = vlcPlayerRef.get();
            if (vlcPlayer == null) {
                v.removeOnLayoutChangeListener(this);
                return;
            }

            if (vlcPlayer.mMediaPlayer == null) return;

            final int width = right - left;
            final int height = bottom - top;
            final int oldWidth = oldRight - oldLeft;
            final int oldHeight = oldBottom - oldTop;
            if (width != oldWidth || height != oldHeight) {
                vlcPlayer.mMediaPlayer.getVLCVout().setWindowSize(width, height);
            }
        }
    }
}
