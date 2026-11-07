# VisualContext Solo (0.1.0)

The assistant running on the glasses themselves: no phone, no Nexus. Internet from any Wi-Fi or a watch hotspot.

- Tap: ask (opens a Gemini Live session). Tap while it talks: interrupt. Tap while listening: send a photo now.
- Back or swipe: end the session. Back again: close. Menu: set up again.
- Quiet until asked; never asks "anything else?"; closes itself after 25 s of silence; resumes the Live connection on its own when Google rotates it.
- Camera only when Gemini calls `olhar` (or you tap). Tools: `olhar`, `buscar_pessoa` (VisualContext memory, needs the device token), `guardar_nota`, Google Search.
- Save mode from setup: nothing / answer as note / answer + photo. Notes stay on the glasses for now (`files/notas/notas.jsonl`).
- Setup by QR made at visualcontext.alema.io/solo (`web/solo.html`); the key never leaves the browser.

Install (glasses on the same Wi-Fi as the computer; see the "Instalar pelo Wi-Fi" plugin):

```
adb install vcsolo-0.1.0.apk
adb shell pm grant io.alema.vcsolo android.permission.CAMERA
adb shell pm grant io.alema.vcsolo android.permission.RECORD_AUDIO
```

---

# VisualContext Solo (0.1.0) — PT

O assistente rodando nos próprios óculos: sem celular, sem Nexus. Internet de qualquer Wi-Fi ou do hotspot do relógio.

- Toque: perguntar (abre uma sessão do Gemini Live). Toque enquanto ele fala: interrompe. Toque enquanto ouve: manda uma foto agora.
- Voltar ou deslizar: encerra a sessão. Voltar de novo: fecha. Menu: configurar de novo.
- Fica quieto até ser chamado; nunca pergunta "mais alguma coisa?"; fecha sozinho depois de 25 s de silêncio; retoma a conexão sozinho quando o Google a troca.
- Câmera só quando o Gemini chama `olhar` (ou quando você toca). Ferramentas: `olhar`, `buscar_pessoa` (memória do VisualContext, precisa do token do aparelho), `guardar_nota`, busca do Google.
- Modo de guardar escolhido na configuração: nada / resposta como nota / resposta + foto. Por enquanto as notas ficam nos óculos.
- Configuração por QR feito em visualcontext.alema.io/solo; a chave nunca sai do navegador.
