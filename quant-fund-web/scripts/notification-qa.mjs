import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'

// Isolated browser context; every API request is intercepted with test fixtures.
const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5173'
const outputDir = path.resolve('qa-artifacts/notifications')
await mkdir(outputDir, { recursive: true })
const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
const names = ['易方达蓝筹精选混合', '华夏沪深300ETF联接A', '招商中证白酒指数A']
let signals = Array.from({ length: 35 }, (_, i) => ({
  id: i + 1, fundCode: String(5827 + i).padStart(6, '0'), fundName: names[i % names.length],
  action: i % 2 ? 'BUY' : 'SELL', actionText: i % 2 ? '建议小额加仓' : '建议轻度减仓',
  reasons: [i % 2 ? '趋势信号改善，结合当前仓位分批增加配置。' : '回撤接近预警阈值，建议降低单基金波动贡献。'],
  signalTime: `${today}T00:${String(i + 1).padStart(2, '0')}:00`
}))
let fail = false
const browser = await chromium.launch({ headless: true, executablePath: 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe' })
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
await context.addInitScript(() => {
  localStorage.setItem('auth', JSON.stringify({ token: 'notification-qa-only', tokenName: 'Authorization', user: {
    id: 999999, username: 'notification-qa', nickname: 'lark-k', role: 'USER', riskLevel: 'MEDIUM'
  } }))
})
await context.route('**/api/**', async (route) => {
  const url = new URL(route.request().url())
  if (url.pathname === '/api/quant/signals' && fail) return route.fulfill({ status: 503, body: 'test offline' })
  const data = url.pathname === '/api/quant/signals' ? signals
    : url.pathname === '/api/dashboard/market-status' ? { trading: false, primaryStatusText: '已收盘', markets: [] }
      : []
  await route.fulfill({ json: { code: 0, data } })
})
const page = await context.newPage()
const errors = []
page.on('pageerror', (error) => errors.push(error.message))
const panel = page.locator('#notification-panel')
const bell = page.locator('.notification-bell')
const results = []
async function check(name, callback) { await callback(); results.push(name) }
async function waitCount(count) { await page.waitForFunction((count) => Number(document.querySelector('.notification-count')?.textContent || 0) === count, count) }
async function open() {
  await bell.click()
  await panel.waitFor({ state: 'visible' })
  await page.waitForFunction(() => getComputedStyle(document.querySelector('.notification-popover')).opacity === '1')
}

try {
  await page.goto(`${baseUrl}/profile`, { waitUntil: 'networkidle' })
  await check('30 条上限与红色未读计数', async () => {
    await waitCount(30)
    await open()
    assert.equal(await panel.locator('.notification-item').count(), 30)
    assert.equal(await panel.locator('time').first().textContent(), `${today.replaceAll('-', '/')} 00:35:00`)
    assert.equal(await panel.locator('time').last().textContent(), `${today.replaceAll('-', '/')} 00:06:00`)
  })
  await check('展开不自动已读，点击单条更新计数', async () => {
    await waitCount(30)
    await panel.locator('.notification-item').first().click()
    await waitCount(29)
    assert.equal(await panel.locator('.notification-item').first().locator('.notification-read').count(), 1)
  })
  await page.screenshot({ path: path.join(outputDir, 'desktop.png'), animations: 'disabled' })
  await check('扫帚悬停提示与一键已读', async () => {
    await page.getByRole('button', { name: '全部标为已读', exact: true }).hover()
    await page.getByRole('tooltip', { name: '一键全部标为已读', exact: true }).waitFor({ state: 'visible' })
    await page.waitForFunction(() => [...document.querySelectorAll('[role="tooltip"]')].some((node) => node.textContent === '一键全部标为已读' && getComputedStyle(node).opacity === '1'))
    await page.screenshot({ path: path.join(outputDir, 'broom-tooltip.png'), animations: 'disabled' })
    await page.getByRole('button', { name: '全部标为已读', exact: true }).click()
    await waitCount(0)
    assert.equal(await panel.locator('.notification-item').count(), 30)
  })
  await check('滚轮查看历史且背景不随之滚动', async () => {
    const list = panel.locator('.notification-list')
    const bodyScroll = await page.evaluate(() => window.scrollY)
    await list.hover()
    await page.mouse.wheel(0, 1300)
    await page.waitForFunction(() => document.querySelector('.notification-list').scrollTop > 100)
    assert.equal(await page.evaluate(() => window.scrollY), bodyScroll)
  })
  await check('Escape 关闭并返回焦点、刷新保留已读', async () => {
    await page.keyboard.press('Escape')
    await panel.waitFor({ state: 'hidden' })
    assert.equal(await bell.evaluate((node) => node === document.activeElement), true)
    await page.reload({ waitUntil: 'networkidle' })
    await waitCount(0)
    await open()
    assert.equal(await panel.locator('.notification-item').count(), 30)
    assert.equal(await panel.locator('.notification-item.is-unread').count(), 0)
  })
  await check('点击外部关闭', async () => {
    await page.locator('.terminal-header h1').click()
    await panel.waitFor({ state: 'hidden' })
  })
  await check('新消息进入且最早历史淘汰', async () => {
    signals = [{ ...signals[0], id: 36, signalTime: `${today}T00:36:00` }]
    await open()
    await waitCount(1)
    assert.equal(await panel.locator('.notification-item').count(), 30)
    assert.equal(await panel.locator('time').last().textContent(), `${today.replaceAll('-', '/')} 00:07:00`)
  })
  await check('移动端气泡不超出视口并可滚动', async () => {
    await page.setViewportSize({ width: 390, height: 844 })
    await page.keyboard.press('Escape')
    await page.evaluate(() => window.scrollTo(0, 0))
    await open()
    const bounds = await panel.boundingBox()
    assert.ok(bounds.x >= 0 && bounds.x + bounds.width <= 390)
    assert.ok(bounds.y >= 0 && bounds.y + bounds.height <= 844)
    await page.screenshot({ path: path.join(outputDir, 'mobile.png'), animations: 'disabled' })
  })
  await check('同步失败保留历史，允许重试', async () => {
    fail = true
    await page.keyboard.press('Escape')
    await open()
    await panel.locator('.notification-error').waitFor({ state: 'visible' })
    assert.equal(await panel.locator('.notification-item').count(), 30)
    fail = false
    await panel.locator('.notification-error').click()
    await panel.locator('.notification-error').waitFor({ state: 'hidden' })
  })
  await check('无建议时不显示徽标并呈现空态', async () => {
    signals = []
    await page.evaluate(() => localStorage.removeItem('quantfund:notifications:v1:999999'))
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.reload({ waitUntil: 'networkidle' })
    await open()
    await page.getByText('暂无买卖建议通知', { exact: true }).waitFor({ state: 'visible' })
    await waitCount(0)
    await page.screenshot({ path: path.join(outputDir, 'empty.png'), animations: 'disabled' })
  })
  assert.deepEqual(errors, [])
  console.log(JSON.stringify({ results, pageErrors: errors, outputDir }, null, 2))
  await writeFile(path.join(outputDir, 'results.json'), JSON.stringify({ results, pageErrors: errors }, null, 2))
} finally {
  await browser.close()
}
