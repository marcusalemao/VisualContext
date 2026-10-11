# What lives outside the code (inventory, 11/10/2026)

[🇬🇧 English](#en--english) | [🇧🇷 Português](#pt--português)

## EN — English

Collected from Beto on 11/10 and checked against the app through the Base44 API (read only).

**Workflows (7, in `workflows/`)**
- *Sync Live Companion* × 6: any change in FaceContextPerson, FaceContextEncounter, EpisodicMemory, PersonInteraction, DynamicSkill or HUDNotification calls `lcSyncToManager`, which mirrors it into the Manager app (6aa30727). The source→target map is the SyncMap entity (123 rows).
  - ⚠️ The people sync has been **failing since 10/10 09:28** (7 failed runs, before the 11/10 access lock). Still to investigate.
- *FaceContext — Gemini spend alert*: Mondays 09:00 São Paulo; sums AIUsageLog against the R$100/month budget and warns on WhatsApp near 80%.
- Marcus's personal workflows (reminders, LinkedIn) are in the same app and stay out of this repo.

**Connectors:** Gmail (Marcus's account) is used by `fcMessages` to show "pending email" (reads from/subject/date). Google Drive is authorized but unused by the backend.

**Storage:** only the 4 pages in `pages/`. Person photos are data URIs inside the records or external avatar URLs (Gravatar, Clearbit, ui-avatars).

**Data backup:** 248 records exported as JSON to Marcus's Google Drive, folder "VisualContext backup 2026-10-11". Never in this repo.

**Rules that lived only in Beto's memory** (now written down; ⚠️ = differs from a later decision, Marcus decides):
1. Audio: rolling buffer, no raw audio kept; 30 s cleanup on silence; a silence under 5 s resumes the same encounter.
2. Legacy FaceContext (fc.alema.io) kept untouched, VisualContext separate; AI response cache keyed by a hash of the full input.
3. ✅ Decided 11/10: every conversation is transcribed, known person or not; discarded only on cancel or a too-short encounter. Old rule: transcript only when a known person is in the conversation. (The 11/10 privacy spec also records encounters with unnamed people.)
4. Dossier: the dashboard card is the exact HUD preview; data comes through the conversational agent, never forms; every fact shows its source.
5. ✅ Decided 11/10: during an encounter the person card is always short (name, how you know each other, last time); the full card only in Recall mode. Old rule: name + short descriptor, no expandable card (10/10).
6. Business: free app, bring your own key, paid "all set up" plan. Matches Community/Plus.
7. Ecosystem: VisualContext records; navigation goes to Anezium's GMaps, assistance to the Nexus Assistant. Competitor to watch: LightMem-Ego.
8. Security: x-vc-token or Base44 login, never trust Origin; ~60 req/min.
9. Gemini budget R$100/month, alert at 80%.
10. Beto × Claude protocol: tie = Marcus decides. (The old code moratorium was replaced on 11/10: Claude and Beto work directly, always with a rollback point.)

## PT — Português

Levantado com o Beto em 11/10 e conferido no app pela API do Base44 (só leitura).

**Workflows (7, em `workflows/`)**
- *Sync Live Companion* × 6: qualquer mudança em FaceContextPerson, FaceContextEncounter, EpisodicMemory, PersonInteraction, DynamicSkill ou HUDNotification chama a `lcSyncToManager`, que copia para o app Manager (6aa30727). O mapa origem→destino é a entidade SyncMap (123 linhas).
  - ⚠️ A sincronização de pessoas **falha desde 10/10 às 09:28** (7 execuções com falha, antes do fechamento de acesso de 11/10). A investigar.
- *FaceContext — Alerta de Gastos Gemini*: segundas, 09:00 de São Paulo; soma o AIUsageLog contra o orçamento de R$100/mês e avisa no WhatsApp perto de 80%.
- Os workflows pessoais do Marcus (lembretes, LinkedIn) estão no mesmo app e ficam fora deste repositório.

**Conectores:** o Gmail (conta do Marcus) é usado pela `fcMessages` para mostrar "e-mail pendente" (lê remetente, assunto e data). O Google Drive está autorizado, mas o backend não usa.

**Storage:** só as 4 páginas de `pages/`. As fotos das pessoas são data URIs dentro dos registros ou URLs externas de avatar (Gravatar, Clearbit, ui-avatars).

**Backup dos dados:** 248 registros exportados em JSON para o Google Drive do Marcus, pasta "VisualContext backup 2026-10-11". Nunca neste repositório.

**Regras que viviam só na memória do Beto** (agora escritas; ⚠️ = diverge de uma decisão posterior, o Marcus decide):
1. Áudio: buffer rolante, nenhum áudio bruto guardado; limpeza de 30 s no silêncio; silêncio de menos de 5 s retoma o mesmo encontro.
2. FaceContext legado (fc.alema.io) intocado, VisualContext separado; cache de respostas da IA indexado pelo hash da entrada completa.
3. ✅ Decidido em 11/10: toda conversa é transcrita, com pessoa conhecida ou não; descartada só se cancelada ou curta demais. Regra antiga: transcrição só com pessoa conhecida na conversa. (A spec de privacidade de 11/10 também registra encontros com pessoas sem nome.)
4. Dossiê: o card do dashboard é a prévia exata do HUD; os dados entram pelo agente conversacional, nunca por formulário; cada fato mostra a origem.
5. ✅ Decidido em 11/10: durante o encontro o card da pessoa é sempre curto (nome, de onde se conhecem, última vez); o card completo só no modo Recordar. Regra antiga: só nome e descritor breve, sem card expansível (10/10).
6. Negócio: app grátis, chave própria grátis, plano pago "tudo configurado". Bate com Community/Plus.
7. Ecossistema: o VisualContext registra; navegação vai para o GMaps do Anezium, assistência para o Assistant do Nexus. Concorrente a observar: LightMem-Ego.
8. Segurança: x-vc-token ou login Base44, nunca confiar em Origin; ~60 req/min.
9. Orçamento Gemini R$100/mês, alerta em 80%.
10. Protocolo Beto × Claude: empate = o Marcus decide. (A antiga moratória de código foi substituída em 11/10: Claude e Beto trabalham direto, sempre com ponto de volta.)
