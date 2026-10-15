# Web UI issue log

## Availability search can be followed by a room conflict

- **Reported:** 2026-10-15 during manual booking-flow verification.
- **Observed:** The landing page displayed rooms; after searching for 2026-10-20 through 2026-10-22, it displayed results. Submitting synthetic guest details for a room returned HTTP 409 with the message that the room had just been booked or its status changed. Retrying another displayed result returned the same conflict. No payment was made and no successful test booking was confirmed.
- **Likely contributing integration defects:** The UI could retain stale/default search dates after a result refresh, live and fallback catalog rooms were not clearly distinguished, and server availability output did not include all room presentation data. The availability query also considered current room state for future stays even though booking validation is date-overlap based.
- **Changes made:** The booking UI now carries API room IDs, prices, and versions with the date search; changing dates invalidates the searched result; fallback rooms are visibly marked as demo; HTTP errors are not treated as offline fallback. The availability response includes room metadata and permits future stays when no active booking overlaps (same-day check-in still requires a clean vacant room).
- **Verification limit:** Browser testing found the original conflict, but after the fix a live end-to-end booking could not be repeated because the local web/API/MySQL services were stopped in the verification session. Frontend request/response mapping and Java compilation were checked separately.

## Outstanding issues from live UI verification

- **Verified:** 2026-10-15 against `http://localhost:3000` with the Java API available on port 8081.
- **Safety:** The room list, availability search, room details, booking steps 1–2, empty required-field validation, gallery filtering/lightbox, mobile drawer, and USD display were exercised. No booking form was submitted, so no real reservation or payment was created. Booking step 3 was not verified.

### Resolved: Login screen remains visible after successful authentication

- **Observed:** The admin login succeeded and redirected to `portal.html?view=dashboard`; the authenticated portal showed the administrator role and its navigation. However, the login form remained visible at the top of the document, with the admin application rendered below it. Browser inspection showed `#auth-view` still has a non-zero layout box after its `hidden` attribute is set.
- **Impact:** Users could see the login form and authenticated portal together, making it unclear whether login succeeded and leaving the page in an incorrect visual state.
- **Cause and change:** `.auth-layout { display: grid; }` in `portal.css` overrode the browser's default `[hidden] { display: none; }` styling. Added an explicit `.auth-layout[hidden]` rule in `portal-customer.css`.
- **Verification:** Customer sign-in, reload with the session, and logout were exercised in the browser; the login panel is hidden while authenticated and displayed again after logout. Admin path not re-tested after the fix.

### High: Admin dashboard statistics endpoint returns 404

- **Observed:** After the successful login, opening the default dashboard requested `GET http://localhost:8081/api/v1/reports/today-stats`, which returned HTTP 404. The dashboard logged “Endpoint không tồn tại” and continued showing zero revenue, zero occupied rooms, and zero pending bookings.
- **Impact:** The dashboard presents zero values as if they were real statistics even though the request failed. Monthly revenue could not be verified because the dashboard stops at the first failed request.
- **Follow-up:** Implement or correct the reports endpoint and make unavailable/loading states explicit instead of displaying misleading zero metrics.
- **GitHub:** #41

### High: Live room identity and pricing do not match what the UI displays

- **Observed:** The availability endpoint returned HTTP 200 and seven rooms. The first live room detail displayed `Deluxe - #101`, name `Standard`, and `500,000 ₫/night`; another visible card showed `VILLA CLASS`, `#301`, name `Standard`, and the same `500,000 ₫` price. These names/categories/prices do not agree with the room catalog or the backend room-type/pricing data.
- **Evidence:** The availability payload contains `id`, `room_type_id`, `room_type_name`, `version`, and `price`, but does not include a room number. The UI fills missing presentation fields from a catalog entry selected by response-array position, while the backend calls the room type `Standard`/`Deluxe` and uses different prices. This can misrepresent the room selected for booking.
- **Follow-up:** Define a stable room-type/room-number contract and map API results by that contract; do not infer room identity from response order. Verify the displayed room ID, number, type, and total against the booking request before enabling real booking.
- **GitHub:** #38

### Customer portal: Must be visually distinct from administration

