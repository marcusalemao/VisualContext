# Backend snapshot: Beto's Base44 app

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

Read-only export taken on 2026-10-11 from the Base44 app `6a11083db49430b410a8c066` (Beto's Superagent), where the VisualContext backend has lived so far. The goal: the code lives in Git, so the project never depends on one app or one assistant.

- `functions/<name>/main.ts`: the 16 backend functions as deployed that day (fcProxy, nexusGateway, fcRecognize, fcMessages, fetchPhotos, lcSyncToManager, rv*, serve*, logRokidCrash).
- `entities/*.json`: the 12 data models (FaceContextPerson, FaceContextEncounter, …).
- `pages/`: the client pages those functions serve (hud-bridge, emulator, HUD editor, Manager webapp).
- **No secrets here.** The functions read them from the environment. Names in use: `VC_DEVICE_TOKEN`, `GEMINI_API_KEY`, `VC_GEMINI_API_KEY`, `GOOGLE_API_KEY`, `GOOGLE_API_KEY_2`, `OPENROUTER`, `BASE44_API_KEY` (`VC_DEVICE_TOKEN_2` is unused).
- **No user data here.** People, encounters and photos are personal data and stay out of this public repo.
- The older copies in `backend/rv*.ts` are from before the 11/10 token lock; the files here are current.

## PT — Português

Cópia somente leitura feita em 11/10/2026 do app Base44 `6a11083db49430b410a8c066` (o Superagent Beto), onde o backend do VisualContext morou até agora. O objetivo: o código fica no Git, e o projeto nunca depende de um app ou de um assistente só.

- `functions/<nome>/main.ts`: as 16 funções do backend como estavam publicadas nesse dia.
- `entities/*.json`: os 12 modelos de dados.
- `pages/`: as páginas que essas funções servem (hud-bridge, emulador, editor do HUD, Manager web).
- **Sem segredos.** As funções leem do ambiente. Nomes em uso listados acima (`VC_DEVICE_TOKEN_2` não é usado).
- **Sem dados de usuários.** Pessoas, encontros e fotos são dados pessoais e ficam fora deste repositório público.
- As cópias antigas em `backend/rv*.ts` são de antes do bloqueio por token de 11/10; as daqui são as atuais.
