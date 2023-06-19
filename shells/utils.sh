#!/bin/bash

#
# Created on 2021-11-23 4:42 PM.
# Copyright © 2021 刘振林. All rights reserved.
#

shopt -s expand_aliases 2>/dev/null # Enable alias
alias fcp='cp -f'
alias int='declare -i'
alias const_int='int -r'
alias bool=int
alias const_bool=const_int

if ! [ "$false" ]; then
  const_bool false=1
fi
if ! [ "$true" ]; then
  const_bool true=0
fi

if ! [ "$isDarwin" ]; then
  [[ "$(uname)" == Darwin* ]]
  const_bool isDarwin=$?
fi

if ! [ "$SHELL_TYPE" ]; then
  readonly SHELL_TYPE_SH='sh'
  readonly SHELL_TYPE_BASH='bash'
  readonly SHELL_TYPE_ZSH='zsh'
  readonly SHELL_TYPE=$(
    __shell_regex___='.*(\/|[[:blank:]]|-)([[:alpha:]]*sh)([[:blank:]].*)?'
    # shellcheck disable=SC2116
    ps -ef | grep "$(echo $$)" | grep -E "$__shell_regex___" \
        | sed -r "s/$__shell_regex___/\2/" | head -n 1
  )
  echo "shell type: $SHELL_TYPE"
fi

# shellcheck disable=SC2139
alias echo_e="$(
  if [ $isDarwin -eq $true ] && [ "$SHELL_TYPE" = "$SHELL_TYPE_SH" ]; then
    echo 'echo'
  else
    echo 'echo -e'
  fi
)"

function echoStyledText() {
  local text
  local textColor
  local bold=0
  local textPrefix='--text='
  local textColorPrefix='--text-color='
  local boldPrefix='--bold='
  for arg in "$@"; do
    case ${arg} in
    ${textPrefix}*) text=${arg:${#textPrefix}} ;;
    ${textColorPrefix}*) textColor=${arg:${#textColorPrefix}} ;;
    ${boldPrefix}*) if [ "${arg:${#boldPrefix}}" -eq $true ]; then bold=1; fi ;;
    esac
  done
  echo_e "\033[$bold;${textColor}m${text}\033[0m"
}

function echoGreenText() {
  local text="$*"
  echoStyledText --text="$text" --text-color=32 --bold=$false
}

function echoBoldBlueText() {
  local text="$*"
  echoStyledText --text="$text" --text-color=34 --bold=$true
}

function echoBoldRedText() {
  local text="$*"
  echoStyledText --text="$text" --text-color=31 --bold=$true
}

# shellcheck disable=SC2162
function read_p() {
  printf '%s' "$1"
  shift 1
  read "$@"
}

if [ ! $__can_pause___ ]; then
  bool __can_pause___=$true
fi
function pause() {
  if [ $__can_pause___ -ne $false ]; then
    read_p "${1:-Press any key to continue...}"
  fi
}

function waitToExit() {
  local canPause=$__can_pause___
  __can_pause___=$true
  pause 'Press any key to exit...'
  __can_pause___=$canPause
  exit $1
}

function cdOrExit() {
  cd "$1" || exit $?
}

function verifyLastOpSuccessed() {
  local exitCode=$?
  local errMsg=
  local pauseOnly=$false

  local pauseOnlyPrefix='--pause-only'
  local exitCodePrefix='--exit-code='
  for arg in "$@"; do
    case ${arg} in
    $pauseOnlyPrefix)
      pauseOnly=$true
      continue ;;
    ${exitCodePrefix}*)
      exitCode=${arg:${#exitCodePrefix}}
      continue ;;
    esac
    if [ "$errMsg" ]; then
      errMsg="$errMsg $arg"
    else
      errMsg=$arg
    fi
  done

  if [ $exitCode -ne 0 ]; then
    if [ "$errMsg" ]; then
      echoBoldRedText "$errMsg" >&2
    else
      echoBoldRedText 'Something might go wrong. Thread is suspended now.' \
        "Expected exit code 0, but got $exitCode." >&2
    fi
    if [ $pauseOnly -eq $true ]; then
      pause 'Press any key to continue...'
    else
      pause 'Press any key to exit...'
      exit $exitCode
    fi
  fi
}

function trace() {
  local cmd=
  for arg in "$@"; do
    if [ "$cmd" ]; then
      if echo "$arg" | grep ' ' >/dev/null; then
        cmd="$cmd \"$arg\""
      else
        cmd="$cmd $arg"
      fi
    else
      cmd=$arg
    fi
  done
  echoGreenText ">>>>  $cmd"

  "$@"
}

function parsePath() {
  local path=$1
  local _pwd=
  if [[ "$2" == --pwd=* ]]; then
    _pwd=${2:6}
  else
    _pwd=${2:-$(pwd)}
  fi
  case $path in
    /*) echo "$path";;
    \~/*) echo "$(echo ~)/${path:2}";;
    ./*|../*) echo "$_pwd/$path";;
  esac
}
