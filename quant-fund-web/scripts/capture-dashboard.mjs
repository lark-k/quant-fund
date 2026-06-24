import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'

const outputDir = path.resolve('qa-artifacts')
const outputPath = path.join(outputDir, 'dashboard-1440x1024.png')

await mkdir(outputDir, { recursive: true })

const browser = await chromium.launch({
  headless: true,
  executablePath: 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
})
const page = await browser.newPage({ viewport: { width: 1440, height: 1024 }, deviceScaleFactor: 1 })

const consoleErrors = []
page.on('console', (message) => {
  if (message.type() === 'error') consoleErrors.push(message.text())
})

await page.goto('http://127.0.0.1:5173/login', { waitUntil: 'networkidle' })
await page.evaluate(() => {
  localStorage.setItem('auth', JSON.stringify({
    token: 'mock-quantfund-token',
    user: {
      id: 1,
      username: 'zhangming',
      nickname: '张明',
      role: 'USER',
      riskLevel: 'MEDIUM'
    }
  }))
})
await page.goto('http://127.0.0.1:5173/dashboard', { waitUntil: 'networkidle' })
await page.waitForLoadState('networkidle')
await page.locator('.dashboard-grid').waitFor({ state: 'visible', timeout: 10000 })
await page.screenshot({ path: outputPath, fullPage: false })

await browser.close()

console.log(JSON.stringify({ outputPath, consoleErrors }, null, 2))
