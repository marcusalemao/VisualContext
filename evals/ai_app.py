"""VisualContext memory answers ("o que eu fiz hoje?"), ported from android/.../Recall.kt for evals.

The phone filters the user's records by date, writes them as lines, and asks Gemini for a spoken answer plus a
short card for the lens. This file does the same over a fictional memory (data/memoria.json), so evals never touch
anyone's real memories. Gemini is reached through the VisualContext server (functions/gem) with an eval access
code, so no Google key lives here. Keep PROMPT in sync with Recall.kt.
"""
import json
import os
import re
from datetime import datetime, timedelta
from pathlib import Path

import requests
from deepeval.tracing import observe, update_current_span, update_current_trace

GEM = "https://vzqjwhlpwvqrosoiwiin.supabase.co/functions/v1/gem/v1beta/models/"
MODEL = os.environ.get("VC_EVAL_MODEL", "gemini-flash-latest")
DATA = json.loads((Path(__file__).parent / "data" / "memoria.json").read_text())
WEEK = ["seg", "ter", "qua", "qui", "sex", "sáb", "dom"]


def gemini(prompt: str, json_out: bool = False) -> str:
    """One text call to Gemini through the VisualContext server (x-vc-token = an eval device token)."""
    body = {"contents": [{"parts": [{"text": prompt}]}], "generationConfig": {"temperature": 0.2}}
    if json_out:
        body["generationConfig"]["responseMimeType"] = "application/json"
    r = requests.post(GEM + MODEL + ":generateContent", json=body, timeout=60,
                      headers={"x-vc-token": os.environ["VC_EVAL_TOKEN"], "Content-Type": "application/json"})
    r.raise_for_status()
    parts = r.json()["candidates"][0]["content"]["parts"]
    return "".join(p.get("text", "") for p in parts if not p.get("thought"))


def fmt(iso: str) -> str:
    d = datetime.fromisoformat(iso)
    return f"{WEEK[d.weekday()]} {d:%d/%m %H:%M}"


def days_for(question: str) -> int:
    """How far back the phone looks (the Live passes 'dias'; this mirrors its usual choices)."""
    q = question.lower()
    if "hoje" in q: return 1
    if "ontem" in q: return 2
    if "semana" in q: return 7
    return 30


@observe(type="retriever", embedder="date-filter")
def retrieve(question: str) -> list[str]:
    now = datetime.fromisoformat(DATA["today"])
    since = now - timedelta(days=days_for(question))
    since = since.replace(hour=0, minute=0) if days_for(question) <= 2 else since
    lines = []
    for e in DATA["encounters"]:
        if datetime.fromisoformat(e["at"]) < since: continue
        who = e.get("person_name")
        kind = "conversa com o assistente" if e["capture_mode"] == "live" else "encontro" + (f" com {who}" if who else "")
        lines.append(" · ".join(x for x in [fmt(e["at"]), kind, e.get("place"), e.get("summary")] if x))
    for n in DATA["notes"]:
        if datetime.fromisoformat(n["at"]) >= since: lines.append(f"nota {fmt(n['at'])} · {n['title']}")
    for t in DATA["things"]:
        if datetime.fromisoformat(t["seen_at"]) >= since: lines.append(f"objeto {fmt(t['seen_at'])} · {t['name']} em {t['location_description']}")
    update_current_span(input=question, retrieval_context=lines)
    return lines


PROMPT = """Você responde pela memória pessoal do usuário (óculos VisualContext). Hoje é {today}.
Pergunta: "{question}"
Registros (mais recentes primeiro):
{records}
Responda em JSON: {{"fala": "resposta falada, em português, 1 a 3 frases, concreta (nomes, lugares, horas)", "titulo": "título curto para a lente, até 24 caracteres", "linhas": ["até 5 linhas curtas para a lente, cada uma até 38 caracteres, começando pela hora quando fizer sentido"]}}. Só use o que está nos registros."""


@observe(type="llm", model=MODEL)
def answer(question: str, records: list[str]) -> dict:
    prompt = PROMPT.format(today=fmt(DATA["today"]), question=question, records="\n".join("- " + r for r in records))
    raw = gemini(prompt, json_out=True)
    j = json.loads(re.sub(r"^```(json)?|```$", "", raw.strip()))
    update_current_span(input=prompt, output=raw)
    return j


@observe(type="agent", name="recall")
def run_traced_ai_app(question: str) -> str:
    records = retrieve(question)
    if not records:
        out = "Não achei nada nesse período."
        update_current_trace(input=question, output=out, retrieval_context=[])
        return out
    j = answer(question, records)
    card = f"[lente] {j.get('titulo', '')}: " + " | ".join(j.get("linhas", []))
    out = f"{j.get('fala', '')}\n{card}"
    update_current_trace(input=question, output=out, retrieval_context=records, metadata={"titulo": j.get("titulo", ""), "linhas": j.get("linhas", [])})
    return out
