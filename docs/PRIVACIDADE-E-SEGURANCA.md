# Privacy & Security — VisualContext

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

Status: proposal (10/10). Decided: privacy by default; the most sensitive guards only in Developer mode. Builds on the decisions already taken: everything is personal, the app locks when the glasses come off, unlock only on the phone, store little. It also draws on the Second Mind principles: privacy by design, local first, minimum data, portable memory, real deletion, and never acting as a judge of the user's truth.

---

## EN — English

### 1. Manifesto: memory is yours

1. **The right to remember.** Memory is an extension of you, not a product about you. VisualContext exists to give back what you lived, not to watch it.
2. **The right to forget.** Forgetting is part of memory. You can forget a moment, a person, or everything, and forgetting is real: the data is destroyed, not hidden. Those who appear in your memory are protected too: nobody becomes a record just for passing by.
3. **Subjectivity.** VisualContext never judges your experience. It doesn't say "that's not what happened". It offers what was recorded, with its source ("you said this on 30/09"), and you decide what it means.
4. **The minimum.** Text, not audio. One face crop, not video. What isn't kept can't leak.
5. **Only yours.** Off your face, it locks. On someone else's face, it doesn't work. No one at VisualContext reads your memory, and that holds for us too.
6. **Portable.** You can take your memory with you (export) or erase it whole, at any time.

### 2. Threat model (what we protect against)

| Risk | Protection |
|---|---|
| Someone else puts on the glasses | Locks when taken off (`GLASSES_WORN`). Unlock only on the phone with fingerprint, face or strong password. |
| Glasses lost or stolen | Glasses keep **nothing** persistent: the 30 s pre-roll lives in RAM, records live on the phone. |
| Phone lost or stolen | Database encrypted, key in the Android Keystore (hardware, StrongBox when available), opened only with biometrics. Remote wipe from the Manager. |
| Our server is breached (Pro/cloud) | Cloud copy is encrypted on the phone before upload (zero-knowledge). The server only sees ciphertext. |
| The AI provider | With your own key, the phone calls the provider directly. With Pro, the text is processed in memory and not stored. Use providers and options that don't train on your data. |
| Forced access / coercion | Panic word or action wipes the keys (crypto-shredding). An optional decoy unlock opens an empty memory. |
| A curious person nearby | No colored alerts, minimal HUD, nothing readable from outside, card at most 70% of the width. |

### 3. Encryption and storage

- **Glasses:** RAM only. No file is ever written: no pre-roll, frames or transcript.
- **Transport glasses ↔ phone:** Nexus link (SPP authenticated, integrity and replay protection) plus our own end-to-end layer (libsodium) on top. Wi-Fi Direct for stills, same encryption.
- **Phone:** SQLCipher (AES-256) database; the key is wrapped by the Keystore and bound to biometric unlock. Face embeddings are encrypted like everything else.
- **Cloud (optional, Pro):** client-side encryption; the server stores ciphertext and logs usage, nothing readable.
- **Updated 11/10:** the user picks where memory lives (default: their own Google Drive), and the glasses now keep a small encrypted cache for offline use. See section 10; two points there are still open.
- **Crypto-shredding:** every person and every encounter has its own key. Deleting the key = deleting the data, including from backups.

### 4. How long things are kept (defaults, all adjustable)

| What | Default |
|---|---|
| Pre-roll (last ~30 s before an encounter) | RAM only, discarded unless an encounter opens |
| Audio | **Never stored**. Only text. |
| Full camera frames | **Never stored**. Only the square face crop and, for objects, one light still. |
| Full transcript of an encounter | 30 days, then only summary, hook and dossier remain |
| Person without a name, never seen again | Erased after 14 days |
| Anonymous faces of an Event (group) | Erased after 7 days unless named |
| Named people and their dossier | Until you erase them |

### 5. Self-destruct

- **Panic:** a phrase or a long action on the phone erases the keys at once. Everything becomes unreadable.
- **Inactivity:** if the phone app isn't unlocked for N days (you choose, off by default), the keys are erased.
- **Failed unlocks:** after N wrong attempts, the keys are erased.
- **Remote:** "erase everything" from the Manager on another device.
- **Selective:** by voice, "forget the last 5 minutes", "forget this conversation", "forget [person]".

