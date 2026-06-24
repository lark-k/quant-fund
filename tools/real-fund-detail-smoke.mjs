#!/usr/bin/env node

const baseUrl = (process.env.QUANTFUND_SMOKE_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '')
const username = `qf_detail_${new Date().toISOString().replace(/\D/g, '').slice(0, 14)}`
const password = 'QuantFund2026'

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
  const payload = text ? JSON.parse(text) : null
  if (!response.ok || payload?.code !== 0) {
    throw new Error(`${method} ${path} failed: status=${response.status}, body=${text.slice(0, 300)}`)
  }
  return payload.data
}

async function main() {
  const login = await request('POST', '/api/auth/register', {
    username,
    password,
    nickname: '真实详情冒烟用户',
    phone: null,
    email: null
  })
  const token = { name: login.tokenName, value: login.tokenValue }
  const code = '012922'
  const [info, estimate, stocks, themes, rank] = await Promise.all([
    request('GET', `/api/funds/${code}`, undefined, token),
    request('GET', `/api/funds/${code}/estimate`, undefined, token),
    request('GET', `/api/funds/${code}/heavy-stocks`, undefined, token),
    request('GET', `/api/funds/${code}/themes`, undefined, token),
    request('GET', `/api/funds/${code}/peer-rank`, undefined, token).catch(() => null)
  ])
  if (!info.fundName || !String(info.fundName).includes('易方达全球成长')) {
    throw new Error(`basic info did not return expected fund: ${JSON.stringify(info)}`)
  }
  if (!info.managerName) {
    throw new Error(`managerName missing: ${JSON.stringify(info)}`)
  }
  if (!stocks.length || !stocks[0].stockName || stocks[0].positionRate === undefined) {
    throw new Error(`heavy stocks missing real fields: ${JSON.stringify(stocks.slice(0, 2))}`)
  }
  if (!themes.length || themes[0].estimatedRate === undefined) {
    throw new Error(`themes missing estimatedRate: ${JSON.stringify(themes)}`)
  }
  console.log(JSON.stringify({
    status: 'passed',
    code,
    fundName: info.fundName,
    managerName: info.managerName,
    estimateGrowthRate: estimate.estimateGrowthRate,
    topStock: `${stocks[0].stockCode} ${stocks[0].stockName}`,
    topStockRate: stocks[0].changeRate,
    primaryTheme: themes[0].themeName,
    primaryThemeRate: themes[0].estimatedRate,
    peerRank: rank?.rankText || null
  }, null, 2))
}

main().catch((error) => {
  console.error(error.message)
  process.exit(1)
})
