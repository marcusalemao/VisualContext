"""Run with:  deepeval test run evals/test_memoria.py --identifier "memoria-rodada-1"   (from the repo root, see README)."""
import pytest

from deepeval import assert_test
from deepeval.dataset import EvaluationDataset, Golden

import ai_app
from metrics import MEMORY_METRICS

dataset = EvaluationDataset()
dataset.add_goldens_from_json_file(file_path="evals/.dataset.json")


@pytest.mark.parametrize("golden", dataset.goldens)
def test_memoria(golden: Golden):
    ai_app.run_traced_ai_app(golden.input)
    assert_test(golden=golden, metrics=MEMORY_METRICS)
