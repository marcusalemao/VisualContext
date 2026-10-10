# analyze_encounter — prompt & contract

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

Source: Marcus's "Sistema de Identificação e Gestão de Perfis" (10/10), turned into a backend prompt plus a JSON contract. Owner of the backend action: **Beto** (fcProxy, Gemini 2.5 Flash, budget log). Consumers: the emulator and the phone app (`saveEncounter`), the glasses HUD and the Manager.

## EN — English

### Contract

The phone sends `POST fcProxy {action:"analyze_encounter", ...}` once per encounter, when it ends.

**Input:**

```json
{
  "action": "analyze_encounter",
  "lang": "pt",
  "transcript": "15:20: … one line per utterance (speech-to-text, no punctuation guaranteed)",
  "person": {
    "name": "Sem nome 3",
    "named": false,
    "look": "Mulher de ~25 anos, cabelo roxo",
    "tags": [],
    "dossier": "",
    "hook": ""
  },
  "previous_encounters": [ { "date": "…", "context": "…", "hook": "…" } ],
  "place": "Café Tal, Pinheiros"
}
```

**Output (JSON only):**

```json
{
  "name": "Marcelo Campos",
  "name_confidence": 0.92,
  "name_evidence": "\"Ah, então você é o Marcelo? Vou te gravar aqui, Marcelo Campos\"",
  "alias": "Celo",
  "tags": ["amigo", "café", "arte"],
  "hook": "a fofoca do relógio falso",
  "context": "1–2 sentences: what this encounter was about",
  "dossier": "≈4 short lines: who this person is to the user, updated with this encounter",
  "actions": ["send him the architect's contact"]
}
```

**Rules for the app:**

| Field | What the app does with it |
|---|---|
| `name` | Renames the person **only if** `name_confidence ≥ 0.8`, the person is still unnamed (or was named during this same encounter), and `name_evidence` is a literal quote from the transcript. |
| `tags` | Become the pills (person `tags`). |
| `hook` | Becomes card line 3 (`custom_fields.hook`) and is stored on the encounter. |
| `dossier` | Becomes the "ABOUT" block (`custom_fields.dossier`). |
| `context` | Becomes the encounter `context`. |

### Card ↔ fields

| Card line | Named person | Unnamed person | Field |
|---|---|---|---|
| 1 · title | Marcelo Campos | Mulher de ~25 anos, cabelo roxo | `name` / `custom_fields.look` |
| 2 · tags (2–3) | amigo · café · arte | bar · criativa | `tags` |
| 3 · occasion | a fofoca do relógio falso | a fofoca do relógio falso | `custom_fields.hook` |
| block · about (≈4 lines) | dossier | dossier | `custom_fields.dossier` |

### Claude's notes on the original brief

- **Tags never say "unknown".** The brief's example tag `desconhecida` contradicts the rule we fixed on 10/10: someone without a name is not unknown. The missing name is already visible in the title.
- **During a conversation, line 3 shows the hook of the previous encounter.** The current encounter is analysed only when it ends. That is also exactly what helps you mid-conversation ("last time: the fake-watch gossip").
- **The name needs evidence.** Without a literal quote and a confidence score, an LLM can invent a plausible name. The app only accepts quoted names.
- **The on-device rules stay.** The emulator and the phone already catch most introductions instantly, so the card updates live. This prompt is the safety net that runs at the end and also writes `tags`, `hook` and `dossier`.

### International (the Store is global)

The rules must not be Brazil-only, but they also don't need a version per country. Two layers:

1. **On the device (instant, offline):** short rules for the most common introductions in **Portuguese, English and Spanish**. Same idea as the Brazilian rules: the person in front, never a third party.
   - **English:** "I'm Mike", "Mike, nice to meet you", "Nice to meet you, Sarah", "You must be Sarah", "Sarah, right?", "This is my friend Tom", "It's Michael, but everyone calls me Mike". A name after mom, sister, boss… belongs to someone else.
   - **Spanish:** "me llamo", "soy Carlos", "mucho gusto, Ana", "Ana, mucho gusto", "encantada", "te presento a mi amigo Luis", "tú eres Ana, ¿no?".
   - Titles (Mr., Ms., Dr., Sr., Sra.) are not part of the name.
2. **The AI at the end (all languages):** the prompt below is language-neutral. It covers what rules can't:
   - **East Asia:** family name first; "Tanaka desu", 我叫/我是, 저는 … 입니다.
   - **Honorifics:** -san, -ssi, 先生, which are not part of the name.
   - **Titles instead of names:** "Manager Kim".
3. **Business cards (Japan, Korea, China, business everywhere):** often the name is never said, it is handed over on a card. The existing badge OCR also reads business cards. The printed name has the highest confidence of all sources.

Speech recognition follows the phone language. The AI receives `lang` and answers in that language.

### Culture & formality: detect from the conversation, never from the person