### 6. Sensitive situations (guardrails)

**Privacy by default (decided 10/10):** everything starts on.
- **Lighter guards** (health, chosen places, card masking) can be turned off in normal Settings.
- **The most sensitive guards** (bathroom, sexual intimacy, automatic enrollment of children) can only be turned off in **Developer mode**, following the Rokid Nexus pattern: a hidden switch that reveals advanced details. They exist there for testing.
  - A guard turned off in Developer mode **turns itself back on after 1 hour**.
  - While any guard is off, the status line shows **DEV**, so you never forget.

| Situation | What happens | How it's detected |
|---|---|---|
| Bathroom, locker room, shower | Camera and microphone pause, pre-roll discarded | On-device image labels (toilet, urinal, shower) |
| Nudity (a child at the pool, the beach) | The moment is kept **as text only**: a neutral scene description ("the kids running around the pool"), with no body or sexual detail. **No image of that moment is ever saved.** | On-device nudity classifier |
| Sexual intimacy | Immediate pause, pre-roll discarded, nothing recorded | Same classifier + context |
| Children | Moments with children are recorded like any other. Only **automatic face enrollment** of a child is blocked: your own children you add once by hand ("esse é o meu filho"), and a stranger's child at the park never becomes a record. | Age estimate under 18 |
| Cards, passwords, documents on screen or paper | Numbers are masked before saving ("Registra isso", OCR) | Card/password/ID patterns |
| Health, clinic, therapy | Option: keep only locally, never send to cloud AI | Place (calendar/GPS) or topic in the conversation |
| Places you choose | Never record at home, at the doctor's, in a temple, etc. | Your geofences |
| Manual | "Pausa", a gesture, or taking the glasses off | You |

- **Detection runs on the phone, never in the cloud.**
- **A paused moment leaves no trace,** except the note "paused (privacy)" in the timeline if you want it.

### 7. Respect for the people around you

- **Nobody is enrolled for passing by:** an encounter needs eye contact (and speech, by default).
- **Strangers aren't identified:** there is no search of faces outside your own memory, no internet lookup.
- **Visible capture:** if the glasses have a recording light, VisualContext never turns it off.

### 8. Settings (Privacy page in the app)

- **Sensitive situations:** all on by default. Light ones toggle here; bathroom, sexual intimacy and automatic enrollment of children only in Developer mode (they return after 1 h).
- **Retention:** transcript (7 / 30 / 90 days / forever), people without a name (7 / 14 / 30 days).
- **Where memory is kept:** my Google Drive (default) / Dropbox / OneDrive / only on this phone / Plus. Always encrypted on the phone first.
- **AI:** on the phone (Gemini Nano) / my key / Pro.
- **Self-destruct:** panic phrase, inactivity, failed attempts.
- **Places where it never records.**
- **Export everything / erase everything.**

### 9. Note for later (store distribution)

Marcus chose not to focus on law now. When the app goes to stores worldwide, face data counts as biometric data in several places: GDPR art. 9 in Europe, BIPA in Illinois, LGPD in Brazil. The design above (local first, consent by eye contact, real deletion, nothing shared) already points the right way. It will only need formal text and an explicit consent screen.

### 10. Offline use and where memory lives (decided 11/10)

**Where memory lives.** Chosen once in setup.
- **Community:** the user's own cloud. Default: Google Drive; also Dropbox, OneDrive, or only on the phone. The app creates a folder there and encrypts everything on the phone before upload, so not even the cloud provider can read it. No server of ours in the path. (iCloud has no open API for Android or web apps; only with a native iOS app someday.)
- **Plus:** we host it, ready to use, with backup and sync, encrypted the same way.
- A cloud drive is good for files (face photos, backups, export), not for fast search. **The search index lives on the phone** (and, in Plus, also on our cloud). The glasses never search on their own: they ask through the phone or over Wi-Fi.

**What each mode can do.**

