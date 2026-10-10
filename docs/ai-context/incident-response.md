# AI INCIDENT RESPONSE PLAN

## 1. INCIDENT CLASSIFICATION
- **Tier 1 (Critical):** AI leaks PII data, quotes wrong rates causing financial loss, or generates offensive content.
- **Tier 2 (High):** AI provides incorrect policy info (e.g., cancellation), AI Gateway crashes.
- **Tier 3 (Medium):** Fallback volume spikes due to slow internal network.

## 2. RESPONSE PROCEDURE (Tier 1 & 2)
1. **Kill Switch:** IT immediately disables guest-facing AI via Admin Dashboard. Chat interface switches to "Live Agent Only" mode.
2. **Containment:** Identify affected guests. Export chat logs.
3. **Apology & Remediation:** Duty Manager immediately contacts affected guests.
4. **Root Cause Analysis (RCA):** Dev team analyzes prompts, RAG search logs, and LLM generated outputs.
5. **Patch & Restore:** Deploy fix to `constraints.md` or Middleware. QA tests the exact failing prompt. Re-enable AI.

## 3. APOLOGY TEMPLATES (For Staff)
*Note: These templates are ONLY FOR STAFF. AI IS NOT ALLOWED to generate compensation promises per Rule 2 in constraints.md.*

**If AI quoted incorrect rates:**
*"Dear [Name], we sincerely apologize. Our virtual assistant provided an incorrect rate due to a system sync error. The correct rate is [Rate]. As a gesture of goodwill, we will apply [Discount/Free Service]."*

**If AI failed to trigger emergency escalation in time:**
*"Dear [Name], we are deeply sorry that the automated system did not connect you with our staff promptly. We are reviewing this issue immediately. How may I assist you right now?"*