We never know in advance where someone is from, and living in the US doesn't make them American. So the app **never classifies the person** (by looks, accent or ethnicity). It reads **how the conversation happens**:

- **Language of each sentence → which rules apply.** All rule sets run together and don't conflict. A mixed conversation (Portuguese plus English) works.
- **Formality is detected from the cues themselves:**
  - formal: "Sr./Dr./Mr.", "prazer em conhecê-lo", a business card, surname-only;
  - informal: "e aí", first name only, nickname.
  - It changes only how the name is shown and saved: "Sr. Tanaka" / "Dr. Paulo" vs. "Kenji" / "Paulo". It never changes how the person is described.
- **Name order:** when the evidence is a business card or a family-name-first introduction, the AI returns `given_name` and `family_name` separately. The card shows what you call the person.

**Optional user setup**, only if the automatic reading isn't enough:
- "Languages I talk in" (multi-select, sets speech recognition);
- "Mostly social / mostly business" (business makes the card OCR and titles more prominent).

No profile per region (Latin, Asian, European) is needed. Those are proxies for the language and formality signals above, and they tend to misfire.

### Prompt (system)

```text
You process the transcript of ONE in-person encounter between the USER (wearing smart glasses) and ONE other person in front of them. Return ONLY valid JSON matching the schema. Write every text field in {lang}.

1. NAME OF THE PERSON IN FRONT
- Introductions are short and informal in every language. Brazil: "Marcos, prazer", "sou a Mariana", "prazer, Ana" (rarely "meu nome é"). English: "I'm Mike", "nice to meet you, Sarah", "you must be Sarah". Spanish: "soy Carlos", "mucho gusto, Ana". Japanese/Chinese/Korean: family name first, honorifics (-san, -ssi, 先生) are not part of the name; people may be called by title ("Manager Kim"). A name read from a business card in the transcript or OCR is the strongest evidence.
- "prazer" is a strong marker that an introduction just happened; the name next to it is the person in front.
- The USER may name the person on purpose: "ah, então você é o Marcelo?", "deixa eu gravar você aqui, Marcelo Campos", "esse é o meu amigo Rafael Souza", "Karin, né?".
- Corrections: the LAST valid name wins ("Gabriela… perdão, Gabriela não, você é a Beatriz" → Beatriz). Spelling confirmations ("Patrícia com C") fix the spelling.
- Nickname: "pode me chamar de Dani, mas é Daniele" → name "Daniele", alias "Dani".
- THIRD PARTIES — VERY IMPORTANT: never use the name of someone who is only mentioned ("o João me disse", "você conhece a Carolina?", "sua mãe, a Karin", public figures). A name right after a family/relationship word (mãe, irmã, amigo, chefe…) belongs to someone else, unless it is "esse é o meu amigo X" presenting the person in front.
- Transcripts have no reliable punctuation or capitalisation. Judge by meaning.
- If unsure, return name = null. Never invent a name. name_evidence must be a literal quote from the transcript.

2. CARD
- tags: 2–3 short lowercase words (relationship, place, interest). Never "desconhecido/desconhecida", never the person's name.
- hook: the single most memorable thing about THIS encounter, a theme, a funny situation, a phrase. Max ~8 words, no name. In Portuguese, phrase it like "a fofoca do relógio falso" so it reads well after the visual description.
- context: 1–2 sentences on what this encounter was about. Not a literal transcript.
- dossier: ~4 short lines about who this person is to the user. Merge with the previous dossier (given in input): keep durable facts, add new ones (job changes, tastes, life events), drop trivia.
- actions: concrete follow-ups the USER committed to, if any.

- Never infer nationality, ethnicity or culture of the person. Formality comes only from how they introduce themselves (titles, business card, surname-only). If the evidence gives family-name-first, fill given_name and family_name.

Schema:
{"name": string|null, "given_name": string|null, "family_name": string|null, "formal": boolean, "name_confidence": number, "name_evidence": string|null, "alias": string|null,
 "tags": string[], "hook": string, "context": string, "dossier": string, "actions": string[]}
```

---

## PT — Português

### Contrato

O celular envia `POST fcProxy {action:"analyze_encounter", ...}` uma vez por encontro, quando ele termina.

O formato de entrada e de saída é o mesmo do JSON da seção em inglês acima. A saída é só JSON.

**Regras do app:**

| Campo | O que o app faz |
|---|---|
| `name` | Renomeia a pessoa **só se** `name_confidence ≥ 0,8`, se ela ainda está sem nome (ou ganhou o nome neste mesmo encontro) e se `name_evidence` é uma citação literal da transcrição. |
| `tags` | Viram as pílulas (`tags` da pessoa). |
| `hook` | Vira a linha 3 do card (`custom_fields.hook`) e fica salvo no encontro. |
| `dossier` | Vira o bloco "SOBRE" (`custom_fields.dossier`). |
| `context` | Vira o `context` do encontro. |

