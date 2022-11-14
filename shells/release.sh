#!/bin/bash

#
# Created on 2021-8-19 8:51:17 PM.
# Copyright © 2021 刘振林. All rights reserved.
#

readonly SHELLS_DIR=$(cd "$(dirname "$0")"; pwd)
source "$SHELLS_DIR"/utils.sh &&
  source "$SHELLS_DIR"/git.sh
# bail out if this is not executed on a subprocess
declare -i exitCode=$?
if [ $exitCode -ne 0 ]; then
  return $exitCode
fi

const_int START_TIME=$(date +%s)
echo "start time: $(date -r $START_TIME '+%a %Y-%m-%d %H:%M:%S %z')"

PROJECTS_ROOT='/Users/liuzhenlin/Android/projects'
APK_PARTS_COUNT=
APP_VERSION_NAME=
bool BETA=$false
bool AMEND=$false
bool UPDATE_DATES=$false

function __echo_release_sh_usage___() {
  if [ "$1" ]; then
    echo "illegal option: $1" >&2
  fi
  printf "%s\n\t%s\n\t%s\n" 'usage: ./shells/release.sh --version-name=<app version name>' \
    '--parts-count=<count of split apk parts> [--beta] [--amend] [--update-dates]' \
    '[--projects-root=<parent dir of the local Videos repo>]'
}

function __parseShellArgs() {
  local projectsRootPrefix='--projects-root='
  local versionNamePrefix='--version-name='
  local partsCountPrefix='--parts-count='
  local beta='--beta'
  local amend='--amend'
  local updateDates='--update-dates'
  for arg in "$@"; do
    case $arg in
    ${projectsRootPrefix}*) PROJECTS_ROOT=$(parsePath "${arg:${#projectsRootPrefix}}") ;;
    ${versionNamePrefix}*) APP_VERSION_NAME=${arg:${#versionNamePrefix}} ;;
    ${partsCountPrefix}*) APK_PARTS_COUNT=${arg:${#partsCountPrefix}} ;;
    $beta) BETA=$true ;;
    $amend) AMEND=$true ;;
    $updateDates) UPDATE_DATES=$true ;;
    --help) __echo_release_sh_usage___; exit 0 ;;
    *) __echo_release_sh_usage___ "$arg"; exit 1 ;;
    esac
  done

  test "$APP_VERSION_NAME"
  verifyLastOpSuccessed "Release version name is not initialized. Use $versionNamePrefix to specify it."
  test "$APK_PARTS_COUNT"
  verifyLastOpSuccessed "The count of apk parts to upload is not initialized. Use $partsCountPrefix" \
    'to specify it.'
}
__parseShellArgs "$@"

readonly COMMIT_MSG="Release version $APP_VERSION_NAME"

readonly SERVICE_PROJECTS_ROOT="$PROJECTS_ROOT/tmp"
readonly APK_SOURCE_DIR="$PROJECTS_ROOT/Videos/app/release"

readonly SSH_APKS_HOLDER_VIDEOS_REPO='git@ApksHolder.github.com:ApksHolder/Videos.git'
readonly SSH_LZLS_VIDEOS_REPO='git@lzls.github.com:lzls/Videos.git'
readonly SSH_LZLS_VIDEOS_SERVER_REPO='git@lzls.gitlab.com:lzls/Videos-Server.git'

if [ ! -e "$SERVICE_PROJECTS_ROOT" ]; then
  # shellcheck disable=SC2003
  cd "$PROJECTS_ROOT" && mkdir "${SERVICE_PROJECTS_ROOT:$(expr ${#PROJECTS_ROOT} + 1)}"
  verifyLastOpSuccessed
fi

function clonePushBranch() {
  local remote=$1
  local branch=$2
  local storedDir=$3

  trace git clone -b "$branch" "$remote" "$storedDir" &&
    trace cd "$storedDir"
}

function stashAndRebasePushBranch() {
  local remote=$1
  local branch=$2
  local storedDir=$3

  trace cd "$storedDir" &&
    trace git stash &&
    trace git checkout "$branch" &&
    trace git reset --hard HEAD~0 &&
    trace git fetch &&
    trace git rebase "$(git remote)/$branch"
}

function ensureRepoPushBranchUpToDate() {
  local remote=$1
  local branch=$2
  local storedDir=$3

  if [ ! -d "$storedDir" ] || [ "$(ls -A "$storedDir")" = '' ]; then
    clonePushBranch "$remote" "$branch" "$storedDir"
  else
    stashAndRebasePushBranch "$remote" "$branch" "$storedDir"
  fi
}

