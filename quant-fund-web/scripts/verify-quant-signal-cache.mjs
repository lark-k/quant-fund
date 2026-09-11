import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { build } from 'esbuild'
import { chromium } from 'playwright'

// Intercept APIs in this isolated browser only; never generate real signals or
// modify the review account while exercising loading and error states.
const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5174'
const outputDir = path.resolve('qa-artifacts/quant-signal-cache')
await mkdir(outputDir, { recursive: true })
const fixtures = await build({
  entryPoints: ['src/api/mock.ts'], bundle: true, write: false,
  platform: 'node', format: 'esm', tsconfig: 'tsconfig.json'
})
const { dashboard, quantSignals } = await import(
  `data:text/javascript;base64,${Buffer.from(fixtures.outputFiles[0].text).toString('base64')}`
)
const browser = await chromium.launch({
  headless: true,
  executablePath: process.env.QA_BROWSER_PATH || 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
})
const page = await browser.newPage({ viewport: { width: 1600, height: 1100 } })
const pageErrors = []
page.on('pageerror', (error) => pageErrors.push(error.message))
const pending = []
let signalRequests = 0
const respond = (route, data, status = 200) => route.fulfill({
  status, contentType: 'application/json', body: JSON.stringify({ code: 0, data })
})
await page.addInitScript(() => localStorage.setItem('auth', JSON.stringify({
  token: 'isolated-qa-session', tokenName: 'Authorization',
  user: { id: 1, username: 'qa', nickname: '回归验证', role: 'USER' }
})))
await page.route('**/api/**', async (route) => {
  const pathname = new URL(route.request().url()).pathname
  if (pathname === '/api/quant/signals') {
    signalRequests += 1
    pending.push(route)
    return
  }
  if (pathname === '/api/dashboard/overview') return respond(route, dashboard)
  if (pathname === '/api/dashboard/market-status') {
    return respond(route, { trading: false, primaryStatusText: '已收盘', markets: [] })
  }
  return respond(route, [])
})
const panel = page.locator('.strategy-panel').filter({ hasText: '今日量化建议' })
const rows = panel.locator('tbody tr')
async function returnToDashboard() {
  await page.locator('.header-user a[href="/profile"]').click()
  await page.waitForURL('**/profile')
  await page.locator('.side-nav a[href="/dashboard"]').click()
  await page.waitForURL('**/dashboard')
}
try {
  await page.goto(`${baseUrl}/dashboard`)
  await panel.getByText('正在加载量化建议', { exact: true }).waitFor()
  const progress = panel.getByRole('progressbar')
  assert.equal(await progress.getAttribute('aria-valuenow'), null)
  await panel.getByText('已等待 00:01', { exact: true }).waitFor()
  await panel.screenshot({ path: path.join(outputDir, 'initial-progress-desktop.png') })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.waitForFunction(() => document.querySelector('.terminal-sidebar').getBoundingClientRect().right <= 0)
  await panel.screenshot({ path: path.join(outputDir, 'initial-progress-mobile.png') })
  assert.equal(await panel.locator('.quant-progress').evaluate((element) => element.scrollWidth > element.clientWidth), false)
  await page.emulateMedia({ reducedMotion: 'reduce' })
  assert.equal(await panel.locator('.quant-progress-beam').evaluate((element) => getComputedStyle(element).animationName), 'none')
  await page.emulateMedia({ reducedMotion: 'no-preference' })
  await page.setViewportSize({ width: 1600, height: 1100 })
  await panel.getByText('本次响应较慢，仍在加载中，请稍候…', { exact: true }).waitFor()
  assert.equal(await panel.getByText('暂无量化建议', { exact: true }).count(), 0)
  assert.deepEqual(await panel.locator('.insight-stat-row strong').allTextContents(), ['--', '--', '--'])
  assert.equal(signalRequests, 1)
  await respond(pending.shift(), quantSignals)
  await rows.first().waitFor()
  await panel.locator('.quant-progress.is-complete').waitFor()
  assert.equal(await progress.getAttribute('aria-valuenow'), '100')
  await panel.locator('.quant-progress').waitFor({ state: 'hidden' })
  const originalRows = await rows.allTextContents()

  const backgroundRequest = page.waitForRequest('**/api/quant/signals')
  await returnToDashboard()
  await backgroundRequest
  await panel.getByRole('button', { name: '生成', exact: true }).waitFor()
  assert.equal(await progress.count(), 0)
  assert.deepEqual(await rows.allTextContents(), originalRows)
  assert.equal(await panel.getByText('暂无量化建议', { exact: true }).count(), 0)
  await panel.screenshot({ path: path.join(outputDir, 'cached-during-refresh.png') })
  await returnToDashboard()
  await panel.getByRole('button', { name: '生成', exact: true }).waitFor()
  assert.equal(await progress.count(), 0)
  assert.deepEqual(await rows.allTextContents(), originalRows)
  // Returning again while the previous request is pending reuses that request.
  assert.equal(signalRequests, 2)
  const changed = quantSignals.map((signal) => ({ ...signal, totalScore: 88.8 }))
  await respond(pending.shift(), changed)
  await rows.first().getByText('88.8', { exact: true }).waitFor()
  assert.equal(await progress.count(), 0)
  assert.ok((await rows.first().innerText()).includes('88.8'))

  const failedRequest = page.waitForRequest('**/api/quant/signals')
  await returnToDashboard()
  await failedRequest
  await panel.getByRole('button', { name: '生成', exact: true }).waitFor()
  assert.equal(await progress.count(), 0)
  await respond(pending.shift(), null, 503)
  await panel.getByText('刷新失败，当前展示上次加载结果。', { exact: false }).waitFor()
  assert.equal(await progress.count(), 0)
  assert.ok((await rows.first().innerText()).includes('88.8'))
  await panel.screenshot({ path: path.join(outputDir, 'retained-after-error.png') })
  const retried = page.waitForRequest('**/api/quant/signals')
  await panel.getByRole('button', { name: '重试', exact: true }).click()
  await retried
  await respond(pending.shift(), [])
  await panel.getByText('暂无量化建议', { exact: true }).waitFor()
  assert.deepEqual(await panel.locator('.insight-stat-row strong').allTextContents(), ['0', '0', '0'])
  assert.equal(await panel.getByRole('button', { name: '重试', exact: true }).count(), 0)
  assert.deepEqual(pageErrors, [])
  const result = {
    baseUrl, passed: true, signalRequests, pageErrors,
    scenarios: ['initial progress', 'mobile layout', 'reduced motion', 'slow response hint', 'completion state', 'silent cached route return', 'in-flight deduplication', 'silent background update', 'failed refresh retains rows', 'retry with empty result']
  }
  await writeFile(path.join(outputDir, 'result.json'), JSON.stringify(result, null, 2))
  console.log(JSON.stringify(result, null, 2))
} finally {
  await browser.close()
}
