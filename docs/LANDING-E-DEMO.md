# Landing, /demo and Clarity — status (08/11/2026)

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

**Built (branch `web/visualcontext-site`, preview https://web-visualcontext-site.visualcontext.pages.dev):**
- `/` commercial landing, PT/EN: lens mock, Manager with free questions, how it works, privacy, download (Android beta by invite, iPhone note).
- `/demo` browser emulator: camera + mic, face recognition in the browser (face-api), Gemini Live through **single-use tokens** issued by the Supabase function `vc-demo` (the key never reaches the page), session-only memory with an optional fictional sample, the demo's own Manager with "ask anything", HUD cards, 10-minute cap, 3-question review at the end.
- `/demo-film` the 1-minute film (was `/demo`).
- `consent.js`: Microsoft Clarity only after "Pode / Não"; events `demo_start`, `capture`, `recognized`, `recall_question`, `recall_answer`, `error_*`, `demo_end`, `review_sent`; `app_version`. Camera, photos and typed text carry `data-clarity-mask`.
- `a11y.js`: solid accessibility panel (text size, high contrast, less motion, low-vision mode).
- Limits in `vc-demo`: 4 sessions per IP per day, 40 per day overall (env `DEMO_PER_DAY`), 25 questions per session (a reconnection costs 4), optional Turnstile.
- Tables `demo_sessions` and `demo_feedback` (review answers).
- Restore point: branch `restore/site-2026-11-08` (tags are blocked by the push proxy).

**Waiting on Marcus:** Gemini key for the demo (Supabase secret `GEMINI_DEMO_KEY`), budget alert, Clarity ID, DNS + Pages custom domains, optional Turnstile.

## PT — Português

**Pronto (branch `web/visualcontext-site`, prévia em https://web-visualcontext-site.visualcontext.pages.dev):**
- `/` landing comercial, PT/EN: lente simulada, Manager com perguntas livres, como funciona, privacidade, baixar (beta Android por convite, nota sobre iPhone).
- `/demo` emulador no navegador: câmera e microfone, reconhecimento de rostos no navegador (face-api), Gemini Live por **tokens de uso único** emitidos pela função `vc-demo` do Supabase (a chave nunca chega à página), memória só da sessão com exemplo fictício opcional, Manager da demo com "pergunte qualquer coisa", cartões de HUD, limite de 10 minutos, avaliação de 3 perguntas no fim.
- `/demo-film` o filme de 1 minuto (antes era `/demo`).
- `consent.js`: Microsoft Clarity só depois do "Pode / Não"; eventos `demo_start`, `capture`, `recognized`, `recall_question`, `recall_answer`, `error_*`, `demo_end`, `review_sent`; `app_version`. Câmera, fotos e texto digitado com `data-clarity-mask`.
- `a11y.js`: painel de acessibilidade sólido (tamanho do texto, alto contraste, menos movimento, modo baixa visão).
- Limites no `vc-demo`: 4 sessões por IP por dia, 40 por dia no total (variável `DEMO_PER_DAY`), 25 perguntas por sessão (uma reconexão vale 4), Turnstile opcional.
- Tabelas `demo_sessions` e `demo_feedback` (respostas da avaliação).
- Ponto de restauração: branch `restore/site-2026-11-08` (o proxy bloqueia push de tags).

**Esperando o Marcus:** chave do Gemini da demo (segredo `GEMINI_DEMO_KEY` no Supabase), alerta de orçamento, ID do Clarity, DNS e domínios no Pages, Turnstile opcional.
