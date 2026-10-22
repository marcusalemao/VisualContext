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

### 1d. Recall mode on the glasses (11/10, revised)
- **Free speech, three intents.** No fixed command words: the AI reads the intent of what was said.
  - **Register a thing:** "this is my key", "this is my motorbike, remember it" → creates the thing (photos + signature), i.e. the automatic skill.
  - **Save where it is:** "key here", "parked here".
  - **Recall:** any question ("where…?", "what was the name…?").
  - "Remember" fits both register and ask; context decides (thing in hand or in front = register; a question = recall). If still unclear, the card asks back in one line.
- **MVP: only two things, key and motorbike.** More things come after these two work well.
- **Phone and web:** lists and cards show where and when, no live distance in the rows. Opening a place or thing shows the map and "Open in map" freely: the phone is the place to explore.
- **Glasses, Recall mode:** for any question, only in standby (no encounter being recorded): "What was the name of the person I just talked to?", "What was the address of the building I visited?", "Where did I leave my motorbike?". A fuller card appears, shaped by the answer: a person → the face card; a place → photo + address; a thing → last photos + where. The map button appears only when the answer is a location. Each memory keeps the location of the moment it was recorded, so a place answer works even with the day timeline off. Example, the motorbike:
  - the last frame where the motorbike was seen **parked** (not a frame from riding, with hands on the handlebar);
  - address and time ("Rua Paim, 210 · today 8:30");
  - one action: "See on map".
- If an encounter starts, the card steps aside and comes back after.
- **The right photo comes from the parking moment, not from a search later.** When the phone's speed drops from riding to zero and then walking, save the location and take a short burst. A voice command ("save: parked here") does the same by hand.
- **At the parking moment, keep two photos:** one with the motorbike (if the signature finds it) and one of the surroundings, even without the bike: a shop front, a sign, the corner. The address alone is often not enough; the picture says "it was there". Signs and shop names in that photo are read (OCR) and added to the card: "Rua Paim, 210 · in front of Padaria Boa".
- **What "See on map" does, in order of preference:**
  1. our own arrow + distance on the HUD (head heading from the Places app + phone GPS); for a few hundred metres this beats turn-by-turn;
  2. hand the coordinates to a glasses navigation app (`geo:` intent), if one accepts it — to be tested: rokid-ar-navigation (voice, walking HUD), Rokid-GMaps;
  3. open Google Maps on the phone.
- Without the phone: address only, no distance (the glasses have no GPS).

