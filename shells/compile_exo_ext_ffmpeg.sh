#!/bin/bash

trace cd "$EXO_PLAYER_ROOT" &&
  FFMPEG_MODULE_PATH="$(pwd)/extensions/ffmpeg/src/main"
verifyLastOpSuccessed

ENABLED_DECODERS=(vorbis opus flac alac pcm_mulaw pcm_alaw mp3 amrnb amrwb aac ac3 eac3 dca mlp truehd)

trace cd "${FFMPEG_MODULE_PATH}/jni" &&
  ensureRepoUpdateToDate git://source.ffmpeg.org/ffmpeg ffmpeg release/4.2 --clean &&
  cd "${FFMPEG_MODULE_PATH}/jni/ffmpeg" &&
  FFMPEG_PATH="$(pwd)"
verifyLastOpSuccessed

#cd "${FFMPEG_MODULE_PATH}/jni" &&
#  ln -s "$FFMPEG_PATH" ffmpeg

trace cd "${FFMPEG_MODULE_PATH}/jni" &&
  trace ./build_ffmpeg.sh \
    "${FFMPEG_MODULE_PATH}" "${NDK_PATH}" "${HOST_PLATFORM}" "${ENABLED_DECODERS[@]}"
