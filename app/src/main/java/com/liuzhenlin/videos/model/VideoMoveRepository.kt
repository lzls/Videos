/*
 * Created on 2024-12-23 6:17 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model

import android.content.Context
import com.liuzhenlin.common.utils.Executors
import com.liuzhenlin.common.utils.FileUtils
import com.liuzhenlin.common.utils.Regex
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.bean.VideoListItem
import com.liuzhenlin.videos.dao.AppPrefs
import com.liuzhenlin.videos.dao.VideoListItemDao
import com.liuzhenlin.videos.insertVideoDir
import com.liuzhenlin.videos.suffix
import com.liuzhenlin.videos.title
import com.liuzhenlin.videos.videoCount
import java.io.File

/**
 * @author 刘振林
 */

interface VideoMoveRepository : Repository<VideoMoveRepository.Callback> {

    public val targetDirs: Array<VideoDirectory>

    public val videos: Array<VideoListItem>

    public val videoQuantity: Int

    fun isTargetDirChecked(index: Int): Boolean
    fun setTargetDirChecked(index: Int, checked: Boolean)

    fun needShowVideoMovePromptDialog(): Boolean
    fun setVideoMovePromptDialogNeedBeShown(needed: Boolean)

    fun moveVideosToCheckedDir()

    interface Callback : Repository.Callback {
        fun onTargetDirCheckedChanged(index: Int, checked: Boolean)
        fun onCheckedTargetDirCountChanged(index: Int)

        fun onVideoMoveStart()
        fun onVideoMoveFinish(moved: Boolean)
    }

    companion object {
        @JvmStatic
        fun create(context: Context, videos: Array<VideoListItem>, targetDirs: Array<VideoDirectory>)
                : VideoMoveRepository {
            return VideoMoveRepositoryImpl(context, videos, targetDirs)
        }
    }
}

private class VideoMoveRepositoryImpl(
        context: Context,
        override val videos: Array<VideoListItem>, override val targetDirs: Array<VideoDirectory>)
    : BaseRepository<VideoMoveRepository.Callback>(context), VideoMoveRepository {

    override val videoQuantity: Int
        get() {
            return if (videos.size == 1
                    && (videos[0] is Video || (videos[0] as VideoDirectory).videoCount() == 1)) {
                1
            } else {
                1.inv()
            }
        }

    override fun isTargetDirChecked(index: Int) = targetDirs[index].isChecked

    override fun setTargetDirChecked(index: Int, checked: Boolean) {
        if (targetDirs[index].isChecked != checked) {
            var oldCheckedCount = 0
            var checkedCount = 0
            for (i in targetDirs.indices) {
                if (i == index) {
                    if (checked) {
                        ++checkedCount
                    } else {
                        ++oldCheckedCount
                    }
                    targetDirs[i].isChecked = checked
                    mCallback?.onTargetDirCheckedChanged(i, checked)
                } else {
                    if (targetDirs[i].isChecked) {
                        ++oldCheckedCount
                        if (checked) {
                            targetDirs[i].isChecked = false
                            mCallback?.onTargetDirCheckedChanged(i, false)
                        }
                    }
                }
            }
            if (checkedCount != oldCheckedCount) {
                mCallback?.onCheckedTargetDirCountChanged(checkedCount)
            }
        }
    }

    override fun needShowVideoMovePromptDialog(): Boolean {
        return !AppPrefs.getSingleton(mContext).hasUserDeclinedVideoMovePromptDialogToBeShownAgain()
    }

    override fun setVideoMovePromptDialogNeedBeShown(needed: Boolean) {
        AppPrefs.getSingleton(mContext).edit()
            .setUserDeclinedVideoMovePromptDialogToBeShownAgain(!needed)
            .apply()
    }

    override fun moveVideosToCheckedDir() {
        mCallback?.onVideoMoveStart()
        Executors.THREAD_POOL_EXECUTOR.execute {
            val moved = moveVideosToCheckedDirSync()
            Executors.MAIN_EXECUTOR.execute {
                mCallback?.onVideoMoveFinish(moved)
            }
        }
    }

    private fun moveVideosToCheckedDirSync(): Boolean {
        for (videodir in targetDirs) {
            if (videodir.isChecked) {
                var moved = false
                val dao = VideoListItemDao.getSingleton(mContext)
                for (video in videos) {
                    moved = if (video is VideoDirectory) {
                        moved or moveVideoDir(video, videodir, dao)
                    } else {
                        val v = video as Video
                        moved or moveVideo(v, videodir, dao)
                    }
                }
                return moved
            }
        }
        return false
    }

    private fun moveVideoDir(
            videodir: VideoDirectory, targetVideoDir: VideoDirectory, dao: VideoListItemDao)
    : Boolean {
        val dirName = FileUtils.getFileNameFromFilePath(videodir.path)
        val targetDirPath = targetVideoDir.path + File.separator + dirName
        val targetDirFile = File(targetDirPath)
        if (!targetDirFile.exists()) {
            targetDirFile.mkdirs()
        }
        var targetDir = targetDirs.find { it.path.equals(targetDirPath, ignoreCase = true) }
        if (targetDir == null) {
            targetDir = dao.queryVideoDirByPath(targetDirPath)
            if (targetDir == null) {
                targetDir = dao.insertVideoDir(targetDirPath)
            }
        }
        var moved = false
        for (item in videodir.videoListItems) {
            moved = if (item is VideoDirectory) {
                moved or moveVideoDir(item, targetDir, dao)
            } else {
                val v = item as Video
                moved or moveVideo(v, targetDir, dao)
            }
        }
        return moved
    }

    private fun moveVideo(video: Video, videodir: VideoDirectory, dao: VideoListItemDao): Boolean {
        val videoPath = video.path
        for (v in videodir.videoListItems) {
            if (v is Video && v.name.equals(video.name, ignoreCase = true)) {
                if (v.path.equals(videoPath, ignoreCase = true))
                    return false

                var i = 1
                var videoName: String
                val vTitleSuffixRegex = Regex("(-\\d+)$")
                var vTitle = v.title
                if (vTitleSuffixRegex.find(vTitle)) {
                    i = vTitleSuffixRegex.group()!!.substring(1).toInt() + 1
                    vTitle = vTitle.substring(0, vTitleSuffixRegex.start())
                }
                outer@
                while (true) {
                    videoName = vTitle + '-' + i + v.suffix
                    for (v2 in videodir.videoListItems) {
                        if (v2 is Video) {
                            if (v2.name.equals(videoName, ignoreCase = true)) {
                                i++
                                continue@outer
                            }
                        }
                    }
                    break
                }
                video.name = videoName
                video.path = video.path.replaceAfterLast(File.separatorChar, video.name)
                break
            }
        }
        videodir.videoListItems.add(video)
        video.isTopped = false
        video.path = video.path.replaceBeforeLast(File.separatorChar, videodir.path)
        return File(videoPath).renameTo(File(video.path)) && dao.updateVideo(video)
    }
}