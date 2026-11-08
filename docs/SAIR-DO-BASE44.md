# Leaving Base44 — plan (started 06/11/2026)

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

**Decision (Marcus, 06/11):** start leaving Base44 now. Reasons: cost, independence (Marcus never wanted Beto to own the project), and a cheaper stack.

### What Base44 does for us today (inventory, 06/11)
| Piece | Where | Size |
|---|---|---|
| People (`FaceContextPerson`: name, company, relation, contacts, notes, tags, 128-d `face_embedding`, last seen) | Beto's app `6a11083d…` | 95 (88 from LinkedIn, 7 with a face) |
| Encounters (`FaceContextEncounter`: person, date, place, context, speech, OCR) | same | 20 |
| Things / places (`EpisodicMemory`) | same | 3 |
| `fcProxy` function: the only door the apps use (`x-vc-token`, `action: list`) | same | — |
| Encounter analysis with an LLM (`analyze_encounter`, AI usage log) | same | — |
| Old Manager UI (dashboard, person profile, duplicate review, encounter form) | app `6aa30727…` (visualcontext.base44.app) | — |
| Beto (agent, chat, WhatsApp) | Base44 Superagent | — |

The apps only talk to Base44 through **one door** (`fcProxy`), so the switch is replacing that door.

### Target (proposal)
- **Database + login + files + server functions: Supabase**, project in São Paulo (`sa-east-1`), free plan. Postgres is portable: if one day we move to the Hetzner VPS, it is a database dump, not a rewrite.
  - Tables: `people`, `faces` (512-d ArcFace + 128-d legacy, `pgvector`), `encounters`, `moments`, `things`, `places`, `notes`, `devices` (hashed tokens).
  - Row-level security: every row belongs to its owner; the glasses/phone use a device token checked by a server function, never the database key.
- **Manager:** a web page on our own site (Cloudflare Pages, free): `visualcontext.alema.io/manager`. Login by e-mail link (Supabase Auth).
- **AI:** Gemini with the user's own key (already so in the app); the Plus plan later through our function.
- **Free-plan catch:** a free Supabase project pauses after 7 days without use; fine while testing, Pro ($25/month) or the VPS before real users.

### Steps
1. Create the Supabase project and the schema. *(waiting for Marcus's OK on where)*
2. Export the 95 people, 20 encounters and 3 things from Base44 and import them (script in `tools/migrate/`, kept so it can be re-run).
3. Server function `vc` with the same actions as `fcProxy` (`list`, `save`, `enroll_face`, `seen`, `ask`) + device tokens.
4. App: switch the backend address (already a setting), keep Base44 as fallback for two weeks.
5. New Manager on the site, against Supabase: Ask anything, catalog, My day, import contacts, merge duplicates (spec `SPEC-MANAGER-INICIO.md`).
6. ~~Two weeks side by side, then Base44 becomes read-only, then off.~~ **Done 08/11/2026** — Marcus approved removing Base44 ("tudo aprovado, pode tirar"): old `webapp`, web emulator and HUD editor left the site (redirect to `/manager`); the app no longer mentions it. The Base44 app itself can be paused in its dashboard; the data was migrated on 06/11 (95 people, 20 encounters, 3 things). Beto: decide with Marcus what he keeps doing (WhatsApp assistant only?).

---

## PT — Português

**Decisão (Marcus, 06/11):** começar a sair do Base44 agora. Motivos: custo, independência (o Marcus nunca quis que o Beto fosse dono do projeto) e uma estrutura mais barata.

### O que o Base44 faz por nós hoje (inventário, 06/11)
| Peça | Onde | Tamanho |
|---|---|---|
| Pessoas (`FaceContextPerson`: nome, empresa, relação, contatos, notas, tags, `face_embedding` de 128 números, última vez) | app do Beto `6a11083d…` | 95 (88 do LinkedIn, 7 com rosto) |
| Encontros (`FaceContextEncounter`: pessoa, data, local, contexto, fala, OCR) | idem | 20 |
| Coisas / lugares (`EpisodicMemory`) | idem | 3 |
| Função `fcProxy`: a única porta que os apps usam (`x-vc-token`, `action: list`) | idem | — |
| Análise de encontro por IA (`analyze_encounter`, registro de uso de IA) | idem | — |
| Manager antigo (dashboard, perfil, duplicados, formulário de encontro) | app `6aa30727…` (visualcontext.base44.app) | — |
| Beto (agente, chat, WhatsApp) | Superagent do Base44 | — |

Os apps só falam com o Base44 por **uma porta** (`fcProxy`), então a troca é substituir essa porta.

### Destino (proposta)
- **Banco + login + arquivos + funções no servidor: Supabase**, projeto em São Paulo (`sa-east-1`), plano grátis. Postgres é portátil: se um dia formos para o VPS da Hetzner, é uma cópia do banco, não uma reescrita.
  - Tabelas: `people`, `faces` (ArcFace 512 + legado 128, `pgvector`), `encounters`, `moments`, `things`, `places`, `notes`, `devices` (tokens com hash).
  - Segurança por linha: cada registro é do seu dono; óculos e celular usam um token de aparelho conferido por uma função no servidor, nunca a chave do banco.
- **Manager:** uma página no nosso site (Cloudflare Pages, grátis): `visualcontext.alema.io/manager`. Login por link no e-mail (Supabase Auth).
- **IA:** Gemini com a chave do próprio usuário (já é assim no app); o plano Plus depois, pela nossa função.
- **Pegadinha do grátis:** projeto grátis do Supabase pausa depois de 7 dias sem uso; tudo bem nos testes, Pro (US$ 25/mês) ou o VPS antes de ter usuários de verdade.

### Passos
1. Criar o projeto no Supabase e as tabelas. *(esperando o OK do Marcus sobre onde)*
2. Exportar as 95 pessoas, 20 encontros e 3 coisas do Base44 e importar (script em `tools/migrate/`, guardado para rodar de novo).
3. Função `vc` no servidor com as mesmas ações do `fcProxy` (`list`, `save`, `enroll_face`, `seen`, `ask`) + tokens de aparelho.
4. App: trocar o endereço do backend (já é um ajuste), com o Base44 de reserva por duas semanas.
5. Manager novo no site, ligado ao Supabase: Pergunte qualquer coisa, catálogo, Meu dia, importar contatos, mesclar duplicados (spec `SPEC-MANAGER-INICIO.md`).
6. ~~Duas semanas lado a lado, depois o Base44 fica só leitura, depois desliga.~~ **Feito em 08/11/2026** — Marcus aprovou tirar o Base44 ("tudo aprovado, pode tirar"): o `webapp` antigo, o emulador web e o editor de HUD saíram do site (redirecionam para `/manager`); o app não o menciona mais. O app no Base44 pode ser pausado no painel de lá; os dados foram migrados em 06/11 (95 pessoas, 20 encontros, 3 coisas). Beto: decidir com o Marcus o que ele continua fazendo (só assistente no WhatsApp?).
