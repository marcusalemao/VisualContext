# Glasscast

**See, record and capture the Rokid Glasses screen on a Mac — as the lens shows it.**
Veja, grave e fotografe a tela dos Rokid Glasses no Mac — do jeito que a lente mostra.

Independent tool by [alema.io](https://alema.io). Not affiliated with Rokid.
Ferramenta independente da [alema.io](https://alema.io). Não é afiliada à Rokid.

## What it does / O que faz

| Command / Comando | |
|---|---|
| `./glasscast.sh espelho` | **The easy one.** Finds the glasses, opens the green lens window, keeps screenshots coming. **O mais fácil.** Acha os óculos, abre a janela verde da lente e vai salvando capturas. |
| `./glasscast.sh lens` | Live window as the lens shows it: one green color, black = nothing. / Janela ao vivo como a lente mostra. |
| `./glasscast.sh view` | Live window with the app's own colors. / Janela ao vivo com as cores do app. |
| `./glasscast.sh camera` | The glasses camera, live. / A câmera dos óculos, ao vivo. |
| `./glasscast.sh demo name` | Records HUD + camera; Ctrl+C builds the **"through the lens"** video (HUD light over the world). / Grava HUD e câmera e monta o vídeo **"através da lente"**. |
| `./glasscast.sh record name` | Live window + video file. / Janela ao vivo + vídeo. |
| `./glasscast.sh snap` | One screenshot. / Uma captura. |
| `./glasscast.sh watch 3` | A screenshot every 3 s (`watch/latest.png` is the newest, last 40 kept) — point an AI assistant at that folder and it sees what you see. / Uma captura a cada 3 s; a última é `watch/latest.png`. |
| `./glasscast.sh compose scene.mp4 hud.mp4` | The "through the lens" look from two videos you already have. |
| `./glasscast.sh off` | Disconnect. / Desconecta. |

## Panel / Painel

Double-click **`Glasscast.command`** (or `./glasscast.sh ui`): a page opens in the browser with switches for everything above, plus the phone — mirror it, screenshot it, and save **Zap's log** to `logs/zap-latest.log`. The page only answers this Mac (127.0.0.1).
Clique duas vezes em **`Glasscast.command`**: abre uma página com botões liga/desliga para tudo, inclusive o celular e o log do Zap.

**Cable or Wi-Fi / Cabo ou Wi-Fi:** with a USB cable plugged in, Glasscast uses it (no pairing, faster); without one, Wi-Fi as before. The phone needs *Developer options › USB debugging* on.
Com o cabo ligado, ele usa o cabo; sem cabo, o Wi-Fi. No celular, ligue *Opções do desenvolvedor › Depuração USB*.

| Command / Comando | |
|---|---|
| `./glasscast.sh phone` | The phone, live. / O celular, ao vivo. |
| `./glasscast.sh phone-watch 5` | A phone screenshot every 5 s (`watch/phone-latest.png`). |
| `./glasscast.sh log` | Zap's log from the phone (`logs/zap-latest.log`). / O log do Zap. |
| `./glasscast.sh status` | What is connected, and how. / O que está conectado, e como. |

## Install (once) / Instalar (uma vez)

```
brew install scrcpy ffmpeg
brew install --cask android-platform-tools
```

Glasscast only **calls** these free tools (adb, scrcpy, ffmpeg); it doesn't include their code.

## Connect (once) / Conectar (uma vez)

1. Install [Rokid Nexus](https://github.com/Anezium/Rokid-Nexus) and open its **Wireless ADB** plugin on the glasses.
2. Run `./glasscast.sh espelho`. If it asks, type what the plugin shows: `IP:PORT CODE` (e.g. `192.168.0.31:40701 969774`).

After pairing once, `espelho` finds the glasses by itself: no numbers to type.
Depois do primeiro pareamento, o `espelho` acha os óculos sozinho.

It only ever talks to the glasses (adb model `RG_glasses`), never to a phone you have plugged in.

## Safety / Segurança

Wireless debugging opens a port on the glasses to your local network. Use it at home, not on public Wi-Fi, and **turn it off in the Wireless ADB plugin when you're done** — `off` only disconnects the Mac.
A depuração sem fio abre uma porta nos óculos para a sua rede. Use em casa e **desligue no plugin Wireless ADB quando terminar**.
