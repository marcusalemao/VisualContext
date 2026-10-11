# Manager — Home, first use and import (approved 11/10/2026)

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

Approved by Marcus on 11/10. Visual reference: the canvas "VisualContext Manager — Início" (three screens: phone home, phone settings, web Manager). It is built in the existing Manager, **visualcontext.base44.app** (Beto's builder app), not from scratch.

### 1. Home (phone and web)
- **Look:** the Nexus grammar: dark ground, green accent, 2 px strokes, generous spacing. Marcus asked for more breathing room than the first draft.
- **"Ask anything" in the middle, with a sparkle icon (✦).** While the user types, a box opens under the field:
  - first line: "✦ Ask your memory: '…'", which sends the question to the AI (`ask_memory`);
  - below: "Search in people / places / things / memories", each with how many items already match. Picking one filters the list and closes the box.
- **Catalog pills:** All · People · Places · Things · Memories, each with its count. The four modules are the same as the four main switches in Settings.
- **Views:** Timeline (Today, Yesterday, This week, Earlier) · Grid (visual cards) · A–Z.
- **Order:** Newest / Oldest. Hidden in A–Z.
- **Answer card:** the answer plus its source (which encounter, date, open follow-ups).
- **Web:** the same ask field on top; the catalog on the left and the selected item's detail on the right, in the HUD card format (photo, name, tags, hook, About, encounters with follow-ups). The two columns stack on a phone.

### 2. Settings
- **On the glasses:** the 4 main switches (Faces, Places, Things, Memories). No sub-settings.
- **Encounter recording:** "Needs speech" / "A look is enough".
- **AI:** plan (Community with your own keys, or Plus), provider and key.
- **Privacy:** sensitive-situation guards (on; only developer mode turns them off), how long to keep things, "Forget everything".
- **Device:** glasses connected, device token (masked, "Change"), language.

### 3. First use: empty, with a well-designed empty state
- **No sample records mixed with real ones.** Sample data would confuse answers ("who is Rafael?"), add fake faces to recognition, and leave cleanup work.
- **Welcome with 3 steps:** wear the glasses and talk to someone · say "remember this" about a thing · ask here anything.
- **Shortcuts:** "Import contacts" and "Create your profile (Me)", so the app knows who the wearer is.
- **"See it with sample data":** opens a separate, clearly labeled demo mode that never mixes with the real memory.

### 4. Online demo and emulator
- **Fictional dataset only,** about 10–15 items across people, places, things and memories.
- **Marcus's 97 contacts never go to the demo.** They are real people from his LinkedIn, and they did not consent (LGPD). They stay in his account only.

### 5. Import contacts

| Source | How | Photo? |
|---|---|---|
| Phone contacts | Android contacts permission, one tap | Yes, when the contact has one (often from the Google account) |
| Google / Gmail | Sign in with Google (People API) | Yes, when the person has one. Skip the generic letter avatar (`default: true`) |
| LinkedIn | Official export: Settings → Data privacy → Get a copy → Connections (CSV) | No. Name, company, position, connection date; email only sometimes. LinkedIn has no API for connections |
| CSV / Excel | Upload; the app asks which column is name, company, etc. | No |
| vCard (.vcf) | File exported from iPhone, Outlook, etc. | Sometimes, embedded |
| WhatsApp | — | No: WhatsApp does not share profile photos with other apps |

**Photo rule (biometric data, LGPD):**
- An imported photo is for **display only** (card and Manager). The app never builds face templates from the address book.
- Face recognition starts only from a **real encounter**. When a new face appears, the app compares names and asks: "Is this Rafael Souza from your contacts?". On confirmation, the records merge.

---

## PT — Português

Aprovado pelo Marcus em 11/10. Referência visual: o canvas "VisualContext Manager — Início" (três telas: início no celular, ajustes no celular, Manager web). Vai ser construído no Manager que já existe, **visualcontext.base44.app** (o app do Beto no construtor), não do zero.

### 1. Início (celular e web)
- **Visual:** a gramática do Nexus: fundo escuro, verde de destaque, contornos de 2 px, espaçamento generoso. O Marcus pediu mais respiro que o primeiro rascunho.
- **"Pergunte qualquer coisa" no meio, com o ícone de estrelinhas (✦).** Enquanto o usuário digita, abre uma caixa embaixo do campo:
  - primeira linha: "✦ Perguntar à memória: '…'", que manda a pergunta para a IA (`ask_memory`);
  - embaixo: "Buscar em pessoas / lugares / coisas / lembranças", cada uma com quantos itens já batem. Escolher uma filtra a lista e fecha a caixa.
- **Pílulas de catálogo:** Todos · Pessoas · Lugares · Coisas · Lembranças, cada uma com a contagem. Os quatro módulos são os mesmos das quatro chaves principais dos Ajustes.
- **Visualização:** Linha do tempo (Hoje, Ontem, Esta semana, Antes) · Grade (cartões visuais) · A–Z.
- **Ordem:** Recentes / Antigos. Some no A–Z.
- **Card de resposta:** a resposta e a fonte (qual encontro, data, pendências).
- **Web:** a mesma pergunta no topo; o catálogo à esquerda e o detalhe do item escolhido à direita, no formato do card do HUD (foto, nome, tags, gancho, Sobre, encontros com pendências). As duas colunas se empilham no celular.

### 2. Ajustes
- **Nos óculos:** as 4 chaves principais (Rostos, Lugares, Coisas, Memórias). Sem subconfigurações.
- **Gravação de encontros:** "Precisa de fala" / "Só o olhar basta".
- **IA:** plano (Community com as suas chaves, ou Plus), provedor e chave.
- **Privacidade:** proteções em situações sensíveis (ligadas; só o modo desenvolvedor desliga), por quanto tempo guardar, "Esquecer tudo".
- **Aparelho:** óculos conectados, token do aparelho (mascarado, botão "Trocar"), idioma.

### 3. Primeiro uso: vazio, com um vazio bem desenhado
- **Nada de registros de exemplo misturados com os reais.** Dados de exemplo confundiriam as respostas ("quem é o Rafael?"), colocariam rostos falsos no reconhecimento e deixariam trabalho de limpeza.
- **Boas-vindas em 3 passos:** coloque os óculos e converse com alguém · diga "lembra disso" para uma coisa · pergunte aqui o que quiser.
- **Atalhos:** "Importar contatos" e "Criar o seu perfil (Eu)", para o app saber quem é você.
- **"Ver com dados de exemplo":** abre um modo de demonstração separado e marcado, que nunca se mistura com a memória real.

### 4. Demo online e emulador
- **Só dados fictícios,** uns 10–15 itens entre pessoas, lugares, coisas e lembranças.
- **Os 97 contatos do Marcus nunca vão para a demo.** São pessoas reais do LinkedIn dele, que não autorizaram (LGPD). Ficam só na conta dele.

### 5. Importar contatos

| Fonte | Como | Foto? |
|---|---|---|
| Agenda do celular | Permissão de contatos do Android, um toque | Sim, quando o contato tem (muitas vêm da conta Google) |
| Google / Gmail | Login com Google (People API) | Sim, quando a pessoa tem. Ignorar a letra genérica (`default: true`) |
| LinkedIn | Exportação oficial: Configurações → Privacidade de dados → Obter uma cópia → Conexões (CSV) | Não. Nome, empresa, cargo, data da conexão; e-mail só às vezes. O LinkedIn não tem API para conexões |
| CSV / Excel | Envio do arquivo; o app pergunta qual coluna é nome, empresa etc. | Não |
| vCard (.vcf) | Arquivo exportado de iPhone, Outlook etc. | Às vezes, embutida |
| WhatsApp | — | Não: o WhatsApp não compartilha fotos de perfil com outros apps |

**Regra das fotos (dado biométrico, LGPD):**
- A foto importada serve **só para mostrar** (card e Manager). O app nunca gera modelo de rosto a partir da agenda.
- O reconhecimento facial só nasce de um **encontro real**. Quando aparece um rosto novo, o app compara os nomes e pergunta: "É o Rafael Souza da sua agenda?". Com a confirmação, os registros se juntam.
