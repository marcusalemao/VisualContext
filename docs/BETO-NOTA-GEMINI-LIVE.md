# Note from Beto — fcProxy done + Gemini Live in parallel (16/10/2026)

[EN English](#en--english) | [PT Portugues](#pt--portugues)

## EN — English

**1. Reply to `docs/TAREFA-BETO-ANALYZE.md` — task DONE (executed by Beto on 10/10).**
- `fcProxy` now uses the secret `VC_GEMINI_API_KEY` sent as the `x-goog-api-key` header, calling `gemini-flash-latest`. Budget logging kept.
- New action `analyze_encounter` deployed with the full prompt (name rules, tags, hook, context, dossier merge, actions), returning parsed JSON. Smoke test passed on Marcus's scenario.
- Old actions untouched; frontend/entities untouched.

**2. New direction from Marcus (16/10, relayed by Beto — Marcus owns the product decision).**
Gemini Live (the live multimodal model, e.g. `gemini-flash-live-preview`) should run **in parallel** with the VisualContext capture function:
- VisualContext keeps its memory-first contract: rolling buffer, keeps only the last frame, deletes the rest (spec J). It stays the REGISTRO layer.
- Gemini Live is the on-demand, deep-query layer with vision. Example: user says "tell me more about this place" → a Live session answers using live camera context, without touching the capture pipeline.
- Division: VisualContext = registro (memory). Gemini Live = consulta/acao pontual (execution). This follows the ecosystem principle already agreed: registro is ours, execution delegates.

**3. Open questions to spec (Claude, before any implementation):**
- Camera sharing: how the VC capture loop and a Live session coexist on the glasses (CXR-S constraints).
- Trigger UX: the "oi Beto" wake already starts Live via the Nexus Assistant gateway — define when a Live answer should read the last VC frame for context.
- Persistence: what (if anything) from a Live session gets saved into memory (default: nothing is saved unless the user asks to Save).
- Battery/thermal budget of running both.

## PT — Portugues

**1. Resposta a `docs/TAREFA-BETO-ANALYZE.md` — tarefa FEITA (executada pelo Beto em 10/10).**
- O `fcProxy` agora usa o secret `VC_GEMINI_API_KEY` no header `x-goog-api-key`, chamando `gemini-flash-latest`. Log de orcamento mantido.
- Nova acao `analyze_encounter` publicada com o prompt completo (regras de nome, tags, gancho, contexto, merge do dossie, acoes), devolvendo JSON puro. Smoke test passou no cenario do Marcus.
- Acoes antigas intactas; frontend e entidades intactos.

**2. Nova direcao do Marcus (16/10, repassada pelo Beto — a decisao de produto e dele).**
O Gemini Live (o modelo multimodal ao vivo, ex. `gemini-flash-live-preview`) deve rodar **em paralelo** com a funcao de captura do VisualContext:
- O VisualContext mantem o contrato memory-first: buffer rolante, guarda so o ultimo frame, apaga o resto (spec J). Continua sendo a camada de REGISTRO.
- O Gemini Live e a camada de consulta profunda sob demanda, com visao. Exemplo: o usuario diz "quero saber mais detalhes sobre esse lugar" -> uma sessao Live responde usando o contexto da camera ao vivo, sem mexer no pipeline de captura.
- Divisao: VisualContext = registro (memoria). Gemini Live = consulta/acao pontual (execucao). Segue o principio de ecossistema ja acordado: registro e nosso, execucao delega.

**3. Questoes abertas pra especificar (Claude, antes de qualquer implementacao):**
- Compartilhamento de camera: como o loop de captura do VC e uma sessao Live coexistem nos oculos (restricoes CXR-S).
- UX de disparo: o "oi Beto" ja inicia o Live pelo gateway do Assistant do Nexus — definir quando a resposta do Live deve ler o ultimo frame do VC como contexto.
- Persistencia: o que (se algo) de uma sessao Live entra na memoria (padrao: nada e salvo a nao ser que o usuario peca pra Salvar).
- Orcamento de bateria/temperatura de rodar os dois.
