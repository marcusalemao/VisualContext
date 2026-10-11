# Anezium Ecosystem Study — what it means for VisualContext

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

Studied 2026-10-10. Sources: shallow clones of 16 Anezium repos, mainly **Rokid-Nexus**: README, VISION, ROADMAP, BUSSPEC, `plugins/AGENTS.md`, `docs/PLUGIN_SDK.md`, `docs/PLUGINS.md`, `docs/SELF_ARM_ONBOARDING.md`, `plans/027`. Also RokidBrew, RokidBrew-Registry, awesome-rokid, Scribe, Relay, Live-Studio, DragonBallScouter, R08-Access-Bridge, OverlayRec, Rokid-APKs, AssistBridge, GMaps, Tasker-Bridge, RokidPipe and Rokid-Shell.
Inspiration only. No code was copied.

---

## EN — English

### 1. The map in one paragraph

Anezium's work has converged on **one platform: Rokid Nexus**.

- **Architecture:** one permanent hub on the glasses renders everything. Every feature ships as a *headless phone APK*: a plugin with no launcher icon, no glasses-side code, and capabilities the wearer grants one by one.
- **Superseded apps:** his standalone apps (Relay, Tasker-Bridge, Lyrics) are deprecated in favor of Nexus plugins.
- **Distribution:** RokidBrew-Registry, F-Droid style. You host the APK; a PR adds `plugins-nexus/<id>.json`; CI checks SHA-256, signer, manifest and "no launcher activity".
- **Size:** 13 first-party plugins plus 10 plugins from four other authors.

**No plugin and no store app identifies people.** Rokid Terminator HUD explicitly says it is "not a person-identification tool". VisualContext would be the first, which is both an opportunity and a curation risk (see §6).

### 2. Hardware facts verified on real devices (some correct our docs)

| Fact (Anezium, verified) | Impact on VisualContext |
|---|---|
| **HUD is 480×640 portrait, 240 dpi** | ⚠️ Our emulator stage is 640×480 landscape. Must be re-checked. A 448 px card fills ~93% of the real width, not 70%. |
| **Additive optics: black = transparent** | ⚠️ A dark "overlay 50/70%" **cannot exist on the glasses**: it is invisible. Hierarchy comes from brightness only, with no filled bright areas. The overlay rule only applies to phone and dashboard previews. |
| Photos render as green luminance, "fine in practice" | Confirms our duotone photo. Image surface limits: edges ≤512 px (≤512² total), ≤64 KiB JPEG/PNG, ≥150 ms between updates. |
| Touchpad gestures: tap; one-axis swipe; **double tap = BACK**; triple tap = Nexus; **long press = Rokid AI**; two-finger = brightness/volume. AI and camera buttons are not rebindable (except by Nexus `assistant` takeover). | Resolves our pending "native gesture names" item. Our grammar (double tap cancels/back) is native. Never invent new gestures. |
| **The glasses mic beamforms toward the wearer's mouth** and gates other sound | ⚠️ The **other person's** voice may be weak or cut. This is the biggest technical risk for encounter transcripts (see §4.3). |
| Continuous camera streaming makes the glasses hot and drains the battery fast (Live-Studio warning) | Always-on face detection has a real thermal and battery cost. Prefer short bursts. |
| `GLASSES_WORN` signal exists (worn/not worn) | **Free implementation of our privacy rule**: took the glasses off → lock. |

### 3. What Nexus gives a plugin (API 3, SDK `bus-client` sdk-v0.21.0, minSdk 30)

Grants (per plugin, signer-bound, revocable): `surfaces`, `ink_surface`, `camera`, `microphone`, `stt`, `tts`, `http_proxy`, `mediasync`, `assistant`, `wireless_debugging`.

How each VisualContext need maps onto Nexus:

