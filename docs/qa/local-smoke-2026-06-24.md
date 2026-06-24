# QuantFund Local Smoke Test - 2026-06-24

## Scope

This smoke test verifies that the current QuantFund backend and frontend can run locally against the real API path, with mock fallback still available for external fund/AI dependencies.

## Environment

- Backend: `http://127.0.0.1:8080`
- Frontend dev server: `http://127.0.0.1:5173`
- Frontend API mode: `VITE_USE_MOCK=false`
- Database: local MySQL `quant_fund`
- Redis: local Redis
- Demo user: `quantdemo`

No real trading API, real order placement, real API key, or database password was persisted to source files.

## Checks

| Check | Result | Evidence |
| --- | --- | --- |
| Backend boot | Passed | Spring Boot started on port `8080`. |
| Frontend dev server boot | Passed | Vite served `http://127.0.0.1:5173/`. |
| SPA entry | Passed | `/` returned `200` and contained `id="app"`. |
| Route fallback | Passed | `/dashboard` returned `200` and contained `id="app"`. |
| Frontend proxy login | Passed | `POST /api/auth/login` through port `5173` returned `code=0`. |
| Dashboard API | Passed | Dashboard summary returned `holdingCount=3`. |
| Holding list API | Passed | Holding list returned 3 records. |
| Trade list API | Passed | Trade list returned 3 simulated trade records. |
| AI history API | Passed | AI history returned 1 existing report before regeneration. |
| Data source API | Passed | Data source list returned 2 records. |
| Operation log API | Passed | Operation log pagination returned records for the demo user. |
| API call log API | Passed | API call log count increased after fund estimate refresh. |
| Profile update API | Passed | `PUT /api/auth/profile` returned `code=0`. |
| Risk profile update API | Passed | `PUT /api/strategies/risk-profile` returned `code=0`. |
| Data source update API | Passed | `PUT /api/system/data-sources/{id}` returned `code=0`. |
| Fund estimate refresh | Passed with fallback | Estimate refresh returned `sourceName=MOCK` after external source failure. |
| AI analysis generation | Passed | `POST /api/ai-analysis/holdings/{holdingId}` returned `code=0`. |

## Notes

- External EastMoney estimate refresh failed on MIME parsing and correctly fell back to mock estimate data.
- API call logs captured the external source failure, matching the fallback/audit requirement.
- A manual PowerShell request using non-UTF-8 encoded JSON caused a risk profile update error. Retesting with UTF-8/ASCII JSON passed. The Vue frontend uses Axios JSON requests and is not affected by that shell encoding issue.
- Browser automation was not used because an in-app browser control tool was not exposed in this session and Product Design instructions require avoiding direct Playwright CLI use without approval.

## Remaining QA

- Perform visual browser walkthrough when an approved browser control path is available.
- Run a fresh database seed before release-style validation to make test counts deterministic.
- Consider frontend chunk splitting to address Vite large chunk warnings.
