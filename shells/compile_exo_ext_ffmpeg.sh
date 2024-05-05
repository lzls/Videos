#!/bin/bash

# Set the following shell variable.
trace cd "$EXO_PLAYER_ROOT" &&
  FFMPEG_MODULE_PATH="$(pwd)/libraries/decoder_ffmpeg/src/main"
verifyLastOpSuccessed

# Set the ABI version for native code (typically it's equal to minSdk and must not exceed it).
int ANDROID_ABI=16

# Configure all the available decoders to include.
ENABLED_DECODERS=(vorbis opus flac alac pcm_mulaw pcm_alaw mp3 amrnb amrwb aac ac3 eac3 dca mlp truehd)

# Fetch FFmpeg and checkout an appropriate branch. We cannot guarantee compatibility with
# all versions of FFmpeg. We currently recommend version 6.0
trace cd "${FFMPEG_MODULE_PATH}/jni" &&
  ensureRepoUpdateToDate git://source.ffmpeg.org/ffmpeg ffmpeg release/6.0 --clean &&
  cd "${FFMPEG_MODULE_PATH}/jni/ffmpeg" &&
  FFMPEG_PATH="$(pwd)"
verifyLastOpSuccessed

# Add a link to the FFmpeg source code in the FFmpeg module jni directory.
#cd "${FFMPEG_MODULE_PATH}/jni" &&
#  ln -s "$FFMPEG_PATH" ffmpeg

# Execute build_ffmpeg.sh to build FFmpeg for armeabi-v7a, arm64-v8a, x86 and x86_64.
# The script can be edited if you need to build for different architectures.
trace cd "${FFMPEG_MODULE_PATH}/jni" &&
  trace ./build_ffmpeg.sh \
      "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ANDROID_ABI}" "${ENABLED_DECODERS[@]}"
