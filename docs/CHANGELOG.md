# CHANGELOG — Hotel PMS Documentation

## [2026-10-26] — Shared account avatars and a dedicated room-type tab

- Added one avatar picker to the shared account settings used by Admin, Staff and Customer. Each upload is attached to the authenticated user's account, uses the configured media provider, and supports preview, save and remove.
- Added the idempotent `users.avatar_url` migration and self-scoped avatar API routes.
- Added an Admin “Hạng phòng” tab with create, edit and delete for room types. Deletion is blocked while physical rooms use the type.
- Removed shared stay-rate controls from physical-room dialogs and reused the existing rate editor in the new tab.
- Verification: focused portal, avatar, media-upload and room-type UI tests passed 29/29; room-type authorization and account profile Java tests passed; Java test compilation succeeded. The full web suite ran 147 cases (140 passed, 7 blocked by loopback HTTP test-server failures). Browser-driven review was stopped by the user during this turn.

## [2026-10-26] — Upload and preview room images

- Replaced the room URL field with an optional JPEG/PNG picker (up to 5 MB) in both add and edit forms; selected images preview locally before submission.
- Consolidated catalog and room upload requests through a shared helper. The current backend stores file bytes in its configured local media directory and the URL/metadata in MariaDB; an external object-storage provider is not configured yet.
- Verified both room dialogs on port 3000, previewed `standard.jpg`, then cancelled without changing QA-801 or uploading the image. Image validation tests passed 2/2; modified JavaScript syntax and `git diff --check` passed.
- Revisited the admin dashboard after backend restart and verified the live report/chart, resolving QA-2026-020.
- Full web regression run: 142 tests, 135 passed, 7 failed (five login/auth integration failures due to unavailable test API and two loopback `EACCES` failures in server tests); focused image-upload validation passed 2/2.

### QA follow-up — Room image persistence

- Fixed room updates rejecting the managed media path returned by the upload API; only strict UUID media paths and absolute HTTP/HTTPS URLs are accepted. Retry after a successful upload reuses its URL.
- Manually uploaded `standard.jpg` and saved it to QA-801 through the admin room dialog. Reopening the dialog fetched the persisted image (1200 px natural width). The room price and housekeeping state were unchanged.
- Added backend URL-validation regression coverage (3 passed). The failed first room update left one unattached media asset; its cleanup is pending a DB reference check because MariaDB is currently stopped.
- Added a Cloudflare R2 S3-compatible media provider with signed server-side uploads and reads. Local storage remains the default; R2 is selected through `.env` and returns a direct URL only when an HTTPS public base is configured. The secret key stays on the backend.
- After the operator filled the R2 settings, restarted with `start-backend.bat` and manually saved `standard.jpg` to QA-801 on port 3000. Reopening the room popup loaded the 1200 × 900 R2 image. Room price and housekeeping status were unchanged. R2 credentials were not printed or added to versioned files.

## [2026-10-26] — Consolidate room pricing and improve admin visibility

- Added a business-date admin overview with occupancy, collections, refund rate, pending refunds, cancellations, arrivals/departures, cleaning work and six-month net revenue.
- Moved hourly and overnight price controls into the add/update room dialog. The dialog states that these rates apply to every room in the selected room type; room-level nightly price remains separate.
- Fixed low-contrast text and controls in light-theme account management, and removed the stray visible “Đóng” text from role/create/delete dialogs.
- Verification: backend compile succeeded and all 140 existing web UI tests passed. Port-3000 admin account and dialog UI checks passed; dashboard API integration failed against a stale running backend build and is tracked as QA-2026-020.

## [2026-10-24] — Link payment history to guest and booking

- Added booking code, customer name and room number to payment audit rows using the existing Folio → booking relationships.
- Kept customer phone and identity numbers out of the transaction table and updated the API contract.
- Rebuilt through `start-backend.bat` and verified guest, booking code and room on the existing folio-5 transaction in the live port-3000 staff page; no extra payment was submitted.

## [2026-10-24] — Improve staff payment history readability

- Kept transaction date/time, amounts and result badges together in the staff payment history; wide tables now scroll horizontally instead of squeezing every field.
- Aligned filter controls in one row on wide screens and kept a responsive layout for smaller screens.
- Verified the refreshed port-3000 staff view against existing synthetic transactions; no extra payment was submitted.

## [2026-10-24] — Clarify front-desk room cards

- Added staff-scoped styles for the shared room-card image placeholder/status, room details, amenity chips, price, actions and unavailable reason. The “Cần dọn phòng” selection remains disabled.
- Updated QA-2026-014 with the live port-3000 visual check; no booking or payment was submitted.

## [2026-10-24] — Verify staff counter collection end to end

- Recorded the authorized synthetic cash collection for Web booking #5 and verified the booking's paid state in the live staff list.
- Confirmed transaction history contains one applied cash entry for 850,500 VND with zero excess; no real funds or external gateway were used.
- Updated QA-2026-009 and test progress; POS and partial/excess collection paths remain open.

## [2026-10-24] — Reuse customer room search in front-desk bookings

- Reused the same stay/guest search controls, availability query builder, and room-card renderer in customer discovery and staff front-desk booking.
- Replaced manual customer lookup with debounced suggestions while staff enter a name, phone, identity number, or email. Only matching customer profiles are suggested; an unmatched guest continues as a new customer.
- Verification: seven focused browser UI tests passed. On the live staff portal, a synthetic unmatched name produced no suggestion; today's room search returned no eligible clean rooms, so no live room selection, booking, or payment was submitted.

## [2026-10-24] — Restore customer profile details in staff and admin lists

- Fixed the customer list API/UI field-name mismatch, included email and address in search results, and exposed whether an identity number is present in the encrypted vault without decrypting it for list views.
- Customer creation now preserves submitted addresses. The customer list endpoint is restricted to Admin/FrontDesk, matching its PII scope.
- Added QA-2026-013 and documented the list response fields and verification status.

## [2026-10-23] — Overdue checkout charges and staff notification center

- Added a 30-minute checkout grace period, per-started-hour overstay charges and one hourly penalty after six hours, with idempotent folio reconciliation across background processing and staff checkout.
- Kept unpaid Web counter bookings in-house after checkout, marked them overdue after the grace period, and added a durable, deduplicated alert to the staff notification center with contact and collection details.
- Documented staff notification API access and the overdue checkout policy. Added lifecycle regression coverage for grace boundaries, fee posting and notification deduplication.
- Validation: pending focused backend and frontend checks; no live payment or email delivery was triggered.

## [2026-10-23] — @codex — Correct MISA POS invoice payload

- Set MISA's required `IsInvoiceCalculatingMachine` marker on POS invoice data, placed the reference ID at the documented nested level, and preserved the caller's invoice object when adding correction data.
- Added synthetic HTTP contract tests for POS/HSM routes, token caching and separation, status calls, safe view links, PDF validation, and cancellation. Updated the e-invoice API contract and QA records. The operations guide now cites Circular 91/2026/TT-BTC alongside Decree 254/2026/NĐ-CP, and records seller-specific legal configuration as an accounting confirmation.
- Validation: **6 focused adapter tests passed**; the POS marker regression also failed as expected when the old payload was temporarily restored. No MISA sandbox request was made because test credentials were unavailable.

