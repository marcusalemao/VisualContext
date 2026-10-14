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
brew install ffmpeg          # só para o compose
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
| `./rokid-mirror.sh view` | janela ao vivo |
| `./rokid-mirror.sh record zap` | janela ao vivo + vídeo em `~/RokidMirror/videos` |
| `./rokid-mirror.sh snap` | um print em `~/RokidMirror/shots` |
| `./rokid-mirror.sh watch 3` | um print a cada 3 s em `~/RokidMirror/watch` (`latest.png` é sempre o mais novo; guarda os 40 últimos) |
| `./rokid-mirror.sh compose cena.mp4 hud.mp4` | HUD flutuando no canto da cena (o preto vira transparente, como na lente) |
| `./rokid-mirror.sh off` | desconecta |

## Segurança

A depuração sem fio abre uma porta nos óculos para a sua rede local. Use em
casa, não em Wi-Fi público, e **desligue no plugin Wireless ADB quando
terminar**. O `off` só desconecta o Mac; quem fecha a porta é o plugin.
