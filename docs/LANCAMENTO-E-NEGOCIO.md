# Launch & Business — decisions (2026-10-10)

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

### Marcus's decisions

1. **Landing page and demo**
   - The landing page uses **inverted colors**: light background, black text. It explains the project, the green/black interface concept and how it works.
   - **No password** for the demo, since the code is public. The visitor gets an **empty Manager** backed by a **temporary emulator session**.
   - A simple anti-bot / anti-overload check (CAPTCHA-like) protects the demo.
2. **License:** the code is open but **not free to use**. Commercial or general use needs prior permission and mandatory credit to the author.
3. **Monetization**
   - **Freemium by usage limit**: free up to a cap (e.g. 5–10 memories), paid beyond it.
   - Lighter options such as "Buy me a coffee" are also on the table.
4. **Ecosystem (Rokid Nexus / Anezium):** move carefully to avoid community rejection or a commercial veto. If restricted, consider building outside that ecosystem.

### Claude's notes (suggestions, not decisions)

- **Demo isolation**
  - Each demo session should live in the visitor's browser (local storage) or an ephemeral namespace that expires. It must never touch the real Base44 data, which today is an open CRUD with no auth.
  - Faces stay in the browser; nothing is uploaded.
  - **Cloudflare Turnstile** is free and the site already runs on Cloudflare, so it fits the CAPTCHA role.
- **Landing style:** the light/inverted page is good for contrast. Show the HUD inside a dark "lens" frame so the green-on-black grammar stays true where it matters.
- **Wording:** call it **"source-available"**, not "open source". OSI's definition excludes commercial restrictions, and the community reacts badly to the mislabel. Recognized templates are safer than a custom text:
  - PolyForm Noncommercial 1.0;
  - Business Source License (BSL 1.1, which converts to open source after N years).
- **The Nexus SDK is Apache-2.0**, which is compatible with a source-available plugin. The RokidBrew registry requires a public `sourceUrl`, which we have.
- **No anti-commercial rule exists in the ecosystem.** awesome-rokid explicitly accepts commercial projects, labeled `· Commercial` with the pricing model stated. Anezium himself uses Ko-fi. The real risk is reputation (the face-ID topic), not money.
- **Freemium cap:** a limit of 5–10 memories cuts the core value. A memory app proves itself only after many encounters. Better options:
  - cap what costs us money (AI summaries, cloud transcription minutes) and keep local memory unlimited;
  - or a time-limited full trial.
- **Payment:** a Nexus plugin cannot use Google Play Billing outside the Play Store. Use an external license key (Stripe, Ko-fi or Lemon Squeezy) checked by the Manager backend.

### Update 10/10 (04:59) — AI keys: free = bring your own key, Pro = everything included

**Marcus's decision:**
- Online demo: Marcus's keys, rate-limited (fake test in the emulator).
- Installed app: free users enter their own API keys and choose the provider.
- **Pro plan:** keys included and pre-configured, nothing to set up.

**Claude's notes:**
- **Providers to offer:** Gemini (free key, the easiest), OpenRouter (one key, many models), OpenAI. The Nexus Assistant from Anezium already does this, and also offers "sign in with your ChatGPT plan". Check the terms of use before copying that sign-in.
- **A free option without any key:** on recent Android phones (Pixel, Galaxy), the phone's own on-device AI (Gemini Nano) can summarize locally, free and private. It's weaker than the cloud models but good enough for summary and hook.
- **Same prompt and contract in both modes.** With your own key, the phone calls the provider directly and the key stays on the phone (Android Keystore), never on our server. With Pro, the phone calls our backend, which holds the keys and logs usage.
- **Pro is cheap for us:** one encounter analysis costs about US$0.0003 on Gemini Flash. A few dollars a month covers heavy use with a large margin.

### Update 10/10 (20:52) — Nexus now, our own channel later

**Marcus's direction:** connect to Rokid Nexus now (it's popular and evolving; its apps now talk to each other through Skills). If VisualContext turns out to be really good and can make money, evaluate publishing it outside Nexus as well. Not to compete with it, but in parallel.

**Claude's notes:**
- **Build so we can leave without rewriting.** The core (faces, memory, AI, Manager) depends on three things that come from Nexus today: the HUD display, the camera and the microphone. Put each behind a small adapter with two possible implementations: Nexus now, our own glasses app later. It costs little now and avoids a rewrite.
- **The glasses app for Lugares is already independent of Nexus.** It reads the head motion and draws on the glasses itself. The same app can later also take over camera, mic and display, which is the standalone version.
- **Installing without Nexus is possible:** the Hi Rokid "Install a new app" button, or our own phone app pairing wireless ADB the same way Nexus does (Kadb, Apache-2.0).
- **Stay in Nexus as a distribution channel even after going standalone.** Skills (who_is_here, ask_memory, last_encounter) let other Nexus apps and the Nexus assistant use VisualContext's memory. That's visibility we would not get alone.
- **Money:** nothing in the ecosystem forbids a commercial plugin (see above). Charging works through an external license key, same as before.

---

## PT — Português

### Decisões do Marcus

1. **Landing page e demo**
   - A landing page tem **cores invertidas**: fundo claro, texto preto. Explica o projeto, o conceito da interface verde/preta e o funcionamento.
   - **Sem senha** na demo, já que o código é público. O visitante recebe um **Manager vazio** ligado a uma **sessão temporária de emulador**.
   - Uma verificação simples contra robôs e sobrecarga (tipo CAPTCHA) protege a demo.
