import threading
import numpy as np


class ModelUnavailable(RuntimeError):
    pass


def compare_embeddings(left, right, threshold=0.68):
    left, right = np.asarray(left, dtype=np.float32), np.asarray(right, dtype=np.float32)
    if left.shape != (512,) or right.shape != (512,):
        raise ValueError("512-dimensional face embeddings required")
    if not np.isfinite(left).all() or not np.isfinite(right).all():
        raise ValueError("Non-finite face embedding")
    norm = float(np.linalg.norm(left) * np.linalg.norm(right))
    if norm <= 0 or not 0.50 <= threshold <= 0.90:
        raise ValueError("Invalid face embedding or threshold")
    score = float(np.clip(np.dot(left, right) / norm, -1, 1))
    matched = score >= threshold
    return {"match": matched, "confidence": score,
            "recommendation": "ACCEPT" if matched else "MANUAL_REVIEW"}


class FaceEngine:
    def __init__(self, settings):
        self.settings, self.model = settings, None
        self.lock = threading.Lock()

    def match(self, card, live):
        # No download fallback. Operator supplies an approved detector/512-dim MobileFaceNet pack.
        with self.lock:
            if self.model is None:
                pack = self.settings.models / "models" / "mobilefacenet"
                if not pack.is_dir() or not list(pack.glob("*.onnx")):
                    raise ModelUnavailable("Approved face models are unavailable")
                from insightface.app import FaceAnalysis
                self.model = FaceAnalysis(name="mobilefacenet", root=str(self.settings.models),
                                          providers=["CPUExecutionProvider"])
                self.model.prepare(ctx_id=-1, det_size=(320, 320))
            vectors = []
            for image in (card, live):
                faces = self.model.get(np.asarray(image)[:, :, ::-1])
                if len(faces) != 1:
                    raise ValueError("Exactly one unobscured face per image is required")
                vectors.append(faces[0].embedding)
            return compare_embeddings(*vectors, self.settings.threshold)
