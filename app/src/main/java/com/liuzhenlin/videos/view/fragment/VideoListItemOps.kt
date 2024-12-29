/*
 * Created on 2018/09/07.
 * Copyright © 2018–2019 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.view.fragment

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.IntDef
import androidx.fragment.app.Fragment
import com.bumptech.glide.util.Preconditions
import com.liuzhenlin.common.utils.FileUtils
import com.liuzhenlin.common.utils.ShareUtils
import com.liuzhenlin.common.utils.URLUtils
import com.liuzhenlin.videos.*
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.bean.VideoListItem
import com.liuzhenlin.videos.dao.VideoListItemDao
import com.liuzhenlin.videos.view.activity.VideoActivity
import java.io.File

/**
 * @author 刘振林
 */

const val FAIL_REASON_FILE_NOT_EXIST = 1     // 不存在该文件
const val FAIL_REASON_FILE_NAME_CLASHED = 2  // 该路径下存在相同名称的文件
const val FAIL_REASON_UNKNOWN = 3            // 未知原因

@IntDef(FAIL_REASON_FILE_NOT_EXIST, FAIL_REASON_FILE_NAME_CLASHED, FAIL_REASON_UNKNOWN)
@Retention(AnnotationRetention.SOURCE)
annotation class RenameFailReason

interface VideoListItemDeleteOnDiskListener<in T : VideoListItem> {
    fun onItemsDeleteStart(vararg items: T)
    fun onItemsDeleteFinish(vararg items: T)
}

interface VideoListItemRenameResultCallback<in T : VideoListItem> {
    fun onItemRenameFail(item: T, @RenameFailReason reason: Int)
    fun onItemRenameSuccess(item: T)
}

interface VideoListItemOpCallback<in T : VideoListItem> : VideoListItemDeleteOnDiskListener<T>,
        VideoListItemRenameResultCallback<T> {

    fun showDeleteItemDialog(item: T, onDeleteAction: (() -> Unit)? = null)
    fun showDeleteItemsPopupWindow(vararg items: T, onDeleteAction: (() -> Unit)? = null)
    fun showRenameItemDialog(item: T, onRenameAction: ((String) -> Unit)? = null)
    fun showItemDetailsDialog(item: T)
    fun showVideosMovePage(vararg items: T)
}

fun Array<out VideoListItem>.deleteOnDisk() {
    if (isEmpty()) return

    val dao = VideoListItemDao.getSingleton(App.getInstanceUnsafe()!!)
    deleteItemsRecursively(listOf(*this), dao)
}

private fun deleteItemsRecursively(items: List<VideoListItem>, dao: VideoListItemDao) {
    for (item in items)
        when (item) {
            is Video -> {
                File(item.path).delete()
                dao.deleteVideo(item.id)
            }
            is VideoDirectory -> {
                deleteItemsRecursively(item.videoListItems, dao)
                dao.deleteVideoDir(item.path)
            }
        }
}

fun <T : VideoListItem> T.renameTo(newName: String, callback: VideoListItemRenameResultCallback<T>?)
        : Boolean {
    if (newName == name) return false

    val context: Context = App.getInstanceUnsafe()!!

    when (this) {
        is Video -> {
            val file = File(path)
            if (!file.exists()) {
                callback?.onItemRenameFail(this, FAIL_REASON_FILE_NOT_EXIST)
                return false
            }

            val newFile = File(file.parent, newName)
            if (!newName.equals(name, ignoreCase = true) && newFile.exists()) {
                callback?.onItemRenameFail(this, FAIL_REASON_FILE_NAME_CLASHED)
                return false
            }

            if (!file.renameTo(newFile)) {
                callback?.onItemRenameFail(this, FAIL_REASON_UNKNOWN)
                return false
            }

            name = newName
            path = newFile.absolutePath
            return if (VideoListItemDao.getSingleton(context).updateVideo(this)) {
                callback?.onItemRenameSuccess(this)
                true
            } else {
                callback?.onItemRenameFail(this, FAIL_REASON_UNKNOWN)
                false
            }
        }
        is VideoDirectory -> {
            name = newName
            return if (VideoListItemDao.getSingleton(context).updateVideoDir(this)) {
                callback?.onItemRenameSuccess(this)
                true
            } else {
                callback?.onItemRenameFail(this, FAIL_REASON_UNKNOWN)
                false
            }
        }
    }
    return false
}