| | Phone nearby | Glasses on Wi-Fi, no phone | No connection |
|---|---|---|---|
| Card of a known person | Yes | Yes | Yes: the signatures of everyone are on the glasses |
| Unknown person | Recorded, described by AI | Recorded, described by AI in the cloud | Recorded with photo; described later |
| Expanded card (dossier, history) | Full | Full, a bit slower | Reduced: only what is on the glasses |
| Ask anything (search) | Yes | Yes, slower | No |
| Places | Yes (phone GPS) | No | No |
| The conversation itself | Yes | Yes | Open, see 10.4 |

Glasses on Wi-Fi without the phone need the glasses' own app (the Nexus plugin runs on the phone) and a way to write to the user's cloud from the glasses. To design.

**10.1 What the glasses keep (three levels).** Internal rules; the user never sees them.
1. **Always, for everyone:** the face signature (~1 KB each) and the short card (name, tags, hook). Thousands fit in a few MB, so recognition of known people works offline.
2. **Only recent people:** the face photo (~50 KB) and the latest encounters, up to a cap: for example the 200 most recent people or the last 30 days, whichever comes first. Past the cap, the oldest leaves the glasses and stays whole in the cloud. The number is calibrated in the real test.
3. **Never on the glasses:** the full history and transcripts.

**10.2 Offline queue.** What is captured without a connection waits on the glasses, with a fixed size limit and a HUD warning near it. It uploads through whichever link comes back first (Wi-Fi or phone). An item is deleted from the glasses only after the destination confirms it arrived.

**10.3 Places needs the phone.** Checked on the glasses on 11/10: the location screen only offers Wi-Fi and Bluetooth scanning, and published specs list no GPS. Almost certainly no GPS chip; final proof is `adb shell pm list features | grep -i location`. Wi-Fi positioning is tens of meters at best (and may not work at all without a location provider), and there is no compass. Building cards need the phone's GPS. The neighborhood map can be cached; the position cannot.

**10.4 Open: two conflicts with sections 3 and 4 (Marcus decides).**
- **a) The glasses used to keep nothing on disk (section 3).** Offline use needs signatures, cards and the queue on the glasses. Proposal: encrypted on the glasses; the key lives only in RAM while the glasses are worn; taking them off locks it, and the next phone unlock opens it again. Result: offline works for as long as you keep the glasses on.
- **b) "Audio is never stored" (section 4).** Without a connection, the conversation can't be transcribed, unless the glasses can do it on their own (to test). Options: (1) keep the rule: offline encounters keep face, time and photo, no conversation; (2) exception: encrypted audio waits on the glasses until it is transcribed, at most 24 h, then is deleted. Recommendation: (1), and test transcription on the glasses.

**10.5 What the user sees.** Kept simple: "Works offline. When you connect, the rest fills in." In daily use, nothing; if something is missing offline, the card says "full memory when connected" instead of an error. Settings has one quiet line with "Learn more". The technical detail lives only in this privacy page.

---

## PT — Português

Status: proposta (10/10). Decidido: privacidade por padrão; as proteções mais sensíveis só no modo desenvolvedor. Parte das decisões já tomadas: tudo é pessoal, o app trava quando os óculos saem do rosto, o desbloqueio é só pelo celular, e guarda-se pouco. Também se apoia nos princípios do Second Mind: privacidade desde o projeto, local primeiro, o mínimo de dados, memória portátil, exclusão de verdade e nunca agir como juiz da verdade de quem usa.

### 1. Manifesto: a memória é sua

1. **Direito de lembrar.** A memória é uma extensão de você, não um produto sobre você. O VisualContext existe para devolver o que você viveu, não para vigiar.
2. **Direito ao esquecimento.** Esquecer faz parte da memória. Você pode esquecer um momento, uma pessoa ou tudo, e o esquecimento é real: o dado é destruído, não escondido. Quem aparece na sua memória também é protegido: ninguém vira registro só por ter passado perto.
3. **Subjetividade.** O VisualContext nunca julga a sua experiência. Ele não diz "não foi assim que aconteceu". Ele oferece o que foi registrado, com a fonte ("você disse isso em 30/09"), e quem decide o que aquilo significa é você.
4. **O mínimo.** Texto, não áudio. Uma foto do rosto, não vídeo. O que não é guardado não vaza.
5. **Só seu.** Fora do seu rosto, trava. No rosto de outra pessoa, não funciona. Ninguém no VisualContext lê a sua memória, e isso vale para nós também.
6. **Portátil.** Você pode levar a sua memória com você (exportar) ou apagar tudo, quando quiser.

