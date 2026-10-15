# Web UI issue log

## Availability search can be followed by a room conflict

- **Reported:** 2026-10-15 during manual booking-flow verification.
- **Observed:** The landing page displayed rooms; after searching for 2026-10-20 through 2026-10-22, it displayed results. Submitting synthetic guest details for a room returned HTTP 409 with the message that the room had just been booked or its status changed. Retrying another displayed result returned the same conflict. No payment was made and no successful test booking was confirmed.
- **Likely contributing integration defects:** The UI could retain stale/default search dates after a result refresh, live and fallback catalog rooms were not clearly distinguished, and server availability output did not include all room presentation data. The availability query also considered current room state for future stays even though booking validation is date-overlap based.
- **Changes made:** The booking UI now carries API room IDs, prices, and versions with the date search; changing dates invalidates the searched result; fallback rooms are visibly marked as demo; HTTP errors are not treated as offline fallback. The availability response includes room metadata and permits future stays when no active booking overlaps (same-day check-in still requires a clean vacant room).
- **Verification limit:** Browser testing found the original conflict, but after the fix a live end-to-end booking could not be repeated because the local web/API/MySQL services were stopped in the verification session. Frontend request/response mapping and Java compilation were checked separately.
