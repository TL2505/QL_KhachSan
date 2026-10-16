"""Chronological holdout. Output is measured evidence, never an embedded claimed MAPE."""
import argparse
import json
import math
from pathlib import Path
from train_forecaster import load_history, fit


def metrics(actual, predicted):
    if len(actual) != len(predicted) or not actual or any(x <= 0 for x in actual):
        raise ValueError("MAPE requires aligned, positive actual occupancy observations")
    if not all(math.isfinite(x) for x in [*actual, *predicted]):
        raise ValueError("Finite observations required")
    n = len(actual)
    return {"mape": sum(abs((a-p)/a) for a, p in zip(actual, predicted))*100/n,
            "rmse": math.sqrt(sum((a-p)**2 for a, p in zip(actual, predicted))/n), "samples": n}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--history", type=Path, required=True)
    parser.add_argument("--holdout-days", type=int, default=30)
    args = parser.parse_args()
    frame = load_history(args.history)
    if args.holdout_days < 30 or len(frame) < 400 + args.holdout_days:
        raise ValueError("Need at least 400 training observations and a 30-day holdout")
    train, holdout = frame.iloc[:-args.holdout_days], frame.iloc[-args.holdout_days:]
    model = fit(train)
    predictions = model.predict(holdout[["ds"]])["yhat"].clip(0, 100).tolist()
    result = metrics(holdout["y"].tolist(), predictions)
    result["status"] = "PASS" if result["mape"] < 8.5 else "FAIL"
    print(json.dumps(result))
    raise SystemExit(0 if result["status"] == "PASS" else 1)
