import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

test.use({ serviceWorkers:'block' });
test.skip(process.env.VECTOR_REVIEW_E2E!=='1'||process.env.E2E_URL!=='http://127.0.0.1:8082',
  'Opt-in test restricted to the seeded local fictitious Java Finance.');

async function get(page:Page,path:string) {
  return page.evaluate(async path=>{
    const response=await fetch('/api'+path);
    if(!response.ok)throw new Error('Local API '+response.status);
    return response.json();
  },path);
}

test('RAG vetorial real: leitura não grava, aplicar/remover exige clique e preserva saldos',async({page},info)=>{
  const fixture=JSON.parse(readFileSync(resolve('../.tools/local-review/vector-ui-fixture.json'),'utf8'));
  const external:string[]=[],writes:string[]=[],errors:string[]=[];
  page.on('pageerror',error=>errors.push(error.message));
  await page.route('**/*',async route=>{
    const url=new URL(route.request().url());
    if(url.protocol.startsWith('http')&&url.hostname!=='127.0.0.1'){
      external.push(url.hostname);await route.abort();return;
    }
    if(route.request().method()!=='GET'&&url.pathname.endsWith('/category'))writes.push(url.pathname);
    await route.continue();
  });
  await page.goto('/');
  await page.getByLabel('E-mail').fill('demo@javafinance.local');
  await page.getByLabel('Senha').fill('DemoLocal123!');
  await page.getByRole('button',{name:'Entrar',exact:true}).click();
  await expect(page.getByText(/Ambiente local de demonstração com dados fictícios/)).toBeVisible();
  const before=await get(page,'/reports/summary?month=2026-11');
  const movements=await get(page,'/movements?from=2026-11-01&to=2026-11-30');
  const feed='/movements/feed?from=2026-11-01&to=2026-11-30&size=100';
  const entry=async()=> (await get(page,feed)).items.find((r:{id:string})=>r.id===fixture.targets.pizza);
  const original=await entry();
  expect(original.categoryId).toBeNull();
  await page.goto('/#/movements?month=2026-11');
  await page.getByRole('button',{name:'Detalhes de Pizza em domicílio',exact:true}).click();
  let dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
  await expect(dialog.getByText('RAG vetorial local · 384 dimensões',{exact:true})).toBeVisible({timeout:20000});
  await expect(dialog.getByText(/pedido de pizza entregue em casa → Delivery/)).toBeVisible();
  await expect(dialog.getByLabel('Categoria para este registro')).toContainText('Delivery');
  expect(writes).toEqual([]);
  expect((await entry()).categoryId).toBeNull();
  const trace=dialog.getByRole('alert').filter({hasText:'RAG vetorial local'});
  await trace.scrollIntoViewIfNeeded();
  await trace.screenshot({path:info.outputPath('vector-ui-suggestion.png')});
  try {
    await dialog.getByRole('button',{name:'Aplicar categoria escolhida',exact:true}).click();
    await expect(dialog).toHaveCount(0);
    expect((await entry()).categoryId).toBe(fixture.categories.delivery);
    await page.getByRole('button',{name:'Detalhes de Pizza em domicílio',exact:true}).click();
    dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
    await dialog.getByRole('button',{name:'Remover categoria',exact:true}).click();
    await expect(dialog).toHaveCount(0);
  } finally {
    const current=await entry();
    if(current.categoryId!==null){
      await page.evaluate(async({id,version})=>{
        const csrf=await(await fetch('/api/auth/csrf')).json();
        const response=await fetch('/api/banking/entries/'+id+'/category',{method:'POST',
          headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify({version,categoryId:null})});
        if(!response.ok)throw new Error('Could not restore the synthetic test category.');
      },{id:current.id,version:current.bankEntryVersion});
    }
  }
  expect((await entry()).categoryId).toBeNull();
  const after=await get(page,'/reports/summary?month=2026-11');
  expect(after.balance).toBe(before.balance);
  expect(after.cashFlow).toBe(before.cashFlow);
  expect(after.competence).toEqual(before.competence);
  expect((await get(page,'/movements?from=2026-11-01&to=2026-11-30')).total).toBe(movements.total);
  expect(writes).toHaveLength(2);
  expect(external).toEqual([]);expect(errors).toEqual([]);
  await info.attach('local-invariants.json',{body:JSON.stringify({categoryRestored:true,manualCategoryWrites:writes.length,
    balancePreserved:true,cashFlowPreserved:true,movementCountPreserved:true,externalBrowserRequests:external,pageErrors:errors}),contentType:'application/json'});
});
