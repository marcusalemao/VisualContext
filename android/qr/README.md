# Leitor QR — Rokid Nexus plugin (v0.1)

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

Open it on the glasses and look at a QR code. The glasses take still photos for up to 25 s, the phone reads them in memory, and the HUD shows what the code says:

| Code | On the glasses | Tap → phone notification → tap |
|---|---|---|
| Wi-Fi | network and password | Android asks "Save this network?" (WEP/enterprise: password copied + Wi-Fi settings) |
| Link | site and address | opens in the browser |
| Pix | "the code says: name · amount · city" | copies Pix Copia e Cola — **never pays** |
| vCard / MECARD / WhatsApp | name, role, phone | "new contact" form |
| LinkedIn link | profile | opens the profile |
| Tampered Pix (bad CRC) | ⚠ "do not pay" | nothing |

- Tap = send to phone · Back = read another / close.
- **Privacy:** photos are decoded in memory and dropped; the app has **no internet permission**.
- Capabilities: `surfaces`, `camera` (the Nexus still-photo API, `NexusSnapshotSession`).
- Decoding: ZXing core (Apache-2.0). Parsing: `libs/qr-core` (shared with VisualContext).
- Tests: `./gradlew :qr:testDebugUnitTest` (card text + decoding of synthetic photos: small, noisy, inverted).

**Build:** `./gradlew :qr:assembleDebug` → `qr/build/outputs/apk/debug/qr-debug.apk`.
**Install:** on the phone; approve in Nexus → Plugins (camera included); open the app once on the phone and allow notifications; on the glasses: triple tap → Leitor QR.

---

## PT — Português

Abra nos óculos e olhe para um QR code. Os óculos tiram fotos por até 25 s, o celular lê na memória e o visor mostra o que o código diz:

| Código | Nos óculos | Toque → notificação no celular → toque |
|---|---|---|
| Wi-Fi | rede e senha | o Android pergunta "Salvar esta rede?" (WEP/corporativo: senha copiada + ajustes de Wi-Fi) |
| Link | site e endereço | abre no navegador |
| Pix | "o código diz: nome · valor · cidade" | copia o Pix Copia e Cola — **nunca paga** |
| vCard / MECARD / WhatsApp | nome, cargo, telefone | formulário de novo contato |
| Link do LinkedIn | perfil | abre o perfil |
| Pix adulterado (CRC errado) | ⚠ "não pague" | nada |

- Toque = mandar para o celular · Voltar = ler outro / fechar.
- **Privacidade:** as fotos são lidas na memória e descartadas; o app **não tem permissão de internet**.
- Permissões no Nexus: `surfaces`, `camera` (a foto da câmera do Nexus, `NexusSnapshotSession`).
- Leitura: ZXing core (Apache-2.0). Interpretação: `libs/qr-core` (a mesma do VisualContext).
- Testes: `./gradlew :qr:testDebugUnitTest` (texto dos cards + leitura de fotos sintéticas: pequena, com ruído, invertida).

**Build:** `./gradlew :qr:assembleDebug` → `qr/build/outputs/apk/debug/qr-debug.apk`.
**Instalar:** no celular; aprovar em Nexus → Plugins (com a câmera); abrir o app uma vez no celular e permitir notificações; nos óculos: toque triplo → Leitor QR.
