# ESCALATION TRIGGERS & PROCEDURES

*Reference: Must comply with `constraints.md` limitations.*

## 1. KEYWORDS (Immediate Hard Escalation)
If the guest's message contains any of the following intents/keywords, escalate immediately:
`refund, cancel, complain, manager, broken, stolen, emergency, fire, doctor, police, medical, hospital, legal`

## 2. ESCALATION WORKFLOW (Hard Escalation)
1. **Transfer Type:** WARM TRANSFER. AI summarizes the guest's issue and context (sentiment, booking ID, summary) into a hidden payload sent to the Staff Dashboard.
2. **SLA (Service Level Agreement):** 
   - Normal Escalation: Staff must acknowledge within **2 minutes**.
   - Emergency Escalation: Immediate visual/audio alert to Duty Manager.
3. **Routing:** Front Desk → (If no response) → Duty Manager → (If system error) → IT Dept.

## 3. SILENT ESCALATION (Quantified Preemptive Monitoring)
- **Condition (ANY of):**
  - Sentiment score in range `[-0.3, 0]` (measured by NLP model).
  - Guest repeats the same question `≥ 2 times` (exact or semantic match).
  - Guest uses escalation keywords but sentiment is `> -0.5` (e.g., asking about cancellation policy peacefully).
- **Action:** Flag conversation with `MONITOR` tag in Staff Dashboard. AI continues responding. If sentiment drops below `-0.5` on the NEXT turn, auto-trigger HARD escalation.
