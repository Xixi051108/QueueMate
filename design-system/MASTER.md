# QueueMate Design System

> Version 1.1 · 2026-07-17
> Scope: QueueMate Vue 3 web application  
> Source of truth: global visual tokens, interaction rules, responsive behavior, and component states

## 1. Product and design direction

QueueMate is a local-life booking and queueing tool for customers, venue operators, and administrators. Its interface should feel like a calm, efficient city service counter: information is easy to scan, the next action is obvious, and status changes are trustworthy.

The visual signature is the **service ticket**. Queue numbers, booking codes, time slots, and consumption codes use a restrained ticket-like treatment: tabular numbers, perforation-style dividers, and compact status stamps. This is functional structure, not decoration.

Design principles:

1. **Status before ornament.** Capacity, price, queue progress, and booking state must be visible before supporting copy.
2. **One primary action per region.** Secondary actions remain visually quiet.
3. **Calm surfaces, decisive signals.** Most of the interface is neutral; color is reserved for navigation, actions, and state.
4. **Mobile use is first-class.** A user may book or take a number while standing outside a venue.
5. **No simulated real-world integrations.** Do not introduce maps, real payment branding, SMS, or social-login visuals.

Avoid:

- purple/pink AI gradients, glassmorphism, decorative blobs, or generic bento marketing layouts;
- gradients on buttons or cards;
- excessive pill shapes; pills are limited to compact status labels;
- radius values above 12px on ordinary surfaces;
- decorative animation, autoplay motion, or scroll reveals;
- emoji as interface icons; use the Element Plus icon set or simple SVG icons;
- vague copy such as “提交” when a specific verb such as “确认预约” is available.

## 2. Existing-page audit

The initial audit found an empty frontend. The current implementation now covers public browsing, customer self-service, venue operations, and administrator tools while preserving the same service-counter visual language.

| Area | Current implementation | Ongoing rule |
| --- | --- | --- |
| Color | Service-blue semantic palette is implemented | Use semantic tokens below; no raw color values inside page components |
| Typography | Chinese-first system stack and data face are implemented | Keep codes, currency, times, and queue numbers tabular |
| Spacing | 4px-based scale is applied across all roles | Reuse the established 8/12/16/24/32 rhythm |
| Layout | Shared service header and 1200px rail are implemented | Keep role navigation predictable and scroll-safe |
| Components | Shared loading, empty, status, venue, ticket, and form patterns exist | Extend existing patterns before adding new visual language |
| Responsive | Public, user, merchant, and admin pages are responsive | Validate 375, landscape, tablet, desktop, and reduced motion |

This is a greenfield implementation, not a redesign. Backend business rules and endpoint contracts remain unchanged.

## 3. Color tokens

The palette is inspired by public-service signage: deep blue for trust and navigation, signal amber for attention, and green for completed states. White and cool gray carry most surfaces.

| Token | Value | Usage |
| --- | --- | --- |
| `--qm-primary-700` | `#124E78` | pressed actions, active navigation |
| `--qm-primary-600` | `#176B9C` | primary buttons, links, focus accents |
| `--qm-primary-100` | `#DCEEF7` | selected and informational backgrounds |
| `--qm-primary-050` | `#F0F7FB` | subtle brand surface |
| `--qm-ink-900` | `#142B3A` | headings and primary text |
| `--qm-ink-700` | `#38505F` | body text |
| `--qm-ink-500` | `#667A86` | secondary text |
| `--qm-line-300` | `#CAD5DB` | strong borders and dividers |
| `--qm-line-200` | `#DCE4E8` | default borders |
| `--qm-surface` | `#FFFFFF` | cards and controls |
| `--qm-canvas` | `#F5F7F8` | page background |
| `--qm-success-700` | `#18734A` | success text and icons |
| `--qm-success-100` | `#DCF3E7` | success background |
| `--qm-warning-700` | `#A64B08` | warnings, limited capacity |
| `--qm-warning-100` | `#FCE8D2` | warning background |
| `--qm-danger-700` | `#B42318` | destructive actions and errors |
| `--qm-danger-100` | `#FDE3E1` | error background |
| `--qm-info-700` | `#2458A6` | neutral progress information |
| `--qm-info-100` | `#E2EBFA` | information background |

