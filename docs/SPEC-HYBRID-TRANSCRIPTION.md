# Hybrid Voice Transcription (Online / Offline) — Spec v1.0

## EN — English

### Context
Voice input in the VisualContext Android app has two very different real-world conditions:
1. **Online, noisy environment** (street, motorcycle, traffic): native on-device ASR produces fragmented output.
2. **Offline**: no internet at all — cloud transcription is impossible.

Wispr Flow solves case 1 with: fast streaming cloud ASR → LLM revision pass (punctuation, filler removal, formatting). It does NOT work offline. Android's native SpeechRecognizer DOES work offline (with downloaded language packs) but produces lower-quality text, especially in noise.

### Decision
Implement a **hybrid transcription engine** in the Android app with automatic path selection:

**Path A — ONLINE (preferred when network is available)**
1. Record audio (transient buffer only)
2. Stream to cloud ASR (Whisper large-v3 class API — e.g., OpenAI Whisper API, Deepgram, or AssemblyAI)
3. LLM revision pass: clean punctuation, remove fillers ("é... então..."), format
4. Return polished text; discard audio immediately

**Path B — OFFLINE (fallback, always available)**
1. Android native `SpeechRecognizer` with on-device recognition (language pack must be downloaded)
2. Raw transcript only (no revision pass in v1)
3. Tag output as `engine=basic`

### Path selection logic
- `ConnectivityManager` check + lightweight connectivity probe before dictation starts
- Online → Path A. On mid-request failure (timeout / API error) → automatic graceful fallback to Path B, no user data loss
- Offline → Path B directly
- Every transcript carries metadata: `engine=cloud|native`, `confidence` when available
- HUD may show a small quality indicator (e.g., dot color) so the user knows which engine produced the text

### Constraints (existing data policy — unchanged)
- **No raw audio is ever persisted.** Audio buffer is discarded immediately after transcription on both paths. Only text output is stored.
- Short HUD commands keep using Rokid native ASR as-is (latency budget). This spec applies to the **dictation / long-form transcription** path only.
- Voice notes sent as files (e.g., to the assistant): transcribed server-side, then discarded.

### Acceptance criteria
1. Dictate a long note online → cloud ASR + revision produces clean text
2. Airplane mode test → native ASR still transcribes
3. Network drops mid-dictation → falls back gracefully, no crash, no lost partial text
4. After transcription, no audio file exists on disk (verify)
5. Transcripts are tagged with engine used

---

## PT — Português

### Contexto
A entrada de voz no app Android do VisualContext tem duas condições reais muito diferentes:
1. **Online, ambiente ruidoso** (rua, moto, trânsito): o ASR nativo on-device produz texto picotado.
2. **Offline**: sem internet nenhuma — transcrição em nuvem é impossível.

O Wispr Flow resolve o caso 1 com: ASR em nuvem com streaming rápido → passo de revisão por LLM (pontuação, remoção de "é... então...", formatação). Ele NÃO funciona offline. O `SpeechRecognizer` nativo do Android FUNCIONA offline (com pacotes de idioma baixados), mas produz texto de qualidade menor, especialmente com ruído.

### Decisão
Implementar um **motor de transcrição híbrido** no app Android com seleção automática de caminho:

**Caminho A — ONLINE (preferido quando há rede)**
1. Gravar áudio (somente buffer transitório)
2. Stream para ASR em nuvem (classe Whisper large-v3 — ex.: Whisper API, Deepgram, AssemblyAI)
3. Passo de revisão por LLM: pontuação, remoção de vício de fala, formatação
4. Devolver texto limpo; descartar o áudio imediatamente

**Caminho B — OFFLINE (fallback, sempre disponível)**
1. `SpeechRecognizer` nativo do Android com reconhecimento on-device (pacote de idioma baixado)
2. Somente transcrição crua (sem revisão na v1)
3. Marcar saída como `engine=basic`

### Lógica de seleção de caminho
- Checagem com `ConnectivityManager` + probe leve de conectividade antes de ditar
- Online → Caminho A. Se falhar no meio (timeout / erro de API) → fallback automático e gracioso para o Caminho B, sem perda de dados
- Offline → Caminho B direto
- Toda transcrição carrega metadados: `engine=cloud|native`, `confidence` quando disponível
- O HUD pode mostrar um pequeno indicador de qualidade (ex.: cor do ponto) para o usuário saber qual motor produziu o texto

### Restrições (política de dados existente — sem mudança)
- **Nenhum áudio bruto é persistido.** O buffer é descartado imediatamente após a transcrição, nos dois caminhos. Somente o texto é armazenado.
- Comandos curtos do HUD continuam usando o ASR nativo do Rokid como está (orçamento de latência). Esta spec vale SOMENTE para o caminho de ditação / transcrição longa.
- Notas de voz enviadas como arquivo (ex.: pro assistente): transcritas no servidor e depois descartadas.

### Critérios de aceite
1. Ditar uma nota longa online → ASR em nuvem + revisão produz texto limpo
2. Teste em modo avião → ASR nativo continua transcrevendo
3. Rede cair no meio da ditação → fallback gracioso, sem crash, sem perder texto parcial
4. Após a transcrição, nenhum arquivo de áudio existe em disco (verificar)
5. Transcrições marcadas com o motor usado
