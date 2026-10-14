# SHARED UI DESIGN TOKENS

## 1. COLOR PALETTE
| Token | Hex Value | Usage |
|---|---|---|
| `color-primary-accent` | `#1B2A4A` | Brand navy accent, primary buttons, headers |
| `color-success` | `#2E7D32` | Success status, confirmed state, positive trends |
| `color-warning` | `#F57C00` | Alerts, pending, attention required |
| `color-error` | `#C62828` | Critical errors, validation failure, destructive action |
| `color-info` | `#1565C0` | Informative notices, link accents |
| `color-bg-light` | `#FAFAFA` | Global default background (Light mode) |
| `color-bg-dark` | `#1E1E1E` | Global background (Dark mode, Phase 4+) |
| `color-text-primary` | `#212121` | High-contrast headers and body text |
| `color-text-secondary` | `#757575` | Captions, muted labels, timestamps |
| `color-border` | `#E0E0E0` | Dividers, card boundaries, input borders |

### Room Status Colors
| Status Code | Status Name | Hex Color | Semantics |
|---|---|---|---|
| `VC` | Vacant Clean | `#4CAF50` | Ready for guest check-in |
| `VD` | Vacant Dirty | `#FF9800` | Housekeeping required |
| `OC` | Occupied Clean | `#2196F3` | In-house guest, cleaned |
| `OD` | Occupied Dirty | `#FF5722` | In-house guest, needs service |
| `OOO` | Out of Order | `#F44336` | Maintenance block / disabled |
| `RESERVED` | Reserved | `#9C27B0` | Allocated for incoming arrival |

## 2. TYPOGRAPHY
- **Font Families:** `Inter, sans-serif` (Web), System native default font (Java Swing).
- **Allowed Font Sizes:** Exactly 6 tokens: `12px`, `14px`, `16px`, `20px`, `24px`, `32px`.
- **Allowed Font Weights:** Exactly 4 weights: `400` (Regular), `500` (Medium), `600` (Semi-bold), `700` (Bold).
- **Line Heights:** `1.5` for body text; `1.2` for headings.

## 3. SPACING SYSTEM (4px Rhythm)
All padding, margin, and gap values MUST use one of the following tokens:
`4px`, `8px`, `12px`, `16px`, `24px`, `32px`, `48px`, `64px`. Odd pixel values are strictly forbidden.

## 4. BORDER RADIUS
- `radius-sm`: `4px` (Inputs, buttons, table badges)
- `radius-md`: `8px` (Cards, dialog windows, popup containers)
- `radius-lg`: `12px` (Large hero cards, chat message bubbles)
- *Constraint:* Never exceed `12px` (no circular cards or pill buttons except input send button).
- *Exception:* Pill-shaped inputs (chat input bar, prompt chips): max `24px`.

## 5. ELEVATION & SHADOWS
- `elevation-l1`: `0 1px 3px rgba(0,0,0,0.12)` (Flat cards, hover state)
- `elevation-l2`: `0 4px 6px rgba(0,0,0,0.12)` (Dropdowns, floating toolbars, modals)
- `elevation-l3`: `0 10px 20px rgba(0,0,0,0.15)` (Active dialogs, alerts)

## 6. Z-INDEX LAYERING
- `z-dropdown`: `10`
- `z-modal`: `100`
- `z-toast`: `1000`
- `z-fullscreen`: `10000`

## 7. ANIMATION DURATIONS
- **Staff Desktop (Swing):** `0ms` (instant). No CSS transitions. Hard max `150ms` for modal open/close only.
- **Guest Web (React):** ≤ `300ms` (Smooth transitions, wizard page slides).
- **Admin Web (React):** ≤ `200ms` (Drawer slide, dropdown menu expand).

## 8. COMPONENT LIBRARIES & ICONS
- **Staff Desktop:** Java Swing with FlatLaf `3.4.1` (Light theme default).
- **Guest Web:** React 18 with MUI `5.x`.
- **Admin Web:** React 18 with Ant Design `5.x`.
- **Charting Engine:** Recharts `2.x`.
- **Icon Set:** Material Icons 24px (Web); FlatLaf built-in vectors 24px (Desktop).
