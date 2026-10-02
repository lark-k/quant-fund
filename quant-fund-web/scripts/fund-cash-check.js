// Run with Playwright CLI against the isolated VITE_USE_MOCK preview after login.
async (page) => {
  const check = (ok, message) => { if (!ok) throw new Error(message) }
  const errors = []; page.on('pageerror', e => errors.push(e.message))
  await page.setViewportSize({ width: 1440, height: 1080 })
  await page.evaluate(async () => {
    const { USE_MOCK } = await import('/src/api/http.ts')
    if (!USE_MOCK) throw new Error('QA requires isolated mock environment')
    const { fundCashApi } = await import('/src/api/fundCash.ts')
    const { quantApi } = await import('/src/api/quant.ts')
    const holdings = await quantApi.holdings()
    const rows = holdings.map(h => ({ fundCode: h.fundCode, fundName: h.fundName, balance: 0, archived: false, pendingBuy: 0, pendingSell: 0 }))
    rows.push({ fundCode: '999998', fundName: '超出首页列表的持仓基金', balance: 0, archived: false, pendingBuy: 100, pendingSell: 0 })
    rows.push({ fundCode: '999999', fundName: '已清仓测试基金', balance: 60, archived: true, pendingBuy: 0, pendingSell: 0 })
    window.cashQa = { data: { version: 0, unallocated: 1000, total: 1060, funds: rows }, fail: false, saves: 0 }
    fundCashApi.get = async () => structuredClone(window.cashQa.data)
    fundCashApi.save = async (id, request) => {
      if (window.cashQa.fail) throw new Error('现金或交易已更新，请重新打开弹窗后调整')
      const next = { ...window.cashQa.data, ...request, version: request.version + 1 }
      next.total = next.unallocated + next.funds.reduce((s, f) => s + f.balance, 0)
      next.funds = next.funds.map(f => ({ ...window.cashQa.data.funds.find(r => r.fundCode === f.fundCode), ...f }))
      window.cashQa.data = next; window.cashQa.saves++; return structuredClone(next)
    }
    const dashboard = quantApi.dashboard
    quantApi.dashboard = async () => {
      const result = await dashboard()
      result.summary.cashAmount = window.cashQa.data.total
      result.summary.accounts[0].cashAmount = window.cashQa.data.total
      return result
    }
  })
  await page.getByRole('button', { name: '修改现金金额' }).click()
  const modal = page.getByRole('dialog', { name: '分配基金可用现金' })
  await modal.getByText('超出首页列表的持仓基金', { exact: false }).waitFor()
  check(await modal.getByText('已移除持仓，现金保留', { exact: false }).count() === 1, 'Archived cash missing')
  const inputs = modal.getByRole('spinbutton')
  await inputs.nth(1).fill('300'); await inputs.nth(1).blur()
  check(await inputs.nth(0).inputValue() === '700.00', 'Fund allocation did not deduct unallocated cash')
  await inputs.nth(2).fill('250'); await inputs.nth(2).blur()
  check(await inputs.nth(0).inputValue() === '450.00', 'Second allocation did not deduct cash')
  check((await modal.locator('.cash-summary').textContent()).includes('1,060.00'), 'Allocation changed total cash')
  await inputs.nth(1).fill('200'); await inputs.nth(1).blur()
  check(await inputs.nth(0).inputValue() === '550.00', 'Reduction did not return cash')
  await inputs.nth(1).fill('300'); await inputs.nth(1).blur()
  await inputs.nth(2).fill('800'); await inputs.nth(2).blur()
  check(await inputs.nth(2).inputValue() === '250.00', 'Insufficient allocation was not rejected')
  check(await inputs.nth(0).inputValue() === '450.00', 'Rejected allocation changed cash')
  await inputs.nth(0).fill('500'); await inputs.nth(0).blur()
  check((await modal.locator('.cash-summary').textContent()).includes('1,110.00'), 'Total is not summed correctly')
  await page.waitForFunction(() => [...document.querySelectorAll('.fund-cash-dialog')].some(el => getComputedStyle(el).opacity === '1'))
  await page.screenshot({ path: 'output/playwright/fund-cash-desktop.png', animations: 'disabled' })
  await modal.getByRole('button', { name: '保存现金分配' }).click()
  await modal.waitFor({ state: 'hidden' })
  check(await page.evaluate(() => window.cashQa.saves) === 1, 'Save not called')
  await page.getByText('1,110.00', { exact: true }).waitFor()
  await page.getByRole('button', { name: '修改现金金额' }).click()
  await inputs.nth(1).waitFor()
  check(await inputs.nth(1).inputValue() === '300.00', 'Saved allocation not restored')
  await page.evaluate(() => { window.cashQa.fail = true })
  await inputs.nth(1).fill('400'); await inputs.nth(1).blur()
  await modal.getByRole('button', { name: '保存现金分配' }).click()
  await modal.getByRole('alert').waitFor()
  check(await inputs.nth(1).inputValue() === '400.00', 'Failed save discarded edits')
  await modal.getByRole('button', { name: '重新读取' }).click()
  await inputs.nth(1).waitFor()
  check(await inputs.nth(1).inputValue() === '300.00', 'Reload did not restore server balance')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.screenshot({ path: 'output/playwright/fund-cash-mobile.png' })
  const bounds = await modal.boundingBox()
  check(bounds.x >= 0 && bounds.x + bounds.width <= 391, 'Mobile dialog overflow')
  await modal.getByRole('button', { name: '取消', exact: true }).click()
  check(errors.length === 0, errors.join('\n'))
  console.log('PASS: all holdings, archived cash, amount sum, save/reopen, conflict/reload, mobile layout; no page errors')
}
