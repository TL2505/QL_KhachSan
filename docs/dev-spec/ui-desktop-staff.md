# UI CONVENTIONS: DESKTOP STAFF (JAVA SWING)

## 1. DESIGN SPECIFICATION & FOUNDATION
- **Framework:** Java Swing + FlatLaf `3.4.1` (`FlatLightLaf.setup()`).
- **Target Audience:** Front Desk, Housekeeping, F&B (8-hour shift, desk-bound).
- **Design Philosophy:** Speed > pretty. Zero state loss. Keyboard driven.
- **Tokens Reference:** All colors, typography, status codes, and spacing imported from `ui-tokens.md`.

## 2. WINDOW & LAYOUT
- **Window Size:** Min `1280×720px`. Resizable.
- **Persistence:** Save window width, height, X, Y coordinates to local `.properties` file on exit.
- **Title Bar:** `Hotel PMS — [Role Name]` (e.g., `Hotel PMS — Front Desk Agent`).
- **3-Panel Layout:**
  - Left navigation sidebar: Fixed `200px` width.
  - Center workspace: Flexible width (`100% - 500px`), dense views.
  - Right detail panel: Fixed `300px` width (collapsible).
- **Top Toolbar:** Height `48px`, contains primary action buttons with shortcut tooltips.
- **Status Bar:** Height `24px` at bottom, displays current user, network status, database ping.
- **Multi-Monitor:** Main PMS window on Primary monitor. Room Map can launch in a detached `JFrame` on Secondary monitor with remembered bounds.

## 3. MANDATORY KEYBOARD SHORTCUTS
- `F1`: Open contextual Help
- `F2`: Trigger Check-in modal
- `F3`: Trigger Check-out modal
- `F4`: Focus Room Search input
- `F5`: Force refresh active data
- `F6`: Select next room in list/map
- `F7`: Select previous room in list/map
- `F8`: Open Folio for selected room/guest
- `F9`: Create New Booking
- `F10`: Print current view / invoice
- `Ctrl + S`: Save active record
- `Ctrl + Z`: Undo last form action
- `Ctrl + F`: Focus global filter bar
- `Ctrl + P`: Print record
- `Esc`: Close active modal / cancel edit
- `Enter`: Submit form / confirm dialog
- `Tab / Shift + Tab`: Cycle next/previous form fields

## 4. DENSE DATA TABLES
- **Row Height:** Exactly `24px` (dense mode). Max `32px` under any circumstances.
- **Alternating Rows:** Row `N` = `#FFFFFF`, Row `N+1` = `#F5F5F5`.
- **Visibility:** Minimum 15 rows visible without scrolling on `1280×720px`.
- **Interactions:** Sortable column headers. Top filter bar with text search and status dropdown.
- **Status Indicators:** `8px` colored circle + text label (colors from `ui-tokens.md`). Never text-only.

## 5. ROOM MAP GRID (DEDICATED SCREEN)
- **Matrix:** 5 columns × N rows grid.
- **Cell Dimensions:** Exactly `80×60px`.
- **Cell Contents:**
  - Room Number: Top-left, `12px` bold text.
  - Background Fill: Room status color from `ui-tokens.md` at 80% opacity.
  - Guest Name: Bottom-left, `12px` text (visible only when status = `OC`).
- **Events:** Single click updates Right Detail Panel (`300px`). Double click opens Folio modal (`F8`).

## 6. FORMS & INPUTS
- **Field Layout:** 1-column vertical form.
- **Labels:** Positioned above input field (`14px`, text-secondary). Required fields prefixed with red asterisk `*`.
- **Input Fields:** Height `32px`, border `1px solid #E0E0E0`, border-radius `4px`.
- **Buttons:** Submit at bottom-right (height `32px`, min-width `48px`, primary accent bg, white text). Cancel button adjacent (gray border, transparent bg).

## 7. DIALOGS, MODALS & NOTIFICATIONS
- **Modal Window:** Centered, max-width `600px`, `elevation-l2` shadow. Header with title and close X button. Footer with Cancel (left) and Confirm (right). Backdrop dim: `rgba(0,0,0,0.5)`.
- **Toasts:** Upper-right corner, `300px` width, auto-dismiss in 5s. Left border `4px` solid (green=success, amber=warning, red=error). Icon + message text.
- **Empty State:** Gray `48px` icon + `14px` text "No data" + optional CTA button.
- **Error State:** Field border turns `2px solid #C62828`. Error text `12px` below input. No shake animations.

## 8. HARD CONSTRAINTS
- **DO:** Bind keyboard shortcuts to all functions. Keep dense tables. Use color dots for status.
- **DON'T:** NEVER use animations or transitions (> `150ms`). NEVER use generic `JOptionPane.showMessageDialog` (use custom FlatLaf modal). NEVER use font sizes outside defined tokens.

## 9. AI STAFF COPILOT DOCK (JAVA SWING)
- **Component Architecture:**
  - Do NOT use `JEditorPane` (avoids HTML 3.2 rendering bugs, slow paint, and Vietnamese font breakage).
  - Use `JPanel` with `BoxLayout(box, BoxLayout.Y_AXIS)` placed inside a `JScrollPane` (smooth scrolling, auto-scroll to bottom on new message).
- **Message Bubble Standards:**
  - Each message bubble is a child `JPanel` with `new FlatRoundBorder()` (arc `12px`).
  - Text rendered via `JTextArea`: `setWrapStyleWord(true)`, `setLineWrap(true)`, `setEditable(false)`, system font `14px`.
  - **Bubble Colors:**
    - Copilot (AI) Bubble: Background `#F5F5F5`, border `1px solid #E0E0E0`, left-aligned.
    - Staff Bubble: Background `#1B2A4A` (Primary Accent), white text, right-aligned.
- **Performance:** Message insertion and layout repaint < 10ms. Non-blocking UI via `SwingWorker` for async AI API responses.

