/*
 * Created on 2022-2-23 4:10:43 PM.
 * Copyright © 2022 刘振林. All rights reserved.
 */

package com.liuzhenlin.videos.web.youtube;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentManager;

import com.liuzhenlin.common.utils.InternetResourceLoadTask;
import com.liuzhenlin.videos.web.R;

public class YoutubeActivity extends AppCompatActivity {

    private YoutubeFragment mFragment;
    private static final String TAG_YOUTUBE_FRAGMENT = "YoutubeFragment";

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(newBase);
        InternetResourceLoadTask.setAppContext(newBase.getApplicationContext());
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube);

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            mFragment = new YoutubeFragment();
            fm.beginTransaction()
                    .add(R.id.content, mFragment, TAG_YOUTUBE_FRAGMENT)
                    .commit();
        } else {
            mFragment = (YoutubeFragment) fm.findFragmentByTag(TAG_YOUTUBE_FRAGMENT);
        }
    }

    @Override
    public void onBackPressed() {
        if (!mFragment.onBackPressed()) {
            super.onBackPressed();
        }
    }
}