# HYBRID PROMPT LOADING STRATEGY

## 1. ARCHITECTURE OVERVIEW
The AI Gateway utilizes a 3-layer hybrid approach to manage context limits and ensure absolute adherence to hotel policies.

### Layer 1: Static Injection (The Core)
- **Content:** `constraints.md` (11 hard rules) + System Persona (Guest/Staff).
- **Execution:** Injected into the `system_prompt` of EVERY single LLM invocation.
- **Budget:** ~500 tokens.
- **Purpose:** Non-negotiable rules that must never be missed by retrieval.

### Layer 2: RAG (Retrieval-Augmented Generation)
- **Content:** `edge-cases.md`, `escalation-rules.md`, Hotel FAQs, SOPs.
- **Execution:** Stored in `pgvector`. Relevant chunks are retrieved based on the semantic similarity of the user's query.
- **Mechanism:**
  - Embedding Model: `text-embedding-3-small` (OpenAI).
  - Retrieval: `Top-K = 3`.
  - Similarity Threshold: `0.75`.
- **Budget:** ~1000 - 1500 tokens per retrieval.
- **Fallback:** If RAG returns 0 results (no match > threshold), the AI relies solely on Static Injection and triggers the "No Data" fallback response.

### Layer 3: Middleware Enforcement (Invisible to AI)
- **Content:** `data-access.md` (RBAC), Cache TTLs, PII masking.
- **Execution:** Runs in the FastAPI middleware *before* the prompt reaches the LLM and *after* the LLM generates a response.
- **Purpose:** Code-level enforcement. The AI cannot "hallucinate" its way out of this layer because it does not control the API payload or the final JSON mask.

## 2. TOKEN BUDGET ESTIMATE (Per Request)
| Component | Token Estimate | Percentage of Context |
| :--- | :--- | :--- |
| Static System Prompt | ~500 | 12.5% |
| Conversation History (Last 5) | ~1000 | 25% |
| RAG Context (Top-K) | ~1500 | 37.5% |
| User Input | ~100 | 2.5% |
| **Total Input Prompt** | **~3100 Tokens** | **< 4K Token Limit Optimized** |
