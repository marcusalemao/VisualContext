# VisualContext — Rokid Nexus plugin (v0.1)

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

Headless phone plugin for [Rokid Nexus](https://github.com/Anezium/Rokid-Nexus) (SDK `bus-client` sdk-v0.23.0, Apache-2.0).

**v0.1 "hello glasses":** opens a card on the HUD that proves the whole chain works — glasses ↔ Nexus ↔ this phone app ↔ VisualContext backend (with the device token). Tap = refresh · Back = close. Camera, microphone and faces come next.

- The card is drawn on the phone as one PNG (image surface, ≤512 px, ≤64 KiB): monochrome green, black = transparent, 20 % green fill, 2 px strokes.
- The device token is typed once in the plugin settings and stays in the phone's private storage.
- Capabilities requested: `surfaces` only.

**Build:** `./gradlew :app:assembleDebug` (JDK 17+, Android SDK 36). Output: `app/build/outputs/apk/debug/app-debug.apk`.

**Install:** Nexus → Settings → Advanced → Developer mode on; install the APK on the phone; approve it in Nexus → Plugins; open it on the glasses (triple tap → VisualContext).

> The approval is tied to the signing certificate. v0.1 is signed with a test key; switching to the final key later means uninstall + reinstall + approve once.

---

## PT — Português

Plugin de celular (sem tela própria nos óculos) para o [Rokid Nexus](https://github.com/Anezium/Rokid-Nexus) (SDK `bus-client` sdk-v0.23.0, Apache-2.0).

**v0.1 "olá, óculos":** abre um card no HUD que prova que a corrente inteira funciona — óculos ↔ Nexus ↔ este app no celular ↔ backend do VisualContext (com o token do aparelho). Toque = atualizar · Voltar = fechar. Câmera, microfone e rostos vêm em seguida.

- O card é desenhado no celular como um PNG só (superfície de imagem, ≤512 px, ≤64 KiB): verde monocromático, preto = transparente, fundo verde 20 %, contornos de 2 px.
- O token do aparelho é digitado uma vez nos ajustes do plugin e fica no armazenamento privado do celular.
- Permissões pedidas: só `surfaces`.

**Build:** `./gradlew :app:assembleDebug` (JDK 17+, Android SDK 36). Saída: `app/build/outputs/apk/debug/app-debug.apk`.

**Instalar:** Nexus → Ajustes → Avançado → ligar o modo desenvolvedor; instalar o APK no celular; aprovar em Nexus → Plugins; abrir nos óculos (toque triplo → VisualContext).

> A aprovação fica presa ao certificado de assinatura. A v0.1 é assinada com uma chave de teste; trocar para a chave definitiva depois exige desinstalar, reinstalar e aprovar uma vez.