## [2026-10-22] — @codex — Shared payment audit and durable lifecycle notifications

- Added a common provider/method/direction transaction audit for cash, POS, legacy bank receipts and future gateway adapters, with payOS as the first new QR integration. Added signature and beneficiary validation, partial/excess handling, duplicate/conflict audits, and late-payment exceptions without reopening cancelled stays.
- Added transactional in-app/email outbox events, check-in/out reminders, bounded retries and conservative UNKNOWN delivery handling. Added customer-owned and staff audit APIs and a portal history view; removed fabricated notification fallback and fixed hard-coded checkout QR/manual transfer confirmation.
- Added [Vietnamese setup and operations guide](vi/payments-notifications.md), configuration placeholders and disposable MariaDB integration checks. Live gateway activation awaits merchant/bank credentials; email delivery remains opt-in.

## [2026-10-22] — @codex — Backend stop batch launcher

- Added `stop-backend.bat` to invoke the existing PID- and workspace-verified PowerShell stop script. It preserves the script exit code and keeps the console open on failure. Updated startup guides for the start/stop pair; the desktop build and standalone web batch wrappers remain removed.

## [2026-10-22] — @codex — Single backend batch launcher

- Added only `start-backend.bat` at the user's request, with the complete command invoking `scripts/start-backend.ps1 -Background`. Startup reads the existing `.env`; on failure the window shows separate database/key checks and log paths. Removed the newly proposed VS Code tasks in favor of this batch launcher; the other batch wrappers remain removed.
- Updated startup guides for the single double-click entry point.

## [2026-10-22] — @codex — Remove batch wrappers before rebuilding startup

- At the user's request, removed all four project-owned `.bat` files: backend start/stop, desktop build and standalone web start. Existing PowerShell and Node entry points remain available; updated their current setup instructions and desktop task references.
- This cleanup does not replace the running backend or modify the database, `.env`, identity keys or application source.

## [2026-10-22] — @codex — Persistent real database startup and developer database handoff

- Real Windows launchers now load the ignored `.env`, retain stable DB/AES/HMAC/JWT configuration, prefer `JAVA_HOME`, run read-only preflight and start backend `Main` directly. Removed implicit localhost database switching by default and secret generation at restart. Scripts restore the caller's environment after temporary configuration checks.
- Added separate connection/schema/key/account diagnostics, explicit one-time fresh database provisioning and an identity-key companion exporter for SQL handoff. Startup authenticates stored AES envelopes and compares HMAC indexes before migration, then verifies readiness after migration. Removed implicit HMAC fallback/padding in vault configuration and the conflicting production Compose HMAC sources. Login database errors no longer become credential mismatches.
- Added the [Vietnamese real database and per-developer handoff runbook](vi/real-database-startup.md), updated startup instructions/template, and recorded [verification and limits](verification/real-database-startup-20261022.md). The operator authorized a new local database after confirming the original VM database's keys were lost; this does not establish recovery of its historical ciphertext.
- Validation: 13 focused Java tests; 8 environment-loader assertions; live MariaDB startup/login/profile/vault checks across restart, negative AES/HMAC/password/network checks, and separate fresh-provision/configuration-switch checks. Secret values are retained only in ignored local configuration.

## [2026-10-22] — @codex — CORS response pipeline — Restore Google OTP login from localhost:3000

- Fixed `ApiResponseUtil` overwriting the origin selected by `CorsFilter` with `*`. Credentialed Google requests from port 3000 could send an OTP email successfully while the browser rejected the response, leaving the OTP form hidden and preventing device verification from completing. JSON responses now preserve the selected origin; dynamic CORS responses include `Vary: Origin`.
- Updated HTTP regression tests to exercise the JSON response writer and the Google controller through CORS/JWT filters with `Origin: http://localhost:3000`. Covered preflight, OTP challenge/verification, recognized-device login without another OTP, rejected Google identity and unrecognized origins without credential permission.
- Validation: **7 focused backend tests and 11 Google sign-in web tests passed**, using Google/SMTP test doubles. A browser without a completed device verification still requires one successful OTP confirmation; the fix does not bypass that check or the password-change OTP requirement.
- Removed the duplicate signed-in device inventory from the customer Profile view; it remains in Security and the combined admin/staff account view. Profile no longer requests the device list. All 11 account-settings web tests passed, and live checks confirmed the Profile/Security separation.

## [2026-10-22] — @codex — Backend/web-ui — Booking navigation, trusted devices, walk-in reception and customer support

- Unified customer booking entry actions through the shared booking-link helper. Separated room discovery/booking from booking history in the customer navigation, so returning to Booking opens the booking interface. Corrected room-image loading and provided local room-image fallbacks.
- Connected the voucher wallet to active database vouchers eligible for rooms (`ROOM`/`ALL`). The selected code travels into room discovery, server pricing and booking creation; the booking action now carries a voucher instead of only redirecting to an unrelated booking screen.
- Updated Google/email OTP behavior: registration and first Google sign-in on an unrecognized browser still require OTP; a previously verified account on a recognized browser can complete sign-in without another email OTP. Signed, HttpOnly device proof lasts up to one year and is bound to account identity, verified email and password state; every Google sign-in still validates Google identity. Already verified profile email no longer prompts for repeated verification. Password-change OTP remains required.
- Added `user_login_devices` through [migration v4.1](../database/migrations/v4_1_login_devices.sql), bootstrap registration and additive startup migration. Phase 4 `GET /api/v1/account/devices` lists only the authenticated user's browsers, platform, IP, login/activity times and current device. Shared account/security screens expose this inventory for admin, staff and customer; the device identifier is hashed and is not an authentication credential.
- Added the staff **Xử lý tại quầy** tab for walk-in reception: guest identity/contact details, available clean rooms, server quote, booking creation, cash/POS collection acknowledgement and check-in. Reused existing booking/folio/payment APIs, version checks and idempotency keys; enabled legacy `STAFF` reception access and server validation of the submitted quote. Housekeeping/maintenance/customer roles cannot create walk-in bookings.
- Reused the administrator membership search, tier filters, sorting, pagination and point-history view for staff. Point adjustment controls remain admin-only, backed by the existing server authorization.
- Replaced the customer Support placeholder with a conversation connected to the staff inbox. Messages enter the shared support queue; history includes legacy direct messages and staff replies. Added five-second polling, refresh/retry feedback, retained drafts after send failure, duplicate-send protection, safe text rendering and request cancellation on navigation/session expiry. Legacy customer chat links use the same view. Chat API authorization checks JWT ownership/sender identity; database failures no longer report successful sends and history responses are not cached.
- Added the Vietnamese [booking and housekeeping refactoring proposal](vi/dev-spec/tai-cau-truc-nghiep-vu-dat-phong-va-buong-phong.md), covering schema, time-window overlap with cleaning buffers, late checkout, housekeeping tasks and concurrency control. This document describes future work; it does **not** establish that the proposed buffer/schema refactor has been deployed.
- Validation: focused authentication/device, voucher, reception and membership checks passed during their implementation. The reception/membership run passed **22 web tests and 24 backend tests**; the subsequent support run passed **17 web tests and one backend HTTP test** with multiple ownership/send-validation cases. These are separate runs, not a combined full-suite total. Live demo checks confirmed room quotes, staff member search/history and existing customer/staff message history. Booking creation, collection and message sending were exercised with test doubles, without submitting real payments or test messages to staff. Source/public assets were synchronized and the demo backend restarted with its existing data.

