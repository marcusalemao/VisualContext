#!/bin/bash
# rokid-mirror — see, record and capture the Rokid Glasses screen on a Mac.
#
# For any project, not one app: watch the HUD live, record demo videos, and
# drop screenshots into a folder Claude can read while you test.
#
# Needs (once):  brew install scrcpy && brew install --cask android-platform-tools
# Optional:      brew install ffmpeg      (for "lens", "demo" and "compose")
#
# First time, in Rokid Nexus open the "Wireless ADB" plugin: it shows a line
# like "adb pair 192.168.0.23:41234 123456". Then here:
#   ./rokid-mirror.sh pair 192.168.0.23:41234 123456
#   ./rokid-mirror.sh connect 192.168.0.23:37000     (the connect port the plugin shows)
# After that:
#   ./rokid-mirror.sh espelho           the easy one: finds the glasses, opens the lens window and
#                                       keeps screenshots coming for Claude. Also leaves an
#                                       "Espelhar óculos.command" here to double-click next time.
#   ./rokid-mirror.sh view              live window (the HUD as apps draw it, in color)
#   ./rokid-mirror.sh lens              live window as the lens shows it: green, one color
#   ./rokid-mirror.sh camera            live window of the glasses camera (what you look at)
#   ./rokid-mirror.sh record [name]     live window + video file
#   ./rokid-mirror.sh demo [name]       records HUD + camera together; Ctrl+C ends and builds
#                                       the "through the lens" video (HUD light over the world)
#   ./rokid-mirror.sh snap [name]       one screenshot
#   ./rokid-mirror.sh watch [seconds]   a screenshot every N seconds (default 3), last 40 kept
#   ./rokid-mirror.sh compose scene.mp4 hud.mp4 [out.mp4]   same look, from two videos you have
#   ./rokid-mirror.sh off               disconnect
#
# Files go into the folder this script is in (change with ROKID_MIRROR_DIR).
# Connect that folder to Claude and it can look at what the glasses show.

set -u

# Files land next to this script (e.g. Documents/Rokid Mirror), unless ROKID_MIRROR_DIR says otherwise.
DIR="${ROKID_MIRROR_DIR:-$(cd "$(dirname "$0")" && pwd)}"
STATE="$HOME/.rokid-mirror"
mkdir -p "$DIR/shots" "$DIR/videos" "$DIR/watch"

say() { printf '%s\n' "$*"; }
die() { printf 'rokid-mirror: %s\n' "$*" >&2; exit 1; }
need() { command -v "$1" >/dev/null 2>&1 || die "$1 is missing. $2"; }
stamp() { date +%Y-%m-%d_%H-%M-%S; }

# The waveguide shows one color: light where the app draws light, nothing where
# it draws black. Brightness becomes Rokid green (#4DFF8C), black stays black.
GREEN="format=gray,format=rgb24,lutrgb=r='val*0.30':g='val':b='val*0.55'"

# HUD light added over a camera video, the way a see-through lens adds it:
# green, centered, about 60% of the frame high. black adds nothing.
through_lens() { # scene.mp4 hud.mp4 out.mp4
  need ffmpeg "brew install ffmpeg"
  need ffprobe "brew install ffmpeg"
  local size sw sh hh
  size="$(ffprobe -v error -select_streams v:0 -show_entries stream=width,height -of csv=p=0:s=x "$1")"
  sw="${size%x*}"; sh="${size#*x}"
  sw=$(( sw / 2 * 2 )); sh=$(( sh / 2 * 2 ))
  hh=$(( sh * 6 / 10 / 2 * 2 ))
  ffmpeg -y -loglevel error -i "$1" -i "$2" -filter_complex \
    "[0:v]scale=${sw}:${sh},format=gbrp[world];[1:v]${GREEN},scale=-2:${hh},pad=${sw}:${sh}:(ow-iw)/2:(oh-ih)/2:black,format=gbrp[hud];[world][hud]blend=all_mode=addition:shortest=1,format=yuv420p" \
    -c:v libx264 -crf 20 -movflags +faststart -an "$3" || die "ffmpeg failed"
}