fun Any?.shareVideo(video: Video) {
    when (this) {
        is Fragment -> shareVideo(video)
        is Context -> shareVideo(video)
    }
}

fun Fragment.shareVideo(video: Video) {
    (activity ?: requireContext()).shareVideo(video)
}

fun Context?.shareVideo(video: Video) {
    val app = App.getInstanceUnsafe()!!
    val context = this ?: app
    if (URLUtils.isNetworkUrl(video.path)) {
        ShareUtils.shareText(
                context,
                FileUtils.getFileTitleFromFileName(video.name) + "：" + video.path,
                "text/plain")
    } else {
        ShareUtils.shareFile(context, Files.PROVIDER_AUTHORITY, File(video.path), "video/*")
    }
}

@JvmOverloads
fun Context.playVideo(uriString: String, videoTitle: String? = null) {
    startActivity(
            Intent(this, VideoActivity::class.java)
                    .setData(Uri.parse(uriString))
                    .putExtra(KEY_VIDEO_TITLE, videoTitle))
}

@JvmOverloads
fun Context.playVideo(uri: Uri, videoTitle: String? = null) {
    startActivity(
            Intent(this, VideoActivity::class.java)
                    .setData(uri)
                    .putExtra(KEY_VIDEO_TITLE, videoTitle))
}

@JvmOverloads
fun Context.playVideos(uriStrings: Array<String>, videoTitles: Array<String?>? = null, selection: Int) {
    Preconditions.checkArgument(uriStrings.size == videoTitles?.size ?: true,
            "Array 'videoTitles' can only be null or its size equals the size of Array 'uriStrings'")

    if (uriStrings.isEmpty()) return

    startActivity(
            Intent(this, VideoActivity::class.java)
                    .putExtra(KEY_VIDEO_URIS, uriStrings.map { Uri.parse(it) }.toTypedArray())
                    .putExtra(KEY_VIDEO_TITLES, videoTitles)
                    .putExtra(KEY_SELECTION, selection))
}

@JvmOverloads
fun Context.playVideos(uris: Array<Uri>, videoTitles: Array<String?>? = null, selection: Int) {
    Preconditions.checkArgument(uris.size == videoTitles?.size ?: true,
            "Array 'videoTitles' can only be null or its size equals the size of Array 'uris'")

    if (uris.isEmpty()) return

    startActivity(
            Intent(this, VideoActivity::class.java)
                    .putExtra(KEY_VIDEO_URIS, uris)
                    .putExtra(KEY_VIDEO_TITLES, videoTitles)
                    .putExtra(KEY_SELECTION, selection))
}

fun Any?.playVideo(video: Video) {
    when (this) {
        is Fragment -> playVideo(video)
        is Activity -> playVideo(video)
    }
}

fun Fragment.playVideo(video: Video) {
    startActivityForResult(
            Intent(requireContext(), VideoActivity::class.java)
                    .putExtra(KEY_VIDEO, video),
            REQUEST_CODE_PLAY_VIDEO)
}

fun Activity.playVideo(video: Video) {
    startActivityForResult(
            Intent(this, VideoActivity::class.java)
                    .putExtra(KEY_VIDEO, video),
            REQUEST_CODE_PLAY_VIDEO)
}

fun Any?.playVideos(vararg videos: Video, selection: Int) {
    when (this) {
        is Fragment -> playVideos(*videos, selection = selection)
        is Activity -> playVideos(*videos, selection = selection)
    }
}

fun Fragment.playVideos(vararg videos: Video, selection: Int) {
    if (videos.isEmpty()) return

    startActivityForResult(
            Intent(requireContext(), VideoActivity::class.java)
                    .putExtra(KEY_VIDEOS, videos).putExtra(KEY_SELECTION, selection),
            REQUEST_CODE_PLAY_VIDEOS)
}

fun Activity.playVideos(vararg videos: Video, selection: Int) {
    if (videos.isEmpty()) return

    startActivityForResult(
            Intent(this, VideoActivity::class.java)
                    .putExtra(KEY_VIDEOS, videos).putExtra(KEY_SELECTION, selection),
            REQUEST_CODE_PLAY_VIDEOS)
}