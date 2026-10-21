import puppeteer from 'puppeteer-core'
const BASE='http://127.0.0.1:8080'
const b=await puppeteer.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:'new',args:['--no-sandbox'],defaultViewport:{width:1500,height:950}})
const p=await b.newPage()
let cur='login'
p.on('console',m=>{ if(m.type()==='error') console.log('CONSOLE-ERR @',cur,'::',m.text().slice(0,300)) })
p.on('pageerror',e=>console.log('PAGE-ERR @',cur,'::',(e.stack||e.message).split('\n').slice(0,3).join(' | ')))
await p.goto(`${BASE}/#/login`,{waitUntil:'networkidle2'})
await new Promise(r=>setTimeout(r,600))
const link=await p.$('.accounts a'); if(link) await link.click()
for(const btn of await p.$$('button')){const t=await p.evaluate(e=>e.innerText,btn); if(t.includes('登')){await btn.click();break}}
await new Promise(r=>setTimeout(r,3000))
const routes=['overview','metadata','metadata/datasources','metadata/collect','lineage','model','standard','standard/mapping','integration','workflow','quality','quality/issues','quality/reports','security','security/audit','asset','asset/map','service','industry','admin']
for(const r of routes){cur=r; await p.goto(`${BASE}/#/${r}`,{waitUntil:'networkidle2'}); await new Promise(x=>setTimeout(x,1000))}
await b.close()
