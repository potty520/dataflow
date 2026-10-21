import puppeteer from 'puppeteer-core'
import fs from 'node:fs'
import path from 'node:path'

const CHROME = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
const BASE = 'http://127.0.0.1:8080'
const OUT = path.resolve('../screenshots')
fs.mkdirSync(OUT, { recursive: true })

const routes = [
  ['login', '/#/login', 0],
  ['overview', '/#/overview'],
  ['metadata', '/#/metadata'],
  ['metadata-ds', '/#/metadata/datasources'],
  ['metadata-collect', '/#/metadata/collect'],
  ['lineage', '/#/lineage'],
  ['model', '/#/model'],
  ['standard', '/#/standard'],
  ['standard-mapping', '/#/standard/mapping'],
  ['integration', '/#/integration'],
  ['workflow', '/#/workflow'],
  ['quality', '/#/quality'],
  ['issues', '/#/quality/issues'],
  ['reports', '/#/quality/reports'],
  ['security', '/#/security'],
  ['audit', '/#/security/audit'],
  ['asset', '/#/asset'],
  ['asset-map', '/#/asset/map'],
  ['service', '/#/service'],
  ['industry', '/#/industry'],
  ['admin', '/#/admin']
]

const errors = []
const failed = []

const browser = await puppeteer.launch({
  executablePath: CHROME,
  headless: 'new',
  args: ['--no-sandbox', '--disable-gpu', '--window-size=1600,1000'],
  defaultViewport: { width: 1600, height: 1000 }
})
const page = await browser.newPage()
page.on('console', (m) => {
  if (m.type() === 'error') errors.push(`[console] ${m.text()}`)
})
page.on('pageerror', (e) => errors.push(`[pageerror] ${e.message}`))
page.on('requestfailed', (r) => failed.push(`${r.url()} ${r.failure()?.errorText}`))
page.on('response', (r) => {
  if (r.status() >= 400) failed.push(`HTTP ${r.status()} ${r.url()}`)
})

await page.goto(`${BASE}/#/login`, { waitUntil: 'networkidle2' })
await new Promise((r) => setTimeout(r, 1200))
await page.screenshot({ path: path.join(OUT, '00-login.png') })

// 登录
const inputs = await page.$$('input')
if (inputs.length >= 2) {
  await inputs[0].click({ clickCount: 3 })
  await inputs[0].type('admin')
  await inputs[1].click({ clickCount: 3 })
  await inputs[1].type('Dgp@2026')
}
const btns = await page.$$('button')
for (const b of btns) {
  const txt = await page.evaluate((el) => el.innerText, b)
  if (txt.includes('登')) { await b.click(); break }
}
await new Promise((r) => setTimeout(r, 3000))

for (const [name, url] of routes) {
  if (name === 'login') continue
  await page.goto(`${BASE}${url}`, { waitUntil: 'networkidle2' })
  await new Promise((r) => setTimeout(r, 1800))
  await page.screenshot({ path: path.join(OUT, `${String(routes.findIndex((x) => x[0] === name)).padStart(2, '0')}-${name}.png`) })
  const title = await page.evaluate(() => document.querySelector('.page-title h2')?.innerText || document.body.innerText.slice(0, 60))
  console.log(`OK ${name.padEnd(18)} -> ${title}`)
}

console.log('\n=== console/page errors ===')
console.log(errors.length ? errors.slice(0, 20).join('\n') : 'none')
console.log('\n=== failed requests ===')
console.log(failed.length ? [...new Set(failed)].slice(0, 20).join('\n') : 'none')

await browser.close()