Rules:

- Body text must meet WCAG AA contrast (4.5:1 minimum).
- Status never relies on color alone; pair color with a text label and, where useful, an icon.
- The primary button uses `primary-600` with white text. Destructive actions are not styled as primary until the confirmation step.
- Disabled state uses neutral colors, not reduced opacity alone.

## 4. Typography

Use local/system fonts to keep the app fast and reliable in a local development environment.

```css
--qm-font-sans: "Noto Sans SC", "Microsoft YaHei UI", "PingFang SC", system-ui, sans-serif;
--qm-font-data: "DIN Alternate", "Roboto Mono", Consolas, monospace;
```

| Role | Size / line-height | Weight | Notes |
| --- | --- | --- | --- |
| Display | 36px / 44px | 700 | Login message or major queue number; mobile 30/38 |
| H1 | 30px / 38px | 700 | One per page; mobile 26/34 |
| H2 | 22px / 30px | 700 | Page sections |
| H3 | 18px / 26px | 600 | Cards and panels |
| Body | 16px / 26px | 400 | Default readable text |
| Body strong | 16px / 24px | 600 | Labels and important values |
| Small | 14px / 22px | 400 | Supporting information |
| Caption | 12px / 18px | 500 | Metadata only; never long body copy |
| Data large | 32px / 36px | 700 | Queue number and wallet balance; tabular numbers |

Use sentence case in Chinese labels. Do not use letter spacing on Chinese body text. Codes, currency values, times, and queue numbers use the data font with `font-variant-numeric: tabular-nums`.

## 5. Spacing and layout

Spacing tokens use a 4px base:

```text
4, 8, 12, 16, 20, 24, 32, 40, 48, 64
```

- App content width: `min(1200px, calc(100% - 48px))` on desktop.
- Mobile page gutter: 16px; tablet gutter: 24px; desktop gutter: 32px.
- Page section gap: 32px desktop, 24px mobile.
- Card padding: 24px desktop, 16px mobile.
- Form control gap: 16px; adjacent action gap: at least 8px.
- Dense metadata rows may use 8px vertical gaps but must retain a 44px minimum interactive target.

Breakpoints:

| Name | Width | Behavior |
| --- | --- | --- |
| Mobile | `< 768px` | Single column, full-width primary actions, cards replace wide tables |
| Tablet | `768–1023px` | Two-column venue grid, compact header |
| Desktop | `1024–1439px` | Three-column venue grid, full navigation |
| Wide | `≥ 1440px` | Content remains capped at 1200px; whitespace grows outside the rail |

No page may require horizontal scrolling at 375px. Wide data tables must become stacked records or use an explicitly labelled scroll region.

## 6. Radius, borders, and elevation

```css
--qm-radius-sm: 4px;
--qm-radius-md: 8px;
--qm-radius-lg: 12px;
--qm-shadow-raised: 0 8px 24px rgba(20, 43, 58, 0.08);
--qm-shadow-overlay: 0 16px 40px rgba(20, 43, 58, 0.16);
```

- Inputs and buttons: 6px.
- Cards: 8px, normally border-only; shadow appears only for hoverable or elevated content.
- Dialogs and drawers: 12px.
- Status tags: pill radius is allowed because the compact silhouette communicates “stamp/status.”
- Do not stack shadows, colored shadows, or glow effects.

## 7. Component rules and states

### Buttons

- Height: 40px desktop, minimum 44px on touch layouts.
- Primary: one per action region; blue fill, white label.
- Secondary: white surface, neutral border, dark label.
- Text action: for low-priority navigation only.
- Destructive: red text/border until a confirmation dialog; the final confirmed action may use red fill.
- Loading: preserve width, disable repeat submission, show a spinner and action-specific copy.
- Focus: visible 3px focus ring with a 2px offset.

