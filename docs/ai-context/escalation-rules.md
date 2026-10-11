# TRIGGER CONDITIONS & ESCALATION FLOW

*Note: Must strictly follow limits in `constraints.md`.*

## 1. KEYWORDS (Immediate Hard Escalation)
If the guest's message contains any of the following keywords/intents, transfer immediately:
`refund, cancel, complain, manager, broken, stolen, emergency, fire, doctor, police, medical, hospital, legal`

## 2. ESCALATION FLOW (Hard Escalation)
1. **Transfer Type:** WARM TRANSFER. AI summarizes the guest's issue and context (sentiment, booking code, summary) into a hidden payload sent to the Staff Dashboard.
2. **SLA (Service Level Agreement):** 
   - Normal Escalation: Staff must acknowledge within **2 minutes**.
   - Emergency Escalation: Immediate visual/audio alarm to Duty Manager.
3. **Routing:** Front Desk → (If no response) → Duty Manager → (If system error) → IT Dept.

## 3. SOFT ESCALATION (Quantified Shadow Monitoring)
- **Conditions (ANY of these):**
  - Sentiment score is between `[-0.3, 0]` (measured by NLP model).
  - Guest repeats the same question `≥ 2 times` (exact or semantic match).
  - Guest uses an escalation keyword but sentiment is `> -0.5` (e.g., asking about cancellation policy normally).
- **Action:** Tag the conversation as `MONITOR` on the Staff Dashboard. AI continues to respond. If sentiment drops below `-0.5` on the NEXT turn, automatically trigger HARD ESCALATION.
