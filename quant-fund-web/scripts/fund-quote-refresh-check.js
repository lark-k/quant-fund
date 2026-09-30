// Playwright CLI run-code fixture. Run against an authenticated VITE_USE_MOCK=true
// dev dashboard only: playwright-cli run-code --filename=scripts/fund-quote-refresh-check.js
async (page) => {
  const assert = (ok, message) => { if (!ok) throw new Error(message) }
  const errors = []
  page.on('pageerror', e => errors.push(e.message))
  await page.evaluate(async () => {
    const { USE_MOCK } = await import('/src/api/http.ts')
    if (!USE_MOCK) throw new Error('This regression fixture requires VITE_USE_MOCK=true')
    const { fundQuoteApi } = await import('/src/api/fundQuote.ts')
    const { useDashboardStore } = await import('/src/stores/dashboard.ts')
    window.quoteRefreshQa = { calls: 0, store: useDashboardStore() }
    fundQuoteApi.nav = async () => {
      window.quoteRefreshQa.calls++
      return Array.from({ length: 100 }, (_, i) => {
        const day = new Date(); day.setDate(day.getDate() - 100 + i)
        return { date: day.toISOString().slice(0, 10), nav: 1 + i / 1000, dailyGrowthRate: .1, accumulatedNav: null, indexReturnRate: i / 10 }
      })
    }
  })
  await page.locator('.holding-quote-link').first().click()
  await page.locator('.fund-quote-chart canvas').waitFor()
  const baseline = await page.evaluate(async () => {
    const { getInstanceByDom } = await import('/node_modules/.vite/deps/echarts_core.js')
    const host = document.querySelector('.fund-quote-chart')
    const chart = getInstanceByDom(host)
    chart.dispatchAction({ type: 'dataZoom', start: 25, end: 75 })
    chart.dispatchAction({ type: 'legendUnSelect', name: 'MA5' })
    document.querySelector('.quote-content').scrollTop = 100
    window.quoteRefreshQa.chart = chart
    window.quoteRefreshQa.host = host
    return { summary: document.querySelector('.quote-summary').textContent, calls: window.quoteRefreshQa.calls,
      scroll: document.querySelector('.quote-content').scrollTop }
  })
  // Simulate repeated timer responses, including cost/profit changes and fresh
  // object identities; then simulate an in-place update from a data store.
  await page.evaluate(async () => {
    const { nextTick } = await import('/node_modules/.vite/deps/vue.js')
    const store = window.quoteRefreshQa.store
    for (let i = 0; i < 5; i++) {
      store.overview = { ...store.overview, topHoldings: store.overview.topHoldings.map(h => ({
        ...h, holdingCost: h.holdingCost + 100, holdingProfit: h.holdingProfit + 50,
        holdingAmount: h.holdingAmount + 50
      })) }
      await nextTick()
    }
    store.overview.topHoldings.forEach(h => { h.holdingCost += 100; h.holdingProfit += 50 })
    await nextTick()
  })
  const after = await page.evaluate(() => {
    const state = window.quoteRefreshQa, option = state.chart.getOption()
    return { summary: document.querySelector('.quote-summary').textContent, calls: state.calls,
      sameHost: state.host === document.querySelector('.fund-quote-chart'),
      zoom: option.dataZoom[0].start, legend: option.legend[0].selected.MA5,
      scroll: document.querySelector('.quote-content').scrollTop,
      loading: document.querySelector('.quote-content').getAttribute('aria-busy') }
  })
  assert(after.calls === baseline.calls, 'Dashboard polling reloaded NAV')
  assert(after.sameHost && after.loading === 'false', 'Chart was cleared/remounted')
  assert(after.zoom === 25 && after.legend === false, 'Viewing state was reset')
  assert(after.summary === baseline.summary && after.scroll === baseline.scroll, 'Viewing snapshot moved')
  await page.getByRole('button', { name: '刷新 / 补齐', exact: true }).click()
  await page.waitForFunction(n => window.quoteRefreshQa.calls === n + 1, baseline.calls)
  await page.locator('.fund-quote-chart canvas').waitFor()
  assert(await page.locator('.quote-summary').textContent() !== baseline.summary, 'Manual refresh failed to take a new snapshot')
  await page.getByRole('combobox', { name: '参考指数', exact: true }).selectOption('000300')
  await page.waitForFunction(n => window.quoteRefreshQa.calls === n + 2, baseline.calls)
  await page.locator('.fund-quote-chart canvas').waitFor()
  await page.keyboard.press('Escape')
  await page.getByRole('dialog', { name: '基金行情', exact: true }).waitFor({ state: 'hidden' })
  await page.locator('.holding-quote-link').nth(1).click()
  await page.waitForFunction(n => window.quoteRefreshQa.calls === n + 3, baseline.calls)
  await page.locator('.fund-quote-chart canvas').waitFor()
  assert(errors.length === 0, errors.join('\n'))
  console.log('PASS: 5 background replacements + in-place update preserve data, chart instance, zoom, legend and scroll; explicit refresh, benchmark and fund changes still load.')
}
