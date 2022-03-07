// IWebService.aidl
package com.liuzhenlin.videos.web.youtube;

// Declare any non-default types here with import statements

interface IWebService {

    void applyDefaultNightMode(int mode);

    void finishYoutubePlaybackActivityIfItIsInPiP();
}