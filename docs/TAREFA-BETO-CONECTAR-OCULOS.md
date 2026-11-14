# Task for Beto — "Conectar óculos" button in the Manager

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

**Why:** today the phone plugin asks the user to paste a device token and the backend address. Marcus: "I would never want to see this screen." The plugin (VisualContext 0.1.3, branch `web/visualcontext-site`) is ready to receive the token by a link; the Manager side is missing.

**What to build in the Manager (visualcontext.base44.app):**
1. A **"Conectar óculos"** button (Settings → Device, and in the first-use empty state).
2. On tap: create a **new device token** for the signed-in user, format `vc_` + 32–64 letters/digits (the plugin rejects anything else). Store only a hash; fcProxy accepts it in `x-vc-token` like today's token.
3. **On a phone:** open `visualcontext://conectar?t=<token>` (the plugin asks "Ligar este celular à sua memória?" and saves it).
   **On a computer:** show the same link as a QR code to scan with the phone camera, with a 10-minute expiry if possible.
4. A **device list** with name, date and **"Desconectar"** (revoke). Revoking makes fcProxy answer 401 for that token.
5. Never log the token, never put it in analytics, never show it again after creation.

**Done when:** Marcus taps "Conectar óculos" on his phone, the plugin opens, he taps "Conectar", and the plugin screen shows "● conectado · N pessoas na memória" — without typing anything.

**Rollback:** checkpoint before publishing, as usual.

### Part 2 (26/10) — faces taught on the glasses
Plugin 0.2 recognises faces on the phone (face-api.js 128-d, the same format as `face_embedding`). New faces are named on the phone and stay there for now. Needed from fcProxy:
- `enroll_face` {person_id?, name?, face_embedding (JSON array of 128), thumb (small JPEG base64)} → links to an existing person or creates one; returns the person.
- `seen` {person_id, when, context?} → updates `last_seen_date` / `last_seen_context`.
Only 7 of the 95 people have a `face_embedding` today.

---

## PT — Português

**Por quê:** hoje o plugin no celular pede para colar um token e o endereço do backend. Marcus: "eu nunca ia querer ver essa tela". O plugin (VisualContext 0.1.3, branch `web/visualcontext-site`) já recebe o token por link; falta o lado do Manager.

**O que construir no Manager (visualcontext.base44.app):**
1. Um botão **"Conectar óculos"** (Ajustes → Aparelho, e no vazio do primeiro uso).
2. Ao tocar: criar um **token de aparelho novo** para o usuário logado, formato `vc_` + 32–64 letras/números (o plugin recusa outro formato). Guardar só o hash; o fcProxy aceita no `x-vc-token` como o token de hoje.
3. **No celular:** abrir `visualcontext://conectar?t=<token>` (o plugin pergunta "Ligar este celular à sua memória?" e guarda).
   **No computador:** mostrar o mesmo link como QR code para ler com a câmera do celular, com validade de 10 minutos se der.
4. Uma **lista de aparelhos** com nome, data e **"Desconectar"** (revogar). Revogado, o fcProxy responde 401 para aquele token.
5. Nunca registrar o token em log, nunca mandar para analytics, nunca mostrar de novo depois de criado.

**Pronto quando:** o Marcus toca em "Conectar óculos" no celular, o plugin abre, ele toca em "Conectar" e a tela do plugin mostra "● conectado · N pessoas na memória" — sem digitar nada.

**Volta atrás:** ponto de restauração antes de publicar, como sempre.

### Parte 2 (26/10) — rostos ensinados nos óculos
O plugin 0.2 reconhece rostos no celular (face-api.js 128-d, o mesmo formato do `face_embedding`). Rostos novos ganham nome no celular e ficam lá por enquanto. Do fcProxy precisamos de:
- `enroll_face` {person_id?, name?, face_embedding (array JSON de 128), thumb (JPEG pequeno em base64)} → liga a uma pessoa existente ou cria; devolve a pessoa.
- `seen` {person_id, when, context?} → atualiza `last_seen_date` / `last_seen_context`.
Hoje só 7 das 95 pessoas têm `face_embedding`.
