/*
 * Created on 2022-2-18 5:54:39 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.util.Consumer;

import com.liuzhenlin.common.compat.RemoteViewsCompat;
import com.liuzhenlin.common.notification.style.DecoratedMediaCustomViewStyle;
import com.liuzhenlin.common.receiver.HeadsetEventsReceiver;
import com.liuzhenlin.common.receiver.MediaButtonEventHandler;
import com.liuzhenlin.common.receiver.MediaButtonEventReceiver;
import com.liuzhenlin.common.utils.Executors;
import com.liuzhenlin.common.utils.InternetResourceLoadTask;
import com.liuzhenlin.common.utils.NotificationChannelManager;
import com.liuzhenlin.common.utils.Synthetic;
import com.liuzhenlin.common.utils.ThemeUtils;
import com.liuzhenlin.common.utils.Utils;
import com.liuzhenlin.videos.web.R;
import com.liuzhenlin.videos.web.bean.Playlist;
import com.liuzhenlin.videos.web.bean.Video;
import com.liuzhenlin.videos.web.player.Constants;
import com.liuzhenlin.videos.web.player.PlayerWebView;
import com.liuzhenlin.videos.web.player.Settings;
import com.liuzhenlin.videos.web.player.WebPlayer;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

public class YoutubePlaybackService extends Service {

    private static final int ID_NOTIFICATION = 20220216;

    @SuppressLint("StaticFieldLeak")
    private static YoutubePlaybackService sInstance;
    private volatile boolean mRunning;

    private Context mContext;

    @Nullable /*package*/ PlayerWebView mView;

    @Nullable @Synthetic WebPlayer mPlayer;
    /*package*/ boolean mPlayerReady;

    private String mVideoId = "";
    private String mPlaylistId = "";

    private int mLinkType;

    private int mPlaylistSize;
    private int mPlaylistIndex;

    private volatile Video mVideo = EMPTY_VIDEO;
    private static final Video EMPTY_VIDEO = new Video();

    /*package*/ volatile int mPlayingStatus = Youtube.PlayingStatus.UNSTARTED;
    private int mLastPlayingStatus = mPlayingStatus;

    private boolean mReplayVideo;
    private boolean mReplayPlaylist;

    private volatile int mPlayPauseBtnImgSrc = R.drawable.ic_pause_white_24dp;
    private volatile int mPlayPauseBtnContentDesc = R.string.pause;

    private int mNotificationActionIconTint;

    private AudioManager mAudioManager;
    private HeadsetEventsReceiver mHeadsetEventsReceiver;
    private MediaButtonEventHandler mMediaButtonEventHandler;
    private ComponentName mMediaButtonEventReceiverComponent;

    public static void peekIfNonnullThenDo(@NonNull Consumer<YoutubePlaybackService> consumer) {
        YoutubePlaybackService service = sInstance;
        if (service != null) {
            consumer.accept(service);
        }
    }

    @Nullable
    public static YoutubePlaybackService get() {
        return sInstance;
    }

    @Nullable
    public WebPlayer getWebPlayer() {
        return mPlayer;
    }

    private MediaButtonEventHandler getMediaButtonEventHandler() {
        if (mMediaButtonEventHandler == null) {
            mMediaButtonEventHandler = new MediaButtonEventHandler(new Messenger(new MsgHandler()));
        }
        return mMediaButtonEventHandler;
    }

    private ComponentName getMediaButtonEventReceiverComponent() {
        if (mMediaButtonEventReceiverComponent == null) {
            mMediaButtonEventReceiverComponent =
                    new ComponentName(mContext, MediaButtonEventReceiver.class);
        }
        return mMediaButtonEventReceiverComponent;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mNotificationActionIconTint = getNotificationActionIconTint();
        sInstance = this;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mRunning = true;
        String action = intent.getAction();
        switch (action) {
            case Constants.Actions.START:
            case Constants.Actions.START_FOREGROUND:
                Bundle bundle = intent.getExtras();
                if (bundle != null) {
                    String playlistId = bundle.getString(Constants.Extras.PLAYLIST_ID);
                    String videoId = bundle.getString(Constants.Extras.VIDEO_ID);
                    int videoIndex = bundle.getInt(Constants.Extras.VIDEO_INDEX);
                    if (action.equals(Constants.Actions.START_FOREGROUND)) {
                        Executors.THREAD_POOL_EXECUTOR.execute(() -> {
                            Notification notification = createNotification(videoId, false);
                            Executors.MAIN_EXECUTOR.execute(() -> {
                                if (mRunning) {
                                    startForeground(ID_NOTIFICATION, notification);
                                }
                            });
                        });
                    }
                    startPlayback(playlistId, videoId, videoIndex);
                }
                break;
            case Constants.Actions.STOP_SELF:
                stop();
                break;
            case Constants.Actions.PLAY_PAUSE:
                onTapPlayPause();
                break;
            case Constants.Actions.NEXT:
                onTapSkipNext();
                break;
            case Constants.Actions.PREV:
                onTapSkipPrevious();
                break;
        }
        return START_NOT_STICKY;
    }

    @Synthetic void onTapPlayPause() {
        if (mPlayer == null) {
            return;
        }
        switch (mPlayingStatus) {
            case Youtube.PlayingStatus.UNSTARTED:
            case Youtube.PlayingStatus.VIDEO_CUED:
            case Youtube.PlayingStatus.PAUSED:
                mPlayer.play();
                break;
            case Youtube.PlayingStatus.ENDED:
                if (mReplayVideo) {
                    mPlayer.play();
                } else if (mReplayPlaylist) {
                    mPlayer.replayPlaylist();
                }
                mReplayPlaylist = mReplayVideo = false;
                break;
            case Youtube.PlayingStatus.BUFFERRING:
            case Youtube.PlayingStatus.PLAYING:
                mPlayer.pause();
                break;
        }
    }

    @Synthetic void onTapSkipNext() {
        if (mPlayer == null) {
            return;
        }
        if (mLinkType == Constants.LinkType.SINGLES) {
            mPlayer.seekToDefault();
        } else {
            mPlayer.next();
        }
    }

    @Synthetic void onTapSkipPrevious() {
        if (mPlayer == null) {
            return;
        }
        if (mLinkType == Constants.LinkType.SINGLES) {
            mPlayer.seekToDefault();
        } else {
            mPlayer.prev();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRunning = false;
        sInstance = null;
        if (mHeadsetEventsReceiver != null) {
            mHeadsetEventsReceiver.unregister();
            mHeadsetEventsReceiver = null;
        }
        if (mPlayer != null) {
            mPlayer.stop();
            mPlayer = null;
            mPlayerReady = false;
        }
        if (mView != null) {
            mView.destroy();
        }
        if (YoutubePlaybackActivity.get() != null) {
            YoutubePlaybackActivity.get().finish();
        }
    }

    public void stop() {
        mRunning = false;
        stopForeground(true);
        stopSelf();
    }

    public static boolean startPlaybackIfUrlIsWatchUrl(@NonNull Context context, @NonNull String url) {
        String videoId;
        String playlistId;
        int videoIndex;

        if (url.matches(Youtube.REGEX_WATCH_URL)) {
            videoId = Youtube.Util.getVideoIdFromWatchUrl(url);
        } else if (url.matches(Youtube.REGEX_SHARE_URL)) {
            videoId = Youtube.Util.getVideoIdFromShareUrl(url);
        } else {
            return false;
        }
        playlistId = Youtube.Util.getPlaylistIdFromWatchOrShareUrl(url);
        videoIndex = Youtube.Util.getVideoIndexFromWatchOrShareUrl(url);

        startPlayback(context, playlistId, videoId, videoIndex);
        return true;
    }

    public static void startPlayback(
            @NonNull Context context, @Nullable String playlistId, @Nullable String videoId) {
        startPlayback(context, playlistId, videoId, Constants.UNKNOWN);
    }

    public static void startPlayback(
            @NonNull Context context,
            @Nullable String playlistId, @Nullable String videoId, int videoIndex) {
        Intent intent = new Intent(context, YoutubePlaybackService.class);
        intent.putExtra(Constants.Extras.PLAYLIST_ID, playlistId);
        intent.putExtra(Constants.Extras.VIDEO_ID, videoId);
        intent.putExtra(Constants.Extras.VIDEO_INDEX, videoIndex);
        intent.setAction(
                Utils.isServiceRunning(context, YoutubePlaybackService.class)
                        ? Constants.Actions.START
                        : Constants.Actions.START_FOREGROUND);
        context.startService(intent);
    }

    private void startPlayback(@Nullable String playlistId, @Nullable String videoId, int videoIndex) {
        if (playlistId == null) {
            playlistId = "";
        }
        if (videoId == null) {
            videoId = "";
        }
        if (videoIndex == Constants.UNKNOWN && videoId.isEmpty()) {
            videoIndex = 0;
        }

        boolean playerChanged;

        if (mView == null) {
            mView = new YoutubePlaybackView(this);
        }
        WebPlayer oldPlayer = mPlayer;
        mPlayer = YoutubePlayerFactory.obtain(mView);
        playerChanged = mPlayer != oldPlayer;
        if (playerChanged) {
            mView.setWebPlayer(mPlayer);
            mPlayerReady = false;
        }

        if (playerChanged || (!playlistId.equals(mPlaylistId) || !videoId.equals(mVideoId))) {
            if (playlistId.isEmpty()) {
                mLinkType = Constants.LinkType.SINGLES;
                mVideoId = videoId;
                mPlaylistId = "";
                mPlaylistSize = 1;
                mPlaylistIndex = 0;
                if (mPlayerReady) {
                    mPlayer.loadVideo(videoId);
                } else {
                    mView.loadVideo(videoId);
                }
            } else {
                mLinkType = Constants.LinkType.PLAYLIST;
                mVideoId = videoId;
                mPlaylistId = playlistId;
                mPlaylistSize = Constants.UNKNOWN;
                mPlaylistIndex = videoIndex;
                if (mPlayerReady && mPlayer instanceof YoutubePlayer) {
                    mPlayer.loadPlaylist(playlistId, videoId, videoIndex);
                } else {
                    if (mPlayerReady && videoIndex != Constants.UNKNOWN) {
                        mPlayer.loadPlaylist(playlistId, videoId, videoIndex);
                    } else {
                        mPlayerReady = false;
                        mView.loadPlaylist(playlistId, videoId, videoIndex);
                    }
                }
            }
            mReplayPlaylist = mReplayVideo = false;
        }
        if (playerChanged) {
            if (YoutubePlaybackActivity.get() != null) {
                YoutubePlaybackActivity.get().finish();
            }
        }
        playInFullscreen();
    }

    public void playInFullscreen() {
        Intent fullscreenIntent = new Intent(mContext, YoutubePlaybackActivity.class);
        fullscreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(fullscreenIntent);
    }

    /*package*/ void onGetPlaylistInfo(Playlist playlist) {
        if (playlist != null) {
            String[] videoIds = playlist.getVideoIds();
            mPlaylistSize = videoIds != null ? videoIds.length : 0;
            mPlaylistIndex = playlist.getVideoIndex();
        }
    }

    /*package*/ void onPlayerReady() {
        if (mPlayer == null) {
            return;
        }
        mPlayerReady = true;
        if (mHeadsetEventsReceiver == null) {
            mHeadsetEventsReceiver = new HeadsetEventsReceiver(mContext) {
                @Override
                public void onHeadsetPluggedOutOrBluetoothDisconnected() {
                    if (mPlayer != null) {
                        mPlayer.pause();
                    }
                }
            };
            mHeadsetEventsReceiver.register(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        }
    }

    /*package*/ void onPlayingStatusChange(int playingStatus) {
        if (mPlayer == null) {
            return;
        }
        switch (playingStatus) {
            case Youtube.PlayingStatus.PLAYING:
                mPlayPauseBtnImgSrc = R.drawable.ic_pause_white_24dp;
                mPlayPauseBtnContentDesc = R.string.pause;
//                if (mLinkType == Constants.LinkType.PLAYLIST) {
//                    mPlayer.requestGetPlaylistInfo();
//                }

                mPlayer.skipAd();
                mPlayer.setMuted(false);

                // Register MediaButtonEventReceiver every time the video starts, which
                // will ensure it to be the sole receiver of MEDIA_BUTTON intents
                MediaButtonEventReceiver.setMediaButtonEventHandler(getMediaButtonEventHandler());
                mAudioManager.registerMediaButtonEventReceiver(getMediaButtonEventReceiverComponent());
                break;
            case Youtube.PlayingStatus.PAUSED:
                mPlayPauseBtnImgSrc = R.drawable.ic_play_white_24dp;
                mPlayPauseBtnContentDesc = R.string.play;
                break;
            case Youtube.PlayingStatus.ENDED:
                if (mLinkType == Constants.LinkType.PLAYLIST) {
                    switch (Settings.getRepeatMode()) {
                        case Constants.RepeatMode.SINGLE:
                            mPlayer.prev();
                            break;
                        // If not repeating then set notification icon to repeat when playlist ends
                        case Constants.RepeatMode.NONE:
                            mReplayPlaylist = true;
                            mPlayPauseBtnImgSrc = R.drawable.ic_replay_white_24dp;
                            mPlayPauseBtnContentDesc = R.string.replay;
                            break;
                    }
                } else {
                    if (Settings.getRepeatMode() != Constants.RepeatMode.NONE) {
                        mPlayer.play();
                    } else {
                        if (Settings.shouldFinishServiceOnPlaylistEnded()) {
                            stop();
                            return;
                        } else {
                            mReplayVideo = true;
                            mPlayPauseBtnImgSrc = R.drawable.ic_replay_white_24dp;
                            mPlayPauseBtnContentDesc = R.string.replay;
                        }
                    }
                }
                break;
        }
        mLastPlayingStatus = mPlayingStatus;
        mPlayingStatus = playingStatus;
        mPlayer.requestGetVideoInfo();
    }

    /*package*/ void onGetVideoInfo(Video video) {
        if (video == null) {
            video = EMPTY_VIDEO;
        }
        boolean changed = mLastPlayingStatus != mPlayingStatus
                || mVideo.getDuration() != video.getDuration()
                || mVideo.getBufferedPosition() != video.getBufferedPosition()
                || mVideo.getCurrentPosition() != video.getCurrentPosition();
        mLastPlayingStatus = mPlayingStatus;
        mVideo = video;
        String videoId = Utils.emptyIfStringNull(video.getId());
        if (!mVideoId.equals(videoId)) {
            mVideoId = videoId;
            changed = true;
            refreshNotification(false);
        }
        if (changed) {
            refreshNotification(true);
        }
    }

    private void refreshNotification(boolean showVideoInfo) {
        Executors.THREAD_POOL_EXECUTOR.execute(() -> {
            NotificationManager notificationManager =
                    (NotificationManager) mContext.getSystemService(NOTIFICATION_SERVICE);
            Notification notification = createNotification(mVideoId, showVideoInfo);
            Executors.MAIN_EXECUTOR.execute(() -> {
                if (mRunning) {
                    notificationManager.notify(ID_NOTIFICATION, notification);
                }
            });
        });
    }

    private Notification createNotification(String videoId, boolean loadInfo) {
        if (!mRunning) return null;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                this, NotificationChannelManager.getPlaybackControlNotificationChannelId(mContext));

        String pkgName = mContext.getPackageName();
        RemoteViews viewBig = new RemoteViews(pkgName, R.layout.web_player_notification_view_large);
        RemoteViews viewSmall = new RemoteViews(pkgName, R.layout.web_player_notification_view_small);

        CountDownLatch[] latch = new CountDownLatch[1];
        if (loadInfo) {
            latch[0] = new CountDownLatch(2);
            Executors.MAIN_EXECUTOR.execute(() -> {
                Executor workerExecutor = Executors.THREAD_POOL_EXECUTOR;
                InternetResourceLoadTask.ofBitmap("https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg")
                        .onResult(new InternetResourceLoadTask.ResultCallback<Bitmap>() {
                            @Override
                            public void onCompleted(Bitmap thumb) {
                                if (thumb != null) {
                                    viewBig.setImageViewBitmap(R.id.image_thumbnail, thumb);
                                    viewSmall.setImageViewBitmap(R.id.image_thumbnail, thumb);
                                }
                                latch[0].countDown();
                            }
                        }).executeOnExecutor(workerExecutor);
                InternetResourceLoadTask.ofString(
                        "https://www.youtube.com/oembed?url=http://www.youtu.be/watch?v="
                                + videoId + "&format=json")
                        .onResult(new InternetResourceLoadTask.ResultCallback<String>() {
                            @Override
                            public void onCompleted(String details) {
                                if (details != null) {
                                    try {
                                        JSONObject detailsJson = new JSONObject(details);
                                        String title = detailsJson.getString("title");
                                        String author = detailsJson.getString("author_name");
                                        builder.setTicker(title);
                                        viewBig.setTextViewText(R.id.text_title, title);
                                        viewBig.setTextViewText(R.id.text_author, author);
                                        viewSmall.setTextViewText(R.id.text_title, title);
                                        viewSmall.setTextViewText(R.id.text_author, author);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                }
                                latch[0].countDown();
                            }
                        }).executeOnExecutor(workerExecutor);
            });
        }

        // Intent to do things
        Intent doThings = new Intent(mContext, YoutubePlaybackService.class);
        int iconTint = mNotificationActionIconTint;

        RemoteViewsCompat.setImageViewResourceWithTint(this,
                viewSmall, R.id.btn_close, R.drawable.ic_close_white_20dp, iconTint);
        RemoteViewsCompat.setImageViewResourceWithTint(this,
                viewBig, R.id.btn_close, R.drawable.ic_close_white_20dp, iconTint);
        // Stop service using doThings Intent
        viewSmall.setOnClickPendingIntent(
                R.id.btn_close,
                PendingIntent.getService(mContext, 0,
                        doThings.setAction(Constants.Actions.STOP_SELF), 0));
        viewBig.setOnClickPendingIntent(
                R.id.btn_close,
                PendingIntent.getService(mContext, 0,
                        doThings.setAction(Constants.Actions.STOP_SELF), 0));

        RemoteViewsCompat.setImageViewResourceWithTint(this,
                viewBig, R.id.btn_play_pause, mPlayPauseBtnImgSrc, iconTint);
        RemoteViewsCompat.setContentDescription(
                viewBig, R.id.btn_play_pause, getText(mPlayPauseBtnContentDesc));
        // Play, Pause video using doThings Intent
        viewBig.setOnClickPendingIntent(
                R.id.btn_play_pause,
                PendingIntent.getService(mContext, 0,
                        doThings.setAction(Constants.Actions.PLAY_PAUSE), 0));

        RemoteViewsCompat.setImageViewResourceWithTint(this,
                viewBig, R.id.btn_next, R.drawable.ic_skip_next_white_24dp, iconTint);
        // Next video using doThings Intent
        viewBig.setOnClickPendingIntent(
                R.id.btn_next,
                PendingIntent.getService(mContext, 0,
                        doThings.setAction(Constants.Actions.NEXT), 0));

        RemoteViewsCompat.setImageViewResourceWithTint(this,
                viewBig, R.id.btn_previous, R.drawable.ic_skip_previous_white_24dp, iconTint);
        // Previous video using doThings Intent
        viewBig.setOnClickPendingIntent(
                R.id.btn_previous,
                PendingIntent.getService(mContext, 0,
                        doThings.setAction(Constants.Actions.PREV), 0));

        // Chronometer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            long remaining = loadInfo ? mVideo.getDuration() - mVideo.getCurrentPosition() : 0;
            if (remaining > 0) {
                viewBig.setLong(R.id.countdownChronometer,
                        "setBase", SystemClock.elapsedRealtime() + remaining);
                viewBig.setBoolean(R.id.countdownChronometer,
                        "setStarted", mPlayingStatus == Youtube.PlayingStatus.PLAYING);
            }
        } else {
            viewBig.setViewVisibility(R.id.countdownChronometer, View.GONE);
        }

        Intent it = new Intent(mContext, YoutubePlaybackActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pit = PendingIntent.getActivity(mContext, 0, it, 0);
        Notification notification =
                builder.setSmallIcon(R.drawable.ic_media_app_notification)
                        .setStyle(new DecoratedMediaCustomViewStyle())
                        .setDefaults(0)
                        .setOnlyAlertOnce(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setCustomContentView(viewSmall)
                        .setCustomBigContentView(viewBig)
                        .setContentIntent(pit)
                        .setAutoCancel(false)
                        .build();

        if (latch[0] != null) {
            while (latch[0].getCount() > 0) {
                try {
                    latch[0].await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        return notification;
    }

    /**
     * Gets the notification action icon tint relying on the current theme. Do NOT cache statically!
     */
    private int getNotificationActionIconTint() {
        return ThemeUtils.getTextAppearanceDefaultTextColor(
                this, R.style.TextAppearance_Compat_Notification_Media);
    }

    private static final class MsgHandler extends Handler {
        MsgHandler() {
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            YoutubePlaybackService service = YoutubePlaybackService.get();
            if (service == null) return;

            switch (msg.what) {
                case MediaButtonEventHandler.MSG_PLAY_PAUSE_KEY_SINGLE_TAP:
                    service.onTapPlayPause();
                    break;
                // Consider double tap as the next.
                case MediaButtonEventHandler.MSG_PLAY_PAUSE_KEY_DOUBLE_TAP:
                case MediaButtonEventHandler.MSG_MEDIA_NEXT:
                    service.onTapSkipNext();
                    break;
                // Consider triple tap as the previous.
                case MediaButtonEventHandler.MSG_PLAY_PAUSE_KEY_TRIPLE_TAP:
                case MediaButtonEventHandler.MSG_MEDIA_PREVIOUS:
                    service.onTapSkipPrevious();
                    break;
            }
        }
    }
}