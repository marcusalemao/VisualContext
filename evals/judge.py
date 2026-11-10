"""Gemini as DeepEval's judge, through the VisualContext server (no Google key here; see ai_app.gemini)."""
import os

from deepeval.models import DeepEvalBaseLLM

import ai_app


class VCGemini(DeepEvalBaseLLM):
    def __init__(self, model: str | None = None):
        self.model = model or os.environ.get("VC_JUDGE_MODEL", "gemini-pro-latest")

    def load_model(self):
        return self.model

    def generate(self, prompt: str, schema=None, **_):
        old = ai_app.MODEL
        ai_app.MODEL = self.model
        try:
            text = ai_app.gemini(prompt, json_out=schema is not None)
        finally:
            ai_app.MODEL = old
        return schema.model_validate_json(text) if schema is not None else text

    async def a_generate(self, prompt: str, schema=None, **kw):
        return self.generate(prompt, schema, **kw)

    def get_model_name(self):
        return f"{self.model} (via VisualContext)"


JUDGE = VCGemini()