- **Glance card (name, photo, relation, headline)**
  - **Nexus primitive:** card surface (≤64 rows, title ≤120) or a compiled **Ink** page (view/text/scroll-view, data binding, tap actions); photo through the **image surface**.
  - **Note:** photos on the HUD work today. Marcus sees them daily in Relay: the **notice band** carries text plus a photo (full width under the title, ≤150 px tall), and the **image surface** shows a full picture. Any image turns green automatically. Only the *Ink* page format cannot embed a photo yet.
  - **Best path (Marcus's insight):** the phone **draws the whole card as one monochrome image**, with the same component as the emulator and dashboard, and sends it through the image surface (≤512×512, ≤64 KiB, at 480 px wide it is 1:1 and crisp). That gives a pixel-exact HUD = dashboard preview, which is one of our fixed rules. Swipe and tap still arrive as input events, so the plugin redraws the image on each move (≥150 ms apart, fine for a timeline).
- **"RECORDING" pill**
  - **Nexus primitive:** **Live activity**: primary ≤12 chars, secondary ≤28, ≤3 actions (e.g. "Cancel"); survives the surface closing.
  - **Note:** this is exactly our pill-outside-the-card idea, in native form.
- **Recent records list**
  - **Nexus primitive:** card rows or an Ink `scroll-view` with `wx:for`.
- **Seeing the face**
  - **Nexus primitive:** `camera`: the glasses stream H.264 over Wi-Fi Direct (or a phone LocalOnlyHotspot); the phone decodes and runs ML; overlays go back via `/camera/overlay`. Freeze = full-resolution still.
  - **Note:** cold open takes **~5.5 s** (warm radio) to 9–14 s. The session shows a camera viewfinder, and the launcher entry is synthesized under the consumer's name.
- **Hearing the conversation**
  - **Nexus primitive:** `microphone` (16 kHz PCM lease) or `stt` (hub ASR, "utterance" mode only, pt supported).
  - **Note:** continuous speech is a *deferred* Nexus item ("until a consumer needs it").
- **Starting from the AI button**
  - **Nexus primitive:** `assistant` grant: the plugin opens with `openType = AI_ASSIST` and receives button edges.
- **Privacy lock**
  - **Nexus primitive:** `onNexusLinkState` → `GLASSES_WORN`.
- **Background**
  - **Nexus primitive:** plugins are *dormant unless open*. Only explicit exceptions apply: a mic lease after `detach()`, or user-scheduled alarms.

Two non-negotiable Nexus rules (from ROADMAP, "Not on the roadmap"): **no glasses-side plugin code**, and **no background polling**.

### 4. Architecture options for VisualContext

#### 4.1 Option A: pure Nexus plugin (recommended for the MVP)

Flow: the wearer looks at someone and presses the **AI button** (assistant takeover) or opens "VisualContext" from the triple-tap launcher.

1. Camera session → **freeze** → full still.
2. The phone runs face detection plus a 128/192-d embedding (ML Kit Face + MobileFaceNet TFLite; local, offline, free).
3. Match → glance card on the HUD; the activity "RECORDING" starts.
4. The `stt`/`microphone` lease captures the encounter; `detach()` lets the display sleep while it keeps listening.

- **Pros:** one-tap install from the Nexus Store; zero glasses code; inherits Nexus trust, consent and update machinery; directly in Anezium's ecosystem.
- **Cons:** not "zero-touch". Each encounter starts with one press and waits ~5 s for the camera. It is "remember on demand", not "remember automatically".

#### 4.2 Option B: native glasses app plus phone companion (DragonBallScouter / Live-Studio pattern)

The glasses run ML Kit face detection at low fps (as DragonBallScouter does on-device); the phone does embedding, matching and the Manager.

- **Pros:** truly automatic auto-enroll, exactly as specified (eye contact plus speech → 7 s buffer → countdown).
- **Cons:** heat and battery; our own install/update path (RokidBrew combo app); we lose Nexus's single link. It can still coexist: Nexus "native apps in menu" (their Next #2) will list it.

#### 4.3 Audio: the beamforming problem has three escape routes

1. **Native recorder via `assistserver`** (seen in the community *rokid-recorder*):
   - Mechanism: bind to `com.rokid.os.sprite.assistserver/.assist.MasterAssistService` (exported, no permission) and send `controlMsgJson(pkg, {"cmd":"cmd_start_audio_record","audioOpenType":"audio_no_ui"})`.
   - Output: Rokid's own recorder writes 16 kHz WAV to `/sdcard/Recordings/`; we pull it Scribe-style over LAN/SPP and transcribe with diarization (ElevenLabs/Deepgram/AssemblyAI).
   - Caveat: an undocumented binder that a firmware update can break. Validate first whether this recorder captures the *other* speaker better.
2. **Phone mic** as a second channel (phone on the table or in a pocket), fused by timestamp.
3. Raw `microphone` lease + 5× gain + diarizing cloud ASR. Cheapest to try; quality unknown.

**Decision needed on hardware:** a 10-minute test with Marcus talking to someone, comparing the three routes.

#### Recommendation

Ship **A** first (it is the ecosystem's language and the fastest path to the Store). Keep the full auto-enroll experience in the web emulator as the north-star demo. Pitch the missing pieces to Anezium as upstream capabilities (§5). If he declines, do **B** as phase 2.

### 5. What VisualContext can contribute to Nexus (ideas he does not have)

His roadmap literally says some features are deferred "until a consumer needs it". **VisualContext is that consumer.**

1. **Continuous speech** (his deferred item): a held lease with streaming partials. We bring the use case and the test plan.
2. **Headless camera snapshot**: a `camera.snapshot` capability that returns one still *without* opening the viewfinder session. That turns the ~5 s cold open into a sub-second glance, and every visual plugin (FoodFacts, visual assistant) gains from it.
3. **Low-fps sentinel**: an opt-in hub-side "face present" event (on-glasses ML Kit detection, no pixels leave), so plugins can react without streaming video. Privacy-preserving by design: only a boolean and a bounding box cross the bus.
4. **Ink image assets**: real photos inside Ink pages. Nice to have only, because the rendered-image card (§3) already works without it.
5. **Worn-state lock as a platform policy**: "plugin data sealed while the glasses are off-face". We already need it; others (OTPs, Relay) benefit too.
6. **People as a shared skill**: once his "Skills for Assistant" (Next #1) ships, VisualContext exposes `who_is_this` / `last_seen(person)` so the Assistant can answer "who is she?" without becoming a face app.

### 6. Risks and honesty points

- **Curation:** his registry carries a disclaimer on the one face-tracking toy. Pitch VisualContext as **personal memory, local-first**: embeddings on the phone, nothing shared, locked off-face, enroll only after a real conversation, and never "identify strangers". This matches the privacy rules Marcus already decided.
- **Licensing:** Nexus is Apache-2.0 (Patcher is GPL). Using the SDK is fine. Our custom source-available license is compatible as a plugin, but the registry expects a public source URL.
- **Signer pinning:** pick the release keystore once. Changing it later means users must reinstall and re-approve.
- **Backend:** the open Base44 CRUD (no auth) is incompatible with any public listing. Fixing it is still item #1 for Beto.

### 7. Concrete next steps

1. **Marcus:** confirm 480×640 portrait on his own glasses (screenshot via scrcpy). If confirmed, I redo the emulator stage and card proportions.
2. **Hardware test:** mic routes (§4.3) plus Nexus camera freeze latency on Marcus's phone.
3. **Me:** a Kotlin skeleton of `plugin-visualcontext`: descriptor (`surfaces, ink_surface, camera, stt, assistant`), card/activity/Ink fallback, `GLASSES_WORN` lock. Built against `sdk-v0.21.0`.
4. **Beto:** secure the backend; expose `/match` that accepts an embedding instead of a photo (the phone does the ML).
5. **Then:** open an issue or discussion on Rokid-Nexus with proposals §5.1–5.3, written as a consumer with a concrete plan.

---

## PT — Português

### 1. O mapa num parágrafo

O trabalho do Anezium convergiu para **uma plataforma: o Rokid Nexus**.

- **Arquitetura:** um hub permanente nos óculos desenha tudo. Cada funcionalidade é um *APK sem tela no celular*: um plugin sem ícone de launcher, sem código nos óculos e com permissões que o usuário concede uma a uma.
- **Apps substituídos:** os apps avulsos dele (Relay, Tasker-Bridge, Lyrics) foram descontinuados em favor dos plugins Nexus.
- **Distribuição:** RokidBrew-Registry, estilo F-Droid. Você hospeda o APK; um PR adiciona `plugins-nexus/<id>.json`; a CI confere SHA-256, assinatura, manifesto e "sem atividade de launcher".
- **Tamanho:** 13 plugins dele e 10 plugins de outros quatro autores.

**Nenhum plugin nem app da loja identifica pessoas.** O Rokid Terminator HUD diz explicitamente que "não é ferramenta de identificação de pessoas". O VisualContext seria o primeiro: oportunidade e risco de curadoria (ver §6).

### 2. Fatos de hardware verificados em aparelho real (alguns corrigem nossos docs)

| Fato (Anezium, verificado) | Impacto no VisualContext |
|---|---|
| **HUD é 480×640 em retrato, 240 dpi** | ⚠️ Nosso emulador está em 640×480 paisagem. Precisa ser reconferido. Um card de 448 px ocupa ~93% da largura real, não 70%. |
| **Óptica aditiva: preto = transparente** | ⚠️ Um "overlay escuro 50/70%" **não existe nos óculos**: fica invisível. Hierarquia só por brilho, sem áreas claras preenchidas. A regra do overlay vale só para os previews no celular e no dashboard. |
| Fotos aparecem como luminância verde, "ok na prática" | Confirma nossa foto em duotone. Limites da superfície de imagem: lados ≤512 px (≤512² no total), ≤64 KiB, ≥150 ms entre atualizações. |
| Gestos do touchpad: toque; deslizar em um eixo; **toque duplo = VOLTAR**; toque triplo = Nexus; **pressão longa = IA da Rokid**; dois dedos = brilho/volume. Botões de IA e câmera não são reprogramáveis (só via `assistant` do Nexus). | Resolve a pendência "nomes dos gestos nativos". Nossa gramática (toque duplo cancela/volta) é nativa. Nunca inventar gesto novo. |
| **O microfone dos óculos foca na boca de quem usa** e corta o resto | ⚠️ A voz da **outra pessoa** pode chegar fraca ou cortada. É o maior risco técnico para a transcrição dos encontros (ver §4.3). |
| Câmera contínua esquenta e drena a bateria (aviso do Live-Studio) | Detecção de rosto sempre ligada tem custo real de calor e bateria. Preferir rajadas curtas. |
| Existe o sinal `GLASSES_WORN` (no rosto / fora) | **Implementação de graça da nossa regra de privacidade**: tirou os óculos → trava. |

### 3. O que o Nexus oferece a um plugin (API 3, SDK `bus-client` sdk-v0.21.0, minSdk 30)

Permissões (por plugin, presas à assinatura, revogáveis): `surfaces`, `ink_surface`, `camera`, `microphone`, `stt`, `tts`, `http_proxy`, `mediasync`, `assistant`, `wireless_debugging`.

Como cada necessidade do VisualContext se encaixa no Nexus:

- **Card de relance (nome, foto, relação, headline)**
  - **Primitiva Nexus:** card (≤64 linhas, título ≤120) ou página **Ink** compilada (view/text/scroll-view, data binding, ações de toque); a foto vai pela **superfície de imagem**.
  - **Observação:** foto no HUD já funciona hoje. O Marcus vê isso todo dia no Relay: a **faixa de notificação** leva texto mais foto (largura toda, embaixo do título, até 150 px de altura), e a **superfície de imagem** mostra a figura inteira. Qualquer imagem vira verde sozinha. Só o formato de página *Ink* ainda não aceita foto embutida.
  - **Melhor caminho (sacada do Marcus):** o celular **desenha o card inteiro como uma imagem monocromática**, com o mesmo componente do emulador e do dashboard, e manda pela superfície de imagem (≤512×512, ≤64 KiB; com 480 px de largura fica 1:1 e nítido). Assim o HUD fica idêntico, pixel a pixel, ao preview do dashboard, que é uma das nossas regras fixas. Deslizar e tocar continuam chegando como eventos, e o plugin redesenha a imagem a cada movimento (com 150 ms de intervalo mínimo, suficiente para uma linha do tempo).
- **Pílula "GRAVANDO"**
  - **Primitiva Nexus:** **Live activity**: primário ≤12 caracteres, secundário ≤28, até 3 ações (ex.: "Cancelar"); sobrevive ao fechamento da superfície.
  - **Observação:** é exatamente nossa ideia da pílula fora do card, em versão nativa.
- **Lista de últimos registros**
  - **Primitiva Nexus:** linhas de card ou `scroll-view` com `wx:for` no Ink.
- **Ver o rosto**
  - **Primitiva Nexus:** `camera`: os óculos mandam H.264 por Wi-Fi Direct (ou hotspot do celular); o celular decodifica e roda a IA; os overlays voltam por `/camera/overlay`. Freeze = foto em resolução cheia.
  - **Observação:** abertura a frio leva **~5,5 s** (rádio aquecido) a 9–14 s. A sessão mostra o visor da câmera, e a entrada no launcher leva o nome do plugin.
- **Ouvir a conversa**
  - **Primitiva Nexus:** `microphone` (lease de PCM 16 kHz) ou `stt` (ASR do hub, só modo "frase", suporta pt).
  - **Observação:** fala contínua é item *adiado* no Nexus ("até um consumidor precisar").
- **Começar pelo botão de IA**
  - **Primitiva Nexus:** permissão `assistant`: o plugin abre com `openType = AI_ASSIST` e recebe os eventos do botão.
- **Trava de privacidade**
  - **Primitiva Nexus:** `onNexusLinkState` → `GLASSES_WORN`.
- **Segundo plano**
  - **Primitiva Nexus:** plugins *dormem a menos que estejam abertos*. Só há exceções explícitas: lease de microfone após `detach()` ou alarmes agendados pelo usuário.

Duas regras inegociáveis do Nexus (ROADMAP, "Not on the roadmap"): **nada de código de plugin nos óculos** e **nada de polling em segundo plano**.

### 4. Opções de arquitetura para o VisualContext

#### 4.1 Opção A: plugin Nexus puro (recomendado para o MVP)

Fluxo: você olha para a pessoa e aperta o **botão de IA** (assistant takeover) ou abre "VisualContext" pelo launcher do toque triplo.

1. Sessão de câmera → **freeze** → foto cheia.
2. O celular roda detecção de rosto e embedding de 128/192 dimensões (ML Kit Face + MobileFaceNet TFLite; local, offline, grátis).
3. Achou → card de relance no HUD; a activity "GRAVANDO" começa.
4. O lease de `stt`/`microphone` grava o encontro; `detach()` deixa a tela apagar enquanto continua ouvindo.

- **Prós:** instalação com um toque pela Nexus Store; zero código nos óculos; herda a segurança, o consentimento e as atualizações do Nexus; fica dentro do ecossistema do Anezium.
- **Contras:** não é "sem tocar em nada". Cada encontro começa com um toque e espera ~5 s pela câmera. É "lembrar sob demanda", não "lembrar sozinho".

#### 4.2 Opção B: app nativo nos óculos + app no celular (padrão DragonBallScouter / Live-Studio)

Os óculos rodam detecção de rosto ML Kit em baixa taxa (como o DragonBallScouter faz no aparelho); o celular faz embedding, comparação e Manager.

- **Prós:** auto-enroll realmente automático, exatamente como especificado (contato visual + fala → buffer de 7 s → contagem).
- **Contras:** calor e bateria; instalação e atualização por nossa conta (app combo do RokidBrew); perdemos o link único do Nexus. Ainda assim os dois convivem: o "apps nativos no menu" do Nexus (item 2 do Next) vai listá-lo.

#### 4.3 Áudio: o problema do microfone direcional tem três saídas

1. **Gravador nativo via `assistserver`** (visto no *rokid-recorder* da comunidade):
   - Mecanismo: conectar em `com.rokid.os.sprite.assistserver/.assist.MasterAssistService` (exportado, sem permissão) e mandar `controlMsgJson(pkg, {"cmd":"cmd_start_audio_record","audioOpenType":"audio_no_ui"})`.
   - Saída: o gravador da própria Rokid salva WAV de 16 kHz em `/sdcard/Recordings/`; puxamos estilo Scribe (LAN/SPP) e transcrevemos com separação de falantes (ElevenLabs/Deepgram/AssemblyAI).
   - Ressalva: é uma interface não documentada; uma atualização de firmware pode quebrar. Validar primeiro se esse gravador capta melhor a *outra* pessoa.
2. **Microfone do celular** como segundo canal (celular na mesa ou no bolso), alinhado pelo horário.
3. Lease cru de `microphone` + ganho de 5× + ASR em nuvem com separação de falantes. Mais barato de testar; qualidade desconhecida.

**Decisão que depende de hardware:** um teste de 10 minutos com o Marcus conversando com alguém, comparando as três rotas.

#### Recomendação

Lançar a **A** primeiro (é a língua do ecossistema e o caminho mais curto até a Store). Manter a experiência completa de auto-enroll no emulador web como demo da visão. Propor ao Anezium as peças que faltam como capacidades da plataforma (§5). Se ele não topar, fazer a **B** como fase 2.

### 5. O que o VisualContext pode contribuir para o Nexus (ideias que ele não tem)

O roadmap dele diz literalmente que algumas coisas estão adiadas "até um consumidor precisar". **O VisualContext é esse consumidor.**

1. **Fala contínua** (item adiado dele): lease mantido com resultados parciais em streaming. Nós trazemos o caso de uso e o plano de teste.
2. **Foto da câmera sem visor**: uma capacidade `camera.snapshot` que devolve uma foto *sem* abrir a sessão com visor. Transforma a abertura a frio de ~5 s num relance de menos de 1 s, e todo plugin visual (FoodFacts, assistente visual) ganha.
3. **Sentinela de baixa taxa**: evento opcional "tem rosto na frente" gerado no hub (ML Kit nos óculos, nenhum pixel sai), para plugins reagirem sem transmitir vídeo. Privado por design: só um booleano e uma caixa delimitadora passam pelo barramento.
4. **Imagens no Ink**: fotos reais dentro de páginas Ink. É só um extra, porque o card desenhado como imagem (§3) já funciona sem isso.
5. **Trava por "óculos no rosto" como política da plataforma**: "dados do plugin lacrados com os óculos fora do rosto". Nós já precisamos disso; outros plugins (OTPs, Relay) ganham também.
6. **Pessoas como skill compartilhada**: quando o "Skills for Assistant" dele (item 1 do Next) sair, o VisualContext expõe `quem_é` / `última_vez(pessoa)` e o Assistant responde "quem é ela?" sem virar um app de rosto.

### 6. Riscos e pontos de honestidade

- **Curadoria:** o registro dele põe um aviso até no brinquedo de rastrear rosto. Apresentar o VisualContext como **memória pessoal, local primeiro**: embeddings no celular, nada compartilhado, trava fora do rosto, cadastro só depois de uma conversa real, e nunca "identificar estranhos". Bate com as regras de privacidade que o Marcus já decidiu.
- **Licença:** o Nexus é Apache-2.0 (o Patcher é GPL). Usar o SDK não tem problema. Nossa licença source-available funciona como plugin, mas o registro espera um link público para o código.
- **Assinatura fixa:** escolher a keystore de release uma vez só. Trocar depois obriga o usuário a reinstalar e reaprovar.
- **Backend:** o CRUD aberto do Base44 (sem autenticação) é incompatível com qualquer publicação. Continua sendo o item 1 do Beto.

### 7. Próximos passos concretos

1. **Marcus:** confirmar 480×640 retrato nos óculos dele (print via scrcpy). Se confirmar, eu refaço o palco do emulador e as proporções do card.
2. **Teste em hardware:** as rotas de microfone (§4.3) e a latência do freeze de câmera do Nexus no celular do Marcus.
3. **Eu:** esqueleto Kotlin do `plugin-visualcontext`: descritor (`surfaces, ink_surface, camera, stt, assistant`), card/activity com fallback Ink, trava por `GLASSES_WORN`. Feito sobre o `sdk-v0.21.0`.
4. **Beto:** fechar o backend; expor um `/match` que recebe embedding em vez de foto (o celular faz a IA).
5. **Depois:** abrir uma issue ou discussão no Rokid-Nexus com as propostas §5.1–5.3, escrita como consumidor com plano concreto.
