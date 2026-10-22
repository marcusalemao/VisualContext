# QR reader ("Pega", working name)

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

Approved direction 11/10: a free, open-source QR reader for the Rokid Glasses, launched before VisualContext and sharing one parsing core with it. Working name "Pega" (Marcus decides the final name).

---

## EN — English

### 1. Why first
- Useful to anyone, small, uncontroversial. Nobody has a general QR reader on Nexus (checked against 22 plugins and 78 store apps; the only one close is WifiScan, Wi-Fi only).
- It tests, in a simple app, what VisualContext needs: the glasses camera, the phone hand-off, installing and updating.
- It earns trust in the Nexus registry before we show face memory.

### 2. What it reads and does

| QR type | Example | Action | Where |
|---|---|---|---|
| Wi-Fi | café, restaurant, shop | Join the network | v1: the phone. v2: the glasses (section 4) |
| Link | menu, site | Open on the phone | Phone |
| Pix | bill, shop counter | Show payee and amount on the HUD, then hand the code to the phone as Pix Copia e Cola | Phone; payment always confirmed in the bank app |
| Contact | vCard, MECARD, LinkedIn, wa.me | Save to phone contacts; with VisualContext, create the person with when and where you met | Phone |
| Text | anything else | Show on the HUD, copy on the phone | Both |

**Never:** pay from the glasses, open a link on its own, or save a contact without a tap.

