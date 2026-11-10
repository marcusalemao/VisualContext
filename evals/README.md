# Evals — memory answers

**EN.** DeepEval suite for the answers the glasses give about the user's own memory ("o que eu fiz hoje?",
"quando vi o Bruno?", "onde deixei as chaves?"). `ai_app.py` is a Python port of `Recall.kt` (same filter, same
prompt) running over a **fictional** memory in `data/memoria.json`; real memories are never used. Gemini — for the
app and for the judge — is reached through the VisualContext server (`functions/gem`) with an eval access code, so no
Google key is stored here and every call counts against that code's limits.

- `generate_dataset.py` — DeepEval Synthesizer over the fictional memory → `.dataset.json` (~40 goldens).
- `metrics.py` — Faithfulness (nothing invented), Answer Relevancy, and a G-Eval for correctness and concreteness.
- `test_memoria.py` — the pytest suite, traced (retriever span + LLM span).

Run from the repo root:

```bash
pip install deepeval requests
export VC_EVAL_TOKEN=vc_…          # device token from an eval invite code (never commit it)
deepeval login                      # optional: results and traces on Confident AI
python evals/generate_dataset.py    # once, or when data/memoria.json changes
deepeval test run evals/test_memoria.py --identifier "memoria-rodada-1"
```

When the prompt changes in `Recall.kt`, change `PROMPT` in `ai_app.py` too.

# Avaliações — respostas da memória

**PT.** Conjunto de avaliações (DeepEval) para as respostas que os óculos dão sobre a memória da pessoa.
`ai_app.py` copia o `Recall.kt` (mesmo filtro, mesmo prompt) e roda sobre uma memória **fictícia**
(`data/memoria.json`); memórias reais nunca entram. O Gemini, do app e do juiz, passa pelo servidor do VisualContext
com um código de acesso de avaliação: nenhuma chave do Google fica aqui, e o gasto conta nos limites desse código.
Quando o prompt mudar no `Recall.kt`, mude o `PROMPT` em `ai_app.py` também.
