/*
 * Created on 2019/3/15 8:51 PM.
 * Copyright © 2019–2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos

import android.os.Environment
import android.view.ViewGroup
import androidx.collection.ArrayMap
import androidx.fragment.app.Fragment
import com.liuzhenlin.common.utils.FileUtils
import com.liuzhenlin.videos.bean.Video
import com.liuzhenlin.videos.bean.VideoDirectory
import com.liuzhenlin.videos.bean.VideoListItem
import com.liuzhenlin.videos.dao.IVideoDirectoryDao
import com.liuzhenlin.videos.dao.VideoListItemDao
import java.io.File
import java.util.*
import java.util.regex.Pattern

/**
 * @author 刘振林
 */

public val Fragment.contextThemedFirst get() = activity ?: contextRequired
public val Fragment.contextRequired get() = context ?: App.getInstanceUnsafe()!!

private val sVideoListItemComparator = Comparator<VideoListItem> { item, item2 ->
    if (item is Video) {
        if (item2 !is Video) {
            return@Comparator 1
        }
    } else if (item2 is Video) {
        return@Comparator -1
    }

    val result = item.name.compareTo(item2.name, ignoreCase = true)
    if (result == 0) {
        item.path.compareTo(item2.path, ignoreCase = true)
    } else {
        result
    }
}

@JvmName("sortVideoListItemsByName")
fun <T : VideoListItem> List<T>?.sortByElementName() {
    Collections.sort(this ?: return, sVideoListItemComparator)
}

@JvmName("reorderedVideoListItems")
fun <T : VideoListItem> List<T>.reordered(): MutableList<T> {
    val items = toMutableList()
    items.sortByElementName()

    var toppedItems: MutableList<T>? = null
    for (item in items) {
        if (item.isTopped) {
            if (toppedItems == null) toppedItems = LinkedList()
            toppedItems.add(item)
        }
    }

    if (toppedItems != null) {
        items.removeAll(toppedItems)
        items.addAll(0, toppedItems)
    }

    return items
}

fun <T : VideoListItem> List<T>?.allEqual(other: List<T>?): Boolean {
    this ?: return other == null

    if (other == null) return size == 0

    if (other.size != size) return false

    for (i in indices) {
        if (!this[i].allEqual(other[i])) return false
    }

    return true
}

fun <T> MutableList<T>.set(src: List<T>?) {
    when {
        src == null -> clear()
        size == src.size -> for (i in indices) this[i] = src[i]
        else -> {
            clear()
            addAll(src)
        }
    }
}

fun <T : VideoListItem> MutableList<T>.deepCopy(src: List<T>) {
    if (size == src.size) {
        for (i in indices) this[i] = src[i].deepCopy()
    } else {
        clear()
        addAll(src.map { it.deepCopy() as T })
    }
}

@JvmName("insertVideoDirToDB")
fun IVideoDirectoryDao.insertVideoDir(directory: String): VideoDirectory {
    val videodir = VideoDirectory()
    videodir.name = FileUtils.getFileNameFromFilePath(directory)
    videodir.path = directory

    insertVideoDir(videodir)

    return videodir
}