### 2. Do que nos protegemos

| Risco | Proteção |
|---|---|
| Outra pessoa coloca os óculos | Trava ao tirar (`GLASSES_WORN`). Desbloqueio só no celular, com digital, rosto ou senha forte. |
| Óculos perdidos ou roubados | Os óculos não guardam **nada** permanente: a pré-gravação de 30 s fica só na memória RAM, e os registros ficam no celular. |
| Celular perdido ou roubado | Banco criptografado, chave no cofre de hardware do Android (StrongBox quando houver), aberto só com biometria. Apagamento remoto pelo Manager. |
| Invasão do nosso servidor (Pro/nuvem) | A cópia na nuvem é criptografada no celular antes de subir (conhecimento zero). O servidor só vê dado cifrado. |
| O provedor de IA | Com chave própria, o celular chama o provedor direto. No Pro, o texto é processado em memória e não é guardado. Usar provedores e opções que não treinam com os seus dados. |
| Acesso forçado / coação | Uma palavra ou ação de pânico apaga as chaves (destruição criptográfica). Um desbloqueio falso, opcional, abre uma memória vazia. |
| Um curioso por perto | Sem alertas coloridos, HUD mínimo, nada legível de fora, card com no máximo 70% da largura. |

### 3. Criptografia e armazenamento

- **Óculos:** só memória RAM. Nenhum arquivo é gravado: nem pré-gravação, nem imagem, nem transcrição.
- **Transporte óculos ↔ celular:** link do Nexus (SPP autenticado, com integridade e proteção contra repetição) e, por cima, a nossa própria camada de ponta a ponta (libsodium). Wi‑Fi Direct para as fotos, com a mesma criptografia.
- **Celular:** banco SQLCipher (AES-256); a chave é protegida pelo cofre do Android e presa ao desbloqueio biométrico. Os dados de reconhecimento facial são criptografados como todo o resto.
- **Nuvem (opcional, Pro):** criptografia no celular; o servidor guarda dado cifrado e registra uso, nada legível.
- **Atualizado em 11/10:** o usuário escolhe onde a memória fica (padrão: o próprio Google Drive), e os óculos passam a guardar um pequeno cache criptografado para funcionar offline. Ver a seção 10; dois pontos ali ainda estão em aberto.
- **Destruição criptográfica:** cada pessoa e cada encontro têm a própria chave. Apagar a chave = apagar o dado, inclusive dos backups.

### 4. Por quanto tempo se guarda (padrões, todos ajustáveis)

| O quê | Padrão |
|---|---|
| Pré-gravação (~30 s antes do encontro) | Só na memória RAM, descartada se não abrir encontro |
| Áudio | **Nunca é guardado**. Só texto. |
| Imagem inteira da câmera | **Nunca é guardada**. Só a foto quadrada do rosto e, para objetos, uma foto leve. |
| Transcrição completa do encontro | 30 dias; depois ficam só resumo, gancho e dossiê |
| Pessoa sem nome que nunca mais apareceu | Apagada depois de 14 dias |
| Rostos anônimos de um Evento (grupo) | Apagados depois de 7 dias, se não forem nomeados |
| Pessoas com nome e o dossiê delas | Até você apagar |

### 5. Autodestruição

- **Pânico:** uma frase ou uma ação longa no celular apaga as chaves na hora. Tudo fica ilegível.
- **Inatividade:** se o app do celular não for desbloqueado por N dias (você escolhe; desligado por padrão), as chaves são apagadas.
- **Tentativas erradas:** depois de N tentativas de desbloqueio erradas, as chaves são apagadas.
- **Remota:** "apagar tudo" pelo Manager, de outro aparelho.
- **Seletiva:** por voz, "esquece os últimos 5 minutos", "esquece essa conversa", "esquece o [pessoa]".

### 6. Situações sensíveis (proteções)

