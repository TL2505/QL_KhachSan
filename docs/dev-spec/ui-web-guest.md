# UI CONVENTIONS: WEB GUEST (REACT 18 + MUI)

## 1. DESIGN SPECIFICATION & FOUNDATION
- **Framework:** React 18 + Material-UI (MUI `5.x`) + TypeScript.
- **Target Audience:** Hotel Guests on mobile phones, tablets, laptops.
- **Design Philosophy:** Mobile-first. Touch targets. Zero cognitive load (1-3 min session completion).
- **Tokens Reference:** All colors, typography, status codes, and spacing imported from `ui-tokens.md`.

## 2. RESPONSIVE BREAKPOINTS & LAYOUT
- **Breakpoints:** Mobile `375px` (iPhone SE), Tablet `768px` (iPad), Desktop `1200px` (Laptop).
- **Max Width Container:**
  - Phone: `100%` up to `480px`.
  - Tablet: Centered up to `768px`.
  - Laptop: Centered `1200px` with `24px` horizontal padding.
- **Touch Target Rule:** Minimum tappable area is `44×44px`. Minimum gap between tappable items is `8px`.
- **Zero Hover Dependency:** No UI element may require hover to display actions, tooltips, or essential data.

## 3. NAVIGATION & HEADER
- **Header:** Sticky top, height `56px`, elevation `0 1px 3px rgba(0,0,0,0.12)`.
  - Left: Hotel name (`16px`, weight `600`).
  - Right: Language switcher (globe icon) + notification bell.
- **Bottom Navigation Bar (Mobile):** Fixed bottom, height `56px`, maximum 5 tabs.
  - Active Tab: Primary accent color fill, filled Material Icon, `12px` label.
  - Inactive Tab: `#757575`, outlined Material Icon, `12px` label.
- **Sub-pages:** Slide in from right (duration `300ms` cubic-bezier ease).

## 4. LOADING & SESSION FLOW
- **Skeleton Screens:** Pulsing gray (`#E0E0E0`) skeleton blocks matching final layout shape (`1.5s` cycle). NEVER display blank white pages or spinner-only blockers.
- **Wizard Forms (1 Question Per Screen):**
  - Top thin progress bar (`4px`, primary accent color).
  - Back arrow at top-left.
  - Primary "Next" button at bottom: full-width, height `48px`, border-radius `8px`, primary accent bg, white text `16px` weight `600`.
  - Final step button label: "Confirm Booking".
- **Session Continuity:** If flow exceeds 3 steps, save form draft in `localStorage`. Restore instantly on return.

## 5. ROOM CARDS & CALL-TO-ACTION (CTA)
- **Room Type Cards:**
  - Container: `16px` padding, border `1px solid #E0E0E0`, border-radius `8px`, elevation-l1.
  - Media: Top image in `16:9` aspect ratio with `8px` rounded corners.
  - Typography: Room title `16px` weight `600`; Nightly price `20px` weight `700` primary accent.
  - Amenities: Max 3 key amenity icons with `12px` descriptive text.
  - Book Button: Full-width button, height `44px`, border-radius `4px`.
- **Primary CTA Rule:** Exactly 1 primary CTA button per view. Disabled state: bg `#E0E0E0`, text `#757575`.

## 6. AI CONCIERGE CHAT INTERFACE
- **Message Bubbles:**
  - AI Bubble: Left aligned, background `#F5F5F5`, border-radius `12px`, max-width `80%`.
  - Guest Bubble: Right aligned, background primary accent `#1B2A4A`, white text, border-radius `12px`, max-width `80%`.
  - Timestamp: `12px` color `#757575` positioned below bubble.
  - Text Limit: Max 4 lines displayed by default; overflow displays "Read more" expand link.
- **Chat Input Bar:** Fixed bottom above tab bar, height `56px`, border-radius `24px`, background `#FFFFFF`. Send button: circular `36px` accent icon button.
- **Prompt Chips:** Exactly 3 quick chips above input (`12px`, border `1px solid #E0E0E0`, border-radius `16px`, background `#FFFFFF`).

## 7. LOCALIZATION, VALIDATION & FEEDBACK
- **i18n Localization:** Auto-detect via `navigator.language` on first visit. Manual toggle dropdown: EN, VI, JP, ZH. Persist language code in `localStorage`.
- **Validation Errors:** Displayed inline below input field (`12px`, color `#C62828`, margin-top `4px`). NEVER use native browser `alert()` or blocking modals.
- **Success State:** Green checkmark icon (`24px`) + text "Booked!" (`20px`, weight `600`) + auto-redirect after `1.5s`. No confetti or decorative clutter.
- **Empty State:** Clean SVG illustration (`120px`) + muted description (`16px`, `#757575`) + primary action CTA.

## 8. HARD CONSTRAINTS
- **DO:** Enforce touch targets ≥ `44px`. Use skeleton loaders. Support EN/VI/JP/ZH. 
- **DON'T:** NEVER use hover dependencies. NEVER use animations > `300ms`. NEVER load uncompressed images > `200KB`. No dark mode in Phase 3.
