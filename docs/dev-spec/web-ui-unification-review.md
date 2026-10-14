# Existing web experience: inventory and unification proposal

Date: 2026-10-15. Status: source review complete; vanilla stack, navy/Inter system, and retention of both light and legacy dark modes are agreed. Frontend integration is in progress. Narrow backend versioned-route and customer-ownership changes remain unapproved. No backend application, API, permission, pricing, payment, or database code has been changed by this work. Focused transport/theme/server tests pass; full role end-to-end browser acceptance remains outstanding.

## Scope and documentation alignment

The current request authorizes completing and unifying the existing Admin, Staff, and Customer web experience. Preserve separate workflows and server-enforced permissions. Reuse supported APIs; any required backend compatibility or authorization changes need prior alignment. No new business features, framework migration, replacement of Swing, AI concierge expansion, new analytics, or new payment policies are included by default.

Sources: ui-tokens.md, ui-web-guest.md, ui-web-admin.md, ui-desktop-staff.md, architecture.md, implementation-plan.md, tech-stack.md, tasks-phase2.md, tasks-phase3.md, ai-context/constraints.md, and the current backend controllers. Documentation contains conflicting or future-phase requirements:

| Conflict | Proposed resolution for this work |
| --- | --- |
| Shared tokens prescribe navy/Inter/light; portal uses amber/Outfit/Jakarta/dark and booking uses green/Georgia. | Adopt documented navy/Inter across existing pages, with different page composition for each role. Light and legacy dark modes both remain supported, with persisted preference. |
| ui-tokens lists Ant Design for Admin; ui-web-admin says unified MUI 5. | Do not introduce either library in the existing vanilla application. Record MUI as the future React direction if a separate migration is agreed. |
| Guest/Admin conventions target React/TypeScript; checked-in pages are vanilla HTML/CSS/ES modules. | Reuse the existing stack and translate visual/component rules into shared CSS and JavaScript. No automatic framework migration. |
| Architecture schedules Staff on Swing and Admin Web in Phase 4. | Maintain the already-existing web modules under the explicit current request. Do not add future-phase screens or replace desktop workflows. |
| implementation-plan/tech-stack disagree internally on Admin Web Phase 3 vs Phase 4, and their Phase 2 platform scope predates the checked-in booking portal. | Treat current executable code and the explicit existing-web request as the scope; preserve future phases as separate work and record contradictions rather than claiming all docs agree. |
| Room token table has six statuses; Phase 2 also supports out_of_service. | Preserve seven distinct backend status codes; add an explicit OOS presentation without merging OOO/OOS or redefining transitions. |
| Guest guide forbids early dark mode; portal currently defaults to dark. | Light is the shared default. Retain the legacy dark mode with persisted preferences, as explicitly requested. |
| Future guest guide requests four languages, AI chat, drafts and additional navigation. | Complete existing VI flows and accessible structure first. Do not promise unsupported locales or new concierge capabilities in this scope. |

## Inventory: keep, fix, or replace presentation

There are 23 screen/workflow surfaces: public booking, login, registration, shared profile, seven Admin views, seven Staff views, and five Customer views. OTP, QR/payment and receipt are substates of the public booking workflow, not extra business modules. Paths below are relative to web-ui/public.

