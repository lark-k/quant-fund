// Playwright CLI run-code --filename; isolated VITE_USE_MOCK preview and Python on 8095.
async (page) => {
  const check = (ok, message) => { if (!ok) throw new Error(message) }
  const errors = [], writes = []
  page.on('pageerror', e => errors.push(e.message))
  page.on('request', r => { if (!['GET', 'HEAD', 'OPTIONS'].includes(r.method())) writes.push(r.url()) })
  const dates = []; const day = new Date('2026-09-29T00:00:00Z')
  while (dates.length < 400) { if (day.getUTCDay() % 6 !== 0) dates.unshift(day.toISOString().slice(0, 10)); day.setUTCDate(day.getUTCDate() - 1) }
  const book = { cashBalance: 1000, holdingShares: 100, holdingAmount: 200, pendingTrades: 0, lastTradeDate: null, snapshotVersion: 1, snapshotAt: '2026-10-02T12:00:00' }
  async function fixture(bear, changes = {}) {
    const navs = dates.map((_, i) => Number((bear ? 3 - i * .004 : 1 + i * .005).toFixed(4)))
    const rows = dates.map((date, i) => ({ date, nav: navs[i], dailyGrowthRate: i ? (navs[i]/navs[i-1]-1)*100 : 0, sourceName: 'QA_SYNTHETIC' }))
    const response = await page.request.post('http://127.0.0.1:8095/api/v1/nav-technical/analyze', { data: { rows, fundType: 'MIXED', evaluatedAt: '2026-10-02T12:00:00', trading: false, execution: { ...book, ...changes } } })
    check(response.ok(), 'Python fixture failed')
    return response.json()
  }
  const fixtures = { buy: await fixture(false), sell: await fixture(true, { cashBalance: 0 }), pending: await fixture(false, { pendingTrades: 1 }), wait: await fixture(true, { lastTradeDate: '2026-09-28' }) }
  await page.setViewportSize({ width: 1440, height: 1080 })
  await page.evaluate(async data => {
    const { USE_MOCK } = await import('/src/api/http.ts')
    if (!USE_MOCK) throw new Error('QA requires mock preview')
    const { navTechnicalApi } = await import('/src/api/navTechnical.ts')
    const { quantApi } = await import('/src/api/quant.ts')
    const { fundCashApi } = await import('/src/api/fundCash.ts')
    const { http } = await import('/src/api/http.ts')
    window.executionQa = { fixtures: data, mode: 'buy', calls: 0, mutations: 0, before: JSON.stringify(await quantApi.holdings()) }
    const rejectWrite = async () => { window.executionQa.mutations++; throw new Error('Unexpected mutation') }
    fundCashApi.save = rejectWrite
    http.post = http.put = http.patch = http.delete = rejectWrite
    navTechnicalApi.analyze = async () => { window.executionQa.calls++; return structuredClone(window.executionQa.fixtures[window.executionQa.mode]) }
  }, fixtures)
  await page.getByRole('button', { name: '华泰柏瑞沪深300ETF', exact: true }).click()
  await page.getByRole('button', { name: '今日交易推荐', exact: true }).click()
  const modal = page.getByRole('dialog', { name: '今日交易推荐', exact: true })
  const input = modal.getByRole('spinbutton', { name: '调整执行金额' })
  await input.waitFor()
  check(await input.inputValue() === '500.00', 'Initial buy budget incorrect')
  await input.fill('1500'); await input.blur()
  await modal.getByRole('alert').filter({ hasText: '超出该基金现金' }).waitFor()
  await modal.getByRole('button', { name: '恢复建议金额' }).click()
  check(await input.inputValue() === '500.00', 'Restore failed')
  await page.waitForFunction(() => [...document.querySelectorAll('.fund-technical-dialog')].some(el => getComputedStyle(el).opacity === '1'))
  await page.screenshot({ path: 'output/playwright/fund-execution-buy.png', animations: 'disabled' })
  await page.evaluate(() => { window.executionQa.mode = 'sell' })
  await modal.getByRole('button', { name: '重新分析', exact: true }).click()
  await modal.getByRole('button', { name: '预览全部清仓' }).click()
  await modal.getByText('已选择全部已确认份额清仓。', { exact: false }).waitFor()
  check((await modal.locator('.execution-preview').textContent()).includes('¥ 0.00'), 'Clear preview did not reach zero')
  await input.fill('1000'); await input.blur()
  await modal.getByRole('alert').filter({ hasText: '减仓不能超过当前持仓' }).waitFor()
  await modal.getByRole('button', { name: '恢复建议金额' }).click()
  check(await input.inputValue() === '70.20', 'Sell restore failed')
  await page.screenshot({ path: 'output/playwright/fund-execution-sell.png', animations: 'disabled' })
  await page.setViewportSize({ width: 390, height: 844 })
  await modal.locator('.execution-plan').scrollIntoViewIfNeeded()
  check(await modal.evaluate(el => el.scrollWidth <= el.clientWidth + 2), 'Mobile dialog overflow')
  await page.screenshot({ path: 'output/playwright/fund-execution-mobile.png', animations: 'disabled' })
  await page.evaluate(() => { window.executionQa.mode = 'pending' })
  await modal.getByRole('button', { name: '重新分析', exact: true }).click()
  await modal.locator('.execution-wait').waitFor()
  check(await input.count() === 0, 'Pending trade still allows amount input')
  await page.evaluate(() => { window.executionQa.mode = 'wait' })
  await modal.getByRole('button', { name: '重新分析', exact: true }).click()
  await modal.locator('.execution-wait').getByText('1 / 3', { exact: false }).waitFor()
  await page.setViewportSize({ width: 1440, height: 1080 })
  await modal.locator('.technical-result').scrollIntoViewIfNeeded()
  await page.screenshot({ path: 'output/playwright/fund-execution-wait.png', animations: 'disabled' })
  await modal.getByRole('button', { name: '返回图表', exact: true }).click()
  await page.evaluate(() => { window.executionQa.mode = 'buy' })
  await page.getByRole('button', { name: '今日交易推荐', exact: true }).click()
  await input.waitFor()
  check(await input.inputValue() === '500.00', 'Reopen did not reset old draft')
  check(await page.evaluate(async () => {
    const { quantApi } = await import('/src/api/quant.ts')
    return window.executionQa.before === JSON.stringify(await quantApi.holdings()) && window.executionQa.mutations === 0
  }), 'Preview mutated holdings or attempted a write')
  check(writes.length === 0, `Unexpected network mutation: ${writes.join(',')}`)
  check(errors.length === 0, `Browser errors: ${errors.join(',')}`)
  console.log('PASS: actual Python sizing, buy/over-budget, sell/full-clear, pending, reset, mobile, no cash or trade mutation')
}