fun Collection<Video>?.toVideoListItems(baseVideoDir: String? = null): MutableList<VideoListItem>? {
    this ?: return null
    if (size == 0) return null

    val dao = VideoListItemDao.getSingleton(App.getInstanceUnsafe()!!)
    val sdcardRoot = Environment.getExternalStorageDirectory().absolutePath
    val baseVideoDirIsSdcardRoot =
            baseVideoDir == null || sdcardRoot.equals(baseVideoDir, ignoreCase = true)
    val videosMap = ArrayMap<String, Any>()
    var videodirs: MutableList<VideoDirectory>? = null
    var newVideoDirs: MutableList<String>? = null
    var index = 0

    // Classify videos by the directory they belong to
    for (video in this) {
        val path = video.path
        // Directory of the video file
        val videodir = path.substring(0, path.lastIndexOf(File.separatorChar))

        var videos: MutableList<Video>? = null
        for (k in videosMap.keys)
            if (k.equals(videodir, ignoreCase = true)) {
                @Suppress("UNCHECKED_CAST")
                videos = videosMap[k] as MutableList<Video>
                break
            }
        if (videos == null) {
            videos = mutableListOf()

            videosMap[videodir] = videos
        }
        videos.add(video)
    }
    while (index < videosMap.size) {
        @Suppress("UNCHECKED_CAST")
        val videos = videosMap.valueAt(index) as MutableList<VideoListItem>
        val dirPath = videosMap.keyAt(index)
        val isSdcardRoot = baseVideoDirIsSdcardRoot && dirPath.equals(sdcardRoot, ignoreCase = true)
        var videodir = dao.queryVideoDirByPath(dirPath)
        val videodirAlreadyExists = videodir != null
        if (!videodirAlreadyExists) {
            videodir = dao.insertVideoDir(dirPath)
        }
        if (!videodirAlreadyExists && !isSdcardRoot) {
            if (newVideoDirs == null) newVideoDirs = LinkedList()
            newVideoDirs.add(videodir!!.path)
        }
        videodir!!.videoListItems = videos

        if (!isSdcardRoot) {
            if (videodirs == null) videodirs = ArrayList()
            videodirs.add(videodir)
        }
        videosMap.setValueAt(index, videodir)

        index++
    }

    // Delete DB records for outdated video dirs descending from baseVideoDir
    val videodirCursor = dao.queryAllVideoDirs()
    if (videodirCursor != null) {
        while (videodirCursor.moveToNext()) {
            val videodir = dao.buildVideoDir(videodirCursor)
            if ((baseVideoDir == null || videodir.path.startsWith("$baseVideoDir/", ignoreCase = true))
                    && videodirs?.contains(videodir) == false) {
                dao.deleteVideoDir(videodir.path)
            }
        }
        videodirCursor.close()
    }

    // Structure the video dirs to be hierarchical, i.e., tree-structured
    if (videodirs != null) {
        var i = 0
        var j = 0
        outer@
        while (i < videodirs.size) {
            val videodir = videodirs[i]
            j = i + 1
            while (j < videodirs.size) {
                val videodir1 = videodirs[j]
                if (videodir.path.length < videodir1.path.length
                        && videodir1.path.startsWith("${videodir.path}/", ignoreCase = true)) {
                    videodir.videoListItems.add(videodir1)
                    videodirs.removeAt(j)
                    videosMap.remove(videodir1.path)
                    j--
                } else if (videodir1.path.length < videodir.path.length
                        && videodir.path.startsWith("${videodir1.path}/", ignoreCase = true)) {
                    videodir1.videoListItems.add(videodir)
                    videodirs.removeAt(i)
                    videosMap.remove(videodir.path)
                    continue@outer
                }
                j++
            }
            i++
        }
        for (videodir in videodirs) {
            videodir.hierarchize()
            if (newVideoDirs != null) {
                for (newVideoDir in newVideoDirs) {
                    if (newVideoDir.equals(videodir.path, ignoreCase = true)) {
                        videodir.setVideoItemsTopped(topped = false, recursive = true, dao)
                        break
                    }
                }
            }
            videodir.reorderItems(recursive = true)
            videodir.computeSize()
        }
    }

    // If a top level dir only has one video, add the video to the set of list items instead.
    if (baseVideoDirIsSdcardRoot) {
        index = 0
        while (index < videosMap.size) {
            val item = videosMap.valueAt(index)
            if (item is VideoDirectory && item.videoListItems.size == 1) {
                videosMap.setValueAt(index, item.videoListItems[0])
                dao.deleteVideoDir(item.path)
            }
            index++
        }
    }

    // Reorder video list items
    var items: MutableList<VideoListItem>? = null
    var toppedItems: MutableList<VideoListItem>? = null
    index = 0
    while (index < videosMap.size) {
        if (items == null) items = mutableListOf()

        val item = videosMap.valueAt(index) as VideoListItem
        if (item is VideoDirectory
                && baseVideoDirIsSdcardRoot && item.path.equals(sdcardRoot, ignoreCase = true)) {
            for (video in item.videoListItems) {
                if (video.isTopped) {
                    if (toppedItems == null) toppedItems = LinkedList()
                    toppedItems.add(video)
                } else {
                    items.add(video)
                }
            }
        } else {
            if (item.isTopped) {
                if (toppedItems == null) toppedItems = LinkedList()
                toppedItems.add(item)
            } else {
                items.add(item)
            }
        }

        index++
    }
    if (items != null) {
        items.sortByElementName()
        if (toppedItems != null) {
            toppedItems.sortByElementName()
            items.addAll(0, toppedItems)
        }
    }

    if (!baseVideoDirIsSdcardRoot) {
        val item0 = items?.get(0) as VideoDirectory
        if (item0.path.equals(baseVideoDir, ignoreCase = true)) {
            return item0.videoListItems
        }
    }
    return items
}

@JvmName("hierarchizeVideoDirs")
fun VideoDirectory.hierarchize() {
    val vds = videoListItems.filterIsInstanceTo(mutableListOf<VideoDirectory>())

    var i = 0
    var j = 0
    outer@
    while (i < vds.size) {
        val vd = vds[i]
        j = i + 1
        while (j < vds.size) {
            val vd1 = vds[j]
            if (vd.path.length < vd1.path.length
                    && vd1.path.startsWith("${vd.path}/", ignoreCase = true)) {
                vd.videoListItems.add(vd1)
                videoListItems.remove(vd1)
                vds.removeAt(j)
                if (j < i) i--
                j--
            } else if (vd1.path.length < vd.path.length
                    && vd.path.startsWith("${vd1.path}/", ignoreCase = true)) {
                vd1.videoListItems.add(vd)
                videoListItems.remove(vd)
                vds.removeAt(i)
                continue@outer
            }
            j++
        }
        i++
    }

    for (vd in vds) {
        vd.hierarchize()
    }
}