**Privacidade por padrão (decidido em 10/10):** tudo começa ligado.
- **Proteções mais leves** (saúde, lugares escolhidos, máscara de cartão) se desligam nos Ajustes normais.
- **As proteções mais sensíveis** (banheiro, intimidade sexual, cadastro automático de crianças) só se desligam no **modo desenvolvedor**, no padrão do Rokid Nexus: uma chave escondida que revela detalhes avançados. Elas ficam lá para testes.
  - Uma proteção desligada no modo desenvolvedor **volta a ligar sozinha depois de 1 hora**.
  - Enquanto alguma estiver desligada, a linha de status mostra **DEV**, para ninguém esquecer.

| Situação | O que acontece | Como é detectado |
|---|---|---|
| Banheiro, vestiário, chuveiro | Câmera e microfone pausam; a pré-gravação é descartada | Rótulos de imagem no aparelho (vaso, mictório, chuveiro) |
| Nudez (criança na piscina, na praia) | O momento fica **só como texto**: uma descrição neutra da cena ("as crianças correndo em volta da piscina"), sem nenhum detalhe de corpo ou sexual. **Nenhuma imagem desse momento é guardada.** | Classificador de nudez no aparelho |
| Intimidade sexual | Pausa imediata, pré-gravação descartada, nada é registrado | Mesmo classificador + contexto |
| Crianças | Momentos com crianças são registrados como qualquer outro. Só o **cadastro automático do rosto** de uma criança é bloqueado: os seus filhos você cadastra uma vez, à mão ("esse é o meu filho"), e o filho de um estranho no parque nunca vira registro. | Idade estimada abaixo de 18 |
| Cartões, senhas, documentos na tela ou no papel | Os números são mascarados antes de salvar ("Registra isso", OCR) | Padrões de cartão, senha e documento |
| Saúde, clínica, terapia | Opção: guardar só no aparelho, nunca mandar para a IA na nuvem | Local (agenda/GPS) ou assunto da conversa |
| Lugares que você escolher | Nunca grava em casa, no médico, no templo etc. | As suas cercas geográficas |
| Manual | "Pausa", um gesto, ou tirar os óculos | Você |

- **A detecção roda no celular, nunca na nuvem.**
- **Um momento pausado não deixa rastro,** a não ser a anotação "pausado (privacidade)" na linha do tempo, se você quiser.

### 7. Respeito a quem está em volta

- **Ninguém é cadastrado por passar perto:** o encontro exige contato visual (e fala, no padrão).
- **Estranhos não são identificados:** não existe busca de rostos fora da sua própria memória, nem consulta na internet.
- **Captura visível:** se os óculos tiverem luz de gravação, o VisualContext nunca a desliga.

### 8. Ajustes (página Privacidade no app)

- **Situações sensíveis:** todas ligadas por padrão. As leves se desligam aqui; banheiro, intimidade sexual e cadastro automático de crianças só no modo desenvolvedor (voltam depois de 1 h).
- **Retenção:** transcrição (7 / 30 / 90 dias / sempre), pessoas sem nome (7 / 14 / 30 dias).
- **Onde a memória fica:** meu Google Drive (padrão) / Dropbox / OneDrive / só neste celular / Plus. Sempre criptografada no celular antes.
- **IA:** no celular (Gemini Nano) / minha chave / Pro.
- **Autodestruição:** frase de pânico, inatividade, tentativas erradas.
- **Lugares onde nunca grava.**
- **Exportar tudo / apagar tudo.**

### 9. Nota para depois (distribuição nas lojas)

O Marcus escolheu não focar em leis agora. Quando o app for para lojas no mundo todo, dado de rosto conta como dado biométrico em vários lugares: GDPR art. 9 na Europa, BIPA em Illinois, LGPD no Brasil. O desenho acima (local primeiro, consentimento pelo contato visual, exclusão real, nada compartilhado) já aponta para o lado certo. Vai faltar só o texto formal e uma tela de consentimento explícita.

### 10. Uso offline e onde a memória fica (decidido em 11/10)

