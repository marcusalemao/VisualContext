# VisualContext — privacy brief for the Hamburg talk (05/11/2026)

[🇬🇧 English](#en--english) | [🇩🇪 Deutsch](#de--deutsch) | [🇧🇷 Português](#pt--português)

## EN — English

One page for the presenter. The long version is `PRIVACIDADE-E-SEGURANCA.md`. Everything below is a design decision already taken; the parts marked (built) exist in code today, the rest is specified.

**1. The camera is not a recorder.** The glasses take single frames, not video. Each frame is described as text on the device and then discarded. Audio is never stored: only the transcript. Of a face, one small crop, never the whole picture. The glasses keep nothing on disk; memory lives on the phone, encrypted, and optionally in the user's own cloud (Google Drive etc.), encrypted on the phone before upload. No server of ours in the path.

**2. Faces stay on the device (built).** Recognition runs on the phone or on the glasses with an on-device model. A face is compared only against the wearer's own memory. There is no search of strangers, no internet lookup, no face database of ours.

**3. Nothing is secret about recording.** An encounter starts with a visible countdown on the lens that can be cancelled with one gesture. The recording light of the glasses is never turned off by the app. Nobody becomes a record for passing by: an encounter needs eye contact and speech. A person without a name who is never seen again is erased after 14 days.

**4. Sensitive moments are decided on the device, never in the cloud.**
- Bathroom, locker room, shower: camera and microphone pause; the pre-roll buffer is discarded.
- Nudity (a child at the pool, the beach): the moment survives only as one neutral sentence, written by an on-device model. No image is ever saved and nothing is sent to any cloud, not even for the transcription.
- Sexual intimacy: nothing is recorded at all.
- Children are never enrolled automatically; a stranger's child never becomes a record.
- Cards, passwords and ID numbers are masked before saving.
- These guards are on by default; the most sensitive ones can be switched off only in a developer mode, and they switch themselves back on after one hour.

**5. The wearer, and only the wearer.** Off the face, the app locks; unlock only on the phone with biometrics. A panic phrase destroys the keys. "Forget this conversation", "forget this person", "forget everything" are real deletions (crypto-shredding), including backups. Export is always available.

**6. Retention defaults.** Full transcript 30 days, then summary only. Named people until the user erases them. Everything adjustable.

**Where this stands against GDPR.** Face data is special-category data (art. 9). The design answers it with: processing on the user's own device under their sole control, no sharing, consent by an announced and cancellable recording, real deletion, data minimisation (text, not audio; a crop, not video). A formal consent screen and privacy text are still to be written before a European release.

---

## DE — Deutsch

Eine Seite für den Vortrag. Die lange Fassung ist `PRIVACIDADE-E-SEGURANCA.md`. Alles unten ist bereits entschieden; (gebaut) existiert heute im Code, der Rest ist spezifiziert.

**1. Die Kamera ist kein Rekorder.** Die Brille nimmt Einzelbilder, kein Video. Jedes Bild wird auf dem Gerät als Text beschrieben und dann verworfen. Audio wird nie gespeichert, nur die Mitschrift. Von einem Gesicht ein kleiner Ausschnitt, nie das ganze Bild. Die Brille speichert nichts; das Gedächtnis liegt verschlüsselt auf dem Telefon und optional in der eigenen Cloud des Nutzers, vor dem Upload auf dem Telefon verschlüsselt. Kein Server von uns dazwischen.

**2. Gesichter bleiben auf dem Gerät (gebaut).** Die Erkennung läuft auf dem Telefon oder auf der Brille mit einem lokalen Modell. Ein Gesicht wird nur mit dem eigenen Gedächtnis des Trägers verglichen. Keine Suche nach Fremden, kein Internet-Abgleich, keine Gesichtsdatenbank von uns.

**3. Aufnahme ist nie heimlich.** Eine Begegnung beginnt mit einem sichtbaren Countdown auf der Linse, der mit einer Geste abgebrochen werden kann. Das Aufnahmelicht der Brille wird von der App nie abgeschaltet. Niemand wird zum Datensatz, weil er vorbeigeht: eine Begegnung braucht Blickkontakt und Sprache. Eine Person ohne Namen, die nie wieder auftaucht, wird nach 14 Tagen gelöscht.

**4. Sensible Momente werden auf dem Gerät entschieden, nie in der Cloud.**
- Bad, Umkleide, Dusche: Kamera und Mikrofon pausieren; der Vorlauf-Puffer wird verworfen.
- Nacktheit (ein Kind am Pool, am Strand): der Moment bleibt nur als ein neutraler Satz, geschrieben von einem lokalen Modell. Kein Bild wird gespeichert, nichts wird an eine Cloud geschickt, auch nicht zur Beschreibung.
- Sexuelle Intimität: es wird gar nichts aufgezeichnet.
- Kinder werden nie automatisch erfasst; das Kind eines Fremden wird nie zum Datensatz.
- Karten, Passwörter und Ausweisnummern werden vor dem Speichern maskiert.
- Diese Schutzmechanismen sind standardmäßig an; die sensibelsten lassen sich nur in einem Entwicklermodus abschalten und schalten sich nach einer Stunde selbst wieder ein.

**5. Der Träger, und nur der Träger.** Abgesetzt, sperrt die App; Entsperren nur am Telefon per Biometrie. Ein Panikwort zerstört die Schlüssel. „Vergiss dieses Gespräch“, „vergiss diese Person“, „vergiss alles“ sind echte Löschungen (Crypto-Shredding), auch in Backups. Export ist jederzeit möglich.

**6. Aufbewahrung (Standard).** Vollständige Mitschrift 30 Tage, danach nur Zusammenfassung. Benannte Personen, bis der Nutzer sie löscht. Alles einstellbar.

**Stand gegenüber der DSGVO.** Gesichtsdaten sind besondere Kategorien (Art. 9). Das Design antwortet mit: Verarbeitung auf dem eigenen Gerät unter alleiniger Kontrolle des Nutzers, keine Weitergabe, Einwilligung durch angekündigte und abbrechbare Aufnahme, echte Löschung, Datenminimierung (Text statt Audio, Ausschnitt statt Video). Ein förmlicher Einwilligungsdialog und ein Datenschutztext fehlen noch vor einer Veröffentlichung in Europa.

---

## PT — Português

Uma página para quem apresenta. A versão longa é `PRIVACIDADE-E-SEGURANCA.md`. Tudo abaixo já foi decidido; (feito) existe no código hoje, o resto está especificado.

**1. A câmera não é um gravador.** Os óculos capturam quadros isolados, não vídeo. Cada quadro é descrito em texto no aparelho e descartado. Áudio nunca é guardado: só a transcrição. De um rosto, um recorte pequeno, nunca a imagem inteira. Os óculos não guardam nada em disco; a memória fica no celular, criptografada, e opcionalmente na nuvem do próprio usuário, criptografada no celular antes de subir. Nenhum servidor nosso no caminho.

**2. Rostos ficam no aparelho (feito).** O reconhecimento roda no celular ou nos óculos com um modelo local. Um rosto só é comparado com a memória do próprio usuário. Não há busca de estranhos, nem consulta na internet, nem banco de rostos nosso.

**3. Gravar nunca é escondido.** Um encontro começa com uma contagem visível na lente, que se cancela com um gesto. A luz de gravação dos óculos nunca é desligada pelo app. Ninguém vira registro por passar perto: um encontro exige contato visual e fala. Pessoa sem nome que nunca mais aparece é apagada em 14 dias.

**4. Momentos sensíveis são decididos no aparelho, nunca na nuvem.**
- Banheiro, vestiário, chuveiro: câmera e microfone pausam; a pré-gravação é descartada.
- Nudez (criança na piscina, na praia): o momento sobrevive só como uma frase neutra, escrita por um modelo local. Nenhuma imagem é guardada e nada é enviado a nuvem alguma, nem para a descrição.
- Intimidade sexual: nada é registrado.
- Crianças nunca são cadastradas sozinhas; o filho de um estranho nunca vira registro.
- Cartões, senhas e documentos são mascarados antes de salvar.
- Essas proteções vêm ligadas; as mais sensíveis só se desligam em modo desenvolvedor e religam sozinhas em uma hora.

**5. Quem usa, e só quem usa.** Fora do rosto, o app trava; desbloqueio só no celular, com biometria. Uma frase de pânico destrói as chaves. "Esquece essa conversa", "esquece essa pessoa", "esquece tudo" são exclusões reais (destruição criptográfica), inclusive nos backups. Exportar está sempre disponível.

**6. Retenção (padrão).** Transcrição completa 30 dias, depois só o resumo. Pessoas com nome até o usuário apagar. Tudo ajustável.

**Diante do GDPR.** Dado de rosto é categoria especial (art. 9). O desenho responde com: processamento no aparelho do próprio usuário sob controle só dele, nada compartilhado, consentimento por gravação anunciada e cancelável, exclusão real, minimização (texto em vez de áudio; recorte em vez de vídeo). Falta escrever a tela formal de consentimento e o texto de privacidade antes de lançar na Europa.