## [2026-10-22] — @codex — Windows backend and web launchers

- Updated the Windows backend launcher to open the integrated website only after backend startup succeeds. Updated the standalone web launcher to start its server separately, wait for an HTTP 200 response, and then open the browser. Documented both launch paths and their ports.

## [2026-10-22] — @codex — Backend/web-ui — Admin membership, account OTP and consistent portal themes

- Replaced the administrator membership placeholder with member statistics, search, tier filters, sorting, paginated records, point history and audited point adjustments. Server-side admin authorization, transactional updates and balance validation prevent unauthorized changes, overdrafts and partial history writes; deductions leave lifetime tier points unchanged.
- Unified membership/history pagination with the shared portal controls, removed duplicate headings and redundant modal close actions, corrected heading fonts/modal framing and displayed a minus prefix when deducting points. Standardized admin page headings and connected the admin booking view to the existing booking renderer.
- Reworked AI settings with locally built daisyUI components, responsive columns and clearer provider/model steps; removed the redundant brand eyebrow while preserving the existing configuration workflow.
- Extended the Phase 4 portal's shared JWT account flow with `GET/POST /api/v1/account/profile` and `POST /api/v1/account/email/request`, `/email/verify`, `/password/request`, `/password/confirm`. Profile ownership comes from the verified JWT, linked user/customer updates are atomic, and the nullable `users.verified_email` migration binds verification to the saved address. Legacy password routes also require OTP.
- Added six-digit email verification and password-change OTPs: five-minute validity after delivery, sixty-second resend cooldown, ten sends per hour per user/purpose and a ten-minute lock after five incorrect attempts. Password changes require a verified email, the current password and a purpose-bound OTP; successful confirmation revokes the current session. Codes are hashed in process memory and OTP requests are not automatically replayed against another backend.
- Shared daisyUI account controls across admin/staff/customer, with separate customer Profile and Security content: personal/stay information remains under Profile, while Security provides read-only email verification and password changes. Fixed obsolete surface-color overrides and aligned dashboard, cards, fields, navigation and actions with Member/Silver/Gold/VIP palettes in both themes. Stored lifetime points now take precedence over partial history, and late navigation results cannot replace the active view or tier.
- Validation: **119/119 web tests passed**; **12 focused account-security backend tests passed**, including an isolated MySQL transaction test. Membership authorization/adjustment database checks and existing Google OTP tests also passed. Browser fixtures covered 24 tier/theme/page combinations and six compact Gold layouts without horizontal overflow. No real email was sent or real account password changed during OTP verification. See [account-security verification and operational limits](verification/web-ui/account-security.md), [tier consistency results](verification/web-ui/customer-tier-consistency-checks.json) and [AI layout checks](verification/web-ui/admin-ai-settings-layout-checks.json). Source/public assets are synchronized; temporary browser fixtures were removed.

## [2026-10-22] — @codex — Match the Google sign-in control to the portal

