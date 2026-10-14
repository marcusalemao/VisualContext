#!/bin/bash
# rokid-mirror — see, record and capture the Rokid Glasses screen on a Mac.
#
# For any project, not one app: watch the HUD live, record demo videos, and
# drop screenshots into a folder Claude can read while you test.
#
# Needs (once):  brew install scrcpy && brew install --cask android-platform-tools
# Optional:      brew install ffmpeg      (only for "compose")
#
# First time, in Rokid Nexus open the "Wireless ADB" plugin: it shows a line
# like "adb pair 192.168.0.23:41234 123456". Then here:
#   ./rokid-mirror.sh pair 192.168.0.23:41234 123456
#   ./rokid-mirror.sh connect 192.168.0.23:37000     (the connect port the plugin shows)
# After that:
#   ./rokid-mirror.sh view              live window
#   ./rokid-mirror.sh record [name]     live window + video file
#   ./rokid-mirror.sh snap [name]       one screenshot
#   ./rokid-mirror.sh watch [seconds]   a screenshot every N seconds (default 3), last 40 kept
#   ./rokid-mirror.sh compose scene.mp4 hud.mp4 [out.mp4]   HUD floating over a camera video
#   ./rokid-mirror.sh off               turn wireless debugging off on the glasses
#
# Files go to ~/RokidMirror (change with ROKID_MIRROR_DIR). Connect that folder
# to Claude and it can look at what the glasses show.

set -u

DIR="${ROKID_MIRROR_DIR:-$HOME/RokidMirror}"
STATE="$HOME/.rokid-mirror"
mkdir -p "$DIR/shots" "$DIR/videos" "$DIR/watch"

say() { printf '%s\n' "$*"; }
die() { printf 'rokid-mirror: %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "$1 is missing. $2"; }
stamp() { date +%Y-%m-%d_%H-%M-%S; }

device() {
  [ -f "$STATE" ] || die "no glasses yet: run 'pair' and 'connect' first (see the top of this file)"
  cat "$STATE"
}

ensure_connected() {
  local dev
  dev="$(device)"
  if ! adb devices | grep -q "^$dev[[:space:]]*device"; then
    adb connect "$dev" >/dev/null 2>&1
    adb devices | grep -q "^$dev[[:space:]]*device" \
      || die "glasses not reachable at $dev. Same Wi-Fi? The connect port changes after a restart: open Wireless ADB in Nexus and run 'connect' again."
  fi
  printf '%s' "$dev"
}

cmd="${1:-help}"
shift || true

case "$cmd" in
  pair)
    need adb "brew install --cask android-platform-tools"
    [ $# -eq 2 ] || die "usage: pair IP:PAIR_PORT CODE"
    adb pair "$1" "$2"
    ;;

  connect)
    need adb "brew install --cask android-platform-tools"
    [ $# -eq 1 ] || die "usage: connect IP:PORT"
    adb connect "$1" | tee /dev/stderr | grep -q "connected to" || die "could not connect to $1"
    printf '%s' "$1" > "$STATE"
    say "ok: glasses at $1 remembered"
    ;;

  view)
    need scrcpy "brew install scrcpy"
    dev="$(ensure_connected)"
    scrcpy -s "$dev" --no-audio --window-title "Rokid HUD" --stay-awake
    ;;

  record)
    need scrcpy "brew install scrcpy"
    dev="$(ensure_connected)"
    file="$DIR/videos/${1:-hud}_$(stamp).mp4"
    say "recording to $file — close the window (or Ctrl+C) to stop"
    scrcpy -s "$dev" --no-audio --window-title "Rokid HUD (recording)" --stay-awake --record "$file"
    say "saved: $file"
    ;;

  snap)
    dev="$(ensure_connected)"
    file="$DIR/shots/${1:-hud}_$(stamp).png"
    adb -s "$dev" exec-out screencap -p > "$file" && say "saved: $file"
    ;;

  watch)
    dev="$(ensure_connected)"
    every="${1:-3}"
    say "a screenshot every ${every}s in $DIR/watch (latest.png is always the newest). Ctrl+C to stop."
    while true; do
      tmp="$DIR/watch/.next.png"
      if adb -s "$dev" exec-out screencap -p > "$tmp" 2>/dev/null && [ -s "$tmp" ]; then
        cp "$tmp" "$DIR/watch/$(stamp).png"
        mv "$tmp" "$DIR/watch/latest.png"
        # keep the last 40 timestamped shots
        ls -1t "$DIR"/watch/20*.png 2>/dev/null | tail -n +41 | while read -r old; do rm -f "$old"; done
      fi
      sleep "$every"
    done
    ;;

  compose)
    need ffmpeg "brew install ffmpeg"
    [ $# -ge 2 ] || die "usage: compose scene.mp4 hud.mp4 [out.mp4]"
    out="${3:-$DIR/videos/composed_$(stamp).mp4}"
    # The lens shows black as nothing: key the black out and float the HUD
    # in the upper right, about a third of the frame wide.
    need ffprobe "brew install ffmpeg"
    scene_w="$(ffprobe -v error -select_streams v:0 -show_entries stream=width -of csv=p=0 "$1")"
    hud_w=$(( (scene_w / 3) / 2 * 2 ))
    ffmpeg -y -i "$1" -i "$2" -filter_complex \
      "[1:v]scale=${hud_w}:-2,format=rgba,colorkey=0x000000:0.18:0.08[lens];[0:v][lens]overlay=W-w-48:48:shortest=1" \
      -c:v libx264 -crf 20 -pix_fmt yuv420p -c:a copy "$out" || die "ffmpeg failed"
    say "saved: $out"
    ;;

  off)
    dev="$(device)"
    adb disconnect "$dev" >/dev/null 2>&1
    say "disconnected. To close the door on the glasses too, turn Wireless debugging off in the Nexus Wireless ADB plugin."
    ;;

  *)
    sed -n '2,24p' "$0" | sed 's/^# \{0,1\}//'
    ;;
esac
