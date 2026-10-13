# Zap — start a WhatsApp message from the Rokid Glasses

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

A Rokid Nexus plugin (phone-side, no glasses code, no server). Relay answers notifications; Zap does what Relay cannot: **start a conversation with anyone**.

**On the glasses:** open Zap → *Speak* → "mensagem pro Bruno: tô chegando" → review card → **Send · Listen · Say it again**. Or pick a recent person and dictate only the message. Nothing is sent before *Send*.

**Who you can write to:** people who messaged you on WhatsApp (learned from WhatsApp's notifications: name and number only, never the text), plus, optionally, the phone's address book.

**How it sends:** opens the WhatsApp chat with the text typed (`whatsapp://send`). With the optional accessibility helper, it presses Send by itself; it only clicks when the text box holds exactly the confirmed message, and works only inside WhatsApp. Without it, the chat stays open on the phone for your thumb.

**Not used:** no internet permission, no unofficial WhatsApp client, no server.

| Module | What |
|---|---|
| `core/` | Pure Kotlin: contact matching by voice, the sentence parser (pt/en/es), phone numbers, links, the `ComposeFlow` state machine. 26 unit tests. |
| `app/` | The Nexus plugin (SDK `sdk-v0.23.0`, capabilities `surfaces,stt,tts`), notification listener, send helper, settings. |
| `upstream/` | The patch proposed to Anezium's Relay ("speak a conversation when opened"). |

Build: `./gradlew :core:testDebugUnitTest :app:assembleDebug` (JDK 17, Android SDK 36).

**To verify on the device:** that WhatsApp's conversation shortcut id is the WhatsApp id (`…@s.whatsapp.net`); the view ids `com.whatsapp:id/entry` and `:id/send`; that the Nexus hub grants `stt`/`tts` to a sideloaded plugin.

## PT — Português

Um plugin do Rokid Nexus (roda no celular, sem código nos óculos, sem servidor). O Relay responde notificações; o Zap faz o que o Relay não faz: **começar uma conversa com qualquer pessoa**.

**Nos óculos:** abre o Zap → *Falar* → "mensagem pro Bruno: tô chegando" → card de revisão → **Enviar · Ouvir · Ditar de novo**. Ou escolhe uma pessoa recente e dita só a mensagem. Nada sai antes de *Enviar*.

**Para quem dá para escrever:** quem te mandou mensagem no WhatsApp (aprendido pelas notificações do WhatsApp: só nome e número, nunca o texto) e, se você quiser, a agenda do celular.

**Como envia:** abre a conversa do WhatsApp com o texto digitado (`whatsapp://send`). Com o ajudante de acessibilidade (opcional), ele aperta Enviar sozinho; só clica quando a caixa de texto tem exatamente a mensagem confirmada, e só funciona dentro do WhatsApp. Sem ele, a conversa fica aberta no celular para o seu dedo.

**Não usa:** permissão de internet, cliente não oficial do WhatsApp, servidor.

| Módulo | O quê |
|---|---|
| `core/` | Kotlin puro: busca de contato por voz, entendimento da frase (pt/en/es), números de telefone, links, a máquina de estados `ComposeFlow`. 26 testes. |
| `app/` | O plugin do Nexus (SDK `sdk-v0.23.0`, capacidades `surfaces,stt,tts`), o leitor de notificações, o ajudante de envio, os ajustes. |
| `upstream/` | O patch proposto ao Relay do Anezium ("falar a conversa ao abrir"). |

Compilar: `./gradlew :core:testDebugUnitTest :app:assembleDebug` (JDK 17, Android SDK 36).

**A conferir no aparelho:** se o id do atalho de conversa do WhatsApp é o id do WhatsApp (`…@s.whatsapp.net`); os ids `com.whatsapp:id/entry` e `:id/send`; se o hub do Nexus concede `stt`/`tts` a um plugin instalado fora da loja.
