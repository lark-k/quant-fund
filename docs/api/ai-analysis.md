# QuantFund AI Analysis API

This document describes the DeepSeek-backed AI analysis module for `QuantFund`.

Important safety boundary:

- AI output is only a reference.
- The system does not place real orders.
- Every AI analysis response includes `disclaimer`: `仅供参考，不构成投资建议，不承诺收益`.
- Buy and sell suggestions must still be manually handled by the user on the original fund platform.
- If AI is disabled, missing API key, fails, or returns invalid JSON, the backend falls back to `WATCH`.

## DeepSeek Integration

The implementation follows the current DeepSeek OpenAI-compatible chat completions API:

- Base URL: `https://api.deepseek.com`
- Endpoint: `POST /chat/completions`
- Header: `Authorization: Bearer ${DEEPSEEK_API_KEY}`
- JSON mode: `response_format: {"type": "json_object"}`
- Streaming: disabled

Configuration keys:

- `quantfund.ai.enabled`
- `quantfund.ai.mock-enabled`
- `quantfund.ai.provider`
- `quantfund.ai.model`
- `quantfund.ai.base-url`
- `quantfund.ai.api-key`
- `quantfund.ai.timeout-ms`
- `quantfund.ai.max-tokens`
- `quantfund.ai.reasoning-enabled`
- `quantfund.ai.account-analysis-concurrency`

Environment variables:

- `DEEPSEEK_ENABLED`
- `DEEPSEEK_MOCK_ENABLED`
- `DEEPSEEK_API_KEY`
- `DEEPSEEK_MODEL`
- `DEEPSEEK_BASE_URL`
- `DEEPSEEK_TIMEOUT_MS`
- `DEEPSEEK_MAX_TOKENS`
- `DEEPSEEK_REASONING_ENABLED`
- `DEEPSEEK_ACCOUNT_ANALYSIS_CONCURRENCY`

Default local behavior enables the DeepSeek integration path and disables mock fallback. The backend can still start without a real DeepSeek key, but analysis calls will return a conservative `WATCH` fallback with `fallbackUsed=true` instead of mock content.

To enable real DeepSeek calls locally, copy `quant-fund-server/.env.example` to `quant-fund-server/.env` and set:

```properties
DEEPSEEK_ENABLED=true
DEEPSEEK_MOCK_ENABLED=false
DEEPSEEK_API_KEY=your_deepseek_api_key
```

Set `DEEPSEEK_MOCK_ENABLED=true` only for local demos that intentionally need mock AI content. Never commit `.env` or a real API key.

## Endpoints

- `POST /api/ai-analysis/holdings/{holdingId}`: generate one holding AI analysis.
- `POST /api/ai-analysis/accounts/{accountId}`: generate AI analysis for each holding in one account.
- `GET /api/ai-analysis/history`: query saved AI reports.
- `POST /api/ai-analysis/reports/{reportId}/regenerate`: regenerate a report from its holding.

History filters:

- `accountId`
- `holdingId`
- `fundCode`

## Structured AI Input

The backend computes and sends structured data to AI. It does not let AI freely invent source data.

The input includes:

- Fund code and name
- Fund type and active/passive flag
- Current estimated NAV
- Latest official NAV
- Holding amount, share, cost, profit, profit rate, daily profit, holding days
- Account total asset
- Account equity position rate
- Single fund position rate
- User risk level
- Recent strategy signals
- Latest deterministic quant action, score breakdown, metrics, reasons and risks when available

For account analysis, the backend limits concurrent AI calls with
`DEEPSEEK_ACCOUNT_ANALYSIS_CONCURRENCY` (default `5`) so one large account does
not create an unbounded request burst.

## Required AI JSON Shape

AI must return:

```json
{
  "action": "BUY | SELL | HOLD | CONVERT | WATCH",
  "actionText": "建议加仓 / 建议减仓 / 建议持有 / 建议转换 / 建议观察",
  "suggestAmount": 0,
  "suggestRatio": 0,
  "confidence": 0.0,
  "riskLevel": "LOW | MEDIUM | HIGH",
  "deadline": "15:00前",
  "strategy": "策略名称",
  "reasons": [],
  "risks": [],
  "dataSummary": "",
  "finalConclusion": ""
}
```

Validation rules:

- Invalid JSON falls back to `WATCH`.
- Missing required fields fall back to `WATCH`.
- Invalid `action` or `riskLevel` falls back to `WATCH`.
- `BUY` and `SELL` must include either `suggestAmount` or `suggestRatio`; otherwise fallback to `WATCH`.

## Persistence

Each analysis is saved to `ai_analysis_report`, including:

- Structured request payload
- Raw AI response payload
- Parsed action fields
- Reasons and risks JSON
- Whether fallback was used
