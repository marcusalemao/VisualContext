#!/bin/bash
# Glasscast — see, record and capture the Rokid Glasses screen on a Mac.
# Independent tool by alema.io; not affiliated with Rokid, and not the "Rokid Mirror" apps.
#
# For any project, not one app: watch the HUD live, record demo videos, and
# drop screenshots into a folder Claude can read while you test.
#
# Needs (once):  brew install scrcpy && brew install --cask android-platform-tools
# Optional:      brew install ffmpeg      (for "lens", "demo" and "compose")
#
# First time, in Rokid Nexus open the "Wireless ADB" plugin: it shows a line
# like "adb pair 192.168.0.23:41234 123456". Then here:
#   ./glasscast.sh pair 192.168.0.23:41234 123456
#   ./glasscast.sh connect 192.168.0.23:37000     (the connect port the plugin shows)
# After that:
#   ./glasscast.sh espelho           the easy one: finds the glasses, opens the lens window and
#                                       keeps screenshots coming for Claude. Also leaves an
#                                       "Espelhar óculos.command" here to double-click next time.
#   ./glasscast.sh view              live window (the HUD as apps draw it, in color)
#   ./glasscast.sh lens              live window as the lens shows it: green, one color
#   ./glasscast.sh camera            live window of the glasses camera (what you look at)
#   ./glasscast.sh record [name]     live window + video file
#   ./glasscast.sh demo [name]       records HUD + camera together; Ctrl+C ends and builds
#                                       the "through the lens" video (HUD light over the world)
#   ./glasscast.sh snap [name]       one screenshot
#   ./glasscast.sh watch [seconds]   a screenshot every N seconds (default 3), last 400 kept (~20 min)
#   ./glasscast.sh compose scene.mp4 hud.mp4 [out.mp4]   same look, from two videos you have
#   ./glasscast.sh off               disconnect
#
# Cable or Wi-Fi: a USB cable is used first when plugged in (no pairing); Wi-Fi otherwise.
# The phone too (anything adb sees that isn't the glasses; cable, USB debugging on):
#   ./glasscast.sh phone             live window of the phone
#   ./glasscast.sh phone-watch [s]   a phone screenshot every N seconds (watch/phone-latest.png)
#   ./glasscast.sh phone-snap        one phone screenshot
#   ./glasscast.sh log               Zap's log from the phone into logs/zap-latest.log
#   ./glasscast.sh fotos             photo timeline: taken on the glasses → reached the phone
#   ./glasscast.sh status            what is connected, and how
#   ./glasscast.sh ui                the panel: switches for all of the above, in the browser
#
# Files go into the folder this script is in (change with GLASSCAST_DIR).
# Connect that folder to Claude and it can look at what the glasses show.

set -u

# Files land next to this script (e.g. Documents/Glasscast), unless GLASSCAST_DIR says otherwise.
DIR="${GLASSCAST_DIR:-${ROKID_MIRROR_DIR:-$(cd "$(dirname "$0")" && pwd)}}"
STATE="$HOME/.glasscast"
mkdir -p "$DIR/shots" "$DIR/videos" "$DIR/watch"

say() { printf '%s\n' "$*"; }
die() { printf 'glasscast: %s\n' "$*" >&2; exit 1; }
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

# Only the glasses, never a phone plugged in for something else: adb lists the
# Rokid Glasses as model RG_glasses (override with ROKID_MODEL).
MODEL="${ROKID_MODEL:-RG_glasses}"
# Cable first, Wi-Fi second: a cable is faster and needs no pairing.
# Over Wi-Fi adb names a device "IP:PORT" or "adb-…_adb-tls-connect…"; over a cable, a plain serial.
is_wifi() { case "$1" in *:*|*_adb-tls*) return 0 ;; *) return 1 ;; esac; }
glasses_all() { adb devices -l 2>/dev/null | awk -v m="model:$MODEL" '$2=="device" && index($0, m) {print $1}'; }
glasses_usb() { local d; for d in $(glasses_all); do is_wifi "$d" || { printf '%s' "$d"; return; }; done; }
glasses_online() { local u; u="$(glasses_usb)"; [ -n "$u" ] && { printf '%s' "$u"; return; }; glasses_all | head -n 1; }