### Card ↔ campos

| Linha do card | Pessoa com nome | Pessoa sem nome | Campo |
|---|---|---|---|
| 1 · título | Marcelo Campos | Mulher de ~25 anos, cabelo roxo | `name` / `custom_fields.look` |
| 2 · tags (2–3) | amigo · café · arte | bar · criativa | `tags` |
| 3 · ocasião | a fofoca do relógio falso | a fofoca do relógio falso | `custom_fields.hook` |
| bloco · sobre (≈4 linhas) | dossiê | dossiê | `custom_fields.dossier` |

### Notas do Claude sobre o texto original

- **Tag nunca diz "desconhecida".** A tag de exemplo `desconhecida` contradiz a regra que fixamos em 10/10: quem não tem nome não é desconhecido. A falta de nome já aparece no título.
- **Durante a conversa, a linha 3 mostra o gancho do encontro anterior.** O encontro atual só é analisado quando termina. E é justamente isso que ajuda no meio da conversa ("da última vez: a fofoca do relógio falso").
- **Nome só com prova.** Sem citação literal e nota de confiança, a IA pode inventar um nome plausível. O app só aceita nome citado.
- **As regras no aparelho continuam.** O emulador e o celular pegam a maioria das apresentações na hora, e o card atualiza ao vivo. Este prompt é a rede de segurança que roda no fim e também escreve `tags`, `hook` e `dossier`.

### Internacional (a loja é global)

As regras não podem ser só do Brasil, mas também não precisam de uma versão por país. São duas camadas:

1. **No aparelho (na hora, offline):** regras curtas para as apresentações mais comuns em **português, inglês e espanhol**. Mesma lógica das regras brasileiras: o nome é de quem está na frente, nunca de um terceiro.
   - **Inglês:** "I'm Mike", "Mike, nice to meet you", "Nice to meet you, Sarah", "You must be Sarah", "Sarah, right?", "This is my friend Tom", "It's Michael, but everyone calls me Mike". Nome depois de mom, sister, boss… é de outra pessoa.
   - **Espanhol:** "me llamo", "soy Carlos", "mucho gusto, Ana", "Ana, mucho gusto", "encantada", "te presento a mi amigo Luis", "tú eres Ana, ¿no?".
   - Títulos (Mr., Ms., Dr., Sr., Sra.) não fazem parte do nome.
2. **A IA no fim (todos os idiomas):** o prompt é neutro de idioma. Ele cobre o que as regras não pegam:
   - **Ásia oriental:** sobrenome primeiro; "Tanaka desu", 我叫/我是, 저는 … 입니다.
   - **Honoríficos:** -san, -ssi, 先生, que não fazem parte do nome.
   - **Cargo no lugar do nome:** "Gerente Kim".
3. **Cartão de visita (Japão, Coreia, China e negócios em geral):** muitas vezes o nome nem é falado, é entregue num cartão. O OCR de crachá que já existe lê também cartões de visita. O nome impresso tem a maior confiança de todas as fontes.

O reconhecimento de voz segue o idioma do celular. A IA recebe `lang` e responde nesse idioma.

### Cultura e formalidade: perceber pela conversa, nunca pela pessoa

Nunca sabemos antes de onde alguém é, e morar nos EUA não faz ninguém americano. Por isso o app **nunca classifica a pessoa** (pela aparência, pelo sotaque ou pela etnia). Ele lê **como a conversa acontece**:

- **Idioma de cada frase → quais regras valem.** Todos os conjuntos de regras rodam juntos e não brigam. Conversa misturada (português e inglês) funciona.
- **A formalidade é percebida pelos próprios sinais:**
  - formal: "Sr./Dr./Mr.", "prazer em conhecê-lo", cartão de visita, só o sobrenome;
  - informal: "e aí", só o primeiro nome, apelido.
  - Ela muda só como o nome aparece e é salvo: "Sr. Tanaka" / "Dr. Paulo" vs. "Kenji" / "Paulo". Nunca muda como a pessoa é descrita.
- **Ordem do nome:** quando a prova é um cartão ou uma apresentação com sobrenome primeiro, a IA devolve `given_name` e `family_name` separados. O card mostra como você chama a pessoa.

**Ajuste opcional do usuário**, só se a leitura automática não bastar:
- "Idiomas em que eu converso" (escolha múltipla, define o reconhecimento de voz);
- "Mais social / mais negócios" (negócios dá mais peso ao cartão de visita e aos títulos).

Não precisa de perfil por região (latino, asiático, europeu). Eles são atalhos para os sinais de idioma e formalidade acima, e costumam errar.

### Prompt (sistema)

É o mesmo bloco da seção em inglês acima. Ele fica em inglês de propósito, porque modelos seguem instruções em inglês com mais precisão. O campo `{lang}` faz a resposta sair em português.
