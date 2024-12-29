/*
 * Created on 2024-12-22 11:57:30 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.bean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;

import com.liuzhenlin.common.Consts;

import java.util.List;

public class FeedbackInfo {

    @NonNull private String text = Consts.EMPTY_STRING;
    @NonNull private String contactWay = Consts.EMPTY_STRING;
    @Nullable private List<String> picturePaths;

    public FeedbackInfo() {
    }

    public FeedbackInfo(
            @NonNull String text, @NonNull String contactWay, @Nullable List<String> picturePaths) {
        this.text = text;
        this.contactWay = contactWay;
        this.picturePaths = picturePaths;
    }

    @NonNull
    public String getText() {
        return text;
    }

    public void setText(@NonNull String text) {
        this.text = text;
    }

    @NonNull
    public String getContactWay() {
        return contactWay;
    }

    public void setContactWay(@NonNull String contactWay) {
        this.contactWay = contactWay;
    }

    @Nullable
    public List<String> getPicturePaths() {
        return picturePaths;
    }

    public void setPicturePaths(@Nullable List<String> picturePaths) {
        this.picturePaths = picturePaths;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        FeedbackInfo that = (FeedbackInfo) o;
        return ObjectsCompat.equals(text, that.text)
                && ObjectsCompat.equals(contactWay, that.contactWay)
                && ObjectsCompat.equals(picturePaths, that.picturePaths);
    }

    @Override
    public int hashCode() {
        return ObjectsCompat.hash(text, contactWay, picturePaths);
    }

    @NonNull
    @Override
    public String toString() {
        return "FeedbackInfo{" +
                "text='" + text + '\'' +
                ", contactWay='" + contactWay + '\'' +
                ", picturePaths=" + picturePaths +
                '}';
    }
}
