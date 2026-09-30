# VisualContext

<p align="center"><img src="https://raw.githubusercontent.com/marcusalemao/VisualContext/main/icons/icon-512.png" width="130" alt="VisualContext"/></p>


[🇧🇷 Português](#visual-context-pt-br) | [🇬🇧 English](#visual-context-en)

---

<a id="visual-context-en"></a>
## VisualContext (English)

A multimodal assistant for Rokid smart glasses: it sees what I see via streaming, talks about anything, remembers where I left my keys, car or bike, who I talked to and what about — and reminds me of my tasks and events.

### Links

- **Live demo (web app):** https://visualcontext.base44.app — open access (no sign-up), early beta — feel free to try it; light anti-abuse protection on write actions.
- **Android APK (glasses app):** will be published in [GitHub Releases](https://github.com/marcusalemao/VisualContext/releases) when ready.
- **License:** custom source-available (see [LICENSE](LICENSE)) — commercial use requires written permission.


### Why this project exists

The native Vision AI on Rokid glasses is fast (camera + instant recognition), but it has **no memory**: every session starts from scratch — it doesn't know my name or remember where I left my keys or who I spoke with. This app solves that by routing the video feed to an assistant with persistent memory (Beto, a Base44 Superagent).

### What it does

- **Sees with me**: continuous camera streaming from the glasses when activated.
- **Talks about anything**: voice questions, short answers on the display + TTS.
- **Remembers my objects**: where I left keys, car, bike, wallet, documents.
- **Remembers places**: where I went during the day — the café, the meeting on that floor, the store.
- **Remembers people**: who I talked to and a summary of the conversation — agreements, topics and follow-ups. Works EVEN WITHOUT facial recognition: each interaction is identified by transient visual anchors (e.g. "Person 1, red coat, 11:43 AM, Av. Paulista") + time + location + conversation summary.
- **Schedule**: reminders for my Google tasks and events.

### Roadmap

- **5G tethering via Android smartwatches** (first target: Samsung Galaxy Watch 7 Ultra) — standalone connectivity for the glasses without the phone.

### Three types of memory

| Type | Entity | Example |
|------|--------|---------|
| Objects | `EpisodicMemory` (personal_object, vehicle, document) | "Keys in the gray jacket pocket, at home, 8:10 AM" |
| Places | `EpisodicMemory` (place) + coordinates | "Meeting on the 7th floor of building X, 2 PM" |
| People | `PersonInteraction` | "Person 1, red coat, 11:43 AM, Av. Paulista — we discussed the budget, they'll send a proposal by Friday" |

The people memory **does not depend on facial recognition**: each interaction gets a temporary identifier (`temp_identifier`), visual anchors (`visual_anchors`: clothing, glasses, accessories), a time window (`start_time`/`end_time`) and a conversation summary (`conversation_summary`). If the face is recognized later, the interaction is linked to the permanent person record.

### Architecture (high level)

```
┌──────────────┐     video stream     ┌──────────────┐     query      ┌─────────────┐
│  Rokid Glasses│  ─────────────────►  │   Server     │  ───────────►  │  Beto (AI)  │
│  (own Android │  (frames via        │ (Oracle Cloud│    REST API    │  Base44 API │
│   app)        │   WebSocket)        │  Free Tier)  │                │  + memory   │
└──────────────┘                      └──────────────┘                └─────────────┘
```

Full details in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### Development rules (validated in practice)

Tested on Samsung Galaxy Z Fold / Rokid (Android 16, One UI 8.5, Knox 3.13):

- Interaction **100% via hardware KeyEvents** (Power, Back, Menu, Volume) — never touch.
- HUD fixed at **640x480 landscape**, short text on the display (no scrolling).
- **Native Android/Samsung TTS** for minimal offline latency.
- Offline wake word **"oi Beto"**.
- `getUserMedia`: back camera first (`facingMode: environment`), show state on screen before calling.

### Roadmap

- [ ] Phase 0 — Android app skeleton (Kotlin + Gradle)
- [ ] Phase 1 — Local camera capture on the glasses (no touch, via KeyEvent)
- [ ] Phase 2 — Sending frames to the server (ring buffer, WebSocket)
- [ ] Phase 3 — Assistant integration (Base44 API, persistent memory)
- [ ] Phase 4 — Day memory: objects (keys/car/bike), visited places and people interactions (visual anchors + conversation summary, no facial recognition required)
- [ ] Phase 5 — Google tasks and events (reminders)
- [ ] Phase 6 — Tethering via Galaxy Watch 7 Pro (own network)

### Status

**Phase 0 — app skeleton.** Public repository under a custom source-available license (commercial use requires the author's written permission).

Official name: **VisualContext** (formerly RokidLive, briefly Live Companion) — renamed 2026-09-15. Official product spec in [docs/visualcontext-spec.md](docs/visualcontext-spec.md).

---

<a id="visual-context-pt-br"></a>
## VisualContext (Português)

Assistente multimodal para óculos Rokid: vê o que eu vejo via streaming, conversa sobre tudo, lembra onde deixei chaves, carro, moto ou bicicleta, com quem e sobre o que conversei — e me lembra das minhas tarefas e eventos.

### Links

- **Demo ao vivo (web app):** https://visualcontext.base44.app — acesso aberto (sem login), beta inicial — pode testar à vontade; proteção leve anti-abuso nas ações de escrita.
- **APK Android (app dos óculos):** será publicado em [GitHub Releases](https://github.com/marcusalemao/VisualContext/releases) quando estiver pronto.
- **Licença:** source-available customizada (ver [LICENSE](LICENSE)) — uso comercial exige permissão por escrito.


### Por que este projeto existe

O Vision AI nativo dos Rokid é rápido (câmera + reconhecimento imediato), mas **não tem memória**: cada sessão começa do zero — não sabe meu nome, não lembra onde deixei as chaves, com quem conversei. Este app resolve isso encaminhando o vídeo para um assistente com memória persistente (Beto, Superagent do Base44).

### O que ele faz

- **Vê comigo**: streaming constante da câmera dos óculos quando ativado.
- **Conversa sobre tudo**: perguntas por voz, respostas curtas no visor + TTS.
- **Lembra dos meus objetos**: onde deixei chaves, carro, moto, carteira, documentos.
- **Lembra dos lugares**: por onde passei no dia — o café, a reunião naquele andar, a loja.
- **Lembra das pessoas**: com quem conversei e o resumo da conversa — acordos, tópicos e pendências. Funciona MESMO sem reconhecimento facial: a interação fica identificada por âncoras visuais transitórias (ex: "Pessoa 1, casaco vermelho, 11:43, Av. Paulista") + horário + local + resumo do que foi falado.
- **Agenda**: lembretes das minhas tarefas e eventos do Google.

### Roadmap

- **Tethering 5G via smartwatches Android** (primeiro alvo: Samsung Galaxy Watch 7 Ultra) — conectividade standalone pros óculos, sem depender do celular.

### Três tipos de memória

| Tipo | Entidade | Exemplo |
|------|----------|---------|
| Objetos | `EpisodicMemory` (objeto_pessoal, veiculo, documento) | "Chaves no bolso da jaqueta cinza, em casa, 8h10" |
| Lugares | `EpisodicMemory` (lugar) + coordenadas | "Reunião no 7º andar do edifício X, 14h" |
| Pessoas | `PersonInteraction` | "Pessoa 1, casaco vermelho, 11:43, Av. Paulista — falamos do orçamento, ficou de mandar proposta até sexta" |

A memória de pessoas **não depende de reconhecimento facial**: cada interação ganha um identificador temporário (`temp_identifier`), âncoras visuais (`visual_anchors`: roupa, óculos, acessórios), janela de tempo (`start_time`/`end_time`) e resumo da conversa (`conversation_summary`). Se o rosto for reconhecido depois, a interação é ligada à pessoa permanente.

### Arquitetura (visão de alto nível)

```
┌──────────────┐     stream de vídeo      ┌──────────────┐     consulta      ┌─────────────┐
│  Óculos Rokid │  ─────────────────────►  │   Servidor   │  ──────────────►  │  Beto (AI)  │
│  (app Android │   (frames via WebSocket) │ (Oracle Cloud│    API REST       │  Base44 API │
│   próprio)    │                          │  Free Tier)  │                   │  + memória  │
└──────────────┘                          └──────────────┘                   └─────────────┘
```

Detalhes completos em [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### Regras de desenvolvimento (validadas na prática)

Testado em Samsung Galaxy Z Fold / Rokid (Android 16, One UI 8.5, Knox 3.13):

- Interação **100% por KeyEvents** de hardware (Power, Back, Menu, Volume) — nunca touch.
- HUD fixo em **paisagem 640x480**, texto curto no visor (sem scroll).
- TTS **nativo do Android/Samsung** para latência mínima offline.
- Wake word offline **"oi Beto"**.
- `getUserMedia`: câmera traseira primeiro (`facingMode: environment`), mostrar estado na tela antes de chamar.

### Roadmap

- [ ] Fase 0 — Esqueleto do app Android (Kotlin + Gradle)
- [ ] Fase 1 — Captura de câmera local nos óculos (sem touch, via KeyEvent)
- [ ] Fase 2 — Envio de frames ao servidor (buffer circular, WebSocket)
- [ ] Fase 3 — Integração com o assistente (API Base44, memória persistente)
- [ ] Fase 4 — Memória do dia: objetos (chaves/carro/moto), lugares visitados e interações com pessoas (âncoras visuais + resumo da conversa, sem depender de reconhecimento facial)
- [ ] Fase 5 — Tarefas e eventos do Google (lembretes)
- [ ] Fase 6 — Tethering via Galaxy Watch 7 Pro (rede própria)

### Status

**Fase 0 — esqueleto do app.** Repositório público sob licença source-available customizada (uso comercial exige permissão por escrito do autor).

Nome oficial: **VisualContext** (ex-RokidLive, brevemente Live Companion) — renomeado em 15/09/2026. Spec oficial de produto em [docs/visualcontext-spec.md](docs/visualcontext-spec.md).