- Added a rounded, theme-aware frame with the portal's surface/accent tokens and hover/focus treatment around the official Google control. Shortened the label to Sign in with Google and used the supported dark outline theme in dark mode, with the standard outline in light mode.
- Removed the white iframe canvas caused by inherited dark color-scheme. Theme changes re-render the button without reinitializing Google authentication or losing the OTP challenge; medium sizing continues to suppress personal account details.
- Validation: 23 focused web tests passed; live visual checks confirmed the light/dark appearance and restored the original dark preference. Synced source/public assets and refreshed cache versions.
- Developer handoff: [Google/SMTP setup and OTP troubleshooting](google-otp-setup.md), [authentication API contracts](dev-spec/api-contracts.md#google-customer-authentication--2026-10-21) and [Vietnamese change summary](vi/CHANGELOG.md). The current portal assets use version `3.6.35`. Visual checks cover button rendering/theme changes; complete Google sign-in and OTP confirmation still require user acceptance with a real account.

## [2026-10-22] — @codex — Keep Google account details off the login button

- Set the official GIS button to the documented non-personalized medium size, preserving the full-width Google sign-in entry without exposing a returning user's name, email or avatar on the portal. Refreshed portal cache versions and updated the existing widget assertion.

## [2026-10-22] — @codex — Recover expired Google OTP codes and resend feedback

- Separated the five-minute OTP validity window from the Google challenge session, which expires fifteen minutes after the most recent successful send (at most two resends), starting code validity after SMTP delivery. Expired codes can be resent within that session; missing/consumed sessions return 410 and restore the Google button. Resend now shows progress, success and failure feedback.
- Serialized verification/resend per challenge, preserved the previous code and resend budget when SMTP fails, retained the total five-attempt limit across resends and consumed successful OTPs only after account/session creation succeeds.
- Fixed new Google customer creation against the non-null phone column by storing an empty missing phone value. Confirmed the running demo schema rejects NULL phones and accepts the new insert inside a transaction that was rolled back.
- Validation: 104 web tests and 10 focused backend tests passed. Lifecycle tests cover slow SMTP, expired-code resend, database retry, failed email delivery, missing/expired sessions and attempt limits; they send no real emails.

## [2026-10-22] — @codex — Preserve Google OTP responses and Vietnamese email text

- Gave Google OTP creation, verification and resend requests a 45-second timeout and disabled automatic alternate-host replay for these requests. The previous 2.5-second deadline could discard the challenge response after SMTP had already delivered the email, leaving no OTP input visible. Added Vietnamese timeout feedback and refreshed portal asset versions.
- Encoded the UTF-8 email body with MIME Base64 so the SMTP ASCII transport preserves Vietnamese accents. Existing emails cannot be repaired retroactively.
- Validation: 102 web tests and 4 focused backend tests passed, including delayed OTP responses, input visibility/focus, no network replay and MIME decoding. Real Google selection and OTP completion remain a user acceptance step.

## [2026-10-22] — @codex — Verify local Google/SMTP setup and unblock Google Identity

- Verified the provided local `.env` contains the required Google/SMTP settings, is ignored by Git, and the running backend reports Google enabled with a healthy database. Confirmed Gmail TLS certificate validation and SMTP authentication succeed without sending an email or exposing credentials.
- Fixed the static-page CSP that blocked the Google SDK: narrowly allowed Google's documented GIS script/style and connect/frame paths while preserving strict API CSP and the existing security headers. Added an HTTP regression test; it and the two Google authentication route tests pass.
- Updated setup guides to reflect `.env` loading by the demo launcher, process-environment precedence and the required Google Identity CSP. Direct Java/non-demo launches still require exported environment variables.
- Restarted with the existing demo schema/keys and confirmed the live page loads the official Google sign-in button with Google enabled. Three focused backend HTTP tests passed. Gmail SMTP authentication was checked without sending mail; the real Google account/OTP flow remains for the user to complete.

## [2026-10-21] — @codex — Google sign-in/sign-up integration, remove Facebook

- Added optional local demo Client ID loading from ignored `.run/demo/google-client-id.txt`, with environment override and format validation. Imported only the Web client ID from the developer-provided Google JSON; no Client Secret was copied. SMTP configuration remains separate. Validated launcher PowerShell syntax and Git exclusion.

- Corrected the setup guide after checking current Google documentation: Sign in with Google/basic identity scopes do not require test-user allowlisting in Testing mode. A disabled Make internal control for personal Google accounts is expected; publishing the app is not required for this sign-in test.

- Replaced the portal's placeholder Google click handler and fake Client ID with one Google Identity Services widget shared by sign-in and sign-up. It uses public backend config and the existing Google verification/email OTP endpoints; only verified OTP completion stores the hotel session. Preserved the linked `customerId` in that session.
- Added truthful missing-config/SDK failure states, retry, duplicate-request protection and OTP resend/cancel. Removed the Facebook button, SDK and portal handlers.
- Removed legacy social authentication helpers that accepted unverified/mock credentials; the old direct Google/Facebook routes return 410. Google customer creation remains keyed by verified Google subject and never auto-links password accounts by email.
- Expanded [the Google setup guide](google-otp-setup.md) with first-time Client ID creation, localhost origins including 8081, SMTP setup, restart and troubleshooting. Updated API contracts. Validation: 98 web tests and 2 focused backend HTTP tests passed; live backend health/database remained UP/CONNECTED after restart and both legacy routes returned 410. Real Google account sign-in/sign-up awaits the developer's OAuth Client ID and SMTP configuration.

## [2026-10-21] — @codex — Customer portal contrast and support placeholder

- Added the customer Hỗ trợ tab directly below Dịch vụ & ưu đãi, with a themed “Sắp ra mắt” placeholder.
- Switched dashboard, voucher/service cards and profile content to theme/tier colors, including nested surfaces, labels, form fields and warning text. Restyled the dashboard booking action, retained readable zero statistics, and made the digital member card follow light/dark mode.
- Replaced the fixed VIP club caption with the actual Member/Silver/Gold/VIP tier, allowed cardholder names to wrap and escaped interpolated names. Corrected the Silver upgrade copy to use the tier policy's 5% room discount. Empty service catalogs now display an explanation.
- Updated the customer audit, synchronized source/public files and refreshed asset versions. Validation: **91/91 web tests passed**, including all four club captions and name escaping. Live desktop checks on `localhost:8081` covered the Member dashboard, services/vouchers, profile and support placeholder in light/dark mode. Mobile and other tiers still require visual acceptance; no real profile/service/message submission was performed.

## [2026-10-21] — @codex — Cloud concierge and independent developer setup

- Replaced the floating chat link with an authenticated in-page AI concierge and added administrator-only encrypted provider settings for Gemini, Groq, Claude and OpenAI. Added discovery of model IDs before saving a configuration, saved-connection testing, credential deletion and persistent Docker volumes.
- Added bounded Gemini conversation memory isolated by authenticated actor/session, explicit system instructions, safe provider errors and generation-status handling. Included public project knowledge for hotel hours, contact, amenities and website navigation, with a read-only admin preview and separate supplemental notes.
- Added [a new-machine setup guide](chatbot-ai-dev-setup.md) for Windows devs: isolated demo database/keys, Java and web launchers, provider/model configuration, test commands, origin/session troubleshooting and credential-storage behavior. Published on branch `CHATBOT_IMP`. Linked it from both READMEs and corrected the root README's outdated HTTP RAG instructions.
- Documented the new admin AI API and cloud chat behavior in `dev-spec/api-contracts.md`. Automated validation: 89 web tests and 38 focused backend tests passed. Live Gemini/browser checks returned the published check-in/check-out times and pool hours; provider availability and account quota remain external conditions.

## [2026-10-21] — @codex — Staff/customer portal UI audit fixes

- Aligned staff and customer headers, sidebar geometry, collapse controls, account placement and responsive navigation with admin. Fixed duplicate account/logout controls, left-aligned the brand, hid signed-in controls on login, and matched customer hover/selection animations while retaining tier colors.
- Unified staff customer records, bookings, invoices, profile/security forms and pagination; corrected room-map spacing. Redesigned checkout cards and the support inbox with responsive scrolling, search, drafts, automatic updates and a composer with a vertically centered send action. Fixed missing chat history when the latest inbox message was sent by staff.
- Standardized membership thresholds with the backend (Member 0, Silver 500, Gold 1,000, VIP 5,000 lifetime earned points); redemption no longer reduces tier progress. Added tier-colored membership cards and an admin membership placeholder below Customers.
- Fixed customer notifications stacking behind content; themed the dropdown, list, empty state and voucher controls for light/dark palettes and constrained the panel to the viewport. Synchronized served source/public assets and bumped cache versions.
- Added membership/chat regression tests and fixture-based browser verification scripts/results/screenshots under `docs/verification/web-ui/`. Latest notification/shell verification passed 40 tier/theme/viewport cases and 16 focused auth/theme/portal tests; earlier staff/table/checkout/support verification is recorded in [the UI audit](../web-ui/MANUAL_UI_AUDIT.md).
- Kept remaining customer light-theme contrast, accent consistency, duplicate headings, card/table layout and notification fallback issues explicitly open in the audit. Browser checks use mocked APIs and lack CDN font/icon/utility rendering in this environment; they do not establish live-backend acceptance.
- Pre-push verification: the complete web UI suite passed **69/69 tests**, with no failures or skips. HTTP integration tests required localhost networking outside the restricted sandbox.

## [2026-10-20] — @codex — `web-ui/portal-admin.css` — Keep customer content within the viewport

- Matched the administrator layout on desktop: the customer portal uses a fixed viewport with independently scrollable content while the header and sidebar remain in place. Restored natural page scrolling on narrow screens.

## [2026-10-20] — @codex — Responsive admin navigation controls

- Kept the administrator brand and theme header in one row, added a persistent manual sidebar collapse, and automatically reduced the vertical navigation to labeled icons in a narrow desktop viewport. The mobile navigation remains horizontal and icon-only.
- Removed the redundant hotel-home shortcut from the portal title bar.
- Removed the shield mark and account avatar from the administrator sidebar while keeping the account details and logout action.

## [2026-10-20] — @codex — Responsive customer navigation

- Matched the administrator navigation breakpoint: vertical customer sidebar above 820px, horizontally scrolling tabs at 820px and below. Kept the single account header and synchronized tab orientation for assistive technology.
- Verified desktop and phone layouts, switching to the profile tab, no horizontal page overflow, and nine portal regression checks.

## [2026-10-20] — @codex — CCCD scanning in the customer's own profile

- Removed the redundant shared title bar on the customer profile tab, retaining the profile form's own heading.
- Placed the member account information card above the profile inputs on desktop and mobile.
- Positioned the scanner after the personal information fields and before the save controls.
- Added local QR-image decoding and QR-reader text input, review-before-apply, clearing scan data, and an optional customer OCR gateway using the existing internal vision service. Bundled jsQR 1.4.0 and its Apache-2.0 license for offline QR decoding.
- Replaced profile access through browser-selected customer IDs and unconditional local success/cache with authenticated own-profile GET/PUT, transactional user/customer updates, encrypted identity writes, and honest API failure feedback. Profile reads expose only whether an identity is saved, and no longer claim identity verification.
- Verified a synthetic QR image in the browser, review/application without automatic persistence, successful profile save, and honest degradation when OCR is not configured. Backend tests cover customer/staff isolation, input validation, ownership, vault encryption, preserving a blank identity and rollback on identity conflicts.

## [2026-10-20] — @codex — Compact customer portal header

- Consolidated the customer account controls into the main navbar, leaving one brand and one row of shorter navigation tabs. Removed duplicate sidebar branding, content eyebrow and repeated return/close links from the customer layout.
- Retained the existing account/logout elements and restored their sidebar placement outside the customer portal. Added compact mobile controls and horizontal navigation scrolling.
- Verified the customer portal using `customer_test` at desktop, 390px and 320px widths; checked that account controls remain within the header and the page does not overflow horizontally.

## [2026-10-20] — @codex — Browser verification of administrator login

- Reproduced valid administrator sessions being cleared by customer-only notification HTTP 401 responses and static scripts being blocked by the shared unauthenticated API rate limit. Scoped lifecycle notifications to customers and excluded static handlers from that API budget while retaining API limits and security headers.
- Corrected the administrator login destination and role-specific page titles; guarded late dashboard responses after navigation; fixed portal JS/CSS asset paths and included the existing administrator stylesheet in the served public tree.
- Verified `admin_main` login through the browser, populated dashboard/rooms/bookings, reload persistence, and no JavaScript console errors in the verified flow. Regression checks: 54 web tests passed; focused static-file and API-budget backend tests passed.

## [2026-10-20] — @codex — Automatic sample import for the local demo

- Updated the demo launcher to import `database/hotel.sql` into a newly generated local schema, excluding server-wide user/grant statements, then apply migrations and encrypt sample identities with persistent demo keys.
- Added unique legacy promotion codes, retained the original sample accounts, and linked the additional demo guest login to an imported customer. Added automatic upgrade from the minimal demo and `-ReloadSample`; normal restarts reuse the saved schema.
- Verified imported counts (15 rooms, 13 customers, 131 bookings, 13 encrypted identities with no plaintext identities) and successful administrator/guest authentication and rooms, services, bookings, customers, promotions, profile and guest-stays HTTP requests.

## [2026-10-20] — @codex — Backend launcher correction

- Added `scripts/start-demo.ps1` and a schema-only Java bootstrap for a separate localhost demo database with persistent keys and synthetic accounts/inventory. Verified live health, both pages, both account logins, and authenticated rooms/services requests.
- Fixed Maven's hardcoded desktop main class so the backend launcher's `-Dexec.mainClass` override takes effect.
- Documented required persistent identity vault keys and the checked-in VM database configuration; added early key validation to the launcher.

## [2026-10-20] — @codex — Phase 4 reports, baseline and changelogs — Implementation evidence and pending release acceptance

- Added [implementation checklist](PHASE_4_IMPLEMENTATION.md), [implementation report](PHASE_4_REPORT.md), and [verification evidence](PHASE_4_VERIFICATION.md); linked current evidence from `PHASE_4_AND_BEYOND_BASELINE.md` and updated the root and Vietnamese documentation changelogs. Historical entries remain unchanged.
- Recorded Phase 4 database bootstrap, transactional service orders/vouchers, signed check-in passes, revenue approval controls, reconciliation, and authentication/payment/booking lifecycle fixes. Removed fabricated booking/payment success and inventory fallbacks.
- Verification: Maven test/package **187 tests, 0 failures, 0 errors, 38 skipped**; web UI **51 passed, 0 failures or skips**; isolated fresh MariaDB **2 integration tests passed**, applying all ten migrations. Skipped tests are not acceptance evidence.
- **Phase 4 release acceptance remains pending:** GATE-01 and GATE-03 passed; GATE-02 passed on a reproducible empty-schema equivalent, with Docker startup unverified; **GATE-04 blocked** by localhost/VM HTTP timeouts and unreachable VM database.
- Outstanding acceptance work: reachable deployed portal/API/webhook/database checks, Docker Compose/entrypoint verification, approved camera/OCR accuracy and timing trials, Python AI tests (`pytest` unavailable), invoice signing/issuer verification, supervised guest pilot, and configured load benchmark. Current PDFs are unsigned internal records.
- Recorded the initial legacy-fixture risk: synthetic rows may have been written to the application schema before disposable-schema guards were added. No prior snapshot exists to identify safe cleanup; no speculative deletion was performed.

## [2026-10-19] — @codex — docs/run-project.md, scripts/start-backend.ps1 — Local backend startup and login configuration
- Added Windows startup instructions, database settings, health verification, and troubleshooting.
- Added a Maven launcher that generates a local JWT secret to prevent token creation failures during login.

## [3.0.0] - 2026-10-17 — @architect — Phase 3 Final Audit, Customer Portal Enhancements, FlatLaf Theme & Go Decision
- **Added:** Web Concierge & Voucher Wallet module (`concierge.js`), Express Check-in QR Pass modal, E-Folio electronic invoice modal, auto-fill for customer bookings, `/api/v1/auth/change-password`, `/api/v1/services`, `/api/v1/service-usage` route registrations, Netplan static IP `192.168.56.102/24` configuration (`deploy/01-netplan-static-ip.yaml`), `DBConnection.java` compatibility wrappers.
- **Fixed:** Complete prevention of UI button freezing across all forms (`#profile-form`, `#password-form`, `#concierge-form`, cancel booking) via `try...catch...finally`; 100% binary synchronization between `web-ui/` and `web-ui/public/` verified with `fc /b`; MariaDB container port `3306:3306` exposure; flexible database configuration in `DatabaseConnection.java` and `config.properties`.
- **Changed:** Full RFC 7807 Problem Details compliance in `GlobalExceptionHandler.java` and `ApiResponseUtil.java` while preserving backward compatibility; FlatLaf Dark Luxury 5★ desktop theme standardization (`Table.rowHeight = 36`, `Button.arc = 12`, `Component.arc = 10`, `TextComponent.arc = 10`, `ProgressBar.arc = 10`, Slate Dark `#0F172A`, Panels `#1E293B`, Gold `#F59E0B`, Light `#F8FAFC`); static asset version parameter `?v=3.5.0` and Nginx `Cache-Control: no-cache, must-revalidate`.
- **Security:** Strict BCrypt + JWT Bearer protection, verified cancellation state guard, physical database isolation.
- **Verification:** 37/37 automated tests passing (**100% Pass Rate, 0 failures**). Official **GO** decision formalized in `docs/PHASE_3_GO_NOGO_ACCEPTANCE.md`.

## [2026-10-16] — @codex — Phase 3 implementation and acceptance evidence
- Synchronized the Phase 3 implementation, final verification evidence and explicit PARTIAL/unreleased status into `vi/CHANGELOG.md`; existing historical entries are preserved.
- Added `phase3-implementation-report.md` with per-task status, approved conflict resolutions, exact test/build evidence and unmeasured gates; Phase 3 is not accepted for release.
- Added `phase3-release-checklist.md` for vault/HMAC cutover, operational secrets/mTLS, model/native/load gates and rollback constraints.
- Added `security/owasp_zap_scan_report.md` as explicitly PENDING scan evidence; no scan or production deployment is claimed.
- Updated `dev-spec/api-contracts.md` with the approved P3 compatibility addendum; recorded approved frozen API compatibility aliases for P3 vision/concierge/revenue, conservative unavailable illustrative revenue, neutral cancellation MONITOR, and management-only low-price override. Original frozen examples remain available for comparison.
- Implemented Phase 3 backend/UI/service foundations: encrypted identity vault with HMAC exact-search index and explicit legacy cutover, OCR/face gateway, mandatory Java-to-AI mTLS, guarded concierge and scoped encrypted ticket chat, versioned rate approvals, retention/anonymization, logout revocation and IP rate limiting. Added Swing check-in/copilot/yield screens and self-contained desktop preview packaging.
- Final Java regression: **162 tests passed, 0 failures, 0 errors, 0 skipped** (32.374s), including the explicitly enabled aggregate benchmark. Python fixture suite: **5 passed**, 1 deprecation warning (0.45s), on Python 3.12; required Python 3.11 container validation remains pending.
- Disposable-database benchmark: 100,000 historical bookings and 730 closed daily snapshots, 20 warmed aggregate reads; final-run p95 **8.455ms**, maximum **10.162ms**. This measures the snapshot aggregate DAO, not a full booking-table scan or the two-hour load gate. Test data was rolled back and the task-created MariaDB instance was shut down.
- Verified local mutual TLS with disposable certificates, including rejection of missing client identity and wrong hostname. Built the final desktop preview with bundled JRE; it was not launched or released. Generated 100 visibly invalid synthetic OCR fixtures and a JMeter draft; neither establishes model accuracy or load acceptance.
- **Phase 3 remains PARTIAL / NOT RELEASED.** Pending gates include approved real-model accuracy/latency/resource measurements, camera/EDT/native UI/RAM checks, Python 3.11 Docker deployment and PKI validation, 200 CCU/two-hour soak, ZAP/manual penetration tests, and outstanding Phase 2 acceptance. Remaining implementation work includes semantic multilingual escalation, complete scan/face capture persistence and key-rotation lifecycle, and correlated/signed load fixtures. No production migration or deployment was performed; guest AI stays disabled by default.


## Rules
- Every change to `/docs/` MUST be logged here before commit.
- Format: `## [YYYY-MM-DD] — @author — file(s) — reason — what changed`
- If a constraint rule is added/modified → note which edge case or incident triggered it.
- If an API endpoint is added/removed → reference the phase (P1/P2/P3).
- Retention: permanent (do not delete old entries).

## [2026-10-16] — @copilot — docs/dev-spec/api-contracts.md, README.md — Phase 3 guest concierge gateway — Document customer JWT/RAG contract, confidence gate, local upstream configuration, and escalation ticket behavior.

## [2026-10-09] — @toan — All 17 files — Initial documentation set
- Created `/docs/ai-context/` (6 files): constraints, data-access, escalation-rules, edge-cases, monitoring, incident-response
- Created `/docs/system-prompts/` (2 files): chatbot-guest, staff-assistant
- Created `/docs/dev-spec/` (8 files): architecture, api-contracts, test-scenarios, prompt-loading, implementation-plan, tech-stack, integration, deployment
- AI Behavior Contract v1.0 (11 hard rules)
- Defined Phase 1 scope (5 core endpoints)
- Deployment: 6-container Docker architecture

## [2026-10-11] — @devops — docs/dev-spec/*, docs/system-prompts/* — Platform Architecture & Tech-Stack Realignment
- Embedded Platform Design Philosophy: "Platform = WHERE THE USER SITS" (Swing Staff Phase 1-3 vs React Web Guest/Admin Phase 3+).
- Realigned documentation to plain HttpServer, Maven (pom.xml), and container health configurations.

## [2026-10-12] — @devops — docs/dev-spec/ui-*.md, docs/vi/dev-spec/ui-*.md — UI/UX Design System Specification
- Created 4 UI specification files: `ui-tokens.md`, `ui-desktop-staff.md`, `ui-web-guest.md`, `ui-web-admin.md` and complete Vietnamese translations.

## [2026-10-12] — @toan — docs/*, database/hotel.sql, docker-compose.yml — Architectural Refinements & Risk Mitigation
- Unified MUI 5.x across Web UIs; deferred Admin Web to Phase 4 to focus Dev C 100% on Guest Web.
- Reallocated AI Gateway responsibilities: Dev D on FastAPI/pgvector skeleton, Dev A on Java API client, Dev B on Night Audit & guardrails.
- Replaced Swing JEditorPane with Native Components (JPanel + BoxLayout + FlatRoundBorder + JTextArea).
- Enforced physical PII database boundary: configured MariaDB user `ai_reader` with read-only SELECT permissions on rooms and promotions.
- Standardized HikariCP Connection Pool and Fixed Thread Pool on plain Java HttpServer.

## [2026-10-12] — @toan — docs/dev-spec/tasks-phase1.md, pom.xml — Phase 1 WBS Specification & Dependencies
- Standardized Phase 1 WBS execution plan across 4 weeks and 4 developers.
- Added TASK-A1.4 for JWT auth / login controller; added HikariCP, SLF4J, and jbcrypt to pom.xml.
- Corrected room domain status codes (`vacant_clean`, `reserved`) in optimistic locking queries.
- Adjusted HikariCP leak detection threshold to 5000ms and timeout to 30s.
- Formatted clean Cross-Developer Dependency Matrix and Go/No-Go Acceptance Checklist.
## [2026-10-13] — @devops — root, SVN_Repo_KhachSan — Clean SVN Folders & Artifacts
- Removed legacy `SVN_Repo_KhachSan/` mirror directory from git tracking and disk.
- Removed `DongBoSVN.bat` synchronization script.
- Cleaned unused SVN placeholder folders (`trunk/`, `branches/`, `tags/`).


## [2026-10-13] — @codex — Dev C C3 frontend/backend files — C3 workflow review fixes
- Added Phase 1 folio payment and POS batch-sync routes with idempotency handling; connected the desktop clients to the configured API and authenticated session.
- Wired HTTP 503 responses to the Night Audit warning banner, disabled registered mutation controls during the freeze, and auto-syncs queued POS charges after unfreeze.
- Routed F2 to check-in and F3 to check-out for the selected room; displayed master and guest folio balances in checkout.
- Standardized room-state handling to the documented six status values and prevented bookings unless a room is `vacant_clean`.
- Added a request-generation guard to prevent stale room refresh results from replacing newer results; aligned the quick-search bar with theme tokens.

## [2026-10-13] — @codex — B1/C1 backend, frontend, tests, and build files — B1/C1 implementation and verification status
- Implemented the B1 migration/versioning and idempotency paths, connected booking pricing to the VAT/service-charge calculator, and added unit and opt-in MariaDB integration coverage.
- Implemented the C1 shared asynchronous API client, global F1–F10 shortcut dispatcher, and shared UI theme tokens; added API/EDT tests and Maven test-runner configuration.
- `mvn -o test` passed the available unit/API tests (26 passed); the three MariaDB integration checks were skipped because no MariaDB 11 test database was configured. B1.1/B1.2 database verification remains outstanding.

## [2026-10-13] — @codex — UpdateDbUtility.java, MariaDbMigrationIntegrationTest.java, database/hotel.sql — MariaDB 11 integration verification
- Verified the full suite on an isolated MariaDB 11.8.6 instance: 26 tests passed, 0 failures, 0 skipped. Coverage includes repeatable legacy-schema migration, the complete `database/hotel.sql` import, concurrent idempotent requests, and persisted failure replay.
- Scoped migration metadata lookups to the active database, taught the schema integration test to honor SQL `DELIMITER` directives, and corrected booking seed 63 from invoice status `paid` to valid booking status `checked_out`.


## [2026-10-14] — @codex — Dev B/C room allocation and desktop room-map files — Phase 1 remaining-task audit and partial implementation
- Added a MariaDB 11 opt-in race test for two room reservations; it requires `HOTEL_RUN_DB_INTEGRATION=true` and a disposable schema whose name ends in `_test`, then checks one success, one conflict, and one version increment.
- Added the fixed 80×60 room tile, 5-column detached map, six canonical status colors, in-memory room/guest/booking filtering, dense 24px booking status renderer, safe detached-window bounds persistence, and an EDT dispatch duration reporter.
- Connected the compact map to `RoomForm`, quick filtering, booking table rendering, and `MainUI` startup. These additions advance B2.1, C2.2, C2.3, C3.3, C4.1, and C4.2; their full WBS definitions of done remain partially unverified.
- Changed classes and focused tests compiled with JDK 17; focused UI tests passed (3 passed). The MariaDB race test was aborted by its environment assumption because no test schema was configured. Maven could not start: the user-level repository lock was unwritable, and the workspace-local retry could not resolve dependencies because Maven Central DNS was unavailable.

## [2026-10-14] — @codex — Dev B/C Phase 1 service completion and integration review
- Added atomic folio payment persistence, master/guest routing and split handling, scheduled 50-record no-show processing, seven-step persisted night-audit checkpoints with resume support, DBR aggregation, credit/refund math, and manager-authorized OOO-room downgrade handling.
- Connected API load failures to the Swing room-map error state, retained the 5-column room map and detached-monitor persistence, and documented the repeatable two-hour front-desk simulation procedure.
- Corrected new booking folios to begin with zero outstanding balance so nightly ledger postings do not duplicate the booking estimate; standardized audit freeze responses on `Retry-After: 300`.
- JDK 17 compiled all production and test Java sources. Focused JUnit run: 6 passed, 0 failed, 2 opt-in MariaDB tests aborted because no disposable MariaDB test schema was configured. `mvn -o test` remains unavailable due an unwritable user-level Maven repository lock. The two-hour simulation and live MariaDB transaction/recovery scenarios still require execution in their target environments.

## [2026-10-15] — @codex — Phase 2 backend, desktop/web UI, migrations, tests, scripts, and documentation — Phase 2 implementation and acceptance status
- Added the guest portal, OTP, public booking, server-authoritative pricing and vouchers, concurrent room allocation, VietQR, HMAC-verified webhooks, idempotent folio settlement, and overpayment handling.
- Added housekeeping and room-state workflows, minibar posting, OOO/OOS maintenance, PDF invoices with explicitly required tax configuration, DBR reporting, and corresponding Swing/web interfaces; included Phase 2 migrations, test doubles, and regression coverage.
- Added the changed-file inventory, required production configuration, implementation/acceptance report, and verification evidence. Synthetic values are limited to test fixtures; no production legal, banking, or OTP values were fabricated.
- The Phase 2 report records 109 automated tests passing with 0 failures and 0 skips on Java 17 and isolated MariaDB 11.4.2. Overall acceptance remains partial pending manual Swing/render-timing checks, Docker verification, and approved production configuration.

## [2026-10-15] — @codex — scripts/validate-phase2-acceptance.py, tests/acceptance/, docs/phase2-manual-acceptance.md, docs/verification/phase2/manual-acceptance.json — Phase 2 Go/No-Go evidence gate
- Added a read-only acceptance validator that reports pending task IDs and rejects missing measurements, skipped/failed automated tests, missing verifier/evidence, and unverified Docker/baseline reconciliation.
- Added four isolated unit checks; retained previously verified browser width/console checks without inventing render or native timing measurements. Overall Phase 2 remains partially accepted.
- Git fetch from origin/PHASE_1 was not executed: automatic approval review was unavailable because its usage limit was reached. No incoming merge/conflict resolution is claimed.

## [2026-10-15] — @codex — Incoming Phase 1 integration, baseline recovery, configuration, and acceptance handoff
- Fetched origin/PHASE_1 at 3844597 and refreshed PHASE_2. Resolved conflicts while preserving Phase 2 transactional and authorization behavior; retained incoming test source and recovered core endpoint cases in isolated test fixtures.
- Reconciled the designated 47-case baseline (34 original + 13 recovered); clean merged regression passed 125 tests with 0 failures, errors, or skips, including a production server registration check.
- Added deep database health and incoming response hardening; corrected Compose schema/configuration forwarding and removed default production credentials.
- C5.1 passed at 375/800/1920 px with rendering below 83 ms and no console errors. Saved evidence and a strict read-only acceptance validator.
- User has no QA host access currently. Documented later Windows Swing and Docker-host execution; C5.3, C6.1, C6.2, C7.1, C7.2, C7.3, C8.1, C8.3 and final D8.4 sign-off remain open. No native or Docker pass is claimed.

## [2026-10-15] — @codex — docs/dev-spec/web-ui-unification-review.md — Existing web inventory and unification proposal
- Inventoried 23 existing web screen/workflow surfaces across Customer, Staff, Admin and shared authentication/profile; recorded keep/fix/replace dispositions and verified source-level integration bugs.
- Proposed shared documented navy/Inter/light tokens, role-specific layouts/components, phased implementation and browser end-to-end acceptance; identified contradictory/future-phase documentation without silently expanding feature or framework scope.
- Recorded versioned-route and customer-ownership backend blockers for prior alignment. No application/backend changes or new browser E2E pass are claimed; style guide and implementation order await agreement.

## [2026-10-15] — @codex — web-ui/public/app.js, docs/dev-spec/web-ui-unification-review.md, web-ui tests — Role shell and session recovery
- Wired explicit presentation roles into the portal shell; Admin, Customer, and Front Desk retain separate menus, while Housekeeping, Maintenance, and F&B receive a restricted informational shell without Front Desk actions. Unknown roles fail closed.
- Connected expired-session notifications to shell logout and login feedback. Updated the review record for the agreed vanilla/navy/Inter direction and persisted light plus legacy dark modes; versioned-route and ownership backend changes remain unapproved.
- Focused Node suite: 15 passed, 0 failed, 0 skipped. Browser verified login light/dark toggle and persisted preference on reload; role end-to-end flows and target viewport matrix remain outstanding.
- `git fetch origin` could not read `.git/FETCH_HEAD` due environment permission denial; upstream movement was not verified. No merge, push, or backend changes were made.

## [2026-10-15] — @codex — web-ui/public/js/staff/room-map.js, web-ui/public/portal.html, shared-theme.css — Canonical room map and API setup copy
- Reworked the room map into a seven-state, text-labeled status overview using the shared canonical room-status helper, safe DOM text, responsive legend styles, and request-generation guards. Removed legacy click actions that posted client-calculated payments or reset room status to `available`; operational tasks remain in their separate modules.
- Corrected the portal API configuration placeholder and ngrok guidance to use `/api/v1`.
- Browser theme checks passed for light/dark toggle and preference persistence. Full role workflow/viewport acceptance has not been run because authenticated test access is unavailable here; backend checkout/ownership dependencies remain unresolved.
- Reworked Staff checkout to read booking folios from existing APIs, show server balances, settle with UUIDv4 idempotency keys, create configured VietQR and poll server payment status, then require a separate explicit checkout action. No client amount field, external QR account or payment-rule change remains in this screen.
- Added authenticated PDF blob retrieval for Admin and Staff invoices; session 401 handling is shared. Dashboard metrics now show loading/unavailable states rather than false zeros, with retry and theme-token chart styling.
- Re-ran focused suite after integration: 16 passed, 0 failed, 0 skipped. JavaScript syntax and `git diff --check` passed. Authenticated financial workflows still require isolated fixture browser acceptance.
- Elevated `git fetch origin` succeeded after the initial metadata permission failure. `origin/PHASE_1` and local `PHASE_1` remain at 3844597; `PHASE_2` remains 7 commits ahead of `origin/PHASE_2` with 0 incoming commits. No merge or push was performed.
- Generic `STAFF` sessions now fail closed unless the backend supplies a distinct supported department role; label normalization accepts `Front Desk` and `FRONT_DESK` without merging departmental menus.
- Replaced unsupported Staff loyalty discount promises with a shared status notice. Customer dashboard/bookings, account booking discovery, and personal loyalty views now show explicit blockers instead of sending customer-ID-dependent requests or calculating account booking prices locally. The distinct OTP Guest flow and unrelated Customer promotions/chat remain separate.

## [2026-10-15] — @codex — web-ui/public/css/theme-overrides.css — Light-mode readability and mobile floating controls
- Improved light-mode text contrast for muted labels and gold-gradient text, and restored a pale gold logo treatment over the unscrolled dark header.
- Set mobile hotline and chat controls to consistent 48px touch targets with 20px icons.
- Removed the experimental hero and footer gradients after review; no test run recorded for these CSS-only updates.

## [2026-10-15] — @codex — web-ui theme surfaces, docs/WEB_UI_ISSUES.md, docs/CHANGELOG.md, docs/vi/CHANGELOG.md — Global theme and UI issue tracking
- Added one shared Auto/Light/Dark theme control across the hotel landing pages and portal, with browser-preference tracking, persistent cross-page selection, accessible control labels, and matching light palettes; preserved customer and management layouts.
- Added theme behavior tests and recorded browser theme-color updates. Focused Node tests passed (6/6); JavaScript syntax and `git diff --check` passed.
- Created GitHub issues for outstanding UI/integration findings: #36 unsupported social icons, #37 lightbox close control, #38 room identity/pricing, #39 incomplete English localization, #40 missing party/room selectors, #41 admin statistics endpoint, #42 customer dashboard/loyalty loading, and #43 offers loading state.
- Documented the issue references in `docs/WEB_UI_ISSUES.md`. No issues already marked resolved were re-opened; `.phase2-runtime/` remains excluded from version control.
# 2026-10-16 — Room status labels

- Added the optional read-only `include_status=true` availability projection to `dev-spec/api-contracts.md`; existing endpoint behavior remains unchanged by default. No schema or data changes.

## [2026-10-23] — Invoice tests, database bootstrap, API contract, and QA tracking

- Updated internal invoice PDF tests to match the current payment-statement contract and moved their generated PDF to `target/qa-artifacts/`.
- Added the electronic-invoice migration to the shared fresh-database bootstrap manifest.
- Documented the MISA meInvoice routes and access rules in `docs/dev-spec/api-contracts.md`.
- Added the QA bug report, test progress, and decision log. Maven ran 278 tests: 236 passed, 0 failed, and 42 guarded skips; the web UI suite passed all 133 tests.

## [2026-10-24] — Shared payment history pagination

- Added exact totals to payment and notification history responses and reused the portal-wide pagination component for page size, result ranges, page indicators, and navigation states.
- Grouped the history table in the shared card treatment and moved “Thanh toán & thông báo” below “Hóa đơn” with a wallet icon.
- Recorded the live verification as QA-2026-017; no database records, payments, or emails were changed.

## [2026-10-26] — Admin operations dashboard

- Reworked the administrator overview to combine payment collections, room occupancy, pending reservations, arrivals/departures, housekeeping, refunds and cancellations in one responsive dashboard.
- Added the admin-only `GET /api/v1/reports/admin-overview` report, using business-date folio records and auditable refund statuses; documented calculation periods and denominators in the API contract.
- Replaced the single-month invoice chart request with a six-month net folio revenue series. No database schema or records were changed.
- Moved accommodation-type hourly and overnight rate editing into room management, added a shortcut to the section, and redirected old rate-page links to the room screen.
- Manual port-3000 checks: account list and add/role/delete dialogs rendered correctly; no account or permission mutation was submitted. The admin dashboard error is reproducible because the running 8081 process predates the latest report route; tracked as QA-2026-020 and pending rebuild/restart verification.

## [2026-10-26] — Cloudflare R2 room images

- Added optional Cloudflare R2 storage for room images using server-side S3 SigV4 credentials; the browser receives only image URLs and credentials remain in the backend environment.
- Kept local media as the default and documented private proxy reads and direct HTTPS public-base configuration.
- Verified on port 3000 by uploading a sample image to QA-801, saving, reopening the room dialog, and loading the persisted 1200 × 900 image from R2. The room price and housekeeping state were unchanged.
- Focused checks passed: 4 R2 adapter tests, 3 room-image URL validation tests, and 2 browser upload-helper tests.