| ID | Role / screen | Source | Disposition and completion work |
| --- | --- | --- | --- |
| WEB-P01 | Public discovery → pricing/voucher → OTP → booking → QR → receipt | index.html; js/booking-core.js and companion modules | Keep the authoritative Phase 2 flow; unify tokens, controls and feedback; retest timers, conflicts, retry and payment states. |
| WEB-S01 | Login | portal.html; app.js | Fix transport, loading and expired-session handling; retain account authentication separately from guest OTP. |
| WEB-S02 | Registration | portal.html; app.js | Keep fields; fix button reset on successful repeat visits and safe error rendering; use shared inline validation. |
| WEB-S03 | Profile/password | js/view-shared.js | Keep workflow; repair contract compatibility; safe rendering, pending/success/error states and keyboard operation. |
| WEB-A01 | Admin dashboard | js/admin/dashboard.js | Keep existing reports/chart; loading, unavailable, empty and retry states replace false zero defaults. Chart uses shared theme tokens. Verify at target widths; no new KPIs. |
| WEB-A02 | Admin rooms | js/admin/rooms.js | Keep authorized CRUD; canonical statuses, safe delete confirmation and reusable table/form. |
| WEB-A03 | Admin users | js/admin/users.js | Keep authorized staff/account administration; preserve actual role IDs and restricted actions; safe fields and validation. |
| WEB-A04 | Admin customers | js/admin/customers.js | Keep search/detail/create/delete where supported; shared customer table with role-specific action configuration. |
| WEB-A05 | Admin promotions | js/admin/promotions.js | Keep CRUD; audit API-supported voucher policy fields before claiming a created voucher is usable. Do not invent caps, dates or discount rules. |
| WEB-A06 | Admin reviews | js/admin/reviews.js | Keep list/detail/moderation; safe content, clear empty/error states and confirmation. |
| WEB-A07 | Admin invoices | js/admin/invoices.js | Keep authorized list/detail and distinct Admin actions; authenticated PDF retrieval is implemented. |
| WEB-F01 | Staff room map | js/staff/room-map.js | Keep the map as a seven-state status overview with safe text rendering and request-generation guards. Use the separate authorized booking/checkout modules for actions; the obsolete room-map quick-book/payment/status-reset actions have been removed. |
| WEB-F02 | Staff bookings | js/staff/bookings.js | Keep search/table; cancellation/check-in controls only where permitted; conflict refresh and truthful booking statuses. |
| WEB-F03 | Staff customers | js/staff/customers.js | Keep search/detail/create; share rendering with Admin without granting Admin deletion capabilities. |
| WEB-F04 | Staff checkout | js/staff/checkout.js | Uses booking folio balances, configured server VietQR, payment-status polling, UUIDv4-idempotent settlement, and a separate explicit checkout request. Frontend integration is implemented; confirm against isolated authenticated browser fixtures before acceptance. |
| WEB-F05 | Staff invoices | js/staff/invoices.js | Keep Staff authorization and finalized PDF requirements; authenticated PDF retrieval is implemented. Presentation consolidation can follow after functional acceptance. |
| WEB-F06 | Staff loyalty policy | js/staff/loyalty.js | Removed unsupported hardcoded thresholds and discount promises. Page states no authoritative staff policy API is available; customer-level values remain on PMS profile. A policy endpoint/approved policy contract is still needed for a full policy view. |
| WEB-F07 | Staff support chat | js/staff/chat.js | Keep existing human support workflow; cancel polling on navigation/logout, visible errors, safe content and ownership checks. No new AI feature. |
| WEB-C01 | Customer dashboard/bookings | js/customer/dashboard.js | Personal dashboard/booking retrieval is now blocked in the UI until backend ownership is verified from JWT identity; no customerId=0 fallback or client-side authorization claim. |
| WEB-C02 | Customer discovery/booking | js/customer/discovery.js | Registered Customer booking is visibly blocked pending an authoritative quote and identity/ownership contract. Public OTP Guest remains separate; no client price math or implicit identity conversion. |
| WEB-C03 | Customer loyalty | js/customer/loyalty.js | Personal history/redemption is visibly blocked pending verified ownership and versioned-route alignment. Server-owned packages/policy remain unchanged. |
| WEB-C04 | Customer promotions | js/customer/promotions.js | Keep active-offer display; apply through server voucher preview/redemption, not local discount math. |
| WEB-C05 | Customer support chat | js/customer/chat.js | Keep human support; remove assumed support-user ID if no approved assignment is available; visible error/retry and lifecycle cleanup. |

Foundation assets: portal.html/app.js (shell/router/modals), api.js (legacy transport/session), style.css (legacy components), css/booking.css and css/payment.css (public components), js/booking-api.js (versioned transport/idempotency), server.js (optional development server), README.md (outdated launch/deployment information).

All checked relative JavaScript imports resolve to existing files. Existence is not proof of a working workflow. Legacy style.css has fixed multi-column grids and no media queries. Admin and Staff invoice modules contain substantial duplicated presentation.

## Verified source findings and priorities

