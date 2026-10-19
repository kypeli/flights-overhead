# Flights Overhead — presentation

Self-contained HTML slide deck. Open `index.html` in any browser; no build step, no server.

```bash
open presentation/index.html
```

## Navigation

| Key | Action |
|---|---|
| `→` / `Space` / `PageDown` / `n` | Next slide |
| `←` / `PageUp` / `p` | Previous slide |
| `Home` / `End` | First / last slide |
| Click left or right half | Previous / next |
| Swipe | Previous / next (touch) |

The URL hash tracks the slide number, so `index.html#27` jumps straight to a slide — handy for rehearsing one section.

Follows the viewer's light/dark theme. Long slides scroll inside the content area.

## Structure — 48 slides

| Slides | Section |
|---|---|
| 1–4 | Overview: what ADS-B is, the full architecture graph, the three codebases |
| 5–24 | **Part one** — from transponder to data stream (Go backend + cloud boundary) |
| 25–34 | **Part two** — the Android client |
| 35–41 | **Part three** — Metro DI |
| 42–44 | Metro vs. Dagger 2, side by side in code |
| 45–46 | Summary table and trade-offs |
| 47–48 | Takeaways, Q&A |

Slides 36 and 42–44 are two-column code comparisons: Dagger 2 on the left (muted heading), Metro on the right (accent heading). The Dagger snippets are illustrative, not compiled.

Contains no screenshots by design — the running system is demonstrated live.

## Editing

Every slide is one `<section class="slide" data-part="...">`. The `data-part` attribute is what the footer shows. Code blocks are hand-highlighted with `.tok-k` (keyword), `.tok-s` (string), `.tok-c` (comment) and `.tok-a` (literal/annotation) spans — there is no highlighting library to keep in sync.

The architecture graph on slide 3 is hand-authored inline SVG (`svg.arch`) on a 1070&times;500 viewBox — no diagramming library, no image file. Node and edge styling lives in the page stylesheet under `.arch`, so it follows the light/dark theme.
