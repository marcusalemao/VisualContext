#!/bin/bash
# Used by the Glasscast apps (no Terminal window): starts the panel in the background
# if it isn't running, then switches on what the app asks for.
#   glasscast-open.sh painel | hud | celular | teste | desligar
HERE="$(cd "$(dirname "$0")" && pwd)"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"   # apps don't get Homebrew on their PATH
URL="http://127.0.0.1:${GLASSCAST_PORT:-8765}"
mkdir -p "$HERE/logs"
up() { curl -s -m 2 "$URL/status" >/dev/null 2>&1; }
on() { for id in "$@"; do curl -s -m 5 -X POST "$URL/switch" -d "{\"id\":\"$id\",\"on\":true}" >/dev/null; done; }

case "${1:-painel}" in
  desligar) up && curl -s -m 5 -X POST "$URL/quit" >/dev/null; exit 0 ;;
esac

echo "panel already running: $(up && echo yes || echo no)"
started=0
if ! up; then
  started=1
  GLASSCAST_NO_BROWSER=1 nohup python3 "$HERE/glasscast-panel.py" > "$HERE/logs/panel.txt" 2>&1 &
  for _ in $(seq 1 30); do up && break; sleep 0.3; done
  sleep 3 # let it find the glasses and the phone
  echo "panel started: $(up && echo yes || echo NO, see logs/panel.txt)"
fi

case "${1:-painel}" in
  painel)  open "$URL" ;;
  app)     # the one Glasscast app: everything on the first time, then just the panel
           [ "$started" = 1 ] && on lens view watch phone phonewatch log fotos; open "$URL" ;;
  hud)     on lens view watch ;;
  celular) on phone phonewatch log ;;
  teste)   on lens view watch phone phonewatch log fotos; open "$URL" ;;
esac