| ID | Priority | Evidence / issue | Proposed treatment |
| --- | --- | --- | --- |
| WEB-B01 | P0 | api.js retrieves a session but never attaches its token; default URL is HTTP /api; success parsing accepts only lowercase success. Main mounts versioned routes and some compatibility aliases; ApiResponseUtil also emits SUCCESS. | Frontend transport repair: same-origin /api/v1, bearer token, response normalization after HTTP status, safe 401/403/409/503 handling. Preserve explicit development overrides without showing connection internals to ordinary guests. |
| WEB-B02 | P0 | PaymentController, ReportController, LoyaltyController and parts of UserController match old /api paths even when Main mounts /api/v1 contexts. Switching the UI base alone cannot repair these handlers. | Backend compatibility blocker: agree narrowly scoped route matching first; no financial or authorization downgrade and no new endpoints by default. |
| WEB-B03 | P0 | BookingController permits CUSTOMER then loads the customer ID supplied in /bookings/customer/{id} without an ownership check in that branch. LoyaltyController likewise uses supplied customer IDs and X-User-Role for finer role decisions. | Backend authorization blocker: align identity/ownership checks using verified JWT identity. Never fix by sending a trusted-looking role header or relying on hidden UI buttons. Reproduce with isolated users before implementation; do not claim an exploit was run. |
| WEB-B04 | P0 | Staff checkout now reads server folios, creates configured VietQR, polls payment status, and submits idempotent folio payments; explicit checkout is separate. The obsolete room-map quick-book/payment/status-reset actions were removed. Authenticated financial browser verification remains pending. | Use isolated fixtures to verify exact balances, duplicate retries, webhook settlement and checkout status. Approved bank configuration stays required; no invented production beneficiary or changed payment policy. |
| WEB-B05 | P1 | Resolved in the shell: Admin, Front Desk, Customer and departmental roles receive explicit presentations; Housekeeping, Maintenance and F&B have a restricted informational shell and no Front Desk actions. Generic STAFF and unknown roles fail closed because no department is established. | Role mapping is presentation only. Server authorization remains authoritative; authenticated cross-role/negative-permission browser acceptance is still outstanding. |
| WEB-B06 | P1 | Styles disagree with documented palette/fonts/sizes/radii/motion; legacy grids lack responsive breakpoints and controls can be under 44px. | Shared tokens and primitives; responsive role shells; measured layout, focus and contrast checks. |
| WEB-B07 | P1 | Unescaped API/session strings are interpolated into innerHTML in several forms/tables/errors; native alert/confirm coexist with custom dialogs. | Safe DOM/text or context-aware escaping; shared accessible dialogs and inline feedback. |
| WEB-B08 | P1 | Registration now resets the submit button on success and failure; the undefined health call was removed. Admin dashboard now distinguishes loading, unavailable and empty reports. Chat error presentation remains outstanding. | Continue shared feedback and module lifecycle work; dashboard failures no longer display as valid zero revenue. |
| WEB-B09 | P1 | Room map and Admin dashboard guard stale requests; Staff checkout stops QR polling on modal close, navigation, session expiry and timeout. Invoice downloads now use authenticated fetch-to-blob with URL cleanup. Other modules still need lifecycle review. | Add shared shell-level view disposal and audit remaining async modules/pollers. |
| WEB-B10 | P1 | Staff loyalty advertises static policy inconsistent with LoyaltyService; Customer discovery calculates room.price × nights locally. | Reuse authoritative PMS values; no client-derived authoritative prices or hardcoded policy promises. |
| WEB-B12 | P1 | portal.html loads unpinned lucide@latest from a CDN and calls lucide.createIcons without consistent guards; styles also depend on remote fonts. | Local/pinned assets where appropriate and graceful font/icon fallbacks; a CDN failure must not prevent navigation, login or submission. |
| WEB-B11 | P2 | README documents index as the management shell, port 3000/Adminer Docker services that are absent from current Compose, and obsolete API paths. | Update routes, serving instructions and verification evidence after implementation. Audit development server containment and /api proxy behavior before retaining it. |

## Proposed unified style guide

- Shared primary navy #1B2A4A; background #FAFAFA; white surfaces; primary text #212121; muted #757575; borders #E0E0E0. Success #2E7D32, warning #F57C00, error #C62828 and info #1565C0. Validate contrast in each component; status always has text/icon, never color alone.
- Inter with system sans-serif fallback across headings/body. Sizes 12/14/16/20/24/32px; weights 400/500/600/700; body line height 1.5, heading 1.2. No new serif/display-font exception without agreement.
- Spacing 4/8/12/16/24/32/48/64px; radii 4/8/12px. Use documented shadows/z-index layers. Native pixel borders and documented shell dimensions are structural measurements, not arbitrary spacing tokens.
- Room states: vacant_clean green #4CAF50; vacant_dirty orange #FF9800; occupied_clean blue #2196F3; occupied_dirty #FF5722; out_of_order #F44336; reserved #9C27B0. Proposed out_of_service uses warning styling with its own OOS label/icon. Do not change backend status values.
- Shared controls: buttons, labeled fields, inline errors, pending/retry/empty/success panels, tables/pagination, search/filter toolbar, status badge, card, modal/drawer, toast and PDF action. Accessible names, visible focus, keyboard/Escape/focus return, 44px touch targets, reduced-motion support, no hover-only actions. Guest feedback uses understandable messages rather than raw internal errors.
- Customer composition: mobile-first discovery/cards and guided booking; existing account views remain personal. Admin composition: dense management tables and current analytics. Front Desk composition: operational room map and reservation/folio tasks. Shared visuals do not imply shared action sets.
- Desktop shell: 240px sidebar / 64px collapsed; 56px header; 24px content padding. Below 1200px collapse sidebar and stack complex groups; below 768px use an accessible navigation drawer and one-column forms. Tables use bounded horizontal scrolling or compact cards without hiding critical amounts/actions.
- Validate 375, 768, 800, 1200 and 1920px, plus a 320px/zoom stress check. Avoid page-level horizontal overflow. Guest motion ≤300ms, management motion ≤200ms; respect reduced motion. Light is the proposed common default; dark mode decision stays explicit.

