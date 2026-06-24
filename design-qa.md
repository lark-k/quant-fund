source visual truth path: C:\Users\lark-k\.codex\generated_images\019eefed-ca5c-77d1-a8d6-9a25ee98ddf2\ig_06697a6aa0fb0774016a3970b9d8fc819abc8f0be5710cb7ef.png
implementation screenshot path: D:\code\personal\quant-fund\quant-fund-web\qa-artifacts\visual-qa\desktop-dashboard.png
comparison evidence path: D:\code\personal\quant-fund\quant-fund-web\qa-artifacts\comparison-source-vs-current-dashboard.png
viewport: 1440 x 1024
state: authenticated QuantFund dashboard, selected Product Design option 2, dark trading terminal layout, Vite mock mode for visual QA only
full-view comparison evidence: source visual and the current desktop dashboard screenshot were normalized to 1440 x 1024 and combined side by side in `comparison-source-vs-current-dashboard.png`.
focused region comparison evidence: focused review covered the top KPI strip, Chinese sidebar navigation, holdings monitor, larger intraday estimate card, AI suggestion card, card-based strategy signals, chart/allocation row, and bottom simulated-trade notice. Separate crop files were not needed because the combined 2880 x 1064 comparison image keeps the dense dashboard regions readable.

**Findings**
- No actionable P0/P1/P2 findings remain.

- [P3] The implementation is denser and more product-complete than the source visual
  Location: dashboard middle and lower rows.
  Evidence: the source visual is a generated concept. The implementation keeps the same dark terminal language, but adds real workflow affordances: refresh, navigation, signal cards, no-empty-state guidance, and larger PC-first cards.
  Impact: acceptable because these additions directly address current product requirements and improve usability.
  Fix: none required.

**Required Fidelity Surfaces**
- Fonts and typography: compact Inter / DIN Alternate / Microsoft YaHei stack, zero letter spacing, readable KPI numbers, Chinese navigation labels, table text, tags, and small metadata. Playwright diagnostics found 0 truncation candidates across 33 screenshots.
- Spacing and layout rhythm: PC dashboard uses 4 larger core KPI cards, a 13/6/5 holdings-estimate-AI grid, card-based strategy signals, and no right-side blank block. Mobile and tablet screenshots no longer show page-level horizontal overflow.
- Colors and visual tokens: dark blue-black panels, blue active navigation, red/green China-market gain/loss semantics, amber disclaimers, thin borders, and restrained trading-terminal contrast remain aligned with option 2.
- Image quality and asset fidelity: source visual is UI-only; visible icons are from Element Plus. No fake image assets, decorative placeholder art, or handcrafted SVG assets were introduced.
- Copy and content: required notices remain present: `仅供参考，不构成投资建议，不承诺收益` and `仅为模拟操作，并非真实交易`. Side navigation and route titles are Chinese. The fund search entry explains real datasource search and supports fuzzy/exact modes. Holding edit no longer asks users to type intraday estimate NAV or official NAV.

**Verification**
- `mvn.cmd "-Dmaven.repo.local=.m2/repository" test`: passed, 48 tests.
- `npm.cmd run build`: passed in default real API mode.
- `VITE_USE_MOCK=true npm.cmd run build`: passed for visual QA preview only.
- `node tools\real-fund-holding-smoke.mjs`: passed against `http://127.0.0.1:8080`; exact search returned `161725 招商中证白酒指数(LOF)A`, fuzzy search for `白酒` returned 4 real candidates, and the result was added to holdings and read back.
- `QA_BASE_URL=http://127.0.0.1:5175 node scripts\visual-qa.mjs`: passed with 33 screenshots, 0 console errors, 0 truncation candidates, 0 routes with horizontal overflow.
- Direct EastMoney fund-search probes on 2026-06-24 returned HTTP 200 for `161725` and URL-encoded `白酒`.
- Secret scan for the provided DeepSeek key and database password fragments: no matches.

**Patches Made Since Previous QA Pass**
- Added real frontend flow for searching funds by name/code/pinyin and adding them to holdings.
- Added portfolio creation fallback for first-login users with no account.
- Wired frontend search to real backend `/api/funds/search` and `POST /api/holdings`.
- Added backend `FUZZY` and `EXACT` fund search modes on the EastMoney-backed datasource path.
- Fixed EastMoney search parsing for real response fields: `JP` pinyin, `FundBaseInfo.FTYPE`, and raw JSON bodies containing fund names with `(LOF)`.
- Reworked PC dashboard layout to prioritize core metrics, enlarge intraday estimate, remove right-side blank space, and prevent holdings/strategy content truncation.
- Converted visible navigation/menu labels and route page titles from English to Chinese.
- Updated datasource docs and README to state that fund data defaults to real datasource adapters and mock fallback is explicitly opt-in.
- Added a UTF-8 safe real-data smoke script for exact/fuzzy fund search, first-login portfolio creation, holding creation, and holding verification.
- Changed holding edit to accept either amount + profit or shares + cost, with NAV and estimate values resolved by the backend datasource.
- Added delete holding actions in holding list and holding edit.
- Added dashboard auto-refresh for intraday estimates and replaced the estimate chart with holding-level daily profit plus related-theme rate data.
- Added red-for-profit and green-for-loss handling across the updated dashboard, list, edit, and detail flows.
- Switched AI defaults to real DeepSeek mode with mock disabled unless explicitly enabled.

**Open Questions**
- None.

**Follow-up Polish**
- Consider code-splitting ECharts/Element Plus if bundle-size warnings become a performance target.

final result: passed
