/*
 * Created on 2022-2-18 5:54:39 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */
package com.liuzhenlin.videos.web.youtube

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.text.TextUtils
import android.view.View
import android.webkit.WebView
import android.widget.RemoteViews
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.util.Consumer
import com.liuzhenlin.common.Consts
import com.liuzhenlin.common.compat.AudioManagerCompat
import com.liuzhenlin.common.compat.RemoteViewsCompat
import com.liuzhenlin.common.notification.style.DecoratedMediaCustomViewStyle
import com.liuzhenlin.common.receiver.HeadsetEventsReceiver
import com.liuzhenlin.common.receiver.MediaButtonEventHandler
import com.liuzhenlin.common.receiver.MediaButtonEventReceiver
import com.liuzhenlin.common.utils.InternetResourceLoadTask
import com.liuzhenlin.common.utils.ReplacingCoroutineLauncher
import com.liuzhenlin.common.utils.ListenerSet
import com.liuzhenlin.common.utils.NotificationChannelManager
import com.liuzhenlin.common.utils.SerialExecutor
import com.liuzhenlin.common.utils.ServiceScope
import com.liuzhenlin.common.utils.ThemeUtils
import com.liuzhenlin.common.utils.Utils
import com.liuzhenlin.common.utils.executeOnCoroutine
import com.liuzhenlin.videos.web.AndroidWebView
import com.liuzhenlin.videos.web.R
import com.liuzhenlin.videos.web.bean.Playlist
import com.liuzhenlin.videos.web.bean.Video
import com.liuzhenlin.videos.web.player.Constants
import com.liuzhenlin.videos.web.player.PlayerWebView
import com.liuzhenlin.videos.web.player.Settings
import com.liuzhenlin.videos.web.player.WebPlayer
import com.liuzhenlin.videos.web.youtube.Youtube.PlayingStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import kotlin.concurrent.Volatile

class YoutubePlaybackService : Service(), PlayerListener {

    private var mForeground: Boolean = false

    // Decorator of CoroutineScope used to remove useless pending playback notifications
    // in the middle for better performance
    private var mCoroutineLauncher: ReplacingCoroutineLauncher? = null

    @JvmField
    protected var mView: PlayerWebView? = null

    public var webPlayer: WebPlayer? = null
        private set

    @JvmField
    protected var mPlayerReady: Boolean = false

    private var mSeekOnPlayerReady: Long = Constants.UNKNOWN.toLong()

    private var mNotiVideoId = ""
    private var mVideoId = ""
    private var mPlaylistId = ""

    private var mLinkType = 0

    private var mPlaylistSize = 0
    private var mPlaylistIndex = 0

    @Volatile private var mVideo: Video = EMPTY_VIDEO

    @PlayingStatus
    @JvmField
    @Volatile protected var mPlayingStatus: Int = PlayingStatus.UNSTARTED
    private var mLastPlayingStatus = mPlayingStatus

    private var mReplayVideo = false
    private var mReplayPlaylist = false

    @Volatile private var mPlayPauseBtnImgSrc = R.drawable.ic_pause_white_24dp
    @Volatile private var mPlayPauseBtnContentDesc = R.string.pause

    private var mAudioManager: AudioManager? = null

    private var mHeadsetEventsReceiver: HeadsetEventsReceiver? = null

    private val mMediaButtonEventHandler: MediaButtonEventHandler by lazy(LazyThreadSafetyMode.NONE) {
        MediaButtonEventHandler(Messenger(MsgHandler()))
    }

    private var mMediaButtonEventReceiverComponent: ComponentName? = null
        get() {
            if (field == null) {
                field = ComponentName(applicationContext, MediaButtonEventReceiver::class.java)
            }
            return field
        }

    private var mListeners: ListenerSet<PlayerListener>? = null

    public val isPlaying: Boolean
        get() {
            val status = mPlayingStatus
            return (status == PlayingStatus.PLAYING || status == PlayingStatus.BUFFERRING)
        }

