# TV design guidelines audit (0.5.1)

Checked on the real TV (1280×720, density 1.5, so an **853×480 dp** canvas) against Google's Android TV
design guides and Material 3's type scale, plus WCAG text contrast.

Google specifies TV layouts on a **960×540 dp** canvas. To keep the same physical proportions here,
guideline values are scaled by **853 / 960 ≈ 0.89**.

| Guideline | Source | Target on this TV | Before | After |
|---|---|---|---|---|
| Keep important content inside ~5% margins | [TV layouts](https://developer.android.com/design/ui/tv/guides/styles/layouts): 48–58 dp sides, 24–28 dp top/bottom | ≥ 43 dp sides, ≥ 24 dp top/bottom | 32 dp sides; ticker text 8 dp from bottom | 44 dp sides, 24 dp top; ticker text ~33 dp from bottom |
| Backgrounds bleed to the edge | TV layouts | Full-bleed backgrounds | ✓ | ✓ (ticker band and gradients are full width) |
| Smallest text: Label Small 11 sp | [TV typography](https://developer.android.com/design/ui/tv/guides/styles/typography) → Material 3 type scale | ≥ 9.8 sp | 19 labels at 8–9 sp; countdown units at 7 sp | No text under 10 sp; countdown units 9.5 sp |
| Text contrast ≥ 4.5:1 | WCAG 2.x | Muted text ≥ 60% white on cards | 50% white on cards ≈ 3.6:1 | 65% white ≈ 5:1 |
| Text over images stays legible | TV layouts / WCAG | Solid or dark backing | Ambient panel 45% black; caption on bare photo | Panel 75% black; caption on its own backing; dashboard ticker on a solid band |
| Focus scale 1.025–1.1, by size, leaving room to grow | [TV focus system](https://developer.android.com/design/ui/tv/guides/styles/focus-system) | Wide cards smaller scale | Every card 1.08× (wide cards overlapped neighbours) | Cards wider than 200 dp 1.04×, tiles 1.08×, plus glow/elevation and outline |
| Up/down, left/right predictable; long-press for more options | [Navigation on TV](https://developer.android.com/design/ui/tv/guides/foundations/navigation-on-tv) | Rows vertical, items horizontal | ✓ | ✓ (hold OK: dismiss notification / open the playing app) |
| Scrolling lists reachable with the D-pad | Navigation on TV | Every list scrolls | F1 standings below 14th unreachable | F1 lists focusable and scrollable (0.4.1) |
| Back returns to the previous screen; no exit gating | Navigation on TV | ✓ | ✓ | ✓ (detail screens close on Back; the home screen is the root) |
| Colour isn't the only signal | WCAG 1.4.1 | Up/down also shown as shape | ✓ ▲ ▼ next to green/red | ✓ |
| Avoid decorative fonts; Roboto throughout | TV typography | Roboto | ✓ | ✓ |

## Still open

- **Overscan**: modern TVs don't overscan, and this one doesn't. The 5% margins are kept anyway,
  for friends' older sets.
- **Card grid**: Google's 12-column grid (52 dp columns, 20 dp gutters) isn't followed exactly. Rows use
  fixed card widths that fit 5 tiles per screen.
- **Colour guide**: the Android TV colour page returned 404 when this was written, so contrast
  follows WCAG.
