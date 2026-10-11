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

### 1b. "My day" card (top of the Timeline view)
- Only in the Timeline view, at the top: a sparkle header "MY DAY · TODAY", 1–2 sentences telling the day, the stops with times (place, people, duration), and a count line (people · places · notes · follow-ups) with "See the day".
- **Built only from what is already recorded:** encounters, place cards seen, things, notes. Nothing extra is captured for it.
- **Route and times (like Google's timeline) are a separate step, opt-in:** asked once at first use ("Should VisualContext remember where you go?"), tied to the Places switch, not a sub-setting. Store stops ("Café da Paim, 12:40–13:30"), not the raw trail; on the device only; raw stops deleted after 30 days, the day summary kept.
- No automatic photos and no nightly notification: the card is there when the user wants it.

### 1c. Suggested questions (11/10)
- Under the ask field, a row of 3–4 dashed chips with ✦, like Read.ai and Scribbl: people often don't know what to ask.
- Examples: "How was my day yesterday?" · "Summary of my last encounter" · "Next steps for my calendar" · "Where did I leave the motorbike?"
- **They change with context:** morning → "What do I have today?"; after an encounter → "Summary of the encounter with Rafael"; open follow-ups → "What is still pending?". First use shows generic ones.
- Tapping a chip asks at once and shows the answer card with its source.
- **Action answers** (calendar, task, map) show one button per item ("Add to calendar", "Create task", "Open in map"). Nothing is created without that tap, keeping the rule that the glasses never act on their own.
- The chips disappear while the user types; then the scope box takes over.
- Visually distinct from the category pills: suggestion chips are dashed outlines; filter pills are solid.

### 1d. Distance and directions only when finding something (11/10)
- Lists, timeline and cards answer **where and when** ("theatre · Rua Paim · you passed by on 06/10"). They never show live distance or map buttons.
- Distance ("350 m from here, about 4 min on foot") and the walk action ("Walk to the motorbike", "Open in map") appear only in **finding mode**: the user asked where something is ("Where did I leave the motorbike?") or opened that thing or place on purpose.
- On the glasses, finding mode never opens during an encounter or conversation. If one starts, directions step aside and come back after.
- Distance needs the phone's location; without it, show only the address.

### 2. Settings
- **On the glasses:** the 4 main switches (Faces, Places, Things, Memories). No sub-settings.
- **Encounter recording:** "Needs speech" / "A look is enough".
- **AI:** plan (Community with your own keys, or Plus), provider and key.
- **Where memory is kept:** my Google Drive (default), Dropbox, OneDrive, only on this phone, or Plus. Asked once in setup.
- **Memory:** one quiet line, "Offline and online. The glasses keep the essentials to work without internet; the rest stays in your cloud." with "Learn more" (privacy page, section 10).
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

### 6. Offline, as the user sees it (11/10)
- **Promise:** "Works offline. When you connect, the rest fills in." No settings to choose what stays where.
- **Daily use:** nothing to do. If something is missing offline, the card shows "full memory when connected", never an error.
- **Search ("Ask anything") and the expanded card** need the phone or Wi-Fi. Offline, the glasses show the short card and the recent encounters.
- **Places needs the phone nearby** (the glasses have no GPS). Say it plainly in onboarding and next to the Places switch.
- Technical rules (what the glasses keep, caps, queue): privacy spec, section 10.

### 7. Built on the existing dashboard (11/10)
The current Manager is **visualcontext.base44.app/dashboard** (code in the private repo `visualcontext-app`; published version from 15/09). This Home evolves it instead of replacing it:
- Its 5 tabs map to the new pills: **Pessoas** = Person; **Coisas** and **Lugares** = EpisodicMemory split by its existing `category` (lugar → Lugares); **Lembranças** = encounters, interactions and "Registra isso" notes.
- **Skills** and **Notificações** move to Settings (configuration, not memory), so the four pills match the four main switches.
- Keep what works: memory cards with "Ver no mapa", the memory form, duplicate review, circular photo crop.
- Fix while at it: the "Óculos conectados" indicator is fake (it flips at random every 8 s) → show the real status or nothing; delete asks for confirmation; remove the leftover "Live Companion" name; one ask field instead of a separate text filter per tab.
- Publish only when the new Home is ready, after a checkpoint and a preview test.

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

### 1b. Card "Meu dia" (topo da Linha do tempo)
- Só na visualização Linha do tempo, no topo: cabeçalho com estrelinha "MEU DIA · HOJE", 1–2 frases contando o dia, as paradas com horário (lugar, pessoas, duração) e uma linha de contagem (pessoas · lugares · notas · pendências) com "Ver o dia".
- **Feito só com o que já foi registrado:** encontros, cards de lugares vistos, coisas, notas. Nada é capturado a mais para ele.
- **Trajeto e horários (como a linha do tempo do Google) são um passo à parte, opcional:** perguntado uma vez no primeiro uso ("Quer que o VisualContext lembre por onde você passa?"), ligado à chave Lugares, sem virar subconfiguração. Guardar paradas ("Café da Paim, 12:40–13:30"), não o rastro; só no aparelho; paradas brutas apagadas em 30 dias, o resumo do dia fica.
- Sem fotos automáticas e sem notificação à noite: o card está lá quando a pessoa quiser.

### 1c. Perguntas sugeridas (11/10)
- Abaixo do campo de pergunta, uma fileira de 3–4 pílulas tracejadas com ✦, como no Read.ai e no Scribbl: muitas vezes a pessoa não sabe o que perguntar.
- Exemplos: "Como foi meu dia ontem?" · "Resumo do último encontro" · "Próximos passos para a agenda" · "Onde deixei a moto?"
- **Mudam com o contexto:** de manhã → "O que tenho hoje?"; depois de um encontro → "Resumo do encontro com o Rafael"; com pendências abertas → "O que ficou pendente?". No primeiro uso aparecem as genéricas.
- Tocar numa pílula já pergunta e mostra o card de resposta com a fonte.
- **Respostas com ação** (agenda, tarefa, mapa) mostram um botão por item ("Pôr na agenda", "Criar tarefa", "Abrir no mapa"). Nada é criado sem esse toque, mantendo a regra de que os óculos nunca agem sozinhos.
- As pílulas somem enquanto a pessoa digita; aí entra a caixa de escopo.
- Visualmente diferentes das pílulas de categoria: as sugestões são contornos tracejados; os filtros são cheios.

### 1d. Distância e caminho só na hora de achar algo (11/10)
- Listas, linha do tempo e cards respondem **onde e quando** ("teatro · Rua Paim · você passou aqui em 06/10"). Nunca mostram distância ao vivo nem botão de mapa.
- A distância ("350 m daqui, uns 4 min a pé") e a ação de ir ("Ir até a moto", "Abrir no mapa") aparecem só no **modo achar**: a pessoa perguntou onde algo está ("Onde deixei a moto?") ou abriu aquela coisa ou lugar de propósito.
- Nos óculos, o modo achar nunca abre durante um encontro ou conversa. Se uma conversa começa, o caminho sai da frente e volta depois.
- A distância precisa da localização do celular; sem ela, mostra só o endereço.

### 2. Ajustes
- **Nos óculos:** as 4 chaves principais (Rostos, Lugares, Coisas, Memórias). Sem subconfigurações.
- **Gravação de encontros:** "Precisa de fala" / "Só o olhar basta".
- **IA:** plano (Community com as suas chaves, ou Plus), provedor e chave.
- **Onde a memória fica:** meu Google Drive (padrão), Dropbox, OneDrive, só neste celular, ou Plus. Perguntado uma vez na configuração inicial.
- **Memória:** uma linha discreta, "Offline e online. Os óculos guardam o essencial para funcionar sem internet; o resto fica na sua nuvem." com "Saiba mais" (página de privacidade, seção 10).
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

### 6. Offline, do jeito que o usuário vê (11/10)
- **Promessa:** "Funciona offline. Quando você se conecta, o resto se completa." Sem ajuste para escolher o que fica onde.
- **No dia a dia:** nada a fazer. Se faltar algo offline, o card mostra "memória completa quando conectar", nunca um erro.
- **A busca ("Pergunte qualquer coisa") e o card expandido** precisam do celular ou do Wi-Fi. Offline, os óculos mostram o card curto e os encontros recentes.
- **Lugares precisa do celular por perto** (os óculos não têm GPS). Dizer isso com clareza no primeiro uso e ao lado da chave Lugares.
- Regras técnicas (o que os óculos guardam, tetos, fila): spec de privacidade, seção 10.

### 7. Em cima do dashboard que já existe (11/10)
O Manager atual é o **visualcontext.base44.app/dashboard** (código no repositório privado `visualcontext-app`; versão publicada de 15/09). Esta tela inicial evolui ele em vez de substituir:
- As 5 abas viram as novas pílulas: **Pessoas** = Person; **Coisas** e **Lugares** = EpisodicMemory separada pela `category` que já existe (lugar → Lugares); **Lembranças** = encontros, interações e notas do "Registra isso".
- **Skills** e **Notificações** vão para Ajustes (são configuração, não memória), e as quatro pílulas ficam iguais às quatro chaves principais.
- Manter o que funciona: cards de memória com "Ver no mapa", o formulário de memória, a revisão de duplicados, o recorte circular da foto.
- Corrigir junto: o indicador "Óculos conectados" é falso (troca sozinho a cada 8 s) → mostrar o status real ou nada; apagar pede confirmação; tirar o nome antigo "Live Companion"; um campo de pergunta único em vez de um filtro de texto separado em cada aba.
- Publicar só quando a nova tela inicial estiver pronta, depois de um ponto de restauração e de um teste na prévia.
