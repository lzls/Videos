/*
 * Created on 2024-12-22 11:12:51 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import com.liuzhenlin.videos.bean.FeedbackInfo;

import java.util.List;

public interface FeedbackRepository extends Repository<FeedbackRepository.Callback> {

    @NonNull
    static FeedbackRepository create(
            @NonNull Context context, @NonNull UserFilledTextsFetcher userTextsFetcher,
            int maxCountOfPicturesToUpload) {
        return new FeedbackRepositoryImpl(context, userTextsFetcher, maxCountOfPicturesToUpload);
    }

    void loadSavedFeedbackInfo(@NonNull Consumer<FeedbackInfo> callback);

    @NonNull FeedbackInfo getSavedFeedbackInfo();
    void setSavedFeedbackInfo(
            @NonNull String text, @NonNull String contactWay, @Nullable List<String> picturePaths);

    void setFeedbackText(@NonNull String feedbackText);
    void setContactWay(@NonNull String contactWay);

    @NonNull List<Bitmap> getPictures();
    @NonNull List<String> getPicturePaths();

    void addPicture(@Nullable String picturePath);
    void removePictureAt(int index);
    void clearPictures();

    boolean hasUserFilledDataChanged();
    boolean persistentlySaveUserFilledData();

    public interface Callback extends BaseRepository.Callback {
        void onFeedbackTextChanged(@NonNull String feedbackText);
        void onContactWayChanged(@NonNull String contactWay);
        void onPictureAdded(@NonNull Bitmap picture);
        void onPictureRemoved(@NonNull Bitmap picture);
        void onPictureCleared();
    }

    public interface UserFilledTextsFetcher {
        @NonNull String getFeedbackText();
        @NonNull String getContactWay();
    }
}
