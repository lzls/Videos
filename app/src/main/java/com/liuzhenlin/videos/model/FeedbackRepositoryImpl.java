/*
 * Created on 2024-12-22 11:12:51 PM.
 * Copyright © 2024 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.model;

import android.content.Context;
import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.util.Consumer;

import com.liuzhenlin.common.Consts;
import com.liuzhenlin.common.utils.BitmapUtils;
import com.liuzhenlin.common.utils.JCoroutine;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.bean.FeedbackInfo;
import com.liuzhenlin.videos.dao.FeedbackSavedPrefs;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;

class FeedbackRepositoryImpl extends BaseRepository<FeedbackRepository.Callback>
        implements FeedbackRepository {

    private final UserFilledTextsFetcher mUserFilledTextsFetcher;
    private final FeedbackSavedPrefs mFeedbackSPs;
    private final FeedbackInfo mSavedFeedbackInfo;

    private final List<Bitmap> mPictures;
    private final List<String> mPicturePaths = new LinkedList<>();
    private final List<String> mLoadedPicturePaths = new LinkedList<>();

    public FeedbackRepositoryImpl(
            @NonNull Context context, @NonNull UserFilledTextsFetcher userTextsFetcher,
            int maxCountOfPicturesToUpload) {
        super(context);
        mUserFilledTextsFetcher = userTextsFetcher;
        mFeedbackSPs = new FeedbackSavedPrefs(context);
        mSavedFeedbackInfo = new FeedbackInfo();
        mPictures = new ArrayList<>(maxCountOfPicturesToUpload + 1);
        //noinspection ConstantConditions
        mPictures.add(BitmapUtils.drawableToBitmap(
                AppCompatResources.getDrawable(context, R.drawable.ic_add_photo_gray_36dp)));
    }

    @Override
    public void loadSavedFeedbackInfo(@NonNull Consumer<FeedbackInfo> callback) {
        CoroutineScope coroutineScope = mCoroutineScope.get();
        JCoroutine.launch(coroutineScope, () -> {
            String feedbackText = mFeedbackSPs.getText();
            String contactWay = mFeedbackSPs.getContactWay();
            List<String> picturePaths = mFeedbackSPs.getPicturePaths();
            if (picturePaths != null) {
                List<String> invalidPaths = null;
                for (String path : picturePaths) {
                    // 有可能该路径下的图片已被删除：如果删除了，从sp文件中移除该保存的路径
                    if (!new File(path).exists()) {
                        if (invalidPaths == null)
                            invalidPaths = new LinkedList<>();
                        invalidPaths.add(path);
                    }
                }
                if (invalidPaths != null) {
                    picturePaths.removeAll(invalidPaths);
                    mFeedbackSPs.edit().setPicturePaths(picturePaths).apply();
                }
            }
            JCoroutine.launch(coroutineScope, Dispatchers.getMain(),
                    () -> callback.accept(new FeedbackInfo(feedbackText, contactWay, picturePaths)));
        });
    }

    @NonNull
    @Override
    public FeedbackInfo getSavedFeedbackInfo() {
        return mSavedFeedbackInfo;
    }

    @Override
    public void setSavedFeedbackInfo(
            @NonNull String text, @NonNull String contactWay, @Nullable List<String> picturePaths) {
        mSavedFeedbackInfo.setText(text);
        mSavedFeedbackInfo.setContactWay(contactWay);
        mSavedFeedbackInfo.setPicturePaths(picturePaths);
    }

    @Override
    public void setFeedbackText(@NonNull String feedbackText) {
        if (mCallback != null && !feedbackText.equals(mUserFilledTextsFetcher.getFeedbackText())) {
            mCallback.onFeedbackTextChanged(feedbackText);
        }
    }

    @Override
    public void setContactWay(@NonNull String contactWay) {
        if (mCallback != null && !contactWay.equals(mUserFilledTextsFetcher.getContactWay())) {
            mCallback.onContactWayChanged(contactWay);
        }
    }

    @NonNull
    @Override
    public List<Bitmap> getPictures() {
        return mPictures;
    }

    @NonNull
    @Override
    public List<String> getPicturePaths() {
        return mPicturePaths;
    }

    @Override
    public void addPicture(@Nullable String picturePath) {
        final List<String> picturePaths = mPicturePaths;
        if (picturePath != null && !picturePaths.contains(picturePath)) {
            picturePaths.add(picturePath);
            JCoroutine.launch(mCoroutineScope.get(), JCoroutine.SingleDispatcher, () -> {
                if (JCoroutine.runBlocking(
                        Dispatchers.getMain(), () -> !picturePaths.contains(picturePath), false)) {
                    return;
                }
                final Bitmap bitmap = BitmapUtils.decodeRotatedBitmapFormFile(picturePath);
                if (bitmap != null) {
                    JCoroutine.launch(mCoroutineScope.get(), Dispatchers.getMain(), () -> {
                        if (picturePaths.contains(picturePath)) {
                            List<String> loadedPicturePaths = mLoadedPicturePaths;
                            List<Bitmap> pictures = mPictures;
                            loadedPicturePaths.add(loadedPicturePaths.size(), picturePath);
                            pictures.add(pictures.size() - 1, bitmap);
                            if (mCallback != null) {
                                mCallback.onPictureAdded(bitmap);
                            }
                        } else {
                            bitmap.recycle();
                        }
                    });
                }
            });
        }
    }

    @Override
    public void removePictureAt(int index) {
        Bitmap picture = mPictures.get(index);
        picture.recycle();
        mPictures.remove(index);
        mPicturePaths.remove(mLoadedPicturePaths.remove(index));
        if (mCallback != null) {
            mCallback.onPictureRemoved(picture);
        }
    }

    @Override
    public void clearPictures() {
        clearPictures(false);
    }

    private void clearPictures(boolean includeAddPhotoBmp) {
        for (int i = mPictures.size() - (includeAddPhotoBmp ? 1 : 2); i >= 0; i--) {
            mPictures.remove(i).recycle();
        }
        mPicturePaths.clear();
        mLoadedPicturePaths.clear();
        if (mCallback != null) {
            mCallback.onPictureCleared();
        }
    }

    @Override
    public void dispose() {
        clearPictures(true);
        super.dispose();
    }

    @Override
    public boolean hasUserFilledDataChanged() {
        return hasUserFilledDataChanged(mUserFilledTextsFetcher.getFeedbackText(),
                mUserFilledTextsFetcher.getContactWay());
    }

    @SuppressWarnings("ConstantConditions")
    private boolean hasUserFilledDataChanged(@NonNull String text, @NonNull String contactWay) {
        String savedFeedbackText = mSavedFeedbackInfo.getText();
        String savedContactWay = mSavedFeedbackInfo.getContactWay();
        List<String> savedPicturePaths = mSavedFeedbackInfo.getPicturePaths();

        if (!(text.equals(savedFeedbackText) && contactWay.equals(savedContactWay)))
            return true;

        boolean arraysAreNull = mPicturePaths == null && savedPicturePaths == null;
        if (arraysAreNull) return false;

        boolean arraysAreNonnull = !(mPicturePaths == null || savedPicturePaths == null);
        if (arraysAreNonnull) {
            //@formatter:off
            return !(mPicturePaths.isEmpty() && savedPicturePaths.isEmpty()
                    || Arrays.equals(
                            mPicturePaths.toArray(Consts.EMPTY_STRING_ARRAY),
                            savedPicturePaths.toArray(Consts.EMPTY_STRING_ARRAY))); //@formatter:on
        } else {
            return !(mPicturePaths != null && mPicturePaths.isEmpty()
                    || savedPicturePaths != null && savedPicturePaths.isEmpty());
        }
    }

    @Override
    public boolean persistentlySaveUserFilledData() {
        String text = mUserFilledTextsFetcher.getFeedbackText();
        String contactWay = mUserFilledTextsFetcher.getContactWay();
        if (hasUserFilledDataChanged(text, contactWay)) {
            setSavedFeedbackInfo(text, contactWay,
                    mPicturePaths.isEmpty() ? null : new ArrayList<>(mPicturePaths));
            mFeedbackSPs.edit()
                    .setText(mSavedFeedbackInfo.getText())
                    .setContactWay(mSavedFeedbackInfo.getContactWay())
                    .setPicturePaths(mSavedFeedbackInfo.getPicturePaths())
                    .apply();
            return true;
        }
        return false;
    }
}