### 3. How it works (v1: Nexus plugin)
- **Camera:** Nexus `camera` capability. The glasses stream H.264 to the phone over Wi-Fi Direct (or the phone's hotspot); the plugin decodes on the phone and draws on the glasses with `/camera/overlay`. Same path the official Lens plugin uses. No app on the glasses.
- **Decoding:** ML Kit barcode scanning, bundled model (works without Google Play services), with ZXing as a fallback.
- **On the HUD:** a box around the code and one line: "Wi-Fi · Café da Paim · tap to join". Tap = act, swipe = next code, back = close. Black background, green text, the Nexus grammar.
- **Hand-off:** the plugin runs on the phone, so links, Pix and contacts are already there. Pix: copy the code and show a notification "Pix ready · open your bank"; bank apps detect a copied Pix code.

### 3b. Built (12/10, v0.1) — what changed from the plan
- **Still photos instead of the video stream.** Nexus SDK 0.23 has `NexusSnapshotSession` (one JPEG per request, the same API the Nexus Assistant uses to "take a photo"). Far simpler than the H.264 link: the plugin asks for a photo every ~0.4 s for up to 25 s and stops at the first code.
- **ZXing core instead of ML Kit:** pure Java, Apache-2.0, works offline, no Google Play services. Three passes (normal, inverted, zoom on the centre).
- **The glasses show, the phone acts:** tap on the glasses → one notification on the phone → tap → Android's own "Save this network?", the browser, the contact form, or Pix Copia e Cola copied. Two human taps, never zero.
- **No internet permission** in the app.
- Code: `android/qr/` (module `:qr`, package `io.alema.qrreader`, plugin id `leitor-qr`). 11 tests.
- To check on the real glasses: snapshot speed, focus on small codes, and whether the hub asks to approve the camera.

### 4. Wi-Fi on the glasses (v2)
The café case for VisualContext needs the **glasses** on the network, not just the phone.
- **Option A, via Nexus:** propose to Anezium a hub operation "join this network" (the hub already turns the glasses' Wi-Fi on through its shell helper). Cleanest; depends on him.
- **Option B, our own glasses app:** Android 12 `ACTION_WIFI_ADD_NETWORKS` (system asks once to save) or `WifiNetworkSuggestion`. This is the pilot of the glasses-native app that VisualContext's offline mode needs.
- Not the wireless-ADB route: it needs the glasses already on a network shared with the phone, which is exactly what is missing in the café.

### 5. Pix details
- Format: BR Code (EMV, tag-length-value). Check the CRC16 (tag 63) before showing anything.
- **Static code:** the name (tag 59) and city (60) are typed by whoever made the code, so they can lie. The HUD shows them as "the code says: Padaria X · R$ 25,00" and adds "confirm in your bank".
- **Dynamic code (tag 26-25, a URL):** the payment provider signs the details. v1 shows "dynamic Pix · amount in the bank"; reading the signed details is v2.
- Fraud angle: a sticker pasted over the real QR. Showing what the code says before the bank opens helps the user notice.

### 6. Shared core (`qr-core`)
Pure Kotlin library, no Android, unit-tested on the JVM. Input: the decoded string. Output: one typed result (`Wifi`, `Link`, `Pix`, `Contact`, `Text`) plus validation errors. Used by Pega and by VisualContext (only the `Contact` type there).

### 7. Privacy
- Nothing leaves the phone except what the user opens (a link) or sends (a Pix to the bank).
- No scan history by default. Wi-Fi passwords live only in Android's own Wi-Fi settings.
- The camera stream is on only while the reader is open.

### 8. Next steps
1. `qr-core` with tests (Claude).
2. v1 plugin on SDK 0.22 with the `camera` capability (Claude); signing and registry PR (Beto).
3. Test on Marcus's glasses: Wi-Fi, a menu link, a Pix code, a vCard.
4. Proposal to Anezium for option A, together with the other platform ideas.

---

## PT — Português

Direção aprovada em 11/10: um leitor de QR gratuito e de código aberto para os Rokid Glasses, lançado antes do VisualContext e com o mesmo núcleo de leitura. Nome provisório "Pega" (o Marcus decide o nome final).

### 1. Por que primeiro
- Útil para qualquer pessoa, pequeno e sem polêmica. Ninguém tem um leitor de QR geral no Nexus (conferido em 22 plugins e 78 apps da loja; o mais próximo é o WifiScan, só Wi-Fi).
- Testa, num app simples, o que o VisualContext precisa: a câmera dos óculos, a passagem para o celular, a instalação e a atualização.
- Ganha confiança no registro do Nexus antes de mostrarmos a memória de rostos.

### 2. O que ele lê e faz

| Tipo de QR | Exemplo | Ação | Onde |
|---|---|---|---|
| Wi-Fi | café, restaurante, loja | Entrar na rede | v1: o celular. v2: os óculos (seção 4) |
| Link | cardápio, site | Abrir no celular | Celular |
| Pix | conta, balcão | Mostrar recebedor e valor no HUD e passar o código para o celular como Pix Copia e Cola | Celular; o pagamento é sempre confirmado no app do banco |
| Contato | vCard, MECARD, LinkedIn, wa.me | Salvar na agenda; com o VisualContext, criar a pessoa com quando e onde vocês se conheceram | Celular |
| Texto | qualquer outro | Mostrar no HUD, copiar no celular | Os dois |

**Nunca:** pagar pelos óculos, abrir um link sozinho ou salvar um contato sem um toque.

### 3. Como funciona (v1: plugin do Nexus)
- **Câmera:** capacidade `camera` do Nexus. Os óculos mandam vídeo H.264 para o celular por Wi-Fi Direct (ou pelo hotspot do celular); o plugin decodifica no celular e desenha nos óculos com `/camera/overlay`. É o mesmo caminho do plugin oficial Lens. Nenhum app nos óculos.
- **Leitura:** ML Kit (leitura de códigos), modelo embutido (funciona sem os serviços do Google Play), com o ZXing de reserva.
- **No HUD:** uma moldura em volta do código e uma linha: "Wi-Fi · Café da Paim · toque para conectar". Toque = agir, deslizar = próximo código, voltar = fechar. Fundo preto, texto verde, a gramática do Nexus.
- **Passagem para o celular:** o plugin roda no celular, então links, Pix e contatos já estão lá. Pix: copiar o código e mostrar a notificação "Pix pronto · abra o seu banco"; os apps de banco detectam um código Pix copiado.

### 3b. Construído (12/10, v0.1) — o que mudou do plano
- **Fotos em vez do vídeo.** O SDK 0.23 do Nexus tem `NexusSnapshotSession` (um JPEG por pedido, a mesma API que o Assistant do Nexus usa para "tirar foto"). Bem mais simples que o link H.264: o plugin pede uma foto a cada ~0,4 s por até 25 s e para no primeiro código.
- **ZXing em vez de ML Kit:** Java puro, Apache-2.0, offline, sem Google Play services. Três tentativas (normal, invertido, zoom no centro).
- **Os óculos mostram, o celular faz:** toque nos óculos → uma notificação no celular → toque → o próprio Android pergunta "Salvar esta rede?", ou abre o navegador, o formulário de contato, ou copia o Pix Copia e Cola. Dois toques de gente, nunca zero.
- **Sem permissão de internet** no app.
- Código: `android/qr/` (módulo `:qr`, pacote `io.alema.qrreader`, id do plugin `leitor-qr`). 11 testes.
- A conferir nos óculos de verdade: velocidade da foto, foco em código pequeno e se o Nexus pede para aprovar a câmera.

### 4. Wi-Fi nos óculos (v2)
O caso do café para o VisualContext precisa dos **óculos** na rede, não só do celular.
- **Opção A, pelo Nexus:** propor ao Anezium uma operação do hub "entrar nesta rede" (o hub já liga o Wi-Fi dos óculos pelo seu ajudante de shell). Mais limpo; depende dele.
- **Opção B, app próprio nos óculos:** no Android 12, `ACTION_WIFI_ADD_NETWORKS` (o sistema pergunta uma vez se salva) ou `WifiNetworkSuggestion`. É o piloto do app nativo dos óculos de que o modo offline do VisualContext precisa.
- Não pelo ADB sem fio: ele exige os óculos já numa rede em comum com o celular, que é justamente o que falta no café.

### 5. Detalhes do Pix
- Formato: BR Code (EMV, tag-tamanho-valor). Conferir o CRC16 (tag 63) antes de mostrar qualquer coisa.
- **Código estático:** o nome (tag 59) e a cidade (60) são digitados por quem fez o código, então podem mentir. O HUD mostra como "o código diz: Padaria X · R$ 25,00" e acrescenta "confirme no banco".
- **Código dinâmico (tag 26-25, uma URL):** o provedor de pagamento assina os dados. Na v1 o HUD mostra "Pix dinâmico · valor no banco"; ler os dados assinados fica para a v2.
- Fraude: um adesivo colado por cima do QR verdadeiro. Mostrar o que o código diz antes de abrir o banco ajuda a pessoa a perceber.

### 6. Núcleo compartilhado (`qr-core`)
Biblioteca Kotlin pura, sem Android, com testes na JVM. Entrada: o texto lido do código. Saída: um resultado tipado (`Wifi`, `Link`, `Pix`, `Contact`, `Text`) e os erros de validação. Usado pelo Pega e pelo VisualContext (lá, só o tipo `Contact`).

### 7. Privacidade
- Nada sai do celular, a não ser o que a pessoa abre (um link) ou envia (um Pix para o banco).
- Sem histórico de leituras por padrão. Senhas de Wi-Fi ficam só nos ajustes de Wi-Fi do próprio Android.
- A câmera só transmite enquanto o leitor está aberto.

### 8. Próximos passos
1. `qr-core` com testes (Claude).
2. Plugin v1 no SDK 0.22 com a capacidade `camera` (Claude); assinatura e PR no registro (Beto).
3. Teste nos óculos do Marcus: Wi-Fi, link de cardápio, código Pix, vCard.
4. Proposta ao Anezium para a opção A, junto com as outras ideias de plataforma.
