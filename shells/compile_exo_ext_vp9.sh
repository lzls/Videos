#!/bin/bash

# Set the following environment variables.
trace cd "$EXO_PLAYER_ROOT" &&
  VP9_MODULE_PATH="$(pwd)/libraries/decoder_vp9/src/main"
verifyLastOpSuccessed

# Fetch an appropriate branch of libvpx. We cannot guarantee compatibility with all versions
# of libvpx. We currently recommend version 1.8.0
trace cd "${VP9_MODULE_PATH}/jni" &&
  ensureRepoUpdateToDate https://chromium.googlesource.com/webm/libvpx libvpx v1.8.0 --clean &&
  cd "${VP9_MODULE_PATH}/jni/libvpx" &&
  LIBVPX_PATH="$(pwd)"
verifyLastOpSuccessed

# Add a link to the libvpx source code in the vp9 module jni directory and run a script
# that generates necessary configuration files for libvpx.
trace cd "${VP9_MODULE_PATH}/jni" &&
#  ln -s "$LIBVPX_PATH" libvpx &&
  trace ./generate_libvpx_android_configs.sh
verifyLastOpSuccessed

# Build the JNI native libraries from the command line and move them to the destination directory.
trace cd "${VP9_MODULE_PATH}/jni" &&
  trace "${NDK_PATH}/ndk-build" APP_ABI=all -j4 &&
  trace rm -rf ../jniLibs &&
  trace mv -f ../libs ../jniLibs &&
  trace cp -rf ../jniLibs "$EXO_PLAYER_ROOT/../Videos/exoplayer2-extensions/vp9/src/main"