function __force() {
  if [ $AMEND -eq $true ]; then
    echo '--force'
  else
    echo ''
  fi
}

function commitAndPush() {
  local commitMsg=${1:-$COMMIT_MSG}
  local remote=${2:-origin}
  shift 2
  local branches=${*:-master}

  if [ $AMEND -eq $true ]; then
    # shellcheck disable=SC2046
    trace gitAmendCommit --m="$commitMsg" $(
        if [ $UPDATE_DATES -eq $true ]; then
          # shellcheck disable=SC2155
          local _date=$(date +%s%z)
          echo "--date=$_date --commit-date=$_date"
        fi)
  else
    trace gitCommit "$commitMsg"
  fi
  verifyLastOpSuccessed --pause-only
  # shellcheck disable=SC2046,SC2116
  trace git push "$remote" $(echo "$branches") $(__force)
}


echoBoldBlueText '\nVerifying the apk parts are split from the current app-release.apk'
int i=1
# shellcheck disable=SC2004
while (($i <= $APK_PARTS_COUNT)); do
  if [ $i -eq 1 ]; then
      cat "$APK_SOURCE_DIR"/app-release$i.apk > "$APK_SOURCE_DIR"/_app-release.apk
  else
      cat "$APK_SOURCE_DIR"/app-release$i.apk >> "$APK_SOURCE_DIR"/_app-release.apk
  fi
  ((i = $i + 1))
done
test "$(shasum "$APK_SOURCE_DIR"/app-release.apk | awk '{ print $1 }')" = \
     "$(shasum "$APK_SOURCE_DIR"/_app-release.apk | awk '{ print $1 }')"
int exitCode=$?
rm -f "$APK_SOURCE_DIR"/_app-release.apk
if [ $exitCode -ne 0 ]; then
  verifyLastOpSuccessed --exit-code=$exitCode
fi


echoBoldBlueText "\nVerifying tag v$APP_VERSION_NAME exists in the commit tree for the dev branch of" \
  "$PROJECTS_ROOT/Videos"

cdOrExit "$PROJECTS_ROOT"/Videos

test "$(git branch --show-current)" = 'dev'
verifyLastOpSuccessed 'Current branch is not dev'

readonly RELEASE_TAG_COMMIT_ID=$(git rev-parse "v$APP_VERSION_NAME")
echo "$RELEASE_TAG_COMMIT_ID" | grep -E '^[[:xdigit:]]{40}$' >/dev/null &&
  git log --pretty=reference | grep "${RELEASE_TAG_COMMIT_ID:0:7}"
verifyLastOpSuccessed "git tag v$APP_VERSION_NAME does not exist in the commit tree"


echoBoldBlueText "\nEnsuring $PROJECTS_ROOT/Videos is up-to-date on its beta branch..."
ensureRepoPushBranchUpToDate $SSH_LZLS_VIDEOS_REPO beta "$PROJECTS_ROOT"/Videos &&
  if [ $AMEND -eq $true ] \
      && git log --pretty=oneline \
          | grep "Merge tag \('\|\"\)v$APP_VERSION_NAME\('\|\"\)" >/dev/null; then
    trace git reset --hard HEAD~1
  fi &&
  trace git merge "v$APP_VERSION_NAME" --no-ff --no-edit &&
  if [ $AMEND -eq $true ] && [ $UPDATE_DATES -ne $true ]; then
    _last_commit=$(git remote)/beta
    trace gitAmendCommit --date="$(gitGetFormattedLog --format='%ad' --head="$_last_commit" -1)" \
                         --commit-date="$(gitGetFormattedLog --format='%cd' --head="$_last_commit" -1)"
  fi &&
  if [ $BETA -eq $true ]; then trace git checkout dev; fi
verifyLastOpSuccessed

if [ $BETA -ne $true ]; then
  echoBoldBlueText "\nEnsuring $PROJECTS_ROOT/Videos is up-to-date on its release branch..."
  ensureRepoPushBranchUpToDate $SSH_LZLS_VIDEOS_REPO release "$PROJECTS_ROOT"/Videos &&
    if [ $AMEND -eq $true ] \
        && git log --pretty=oneline \
            | grep "Merge tag \('\|\"\)v$APP_VERSION_NAME\('\|\"\)" >/dev/null; then
      trace git reset --hard HEAD~1
    fi &&
    trace git merge "v$APP_VERSION_NAME" --no-ff --no-edit &&
    if [ $AMEND -eq $true ] && [ $UPDATE_DATES -ne $true ]; then
      _last_commit=$(git remote)/release
      trace gitAmendCommit --date="$(gitGetFormattedLog --format='%ad' --head="$_last_commit" -1)" \
          --commit-date="$(gitGetFormattedLog --format='%cd' --head="$_last_commit" -1)"
    fi &&
    trace git checkout dev
  verifyLastOpSuccessed
