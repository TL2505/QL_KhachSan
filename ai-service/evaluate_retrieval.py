"""Offline, approved SOP evaluation. Never substitutes fixtures for real retrieval."""
import argparse
import json
import time
from config import Settings
from rag_engine import RagEngine


def evaluate(path):
    rows = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if len(rows) < 50:
        raise ValueError("At least 50 independently reviewed questions are required")
    engine = RagEngine(Settings.load())
    # Warm-up is excluded explicitly; cold startup remains a separate acceptance gate.
    engine.query(rows[0]["question"], rows[0]["audience"], rows[0]["language"])
    correct, elapsed = 0, []
    for row in rows:
        if not row.get("expected_sources"):
            raise ValueError("Reviewed expected_sources are required for every question")
        start = time.perf_counter()
        result = engine.query(row["question"], row["audience"], row["language"])
        elapsed.append((time.perf_counter() - start) * 1000)
        actual = {item["source"] for item in result["citations"]}
        correct += bool(actual.intersection(row["expected_sources"]))
    ordered = sorted(elapsed)
    return {"questions": len(rows), "hit_at_3": correct / len(rows),
            "warm_query_p95_ms": ordered[min(len(rows)-1, int(len(rows)*.95))],
            "includes_embedding": True, "answer_faithfulness_review": "PENDING"}


if __name__ == "__main__":
    from pathlib import Path
    parser = argparse.ArgumentParser()
    parser.add_argument("reviewed_questions", type=Path)
    args = parser.parse_args()
    print(json.dumps(evaluate(args.reviewed_questions), indent=2))
