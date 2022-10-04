/*
 * Created on 2022-10-4 11:37:13 AM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import androidx.annotation.NonNull;

import com.liuzhenlin.common.utils.FileUtils;
import com.liuzhenlin.videos.bean.Video;
import com.liuzhenlin.videos.dao.VideoListItemDao;

import java.io.File;

import kotlin.text.StringsKt;

public class LegacyExternalStorageDataMigrator {

    private final Context mContext;

    public LegacyExternalStorageDataMigrator(@NonNull Context context) {
        mContext = context.getApplicationContext();
    }

    public boolean isLegacyDataAccessible() {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q
                || Environment.isExternalStorageLegacy();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @TargetApi(Build.VERSION_CODES.R)
    public boolean migrate() {
        File legacyAppExternalFilesDir = Files.getLegacyAppExternalFilesDir();
        File legacyAppScreenshotsDir =
                new File(legacyAppExternalFilesDir, Files.LEGACY_SCREENSHOTS_LAST_PATH_SEGMENT);
        File legacyAppShortClipsDir =
                new File(legacyAppExternalFilesDir, Files.LEGACY_SHORT_CLIPS_LAST_PATH_SEGMENT);

        File appDownloadsDir = Files.getAppExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File appDocumentsDir = Files.getAppExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File appPicturesDir = Files.getAppExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File appMoviesDir = Files.getAppExternalFilesDir(Environment.DIRECTORY_MOVIES);
        File appScreenshotsDir = new File(appPicturesDir, Files.SCREENSHOTS_FOLDER);
        File appShortClipsDir = new File(appMoviesDir, Files.SHORT_CLIPS_FOLDER);

        if (!appScreenshotsDir.exists()) {
            appScreenshotsDir.mkdir();
        }
        if (!appShortClipsDir.exists()) {
            appShortClipsDir.mkdir();
        }

        boolean suceessful = true;

        File guidFile = new File(Environment.getExternalStorageDirectory(), Files.GUID);
        if (guidFile.exists()) {
            suceessful = guidFile.renameTo(new File(appDocumentsDir, guidFile.getName()));
        }

        File[] apks = legacyAppExternalFilesDir.listFiles(File::isFile);
        if (apks != null) {
            for (File apk : apks) {
                suceessful &= apk.renameTo(new File(appDownloadsDir, apk.getName()));
            }
        }

        File[] shortVideos = legacyAppShortClipsDir.listFiles(File::isFile);
        if (shortVideos != null) {
            VideoListItemDao videoDao = VideoListItemDao.getSingleton(mContext);
            for (File shortVideo : shortVideos) {
                String shortVideoOldPath = shortVideo.getAbsolutePath();
                suceessful &= shortVideo.renameTo(new File(appShortClipsDir, shortVideo.getName()));
                Video video = videoDao.queryVideoByPath(shortVideoOldPath);
                if (video != null) {
                    videoDao.deleteVideo(video.getId());
                }
                FileUtils.recordMediaFileToDatabaseAndScan(
                        mContext,
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        shortVideo,
                        FileUtils.getMimeTypeFromPath(shortVideoOldPath, "video/mp4"));
            }
        }

        File[] screenshots = legacyAppScreenshotsDir.listFiles(File::isFile);
        if (screenshots != null) {
            for (File screenshot : screenshots) {
                String screenshotOldPath = screenshot.getAbsolutePath();
                suceessful &= screenshot.renameTo(new File(appScreenshotsDir, screenshot.getName()));
                mContext.getContentResolver().delete(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        MediaStore.Images.Media.DATA + "='"
                                + StringsKt.replace(screenshotOldPath,"'", "''", false)
                                + "' COLLATE NOCASE",
                        null);
                FileUtils.recordMediaFileToDatabaseAndScan(
                        mContext,
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        screenshot,
                        FileUtils.getMimeTypeFromPath(screenshotOldPath, "image/png"));
            }
        }

        if (suceessful) {
            FileUtils.rmrf(legacyAppExternalFilesDir);
        }

        return suceessful;
    }
}
