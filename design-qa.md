# Dashboard AI suggestion card — targeted design QA

- Source visual truth: `C:\Users\lark-k\AppData\Local\Temp\codex-clipboard-21f10e58-2796-4b8e-b36d-fcc950963a96.png`
- Implementation screenshot: `D:\code\personal\quant-fund\qa-artifacts\ai-panel-expanded-list-scrolled.png`
- Combined comparison: `D:\code\personal\quant-fund\qa-artifacts\ai-panel-gap-comparison.png`
- Source pixels: 1131 × 743.
- Implementation pixels: 1264 × 710; CSS viewport 1280 × 720; browser capture used its native in-app scale. Geometry was evaluated in CSS pixels, so no density normalization was needed for the alignment check.
- State: authenticated dashboard in local mock-data visual-QA mode; dark desktop layout. The reference has seven AI rows while the mock implementation has two, so the comparison is scoped to the scroll-region geometry and bottom disclaimer alignment.

**Findings**

- No actionable P0/P1/P2 findings remain for the reported gap.
- The AI table wrapper now consumes the full flexible row above the disclaimer: `max-height: none`, `overflow-y: auto`, measured height 532.1 px.
- The table wrapper ends 10 px above the disclaimer, matching the panel gap token. The disclaimer remains 8 px above the card bottom and aligned to the left content edge.

**Required fidelity surfaces**

- Fonts and typography: unchanged from the existing dashboard.
- Spacing and layout rhythm: the former 238 px cap was removed only for the AI suggestion list; the strategy panel keeps its cap.
- Colors and visual tokens: unchanged.
- Image quality and assets: no image or icon changes.
- Copy and content: disclaimer text is unchanged.

**Verification**

- `npm run build`: passed.
- Browser-rendered layout: passed; list region fills the available row and the disclaimer stays at the lower-left edge.
- Primary interaction: the list remains an independent `overflow-y: auto` scroll region.
- Browser console: 0 errors and 0 warnings in a fresh mock-data QA tab.
- Refreshed production web container: healthy; `http://127.0.0.1:5173/` returned HTTP 200.

**Comparison history**

- Earlier finding [P2]: AI list retained a 238 px maximum height, leaving a large empty band before the bottom disclaimer.
- Fix: removed the AI-only maximum height and made its table wrapper fill the `minmax(0, 1fr)` grid row.
- Post-fix evidence: wrapper bottom 632.8 px, disclaimer top 642.8 px, 10 px gap; card bottom 670.8 px.

**Follow-up polish**

- None for this targeted change.

final result: passed
