import puppeteer from 'puppeteer-core'
const BASE='http://127.0.0.1:8080'
const b=await puppeteer.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:'new',args:['--no-sandbox'],defaultViewport:{width:1500,height:950}})
const p=await b.newPage()
await p.evaluateOnNewDocument(()=>{ window.__errs=[]; const oe=console.error; console.error=function(...a){ window.__errs.push(a.map(x=>x&&x.stack?x.stack:String(x)).join('\n')) ; oe.apply(console,a) } })
await p.goto(`${BASE}/#/login`,{waitUntil:'networkidle2'})
await new Promise(r=>setTimeout(r,600))
const link=await p.$('.accounts a'); if(link) await link.click()
for(const btn of await p.$$('button')){const t=await p.evaluate(e=>e.innerText,btn); if(t.includes('登')){await btn.click();break}}
await new Promise(r=>setTimeout(r,3000))
await p.goto(`${BASE}/#/quality`,{waitUntil:'networkidle2'})
await new Promise(r=>setTimeout(r,2500))
const errs=await p.evaluate(()=>window.__errs)
console.log(errs.join('\n----\n').slice(0,3000))
await b.close()
