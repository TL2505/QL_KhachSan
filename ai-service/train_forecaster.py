"""Train with aggregate daily occupancy only. Never accepts raw bookings or customer data."""
import argparse
from pathlib import Path


def load_history(path):
    import pandas as pd
    frame = pd.read_csv(path)
    if set(frame.columns) != {"date", "occupancy"}:
        raise ValueError("Only date,occupancy aggregate columns are permitted")
    frame = frame.rename(columns={"date": "ds", "occupancy": "y"})
    frame["ds"] = pd.to_datetime(frame["ds"], errors="raise")
    if frame["ds"].duplicated().any() or not frame["y"].between(0, 100).all():
        raise ValueError("Invalid history values")
    return frame.sort_values("ds").reset_index(drop=True)


def fit(frame):
    from prophet import Prophet
    if len(frame) < 400:
        raise ValueError("At least 400 observed daily aggregates are required")
    model = Prophet(weekly_seasonality=True, yearly_seasonality=True, daily_seasonality=False)
    model.add_country_holidays(country_name="VN")
    return model.fit(frame)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--history", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    from prophet.serialize import model_to_json
    model = fit(load_history(args.history))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(model_to_json(model), encoding="utf-8")
