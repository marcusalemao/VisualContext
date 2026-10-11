# Task for Beto — fcProxy: Gemini key + analyze_encounter

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

**Why:** `ai_summary` has been failing with "invalid authentication credentials (Expected OAuth 2 access token)", so every summary was the transcript cut off ("model":"fallback"). `analyze_encounter` doesn't exist yet ("unknown_action").

**Already done (Claude, 10/10):**
- The new key is stored as the secret **`VC_GEMINI_API_KEY`**. The old secrets are untouched.
- The key was tested directly on `gemini-flash-latest` and works.
- The full prompt was tested on Marcus's scenario and returned valid JSON: name "Marcus" from "olha é marcos com u", hook "o reencontro dos tempos da Latam", plus tags, dossier and actions.
- `gemini-2.5-flash` is no longer available to new keys. Use `gemini-flash-latest`.

**What's left:** this app is not a builder app, so Claude cannot edit `fcProxy`. The change request below is ready for you.

## PT — Português

**Por quê:** o `ai_summary` vem falhando com "credenciais inválidas (esperava token OAuth 2)", então todo resumo era a transcrição cortada ("model":"fallback"). O `analyze_encounter` ainda não existe ("unknown_action").

**Já feito (Claude, 10/10):**
- A chave nova está salva no secret **`VC_GEMINI_API_KEY`**. Os secrets antigos não foram mexidos.
- A chave foi testada direto no `gemini-flash-latest` e funciona.
- O prompt completo foi testado no cenário do Marcus e devolveu JSON válido: nome "Marcus" a partir de "olha é marcos com u", gancho "o reencontro dos tempos da Latam", além de tags, dossiê e ações.
- O `gemini-2.5-flash` não está mais disponível para chaves novas. Use o `gemini-flash-latest`.

**O que falta:** este app não é um app do construtor, então o Claude não consegue editar o `fcProxy`. O pedido de mudança abaixo está pronto para você.

---

## Change request (EN, ready to apply)

Change ONLY the backend function fcProxy. Do not touch the frontend, entities or other functions. Keep every existing action exactly as it is.

1) Gemini calls inside fcProxy (ai_summary and the new action below) must use the secret VC_GEMINI_API_KEY (Deno.env.get("VC_GEMINI_API_KEY")), sent in the header "x-goog-api-key" (NOT as a Bearer/OAuth token), to
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent
(gemini-2.5-flash is no longer available to this key). Keep the existing budget logging. If the call fails, return the error text in the response field "error" as today.

2) Add a new action "analyze_encounter" to fcProxy (same CORS/JSON handling as the others).
Input body: { action:"analyze_encounter", lang:"pt"|"en", transcript:string, person:{name, named, look, tags, dossier, hook}, previous_encounters:[{date, context, hook}], place? }
It calls Gemini with systemInstruction = the prompt below (replace {lang} with the input lang), the whole input JSON (without "action") as the user message, and generationConfig.responseMimeType = "application/json".
Return the parsed JSON object from the model as the response body (fields: name, given_name, family_name, formal, name_confidence, name_evidence, alias, tags, hook, context, dossier, actions). If the transcript is empty return {"error":"empty_transcript"}. Log the call in the same budget log as ai_summary.

System prompt (verbatim):
---
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
---

Full contract and rules for the app: `docs/PROMPT-ANALYZE-ENCOUNTER.md`.
