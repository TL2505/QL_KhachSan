"""Measure approved, external labeled synthetic images without printing PII."""
import argparse
import base64
import json
import math
import time
from pathlib import Path
from config import Settings
from ocr_engine import OcrEngine
from face_engine import FaceEngine
from images import decode_image


def image(root, relative):
    path = (root / relative).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError("Image path must remain inside the approved dataset")
    return decode_image(base64.b64encode(path.read_bytes()).decode("ascii"))


def latency(samples):
    ordered = sorted(samples)
    return {"samples": len(samples), "p95_ms": ordered[min(len(ordered)-1, math.ceil(.95*len(ordered))-1)], "max_ms": max(ordered)}


def evaluate(root):
    manifest = json.loads((root / "reviewed-manifest.json").read_text(encoding="utf-8"))
    rows, pairs = manifest["ocr"], manifest["face_pairs"]
    if len(rows) < 100 or not pairs or manifest.get("synthetic_review_approved") is not True:
        raise ValueError("Reviewed synthetic dataset with >=100 OCR cases and labeled face pairs required")
    settings = Settings.load()
    ocr, face = OcrEngine(settings), FaceEngine(settings)
    # No warm-up excluded: first sample includes model loading. Record both max and p95.
    correct, ocr_ms, face_ms = 0, [], []
    false_accepts = false_rejects = genuine = impostors = 0
    for row in rows:
        encoded = image(root, row["file"])
        start = time.perf_counter()
        result = ocr.recognize(encoded)
        ocr_ms.append((time.perf_counter()-start)*1000)
        correct += all(result[field] == row[field] for field in ("id_card", "full_name", "dob", "address"))
    for pair in pairs:
        if not isinstance(pair["same_identity"], bool):
            raise ValueError("Boolean face ground truth required")
        card, live = image(root, pair["id_photo"]), image(root, pair["live_face"])
        start = time.perf_counter()
        result = face.match(card, live)
        face_ms.append((time.perf_counter()-start)*1000)
        if pair["same_identity"]:
            genuine += 1
            false_rejects += not result["match"]
        else:
            impostors += 1
            false_accepts += result["match"]
    if not genuine or not impostors:
        raise ValueError("Both genuine and impostor pairs required")
    return {"ocr_all_fields_exact_accuracy": correct/len(rows), "ocr_latency": latency(ocr_ms),
            "face_latency": latency(face_ms), "threshold": settings.threshold,
            "genuine_pairs": genuine, "impostor_pairs": impostors,
            "far": false_accepts/impostors, "frr": false_rejects/genuine,
            "zero_error_far_one_sided_95_upper": 1-.05**(1/impostors) if false_accepts == 0 else None,
            "independent_trial_and_anti_spoof_review": "PENDING", "includes_cold_first_sample": True}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("approved_synthetic_dataset", type=Path)
    # Errors propagate to a nonzero exit; no fake acceptance numbers on missing models.
    print(json.dumps(evaluate(parser.parse_args().approved_synthetic_dataset), indent=2))
