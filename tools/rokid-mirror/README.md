# rokid-mirror

Veja, grave e fotografe a tela dos Rokid Glasses no Mac — para qualquer projeto
(Zap, VisualContext, o que vier). Serve para três coisas:

- **testar sem pôr os óculos**: o HUD aparece numa janela do Mac;
- **gravar demos**: vídeo da tela dos óculos e, com `compose`, o HUD flutuando
  sobre um vídeo da cena, como quem usa enxerga;
- **o Claude acompanhar**: `watch` salva um print a cada poucos segundos numa
  pasta; ligue essa pasta ao Claude e ele vê o que os óculos mostram.

## Instalar (uma vez)

```
brew install scrcpy
brew install --cask android-platform-tools
brew install ffmpeg          # para lens, demo e compose
chmod +x rokid-mirror.sh
```

## Conectar (por Wi-Fi, sem cabo)

1. Mac e óculos na mesma rede Wi-Fi (a rede dos óculos é configurada pelo Hi Rokid).
2. No Rokid Nexus, abra o plugin **Wireless ADB**. Ele mostra o comando de
   pareamento (IP, porta e código) e a porta de conexão.
3. No Terminal:
   ```
   ./rokid-mirror.sh pair 192.168.0.23:41234 123456
   ./rokid-mirror.sh connect 192.168.0.23:37000
   ```
   O pareamento é uma vez só. A porta de conexão muda quando os óculos
   reiniciam: aí é só repetir o `connect`.

## Usar

| Comando | O que faz |
|---|---|
| `./rokid-mirror.sh view` | janela ao vivo, com as cores que o app desenha |
| `./rokid-mirror.sh lens` | janela ao vivo **como a lente mostra**: tudo verde, uma cor só, preto = nada |
| `./rokid-mirror.sh camera` | janela ao vivo da **câmera dos óculos** (o que você está olhando) |
| `./rokid-mirror.sh demo zap` | grava HUD e câmera juntos; Ctrl+C termina e monta o vídeo **"através da lente"** (a luz verde do HUD somada ao mundo) |
| `./rokid-mirror.sh record zap` | janela ao vivo + vídeo em `~/RokidMirror/videos` |
| `./rokid-mirror.sh snap` | um print em `~/RokidMirror/shots` |
| `./rokid-mirror.sh watch 3` | um print a cada 3 s em `~/RokidMirror/watch` (`latest.png` é sempre o mais novo; guarda os 40 últimos) |
| `./rokid-mirror.sh compose cena.mp4 hud.mp4` | o mesmo visual "através da lente", a partir de dois vídeos que você já tem |
| `./rokid-mirror.sh off` | desconecta |

## Por que o `view` não parece a lente

O espelhamento mostra a imagem que o app desenha. A lente dos Rokid é
transparente e de uma cor só: onde o app desenha luz aparece verde, onde
desenha preto não aparece nada, e atrás está o mundo real. O `lens` imita a
cor; o `demo` junta também o mundo (pela câmera dos óculos).

## Segurança

A depuração sem fio abre uma porta nos óculos para a sua rede local. Use em
casa, não em Wi-Fi público, e **desligue no plugin Wireless ADB quando
terminar**. O `off` só desconecta o Mac; quem fecha a porta é o plugin.
