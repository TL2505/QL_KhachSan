import json
import math
import threading
from datetime import date, timedelta
from face_engine import ModelUnavailable


class ForecastEngine:
    def __init__(self, settings):
        self.settings, self.model = settings, None
        self.lock = threading.Lock()

    def forecast(self, start_date, days):
        if not 1 <= days <= 90:
            raise ValueError("Invalid forecast horizon")
        start = date.fromisoformat(start_date)
        with self.lock:
            if self.model is None:
                path = self.settings.models / "occupancy_prophet.json"
                if not path.is_file():
                    raise ModelUnavailable("Approved forecast model is unavailable")
                from prophet.serialize import model_from_json
                self.model = model_from_json(path.read_text(encoding="utf-8"))
            import pandas as pd
            dates = [start + timedelta(days=i) for i in range(days)]
            prediction = self.model.predict(pd.DataFrame({"ds": dates}))
            result = []
            for day, row in zip(dates, prediction.itertuples()):
                if not all(math.isfinite(float(x)) for x in (row.yhat, row.yhat_lower, row.yhat_upper)):
                    raise ModelUnavailable("Forecast contains non-finite values")
                occupancy = min(100.0, max(0.0, float(row.yhat)))
                lower = min(occupancy, max(0.0, float(row.yhat_lower)))
                upper = max(occupancy, min(100.0, float(row.yhat_upper)))
                multiplier = 1.50 if occupancy >= 85 else 1.30 if occupancy >= 70 else 1.15 if occupancy >= 50 else 1.00
                result.append({"date": day.isoformat(), "predicted_occupancy": occupancy,
                    "recommended_multiplier": multiplier, "confidence_interval": [lower, upper]})
            return result