@JvmName("setVideoItemsInDirTopped")
fun VideoDirectory.setVideoItemsTopped(topped: Boolean, recursive: Boolean, dao: VideoListItemDao) {
    for (item in videoListItems) {
        if (item.isTopped != topped) {
            item.isTopped = topped
            dao.setVideoListItemTopped(item, topped)
        }
        if (recursive) {
            if (item is VideoDirectory) {
                item.setVideoItemsTopped(topped, true, dao)
            }
        }
    }
}

@JvmName("reorderVideoDirItems")
fun VideoDirectory.reorderItems(recursive: Boolean = false) {
    videoListItems = videoListItems.reordered()
    if (recursive) {
        for (item in videoListItems) {
            if (item is VideoDirectory) {
                item.reorderItems(true)
            }
        }
    }
}

@JvmName("firstDirectoryVideoOrNull")
fun VideoDirectory.firstVideoOrNull(findRecursively: Boolean = true): Video? {
    for (videoItem in videoListItems) {
        if (videoItem is Video)
            return videoItem
    }
    if (findRecursively) {
        for (videoItem in videoListItems) {
            if (videoItem is VideoDirectory) {
                val video = videoItem.firstVideoOrNull(true)
                if (video != null)
                    return video
            }
        }
    }
    return null
}

@JvmName("searchVideoListItem")
fun Collection<VideoListItem>?.search(
        predicate: (VideoListItem) -> Boolean, recursive: Boolean = true): VideoListItem? {
    for (item in this ?: return null) {
        if (predicate(item)) {
            return item
        }
        if (recursive && item is VideoDirectory) {
            val ret = item.videoListItems.search(predicate, true)
            if (ret != null)
                return ret
        }
    }
    return null
}

@JvmName("getDirectoryVideos")
fun VideoDirectory.videos(includeDescendants: Boolean = true): MutableList<Video> {
    val videos = mutableListOf<Video>()
    for (videoItem in videoListItems) {
        if (videoItem is Video) {
            videos.add(videoItem)
        } else if (includeDescendants && videoItem is VideoDirectory) {
            videos.addAll(videoItem.videos(true))
        }
    }
    return videos
}

@JvmName("getVideoCount")
fun VideoDirectory.videoCount(includeDescendants: Boolean = true): Int {
    var count = 0
    for (videoItem in videoListItems) {
        if (videoItem is Video) {
            count++
        } else if (includeDescendants && videoItem is VideoDirectory) {
            count += videoItem.videoCount(true)
        }
    }
    return count
}

@JvmName("computeVideoDirSize")
fun VideoDirectory.computeSize(recursive: Boolean = true): Long {
    var size = 0L
    for (item in videoListItems) {
        if (item is Video) {
            size += item.size
        } else if (recursive && item is VideoDirectory) {
            size += item.computeSize(true)
        }
    }
    if (recursive) {
        this.size = size
    }
    return size
}

fun VideoDirectory.hasUnwrittableVideo(checkRecursively: Boolean = true): Boolean {
    for (videoItem in videoListItems) {
        if (videoItem is Video) {
            if (!videoItem.isWritable)
                return true
        }
        if (checkRecursively && videoItem is VideoDirectory) {
            if (videoItem.hasUnwrittableVideo(true))
                return true
        }
    }
    return false
}

@get:JvmName("getVideoTitle")
val Video?.title: String
    get() =
        if (this == null) ""
        else {
            val indexOfSuffix = name.lastIndexOf(".")
            if (indexOfSuffix > 0) {
                name.substring(0, indexOfSuffix)
            } else {
                name
            }
        }

@get:JvmName("getVideoSuffix")
val Video?.suffix: String
    get() {
        if (this != null) {
            val indexOfSuffix = name.lastIndexOf(".")
            if (indexOfSuffix > 0) {
                return name.substring(indexOfSuffix)
            }
        }
        return ""
    }

fun CharSequence?.equalsOrMatches(pattern: CharSequence?, equalsIgnoreCase: Boolean = false): Boolean {
    if (this === pattern) return true
    if (this != null && pattern != null) {
        return this.contentEquals(pattern, equalsIgnoreCase)
                || Pattern.matches(pattern.toString(), this)
    }
    return false
}

/**
 * Returns the view at [index].
 *
 * @throws IndexOutOfBoundsException if index is less than 0 or greater than or equal to the count.
 */
operator fun ViewGroup.get(index: Int) =
        getChildAt(index) ?: throw IndexOutOfBoundsException("Index: $index, Size: $childCount")