    fun addPlayerListener(listener: PlayerListener?) {
        if (listener != null) {
            if (mListeners == null) {
                mListeners = ListenerSet()
            }
            mListeners?.add(listener)
        }
    }

    fun removePlayerListener(listener: PlayerListener?) {
        if (listener != null) {
            mListeners?.remove(listener)
        }
    }

    override fun onCreate() {
        super.onCreate()
        mAudioManager = applicationContext.getSystemService(AUDIO_SERVICE) as AudioManager
        mCoroutineLauncher = ReplacingCoroutineLauncher(ServiceScope())
        sInstance = this
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when (val action = intent.action) {
            Constants.Actions.START, Constants.Actions.START_FOREGROUND -> {
                val bundle = intent.extras
                if (bundle != null) {
                    val playlistId = bundle.getString(Constants.Extras.PLAYLIST_ID)
                    val videoId = bundle.getString(Constants.Extras.VIDEO_ID)
                    val videoIndex = bundle.getInt(Constants.Extras.VIDEO_INDEX)
                    val videoStartMs = bundle.getLong(Constants.Extras.VIDEO_START_MS)
                    val fromPlaybackView = bundle.getBoolean(Constants.Extras.FROM_PLAYBACK_VIEW)
                    if (action == Constants.Actions.START_FOREGROUND) {
                        mCoroutineLauncher?.launch(NotificationDispatcher) {
                            val notification =
                                    createNotification(videoId, SystemClock.elapsedRealtime(), false)
                            withContext(Dispatchers.Main) {
                                startForeground(ID_NOTIFICATION, notification)
                                mForeground = true
                            }
                        }
                    }
                    startPlayback(playlistId, videoId, videoIndex, videoStartMs, fromPlaybackView)
                }
            }
            Constants.Actions.STOP_SELF -> stop()
            Constants.Actions.PLAY_PAUSE -> onTapPlayPause()
            Constants.Actions.NEXT -> onTapSkipNext()
            Constants.Actions.PREV -> onTapSkipPrevious()
        }
        return START_NOT_STICKY
    }

    private fun onTapPlayPause() {
        webPlayer?.let {
            when (mPlayingStatus) {
                PlayingStatus.UNSTARTED, PlayingStatus.VIDEO_CUED, PlayingStatus.PAUSED -> it.play()
                PlayingStatus.ENDED -> {
                    if (mReplayVideo) {
                        it.play()
                    } else if (mReplayPlaylist) {
                        it.replayPlaylist()
                    }
                    mReplayVideo = false
                    mReplayPlaylist = false
                }
                PlayingStatus.BUFFERRING, PlayingStatus.PLAYING -> it.pause()
            }
        }
    }

    private fun onTapSkipNext() {
        webPlayer?.let {
            if (mLinkType == Constants.LinkType.SINGLES) {
                it.seekToDefault()
            } else {
                it.next()
            }
        }
    }

