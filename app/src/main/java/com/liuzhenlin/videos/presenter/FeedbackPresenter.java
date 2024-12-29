/*
 * Created on 2020-12-10 2:11:34 PM.
 * Copyright © 2020 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.presenter;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liuzhenlin.common.Consts;
import com.liuzhenlin.common.utils.NetworkUtil;
import com.liuzhenlin.common.utils.Synthetic;
import com.liuzhenlin.videos.R;
import com.liuzhenlin.videos.bean.FeedbackInfo;
import com.liuzhenlin.videos.model.FeedbackRepository;
import com.liuzhenlin.videos.utils.MailUtil;
import com.liuzhenlin.videos.utils.Utils;
import com.liuzhenlin.videos.view.activity.IFeedbackView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static com.liuzhenlin.common.Consts.EMPTY_STRING;
import static com.liuzhenlin.common.Consts.EMPTY_STRING_ARRAY;

/**
 * @author 刘振林
 */
class FeedbackPresenter extends Presenter<IFeedbackView> implements IFeedbackPresenter,
        FeedbackRepository.Callback, FeedbackRepository.UserFilledTextsFetcher {

    private static final String PREFIX_MAIL_SUBJECT = "[视频反馈] ";
    private static final int MAX_COUNT_UPLOAD_PICTURES = 3;
    @Synthetic FeedbackRepository mFeedbackRepository;
    private final PictureGridAdapter mGridAdapter = new PictureGridAdapter();

    private boolean mSavedFeedbackInfoLoaded;
    private static final String KEY_SAVED_FEEDBACK_INFO_LOADED = "ksfil";

    private static final String KEY_SAVED_FEEDBACK_TEXT = "ksft";
    private static final String KEY_SAVED_CONTACT_WAY = "kscw";
    private static final String KEY_SAVED_PICTURE_PATHS = "kspp";

    private static final String KEY_FILLED_FEEDBACK_TEXT = "kfft";
    private static final String KEY_FILLED_CONTACT_WAY = "kfcw";
    private static final String KEY_FILLED_PICTURE_PATHS = "kfpp";

    @Override
    public void attachToView(@NonNull IFeedbackView view) {
        super.attachToView(view);
        mFeedbackRepository = FeedbackRepository.create(mContext, this, MAX_COUNT_UPLOAD_PICTURES);
        mFeedbackRepository.setCallback(this);
    }

    @Override
    public void detachFromView(@NonNull IFeedbackView view) {
        mFeedbackRepository.clearPictures(true);
        mFeedbackRepository.setCallback(null);
        mFeedbackRepository = null;
        super.detachFromView(view);
    }

    @Override
    public void onViewCreated(@NonNull IFeedbackView view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            loadSavedFeedbackInfo();
        }
    }

    private void loadSavedFeedbackInfo() {
        FeedbackRepository repository = mFeedbackRepository;
        if (repository == null) return;

        repository.loadSavedFeedbackInfo(feedbackInfo -> {
            String feedbackText = feedbackInfo.getText();
            String contactWay = feedbackInfo.getContactWay();
            List<String> picturePaths = feedbackInfo.getPicturePaths();

            repository.setSavedFeedbackInfo(feedbackText, contactWay, picturePaths);
            mSavedFeedbackInfoLoaded = true;

            repository.setFeedbackText(feedbackText);
            repository.setContactWay(contactWay);
            if (picturePaths != null) {
                for (String path: picturePaths) {
                    repository.addPicture(path);
                }
            }
        });
    }

    @Override
    public void restoreInstanceState(@NonNull Bundle savedInstanceState) {
        mSavedFeedbackInfoLoaded = savedInstanceState.containsKey(KEY_SAVED_FEEDBACK_INFO_LOADED);
        if (!mSavedFeedbackInfoLoaded) {
            loadSavedFeedbackInfo();
            return;
        }

        FeedbackRepository repository = mFeedbackRepository;
        if (repository != null) {
            String[] savedPicturePaths = (String[])
                    savedInstanceState.getSerializable(KEY_SAVED_PICTURE_PATHS);
            List<String> savedPicturePathList =
                    savedPicturePaths == null
                            ? null : new ArrayList<>(Arrays.asList(savedPicturePaths));
            if (savedPicturePathList != null) {
                Iterator<String> it = savedPicturePathList.iterator();
                while (it.hasNext()) {
                    if (!new File(it.next()).exists()) {
                        it.remove();
                    }
                }
            }
            repository.setSavedFeedbackInfo(
                    savedInstanceState.getString(KEY_SAVED_FEEDBACK_TEXT, EMPTY_STRING),
                    savedInstanceState.getString(KEY_SAVED_CONTACT_WAY, EMPTY_STRING),
                    savedPicturePathList);

            repository.setFeedbackText(
                    savedInstanceState.getString(KEY_FILLED_FEEDBACK_TEXT, EMPTY_STRING));
            repository.setContactWay(
                    savedInstanceState.getString(KEY_FILLED_CONTACT_WAY, EMPTY_STRING));
            String[] picturePaths = (String[])
                    savedInstanceState.getSerializable(KEY_FILLED_PICTURE_PATHS);
            if (picturePaths != null) {
                for (String path : picturePaths) {
                    if (!new File(path).exists()) {
                        continue;
                    }
                    repository.addPicture(path);
                }
            }
        }
    }

    @Override
    public void saveInstanceState(@NonNull Bundle outState) {
        FeedbackRepository repository = mFeedbackRepository;
        if (repository != null && mSavedFeedbackInfoLoaded) {
            outState.putString(KEY_SAVED_FEEDBACK_INFO_LOADED, null);

            FeedbackInfo savedFeedbackInfo = repository.getSavedFeedbackInfo();
            outState.putString(KEY_SAVED_FEEDBACK_TEXT, savedFeedbackInfo.getText());
            outState.putString(KEY_SAVED_CONTACT_WAY, savedFeedbackInfo.getContactWay());
            if (savedFeedbackInfo.getPicturePaths() != null) {
                outState.putSerializable(KEY_SAVED_PICTURE_PATHS,
                        savedFeedbackInfo.getPicturePaths().toArray(Consts.EMPTY_STRING_ARRAY));
            }

            List<String> picturePaths = repository.getPicturePaths();
            outState.putString(KEY_FILLED_FEEDBACK_TEXT, getFeedbackText());
            outState.putString(KEY_FILLED_CONTACT_WAY, getContactWay());
            if (!picturePaths.isEmpty()) {
                outState.putSerializable(KEY_FILLED_PICTURE_PATHS,
                        picturePaths.toArray(Consts.EMPTY_STRING_ARRAY));
            }
        }
    }

    @Override
    public void persistentlySaveUserFilledData(boolean toastResultIfSaved) {
        if (mFeedbackRepository != null
                && mFeedbackRepository.persistentlySaveUserFilledData()
                && toastResultIfSaved && mView != null) {
            mView.toastResultOnUserFilledDataSaved();
        }
    }

    @Override
    public void onBackPressed(@NonNull OnBackPressedCallback callback) {
        if (mFeedbackRepository != null
                && mFeedbackRepository.hasUserFilledDataChanged()) {
            callback.showConfirmSaveDataDialog();
        } else {
            callback.back();
        }
    }

    @Override
    public void sendFeedback() {
        String text = getFeedbackText();
        String contactWay = getContactWay();
        if (mThemedContext != null && NetworkUtil.isNetworkConnected(mContext)) {
            String deviceInfo = Utils.collectAppAndDeviceInfo(mContext).toString();
            if (!deviceInfo.isEmpty()) {
                text += "\n\n----------------------------------------------------------------\n"
                        + deviceInfo;
            }
            String[] attachmentPaths = null;
            if (mFeedbackRepository != null) {
                List<String> picturePaths = mFeedbackRepository.getPicturePaths();
                attachmentPaths =
                        picturePaths.isEmpty() ? null : picturePaths.toArray(EMPTY_STRING_ARRAY);
            }
            MailUtil.sendMail(
                    mThemedContext, PREFIX_MAIL_SUBJECT + contactWay, text, null, attachmentPaths);

            if (mFeedbackRepository != null) {
                mFeedbackRepository.setFeedbackText(EMPTY_STRING);
                mFeedbackRepository.setContactWay(EMPTY_STRING);
                mFeedbackRepository.clearPictures(false);
                mFeedbackRepository.persistentlySaveUserFilledData();
            }
        } else {
            if (mView != null) {
                mView.showToast(mContext, R.string.noNetworkConnection, Toast.LENGTH_SHORT);
            }
            persistentlySaveUserFilledData(false);
        }
    }

    @Override
    public void onFeedbackTextChanged(@NonNull String feedbackText) {
        if (mView != null) {
            mView.setFeedbackText(feedbackText);
        }
    }

    @Override
    public void onContactWayChanged(@NonNull String contactWay) {
        if (mView != null) {
            mView.setContactWayText(contactWay);
        }
    }

    @NonNull
    @Override
    public String getFeedbackText() {
        return mView == null ? EMPTY_STRING : mView.getFeedbackText();
    }

    @NonNull
    @Override
    public String getContactWay() {
        return mView == null ? EMPTY_STRING : mView.getContactWay();
    }

    @Override
    public void addPicture(@Nullable String path) {
        if (mFeedbackRepository != null) {
            mFeedbackRepository.addPicture(path);
        }
    }

    @Override
    public void onPictureAdded(@NonNull Bitmap picture) {
        mGridAdapter.notifyDataSetChanged();
    }

    @Override
    public void removePictureAt(int index) {
        if (mFeedbackRepository != null) {
            mFeedbackRepository.removePictureAt(index);
        }
    }

    @Override
    public void onPictureRemoved(@NonNull Bitmap picture) {
        // 图片全部被删除时，销毁对话框
        if (mView != null && mFeedbackRepository != null
                && mFeedbackRepository.getPictures().size() == 1) {
            mView.hidePicturePreviewDialog();
        }
        mGridAdapter.notifyDataSetChanged();
    }

    @Override
    public void onPictureCleared() {
        if (mView != null) {
            mView.hidePicturePreviewDialog();
        }
        mGridAdapter.notifyDataSetChanged();
    }

    @NonNull
    @Override
    public <T extends BaseAdapter & AdapterView.OnItemClickListener> T getPictureGridAdapter() {
        //noinspection unchecked
        return (T) mGridAdapter;
    }

    private final class PictureGridAdapter extends BaseAdapter
            implements AdapterView.OnItemClickListener {

        PictureGridAdapter() {
        }

        @Override
        public int getCount() {
            int count = mFeedbackRepository == null ? 0 : mFeedbackRepository.getPictures().size();
            return Math.min(count, MAX_COUNT_UPLOAD_PICTURES);
        }

        @Override
        public Object getItem(int position) {
            return mFeedbackRepository.getPictures().get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return mView.getPictureGridViewHolder(position, convertView, parent,
                    (Bitmap) getItem(position), mFeedbackRepository.getPictures().size()).itemView;
        }

        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            List<Bitmap> pictures = mFeedbackRepository.getPictures();
            int pictureCount = pictures.size();
            if (position == pictureCount - 1) {
                // 通过图片路径数判断，否则如果图片还未全部加载，会导致判断不准
                if (mFeedbackRepository.getPicturePaths().size() < MAX_COUNT_UPLOAD_PICTURES) {
                    mView.pickPicture();
                }
            } else {
                mView.showPicturePreviewDialog(pictures.subList(0, pictureCount - 1), position);
            }
        }
    }
}
