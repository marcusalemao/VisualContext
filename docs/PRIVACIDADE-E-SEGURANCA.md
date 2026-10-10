# Privacy & Security — VisualContext

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

Status: proposal (10/10), for Marcus to decide. Builds on the decisions already taken: everything is personal, the app locks when the glasses come off, unlock only on the phone, store little. It also draws on the Second Mind principles: privacy by design, local first, minimum data, portable memory, real deletion, and never acting as a judge of the user's truth.

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

**Recommended:** on by default, with the user able to turn each one off in Settings. Marcus's proposal was to let each person turn them on. Turning them off is the user's choice; starting with them on protects people who never open the settings.

| Situation | What happens | How it's detected |
|---|---|---|
| Bathroom, locker room, shower | Camera and microphone pause, pre-roll discarded | On-device image labels (toilet, urinal, shower) |
| Nudity / intimacy | Immediate pause, pre-roll discarded, nothing goes to the cloud | On-device nudity classifier |
| Children | Never enrolled automatically. A child's face is only saved by hand. | Age estimate under 18 |
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

- **Sensitive situations:** each one on or off, all on by default.
- **Retention:** transcript (7 / 30 / 90 days / forever), people without a name (7 / 14 / 30 days).
- **Cloud:** off / encrypted backup only / Pro.
- **AI:** on the phone (Gemini Nano) / my key / Pro.
- **Self-destruct:** panic phrase, inactivity, failed attempts.
- **Places where it never records.**
- **Export everything / erase everything.**

### 9. Note for later (store distribution)

Marcus chose not to focus on law now. When the app goes to stores worldwide, face data counts as biometric data in several places: GDPR art. 9 in Europe, BIPA in Illinois, LGPD in Brazil. The design above (local first, consent by eye contact, real deletion, nothing shared) already points the right way. It will only need formal text and an explicit consent screen.

---

## PT — Português

Status: proposta (10/10), para o Marcus decidir. Parte das decisões já tomadas: tudo é pessoal, o app trava quando os óculos saem do rosto, o desbloqueio é só pelo celular, e guarda-se pouco. Também se apoia nos princípios do Second Mind: privacidade desde o projeto, local primeiro, o mínimo de dados, memória portátil, exclusão de verdade e nunca agir como juiz da verdade de quem usa.

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

**Recomendação:** ligadas por padrão, e quem usa desliga cada uma nos ajustes. A proposta do Marcus era deixar cada pessoa ligar. Desligar continua sendo escolha de quem usa; começar ligado protege quem nunca abre os ajustes.

| Situação | O que acontece | Como é detectado |
|---|---|---|
| Banheiro, vestiário, chuveiro | Câmera e microfone pausam; a pré-gravação é descartada | Rótulos de imagem no aparelho (vaso, mictório, chuveiro) |
| Nudez / intimidade | Pausa imediata; pré-gravação descartada; nada vai para a nuvem | Classificador de nudez no aparelho |
| Crianças | Nunca cadastradas automaticamente. O rosto de uma criança só entra por cadastro manual. | Idade estimada abaixo de 18 |
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

- **Situações sensíveis:** cada uma liga/desliga, todas ligadas por padrão.
- **Retenção:** transcrição (7 / 30 / 90 dias / sempre), pessoas sem nome (7 / 14 / 30 dias).
- **Nuvem:** desligada / só backup criptografado / Pro.
- **IA:** no celular (Gemini Nano) / minha chave / Pro.
- **Autodestruição:** frase de pânico, inatividade, tentativas erradas.
- **Lugares onde nunca grava.**
- **Exportar tudo / apagar tudo.**

### 9. Nota para depois (distribuição nas lojas)

O Marcus escolheu não focar em leis agora. Quando o app for para lojas no mundo todo, dado de rosto conta como dado biométrico em vários lugares: GDPR art. 9 na Europa, BIPA em Illinois, LGPD no Brasil. O desenho acima (local primeiro, consentimento pelo contato visual, exclusão real, nada compartilhado) já aponta para o lado certo. Vai faltar só o texto formal e uma tela de consentimento explícita.