## Proposed implementation order and acceptance gates

| Batch | Work | Gate |
| --- | --- | --- |
| 0 | Agree inventory dispositions, style guide and existing-stack scope; separately decide backend alignment items WEB-B02/B03. | Vanilla stack and shared navy/Inter system with light/dark retention are agreed. WEB-B02/B03 remain pending; no unapproved backend changes. |
| 1 | Repair API/session/envelope handling and role navigation; safely render dynamic data; align backend route/ownership blockers only if agreed. | Correct tokens/statuses; expired/missing auth handled; cross-role and cross-customer requests denied server-side. |
| 2 | Shared tokens/components and responsive shells; consolidate table/form/modal feedback and lifecycle disposal. | Representative Customer, Staff and Admin screens reviewed at all target widths, keyboard and reduced motion. |
| 3 | Customer public/account flows, authoritative pricing/vouchers, OTP/QR/polling, own records and accessible states. | End-to-end successful journey plus invalid OTP/voucher, no availability, 409 and expired session; no foreign PII or premature payment confirmation. |
| 4 | Existing Staff room map/bookings/customers/checkout/invoices/loyalty/chat. | Check-in/allocation and financial flow use existing server APIs; duplicate submit/retry cannot duplicate posting; departmental restrictions preserved. |
| 5 | Existing Admin dashboard/rooms/users/customers/promotions/reviews/invoices. | Supported mutations verified, destructive actions confirmed, errors never shown as zero revenue, no unimplemented control presented as working. |
| 6 | Cross-role regression, responsive/a11y/performance verification, launch docs and evidence. | All key web journeys and negative role/ownership cases pass; backend regression stays green; deferred cases explicitly recorded. |

## End-to-end test plan after integration

Use disposable MariaDB 11 *_test schemas and labeled synthetic identities/bank/VAT/PIN/OTP fixtures. Never use production credentials, customer data or real bank delivery. The existing 125-pass Java suite is prior backend evidence, not proof these legacy web journeys work.

| Actor | Key journey / assertions |
| --- | --- |
| Public/OTP guest | Availability → server quote → voucher → OTP → one idempotent booking → configured local QR → simulated signed callback → server-confirmed receipt. Test resend/invalid OTP, quota failure, stale room/version, retry and duplicate callback. |
| Registered Customer A/B | Login → own dashboard/bookings/loyalty → supported booking/support/profile → logout. Swap customer IDs and direct URLs: no access to B's data or Staff/Admin actions. Preserve separate OTP/account identity semantics. |
| Front Desk | Login → room map → existing booking/check-in → folio settlement → explicit checkout → finalized authorized PDF. Verify exact server amounts, conflict recovery and freeze 503 behavior. |
| Housekeeping / Maintenance / F&B | Login/direct navigation shows only permitted existing capabilities. Billing, Admin CRUD and unrelated departmental actions denied by backend; no invented departmental screens. |
| Admin | Reports → room/customer/user/promotion/review/invoice modules → supported create/update/delete → refresh persistence → PDF → logout. Unsupported policy fields remain a visible blocker until aligned. |
| All roles | Loading, empty, slow network, abort/navigation, 401/403/409/503/500, duplicate clicks, safe text rendering, keyboard/dialog focus, viewport/zoom, console errors, timer cleanup and session isolation. |

Run browser E2E against the integrated Java server using available local browser tooling, followed by the clean Java regression suite. Capture screenshots, request/status evidence and synthetic-record assertions. Native Swing/Docker acceptance previously deferred for lack of QA host access remains a separate handoff and must not be marked passed by these web tests.

## Decisions needed

1. Decision recorded: retain the existing vanilla stack, unify on navy/Inter/light, and preserve legacy dark mode with persisted preference.
2. For WEB-B02/B03, agreement is still needed for narrowly scoped versioned-route and verified-identity ownership fixes before affected workflows can be completed. Until then, affected workflows remain blocked; no backend route or ownership change is authorized. This follows the user's explicit instruction to align backend changes beforehand, not an inferred approval requirement from a skill.