    private fun onTapSkipPrevious() {
        webPlayer?.let {
            if (mLinkType == Constants.LinkType.SINGLES) {
                it.seekToDefault()
            } else {
                it.prev()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        sInstance = null
        mCoroutineLauncher?.let {
            it.cancel()
            mCoroutineLauncher = null
        }
        mHeadsetEventsReceiver?.let {
            it.unregister()
            mHeadsetEventsReceiver = null
        }
        webPlayer?.let {
            it.stop()
            webPlayer = null
            mPlayerReady = false
        }
        mView?.destroy()
        YoutubePlaybackActivity.get()?.finish()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Stops when user removes the task holding YoutubePlaybackActivity from the Recents,
        // or closes that Activity in PiP through the 'Close' button, etc.
        if (rootIntent?.component?.shortClassName
                == YoutubePlaybackActivity::class.java.name.replace(packageName, "")) {
            stop()
        }
    }

    fun stop() {
        mCoroutineLauncher?.cancel()
        mCoroutineLauncher = null
        mForeground = false
        stopForeground(true)
        stopSelf()
    }

    private fun startPlayback(
        playlistId: String?, videoId: String?, videoIndex: Int, videoStartMs: Long,
        fromPlaybackView: Boolean
    ) {
        var playlistId = playlistId
        var videoId = videoId
        var videoIndex = videoIndex
        if (playlistId == null) {
            playlistId = ""
        }
        if (videoId == null) {
            videoId = ""
        }
        if (videoIndex == Constants.UNKNOWN && videoId.isEmpty()) {
            videoIndex = 0
        }

        val playerChanged: Boolean
        val videoIdChanged = videoId != mVideoId

        var view = mView
        if (view == null) {
            view = YoutubePlaybackView(this)
            mView = view
        }
        val oldPlayer = webPlayer
        val player = YoutubePlayerFactory.obtain(view)
        webPlayer = player
        playerChanged = player !== oldPlayer
        if (playerChanged) {
            view.webPlayer = player
            mPlayerReady = false
        }

        if (playerChanged || (playlistId != mPlaylistId || videoIdChanged)) {
            mSeekOnPlayerReady = Constants.UNKNOWN.toLong()
            mReplayVideo = false
            mReplayPlaylist = false
            if (Youtube.Prefs.get(applicationContext).retainHistoryVideoPages()) {
                // We need a new page if the history video pages in the backstack are required
                // by the user to be still held on to, so set mPlayerReady to false and use
                // the view to load the video/playlist down below.
                mPlayerReady = false
            }
            if (playlistId.isEmpty()) {
                mLinkType = Constants.LinkType.SINGLES
                mVideoId = videoId
                mPlaylistId = ""
                mPlaylistSize = 1
                mPlaylistIndex = 0
                if (mPlayerReady) {
                    player.loadVideo(videoId, videoStartMs)
                } else {
                    view.loadVideo(videoId, videoStartMs)
                }
            } else {
                mLinkType = Constants.LinkType.PLAYLIST
                mVideoId = videoId
                mPlaylistId = playlistId
                mPlaylistSize = Constants.UNKNOWN
                mPlaylistIndex = videoIndex
                if (mPlayerReady && player is YoutubePlayer) {
                    player.loadPlaylist(playlistId, videoId, videoIndex, videoStartMs)
                } else {
                    if (mPlayerReady && videoIndex != Constants.UNKNOWN) {
                        player.loadPlaylist(playlistId, videoId, videoIndex, videoStartMs)
                    } else {
                        mPlayerReady = false
                        view.loadPlaylist(playlistId, videoId, videoIndex, videoStartMs)
                    }
                }
            }
        } else {
            if (mPlayerReady) {
                videoSeekTo(videoStartMs)
            } else {
                mSeekOnPlayerReady = videoStartMs
            }
        }
        if (playerChanged || !fromPlaybackView) {
            val ytPlaybackActivity = YoutubePlaybackActivity.get()
            // Have the video view exit fullscreen first, to avoid it going fullscreen automatically
            // after it exits from PiP to the default display mode.
            if (ytPlaybackActivity != null && ytPlaybackActivity.isInPictureInPictureMode) {
                view.exitFullscreen()
            }
            if (playerChanged) {
                view.addPageListener(object : AndroidWebView.PageListener {
                    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                        // Clear history pages that used a different player
                        // XXX: We prefer to retain the history so that WebView can go back later
                        //      and resume them with the current player.
                        view.clearHistory()
                        (view as YoutubePlaybackView).removePageListener(this)
                    }
                })
                if (ytPlaybackActivity != null) {
                    ActivityCompat.recreate(ytPlaybackActivity)
                }
            }
            playInForeground()
        }
        if (videoIdChanged) {
            val video = Video()
            video.id = videoId
            refreshNotificationForVideo(video)
        }
    }

    fun playInForeground() {
        applicationContext.let {
            val fullscreenIntent = Intent(it, YoutubePlaybackActivity::class.java)
            fullscreenIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            it.startActivity(fullscreenIntent)
        }
    }

    private fun videoSeekTo(videoStartMs: Long) {
        webPlayer?.let {
            if (videoStartMs == Constants.TIME_UNSET) {
                it.seekToDefault()
            } else {
                it.seekTo(videoStartMs)
            }
        }
    }

    protected fun onGetPlaylistInfo(playlist: Playlist?) {
        if (playlist != null) {
            val videoIds = playlist.videoIds
            mPlaylistSize = videoIds?.size ?: 0
            mPlaylistIndex = playlist.videoIndex
        }
    }

    override fun onPlayerReady() {
        webPlayer?.let { player ->
            mPlayerReady = true
            if (mSeekOnPlayerReady != Constants.UNKNOWN.toLong()) {
                videoSeekTo(mSeekOnPlayerReady)
                mSeekOnPlayerReady = Constants.UNKNOWN.toLong()
            }
            if (mHeadsetEventsReceiver == null) {
                mHeadsetEventsReceiver = object : HeadsetEventsReceiver(applicationContext) {
                    override fun onHeadsetPluggedOutOrBluetoothDisconnected() {
                        player.pause()
                    }
                }
                mHeadsetEventsReceiver?.register(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            }
            mListeners?.forEach { listener -> listener.onPlayerReady() }
        }
    }

    @SuppressLint("SwitchIntDef")
    override fun onPlayerStateChange(@PlayingStatus playingStatus: Int) {
        webPlayer?.let { player ->
            when (playingStatus) {
                PlayingStatus.PLAYING -> {
                    mPlayPauseBtnImgSrc = R.drawable.ic_pause_white_24dp
                    mPlayPauseBtnContentDesc = R.string.pause
//                    if (mLinkType == Constants.LinkType.PLAYLIST) {
//                        player.requestGetPlaylistInfo();
//                    }

                    player.skipAd()
                    player.setMuted(false)

                    // Register MediaButtonEventReceiver every time the video starts, which
                    // will ensure it to be the sole receiver of MEDIA_BUTTON intents
                    MediaButtonEventReceiver.setMediaButtonEventHandler(mMediaButtonEventHandler)
                    mAudioManager?.let {
                        AudioManagerCompat.registerMediaButtonEventReceiver(
                                applicationContext, it, mMediaButtonEventReceiverComponent)
                    }
                }

                PlayingStatus.PAUSED -> {
                    mPlayPauseBtnImgSrc = R.drawable.ic_play_white_24dp
                    mPlayPauseBtnContentDesc = R.string.play
                }

                PlayingStatus.ENDED ->
                    if (mLinkType == Constants.LinkType.PLAYLIST) {
                        when (Settings.getRepeatMode()) {
                            Constants.RepeatMode.SINGLE -> player.prev()

                            // If not repeating then set notification icon to repeat
                            // when playlist ends
                            Constants.RepeatMode.NONE -> {
                                mReplayPlaylist = true
                                mPlayPauseBtnImgSrc = R.drawable.ic_replay_white_24dp
                                mPlayPauseBtnContentDesc = R.string.replay
                            }
                        }
                    } else {
                        if (Settings.getRepeatMode() != Constants.RepeatMode.NONE) {
                            player.play()
                        } else {
                            if (Settings.shouldFinishServiceOnPlaylistEnded()) {
                                stop()
                                return
                            } else {
                                mReplayVideo = true
                                mPlayPauseBtnImgSrc = R.drawable.ic_replay_white_24dp
                                mPlayPauseBtnContentDesc = R.string.replay
                            }
                        }
                    }
            }
            mLastPlayingStatus = mPlayingStatus
            mPlayingStatus = playingStatus
            player.requestGetVideoInfo(true)
            mListeners?.forEach { it.onPlayerStateChange(playingStatus) }
        }
    }

    protected fun onGetVideoInfo(video: Video?, refreshNotification: Boolean) {
        var video = video
        if (refreshNotification) {
            if (video == null) {
                video = EMPTY_VIDEO
            }
            refreshNotificationForVideo(video)
        }
    }

    private fun refreshNotificationForVideo(video: Video) {
        var changed = mLastPlayingStatus != mPlayingStatus
                || mVideo.duration != video.duration
                || mVideo.currentPosition != video.currentPosition
        recacheVideoInfo(video)
        if (mNotiVideoId != mVideoId) {
            mNotiVideoId = mVideoId
            changed = true
        }
        if (changed) {
            refreshNotification(true)
        }
    }

    private fun recacheVideoInfo(video: Video) {
        val lastVideoId = mVideo.id
        mLastPlayingStatus = mPlayingStatus
        mVideo = video
        // Video id may not currently available from the player, so we need to verify if we can
        // modify the mVideoId, as the played video can be changed directly through the web player,
        // like prev() and next() of the YoutubeIFramePlayer.
        if (!TextUtils.isEmpty(video.id) || Utils.emptyIfStringNull(lastVideoId) == mVideoId) {
            mVideoId = Utils.emptyIfStringNull(video.id)
        }
    }

    fun refreshNotification() {
        refreshNotification(true)
    }

    private fun refreshNotification(showVideoInfo: Boolean) {
        val elapsedTime = SystemClock.elapsedRealtime()
        mCoroutineLauncher?.launch(NotificationDispatcher) {
            val notification = createNotification(mVideoId, elapsedTime, showVideoInfo)
            withContext(Dispatchers.Main) {
                if (mForeground) {
                    NotificationManagerCompat.from(applicationContext)
                            .notify(ID_NOTIFICATION, notification)
                } else {
                    startForeground(ID_NOTIFICATION, notification)
                }
            }
        }
    }

    private suspend fun createNotification(videoId: String?, elapsedTime: Long, loadInfo: Boolean)
            : Notification {
        val context = applicationContext
        val builder = NotificationCompat.Builder(
                this, NotificationChannelManager.getPlaybackControlNotificationChannelId(context))

        val pkgName = context.packageName
        val viewBig = RemoteViews(pkgName, R.layout.web_player_notification_view_large)
        val viewSmall = RemoteViews(pkgName, R.layout.web_player_notification_view_small)

        var bitmapTask: Deferred<Bitmap?>? = null
        var jsonTask: Deferred<String?>? = null
        if (loadInfo && !TextUtils.isEmpty(videoId)) {
            withContext(Dispatchers.Main) {
                bitmapTask =
                    InternetResourceLoadTask.ofBitmap("https://i.ytimg.com/vi/$videoId/mqdefault.jpg")
                        .executeOnCoroutine(this, Dispatchers.IO)
                jsonTask =
                    InternetResourceLoadTask.ofString(
                        "https://www.youtube.com/oembed?url=http://www.youtu.be/watch?v="
                                + videoId + "&format=json")
                        .executeOnCoroutine(this, Dispatchers.IO)
            }
        }

        // Intent to do things
        val doThings = Intent(context, YoutubePlaybackService::class.java)
        val iconTint = notificationActionIconTint

        RemoteViewsCompat.setImageViewResourceWithTint(
                this, viewSmall, R.id.btn_close, R.drawable.ic_close_white_20dp, iconTint)
        RemoteViewsCompat.setImageViewResourceWithTint(
                this, viewBig, R.id.btn_close, R.drawable.ic_close_white_20dp, iconTint)
        // Stop service using doThings Intent
        viewSmall.setOnClickPendingIntent(
                R.id.btn_close,
                getNotificationActionPendingIntent(doThings.setAction(Constants.Actions.STOP_SELF)))
        viewBig.setOnClickPendingIntent(
                R.id.btn_close,
                getNotificationActionPendingIntent(doThings.setAction(Constants.Actions.STOP_SELF)))

        RemoteViewsCompat.setImageViewResourceWithTint(
                this, viewBig, R.id.btn_play_pause, mPlayPauseBtnImgSrc, iconTint)
        RemoteViewsCompat.setContentDescription(
                viewBig, R.id.btn_play_pause, getText(mPlayPauseBtnContentDesc))
        // Play, Pause video using doThings Intent
        viewBig.setOnClickPendingIntent(
                R.id.btn_play_pause,
                getNotificationActionPendingIntent(doThings.setAction(Constants.Actions.PLAY_PAUSE)))

        RemoteViewsCompat.setImageViewResourceWithTint(
                this, viewBig, R.id.btn_next, R.drawable.ic_skip_next_white_24dp, iconTint)
        // Next video using doThings Intent
        viewBig.setOnClickPendingIntent(
                R.id.btn_next,
                getNotificationActionPendingIntent(doThings.setAction(Constants.Actions.NEXT)))

        RemoteViewsCompat.setImageViewResourceWithTint(
                this, viewBig, R.id.btn_previous, R.drawable.ic_skip_previous_white_24dp, iconTint)
        // Previous video using doThings Intent
        viewBig.setOnClickPendingIntent(
                R.id.btn_previous,
                getNotificationActionPendingIntent(doThings.setAction(Constants.Actions.PREV)))

        val it = Intent(context, YoutubePlaybackActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pit = PendingIntent.getActivity(context, 0, it, Consts.PENDING_INTENT_FLAG_IMMUTABLE)
        builder.setSmallIcon(R.drawable.ic_media_app_notification)
                .setStyle(DecoratedMediaCustomViewStyle())
                .setDefaults(0)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setCustomContentView(viewSmall)
                .setCustomBigContentView(viewBig)
                .setContentIntent(pit)
                .setAutoCancel(false)

        if (bitmapTask != null || jsonTask != null) {
            val thumb = bitmapTask?.await()
            var title: String? = null
            var author: String? = null
            jsonTask?.await()?.let {
                try {
                    val detailsJson = JSONObject(it)
                    title = detailsJson.getString("title")
                    author = detailsJson.getString("author_name")
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }

            if (thumb != null) {
                viewBig.setImageViewBitmap(R.id.image_thumbnail, thumb)
                viewSmall.setImageViewBitmap(R.id.image_thumbnail, thumb)
            }
            if (title != null) {
                builder.setTicker(title)
                viewBig.setTextViewText(R.id.text_title, title)
                viewSmall.setTextViewText(R.id.text_title, title)
            }
            if (author != null) {
                viewBig.setTextViewText(R.id.text_author, author)
                viewSmall.setTextViewText(R.id.text_author, author)
            }
        }

        // Chronometer
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (loadInfo) {
                val video = mVideo
                val endTime = elapsedTime + (video.duration - video.currentPosition)
                if (endTime > SystemClock.elapsedRealtime()) {
                    viewBig.setLong(R.id.countdownChronometer, "setBase", endTime)
                    viewBig.setBoolean(R.id.countdownChronometer,
                            "setStarted", mPlayingStatus == PlayingStatus.PLAYING)
                }
            }
        } else {
            viewBig.setViewVisibility(R.id.countdownChronometer, View.GONE)
        }

        return builder.build()
    }

    private fun getNotificationActionPendingIntent(intent: Intent): PendingIntent {
        return PendingIntent.getService(applicationContext, 0, intent,
                Consts.PENDING_INTENT_FLAG_IMMUTABLE)
    }

    private inline val notificationActionIconTint: Int
        /**
         * Gets the notification action icon tint relying on the current theme.
         * Do NOT cache statically!
         */
        get() {
            // MUST use the application Context to retrieve the default text color of the below
            // TextAppearance used by the system UI, whose night mode the application Context will
            // always keep in sync with.
            return ThemeUtils.getTextAppearanceDefaultTextColor(
                    applicationContext, R.style.TextAppearance_Compat_Notification_Media)
        }

    private class MsgHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            val service = get() ?: return

            when (msg.what) {
                MediaButtonEventHandler.MSG_PLAY_PAUSE_KEY_SINGLE_TAP ->
                    service.onTapPlayPause()

                // Consider double tap as the next.
                MediaButtonEventHandler.MSG_PLAY_PAUSE_KEY_DOUBLE_TAP,
                MediaButtonEventHandler.MSG_MEDIA_NEXT ->
                    service.onTapSkipNext()

                // Consider triple tap as the previous.
                MediaButtonEventHandler.MSG_PLAY_PAUSE_KEY_TRIPLE_TAP,
                MediaButtonEventHandler.MSG_MEDIA_PREVIOUS ->
                    service.onTapSkipPrevious()
            }
        }
    }

    companion object {
        private const val ID_NOTIFICATION = 20220216

        @SuppressLint("StaticFieldLeak")
        private var sInstance: YoutubePlaybackService? = null

        @JvmField
        protected val EMPTY_VIDEO: Video = Video()

        private val NotificationDispatcher: CoroutineDispatcher =
                SerialExecutor().asCoroutineDispatcher()

        @JvmStatic
        fun peekIfNonnullThenDo(consumer: Consumer<YoutubePlaybackService>) {
            val service = sInstance
            if (service != null) {
                consumer.accept(service)
            }
        }

        @JvmStatic
        fun get(): YoutubePlaybackService? {
            return sInstance
        }

        @JvmStatic
        @JvmOverloads
        fun startPlaybackIfUrlIsWatchUrl(
            context: Context, url: String, fromPlaybackView: Boolean = false
        ): Boolean {
            val videoId = if (Youtube.REGEX_WATCH_URL.matches(url)) {
                Youtube.Util.getVideoIdFromWatchUrl(url)
            } else if (Youtube.REGEX_SHARE_URL.matches(url)) {
                Youtube.Util.getVideoIdFromShareUrl(url)
            } else {
                return false
            }
            val playlistId = Youtube.Util.getPlaylistIdFromWatchOrShareUrl(url)
            val videoIndex = Youtube.Util.getVideoIndexFromWatchOrShareUrl(url)
            val videoStartMs = Youtube.Util.getVideoStartMsFromWatchOrShareUrl(url)

            startPlayback(context, playlistId, videoId, videoIndex, videoStartMs, fromPlaybackView)
            return true
        }

        @JvmStatic
        fun startPlayback(
            context: Context,
            playlistId: String?, videoId: String?, videoStartMs: Long,
            fromPlaybackView: Boolean
        ) {
            startPlayback(
                    context, playlistId, videoId, Constants.UNKNOWN, videoStartMs, fromPlaybackView)
        }

        @JvmStatic
        fun startPlayback(
            context: Context,
            playlistId: String?, videoId: String?, videoIndex: Int, videoStartMs: Long,
            fromPlaybackView: Boolean
        ) {
            val intent = Intent(context, YoutubePlaybackService::class.java)
            intent.putExtra(Constants.Extras.PLAYLIST_ID, playlistId)
            intent.putExtra(Constants.Extras.VIDEO_ID, videoId)
            intent.putExtra(Constants.Extras.VIDEO_INDEX, videoIndex)
            intent.putExtra(Constants.Extras.VIDEO_START_MS, videoStartMs)
            intent.putExtra(Constants.Extras.FROM_PLAYBACK_VIEW, fromPlaybackView)
            intent.setAction(
                    if (Utils.isServiceRunning(context, YoutubePlaybackService::class.java))
                        Constants.Actions.START
                    else Constants.Actions.START_FOREGROUND)
            context.startService(intent)
        }
    }
}