### 1e. Things: register once, find always (11/10)
- **Register once, with the thing in hand.** "This is my key": a few photos from different angles, a short description and a visual signature (like the face signature). Hands mark that it is yours and that this is the teaching moment.
- **Answer = the last photo where it was seen.** "Where is my key?" shows that photo, the time, and where it was, as far as we know. Indoors GPS is useless and a transcript can't tell bed from wardrobe or fridge; the photo can, because you recognise your own bed.
- **Which house:** the Wi-Fi network name (home, office, a friend's) says which building, without GPS.
- **Which room (optional):** teach each room once ("this is the bedroom"); after that the scene says the room. Without it, the AI describes the spot ("on the bed", "inside the fridge").
- **Show the last 2–3 sightings**, not only one: the latest frame may be wrong, or the key moved after it.
- **Capture:** the camera can't stay on all day (heat, battery). Plan: a low-resolution frame every few seconds while the user moves around, checked on the phone against registered things only; frames with no registered thing are discarded at once; kept sightings expire after a few days. Plus a voice shortcut: "save: key here".
- **Today's code** (rvLearnSkill) learns from text only. It needs to accept photos and store the visual signature.

### 1f. Places you pass by: AR cards on the glasses (11/10)
- **On the glasses, always** (not a phone feature). This is the experience tested on 10/10 in the prototype `web/lugares.html`.
- Places nearby (MASP, a museum, a café) appear on their own, **up to 2 at a time**, inside the glasses' **24° field of view**, each card pinned to the building's direction while you turn your head.
- **Dotted outline = probably behind other buildings.**
- How: phone GPS + head heading + the map of the surroundings. No image analysis.
- Decoration, not a task. Seen places go to the timeline.
- Turned off with the Places switch in Settings. No sub-settings (fixed: 2 cards, 24°).
- **During an encounter, places are hidden.** The glasses show only the person card, kept short: name, how you know each other ("supplier · met at the Torra Fina offsite"), and when you last saw each other ("last time: 12/09, the budget"). Places come back when the encounter ends.
- **Later version:** if you keep looking at a place for a few seconds, the card expands with a web search: current exhibitions or, if there is nothing current, a short note about the building. Only the place name goes to the search, never the photo.

### 1g. Hint during a conversation (idea for testing, 11/10)
- When the other person asks you something and your memory has a fact that answers it, a small chip can appear: "✦ pilot starts in March (you said on 12/09)".
- Rules to keep distraction low:
  - **Pull, not push:** a small ✦ dot appears; one tap opens the chip. Nothing flies across the screen on its own.
  - **Only from memory**, with its source. No generic AI answers ("say you are proactive").
  - **Max one chip, a few words,** and it fades by itself.
  - **Off by default;** turned on per encounter or in Settings.
- Risks to test: eyes going to the HUD are noticed by the other person; the glasses mic favours the wearer, so the other person's question may be missed (phone mic helps).
- Job interviews: many companies treat live hidden assistance as cheating. Not a use case we promote.

### 1h. Every conversation is transcribed (decided 11/10, replaces the 30/09 rule)
- Any conversation is transcribed, known person or not. Someone unknown to the system may be someone you know who is not registered yet, or someone you are meeting now: that is how the people database grows.
- A transcript is discarded only if you cancel it (✕ on the countdown) or if the encounter was too short (the timer).
- The old rule "transcript only with a known person" is retired.
- **Person card during an encounter: always short** (decided 11/10): name, how you know each other, last time you met. No expanded version during the conversation, even with phone or Wi-Fi; the full card belongs to Recall mode.

### 1i. Audio-only mode, accessible from the start (12/10)
For blind and low-vision people, and for anyone who prefers no display. Same app, a setting: **Output: display · display + voice · voice only**.
- **Every card has a spoken version written with it** (one short sentence), not read out field by field. The data is the same; only the renderer changes (HUD image or Nexus `tts`).
- **Person (during an encounter):** a soft sound, then the short card: "Rafael Souza. Supplier, from the Torra Fina meeting. Last time, 12/09." Said once, when the face is recognised, before the conversation gets going, at low volume. Never repeated on its own.
- **Recall mode** works naturally by voice: ask, hear the answer. "Where is my key?" → "In the bedroom, on the bed, today at 9:10."
- **Places:** not narrated while walking (that would be noise). Only when asked: "Where am I?" / "What is around me?". Street-crossing and obstacles stay with Rokid's own Vision assistant; VisualContext is memory, not navigation.
- **Confirmations become sounds and voice:** new face → "New person. Keep?" (double tap cancels, like the ✕ on the countdown); recording → a short tone at start and end; saved → one tick.
- **Privacy of sound:** open-ear speakers can be heard up close. Default volume low; recommend Bluetooth earbuds for voice-only mode; never say names louder than a whisper level.
- First version: the spoken sentence is generated together with each card (same AI call that writes the headline), so nothing is built twice.

### 1j. Modules: one app, four modes (decided 22/10)
Like Rokid's own modes, VisualContext has **modules** chosen at first use and changed on the phone at any time. More than one can be on (a low-vision user may also want Business). Underneath it is the same engine: a module only changes the defaults — what is shown, what is spoken, what is kept.
- **The four switches stay in every module** (Marcus, 22/10): Faces/People, Places, Things and Memories can be turned on or off in any module; a module only sets their starting values.
- **General (default):** everyday memory — people, key, motorbike, places.
- **Business:** the person's dossier (company, what you talked about, open follow-ups), encounter summary in the Manager, next actions.
- **Low vision:** output "voice only" (1i) by default; "who is in front of me", whispered; reading signs and texts aloud; Gemini describing the surroundings and guiding the aim by voice ("turn a little to the right", like Google's Guided Vision). Never obstacle avoidance or navigation.
- **Deaf / hard of hearing:** live captions of the conversation on the lens, with the speaker's name when known; WhatsApp voice notes transcribed (Zap); bridge to the LIBRAS project.
- **Follow Rokid, don't fight it:** if the glasses are in Rokid's "Visual assistance" or "Hearing assistance" mode, suggest the matching module. Marcus to record those Rokid modes first (backlog).
- **Why now:** glasses for low vision today cost thousands of dollars (e.g. Envision) and Google's glasses are not out yet; Rokid is affordable and ships to Brazil. Accessibility can be the launch hook in the store, while memory stays the product for everyone. First tester: a relative with severe low vision; then an institution (e.g. Fundação Dorina Nowill).

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
- **Welcome with 3 steps:** wear the glasses and talk to someone · say "save this" about a thing · ask here anything.
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

### 1d. Modo Recordar nos óculos (11/10, revisado)
- **Fala livre, três intenções.** Sem palavras de comando fixas: a IA entende a intenção do que foi dito.
  - **Cadastrar uma coisa:** "essa é a minha chave", "essa é a minha moto, lembre dela" → cria a coisa (fotos + assinatura), ou seja, a skill automática.
  - **Guardar onde está:** "chave aqui", "parei aqui".
  - **Recordar:** qualquer pergunta ("onde…?", "qual era o nome…?").
  - "Lembre" serve para cadastrar e para perguntar; o contexto decide (coisa na mão ou na frente = cadastrar; pergunta = recordar). Se ainda ficar dúvida, o card pergunta de volta numa linha.
- **MVP: só duas coisas, chave e moto.** Outras coisas entram depois que essas duas funcionarem bem.
- **Celular e web:** listas e cards mostram onde e quando, sem distância ao vivo nas linhas. Ao abrir um lugar ou coisa, mapa e "Abrir no mapa" aparecem à vontade: o celular é o lugar de explorar.
- **Óculos, modo Recordar:** para qualquer pergunta, só em standby (sem encontro sendo gravado): "Qual o nome da pessoa com quem falei agora há pouco?", "Qual o endereço do prédio que visitei?", "Onde deixei minha moto?". Aparece um card mais completo, no formato da resposta: pessoa → o card do rosto; lugar → foto + endereço; coisa → últimas fotos + onde. O botão de mapa só aparece quando a resposta é um local. Cada memória guarda o local do momento em que foi registrada, então a resposta de lugar funciona mesmo com a linha do tempo do dia desligada. Exemplo, a moto:
  - o último quadro em que a moto foi vista **estacionada** (não um quadro pilotando, com as mãos no guidão);
  - endereço e hora ("Rua Paim, 210 · hoje 8:30");
  - uma ação: "Ver no mapa".
- Se um encontro começa, o card sai da frente e volta depois.
- **A foto certa vem da hora de estacionar, não de uma busca depois.** Quando a velocidade do celular cai de pilotando para zero e depois para caminhada, guarda o local e faz uma rajada curta. Um comando de voz ("guarda: parei aqui") faz o mesmo na mão.
- **Na hora de estacionar, guardar duas fotos:** uma com a moto (se a assinatura achar) e uma do entorno, mesmo sem a moto: a fachada de uma loja, uma placa, a esquina. Só o endereço muitas vezes não basta; a imagem diz "foi aí". Placas e nomes de loja nessa foto são lidos (OCR) e entram no card: "Rua Paim, 210 · em frente à Padaria Boa".
- **O que "Ver no mapa" faz, em ordem de preferência:**
  1. nossa própria seta + distância no HUD (direção da cabeça do app de Lugares + GPS do celular); para algumas centenas de metros é melhor que navegação curva a curva;
  2. passar as coordenadas para um app de navegação nos óculos (intent `geo:`), se algum aceitar — a testar: rokid-ar-navigation (voz, HUD a pé), Rokid-GMaps;
  3. abrir o Google Maps no celular.
- Sem o celular: só o endereço, sem distância (os óculos não têm GPS).

### 1e. Coisas: cadastrar uma vez, achar sempre (11/10)
- **Cadastro uma vez, com a coisa na mão.** "Essa é a minha chave": algumas fotos de ângulos diferentes, uma descrição curta e uma assinatura visual (como a do rosto). As mãos dizem que é sua e que aquele é o momento de ensinar.
- **Resposta = a última foto em que ela foi vista.** "Onde está minha chave?" mostra essa foto, a hora e onde estava, até onde sabemos. Dentro de casa o GPS não serve, e a transcrição não sabe se é a cama, o armário ou a geladeira; a foto sabe, porque você reconhece a sua cama.
- **Qual casa:** o nome da rede Wi-Fi (casa, escritório, casa de um amigo) diz em que lugar, sem GPS.
- **Qual cômodo (opcional):** ensinar cada cômodo uma vez ("esse é o quarto"); depois a cena diz o cômodo. Sem isso, a IA descreve o ponto ("em cima da cama", "dentro da geladeira").
- **Mostrar as 2–3 últimas vezes**, não só uma: o último quadro pode estar errado, ou a chave mudou de lugar depois.
- **Captura:** a câmera não pode ficar ligada o dia todo (calor, bateria). Plano: um quadro em baixa resolução a cada poucos segundos enquanto a pessoa se move, conferido no celular só contra as coisas cadastradas; quadro sem coisa cadastrada é descartado na hora; os avistamentos guardados expiram em poucos dias. Mais um atalho de voz: "guarda: chave aqui".
- **O código de hoje** (rvLearnSkill) aprende só por texto. Precisa aceitar fotos e guardar a assinatura visual.

### 1f. Lugares por onde você passa: cards de RA nos óculos (11/10)
- **Nos óculos, sempre** (não é recurso do celular). É a experiência testada em 10/10 no protótipo `web/lugares.html`.
- Lugares por perto (MASP, um museu, um café) aparecem sozinhos, **até 2 de cada vez**, dentro do **campo de visão de 24°** dos óculos, cada card preso à direção do prédio enquanto você vira a cabeça.
- **Contorno pontilhado = provavelmente atrás de outros prédios.**
- Como: GPS do celular + direção da cabeça + o mapa do entorno. Sem análise de imagem.
- Enfeite, não tarefa. Os lugares vistos vão para a linha do tempo.
- Desliga com a chave Lugares nos Ajustes. Sem subconfigurações (fixo: 2 cards, 24°).
- **Durante um encontro, os lugares somem.** Os óculos mostram só o card da pessoa, curto: nome, de onde vocês se conhecem ("fornecedor · conheceram no encontro da Torra Fina") e quando se viram pela última vez ("última vez: 12/09, o orçamento"). Os lugares voltam quando o encontro termina.
- **Versão futura:** se você fica olhando para um lugar por alguns segundos, o card se expande com uma busca na web: as exposições em cartaz ou, se não houver nada atual, uma nota curta sobre o prédio. Só o nome do lugar vai para a busca, nunca a foto.

### 1g. Cola durante a conversa (ideia para testar, 11/10)
- Quando a outra pessoa te pergunta algo e a sua memória tem um fato que responde, pode aparecer um chip pequeno: "✦ piloto começa em março (você disse em 12/09)".
- Regras para distrair pouco:
  - **Puxar, não empurrar:** aparece um pontinho ✦; um toque abre o chip. Nada voa pela tela sozinho.
  - **Só da memória,** com a fonte. Nada de resposta genérica de IA ("diga que você é proativo").
  - **No máximo um chip, poucas palavras,** e ele some sozinho.
  - **Desligado por padrão;** liga por encontro ou nos Ajustes.
- Riscos a testar: o olhar indo para o HUD é percebido pela outra pessoa; o microfone dos óculos favorece quem usa, então a pergunta da outra pessoa pode se perder (o microfone do celular ajuda).
- Entrevista de emprego: muitas empresas tratam ajuda escondida ao vivo como trapaça. Não é um uso que vamos promover.

### 1h. Toda conversa é transcrita (decidido em 11/10, substitui a regra de 30/09)
- Qualquer conversa é transcrita, com pessoa conhecida ou não. Alguém desconhecido para o sistema pode ser alguém que você conhece e ainda não cadastrou, ou alguém que você está conhecendo agora: é assim que o banco de pessoas cresce.
- A transcrição só é descartada se você cancelar (✕ no countdown) ou se o encontro for curto demais (o timer).
- A regra antiga "transcrição só com pessoa conhecida" sai.
- **Card da pessoa durante o encontro: sempre curto** (decidido em 11/10): nome, de onde se conhecem, última vez que se viram. Sem versão expandida durante a conversa, mesmo com celular ou Wi-Fi; o card completo é do modo Recordar.

### 1i. Modo só áudio, acessível desde o começo (12/10)
Para pessoas cegas e com baixa visão, e para quem prefere sem tela. Mesmo app, um ajuste: **Saída: tela · tela + voz · só voz**.
- **Cada card tem uma versão falada escrita junto** (uma frase curta), não lida campo por campo. O dado é o mesmo; só muda quem desenha (imagem no HUD ou `tts` do Nexus).
- **Pessoa (durante o encontro):** um som suave e o card curto: "Rafael Souza. Fornecedor, do encontro da Torra Fina. Última vez, 12/09." Dito uma vez, quando o rosto é reconhecido, antes de a conversa engrenar, em volume baixo. Nunca repete sozinho.
- **Modo Recordar** funciona naturalmente por voz: pergunta, ouve a resposta. "Onde está minha chave?" → "No quarto, em cima da cama, hoje às 9h10."
- **Lugares:** não são narrados enquanto você anda (seria barulho). Só quando pedir: "Onde estou?" / "O que tem em volta?". Atravessar a rua e obstáculos ficam com o assistente Vision da própria Rokid; o VisualContext é memória, não navegação.
- **Confirmações viram som e voz:** rosto novo → "Pessoa nova. Guardo?" (toque duplo cancela, como o ✕ do countdown); gravação → um tom curto no começo e no fim; salvo → um clique.
- **Privacidade do som:** o alto-falante aberto pode ser ouvido de perto. Volume baixo por padrão; recomendar fone Bluetooth no modo só voz; nunca falar nomes acima de um sussurro.
- Primeira versão: a frase falada é gerada junto com cada card (na mesma chamada de IA que escreve o gancho), para nada ser construído duas vezes.

### 1j. Módulos: um app, quatro modos (decidido em 22/10)
Como os modos da própria Rokid, o VisualContext tem **módulos**, escolhidos no primeiro uso e trocados no celular quando quiser. Dá para ligar mais de um (uma pessoa de baixa visão pode querer também o Business). Por baixo é o mesmo motor: o módulo só muda os padrões — o que aparece, o que é falado, o que é guardado.
- **As quatro chaves continuam em todos os módulos** (Marcus, 22/10): Pessoas, Lugares, Coisas e Lembranças podem ser ligadas ou desligadas em qualquer módulo; o módulo só define como elas começam.
- **Geral (padrão):** memória do dia a dia — pessoas, chave, moto, lugares.
- **Business:** o dossiê da pessoa (empresa, o que já conversaram, pendências), resumo do encontro no Manager, próximas ações.
- **Baixa visão:** saída "só voz" (1i) por padrão; "quem está na minha frente", dito baixinho; leitura de placas e textos em voz alta; o Gemini descrevendo o ambiente e orientando a mira por voz ("vire um pouco para a direita", como o Guided Vision do Google). Nunca desvio de obstáculo nem navegação.
- **Surdez:** legenda ao vivo da conversa na lente, com o nome de quem fala quando a pessoa é conhecida; áudios do WhatsApp transcritos (Zap); ponte com o projeto de LIBRAS.
- **Seguir a Rokid, não brigar:** se os óculos estão no modo "Assistência visual" ou "Assistência auditiva" da Rokid, sugerir o módulo correspondente. Antes, o Marcus grava esses modos da Rokid (backlog).
- **Por que agora:** óculos para baixa visão hoje custam milhares de dólares (ex.: Envision) e os óculos do Google ainda não saíram; a Rokid é acessível e entrega no Brasil. A acessibilidade pode ser o gancho de lançamento na loja, enquanto a memória continua sendo o produto para todo mundo. Primeiro testador: um parente com baixa visão grave; depois uma instituição (ex.: Fundação Dorina Nowill).

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
- **Boas-vindas em 3 passos:** coloque os óculos e converse com alguém · diga "guarda isso" para uma coisa · pergunte aqui o que quiser.
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
