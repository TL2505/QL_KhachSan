import base64
import io
import sys
from pathlib import Path
import numpy as np
import pytest
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from config import Settings
from images import decode_image
from face_engine import compare_embeddings
from ocr_engine import parse_lines


def image_data():
    data = io.BytesIO()
    Image.new("RGB", (64, 64), "white").save(data, format="PNG")
    return base64.b64encode(data.getvalue()).decode()


def test_threshold_and_synthetic_vector_boundaries(monkeypatch):
    monkeypatch.delenv("AI_FACE_MATCH_THRESHOLD", raising=False)
    assert Settings.load().threshold == 0.68
    monkeypatch.setenv("AI_FACE_MATCH_THRESHOLD", "0.91")
    with pytest.raises(ValueError):
        Settings.load()
    left = np.zeros(512); left[0] = 1
    for similarity in (0.50, 0.67, 0.68, 0.69, 0.90, 1.0):
        right = np.zeros(512)
        right[:2] = similarity, np.sqrt(1 - similarity ** 2)
        result = compare_embeddings(left, right)
        assert result["match"] == (similarity >= 0.68)
        assert result["recommendation"] == ("ACCEPT" if similarity >= 0.68 else "MANUAL_REVIEW")
    with pytest.raises(ValueError):
        compare_embeddings(np.zeros(512), left)
    with pytest.raises(ValueError):
        compare_embeddings(np.ones(128), left)
    with pytest.raises(ValueError):
        compare_embeddings(np.full(512, np.nan), left)


def test_bounded_image_decoder_and_invalid_content():
    assert decode_image(image_data()).size == (64, 64)
    for malformed in ("invalid!", "", base64.b64encode(b"not an image").decode(), "x" * 7_000_000):
        with pytest.raises(ValueError):
            decode_image(malformed)


def test_ocr_parser_only_with_synthetic_in_memory_fields():
    number = "0" * 12  # Deliberately invalid synthetic identity; never a real CCCD.
    result = parse_lines([number, "Họ và tên: KHÁCH KIỂM THỬ", "01/01/2000", "Nơi thường trú: ĐỊA CHỈ GIẢ LẬP"])
    assert result["id_card"] == number
    assert result["full_name"] == "KHÁCH KIỂM THỬ"
    assert result["dob"] == "2000-01-01"
    with pytest.raises(ValueError):
        parse_lines(["Unreadable synthetic card"])
    with pytest.raises(ValueError):
        parse_lines([number, "Full name: SYNTHETIC", "31/02/2000"])


def test_http_offline_invalid_and_oversize_do_not_echo_identity(monkeypatch, tmp_path):
    from fastapi.testclient import TestClient
    import app
    app.ocr.settings = Settings(0.68, tmp_path, tmp_path)
    app.face.settings = app.ocr.settings
    with TestClient(app.app) as client:
        assert client.get("/health").json()["acceptance"] == "PENDING"
        image = image_data()
        response = client.post("/ocr", json={"image": image})
        assert response.status_code == 503
        assert image not in response.text
        response = client.post("/face-match", json={"id_photo": image, "live_face": image})
        assert response.status_code == 503
        assert image not in response.text
        response = client.post("/ocr", json={"image": image, "extra": "SYNTHETIC-PRIVATE-PAYLOAD"})
        assert response.status_code == 422
        assert "SYNTHETIC-PRIVATE-PAYLOAD" not in response.text
        assert client.post("/ocr", json={"image": "bad!"}).status_code == 422
        assert client.post("/ocr", content=b"x" * 14_000_001).status_code == 413


def test_forecast_metrics_and_missing_model(tmp_path):
    from evaluate_forecast import metrics
    from forecast_engine import ForecastEngine
    from face_engine import ModelUnavailable
    assert metrics([50.0, 50.0], [45.0, 55.0])["mape"] == 10.0
    with pytest.raises(ValueError):
        metrics([0.0], [1.0])
    engine = ForecastEngine(Settings(0.68, tmp_path, tmp_path))
    with pytest.raises(ModelUnavailable):
        engine.forecast("2026-10-16", 30)
