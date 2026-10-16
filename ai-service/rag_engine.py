import json
import threading
import numpy as np
from face_engine import ModelUnavailable


class RagEngine:
    def __init__(self, settings):
        self.settings, self.encoder = settings, None
        self.stores = {}
        self.lock = threading.Lock()

    def query(self, message, audience, language="en"):
        if audience not in ("guest", "staff"):
            raise ValueError("Invalid audience")
        with self.lock:
            import faiss
            if self.encoder is None:
                path = self.settings.models / "all-MiniLM-L6-v2"
                if not path.is_dir():
                    raise ModelUnavailable("Approved local embedding model is unavailable")
                from sentence_transformers import SentenceTransformer
                self.encoder = SentenceTransformer(str(path), device="cpu", local_files_only=True)
            if audience not in self.stores:
                folder = self.settings.knowledge / audience
                if not (folder / "hotel_sops.index").is_file():
                    raise ModelUnavailable("Approved SOP index is unavailable")
                index = faiss.read_index(str(folder / "hotel_sops.index"))
                chunks = json.loads((folder / "chunks.json").read_text(encoding="utf-8"))
                if not isinstance(index, faiss.IndexFlatIP) or index.d != 384 or index.ntotal != len(chunks):
                    raise ModelUnavailable("Invalid SOP index")
                self.stores[audience] = index, chunks
            index, chunks = self.stores[audience]
            vector = np.asarray(self.encoder.encode([message], normalize_embeddings=True), dtype="float32")
            scores, ids = index.search(vector, 3)
            hits = [{"source": chunks[int(i)]["source"], "text": chunks[int(i)]["text"], "score": float(s)}
                    for s, i in zip(scores[0], ids[0]) if i >= 0 and s >= 0.80 and chunks[int(i)].get("language") == language]
            # Extractive responses only. No invented policies, rates or commitments.
            return {"answer": "\n\n".join(hit["text"] for hit in hits) if hits else
                    "Let me double-check with the Front Desk team.", "citations": hits,
                    "confidence": max((h["score"] for h in hits), default=0), "escalate": not bool(hits)}