**Onde a memória fica.** Escolhido uma vez, na configuração inicial.
- **Community:** a nuvem do próprio usuário. Padrão: Google Drive; também Dropbox, OneDrive, ou só no celular. O app cria uma pasta lá e criptografa tudo no celular antes de subir, então nem o provedor da nuvem consegue ler. Nenhum servidor nosso no caminho. (O iCloud não tem API aberta para apps Android ou web; só com um app nativo de iOS, se um dia existir.)
- **Plus:** nós hospedamos, pronto para usar, com backup e sincronização, criptografado do mesmo jeito.
- Uma nuvem de arquivos é boa para arquivos (fotos de rosto, backup, exportação), não para busca rápida. **O índice de busca fica no celular** (e, no Plus, também na nossa nuvem). Os óculos nunca buscam sozinhos: perguntam pelo celular ou pelo Wi-Fi.

**O que cada modo faz.**

| | Celular por perto | Óculos no Wi-Fi, sem celular | Sem conexão |
|---|---|---|---|
| Card de um conhecido | Sim | Sim | Sim: as assinaturas de todos ficam nos óculos |
| Desconhecido | Registrado, descrito pela IA | Registrado, descrito pela IA na nuvem | Registrado com foto; descrito depois |
| Card expandido (dossiê, histórico) | Completo | Completo, um pouco mais lento | Reduzido: só o que está nos óculos |
| Pergunte qualquer coisa (busca) | Sim | Sim, mais lento | Não |
| Lugares | Sim (GPS do celular) | Não | Não |
| A conversa em si | Sim | Sim | Em aberto, ver 10.4 |

Óculos no Wi-Fi sem celular pedem o app próprio dos óculos (o plugin do Nexus roda no celular) e um jeito de os óculos escreverem na nuvem do usuário. A desenhar.

**10.1 O que os óculos guardam (três níveis).** Regras internas; o usuário nunca vê.
1. **Sempre, de todos:** a assinatura do rosto (~1 KB cada) e o card curto (nome, tags, gancho). Milhares cabem em poucos MB, então o reconhecimento de conhecidos funciona offline.
2. **Só das pessoas recentes:** a foto do rosto (~50 KB) e os últimos encontros, até um teto: por exemplo as 200 pessoas mais recentes ou os últimos 30 dias, o que vier primeiro. Passou do teto, a mais antiga sai dos óculos e continua inteira na nuvem. O número é calibrado no teste real.
3. **Nunca nos óculos:** o histórico completo e as transcrições.

**10.2 Fila offline.** O que é capturado sem conexão espera nos óculos, com um limite fixo de espaço e um aviso no visor perto dele. Sobe pelo primeiro caminho que voltar (Wi-Fi ou celular). Um item só é apagado dos óculos depois que o destino confirma que recebeu.

**10.3 Lugares precisa do celular.** Conferido nos óculos em 11/10: a tela de localização só oferece busca por Wi-Fi e Bluetooth, e as especificações publicadas não citam GPS. Quase certamente não há chip de GPS; a prova final é `adb shell pm list features | grep -i location`. A posição por Wi-Fi erra dezenas de metros no melhor caso (e pode nem funcionar sem um provedor de localização), e não há bússola. Os cards de prédios precisam do GPS do celular. O mapa do bairro dá para guardar; a posição, não.

**10.4 Em aberto: dois conflitos com as seções 3 e 4 (o Marcus decide).**
- **a) Os óculos não guardavam nada em disco (seção 3).** O uso offline precisa de assinaturas, cards e da fila nos óculos. Proposta: criptografados nos óculos; a chave fica só na RAM enquanto os óculos estão no rosto; tirar os óculos trava, e o próximo desbloqueio no celular abre de novo. Resultado: o offline funciona enquanto você não tirar os óculos.
- **b) "Áudio nunca é guardado" (seção 4).** Sem conexão, a conversa não tem como ser transcrita, a não ser que os óculos transcrevam sozinhos (a testar). Opções: (1) manter a regra: encontros offline guardam rosto, hora e foto, sem a conversa; (2) exceção: o áudio criptografado espera nos óculos até ser transcrito, no máximo 24 h, e depois é apagado. Recomendação: (1), e testar a transcrição nos óculos.

**10.5 O que o usuário vê.** Simples: "Funciona offline. Quando você se conecta, o resto se completa." No dia a dia, nada; se faltar algo offline, o card diz "memória completa quando conectar" em vez de um erro. Nos Ajustes, uma linha discreta com "Saiba mais". O detalhe técnico fica só nesta página de privacidade.
