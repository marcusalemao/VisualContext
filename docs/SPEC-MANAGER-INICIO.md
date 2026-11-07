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
  - then the one question: "Want to go there?" (below).
- If an encounter starts, the card steps aside and comes back after.
- **The right photo comes from the parking moment, not from a search later.** When the phone's speed drops from riding to zero and then walking, save the location and take a short burst. A voice command ("save: parked here") does the same by hand.
- **At the parking moment, keep two photos:** one with the motorbike (if the signature finds it) and one of the surroundings, even without the bike: a shop front, a sign, the corner. The address alone is often not enough; the picture says "it was there". Signs and shop names in that photo are read (OCR) and added to the card: "Rua Paim, 210 · in front of Padaria Boa".
- **One question after the answer (decided 06/11, replaces "one button: See on map").** The glasses never act on their own, but they may ask. After a location answer, the AI asks **one** short question, with the most likely mode already suggested by distance, by voice and on the HUD:
  - near (up to ~800 m): "Want to go there? On foot it's about 4 minutes.";
  - farther: "Want to go there? It's 6 km: public transport, motorbike or car?" (public transport is always offered when the phone has it, e.g. "metro, 15 minutes");
  - silence or "no" = nothing happens. Never a second question.
- **If you say yes:**
  - **on foot:** we guide. The arrow + distance on the HUD (head heading from the Places app + phone GPS) gets a spoken version: "turn right in 50 metres", "it's ahead, next to the bakery". Low-vision module: voice only. No obstacle avoidance: that stays with Rokid's own Vision assistant;
  - **public transport, motorbike, car:** hand the destination to Google Maps on the phone (or to a glasses navigation app via `geo:` intent, if one accepts it — to be tested: rokid-ar-navigation, Rokid-GMaps), with the chosen mode preselected. Nobody rides by our HUD.
- **The same rule for other answers:** "Rafael said 3 pm" → "Put it in the calendar?"; "the key is in the bedroom" → nothing to ask. Only when there is one obvious action, and only one question.
- Without the phone: address only, no distance (the glasses have no GPS).

