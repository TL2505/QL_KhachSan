# AI MONITORING & KPIs

## 1. KEY METRICS (KPIs)
- **Deflection Rate:** Target `> 60%` (Conversations resolved without staff intervention).
- **Violation Rate:** Target `< 0.1%` (AI violating `constraints.md`).
- **Escalation SLA Compliance:** Target `> 95%` (Staff acknowledges warm transfer within 2 minutes).
- **Average Handling Time (AHT):** Target AI response speed `< 2 seconds`.

## 2. CONTINUOUS MONITORING
- **Sentiment Tracking:** Daily report on average guest sentiment using AI.
- **Trigger Keywords:** Weekly analysis of top reasons causing Hard Escalation to optimize FAQs or SOPs.
- **Hallucination Checks:** Weekly, Compliance/QA role randomly samples 100 AI logs to verify data rule adherence.

## 3. ALARM THRESHOLDS (Slack / Email)
- **CRITICAL:** Violation rate exceeds `0.5%` in 1 hour → Alert IT & GM.
- **HIGH:** Escalation SLA compliance drops below `80%` (Staff ignoring chats) → Alert Front Office Manager (FOM).
- **MEDIUM:** Fallbacks triggered `> 10 times` in an hour (Possible PMS API error) → Alert IT.
