#!/usr/bin/env node

const args = new Map()
for (const arg of process.argv.slice(2)) {
  const [key, ...rest] = arg.replace(/^--/, '').split('=')
  args.set(key, rest.join('='))
}

const baseUrl = (args.get('base-url') || process.env.QUANTFUND_SMOKE_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '')
const username = args.get('username') || process.env.QUANTFUND_SMOKE_USERNAME || `qf_smoke_${new Date().toISOString().replace(/\D/g, '').slice(0, 14)}`
const password = args.get('password') || process.env.QUANTFUND_SMOKE_PASSWORD || 'QuantFund2026'
const nickname = args.get('nickname') || process.env.QUANTFUND_SMOKE_NICKNAME || '真实基金联调用户'
const exactKeyword = args.get('exact-keyword') || process.env.QUANTFUND_SMOKE_EXACT_KEYWORD || '161725'
const fuzzyKeyword = args.get('fuzzy-keyword') || process.env.QUANTFUND_SMOKE_FUZZY_KEYWORD || '白酒'

function assertApiSuccess(response, step) {
  if (!response || response.code !== 0) {
    throw new Error(`${step} failed: code=${response?.code}, message=${response?.message}`)
  }
  return response.data
}

async function request(method, path, body, token) {
  const headers = {}
  if (token) headers[token.name] = token.value
  if (body !== undefined) headers['content-type'] = 'application/json; charset=utf-8'

  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body)
  })
  const text = await response.text()
  let payload
  try {
    payload = text ? JSON.parse(text) : null
  } catch {
    throw new Error(`${method} ${path} returned non-JSON status=${response.status}: ${text.slice(0, 240)}`)
  }
  if (!response.ok) {
    throw new Error(`${method} ${path} returned HTTP ${response.status}: ${text.slice(0, 240)}`)
  }
  return payload
}

function normalizeFundType(rawType, fundName = '') {
  const value = String(rawType || '').toUpperCase()
  const name = String(fundName || '')
  if (value.includes('ETF') || name.includes('ETF')) return name.includes('联接') ? 'ETF_LINK' : 'ETF'
  if (value.includes('INDEX') || rawType?.includes?.('指数') || name.includes('指数')) return name.includes('增强') ? 'INDEX_ENHANCED' : 'INDEX'
  if (value.includes('BOND') || rawType?.includes?.('债') || name.includes('债')) return 'BOND'
  if (value.includes('MONEY') || rawType?.includes?.('货币') || name.includes('货币')) return 'MONEY_MARKET'
  if (value.includes('QDII') || name.includes('QDII')) return 'QDII'
  if (value.includes('MIXED') || rawType?.includes?.('混合') || name.includes('混合')) return 'MIXED'
  if (value.includes('ACTIVE') || rawType?.includes?.('股票') || name.includes('股票')) return 'ACTIVE_EQUITY'
  return 'UNKNOWN'
}

async function main() {
  try {
    await fetch(`${baseUrl}/api/health`)
  } catch (error) {
    throw new Error(`Backend is not reachable at ${baseUrl}. Start quant-fund-server first, then rerun this script. Details: ${error.message}`)
  }

  let loginData
  try {
    const register = await request('POST', '/api/auth/register', {
      username,
      password,
      nickname,
      phone: null,
      email: null
    })
    loginData = assertApiSuccess(register, 'register')
  } catch (error) {
    console.log(`Register skipped or failed, trying login: ${error.message}`)
    const login = await request('POST', '/api/auth/login', { username, password })
    loginData = assertApiSuccess(login, 'login')
  }

  if (!loginData.tokenName || !loginData.tokenValue) {
    throw new Error('login/register did not return tokenName and tokenValue')
  }
  const token = { name: loginData.tokenName, value: loginData.tokenValue }

  const exactParams = new URLSearchParams({ keyword: exactKeyword, mode: 'EXACT' })
  const exactResults = assertApiSuccess(await request('GET', `/api/funds/search?${exactParams}`, undefined, token), 'exact fund search')
  if (!exactResults.length) throw new Error(`exact fund search returned no result for ${exactKeyword}`)

  const fuzzyParams = new URLSearchParams({ keyword: fuzzyKeyword, mode: 'FUZZY' })
  const fuzzyResults = assertApiSuccess(await request('GET', `/api/funds/search?${fuzzyParams}`, undefined, token), 'fuzzy fund search')
  if (!fuzzyResults.length) throw new Error(`fuzzy fund search returned no result for ${fuzzyKeyword}`)

  const fund = exactResults[0]
  let accounts = assertApiSuccess(await request('GET', '/api/portfolios', undefined, token), 'portfolio list')
  let account = accounts[0]
  if (!account) {
    account = assertApiSuccess(await request('POST', '/api/portfolios', {
      accountName: '真实基金联调账户',
      platformType: 'MANUAL',
      maxSingleFundPositionRate: 25
    }, token), 'portfolio create')
  }

  const holdingParams = new URLSearchParams({ fundCode: fund.fundCode })
  let existingHoldings = assertApiSuccess(await request('GET', `/api/holdings?${holdingParams}`, undefined, token), 'holding lookup')
  let holding = existingHoldings[0]

  if (!holding) {
    const fundType = normalizeFundType(fund.fundType, fund.fundName)
    holding = assertApiSuccess(await request('POST', '/api/holdings', {
      accountId: account.id,
      fundCode: fund.fundCode,
      fundName: fund.fundName,
      fundType,
      activeFund: fundType === 'ACTIVE_EQUITY' || fundType === 'MIXED',
      holdingAmount: 0,
      holdingShare: 0,
      holdingCost: 0,
      currentEstimateNav: 0,
      latestOfficialNav: 0,
      sourcePlatform: '真实联调手动添加',
      regularInvestment: false,
      coreHolding: false,
      watchFocus: true
    }, token), 'holding create')
  }

  const verifiedHoldings = assertApiSuccess(await request('GET', `/api/holdings?${holdingParams}`, undefined, token), 'holding verify')
  if (!verifiedHoldings.some((item) => item.fundCode === fund.fundCode)) {
    throw new Error(`holding verify failed: ${fund.fundCode} not found in holding list`)
  }

  console.log(JSON.stringify({
    status: 'passed',
    baseUrl,
    username,
    exactKeyword,
    exactResult: `${fund.fundCode} ${fund.fundName}`,
    fuzzyKeyword,
    fuzzyResultCount: fuzzyResults.length,
    accountId: account.id,
    holdingId: holding.id,
    disclaimer: '仅供参考，不构成投资建议，不承诺收益',
    simulatedTradeNotice: '仅为模拟操作，并非真实交易'
  }, null, 2))
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