- **Requirement:** The customer account must use a different interface from the admin, not merely different menu labels. Admin/staff need a management console; customers need a guest-facing experience.
- **Verified:** Signed in with the test customer account on 2026-10-15. The customer sees a different navigation and content set from the admin: customer overview, room discovery, loyalty points, offers, concierge support, and profile/security. Before the visual update, both roles still shared the same dark sidebar-and-content layout. The configured staff navigation is also role-specific (room map, bookings, check-in/out, invoices, loyalty, and messages); a separate staff sign-in was not performed in this test.
- **Change:** Added a dedicated customer portal presentation in `portal-customer.css` with horizontal guest navigation and guest-oriented content styling. A shared theme button now cycles through automatic, light, and dark modes across the hotel website and every portal role; the setting follows the browser preference by default and is stored once for all roles. Admin/staff retain the management-console layout. The guest account controls remain visible on mobile.
- **High — Customer dashboard and loyalty history fail to load:** The dashboard remained in loading states for booking/loyalty data, and the loyalty-history request returned HTTP 405. The loyalty page also remained loading after the same method error. The dashboard should isolate failures per data source and show an explicit error/empty state rather than leaving the entire view incomplete.
- **GitHub:** #42
- **Medium — Offers remain in a loading state:** The offers view still displayed “Loading active promotions...” after waiting 2.5 seconds. No definitive API response was captured for this view, so the exact backend cause remains unverified. Replace the loading message with a visible error or empty state when loading fails.
- **GitHub:** #43
- **High — Room discovery repeats the room-type mapping issue:** Searching for 2026-10-15 through 2026-10-16 showed 13 rooms, all labeled “Standard,” with prices spanning 500,000 ₫, 1,000,000 ₫, and 1,500,000 ₫ per night. This is inconsistent with the displayed type and pricing tiers and may mislead a customer during room selection.
- **GitHub:** #38
- **Resolved shared-shell defect:** The login panel is now hidden during the authenticated customer session and shown again after logout.
- **Safety:** Room search and navigation were exercised, but no booking was submitted and no concierge message was sent. The customer was logged out after testing.

### Medium: Language selection translates only part of the page

- **Observed:** After selecting English, the desktop navigation and some booking-search labels changed to English, but the hero, room cards/actions, testimonials, footer, and booking flow remained Vietnamese. Opening the mobile drawer also showed Vietnamese navigation.
- **Follow-up:** Translate all user-facing copy and dynamically rendered content, including mobile navigation, or label the selector as a partial preview until full translations exist.
- **GitHub:** #39

### Medium: Booking search omits required party/room controls

- **Observed:** The search bar has one combined guest selector and no separate children count or room-count selector.
- **Specification gap:** The specification calls for adults and children to be selectable separately and for 1–5 rooms to be selectable.
- **Follow-up:** Add and carry these values through availability search and booking, or explicitly narrow the approved specification.
- **GitHub:** #40

### Medium: Lightbox close control is obstructed by the sticky header

- **Observed:** At a scrolled desktop position, a real click on the lightbox close button repeatedly timed out because the fixed header intercepted pointer events. Escape keyboard handling did close the lightbox.
- **Follow-up:** Adjust overlay/header stacking or place the close control outside the header hit area; verify pointer close, Escape, previous/next, and zoom while scrolled.
- **GitHub:** #37

### Low: Social icon names produce Lucide console warnings

- **Observed:** The browser console repeatedly reported that `facebook`, `instagram`, `linkedin`, and `youtube` were not found in the loaded Lucide icon set.
- **Follow-up:** Use supported icons or inline SVGs so the footer social controls render without warnings.
- **GitHub:** #36

### Test suite blocker: Two existing test modules fail during import

- **Command:** `node --test web-ui/tests/server.test.mjs web-ui/tests/transport.test.mjs web-ui/tests/theme-and-roles.test.mjs`
- **Result:** 5 tests passed and 2 test files failed before their tests ran (exit code 1).
- **Errors:** `server.test.mjs` imports `createWebServer` from `server.js`, but that export is absent. `transport.test.mjs` imports `fetchAuthenticatedFile` from `public/api.js`, but that export is absent.
- **Follow-up:** Reconcile these tests with the current server/API implementation, or restore the intended exported interfaces, then rerun the full command.
