"""Offline build only: explicit approved, non-PII knowledge input separated by audience."""
import argparse
import json
from pathlib import Path


def chunks(tokenizer, text):
    encoded = tokenizer(text, add_special_tokens=False, return_offsets_mapping=True)
    tokens, offsets = encoded["input_ids"], encoded["offset_mapping"]
    for start in range(0, len(tokens), 250):
        yield text[offsets[start][0]:offsets[min(start + 300, len(tokens)) - 1][1]]
        if start + 300 >= len(tokens):
            break


def build(source, model, output):
    import faiss
    import numpy as np
    from sentence_transformers import SentenceTransformer
    encoder = SentenceTransformer(str(model), device="cpu", local_files_only=True)
    records = json.loads(source.read_text(encoding="utf-8"))
    for audience in ("guest", "staff"):
        rows = [{"source": row["source"], "text": part, "language": row["language"]} for row in records
                if row["audience"] == audience and row.get("approved") is True
                and row.get("language") in ("en", "vi", "ja", "zh")
                for part in chunks(encoder.tokenizer, row["text"])]
        if not rows:
            raise ValueError("Both audiences require approved SOP documents")
        vectors = np.asarray(encoder.encode([r["text"] for r in rows], normalize_embeddings=True), dtype="float32")
        if vectors.shape[1] != 384:
            raise ValueError("ADR-08 requires 384-dimensional vectors")
        index = faiss.IndexFlatIP(384)
        index.add(vectors)
        folder = output / audience
        folder.mkdir(parents=True, exist_ok=True)
        faiss.write_index(index, str(folder / "hotel_sops.index"))
        (folder / "chunks.json").write_text(json.dumps(rows, ensure_ascii=False), encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--model", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    build(args.source, args.model, args.output)
