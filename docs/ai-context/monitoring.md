# AI MONITORING & KPI METRICS

## 1. KEY PERFORMANCE INDICATORS (KPIs)
- **Deflection Rate:** Target `> 60%` (Conversations resolved without Staff intervention).
- **Violation Rate:** Target `< 0.1%` (Instances where AI violates `constraints.md`).
- **Escalation SLA Compliance:** Target `> 95%` (Staff acknowledges warm transfers within 2 minutes).
- **Average Handle Time (AHT):** AI response time target `< 2 seconds`.

## 2. CONTINUOUS MONITORING
- **Sentiment Tracking:** Daily report on average guest sentiment interacting with AI.
- **Keyword Triggers:** Weekly analysis of top reasons for Hard Escalations to optimize FAQs or SOPs.
- **Hallucination Checks:** Weekly random sampling of 100 AI logs by Compliance/QA role to ensure data-access compliance.

## 3. ALERTING THRESHOLDS (Slack / Email)
- **CRITICAL:** Violation Rate exceeds `0.5%` in 1 hour → Alert IT & GM.
- **HIGH:** Escalation SLA drops below `80%` (Staff ignoring chats) → Alert Front Office Manager.
- **MEDIUM:** Fallback response triggered `> 10 times` in an hour (Possible PMS API outage) → Alert IT.