2. **Licença:** código aberto, mas **não livre**. Uso comercial ou geral exige permissão prévia e crédito obrigatório ao autor.
3. **Monetização**
   - **Freemium por limite de uso**: grátis até um teto (ex.: 5–10 memórias), pago acima disso.
   - Opções leves como "Buy me a coffee" também estão em consideração.
4. **Ecossistema (Rokid Nexus / Anezium):** ir com cuidado para evitar rejeição da comunidade ou veto comercial. Se houver restrição, avaliar desenvolver fora desse ecossistema.

### Notas do Claude (sugestões, não decisões)

- **Isolamento da demo**
  - Cada sessão da demo deve morar no navegador do visitante (armazenamento local) ou num espaço temporário que expira. Nunca deve tocar nos dados reais do Base44, que hoje é um CRUD aberto sem autenticação.
  - Os rostos ficam no navegador; nada é enviado.
  - O **Cloudflare Turnstile** é grátis e o site já roda na Cloudflare, então serve como o CAPTCHA.
- **Estilo da landing:** a página clara/invertida funciona pelo contraste. Mostrar o HUD dentro de uma moldura escura de "lente", para manter a gramática verde-no-preto onde ela importa.
- **Nome certo:** chamar de **"source-available"**, não "open source". A definição da OSI exclui restrição comercial, e a comunidade reage mal ao rótulo errado. Modelos reconhecidos são mais seguros que um texto próprio:
  - PolyForm Noncommercial 1.0;
  - Business Source License (BSL 1.1, que vira open source depois de N anos).
- **O SDK do Nexus é Apache-2.0**, compatível com um plugin source-available. O registro do RokidBrew exige um `sourceUrl` público, e isso nós temos.
- **Não existe regra anticomercial no ecossistema.** O awesome-rokid aceita explicitamente projetos comerciais, marcados com `· Commercial` e o modelo de preço informado. O próprio Anezium usa Ko-fi. O risco real é de reputação (o tema reconhecimento facial), não de dinheiro.
- **Teto do freemium:** um limite de 5–10 memórias corta o valor central. Um app de memória só se prova depois de muitos encontros. Opções melhores:
  - limitar o que nos custa dinheiro (resumos de IA, minutos de transcrição em nuvem) e deixar a memória local ilimitada;
  - ou um teste completo por tempo limitado.
- **Pagamento:** um plugin Nexus não consegue usar o Google Play Billing fora da Play Store. Usar uma chave de licença externa (Stripe, Ko-fi ou Lemon Squeezy) validada pelo backend do Manager.

### Atualização 10/10 (04:59) — chaves de IA: grátis = sua própria chave, Pro = tudo incluso

**Decisão do Marcus:**
- Demo online: chaves do Marcus, com limite de uso (teste fake no emulador).
- App instalado: quem usa de graça coloca as próprias chaves de API e escolhe o provedor.
- **Plano Pro:** chaves incluídas e pré-configuradas, nada para configurar.

**Notas do Claude:**
- **Provedores a oferecer:** Gemini (chave grátis, o mais fácil), OpenRouter (uma chave, vários modelos), OpenAI. O Assistant do Nexus, do Anezium, já faz isso e ainda oferece "entrar com o seu plano do ChatGPT". Vale checar os termos de uso antes de copiar esse login.
- **Opção grátis sem chave nenhuma:** em celulares Android recentes (Pixel, Galaxy), a IA do próprio celular (Gemini Nano) resume localmente, de graça e com privacidade. É mais fraca que os modelos na nuvem, mas dá conta de resumo e gancho.
- **Mesmo prompt e mesmo contrato nos dois modos.** Com chave própria, o celular chama o provedor direto e a chave fica no celular (Android Keystore), nunca no nosso servidor. No Pro, o celular chama o nosso backend, que guarda as chaves e registra o uso.
- **O Pro custa pouco para nós:** a análise de um encontro sai por cerca de US$0,0003 no Gemini Flash. Alguns dólares por mês cobrem uso pesado com folga.

### Atualização 10/10 (20:52) — Nexus agora, canal próprio depois

**Direção do Marcus:** conectar ao Rokid Nexus agora (é famoso e está evoluindo; os apps dele agora conversam entre si pelas Skills). Se o VisualContext ficar muito bom e der para ganhar dinheiro, avaliar publicar também fora do Nexus. Não para concorrer com ele, e sim em paralelo.

**Notas do Claude:**
- **Construir de um jeito que dê para sair sem reescrever.** O núcleo (rostos, memória, IA, Manager) depende de três coisas que hoje vêm do Nexus: a tela do HUD, a câmera e o microfone. Cada uma fica atrás de um adaptador pequeno, com duas implementações possíveis: Nexus agora, nosso próprio app nos óculos depois. Custa pouco agora e evita reescrever tudo.
- **O app dos óculos para Lugares já é independente do Nexus.** Ele lê o movimento da cabeça e desenha nos óculos. O mesmo app pode depois assumir também câmera, microfone e tela, e essa é a versão independente.
- **Dá para instalar sem o Nexus:** pelo botão "Instalar um novo app" do Hi Rokid, ou pelo nosso próprio app no celular pareando o ADB por Wi-Fi do mesmo jeito que o Nexus faz (Kadb, Apache-2.0).
- **Ficar no Nexus como canal de distribuição mesmo depois de ser independente.** As Skills (quem está aqui, perguntar à memória, último encontro) deixam outros apps do Nexus e o assistente dele usarem a memória do VisualContext. É visibilidade que sozinhos não teríamos.
- **Dinheiro:** nada no ecossistema proíbe um plugin comercial (ver acima). A cobrança funciona por chave de licença externa, como já estava previsto.
