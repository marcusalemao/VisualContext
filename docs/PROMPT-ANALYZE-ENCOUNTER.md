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

### Prompt (system)

```text
You process the transcript of ONE in-person encounter between the USER (wearing smart glasses) and ONE other person in front of them. Return ONLY valid JSON matching the schema. Write every text field in {lang}.

1. NAME OF THE PERSON IN FRONT
- In Brazil people rarely say "meu nome é". Expect short, informal introductions: "Marcos, prazer", "sou a Mariana", "prazer, Ana".
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

Schema:
{"name": string|null, "name_confidence": number, "name_evidence": string|null, "alias": string|null,
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

### Prompt (sistema)

É o mesmo bloco da seção em inglês acima. Ele fica em inglês de propósito, porque modelos seguem instruções em inglês com mais precisão. O campo `{lang}` faz a resposta sair em português.
