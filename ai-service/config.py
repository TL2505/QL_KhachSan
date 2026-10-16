"""No model downloads, database credentials or insecure TLS defaults at runtime."""
from dataclasses import dataclass
import os
from pathlib import Path


@dataclass(frozen=True)
class Settings:
    threshold: float
    models: Path
    knowledge: Path

    @classmethod
    def load(cls):
        threshold = float(os.getenv("AI_FACE_MATCH_THRESHOLD", "0.68"))
        if not 0.50 <= threshold <= 0.90:
            raise ValueError("AI_FACE_MATCH_THRESHOLD must be within [0.50, 0.90]")
        return cls(threshold, Path(os.getenv("AI_MODELS_DIR", "/models")),
                   Path(os.getenv("AI_KNOWLEDGE_DIR", "/knowledge")))
