# AI INCIDENT RESPONSE PLAN

## 1. INCIDENT CLASSIFICATION
- **Tier 1 (Critical):** AI leaks PII, quotes incorrect price resulting in financial loss, or generates offensive content.
- **Tier 2 (High):** AI provides incorrect policy info (e.g., cancellation), AI Gateway goes offline.
- **Tier 3 (Medium):** High volume of fallback responses due to internal system latency.

## 2. RESPONSE WORKFLOW (Tier 1 & 2)
1. **Kill Switch:** IT instantly disables the Guest-facing AI via the Admin Dashboard. Chat UI reverts to "Live Staff Only" mode.
2. **Containment:** Identify affected guests. Export chat logs.
3. **Apology & Recovery:** Duty Manager contacts affected guests immediately.
4. **Root Cause Analysis (RCA):** Dev team analyzes prompts, RAG retrieval logs, and LLM output.
5. **Patch & Restore:** Deploy fix to `constraints.md` or Middleware. QA tests the exact failing prompt. Re-enable AI.

## 3. APOLOGY TEMPLATES (For Staff Use)
*Note: Templates are for STAFF use only. AI must NOT generate compensation promises per constraints.md Rule 2.*

**If AI quoted wrong price:** 
*"Dear [Name], we sincerely apologize. Our virtual assistant provided an inaccurate rate due to a syncing error. The correct rate is [Rate]. As a gesture of goodwill, we will honor [Discount/Amenity]."*

**If AI failed to escalate emergency:**
*"Dear [Name], we are deeply sorry our automated system did not connect you to our team fast enough. We are reviewing this immediately. How can I assist you right now?"*
