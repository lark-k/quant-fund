import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import { chromium } from 'playwright'

const baseUrl = process.env.QA_BASE_URL || 'http://127.0.0.1:5173'
const outputDir = path.resolve('qa-artifacts', 'visual-qa')

const viewports = [
  { name: 'desktop', width: 1440, height: 1024 },
  { name: 'tablet', width: 1024, height: 900 },
  { name: 'mobile', width: 390, height: 844 }
]

const routes = [
  '/dashboard',
  '/holdings',
  '/holding-edit',
  '/fund-detail',
  '/ai-analysis',
  '/profit-analysis',
  '/profit-calendar',
  '/trades',
  '/strategy-config',
  '/system-config',
  '/profile'
]

await mkdir(outputDir, { recursive: true })

const browser = await chromium.launch({
  headless: true,
  executablePath: 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
})

const results = []
const consoleErrors = []

for (const viewport of viewports) {
  const page = await browser.newPage({
    viewport: { width: viewport.width, height: viewport.height },
    deviceScaleFactor: 1
  })
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push({ viewport: viewport.name, text: message.text() })
    }
  })

  await page.goto(`${baseUrl}/login`, { waitUntil: 'networkidle' })
  await page.evaluate(() => {
    localStorage.setItem('auth', JSON.stringify({
      token: 'mock-quantfund-token',
      tokenName: 'Authorization',
      user: {
        id: 1,
        username: 'zhangming',
        nickname: '张明',
        role: 'USER',
        riskLevel: 'MEDIUM'
      }
    }))
  })

  for (const route of routes) {
    await page.goto(`${baseUrl}${route}`, { waitUntil: 'networkidle' })
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(300)

    const routeName = route.replace('/', '') || 'root'
    const screenshotPath = path.join(outputDir, `${viewport.name}-${routeName}.png`)
    await page.screenshot({ path: screenshotPath, fullPage: false })

    const diagnostics = await page.evaluate(() => {
      const normalize = (text) => text.replace(/\s+/g, ' ').trim()
      const isVisible = (element) => {
        const style = window.getComputedStyle(element)
        const rect = element.getBoundingClientRect()
        return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 1 && rect.height > 1
      }
      const selectorFor = (element) => {
        const parts = []
        let current = element
        while (current && current.nodeType === Node.ELEMENT_NODE && current !== document.body && parts.length < 4) {
          let part = current.tagName.toLowerCase()
          if (current.id) part += `#${current.id}`
          const className = String(current.className || '').trim().split(/\s+/).filter(Boolean).slice(0, 3).join('.')
          if (className) part += `.${className}`
          parts.unshift(part)
          current = current.parentElement
        }
        return parts.join(' > ')
      }
      const elements = [...document.querySelectorAll('body *')]
      const truncations = []
      const horizontalOverflow = document.documentElement.scrollWidth - document.documentElement.clientWidth
      for (const element of elements) {
        if (!isVisible(element)) continue
        const text = normalize(element.innerText || element.textContent || '')
        if (text.length < 2) continue
        const style = window.getComputedStyle(element)
        const widthOverflow = element.scrollWidth - element.clientWidth
        const heightOverflow = element.scrollHeight - element.clientHeight
        const clipsText = widthOverflow > 1 || heightOverflow > 1
        const clippingStyle = style.overflow === 'hidden' || style.overflowX === 'hidden' || style.overflowY === 'hidden' || style.textOverflow === 'ellipsis'
        if (clipsText && clippingStyle) {
          const rect = element.getBoundingClientRect()
          truncations.push({
            selector: selectorFor(element),
            text: text.slice(0, 140),
            clientWidth: Math.round(element.clientWidth),
            scrollWidth: Math.round(element.scrollWidth),
            clientHeight: Math.round(element.clientHeight),
            scrollHeight: Math.round(element.scrollHeight),
            overflow: `${style.overflow}/${style.overflowX}/${style.overflowY}`,
            textOverflow: style.textOverflow,
            whiteSpace: style.whiteSpace,
            rect: {
              x: Math.round(rect.x),
              y: Math.round(rect.y),
              width: Math.round(rect.width),
              height: Math.round(rect.height)
            }
          })
        }
      }
      return {
        title: document.title,
        url: location.pathname,
        horizontalOverflow,
        truncations: truncations.slice(0, 80)
      }
    })

    results.push({
      viewport,
      route,
      screenshotPath,
      ...diagnostics
    })
  }

  await page.close()
}

await browser.close()

const report = {
  baseUrl,
  capturedAt: new Date().toISOString(),
  consoleErrors,
  results
}

await writeFile(path.join(outputDir, 'diagnostics.json'), JSON.stringify(report, null, 2), 'utf8')
console.log(JSON.stringify({
  outputDir,
  pages: results.length,
  consoleErrors: consoleErrors.length,
  truncationCandidates: results.reduce((sum, item) => sum + item.truncations.length, 0),
  routesWithHorizontalOverflow: results.filter((item) => item.horizontalOverflow > 1).map((item) => `${item.viewport.name}:${item.route}`)
}, null, 2))
