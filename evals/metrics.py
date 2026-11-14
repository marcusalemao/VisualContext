"""Metrics for the memory answers. Kept apart from the test file so thresholds live in one place."""
from deepeval.metrics import AnswerRelevancyMetric, FaithfulnessMetric, GEval
from deepeval.test_case import LLMTestCaseParams

from judge import JUDGE

# Nothing invented: every fact in the answer must be in the records (the prompt says "Só use o que está nos registros").
faithfulness = FaithfulnessMetric(threshold=0.8, model=JUDGE, include_reason=True)
# The answer is about what was asked.
relevancy = AnswerRelevancyMetric(threshold=0.7, model=JUDGE, include_reason=True)
# Correct against the expected answer, and concrete the way the glasses need (names, places, times; dates resolved).
correctness = GEval(
    name="Correção e concretude",
    criteria=(
        "Compare a resposta (actual output) com a resposta esperada. A resposta deve trazer os mesmos fatos que importam "
        "(quem, onde, quando, o que foi combinado), com nomes, lugares e horas concretos, e datas relativas resolvidas "
        "certas para o dia de hoje. Penalize fatos errados, omissões do essencial e respostas vagas. Ignore diferenças de estilo."
    ),
    evaluation_params=[LLMTestCaseParams.INPUT, LLMTestCaseParams.ACTUAL_OUTPUT, LLMTestCaseParams.EXPECTED_OUTPUT],
    threshold=0.6,
    model=JUDGE,
)

MEMORY_METRICS = [faithfulness, relevancy, correctness]
