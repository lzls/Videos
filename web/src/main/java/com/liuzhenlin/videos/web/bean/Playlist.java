/*
 * Created on 2022-5-25 4:13:14 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.bean;

import androidx.core.util.ObjectsCompat;

import com.google.gson.annotations.SerializedName;
import com.liuzhenlin.videos.web.player.Constants.Keys;

import java.util.Arrays;

public class Playlist {

    @SerializedName(Keys.ID) private String id;
    @SerializedName(Keys.PLAYLIST) private String[] videoIds;
    @SerializedName(Keys.PLAYLIST_INDEX) private int videoIndex;

    public Playlist() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String[] getVideoIds() {
        return videoIds;
    }

    public void setVideoIds(String[] videoIds) {
        this.videoIds = videoIds;
    }

    public int getVideoIndex() {
        return videoIndex;
    }

    public void setVideoIndex(int videoIndex) {
        this.videoIndex = videoIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Playlist playlist = (Playlist) o;
        return videoIndex == playlist.videoIndex &&
                ObjectsCompat.equals(id, playlist.id) &&
                Arrays.equals(videoIds, playlist.videoIds);
    }

    @Override
    public int hashCode() {
        int result = ObjectsCompat.hash(id, videoIndex);
        result = 31 * result + Arrays.hashCode(videoIds);
        return result;
    }

    @Override
    public String toString() {
        return "Playlist{" +
                "id='" + id + '\'' +
                ", videoIds=" + Arrays.toString(videoIds) +
                ", videoIndex=" + videoIndex +
                '}';
    }
}
