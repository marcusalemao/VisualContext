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