fi

echoBoldBlueText "\nEnsuring $SERVICE_PROJECTS_ROOT/Videos-master is up-to-date on its push branch..."
ensureRepoPushBranchUpToDate $SSH_LZLS_VIDEOS_REPO master "$SERVICE_PROJECTS_ROOT"/Videos-master
verifyLastOpSuccessed

echoBoldBlueText "\nEnsuring $SERVICE_PROJECTS_ROOT/Videos is up-to-date on its push branch..."
ensureRepoPushBranchUpToDate $SSH_APKS_HOLDER_VIDEOS_REPO master "$SERVICE_PROJECTS_ROOT"/Videos
verifyLastOpSuccessed

echoBoldBlueText "\nEnsuring $SERVICE_PROJECTS_ROOT/Videos-Server is up-to-date on its push branch..."
ensureRepoPushBranchUpToDate $SSH_LZLS_VIDEOS_SERVER_REPO master "$SERVICE_PROJECTS_ROOT"/Videos-Server
verifyLastOpSuccessed


echoBoldBlueText '\nPushing data of app upgrade to https://github.com/ApksHolder/Videos.git...'
cd "$SERVICE_PROJECTS_ROOT"/Videos &&
  if [ $BETA -ne $true ]; then fcp "$APK_SOURCE_DIR"/app-release.apk ./; fi &&
  fcp "$PROJECTS_ROOT"/Videos/app.json ./ &&
  git add ./app-release.apk ./app.json &&
  commitAndPush "$COMMIT_MSG" $SSH_APKS_HOLDER_VIDEOS_REPO master master:release
verifyLastOpSuccessed --pause-only

_branches=
if [ $BETA -eq $true ]; then
  _branches='dev and beta'
else
  _branches='dev, beta and release'
fi
echoBoldBlueText "\nPushing the new release tag and the latest code to $_branches branches of" \
  'https://github.com/lzls/Videos.git...'
# shellcheck disable=SC2046
cd "$PROJECTS_ROOT"/Videos &&
  trace git push $SSH_LZLS_VIDEOS_REPO tags/"v$APP_VERSION_NAME" \
      dev:dev beta:beta $(if [ $BETA -ne $true ]; then echo release:release; else echo ''; fi) \
      $(__force)
verifyLastOpSuccessed --pause-only

echoBoldBlueText '\nPushing information of app upgrade to the master branch of' \
  'https://github.com/lzls/Videos.git...'
cd "$SERVICE_PROJECTS_ROOT"/Videos-master &&
  fcp "$PROJECTS_ROOT"/Videos/app.json ./ &&
  git add ./app.json &&
  commitAndPush "$COMMIT_MSG" $SSH_LZLS_VIDEOS_REPO master
verifyLastOpSuccessed --pause-only

echoBoldBlueText '\nPushing data of app upgrade to https://gitlab.com/lzls/Videos-Server.git...' \
  'This will be automatically mirrored to https://gitee.com/lzl_s/Videos-Server.git and' \
  'https://gitee.com/lzl_s/Videos-Service.git by the GitLab server.'

cdOrExit "$SERVICE_PROJECTS_ROOT"/Videos-Server/app/Android

if [ $BETA -ne $true ]; then
  i=1
  # shellcheck disable=SC2004
  while (($i <= $APK_PARTS_COUNT)); do
    fcp "$APK_SOURCE_DIR"/app-release$i.apk ./ &&
      git add ./app-release$i.apk
    verifyLastOpSuccessed --pause-only
    ((i = $i + 1))
  done
fi

fcp "$PROJECTS_ROOT"/Videos/app.json ./ &&
  git add ./app.json &&
  fcp "$APK_SOURCE_DIR"/app-release.apk ./Videos_v"$APP_VERSION_NAME".apk &&
  git add ./Videos_v"$APP_VERSION_NAME".apk &&
  commitAndPush "$COMMIT_MSG" $SSH_LZLS_VIDEOS_SERVER_REPO master


# shellcheck disable=SC2046,SC2003
echo_e "\ncost time: $(date -r $(expr $(date +%s) - $START_TIME) +%M:%S)"
