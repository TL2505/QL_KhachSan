# UI CONVENTIONS: WEB ADMIN & GM (REACT 18 + MUI 5.X — PHASE 4)

## 1. DESIGN SPECIFICATION & FOUNDATION
- **Framework:** React 18 + Material-UI (MUI) `5.x` + TypeScript (Unified single design system with Guest Web; scheduled for Phase 4 while Phase 1–3 relies on desktop Swing `AdminForm.java`).
- **Target Audience:** General Manager, Operations Directors, System Admins.
- **Design Philosophy:** Data density, instant scanning, deep reporting, responsive (laptop, tablet, phone).
- **Tokens Reference:** All colors, typography, status codes, and spacing imported from `ui-tokens.md` (reusing `theme.ts` & `ApiClient.ts`).


## 2. NAVIGATION & WORKSPACE LAYOUT
- **Desktop Layout (> 1200px):**
  - Left Sidebar: Fixed `240px` (collapsible to `64px` icon-only mode).
  - Top Navigation Bar: Height `56px`, elevation-l1. Global search on left; notification bell and user avatar profile on right.
  - Main Canvas: Padding `24px`, background `#FAFAFA` (or `#1E1E1E` in dark mode).
- **Sidebar Menu Items:**
  - Height `40px`, icon `20px` + label `14px`.
  - Active Item: Primary accent background at 10% opacity + `3px` solid accent left border.
  - Sections: Dashboard, Rooms, Bookings, Revenue, Staff, AI Monitor, Settings.
- **Collapsed Mode (`64px`):** Icons centered, tooltips displayed on hover.

## 3. DASHBOARD HOME VIEW (ANALYTICS)
- **Row 1 — 4 KPI Cards (Equal Width Grid):**
  - Card Metric: Category label (`12px`, `#757575`), KPI value (`32px`, weight `700`).
  - Trend Indicator: Up arrow green (`#2E7D32`) or down arrow red (`#C62828`) (`12px`).
  - Period Filter: Segmented switch (7d / 30d / 90d) top-right.
- **Row 2 — Chart Grid (Total Height `280px`):**
  - 60% Width: Recharts Line Chart (Occupancy & RevPAR trends).
  - 40% Width: Recharts Donut Chart (Channel / Segment distribution).
- **Row 3 — Recent Bookings Table:**
  - 10 rows visible. Columns: Guest Name, Room, Check-in, Check-out, Status Badge, Folio Amount.

## 4. DATA TABLES & DETAIL DRAWER
- **Row Dimensions:** Height exactly `40px` per row (high-density MUI Table / DataGrid compact mode).
- **Typography:** `14px` primary text, `12px` for secondary timestamps/IDs.
- **Controls:** Header sorting indicators; top filter bar with search input and multi-select filters; "Export CSV" and "Export PDF" buttons on top-right.
- **Pagination:** Options for 20 / 50 / 100 rows per page.
- **Detail Drawer:** Clicking a row opens a slide-in drawer from the right (width `400px`, duration `200ms`) with full reservation metadata and activity logs.

## 5. CHARTS (RECHARTS 2.X ENGINE)
- **Supported Visuals:** Line (trends), Bar (channel comparisons), Donut (guest segmentation).
- **Constraints:** Maximum 3 charts per screen. Fixed height `280px`.
- **Palette:** Chart series use primary accent `#1B2A4A` alongside categorical tokens from `ui-tokens.md`.
- **Interactivity:** Instant hover tooltip showing absolute number and percentage. Render with zero entrance animations for instant data perception.

## 6. FORMS & INPUT LAYOUT
- **Structure:** 2-column horizontal form (label width `120px` left, input field on right).
- **Form Card Groups:** Contained in white cards with `16px` padding, border `1px solid #E0E0E0`, border-radius `8px`.
- **Section Headers:** `16px` weight `600`, bottom margin `16px`.
- **Action Buttons:** Submit button bottom-right (primary accent bg); Cancel button adjacent (gray border outline).

## 7. RESPONSIVE BEHAVIOR & THEME
- **Tablet (768px – 1200px):** Sidebar auto-collapses to `64px`; charts stack vertically; table maintains horizontal scroll.
- **Mobile (< 768px):** Sidebar hidden behind top hamburger drawer; KPI cards render 2 per row; tables convert to compact cards (1 item per card).
- **Dark Mode Support:** User menu dropdown offers "Dark Mode" toggle using MUI `ThemeProvider` (`palette.mode = 'dark'`). Preferences saved in `localStorage`.
- **Notifications Panel:** 320px dropdown panel from header bell icon. Unread items display a primary accent dot and bold text.

## 8. HARD CONSTRAINTS
- **DO:** Include date range pickers on all reports. Provide CSV/PDF exports. Support keyboard tab navigation.
- **DON'T:** NEVER place more than 3 charts on one page. NEVER animate transitions > `200ms`. NEVER omit table pagination.