### Forms

- Every input has a persistent visible label; placeholder text is an example, never the label.
- Validation appears beside the relevant field and explains how to recover.
- Required fields are identified in text, not color alone.
- Enter submits simple authentication forms; Escape closes dialogs without losing data silently.
- Use the backend's existing validation boundaries. Do not invent password strength or username character rules.

### Cards and lists

- Venue cards lead with category, venue name, service availability, address, then supporting description.
- A card is clickable only when the full card navigates to one destination; nested actions must remain separate buttons.
- Booking and transaction lists show a prominent state, primary reference number, time, and amount before metadata.
- Empty states explain the next available action; errors state what failed and provide retry.

### Status stamps

| Semantic state | Examples | Treatment |
| --- | --- | --- |
| Neutral | closed, cancelled, void | gray surface + explicit label |
| Progress | waiting, called, booked | blue surface; queue numbers use data font |
| Success | active, fulfilled, redeemed | green surface |
| Attention | low capacity, no-show, expiring | amber surface |
| Failure | frozen, failed, invalid | red surface |

### Service ticket pattern

- Use for queue tickets, booking vouchers, and booking summaries only.
- Use a dashed divider to separate the code/number from details.
- Keep surfaces rectangular with an 8px radius; do not add fake paper tears or ornamental cut-outs.
- Consumption codes may be copied, but must not be written to console logs or analytics.

### Feedback

- Loading: skeleton for content lists, spinner for direct actions.
- Success: concise toast using the same verb as the action (“预约成功”, “已取消预约”).
- Error: show the backend message when it is safe and actionable; map network/auth failures to clear recovery steps.
- A `401` clears the expired local session and returns to login with the intended route preserved.
- A `403` explains that the current role cannot perform the action; it does not pretend the resource is missing.

## 8. Navigation

- Public routes: venue list, venue detail, slot availability, public queue progress.
- Authenticated USER routes: my bookings, wallet, my queue tickets.
- MERCHANT and ADMIN navigation must not expose USER-only payment or booking actions.
- Desktop uses a compact top service bar. Mobile uses a wrapping/scroll-safe navigation row initially; a bottom bar may be introduced only when there are five or fewer stable primary destinations.
- Route protection lives in Vue Router navigation guards, not repeated inside every page.

## 9. Motion and interaction

- Default transition duration: 160ms; overlays may use 220ms.
- Animate opacity and transform only. Do not animate width, height, or layout position.
- No automatic page-load choreography or scroll-triggered reveal.
- Hover elevation is limited to clickable cards on pointer devices.
- Respect `prefers-reduced-motion: reduce` by removing nonessential transitions.
- Use `touch-action: manipulation` for buttons and links.

## 10. Accessibility and QA

- Semantic landmarks: `header`, `nav`, `main`, and meaningful heading order.
- Keyboard navigation and visible focus are required for every action.
- Icon-only controls require accessible names and tooltips where meaning is not obvious.
- Minimum touch target is 44×44px with 8px between adjacent actions.
- Do not disable browser zoom.
- Verify loading, empty, error, success, disabled, hover, focus, and mobile states.
- Visual QA widths: 375, 768, 1024, and 1440px.
- Before delivery, run the production build and inspect public authentication, user records, merchant operations, and administrator tools.

## 11. Implementation mapping

Element Plus remains the component base, but its theme variables must map to the QueueMate tokens. Vue components should consume semantic CSS variables rather than hardcoded hex values. Axios provides a single response/error interceptor. Authentication state, role-aware home routing, and route guards are shared. The venue operations page groups slots, queue transitions, voucher redemption, and busy-hours data because they form one real operational workflow. Page-specific exceptions belong in `design-system/pages/<page-name>.md`; if no override exists, this file is authoritative.