### 1e. Things: register once, find always (11/10)
- **Register once, with the thing in hand.** "This is my key": a few photos from different angles, a short description and a visual signature (like the face signature). Hands mark that it is yours and that this is the teaching moment.
- **Answer = the last photo where it was seen.** "Where is my key?" shows that photo, the time, and where it was, as far as we know. Indoors GPS is useless and a transcript can't tell bed from wardrobe or fridge; the photo can, because you recognise your own bed.
- **Which house:** the Wi-Fi network name (home, office, a friend's) says which building, without GPS.
- **Which room (optional):** teach each room once ("this is the bedroom"); after that the scene says the room. Without it, the AI describes the spot ("on the bed", "inside the fridge").
- **Show the last 2–3 sightings**, not only one: the latest frame may be wrong, or the key moved after it.
- **Capture:** the camera can't stay on all day (heat, battery). Plan: a low-resolution frame every few seconds while the user moves around, checked on the phone against registered things only; frames with no registered thing are discarded at once; kept sightings expire after a few days. Plus a voice shortcut: "save: key here".
- **Three sources for "last seen", one card** (Marcus, 25/10). The heart stays the description of the last frame where the thing was seen. But many people already use tags, and nobody wears the glasses 24 hours a day, so the card takes the best source it has and says which one:
  - **Camera** (glasses on): the last photo + its description. Best for "where exactly".
  - **Tag** the user already has: Find Hub / Find My tags (UGREEN, AirTag, Moto Tag…) through the "Open in Find Hub" button, which can ring it or, on tags with UWB, point to it; generic BLE tags (iTag type) read by our app: near/far, ring. Best for "it's in the house but where" and for when the glasses were off.
  - **NFC sticker** (phone tap): NFC is a few centimetres, so it doesn't *find*; it *marks*. Tap the sticker on a drawer or a box → "what's in here" (the last photo of its contents); tap the one on the keyring → "key here" without glasses. Cheap and works with no battery.
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

### 1g. AI cues during a conversation (revised 07/11; was "idea for testing", 11/10)
Marcus, 07/11: very important; loose hints during the conversation, including **suggesting what to answer** and **correcting the wearer**. Inspired by Even Conversate's AI Cues, with our memory behind it.
- **Five kinds of cue:**
  - **From memory:** "✦ pilot starts in March (you said on 12/09)".
  - **What to answer:** a short suggested reply to the question just asked.
  - **Correction:** "✦ it was 3 pm, not 4 pm (Rafael, 02/11)"; "✦ her name is Carla, not Clara".
  - **Concept:** a one-line explanation of a term that was just said.
  - **Bio:** who a person mentioned in the conversation is, from memory first, then the web (labelled).
- **How it reaches you (Marcus, 07/11):** only shown on the lens, or **spoken softly** in the ear (voice-only mode). Nothing to pick or scroll during a live conversation; the scroll-and-pick quick replies are a Zap thing.
- **Low distraction:** max one cue on the lens, a few words, fades after a few seconds; always with its source; no generic coaching ("say you are proactive").
- **On per conversation or in Settings** (Marcus to decide: cues appear on their own, like Even, or a small ✦ dot you tap to open, as in the 11/10 draft).
- Risks to test: eyes going to the HUD are noticed; the glasses mic favours the wearer, so the other person's words may be missed (phone mic helps).
- Job interviews as a candidate: not blocked, not promoted (see 1o).

### 1h. Every conversation is transcribed (decided 11/10, replaces the 30/09 rule)
- Any conversation is transcribed, known person or not. Someone unknown to the system may be someone you know who is not registered yet, or someone you are meeting now: that is how the people database grows.
- A transcript is discarded only if you cancel it (✕ on the countdown) or if the encounter was too short (the timer).
- The old rule "transcript only with a known person" is retired.
- **Built 07/11 (app 0.14.0): listening is separate from answering.** While a face is in front of you and the assistant (Falar) is off, the phone transcribes with its own on-device recognizer (free; offline when supported), catches names, and keeps the conversation as the encounter (summary by the AI). It never speaks. Falar takes the microphone while on. Ends 60 s after the last face; under 20 s or cancelled (✕), nothing is kept. Why not Falar always on (Marcus asked): Live answers whatever it hears, costs per minute and drains battery.
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
- **Built in the emulator (07/11, app 0.15.0):** output button (▭ Tela · ▭+🔊 · 🔊 Voz) and the same choice in Settings. Voice only hides the lens, as glasses without a display. Known person: soft sound + the short card, once (again only after 15 min). New person: "Pessoa nova. Guardar? Dois toques cancelam." (double tap = ✕). Listening to the conversation: a tone at start and end. Saved: a tick. QR: the question is said. Phone's own text-to-speech at low volume. Glasses without a camera (no face to start an encounter): double tap / "grava essa conversa", the calendar, names said, and later a voice signature with the same consent rules as faces.

### 1j. Modules: one app, four modes (decided 22/10)
Like Rokid's own modes, VisualContext has **modules** chosen at first use and changed on the phone at any time. More than one can be on (a low-vision user may also want Business). Underneath it is the same engine: a module only changes the defaults — what is shown, what is spoken, what is kept.
- **The four switches stay in every module** (Marcus, 22/10): Faces/People, Places, Things and Memories can be turned on or off in any module; a module only sets their starting values.
- **General (default):** everyday memory — people, key, motorbike, places.
- **Business:** the person's dossier (company, what you talked about, open follow-ups), encounter summary in the Manager, next actions.
- **Low vision:** output "voice only" (1i) by default; "who is in front of me", whispered; reading signs and texts aloud; Gemini describing the surroundings and guiding the aim by voice ("turn a little to the right", like Google's Guided Vision). Never obstacle avoidance or navigation.
- **Deaf / hard of hearing:** live captions of the conversation on the lens, with the speaker's name when known; WhatsApp voice notes transcribed (Zap); bridge to the LIBRAS project.
- **Follow Rokid, don't fight it:** if the glasses are in Rokid's "Visual assistance" or "Hearing assistance" mode, suggest the matching module. Marcus to record those Rokid modes first (backlog).
- **Why now:** glasses for low vision today cost thousands of dollars (e.g. Envision) and Google's glasses are not out yet; Rokid is affordable and ships to Brazil. Accessibility can be the launch hook in the store, while memory stays the product for everyone. First tester: a relative with severe low vision; then an institution (e.g. Fundação Dorina Nowill).

### 1k. Key left on the motorbike (decided 25/10)
Marcus's real case: getting off the bike and leaving the key in the ignition or on the handlebar. Reference for scenarios and UX only: his MotoKey Guard prototype (made in Gemini; a simulator, no Bluetooth). Find Hub / Find My tags (his UGREEN FineTrack) can't do this: they don't talk to each other and have no API for other apps.
- **Trigger = the parking moment (1d).** The phone's speed drops from riding to zero, then walking starts. That is when we ask, not before.
- **"Did the key come with you?"** — checked in this order:
  1. **The glasses camera:** in the parking burst, is the key in your hand or going into the pocket (the key's visual signature from 1e)?
  2. **A generic BLE tag** (iTag type, a few reais) on the keyring, if the user has one: its signal stays strong next to the phone = the key came. Our app can read these directly.
  3. If neither answers, ask on the HUD (one line, tap = "it's with me").
- **If the key stayed:** HUD card "the key stayed on the bike?" + phone vibration. If you walk about **15 m** away and nothing changed, a louder alarm on the phone.
- **Snooze 2 minutes** (e.g. you went back to fetch it, or a friend is holding it).
- **Home safe zone:** in your own garage (home Wi-Fi name, or the saved location) nothing fires.
- **Low vision / voice only:** one short spoken line, "A chave ficou na moto?", then the same tap / double tap.
- **Lost far away** stays with Find Hub: the thing's card only shows the "Open in Find Hub" link (backlog `ble-tags`).
- Lives under the **Things** switch (key and motorbike are the MVP things). Never acts on its own beyond the warning.

### 1l. Memory in moments (idea, 26/10 — after the talk)
Inspired by Gemma 4 on Marcus's phone (ask questions about a one-hour video; it answers with the clips where a topic came up). The glasses can't record hours of video, so we keep **moments** instead: one frame + the matching stretch of transcript + time and place, a few seconds each.
- **Ask, get the moment back:** "what did Rafael say about price?", "where did I put the key?" → the answer plus the moment it came from (photo, quote, time), like the Gemma clips.
- **Search on the phone, nothing sent out:** candidate engine **EmbeddingGemma 2 (740M, LiteRT-LM, Apache-2.0)**: text, image, audio and video in one shared 768-d space; ~26 ms per text on a Galaxy S26 Ultra; 485 MB download. One vector per moment; a question becomes a vector and finds the closest moments. Gemini (or Gemma on the phone) then writes the answer only from those moments.
- **Also good for Things (1e):** the photos of "this is my key" become vectors; frames are compared against them. Better than face-style signatures for objects.
- **Not for faces:** general embeddings don't tell two people apart reliably; faces keep the face-recognition model (0.2).
- **To check:** Portuguese quality (the model page doesn't list languages), battery, and whether the 485 MB download is optional (Plus / later).

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

### 5b. Import from Google Photos (decided 06/11)
Google Photos has no API for its named face groups (since 31/03/2025 apps only see photos the user picks one by one), so the app reads the screen, with the user's permission (Android screen capture).
1. **Capture the grid:** the user opens "People and pets" and scrolls; the app collects every face with a name.
2. **Select in our app:** the grid is rebuilt in VisualContext with a checkbox per person ("select all" / "clear"). Children come ticked like everyone (Marcus, 06/11: they are already named in his own Google Photos; a stranger's child is still never enrolled by the glasses); pets and the user himself are left out (the user becomes "Me").
3. **Recent photos, automatically:** for each ticked person the app opens their page (newest photos first), reads the full name from the header (the grid cuts names) and keeps 3–5 faces that agree with each other (a group photo doesn't mix people). Uses the same accessibility technique as the Zap; without it, the user taps each person and the app captures each page by itself.
4. **Review and save.** Faces from Google Photos are used for recognition (Marcus, 06/11: any name in the capture, replacing the 11/10 rule for this source), marked "face from Google Photos"; the first real encounter confirms.
- **Several looks per person (07/11, Marcus: "às vezes com barba, sem barba, barba tingida"; app 0.13.0):** up to 12 faces per person. A sure recognition that still looks different from every stored face is kept as a new look (at most one every 10 min per person). Saying the name of someone the glasses didn't recognise adds that face as one more look (it never renames another person). In Settings: "＋ adicionar fotos" from the gallery, and "ver todas" (long-press a photo to remove it). Every new look also goes to the memory (`enroll_face`). Step 3 (each person's Google Photos page) is still to do.
- **Also put the photo in the phone's contacts (on by default since 07/11, Marcus: "two birds with one stone"; built in 0.12.1):** only contacts that already exist, only those without a photo (replacing one is a per-person tick), the most recent face cropped square, no new contacts created. Note shown: the phone's contacts usually sync to Google Contacts, so the photo shows on all the user's devices (only to the user). Permission to edit contacts is asked only when this option is turned on.
- **Status 07/11 (app 0.8.0):** steps 1, 2 and 4 built (screen capture, names read under the faces, pick grid with all ticked, faces saved on the phone and in the memory as "rosto do Google Fotos"). Step 3 (each person's page, full name and recent faces) and the photo in contacts come next.
- **Other sources (07/11, app 0.12.0):** the same reader takes any list with photo and name. **Phone contacts with a photo** are read directly (no screenshot). **LinkedIn, Facebook, Instagram:** small photo on the left, name on its right (the line under it, the headline, is left out); the user scrolls with the capture on, or sends a print. **Automatic scrolling only for Google Photos** (his own photos): LinkedIn and Facebook forbid robots and restrict accounts for it. Each face says where it came from ("rosto do LinkedIn"). The CSV export from LinkedIn has no photos; that's why prints.
- **Web route and automatic (07/11):** Google Photos on the web (photos.google.com/people in Chrome) shows names over the bottom of each tile, almost never cut, especially in landscape at ~80% zoom (Marcus's finding). "Importar sozinho": an accessibility service scrolls the page in Chrome until it stops changing, then returns to the pick screen.
- **Same names (three "Bruno"s):** the photo goes to a contact only when the full name matches exactly one contact. Two or more matches, a first name only, or no match: nothing is written; the review shows the candidates and the user picks. When candidates already have a photo, the app compares faces and suggests the likely one, but never decides alone.

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

### 1m. Free conversations with the assistant are memory too (decided 07/11)
- Every free conversation with Gemini Live becomes **one moment**, kept with its context: when, where (address and coordinates), who was in view (names recognised in the last 10 minutes) and a one-sentence summary. "What did I ask about MASP that day?" must have an answer years later.
- **Text only, both sides** ("Você: …" / "Assistente: …"). Audio is never stored.
- **Exceptions (privacy rules):** saying "não guarda isso", "esquece o que eu disse" or "apaga isso" removes that exchange and the one before; sensitive moments get one neutral sentence; nothing is saved while memory is paused.
- Saved when Live closes (Fechar, 40 s of silence, or the connection drops). Offline, it waits on the phone and goes with the next one.
- **Reading for the user is help, not memory (07/11):** what the assistant reads aloud (menu, sign, leaflet, book page) is kept as one line ("[o assistente leu o cardápio]"); what the user said or decided stays ("pediu o risoto de cogumelos"). The full paper becomes memory only with "lembra disso". Done by a "memory version" of the conversation made by the AI when Live closes (`Describe.memoryVersion`).
- In the Manager it shows under **Memories** with ✦ ("Conversa com o assistente").
- Built in app 0.7.0 (`LiveBrain.saveConversation`, `Moments`, vc action `moment`, `capture_mode = live`).

### 1n. "Remember this" for papers: business card, badge, prescription, form (07/11)
- Saying "lembra disso" with a paper in view takes one frame, reads it and stores a **note with a title and fields**; the frame is then discarded (option later: keep the photo of the paper).
  - **Business card / badge:** creates or completes the person (name, company, role, phone, e-mail) and links it to the encounter.
  - **Medical prescription:** medicine, dose, schedule, doctor, date. Health data: by default it stays only in the memory and is **not** sent to the notes app (a tick turns that on).
  - **Form or any paper:** title, short summary and the full text.
- **Notes apps, two per system:** Android = Google Keep and Samsung Notes; iPhone = Apple Notes and Google Keep. None of them lets another app write directly (Keep's API is Workspace-only; Apple Notes and Samsung Notes have none), so:
  - **Android:** the app sends the note straight to the app chosen once in Settings (Keep, Samsung Notes, or "ask every time"); no login, no extra permission.
  - **iPhone:** the Manager in Safari has "Send to Notes", which opens the iPhone share sheet (Notes, Keep…). Later, with the iPhone helper app, an Apple Shortcut can do it on its own.
- Everything is also searchable in the Manager ("which medicine did the doctor prescribe in October?").

### 1o. Prep before a conversation (decided 07/11, top priority)
Feedback from a CEO to Marcus: business conversations need prep, "so you don't get it wrong". Inspired by Even's Prep Notes, but ours knows the real history.
- **The brief** (one screen in the Manager, one short card on the lens, expandable): who the person is to you (relation, company, role), how you met, the last encounter and its open follow-ups, **"don't get it wrong"** (how they like to be called, names of people close to them, topics to avoid, promises you made), 3 talking points, and **from the web** (company news, public profile), always labelled as web and kept apart from memory.
- **When:** (1) **10 minutes before** a calendar event with that person (phone calendar, read only; attendees matched by e-mail or name) → notification + card on the lens; (2) **on request:** "me prepara para a conversa com o Rafael" (Live) or the "Preparar conversa" button on the person in the Manager; (3) **at the meeting:** when the face is recognised, the card shows the brief's hook first.
- **Your own material:** notes or files attached to the person or the event in the Manager (later: a deck, a CV, a proposal) enter the brief.
- **Interviews:** prep before is a first-class use (the company, the interviewer from the web). Live hints during a job interview as a candidate: not blocked, not promoted (many companies treat it as cheating).

### 1p. Live captions on the lens (07/11)
The transcript already exists; show it as captions, toggled per conversation. For people who hear little, noisy places, and the General module (ADHD, early dementia). Inspired by Even Conversate.

### 1q. Live translation (07/11)
Captions translated on the lens; the reply read aloud to the wearer. Gemini Live already does it; what's missing is the mode (language pair, captions, voice). For Marcus in German, talks abroad, tourists.

### 1r. Skills of our own, learned (decided 07/11)
VisualContext is born independent of Nexus, so it needs its own skills runner.
- **A skill** is a named recipe: when to use it (trigger phrases), what to fetch (steps), how to show it (lens card + one spoken sentence). Examples asked by Marcus: **my portfolio** (B3 quotes), **recent e-mails** (Gmail, read only), **talk to Claude** (Claude API with his key, or OpenRouter), and skills he takes to the glasses.
- **Learned, not programmed:** the second similar request, or "aprende isso", → "Quer que eu crie a habilidade 'Minha carteira'?" → yes → the AI writes the recipe and asks only what's missing (tickers, which account). Next time Gemini Live calls it directly (each skill is a Live tool).
- **Safety:** a recipe is not code, only steps from a closed menu (web search, read a URL, read Gmail/Calendar, query the memory, call an AI, build the card). Anything that sends, buys or deletes needs a two-step confirmation (as Zap's send). Keys and logins stay on the phone; the recipe stores only the connection's name.
- Stored in our database; the same on glasses (Solo), phone and emulator. With Nexus present, the same skill is also published to the Nexus Assistant. Later: share skills between users.

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
  - e a pergunta única: "Quer ir até lá?" (abaixo).
- Se um encontro começa, o card sai da frente e volta depois.
- **A foto certa vem da hora de estacionar, não de uma busca depois.** Quando a velocidade do celular cai de pilotando para zero e depois para caminhada, guarda o local e faz uma rajada curta. Um comando de voz ("guarda: parei aqui") faz o mesmo na mão.
- **Na hora de estacionar, guardar duas fotos:** uma com a moto (se a assinatura achar) e uma do entorno, mesmo sem a moto: a fachada de uma loja, uma placa, a esquina. Só o endereço muitas vezes não basta; a imagem diz "foi aí". Placas e nomes de loja nessa foto são lidos (OCR) e entram no card: "Rua Paim, 210 · em frente à Padaria Boa".
- **Uma pergunta depois da resposta (decidido em 06/11, substitui "uma ação: Ver no mapa").** Os óculos nunca agem sozinhos, mas podem perguntar. Depois de uma resposta de lugar, a IA faz **uma** pergunta curta, já com o modo mais provável sugerido pela distância, por voz e no HUD:
  - perto (até ~800 m): "Quer ir até lá? A pé são uns 4 minutos.";
  - longe: "Quer ir até lá? São 6 km: transporte público, moto ou carro?" (transporte público é sempre oferecido quando o celular tem a informação, ex.: "metrô, 15 minutos");
  - silêncio ou "não" = nada acontece. Nunca uma segunda pergunta.
- **Se você disser sim:**
  - **a pé:** a gente guia. A seta + distância no HUD (direção da cabeça do app de Lugares + GPS do celular) ganha a versão falada: "vire à direita em 50 metros", "é ali na frente, do lado da padaria". Módulo baixa visão: só a voz. Sem desvio de obstáculo: isso continua com o assistente Vision da própria Rokid;
  - **transporte público, moto, carro:** passar o destino para o Google Maps no celular (ou para um app de navegação nos óculos pelo intent `geo:`, se algum aceitar — a testar: rokid-ar-navigation, Rokid-GMaps), com o modo escolhido já marcado. Ninguém pilota pelo nosso visor.
- **A mesma regra para outras respostas:** "o Rafael marcou às 15h" → "Ponho na agenda?"; "a chave está no quarto" → nada a perguntar. Só quando existe uma ação óbvia, e só uma pergunta.
- Sem o celular: só o endereço, sem distância (os óculos não têm GPS).

### 1e. Coisas: cadastrar uma vez, achar sempre (11/10)
- **Cadastro uma vez, com a coisa na mão.** "Essa é a minha chave": algumas fotos de ângulos diferentes, uma descrição curta e uma assinatura visual (como a do rosto). As mãos dizem que é sua e que aquele é o momento de ensinar.
- **Resposta = a última foto em que ela foi vista.** "Onde está minha chave?" mostra essa foto, a hora e onde estava, até onde sabemos. Dentro de casa o GPS não serve, e a transcrição não sabe se é a cama, o armário ou a geladeira; a foto sabe, porque você reconhece a sua cama.
- **Qual casa:** o nome da rede Wi-Fi (casa, escritório, casa de um amigo) diz em que lugar, sem GPS.
- **Qual cômodo (opcional):** ensinar cada cômodo uma vez ("esse é o quarto"); depois a cena diz o cômodo. Sem isso, a IA descreve o ponto ("em cima da cama", "dentro da geladeira").
- **Mostrar as 2–3 últimas vezes**, não só uma: o último quadro pode estar errado, ou a chave mudou de lugar depois.
- **Captura:** a câmera não pode ficar ligada o dia todo (calor, bateria). Plano: um quadro em baixa resolução a cada poucos segundos enquanto a pessoa se move, conferido no celular só contra as coisas cadastradas; quadro sem coisa cadastrada é descartado na hora; os avistamentos guardados expiram em poucos dias. Mais um atalho de voz: "guarda: chave aqui".
- **Três fontes para "última vez visto", um card só** (Marcus, 25/10). O coração continua sendo a descrição do último quadro em que a coisa foi vista. Mas muita gente já usa tag, e ninguém fica de óculos 24 horas por dia, então o card usa a melhor fonte que tiver e diz qual foi:
  - **Câmera** (com os óculos): a última foto + a descrição. A melhor para "onde exatamente".
  - **Tag** que a pessoa já tem: tags do Find Hub / Buscar (UGREEN, AirTag, Moto Tag…) pelo botão "Abrir no Find Hub", que faz apitar ou, nas tags com UWB, aponta a direção; tags BLE genéricas (tipo iTag) lidas pelo nosso app: perto/longe, apitar. A melhor para "está em casa, mas onde" e para quando os óculos estavam desligados.
  - **Adesivo NFC** (encostar o celular): o NFC só alcança poucos centímetros, então ele não *acha*, ele *marca*. Encostar no adesivo de uma gaveta ou caixa → "o que tem aqui dentro" (a última foto do conteúdo); encostar no do chaveiro → "chave aqui", sem óculos. Barato e sem bateria.
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

### 1g. Dicas da IA durante a conversa (revisado em 07/11; era "ideia para testar", 11/10)
Marcus, 07/11: muito importante; dicas soltas durante a conversa, inclusive **sugerir o que responder** e **corrigir quem usa**. Inspirado nos AI Cues do Even Conversate, com a nossa memória por trás.
- **Cinco tipos de dica:**
  - **Da memória:** "✦ piloto começa em março (você disse em 12/09)".
  - **O que responder:** uma resposta curta sugerida para a pergunta que acabou de ser feita.
  - **Correção:** "✦ foi às 15h, não às 16h (Rafael, 02/11)"; "✦ o nome dela é Carla, não Clara".
  - **Conceito:** uma linha explicando um termo que acabou de ser dito.
  - **Bio:** quem é uma pessoa citada na conversa, primeiro da memória, depois da web (marcado).
- **Como chega (Marcus, 07/11):** só mostrada na lente, ou **falada baixinho** no ouvido (modo só voz). Nada para escolher ou rolar durante uma conversa ao vivo; respostas rápidas para rolar e escolher são coisa do Zap.
- **Pouca distração:** no máximo uma dica na lente, poucas palavras, some em alguns segundos; sempre com a fonte; nada de conselho genérico ("diga que você é proativo").
- **Liga por conversa ou nos Ajustes** (Marcus decide: as dicas aparecem sozinhas, como na Even, ou um pontinho ✦ que você toca para abrir, como no rascunho de 11/10).
- Riscos a testar: o olhar indo para o HUD é percebido; o microfone dos óculos favorece quem usa, então a fala da outra pessoa pode se perder (o microfone do celular ajuda).
- Entrevista de emprego como candidato: não bloqueado, não anunciado (ver 1o).

### 1h. Toda conversa é transcrita (decidido em 11/10, substitui a regra de 30/09)
- Qualquer conversa é transcrita, com pessoa conhecida ou não. Alguém desconhecido para o sistema pode ser alguém que você conhece e ainda não cadastrou, ou alguém que você está conhecendo agora: é assim que o banco de pessoas cresce.
- A transcrição só é descartada se você cancelar (✕ no countdown) ou se o encontro for curto demais (o timer).
- A regra antiga "transcrição só com pessoa conhecida" sai.
- **Feito em 07/11 (app 0.14.0): ouvir é separado de responder.** Com um rosto na frente e o assistente (Falar) desligado, o celular transcreve com o reconhecedor do próprio aparelho (de graça; offline quando o aparelho permite), pega os nomes e guarda a conversa como o encontro (resumo pela IA). Nunca fala. O Falar assume o microfone enquanto está ligado. Termina 60 s depois do último rosto; com menos de 20 s ou cancelado (✕), nada fica. Por que não o Falar sempre ligado (pergunta do Marcus): o Live responde a tudo que ouve, cobra por minuto e gasta bateria.
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
- **Feito no emulador (07/11, app 0.15.0):** botão de saída (▭ Tela · ▭+🔊 · 🔊 Voz) e a mesma escolha nos Ajustes. Só voz esconde a lente, como óculos sem tela. Pessoa conhecida: som suave + o card curto, uma vez (de novo só depois de 15 min). Pessoa nova: "Pessoa nova. Guardar? Dois toques cancelam." (dois toques = ✕). Ouvindo a conversa: um bip no começo e no fim. Guardado: um clique. QR: a pergunta é falada. Voz do próprio celular, em volume baixo. Óculos sem câmera (sem rosto para começar o encontro): dois toques / "grava essa conversa", a agenda, os nomes ditos e, depois, assinatura de voz com as mesmas regras de consentimento do rosto.

### 1j. Módulos: um app, quatro modos (decidido em 22/10)
Como os modos da própria Rokid, o VisualContext tem **módulos**, escolhidos no primeiro uso e trocados no celular quando quiser. Dá para ligar mais de um (uma pessoa de baixa visão pode querer também o Business). Por baixo é o mesmo motor: o módulo só muda os padrões — o que aparece, o que é falado, o que é guardado.
- **As quatro chaves continuam em todos os módulos** (Marcus, 22/10): Pessoas, Lugares, Coisas e Lembranças podem ser ligadas ou desligadas em qualquer módulo; o módulo só define como elas começam.
- **Geral (padrão):** memória do dia a dia — pessoas, chave, moto, lugares.
- **Business:** o dossiê da pessoa (empresa, o que já conversaram, pendências), resumo do encontro no Manager, próximas ações.
- **Baixa visão:** saída "só voz" (1i) por padrão; "quem está na minha frente", dito baixinho; leitura de placas e textos em voz alta; o Gemini descrevendo o ambiente e orientando a mira por voz ("vire um pouco para a direita", como o Guided Vision do Google). Nunca desvio de obstáculo nem navegação.
- **Surdez:** legenda ao vivo da conversa na lente, com o nome de quem fala quando a pessoa é conhecida; áudios do WhatsApp transcritos (Zap); ponte com o projeto de LIBRAS.
- **Seguir a Rokid, não brigar:** se os óculos estão no modo "Assistência visual" ou "Assistência auditiva" da Rokid, sugerir o módulo correspondente. Antes, o Marcus grava esses modos da Rokid (backlog).
- **Por que agora:** óculos para baixa visão hoje custam milhares de dólares (ex.: Envision) e os óculos do Google ainda não saíram; a Rokid é acessível e entrega no Brasil. A acessibilidade pode ser o gancho de lançamento na loja, enquanto a memória continua sendo o produto para todo mundo. Primeiro testador: um parente com baixa visão grave; depois uma instituição (ex.: Fundação Dorina Nowill).

### 1k. Chave esquecida na moto (decidido em 25/10)
O caso real do Marcus: descer da moto e deixar a chave no contato ou no guidão. Referência só de cenários e UX: o protótipo MotoKey Guard dele (feito no Gemini; um simulador, sem Bluetooth). As tags do Find Hub / Buscar (a UGREEN FineTrack dele) não resolvem isso: uma não conversa com a outra e não têm API para outros apps.
- **Gatilho = a hora de estacionar (1d).** A velocidade do celular cai de pilotando para zero e depois começa a caminhada. É aí que perguntamos, não antes.
- **"A chave veio com você?"** — conferido nesta ordem:
  1. **Câmera dos óculos:** na rajada de estacionar, a chave está na sua mão ou indo para o bolso (a assinatura visual da chave, de 1e)?
  2. **Uma tag BLE genérica** (tipo iTag, poucos reais) no chaveiro, se a pessoa tiver: o sinal continua forte perto do celular = a chave veio. O nosso app lê essas tags direto.
  3. Se nenhum dos dois responder, pergunta no HUD (uma linha, toque = "está comigo").
- **Se a chave ficou:** card no HUD "a chave ficou na moto?" + vibração no celular. Se você se afastar uns **15 m** e nada mudou, alarme mais forte no celular.
- **Adiar 2 minutos** (ex.: você voltou para buscar, ou um amigo está com ela).
- **Zona segura de casa:** na sua garagem (nome do Wi-Fi de casa ou o local salvo) nada dispara.
- **Baixa visão / só voz:** uma frase curta falada, "A chave ficou na moto?", e o mesmo toque / toque duplo.
- **Perdida longe** continua com o Find Hub: o card da coisa só mostra o link "Abrir no Find Hub" (backlog `ble-tags`).
- Fica sob a chave **Coisas** (chave e moto são as coisas do MVP). Nunca age sozinho além do aviso.

### 1l. Memória em momentos (ideia, 26/10 — depois da palestra)
Inspirado no Gemma 4 no celular do Marcus (perguntas sobre um vídeo de uma hora; ele responde com os trechos onde o assunto apareceu). Os óculos não gravam horas de vídeo, então guardamos **momentos**: um quadro + o trecho da transcrição + hora e lugar, alguns segundos cada.
- **Pergunta e recebe o momento:** "o que o Rafael falou sobre preço?", "onde deixei a chave?" → a resposta e o momento de onde ela veio (foto, frase, hora), como os trechos do Gemma.
- **Busca no celular, nada sai:** motor candidato **EmbeddingGemma 2 (740M, LiteRT-LM, Apache-2.0)**: texto, imagem, áudio e vídeo num mesmo espaço de 768 dimensões; ~26 ms por texto num Galaxy S26 Ultra; download de 485 MB. Um vetor por momento; a pergunta vira vetor e acha os momentos mais próximos. Depois o Gemini (ou o Gemma no celular) escreve a resposta só com esses momentos.
- **Serve também para Coisas (1e):** as fotos de "essa é a minha chave" viram vetores e os quadros são comparados com elas. Melhor que assinatura tipo rosto para objetos.
- **Não serve para rostos:** embeddings gerais não separam bem duas pessoas; rostos continuam com o modelo de reconhecimento facial (0.2).
- **A conferir:** qualidade em português (a página do modelo não lista idiomas), bateria, e se o download de 485 MB fica opcional (Plus / depois).

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

### 5b. Importar do Google Fotos (decidido em 06/11)
O Google Fotos não tem API para os grupos de rostos com nome (desde 31/03/2025 os apps só veem fotos que o usuário escolhe uma a uma), então o app lê a tela, com permissão do usuário (captura de tela do Android).
1. **Capturar a grade:** o usuário abre "Pessoas e animais" e rola; o app junta todos os rostos com nome.
2. **Selecionar no nosso app:** a grade é recriada no VisualContext com uma caixinha por pessoa ("selecionar todos" / "limpar"). Crianças vêm marcadas como todo mundo (Marcus, 06/11: já têm nome no Google Fotos dele; filho de estranho continua nunca sendo cadastrado pelos óculos); animais e o próprio usuário ficam de fora (o usuário vira o "Eu").
3. **Fotos recentes, sozinho:** para cada pessoa marcada o app abre a página dela (fotos mais novas primeiro), lê o nome completo no topo (a grade corta os nomes) e guarda de 3 a 5 rostos parecidos entre si (foto em grupo não mistura pessoas). Usa a mesma técnica de acessibilidade do Zap; sem ela, o usuário toca em cada pessoa e o app captura cada página sozinho.
4. **Conferir e guardar.** Rostos do Google Fotos servem para reconhecer (Marcus, 06/11: qualquer nome da captura, substituindo a regra de 11/10 para esta fonte), marcados "rosto do Google Fotos"; o primeiro encontro de verdade confirma.
- **Vários visuais por pessoa (07/11, Marcus: "às vezes com barba, sem barba, barba tingida"; app 0.13.0):** até 12 rostos por pessoa. Um reconhecimento com certeza que ainda parece diferente de todos os rostos guardados vira um visual novo (no máximo um a cada 10 min por pessoa). Dizer o nome de alguém que os óculos não reconheceram acrescenta aquele rosto como mais um visual (nunca renomeia outra pessoa). Nos Ajustes: "＋ adicionar fotos" da galeria e "ver todas" (segurar uma foto para tirar). Todo visual novo também vai para a memória (`enroll_face`). O passo 3 (a página de cada pessoa no Google Fotos) ainda falta.
- **Também pôr a foto na agenda do celular (ligada por padrão desde 07/11, Marcus: "dois coelhos numa cajadada só"; feito na 0.12.1):** só contatos que já existem, só os sem foto (trocar é uma caixinha por pessoa), o rosto mais recente recortado quadrado, nenhum contato novo criado. Aviso: a agenda costuma sincronizar com o Google Contatos, então a foto aparece em todos os aparelhos do usuário (só para ele). A permissão de editar contatos só é pedida quando a opção é ligada.
- **Situação 07/11 (app 0.8.0):** passos 1, 2 e 4 feitos (captura de tela, nomes lidos embaixo dos rostos, grade de escolha com todos marcados, rostos guardados no celular e na memória como "rosto do Google Fotos"). O passo 3 (página de cada pessoa, nome completo e rostos recentes) e a foto na agenda vêm depois.
- **Outras fontes (07/11, app 0.12.0):** o mesmo leitor serve para qualquer lista com foto e nome. **Contatos do celular com foto** são lidos direto (sem print). **LinkedIn, Facebook, Instagram:** foto pequena à esquerda e nome à direita (a linha de baixo, o cargo, fica de fora); quem rola é o usuário, com a captura ligada, ou manda um print. **Rolagem automática só no Google Fotos** (as fotos dele): LinkedIn e Facebook proíbem robôs e restringem contas por isso. Cada rosto diz de onde veio ("rosto do LinkedIn"). O CSV do LinkedIn não traz fotos; por isso os prints.
- **Pela web e automático (07/11):** o Google Fotos na web (photos.google.com/people no Chrome) mostra o nome sobre a parte de baixo de cada quadrado, quase nunca cortado, ainda mais deitado com zoom de ~80% (descoberta do Marcus). "Importar sozinho": um serviço de acessibilidade rola a página no Chrome até ela parar de mudar e volta para a tela de escolha.
- **Nomes iguais (três "Bruno"):** a foto só vai para um contato quando o nome completo bate com exatamente um contato. Dois ou mais, só o primeiro nome, ou nenhum: nada é escrito; a conferência mostra os candidatos e o usuário escolhe. Quando os candidatos já têm foto, o app compara os rostos e sugere o provável, mas nunca decide sozinho.

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

### 1m. Conversas livres com o assistente também são memória (decidido em 07/11)
- Toda conversa livre com o Gemini Live vira **um momento**, guardado com o contexto: quando, onde (endereço e coordenadas), quem estava no campo de visão (nomes reconhecidos nos últimos 10 minutos) e um resumo de uma frase. "O que eu perguntei sobre o MASP naquele dia?" precisa ter resposta anos depois.
- **Só texto, dos dois lados** ("Você: …" / "Assistente: …"). O áudio nunca é guardado.
- **Exceções (regras de privacidade):** dizer "não guarda isso", "esquece o que eu disse" ou "apaga isso" tira aquela troca e a anterior; momentos sensíveis viram uma frase neutra; nada é guardado com a memória pausada.
- É salvo quando o Live fecha (Fechar, 40 s de silêncio ou queda da conexão). Sem internet, espera no celular e vai junto com o próximo.
- **Ler para o usuário é ajuda, não memória (07/11):** o que o assistente lê em voz alta (cardápio, placa, bula, página de livro) fica como uma linha ("[o assistente leu o cardápio]"); o que o usuário disse ou decidiu fica ("pediu o risoto de cogumelos"). O papel inteiro só vira memória com "lembra disso". Feito por uma "versão de memória" da conversa, que a IA prepara quando o Live fecha (`Describe.memoryVersion`).
- No Manager aparece em **Lembranças** com ✦ ("Conversa com o assistente").
- Feito no app 0.7.0 (`LiveBrain.saveConversation`, `Moments`, ação `moment` do vc, `capture_mode = live`).

### 1n. "Lembra disso" para papéis: cartão de visita, crachá, receita, formulário (07/11)
- Dizer "lembra disso" com um papel à vista tira uma foto, lê e guarda uma **nota com título e campos**; a foto é descartada em seguida (opção depois: guardar a foto do papel).
  - **Cartão de visita / crachá:** cria ou completa a pessoa (nome, empresa, cargo, telefone, e-mail) e liga ao encontro.
  - **Receita médica:** remédio, dose, horário, médico, data. Dado de saúde: por padrão fica só na memória e **não** vai para o app de notas (uma caixinha liga isso).
  - **Formulário ou papel qualquer:** título, resumo curto e o texto inteiro.
- **Apps de notas, dois por sistema:** Android = Google Keep e Samsung Notes; iPhone = Apple Notas e Google Keep. Nenhum deixa outro app escrever direto (a API do Keep é só para Workspace; Apple Notas e Samsung Notes não têm), então:
  - **Android:** o app manda a nota direto para o app escolhido uma vez nos Ajustes (Keep, Samsung Notes ou "perguntar sempre"); sem login, sem permissão extra.
  - **iPhone:** o Manager no Safari tem "Enviar para Notas", que abre o menu de compartilhar do iPhone (Notas, Keep…). Depois, com o app de apoio do iPhone, um Atalho da Apple faz isso sozinho.
- Tudo fica pesquisável no Manager também ("qual remédio o médico passou em outubro?").

### 1o. Preparação antes da conversa (decidido em 07/11, prioridade)
Feedback de um CEO ao Marcus: conversa de negócios pede preparação, "para você não errar". Inspirado no Prep Notes da Even, mas o nosso conhece a história de verdade.
- **O briefing** (uma tela no Manager, um card curto na lente, que expande): quem a pessoa é para você (relação, empresa, cargo), como se conheceram, o último encontro e as pendências, **"para não errar"** (como gosta de ser chamada, nomes de pessoas próximas, assuntos a evitar, promessas que você fez), 3 pontos de conversa e **da web** (notícias da empresa, perfil público), sempre marcado como web e separado da memória.
- **Quando:** (1) **10 minutos antes** de um compromisso da agenda com essa pessoa (agenda do celular, só leitura; participantes pelo e-mail ou nome) → notificação + card na lente; (2) **quando pedir:** "me prepara para a conversa com o Rafael" (Live) ou o botão "Preparar conversa" na pessoa, no Manager; (3) **na reunião:** quando o rosto é reconhecido, o card mostra primeiro o gancho do briefing.
- **Seu material:** notas ou arquivos anexados à pessoa ou ao evento no Manager (depois: apresentação, currículo, proposta) entram no briefing.
- **Entrevistas:** preparar antes é uso de primeira classe (a empresa, o entrevistador pela web). Dica ao vivo numa entrevista de emprego como candidato: não bloqueado, não anunciado (muitas empresas consideram trapaça).

### 1p. Legenda ao vivo na lente (07/11)
A transcrição já existe; mostrar como legenda, ligada por conversa. Para quem ouve pouco, lugar barulhento e o módulo Geral (TDAH, demência inicial). Inspirado no Even Conversate.

### 1q. Tradução ao vivo (07/11)
Legenda traduzida na lente; a resposta lida em voz alta para quem usa. O Gemini Live já faz; falta o modo (par de idiomas, legenda, voz). Para o Marcus em alemão, palestras fora, turistas.

### 1r. Habilidades próprias, que aprendem (decidido em 07/11)
O VisualContext nasce independente do Nexus, então precisa do próprio executor de habilidades.
- **Uma habilidade** é uma receita com nome: quando usar (frases que disparam), o que buscar (passos), como mostrar (card na lente + uma frase falada). Exemplos do Marcus: **minha carteira** (cotações da B3), **e-mails recentes** (Gmail, só leitura), **falar com o Claude** (API do Claude com a chave dele, ou OpenRouter), e habilidades que ele leva para os óculos.
- **Aprende, não se programa:** no segundo pedido parecido, ou com "aprende isso", → "Quer que eu crie a habilidade 'Minha carteira'?" → sim → a IA escreve a receita e pergunta só o que falta (quais ações, qual conta). Da próxima vez o Gemini Live chama direto (cada habilidade é uma ferramenta do Live).
- **Segurança:** a receita não é código, só passos de um cardápio fechado (buscar na web, ler um endereço, ler Gmail/agenda, consultar a memória, chamar uma IA, montar o card). O que envia, compra ou apaga pede confirmação em duas etapas (como o envio do Zap). Chaves e logins ficam no celular; a receita guarda só o nome da conexão.
- Fica no nosso banco; igual nos óculos (Solo), no celular e no emulador. Com o Nexus presente, a mesma habilidade também é publicada para o Assistant dele. Depois: compartilhar habilidades entre usuários.