# The phone: anything adb sees that isn't the glasses (or exactly ROKID_PHONE). Cable first.
phone_all() { adb devices -l 2>/dev/null | awk -v m="model:$MODEL" 'NR>1 && $2=="device" && !index($0, m) {print $1}'; }
phone_serial() {
  if [ -n "${ROKID_PHONE:-}" ]; then printf '%s' "$ROKID_PHONE"; return; fi
  local d; for d in $(phone_all); do is_wifi "$d" || { printf '%s' "$d"; return; }; done
  phone_all | head -n 1
}
need_phone() {
  local p; p="$(phone_serial)"
  [ -n "$p" ] || die "celular não encontrado. Ligue pelo cabo e aceite 'Permitir depuração USB?' no celular (Opções do desenvolvedor > Depuração USB)."
  printf '%s' "$p"
}

# Zap's own log tags, plus crashes (any app) so a Zap crash is never missed.
ZAP_TAGS="Zap:V ZapNotifier:V ZapInbox:V ZapGallery:V ZapPhotoWatcher:V ZapSkills:V ZapPhotos:V ZapVoiceNotes:V AndroidRuntime:E"

# A screenshot as a real file on the device, then copied over: "exec-out screencap" can
# mix warnings into the picture on some devices and leave a PNG nothing can open.
grab() { # serial out.png
  local remote="/data/local/tmp/glasscast_shot.png" err
  # Why a shot failed goes to logs/grab-errors.txt, so a failure can be read instead of guessed.
  err="$(adb -s "$1" shell "screencap -p $remote" 2>&1)" || { printf '%s %s screencap: %s\n' "$(date +%T)" "$1" "$err" >> "$DIR/logs/grab-errors.txt"; return 1; }
  [ -n "$err" ] && printf '%s %s screencap said: %s\n' "$(date +%T)" "$1" "$err" >> "$DIR/logs/grab-errors.txt"
  err="$(adb -s "$1" pull "$remote" "$2" 2>&1)" || { printf '%s %s pull: %s\n' "$(date +%T)" "$1" "$err" >> "$DIR/logs/grab-errors.txt"; return 1; }
  if [ "$(wc -c < "$2")" -gt 2000 ] && [ "$(head -c 4 "$2" | tail -c 3)" = "PNG" ]; then return 0; fi
  printf '%s %s not a picture: %s bytes, starts with %s\n' "$(date +%T)" "$1" "$(wc -c < "$2" | tr -d ' ')" "$(head -c 16 "$2" | od -An -c | tr -s ' ' | head -1)" >> "$DIR/logs/grab-errors.txt"
  return 1
}

device() {
  # A cable beats any remembered Wi-Fi address.
  local usb; usb="$(glasses_usb)"
  if [ -n "$usb" ]; then printf '%s' "$usb"; return; fi
  if [ -f "$STATE" ]; then cat "$STATE"; return; fi
  local found
  found="$(glasses_online)"
  [ -n "$found" ] || die "no glasses yet: run 'pair' and 'connect' first (see the top of this file)"
  printf '%s' "$found"
}

