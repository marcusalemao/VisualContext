# Rename plan: FaceContext → VisualContext (draft, nothing executed)

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

**Marcus's decision (11/10):** everything is called **VisualContext**. "Faces" becomes a module inside it, not a separate product.

### Proposed structure: one product, four modules
The modules are the same as the four main switches in Settings, so the names reinforce each other:

| Module | Switch | What it covers |
|---|---|---|
| `faces` | Faces | people, encounters, dossier, face recognition |
| `places` | Places | building cards, "you were here" |
| `things` | Things | motorbike, keys, "Registra isso" |
| `memories` | Memories | ask anything, timeline |

### Inventory of old names

| Where | Old name | New name | Risk |
|---|---|---|---|
| Domain | `fc.alema.io` | `visualcontext.alema.io` (fc stays as a 301 redirect) | low |
| GitHub | repo `Vision` (publishes fc.alema.io via Cloudflare Pages) | merge into `VisualContext` or rename | low — GitHub keeps redirects |
| Base44 app (Beto) | subdomain `facecontext-10a8c066.base44.app` | display name "VisualContext" | low |
| Backend functions | `fcProxy`, `fcRecognize`, `fcMessages` | `vcProxy`, `vcFacesRecognize`, `vcMessages` | medium — every client calls them by name |
| Database entities | `FaceContextPerson`, `FaceContextEncounter`, `FaceContextHUDLayout` | `faces.person`, `faces.encounter`, `hud.layout` (public names only) | **high** if migrated |
| Emulator / Manager / plugin | strings and URLs | already use VisualContext; only the URLs change | low |

### Claude's recommendation: rename what people see, alias what they don't
1. **Entities: do not migrate the database.** Base44 cannot rename an entity. Migrating means creating new ones, copying 96 people and 20 encounters, re-pointing everything, and deleting the old ones. That is the riskiest step there is, with zero benefit to the user, since nobody sees those names. Instead, fcProxy accepts public aliases (`faces.person` → `FaceContextPerson`). The aliases table already exists (`persons`, `encounters`). Clients move to the new names, and the internal ones become an implementation detail.
2. **Functions:** create `vcProxy` as a copy of `fcProxy`. Keep `fcProxy` working as a thin wrapper for a few weeks, move the clients (emulator, Manager, HUD Editor, plugin) one by one, then remove it.
3. **Domain:** publish at `visualcontext.alema.io`. `fc.alema.io` becomes a 301 redirect, so old links still work.
4. **Repo:** move the Manager and HUD Editor from `Vision` into `VisualContext/web` (it already has a copy, but an older one). Point Cloudflare Pages at it, then archive `Vision`.

### Order (each step reversible, one at a time, with a test after each)
aliases in fcProxy → new domain + redirect → `vcProxy` + clients → repo/Pages → remove `fcProxy` wrapper.

### Who does what
- **Beto:** aliases and `vcProxy` in the backend; Base44 app name.
- **Claude:** emulator, Manager and plugin clients; repo; plan and tests.
- **Marcus:** DNS (Porkbun) and Cloudflare Pages; approving each step.

---

## PT — Português

**Decisão do Marcus (11/10):** tudo se chama **VisualContext**. "Faces" (rostos) vira um módulo dentro dele, não um produto separado.

### Estrutura proposta: um produto, quatro módulos
Os módulos são os mesmos das quatro chaves principais dos Ajustes, então os nomes se reforçam:

| Módulo | Chave | O que cobre |
|---|---|---|
| `faces` | Rostos | pessoas, encontros, dossiê, reconhecimento |
| `places` | Lugares | cards de prédios, "você esteve aqui" |
| `things` | Coisas | moto, chave, "Registra isso" |
| `memories` | Memórias | pergunte qualquer coisa, linha do tempo |

### Inventário dos nomes antigos

| Onde | Nome antigo | Nome novo | Risco |
|---|---|---|---|
| Domínio | `fc.alema.io` | `visualcontext.alema.io` (o fc vira redirecionamento 301) | baixo |
| GitHub | repo `Vision` (publica o fc.alema.io pela Cloudflare Pages) | juntar no `VisualContext` ou renomear | baixo — o GitHub mantém redirecionamentos |
| App do Beto no Base44 | subdomínio `facecontext-10a8c066.base44.app` | nome de exibição "VisualContext" | baixo |
| Funções do backend | `fcProxy`, `fcRecognize`, `fcMessages` | `vcProxy`, `vcFacesRecognize`, `vcMessages` | médio — todo cliente chama pelo nome |
| Entidades do banco | `FaceContextPerson`, `FaceContextEncounter`, `FaceContextHUDLayout` | `faces.person`, `faces.encounter`, `hud.layout` (só os nomes públicos) | **alto** se migrar |
| Emulador / Manager / plugin | textos e endereços | já usam VisualContext; mudam só os endereços | baixo |

### Recomendação do Claude: renomear o que se vê, apelidar o que não se vê
1. **Entidades: não migrar o banco.** O Base44 não renomeia entidade. Migrar significa criar entidades novas, copiar 96 pessoas e 20 encontros, apontar tudo de novo e apagar as antigas. É o passo mais arriscado que existe, sem nenhum ganho para o usuário, porque ninguém vê esses nomes. No lugar disso, o fcProxy aceita apelidos públicos (`faces.person` → `FaceContextPerson`). A tabela de apelidos já existe (`persons`, `encounters`). Os clientes passam a usar os nomes novos, e os internos viram detalhe de implementação.
2. **Funções:** criar `vcProxy` como cópia do `fcProxy`. O `fcProxy` continua funcionando por algumas semanas como um repasse fino, os clientes migram um a um (emulador, Manager, HUD Editor, plugin), e depois ele sai.
3. **Domínio:** publicar em `visualcontext.alema.io`. O `fc.alema.io` vira redirecionamento 301, então links antigos continuam funcionando.
4. **Repo:** trazer o Manager e o HUD Editor do `Vision` para `VisualContext/web` (lá já existe uma cópia, só que mais antiga). Apontar a Cloudflare Pages para ele e depois arquivar o `Vision`.

### Ordem (cada passo reversível, um de cada vez, com teste depois de cada um)
apelidos no fcProxy → domínio novo + redirecionamento → `vcProxy` + clientes → repo/Pages → remover o repasse `fcProxy`.

### Quem faz o quê
- **Beto:** apelidos e `vcProxy` no backend; nome do app no Base44.
- **Claude:** clientes do emulador, do Manager e do plugin; repo; plano e testes.
- **Marcus:** DNS (Porkbun) e Cloudflare Pages; aprovar cada passo.
