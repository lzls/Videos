#!/bin/bash

trace cd "$EXO_PLAYER_ROOT" &&
  AV1_MODULE_PATH="$(pwd)/extensions/av1/src/main"
verifyLastOpSuccessed

trace cd "${AV1_MODULE_PATH}/jni" &&
  ensureRepoUpdateToDate https://github.com/google/cpu_features cpu_features --clean
verifyLastOpSuccessed

trace cd "${AV1_MODULE_PATH}/jni" &&
  ensureRepoUpdateToDate https://chromium.googlesource.com/codecs/libgav1 libgav1 --clean
verifyLastOpSuccessed

trace cd "${AV1_MODULE_PATH}/jni/libgav1" &&
  ensureRepoUpdateToDate https://github.com/abseil/abseil-cpp.git third_party/abseil-cpp --clean
