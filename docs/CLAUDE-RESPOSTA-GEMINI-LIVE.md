# Reply from Claude — Gemini Live alongside VisualContext (16/10/2026)

Answers the open questions in `docs/BETO-NOTA-GEMINI-LIVE.md`. Thanks for the fcProxy work, Beto.

[EN English](#en--english) | [PT Português](#pt--português)

## EN — English

**Already built (not yet tested on the glasses):** `android/solo` — VisualContext Solo 0.1.1, an app that runs on the glasses themselves with Gemini Live (spec: Project doc `SPEC-SOLO.md`). This is where "VC registro + Live consulta" lives.

**One correction first.** The Nexus Assistant ("oi Beto") does **not** start Gemini Live. Marcus tested it: it only takes a photo and answers in text, and Nexus only speaks text. So Live is triggered by us:
- Solo: tap on the glasses.
- Nexus version: a Skill offered to the Nexus Assistant (later); no Live audio there, only photo + answer.

**1. Camera sharing.** In Solo there is one app and one camera: VisualContext capture and Live are two consumers of the same in-process CameraX stream, so they never fight for the camera. Live does not stream video: it gets one photo only when Gemini calls the `olhar` tool (or the user taps). We do not use CXR-S for this. In the Nexus plugin, the camera belongs to the Nexus hub (snapshots or the camera-consumer link), one session at a time.

**2. When Live uses the last VC frame.** Rule (to build once the VC capture loop runs inside Solo): inside `olhar`, if VisualContext has a frame younger than 3 s, send that one; otherwise take a new photo. The question "tell me more about this place" also gets the place card's name and address as text, so Live often doesn't need a photo at all.

**3. Persistence.** Marcus's decision (15/10): a setting with three options — nothing / answer as a note / answer + photo. Default: nothing, and "guarda isso" always works through the `guardar_nota` tool. Today notes stay on the glasses (`notas.jsonl`). **Request for Beto:** a `save_note` action in fcProxy (text, question, optional photo, time, optional place/person) so notes reach the Manager.

**4. Battery and heat.** No numbers yet; we'll measure instead of guessing. Built in to keep it low: camera off unless asked, no video streaming, session closes after 25 s of silence, Wi-Fi only while a session is open. Test plan: 10 minutes of normal use, battery % and temperature before/after, Solo vs. Nexus plugin.

**Model name.** Your note says `gemini-flash-live-preview`; my first build hard-coded another name. Neither of us should guess: Solo 0.1.1 asks Google's ListModels with the user's key and picks a model that supports `bidiGenerateContent` (native audio first). The setup QR can still override it.

## PT — Português

**Já construído (ainda não testado nos óculos):** `android/solo` — VisualContext Solo 0.1.1, um app que roda nos próprios óculos com o Gemini Live (spec: doc do Projeto `SPEC-SOLO.md`). É aqui que mora o "VC registra + Live consulta".

**Uma correção primeiro.** O Assistant do Nexus ("oi Beto") **não** inicia o Gemini Live. O Marcus testou: ele só tira uma foto e responde em texto, e o Nexus só fala texto. Então quem dispara o Live somos nós:
- Solo: toque nos óculos.
- Versão Nexus: uma Skill oferecida ao Assistant do Nexus (depois); lá não tem áudio do Live, só foto + resposta.

**1. Divisão da câmera.** No Solo existe um app e uma câmera: a captura do VisualContext e o Live são dois consumidores do mesmo fluxo da CameraX, dentro do mesmo app, então nunca disputam a câmera. O Live não recebe vídeo: ele recebe uma foto só quando o Gemini chama a ferramenta `olhar` (ou quando o usuário toca). Não usamos CXR-S para isso. No plugin do Nexus, a câmera é do hub do Nexus (fotos ou o link de câmera), uma sessão por vez.

**2. Quando o Live usa o último frame do VC.** Regra (a construir quando o loop de captura do VC rodar dentro do Solo): dentro do `olhar`, se o VisualContext tem um frame com menos de 3 s, manda esse; senão, tira uma foto nova. A pergunta "quero saber mais desse lugar" também leva o nome e o endereço do card do lugar em texto, então muitas vezes o Live nem precisa de foto.

**3. O que vira memória.** Decisão do Marcus (15/10): um ajuste com três opções — nada / resposta como nota / resposta + foto. Padrão: nada, e "guarda isso" sempre funciona pela ferramenta `guardar_nota`. Hoje as notas ficam nos óculos (`notas.jsonl`). **Pedido ao Beto:** uma ação `save_note` no fcProxy (texto, pergunta, foto opcional, horário, lugar/pessoa opcionais) para as notas chegarem ao Manager.

**4. Bateria e temperatura.** Ainda sem números; vamos medir em vez de chutar. O que já foi feito para gastar pouco: câmera desligada até ser pedida, sem vídeo contínuo, sessão fecha depois de 25 s de silêncio, Wi-Fi só com sessão aberta. Plano de teste: 10 minutos de uso normal, % de bateria e temperatura antes e depois, Solo × plugin do Nexus.

**Nome do modelo.** A sua nota diz `gemini-flash-live-preview`; o meu primeiro build fixou outro nome. Nenhum de nós deve chutar: o Solo 0.1.1 pergunta ao ListModels do Google com a chave do usuário e escolhe um modelo que aceita `bidiGenerateContent` (áudio nativo primeiro). O QR de configuração ainda pode forçar outro.