ensure_connected() {
  local dev online
  dev="$(device)" || exit 1
  online() { adb devices -l | awk -v d="$1" -v m="model:$MODEL" '$1==d && $2=="device" && index($0, m) {found=1} END {exit !found}'; }
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
    dev="$(glasses_online)"
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
      printf '#!/bin/bash\nexec bash "%s" espelho\n' "$DIR/glasscast.sh" > "$launcher"
      chmod +x "$launcher"
    fi
    # Start clean: stop any earlier mirror still running (other Terminal windows)
    # and find the glasses again instead of trusting an old address.
    pkill -f "(rokid-mirror|glasscast).sh (watch|lens)" 2>/dev/null
    pkill -f "ffplay.*Rokid HUD" 2>/dev/null
    pkill -f "screenrecord --output-format=h264" 2>/dev/null
    rm -f "$STATE"
    adb start-server >/dev/null 2>&1
    [ -n "$(glasses_usb)" ] && say "óculos no cabo: sem Wi-Fi nem pareamento."
    # Addresses Claude left for us (conectar.txt: "connect IP:PORT" / "pair IP:PORT CODE"),
    # so nobody has to type numbers into the Terminal.
    if [ -f "$DIR/conectar.txt" ]; then
      while read -r verb a b _; do
        case "$verb" in
          connect) say "conectando em $a…"; adb connect "$a" ;;
          pair) [ -n "$(glasses_online)" ] || { say "pareando com $a…"; adb pair "$a" "$b" && sleep 3; } ;;
        esac
      done < "$DIR/conectar.txt"
      rm -f "$DIR/conectar.txt"
    fi
    sleep 2 # adb finds paired glasses on the Wi-Fi by itself; give it a moment
    if ! dev="$(ensure_connected 2>/dev/null)"; then
      # Not found by itself: the wearer reads what Wireless ADB shows and types it once.
      say ""
      say "Não achei os óculos sozinho. Vamos juntos:"
      say "  1. Óculos e Mac no mesmo Wi-Fi."
      say "  2. Nos óculos, abra Rokid Nexus > Wireless ADB."
      say "  3. Digite aqui os números que ele mostra, separados por espaço, e Enter."
      say "     Com código de pareamento:  192.168.0.31:40701 969774"
      say "     Só o endereço:             192.168.0.31:35837"
      say "     Dica: deixe o plugin aberto nos óculos enquanto digita (o código vale ~2 min e some se você sair dele)."
      # Up to three tries without restarting: a code that expired just needs the new one.
      dev=""
      for attempt in 1 2 3; do
        printf '> '
        read -r first second _ || break
        first="${first#adb pair }"; first="${first#adb connect }"; first="${first#adb }"
        if [ -n "${second:-}" ]; then
          if ! adb pair "$first" "$second"; then
            say "Não deu certo (código vencido?). Olhe o código novo no plugin e digite de novo:"
            continue
          fi
          sleep 3 # once paired, the glasses announce their connect address
        elif [ -n "${first:-}" ]; then
          adb connect "$first" >/dev/null 2>&1
        fi
        dev="$(ensure_connected 2>/dev/null)" && break
        say "Ainda não achei os óculos. Digite de novo o que o plugin mostra:"
      done
      [ -n "$dev" ] || die "não consegui. Confira o Wi-Fi e se o Wireless ADB está ligado, e rode de novo."
      say "Pronto: pareado. Da próxima vez não precisa digitar nada."
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
    # Small and always on top: a remote control for the keyboard. Look at the green
    # "HUD verde" window; click this one and use the arrows, Enter, right-click (back).
    scrcpy -s "$dev" --no-audio --window-title "Controle (clique e use as setas)" --stay-awake \
      --window-width=180 --always-on-top
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
      || die "the glasses camera didn't open. Close apps using it, or see './glasscast.sh cameras'."
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
    grab "$dev" "$file" && say "saved: $file" || die "não consegui tirar o print dos óculos"
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
      if grab "$dev" "$tmp"; then
        cp "$tmp" "$DIR/watch/$(stamp).png"
        mv "$tmp" "$DIR/watch/latest.png"
        misses=0
        # keep the last 40 timestamped shots
        ls -1t "$DIR"/watch/20*.png 2>/dev/null | tail -n +$(( ${GLASSCAST_KEEP_HUD:-400} + 1 )) | while read -r old; do rm -f "$old"; done
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

  status)
    # One line per device, for the panel: "glasses usb SERIAL" / "glasses wifi ADDR" / "glasses none".
    need adb "brew install --cask android-platform-tools"
    adb start-server >/dev/null 2>&1
    g="$(glasses_online)"; p="$(phone_serial)"
    if [ -z "$g" ]; then say "glasses none"; elif is_wifi "$g"; then say "glasses wifi $g"; else say "glasses usb $g"; fi
    if [ -z "$p" ]; then say "phone none"; elif is_wifi "$p"; then say "phone wifi $p"; else say "phone usb $p"; fi
    # A phone that is plugged in but hasn't accepted this Mac yet.
    adb devices 2>/dev/null | awk '$2=="unauthorized" {print "unauthorized " $1}'
    ;;

  find)
    # No questions asked: cable, then the remembered or announced Wi-Fi address.
    need adb "brew install --cask android-platform-tools"
    adb start-server >/dev/null 2>&1
    dev="$(ensure_connected)" || exit 1
    say "óculos: $dev"
    ;;

  phone)
    need scrcpy "brew install scrcpy"
    p="$(need_phone)" || exit 1
    scrcpy -s "$p" --no-audio --window-title "Celular" --stay-awake
    ;;

  phone-snap)
    p="$(need_phone)" || exit 1
    file="$DIR/shots/${1:-celular}_$(stamp).png"
    grab "$p" "$file" && say "saved: $file" || die "não consegui tirar o print do celular"
    ;;

  phone-watch)
    p="$(need_phone)" || exit 1
    every="${1:-5}"
    say "a phone screenshot every ${every}s in $DIR/watch (phone-latest.png is the newest). Ctrl+C to stop."
    trap 'exit 0' INT
    while true; do
      tmp="$DIR/watch/.phone-next.png"
      if grab "$p" "$tmp"; then
        cp "$tmp" "$DIR/watch/phone_$(stamp).png"
        mv "$tmp" "$DIR/watch/phone-latest.png"
        ls -1t "$DIR"/watch/phone_*.png 2>/dev/null | tail -n +$(( ${GLASSCAST_KEEP_PHONE:-240} + 1 )) | while read -r old; do rm -f "$old"; done
      else
        rm -f "$tmp"
        p="$(phone_serial)"; [ -n "$p" ] || sleep 5
      fi
      sleep "$every"
    done
    ;;

  log)
    # Zap's log from the phone into logs/zap-latest.log (fresh each start) and a dated copy.
    # Survives Zap restarts: it follows the tags, not one process.
    p="$(need_phone)" || exit 1
    mkdir -p "$DIR/logs"
    latest="$DIR/logs/zap-latest.log"
    dated="$DIR/logs/zap_$(stamp).log"
    : > "$latest"
    say "Zap log in $latest — Ctrl+C to stop."
    trap 'exit 0' INT
    while true; do
      # -T 1: from now on, not the phone's whole history.
      # shellcheck disable=SC2086
      adb -s "$p" logcat -v time -T 1 -s $ZAP_TAGS 2>/dev/null | tee -a "$dated" >> "$latest"
      sleep 2 # cable pulled or phone restarted: wait and follow again
      p="$(phone_serial)"; [ -n "$p" ] || sleep 5
    done
    ;;

  photos|fotos)
    # Photo timeline: when each photo appears on the glasses, and when it reaches the phone.
    # Goes to logs/fotos-latest.log and into the Zap log, so one file tells where a photo stopped:
    # never reached the phone = Nexus; reached it but no offer = Zap.
    dev="$(ensure_connected)" || exit 1
    mkdir -p "$DIR/logs"
    out="$DIR/logs/fotos-latest.log"; zlog="$DIR/logs/zap-latest.log"
    seen="$DIR/logs/.fotos-seen"; times="$DIR/logs/.fotos-times"
    : > "$out"; : > "$seen"; : > "$times"
    MARK=/data/local/tmp/glasscast_mark
    DIRS="/sdcard/DCIM /sdcard/Pictures /sdcard/Movies"
    adb -s "$dev" shell "touch $MARK" >/dev/null 2>&1
    p="$(phone_serial)"; [ -n "$p" ] && adb -s "$p" shell "touch $MARK" >/dev/null 2>&1
    note() { local line; line="$(date +%H:%M:%S) $*"; say "$line"; printf '%s\n' "$line" >> "$out"; printf '%s FOTOS %s\n' "$(date '+%m-%d %H:%M:%S')" "$*" >> "$zlog"; }
    newer() { adb -s "$1" shell "find $DIRS -type f -newer $MARK \\( -iname '*.jpg' -o -iname '*.jpeg' -o -iname '*.png' -o -iname '*.mp4' \\) 2>/dev/null" 2>/dev/null | tr -d '\r'; }
    note "linha do tempo ligada (óculos $dev${p:+, celular $p})"
    trap 'exit 0' INT
    while true; do
      for f in $(newer "$dev"); do
        grep -qxF "G $f" "$seen" && continue
        printf 'G %s\n' "$f" >> "$seen"
        name="${f##*/}"; printf '%s %s\n' "$name" "$(date +%s)" >> "$times"
        note "ÓCULOS  nova: $name"
      done
      if [ -n "$p" ]; then
        for f in $(newer "$p"); do
          grep -qxF "P $f" "$seen" && continue
          printf 'P %s\n' "$f" >> "$seen"
          name="${f##*/}"
          took="$(awk -v n="$name" '$1==n {print $2; exit}' "$times")"
          if [ -n "$took" ]; then note "CELULAR chegou: $name ($(( $(date +%s) - took )) s depois)"
          else note "CELULAR arquivo novo: $name (${f%/*})"; fi
        done
      fi
      sleep 1
    done
    ;;

  ui|painel)
    command -v python3 >/dev/null 2>&1 || die "python3 is missing. Run: xcode-select --install"
    exec python3 "$(cd "$(dirname "$0")" && pwd)/glasscast-panel.py" "$@"
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
    sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'
    ;;
esac
