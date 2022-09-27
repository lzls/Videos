#!/bin/bash

#
# Created on 2021-9-16 10:00:47 AM.
# Copyright © 2021 刘振林. All rights reserved.
#

if [ "$SHELLS_DIR" ]; then
  source "$SHELLS_DIR"/utils.sh
else
  source ./utils.sh
fi
verifyLastOpSuccessed

function __amend() {
  local amend=$1
  if [ $amend -eq $true ]; then
    echo '--amend'
  else
    echo ''
  fi
}

function __gitCommit() {
  local amendCommit=$1
  local commitMsg=$2
  local author=$3
  local authorEmail=$4
  local date=$5
  local committer=$6
  local committerEmail=$7
  local committerDate=$8

  GIT_COMMITTER_DATE=$committerDate GIT_COMMITTER_NAME=$committer GIT_COMMITTER_EMAIL=$committerEmail \
    git commit $(__amend "$amendCommit") --m="$commitMsg" --date="$date" --author="$author <$authorEmail>" --no-edit
}

function gitCommit() {
  # shellcheck disable=SC2155
  local _date=$(date +%s%z)
  local commitMsg=$1                                  # 提交说明
  local author=${2:-$(git config user.name)}          # 作者
  local authorEmail=${3:-$(git config user.email)}    # 作者邮箱
  local date=${4:-$_date}                             # 作者修订日期
  local committer=${5:-$(git config user.name)}       # 提交者
  local committerEmail=${6:-$(git config user.email)} # 提交者邮箱
  local committerDate=${7:-$_date}                    # 提交日期

  __gitCommit $false "$commitMsg" "$author" "$authorEmail" "$date" "$committer" "$committerEmail" "$committerDate"
}

function gitGetFormattedLog() {
  local formatArg=
  local head='HEAD'

  local formatArgPrefix='--format='
  local headPrefix='--head='
  local namedArgsCount=0
  for arg in "$@"; do
    case ${arg} in
    ${formatArgPrefix}*)
      formatArg=${arg:${#formatArgPrefix}}
      # shellcheck disable=SC2003
      namedArgsCount=$(expr $namedArgsCount + 1) ;;
    ${headPrefix}*)
      head=${arg:${#headPrefix}}
      # shellcheck disable=SC2003
      namedArgsCount=$(expr $namedArgsCount + 1) ;;
    esac
  done

  test "$formatArg"
  verifyLastOpSuccessed "formatArg must be initialized explicitly. Use $formatArgPrefix to define it."

  shift $namedArgsCount
  git log "$head" --pretty=format:"$formatArg" "$@"
}

function gitAmendCommit() {
  local commitMsgSubject
  local commitMsgBody
  local commitMsg
  local committerDate
  local date
  local author
  local authorEmail
  local committer
  local committerEmail

  local commitMsgPrefix='-m'
  local commitMsgPrefix2='--m='
  local committerDatePrefix='--commit-date='
  local datePrefix='--date='
  local authorPrefix='--author='
  local authorEmailPrefix='--author-email='
  local committerPrefix='--committer='
  local committerEmailPrefix='--committer-email='
  local lastArg=
  for arg in "$@"; do
    case ${arg} in
    ${commitMsgPrefix}*) commitMsg=${arg:${#commitMsgPrefix}} ;;
    ${commitMsgPrefix2}*) commitMsg=${arg:${#commitMsgPrefix2}} ;;
    ${committerDatePrefix}*) committerDate=${arg:${#committerDatePrefix}} ;;
    ${datePrefix}*) date=${arg:${#datePrefix}} ;;
    ${authorPrefix}*) author=${arg:${#authorPrefix}} ;;
    ${authorEmailPrefix}*) authorEmail=${arg:${#authorEmailPrefix}} ;;
    ${committerPrefix}*) committer=${arg:${#committerPrefix}} ;;
    ${committerEmailPrefix}*) committerEmail=${arg:${#committerEmailPrefix}} ;;
    -*) ;;
    *) if [ "$lastArg" = "$commitMsgPrefix" ]; then commitMsg=$arg; fi ;;
    esac
    lastArg=$arg
  done

  if [ ! "$commitMsg" ]; then
    commitMsgSubject=$(gitGetFormattedLog '--format=%s' -1)
    commitMsgBody=$(gitGetFormattedLog '--format=%b' -1)
    commitMsg=$commitMsgSubject
    if [ "$commitMsgBody" ]; then
      commitMsg="$commitMsg"$'\n'$'\n'"$commitMsgBody"                      # 提交说明
    fi
  fi
  committerDate=${committerDate:-$(gitGetFormattedLog '--format=%cd' -1)}   # 提交日期
  date=${date:-$(gitGetFormattedLog '--format=%ad' -1)}                     # 作者修订日期
  author=${author:-$(gitGetFormattedLog '--format=%an' -1)}                 # 作者
  authorEmail=${authorEmail:-$(gitGetFormattedLog '--format=%ae' -1)}       # 作者邮箱
  committer=${committer:-$(gitGetFormattedLog '--format=%cn' -1)}           # 提交者
  committerEmail=${committerEmail:-$(gitGetFormattedLog '--format=%ce' -1)} # 提交者邮箱

  __gitCommit $true "$commitMsg" "$author" "$authorEmail" "$date" "$committer" "$committerEmail" "$committerDate"
}

function printCodeLineCount() {
   git log "${1:-HEAD}" --pretty=tformat: --numstat | awk '{ add += $1; subs += $2; loc += $1 - $2 }
     END { printf "added lines: %s, removed lines: %s, total lines: %s\n", add, subs, loc }'
}
