/*
 * Created on 2022-5-25 4:00:51 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.bean;

import androidx.core.util.ObjectsCompat;

import com.google.gson.annotations.SerializedName;
import com.liuzhenlin.videos.web.player.Constants.Keys;

public class Video {

    @SerializedName(Keys.ID) private String id;
    @SerializedName(Keys.WIDTH) private int width;
    @SerializedName(Keys.HEIGHT) private int height;
    @SerializedName(Keys.DURATION) private long duration;
    @SerializedName(Keys.BUFFERED_POSITION) private long bufferedPosition;
    @SerializedName(Keys.CURRENT_POSITION) private long currentPosition;

    public Video() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getBufferedPosition() {
        return bufferedPosition;
    }

    public void setBufferedPosition(long bufferedPosition) {
        this.bufferedPosition = bufferedPosition;
    }
    public long getCurrentPosition() {
        return currentPosition;
    }

    public void setCurrentPosition(long currentPosition) {
        this.currentPosition = currentPosition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Video video = (Video) o;
        return width == video.width &&
                height == video.height &&
                duration == video.duration &&
                bufferedPosition == video.bufferedPosition &&
                currentPosition == video.currentPosition &&
                ObjectsCompat.equals(id, video.id);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(id, width, height, duration, bufferedPosition, currentPosition);
    }

    @Override
    public String toString() {
        return "Video{" +
                "id='" + id + '\'' +
                ", width=" + width +
                ", height=" + height +
                ", duration=" + duration +
                ", bufferedPosition=" + bufferedPosition +
                ", currentPosition=" + currentPosition +
                '}';
    }
}
