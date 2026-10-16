# Phase 3 AI service

This is a fail-closed implementation scaffold, not an accepted model release. Production uses Python 3.11, CPU inference, offline approved model files, Faiss IndexFlatIP/384, and mandatory mutual TLS. It has no database driver or database credentials; `ai_reader` permissions are unchanged. Do not put real documents, embeddings, prompts, private keys or trained artifacts in Git.

Install `requirements.txt` in an isolated Python 3.11 environment. The Docker dependency build has not been verified on this workstation. Provision these approved, licensed local artifacts read-only:

- `/models/ocr-det`, `/models/ocr-rec`, `/models/ocr-cls`: Paddle inference directories including `inference.pdmodel`.
- `/models/models/mobilefacenet/*.onnx`: compatible InsightFace detector/512-dimensional recognition pack. A generic face model is not proof of MobileFaceNet compliance.
- `/models/all-MiniLM-L6-v2`: complete offline sentence-transformer model.
- `/models/occupancy_prophet.json`: native Prophet serialized model, produced by `train_forecaster.py`. No mislabeled ONNX file is generated.
- `/knowledge/{guest,staff}/hotel_sops.index` and `chunks.json`, built from separately approved audience/language documents by `embed_sops.py`. Only approved PMS policy text may be indexed. The service returns original passages verbatim, not generated policies.

Use `--help` for the exact CLI parameters of `embed_sops.py`, `train_forecaster.py` and `evaluate_forecast.py`. Train only on reviewed daily aggregates exported through `RevenueAggregatorDao`; CSV columns are date/occupancy, never guest or ledger rows. Evaluate on a chronological holdout of at least 30 days. Undefined MAPE with zero actual occupancy is rejected rather than silently altered. Keep training and test periods separate.

`python evaluate_retrieval.py reviewed-questions.jsonl` requires at least 50 independently reviewed rows containing `question`, `audience`, `language`, `expected_sources`. It reports hit@3 and warmed query latency including embedding. This does not measure isolated Faiss search time or prove answer faithfulness; those need separate instrumentation/manual source review. No authoritative SOP corpus or labeled questions were supplied here.

`python generate_synthetic_cccd.py ../target/fresh-synthetic-directory` produces 100 conspicuously invalid cards with zero IDs, synthetic names and no real faces. This verifies fixture generation, not Vietnamese OCR accuracy, FAR, FRR or anti-spoofing. Real accuracy gates require an approved labeled synthetic Vietnamese corpus and appropriately sized independent genuine/impostor trials. A 100-image set alone cannot substantiate FAR <0.01%.

`python mock_server.py` starts the Day-1 HTTP stub on **127.0.0.1:8089 only**, with synthetic vision, RAG and forecast responses. It is for direct UI/contract fixture work. The production Java client intentionally refuses HTTP: use an injected test transport or a test mTLS proxy; do not weaken production TLS to reach this stub. `AI_SERVICE_MOCK=true` enables Java's separately labeled vision mock only. Guest AI remains off by default. No fixture is an authoritative hotel policy.

The Java gateway has a 3-second total deadline and bounded response; Python has two bounded inference slots and a 2.8-second deadline. Timed-out work retains its slot until inference actually finishes. Missing models return an unavailable error; health `UP` is not model acceptance. OCR/face images exist transiently in memory, never in error text or access logs. Deployment must enforce secret ownership/read permissions, certificate SAN `ai-service`, CA/EKU/expiry checks and the 1.5 GB/2 CPU resource cap.

See `docs/phase3-implementation-report.md` and `docs/phase3-release-checklist.md` for measured results and pending gates.

`python evaluate_vision.py APPROVED_SYNTHETIC_DATASET` requires `reviewed-manifest.json` with `synthetic_review_approved:true`, `ocr` rows (file/id_card/full_name/dob/address) and `face_pairs` rows (id_photo/live_face/same_identity boolean). It reports exact all-field OCR accuracy, p95/max inference latency including the first cold sample, FAR/FRR, trial counts and a one-sided95% bound when there are zero false accepts. Missing models/data fail; no model metric is available from fixture-only tests. Independent identities/trials and anti-spoof assessment require separate review.
