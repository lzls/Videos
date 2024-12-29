/*
 * Created on 2020-12-10 2:09:17 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter;

import android.os.Bundle;
import android.widget.AdapterView;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.videos.view.activity.IFeedbackView;

/**
 * @author 刘振林
 */
public interface IFeedbackPresenter extends IPresenter<IFeedbackView> {

    @NonNull
    static IFeedbackPresenter newInstance() {
        return new FeedbackPresenter();
    }

    void restoreInstanceState(@NonNull Bundle savedInstanceState);
    void saveInstanceState(@NonNull Bundle outState);

    void persistentlySaveUserFilledData(boolean toastResultIfSaved);

    void onBackPressed(@NonNull OnBackPressedCallback callback);

    void sendFeedback();

    void addPicture(@Nullable String path);
    void removePictureAt(int index);

    @NonNull
    <T extends BaseAdapter & AdapterView.OnItemClickListener> T getPictureGridAdapter();

    public interface OnBackPressedCallback {
        void showConfirmSaveDataDialog();
        void back();
    }
}
