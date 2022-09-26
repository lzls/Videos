#!/bin/bash

trace cd "$EXO_PLAYER_ROOT" &&
  VP9_MODULE_PATH="$(pwd)/extensions/vp9/src/main"
verifyLastOpSuccessed

trace cd "${VP9_MODULE_PATH}/jni" &&
  ensureRepoUpdateToDate https://chromium.googlesource.com/webm/libvpx libvpx v1.8.0 --clean &&
  cd "${VP9_MODULE_PATH}/jni/libvpx" &&
  LIBVPX_PATH="$(pwd)"
verifyLastOpSuccessed

trace cd "${VP9_MODULE_PATH}/jni" &&
#  ln -s "$LIBVPX_PATH" libvpx &&
  trace ./generate_libvpx_android_configs.sh
verifyLastOpSuccessed

trace cd "${VP9_MODULE_PATH}/jni" &&
  trace "${NDK_PATH}/ndk-build" APP_ABI=all -j4 &&
  trace rm -rf ../jniLibs &&
  trace mv -f ../libs ../jniLibs &&
  trace cp -rf ../jniLibs "$EXO_PLAYER_ROOT/../Videos/exoplayer2-extensions/vp9/src/main"
