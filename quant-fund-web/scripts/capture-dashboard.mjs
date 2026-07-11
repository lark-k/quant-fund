import { mkdir } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5173'
const outputPath = path.resolve(process.env.QA_OUTPUT_PATH || path.join('qa-artifacts', 'dashboard-1440x1024.png'))
const outputDir = path.dirname(outputPath)

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

await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
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
await page.goto(`${baseUrl}/dashboard`, { waitUntil: 'networkidle' })
await page.waitForLoadState('networkidle')
await page.locator('.dashboard-grid').waitFor({ state: 'visible', timeout: 10000 })
await page.screenshot({ path: outputPath, fullPage: false })

await browser.close()

console.log(JSON.stringify({ outputPath, consoleErrors }, null, 2))