device() {
  if [ -f "$STATE" ]; then cat "$STATE"; return; fi
  # Never connected by hand: take any glasses adb already sees.
  local found
  found="$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device" {print $1; exit}')"
  [ -n "$found" ] || die "no glasses yet: run 'pair' and 'connect' first (see the top of this file)"
  printf '%s' "$found"
}

ensure_connected() {
  local dev online
  dev="$(device)" || exit 1
  online() { adb devices | awk -v d="$1" '$1==d && $2=="device" {found=1} END {exit !found}'; }
  if ! online "$dev"; then
    adb connect "$dev" >/dev/null 2>&1
  fi
  if ! online "$dev"; then
    # Paired once = no numbers ever again: the glasses announce their current
    # address on the Wi-Fi (mDNS); connect to whatever they announce now.
    local addr
    for addr in $(adb mdns services 2>/dev/null | awk '/_adb-tls-connect/ {print $NF}'); do
      adb connect "$addr" >/dev/null 2>&1 && online "$addr" && { printf '%s' "$addr" > "$STATE"; printf '%s' "$addr"; return; }
    done
  fi
  if ! online "$dev"; then
    # The port changes when the glasses restart, but once paired, adb also finds
    # them by name on the Wi-Fi (adb-…_adb-tls-connect). Use whatever is online.
    dev="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
    [ -n "$dev" ] || die "glasses not reachable. Same Wi-Fi? Open Wireless ADB in Nexus and run 'connect IP:PORT' with the port it shows."
    printf '%s' "$dev" > "$STATE"
  fi
  printf '%s' "$dev"
}

cmd="${1:-help}"
shift || true

