/*
 * Created on 2017/09/30.
 * Copyright © 2017 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.media.ThumbnailUtils;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.liuzhenlin.common.utils.Utils;
import com.liuzhenlin.videos.App;
import com.liuzhenlin.videos.Configs;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.bean.Video;

/**
 * @author 刘振林
 */
public class VideoUtils2 {
    private VideoUtils2() {
    }

    public static void loadVideoThumbIntoImageView(
            @NonNull ImageView view, @NonNull Video video) {
        loadVideoThumbIntoImageView(view, video.getPath());
    }

    public static void loadVideoThumbIntoImageView(
            @NonNull ImageView view, @NonNull String path) {
        loadVideoThumbIntoFragmentImageView(null, view, path);
    }

    public static void loadVideoThumbIntoFragmentImageView(
            @Nullable Fragment fragment, @NonNull ImageView view, @NonNull Video video) {
        loadVideoThumbIntoFragmentImageView(fragment, view, video.getPath());
    }

    public static void loadVideoThumbIntoFragmentImageView(
            @Nullable Fragment fragment, @NonNull ImageView view, @NonNull String path) {
        Context context = view.getContext();
//        final float aspectRatio = (float) video.getWidth() / (float) video.getHeight();
        final int thumbWidth = getVideoThumbWidth(context);
//        final int height = Utils.roundFloat((float) thumbWidth / aspectRatio);
        final int thumbHeight /* maxHeight */ = Utils.roundFloat(thumbWidth * 9f / 16f);
//        final int thumbHeight = height > maxHeight ? maxHeight : height;

        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp.width != thumbWidth || lp.height != thumbHeight) {
            lp.width = thumbWidth;
            lp.height = thumbHeight;
            view.setLayoutParams(lp);
        }
        view.setScaleType(ImageView.ScaleType.CENTER_CROP);

        RequestManager requestManager;
        if (fragment != null) {
            requestManager = Glide.with(fragment);
        } else {
            requestManager = Glide.with(context);
        }
        requestManager
                .load(path)
                .override(thumbWidth, thumbHeight)
                .centerCrop()
                .placeholder(R.drawable.ic_default_thumb)
                .into(view);
    }

    public static int getVideoThumbWidth(@NonNull Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.videoThumbWidth);
    }

    @Nullable
    public static Bitmap generateMiniThumbnail(@NonNull Resources res, @NonNull String path) {
        //noinspection deprecation
        Bitmap thumb =
                ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND);
        if (thumb != null) {
            final float ratio = res.getDisplayMetrics().widthPixels / 1080f;
            if (ratio != 1) {
                thumb = ThumbnailUtils.extractThumbnail(
                        thumb,
                        Utils.roundFloat(thumb.getWidth() * ratio),
                        Utils.roundFloat(thumb.getHeight() * ratio),
                        ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
            }
        }
        return thumb;
    }

    @NonNull
    public static String formatVideoResolution(int width, int height) {
        if (width == 360 && height == 480 || height == 360 && width == 480) {
            return "360P";
        }
        if (width == 540 && height == 960 || height == 540 && width == 960) {
            return "540P";
        }
        if (width == 720 && height == 1280 || height == 720 && width == 1280) {
            return "720P";
        }
        if (width == 1080 && height == 1920 || height == 1080 && width == 1920) {
            return "1080P";
        }
        if (width == 1440 && height == 2560 || height == 1440 && width == 2560) {
            return "2K";
        }
        if (width == 2160 && height == 3840 || height == 2160 && width == 3840) {
            return "4K";
        }
        if (width == 4320 && height == 7680 || height == 4320 && width == 7680) {
            return "8K";
        }

        return width + " × " + height;
    }

    @NonNull
    public static String concatVideoProgressAndDuration(int progress, int duration) {
        final StringBuilder result = new StringBuilder();

        //noinspection ConstantConditions
        final boolean chinese = "zh".equals(
                App.getInstanceUnsafe().getResources().getConfiguration().locale.getLanguage());

        final String haveFinishedWatching = chinese ? "已看完" : "Have finished watching";
        final String separator = " | ";
        final String wordSeparator = chinese ? "" : " ";
        final String watchedTo = chinese ? "观看至" : "Watched to";
        final String hoursPlural = chinese ? "小时" : "hours";
        final String minutesPlural = chinese ? "分钟" : "minutes";
        final String slong = chinese ? "时长" : "long";
        final String lessThanAMinute = chinese ? "小于1分钟" : "Less than a minute";

        if (progress >= duration - Configs.TOLERANCE_VIDEO_DURATION) {
            result.append(haveFinishedWatching).append(separator);
        } else {
            final int totalSeconds = progress / 1000;
            final int minutes = (totalSeconds / 60) % 60;
            final int hours = totalSeconds / 3600;

            final String shours = chinese ? "小时" : hours > 1 ? "hours" : "hour";
            final String sminutes = chinese ? "分钟" : minutes > 1 ? "minutes" : "minute";

            if (hours > 0) {
                result.append(watchedTo)
                        .append(wordSeparator).append(hours).append(wordSeparator).append(shours);
                if (minutes > 0) {
                    result.append(wordSeparator).append(minutes).append(wordSeparator).append(sminutes);
                }
                result.append(separator);

            } else if (minutes > 0) {
                result.append(watchedTo)
                        .append(wordSeparator).append(minutes).append(wordSeparator).append(sminutes)
                        .append(separator);
            }
        }

        final int totalSeconds = duration / 1000;
        final int minutes = (totalSeconds / 60) % 60;
        final int hours = totalSeconds / 3600;

        final String shours = chinese ? "小时" : hours > 1 ? "hours" : "hour";
        final String sminutes = chinese ? "分钟" : minutes > 1 ? "minutes" : "minute";

        if (hours > 0) {
            if (chinese) {
                result.append(slong)
                        .append(wordSeparator).append(hours).append(wordSeparator).append(shours);
            } else {
                result.append(hours).append(wordSeparator).append(minutes > 0 ? shours : hoursPlural);
            }
            if (minutes > 0) {
                result.append(wordSeparator).append(minutes).append(wordSeparator).append(minutesPlural);
            }
            if (!chinese) {
                result.append(wordSeparator).append(slong);
            }
        } else if (minutes > 0) {
            if (chinese) {
                result.append(slong)
                        .append(wordSeparator).append(minutes).append(wordSeparator).append(sminutes);
            } else {
                result.append(minutes).append(wordSeparator).append(minutesPlural).append(wordSeparator)
                        .append(slong);
            }
        } else {
            result.append(lessThanAMinute);
        }

        return result.toString();
    }
}
