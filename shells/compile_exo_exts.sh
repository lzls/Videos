#!/bin/bash

#sh ./shells/compile_exo_exts.sh --exoplayer-root=~/Android/projects/AndroidxMedia3 \
#  --ndk-path=~/Android/SDK/ndk/android-ndk-r23c --host-platform=darwin-x86_64

SHELLS_DIR=$(cd "$(dirname "$0")"; pwd)
source "$SHELLS_DIR"/utils.sh
# bail out if this is not executed on a subprocess
declare -i exitCode=$?
if [ $exitCode -ne 0 ]; then
  return $exitCode
fi

int START_TIME="$(date +%s)"
echo_e "start time: $(date -r $START_TIME '+%a %Y-%m-%d %H:%M:%S %z')\n"

function __echo_release_sh_usage___() {
  if [ "$1" ]; then
    echo "illegal option: $1" >&2
  fi
  printf "%s\n\t%s\n\t%s\n" 'usage: ./shells/compile_exo_exts.sh' \
    '--exoplayer-root=<path to ExoPlayer project checkout>' \
    '--ndk-path=<path to Android NDK> --host-platform=[linux-x86_64|darwin-x86_64]'
}

function __parseShellArgs() {
  local exoPlayerRootPrefix='--exoplayer-root='
  local ndkPathPrefix='--ndk-path='
  local hostPlatformPrefix='--host-platform='
  for arg in "$@"; do
    case $arg in
    ${exoPlayerRootPrefix}*) EXO_PLAYER_ROOT=$(parsePath "${arg:${#exoPlayerRootPrefix}}") ;;
    ${ndkPathPrefix}*) NDK_PATH=$(parsePath "${arg:${#ndkPathPrefix}}") ;;
    ${hostPlatformPrefix}*) HOST_PLATFORM=${arg:${#hostPlatformPrefix}} ;;
    --help) __echo_release_sh_usage___; exit 0 ;;
    *) __echo_release_sh_usage___ "$arg"; exit 1 ;;
    esac
  done

  if [ ! "$EXO_PLAYER_ROOT" ] || [ ! "$NDK_PATH" ] || [ ! "$HOST_PLATFORM" ]; then
    __echo_release_sh_usage___; exit 1
  fi
}
__parseShellArgs "$@"

function ensureRepoUpdateToDate() {
  local repo=$1
  local dir=$2
  local branch=
  local clean=$false
  shift 2
  for arg in "$@"; do
    case $arg in
    --clean) clean=$true ;;
    *) branch=$arg ;;
    esac
  done
  if [ ! -d "$dir" ] || [ "$(ls -A "$dir")" = '' ]; then
    if [ "$branch" ]; then
      trace git clone -b "$branch" "$repo" "$dir"
    else
      trace git clone "$repo" "$dir"
    fi
  else
    trace cd "$dir" &&
      trace git reset --hard &&
      if [ $clean -eq $true ]; then trace git clean -qxdf; fi &&
      if [ "$branch" ]; then trace git checkout "$branch"; fi &&
      trace git pull "$(git remote)" "$branch" --rebase
  fi
}

trace ensureRepoUpdateToDate https://github.com/androidx/media.git "$EXO_PLAYER_ROOT" release
verifyLastOpSuccessed; echo

trace . "$SHELLS_DIR/compile_exo_ext_av1.sh"; verifyLastOpSuccessed --pause-only; echo
trace . "$SHELLS_DIR/compile_exo_ext_ffmpeg.sh"; verifyLastOpSuccessed --pause-only; echo
trace . "$SHELLS_DIR/compile_exo_ext_vp9.sh"; verifyLastOpSuccessed --pause-only; echo

#trace cd "$EXO_PLAYER_ROOT" &&
#  trace ./gradlew clean &&
#  trace ./gradlew assembleRelease

# shellcheck disable=SC2046,SC2003
echo_e "cost time: $(date -r $(expr $(date +%s) - $START_TIME) +%M:%S)"