case "$cmd" in
  espelho|mirror)
    need adb "brew install --cask android-platform-tools"
    need ffplay "brew install ffmpeg"
    # Next time: a double-click in Finder instead of typing.
    launcher="$DIR/Espelhar óculos.command"
    if [ ! -x "$launcher" ]; then
      printf '#!/bin/bash\nexec bash "%s" espelho\n' "$DIR/rokid-mirror.sh" > "$launcher"
      chmod +x "$launcher"
    fi
    adb start-server >/dev/null 2>&1
    sleep 2 # adb finds paired glasses on the Wi-Fi by itself; give it a moment
    if ! dev="$(ensure_connected 2>/dev/null)"; then
      say ""
      say "Não achei os óculos."
      say "  1. Óculos e Mac no mesmo Wi-Fi."
      say "  2. Nos óculos, abra o Rokid Nexus > plugin Wireless ADB (deixe ligado)."
      say "  3. Rode de novo (ou clique duas vezes em 'Espelhar óculos')."
      exit 1
    fi
    say "óculos encontrados ($dev)"
    say "abrindo a janela da lente; capturas em $DIR/watch para o Claude ver."
    say "para parar: clique nesta janela do Terminal e aperte Control + C."
    bash "$0" watch 3 >/dev/null 2>&1 &
    watcher=$!
    trap 'kill $watcher 2>/dev/null; exit 0' INT TERM EXIT
    bash "$0" lens
    ;;

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
    dev="$(ensure_connected)" || exit 1
    scrcpy -s "$dev" --no-audio --window-title "Rokid HUD" --stay-awake
    ;;

  lens)
    need ffplay "brew install ffmpeg"
    dev="$(ensure_connected)" || exit 1
    say "lens view — Ctrl+C to stop"
    trap 'exit 0' INT
    # Android's own screen recorder streams the HUD; ffplay shows it tinted.
    # It restarts every 3 minutes (the recorder's limit), so expect a blink.
    while true; do
      adb -s "$dev" exec-out screenrecord --output-format=h264 --time-limit=180 - 2>/dev/null \
        | ffplay -loglevel warning -f h264 -framerate 30 -window_title "Rokid HUD (lente)" -alwaysontop \
            -fflags nobuffer -flags low_delay -framedrop -vf "$GREEN" -
    done
    ;;

  camera)
    need scrcpy "brew install scrcpy"
    dev="$(ensure_connected)" || exit 1
    scrcpy -s "$dev" --video-source=camera --camera-facing=back --no-audio --window-title "Rokid camera" \
      || die "the glasses camera didn't open. Close apps using it, or see './rokid-mirror.sh cameras'."
    ;;

  cameras)
    need scrcpy "brew install scrcpy"
    dev="$(ensure_connected)" || exit 1
    scrcpy -s "$dev" --list-cameras
    ;;

  demo)
    need scrcpy "brew install scrcpy"
    dev="$(ensure_connected)" || exit 1
    name="${1:-demo}_$(stamp)"
    hud="$DIR/videos/${name}_hud.mp4"
    cam="$DIR/videos/${name}_camera.mp4"
    out="$DIR/videos/${name}_lente.mp4"
    say "recording HUD and camera — use the glasses, then Ctrl+C here to finish"
    scrcpy -s "$dev" --no-audio --no-window --stay-awake --record "$hud" >/dev/null 2>&1 &
    p1=$!
    scrcpy -s "$dev" --video-source=camera --camera-facing=back --no-audio --no-window --record "$cam" >/dev/null 2>&1 &
    p2=$!
    trap 'kill -INT $p1 $p2 2>/dev/null' INT
    wait $p1 $p2 2>/dev/null; wait $p1 $p2 2>/dev/null
    trap - INT
    [ -s "$hud" ] || die "no HUD video was recorded"
    if [ -s "$cam" ]; then
      through_lens "$cam" "$hud" "$out"
      say "saved: $out (and the two raw videos next to it)"
    else
      say "the camera didn't record (busy or blocked); HUD only: $hud"
    fi
    ;;

  record)
    need scrcpy "brew install scrcpy"
    dev="$(ensure_connected)" || exit 1
    file="$DIR/videos/${1:-hud}_$(stamp).mp4"
    say "recording to $file — close the window (or Ctrl+C) to stop"
    scrcpy -s "$dev" --no-audio --window-title "Rokid HUD (recording)" --stay-awake --record "$file"
    say "saved: $file"
    ;;

  snap)
    dev="$(ensure_connected)" || exit 1
    file="$DIR/shots/${1:-hud}_$(stamp).png"
    adb -s "$dev" exec-out screencap -p > "$file" && say "saved: $file"
    ;;

  watch)
    dev="$(ensure_connected)" || exit 1
    every="${1:-3}"
    say "a screenshot every ${every}s in $DIR/watch (latest.png is always the newest). Ctrl+C to stop."
    trap 'exit 0' INT
    misses=0
    while true; do
      tmp="$DIR/watch/.next.png"
      # Keep only whole pictures: a dropped link leaves an empty or cut file.
      if adb -s "$dev" exec-out screencap -p > "$tmp" 2>/dev/null && [ "$(wc -c < "$tmp")" -gt 2000 ]; then
        cp "$tmp" "$DIR/watch/$(stamp).png"
        mv "$tmp" "$DIR/watch/latest.png"
        misses=0
        # keep the last 40 timestamped shots
        ls -1t "$DIR"/watch/20*.png 2>/dev/null | tail -n +41 | while read -r old; do rm -f "$old"; done
      else
        rm -f "$tmp"
        misses=$((misses + 1))
        # The glasses moved to a new port (restart, Wi-Fi blip): find them again.
        if [ "$misses" -ge 3 ]; then
          say "lost the glasses, looking for them again…"
          found="$(ensure_connected 2>/dev/null)" && [ -n "$found" ] && dev="$found" && misses=0
        fi
      fi
      sleep "$every"
    done
    ;;

  compose)
    need ffmpeg "brew install ffmpeg"
    [ $# -ge 2 ] || die "usage: compose scene.mp4 hud.mp4 [out.mp4]"
    out="${3:-$DIR/videos/composed_$(stamp).mp4}"
    through_lens "$1" "$2" "$out"
    say "saved: $out"
    ;;

  off)
    dev="$(device)"
    adb disconnect "$dev" >/dev/null 2>&1
    say "disconnected. To close the door on the glasses too, turn Wireless debugging off in the Nexus Wireless ADB plugin."
    ;;

  *)
    sed -n '2,29p' "$0" | sed 's/^# \{0,1\}//'
    ;;
esac
