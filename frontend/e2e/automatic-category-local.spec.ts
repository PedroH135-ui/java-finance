import { test, expect, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';

test.use({serviceWorkers:'block'});
test.skip(process.env.AUTOMATIC_REVIEW_E2E!=='1'||process.env.E2E_URL!=='http://127.0.0.1:8082',
  'Opt-in: only the local synthetic automatic-category environment.');

async function get(page:Page,path:string){
  return page.evaluate(async path=>{
    const response=await fetch('/api'+path);if(!response.ok)throw new Error('Local API '+response.status);return response.json();
  },path);
}
function seedAutomatic(repeatOf?:string){
  const args=['-m','finance_vector.seed_ui','--root',resolve('..'),'--automatic-test-id',randomUUID()];
  if(repeatOf)args.push('--automatic-repeat-of',repeatOf);
  execFileSync(resolve('../.tools/vector-rag-venv/Scripts/python.exe'),args,
    {cwd:resolve('..'),env:{...process.env,PYTHONPATH:resolve('../vector-lab')+';'+resolve('../category-ai')},stdio:'pipe'});
  return JSON.parse(readFileSync(resolve('../.tools/local-review/automatic-category-fixture.json'),'utf8'));
}

test('classifica em segundo plano e respeita correção e remoção sem alterar saldos',async({page},info)=>{
  test.setTimeout(180000);
  const writes:string[]=[],external:string[]=[],errors:string[]=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
    const url=new URL(route.request().url());
    if(url.protocol.startsWith('http')&&url.hostname!=='127.0.0.1'){external.push(url.hostname);await route.abort();return;}
    if(route.request().method()!=='GET'&&url.pathname.endsWith('/category'))writes.push(url.pathname);
    await route.continue();
  });
  await page.goto('/');
  await page.getByLabel('E-mail').fill('demo@javafinance.local');await page.getByLabel('Senha').fill('DemoLocal123!');
  await page.getByRole('button',{name:'Entrar',exact:true}).click();
  await expect(page.getByText('Demonstração local',{exact:true})).toBeVisible();
  expect((await get(page,'/banking/category-automation')).enabled).toBe(true);
  // Create a fresh guarded synthetic bank record after the server is running, without calling assistance.
  const fixture=seedAutomatic();
  const feed='/movements/feed?from=2026-11-01&to=2026-11-30&size=100';
  const entry=async()=> (await get(page,feed)).items.find((r:{id:string})=>r.id===fixture.id);
  const before=await get(page,'/reports/summary?month=2026-11');
  const total=(await get(page,feed)).total;
  await page.goto('/#/movements?month=2026-11');
  await expect(page.getByText(/Classificação automática local ativada/)).toBeVisible();
  await expect.poll(async()=> (await entry())?.categorySource,{timeout:90000,intervals:[2000]}).toBe('AUTO');
  expect((await entry()).categoryId).toBe(fixture.deliveryCategoryId);
  expect(writes).toEqual([]); // Only the backend worker saved; the browser has not applied a category.
  await page.getByLabel('Pesquisar descrição').fill(fixture.description);
  await page.getByRole('button',{name:'Detalhes de '+fixture.description,exact:true}).click();
  let dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
  await expect(dialog.getByText(/Categoria aplicada automaticamente/)).toBeVisible();
  await dialog.getByLabel('Categoria para este registro').click();
  await page.getByRole('option',{name:'Alimentação',exact:true}).click();
  await dialog.getByRole('button',{name:'Salvar minha categoria',exact:true}).click();
  await expect(dialog).toHaveCount(0);
  await expect.poll(async()=> (await entry()).categorySource).toBe('MANUAL');
  expect((await entry()).categoryId).toBe(fixture.otherCategoryId);
  // Observe another scheduler cycle rather than sleeping a fixed duration.
  let run=(await get(page,'/banking/category-automation')).lastRun;
  await expect.poll(async()=> (await get(page,'/banking/category-automation')).lastRun,{timeout:30000}).not.toBe(run);
  expect((await entry()).categoryId).toBe(fixture.otherCategoryId);
  await page.getByRole('button',{name:'Detalhes de '+fixture.description,exact:true}).click();
  dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
  await expect(dialog.getByText(/Categoria escolhida por você/)).toBeVisible();
  await dialog.getByRole('button',{name:'Remover categoria',exact:true}).click();await expect(dialog).toHaveCount(0);
  run=(await get(page,'/banking/category-automation')).lastRun;
  await expect.poll(async()=> (await get(page,'/banking/category-automation')).lastRun,{timeout:30000}).not.toBe(run);
  expect((await entry()).categoryId).toBeNull();
  await page.getByRole('button',{name:'Detalhes de '+fixture.description,exact:true}).click();
  dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
  await expect(dialog.getByText(/Classificação automática pausada/)).toBeVisible();
  await dialog.getByRole('button',{name:'Permitir classificação automática novamente',exact:true}).click();
  await expect(dialog).toHaveCount(0);
  await expect.poll(async()=> (await entry()).categorySource,{timeout:60000,intervals:[2000]}).toBe('AUTO');
  expect((await entry()).categoryId).toBe(fixture.deliveryCategoryId);
  await page.getByRole('button',{name:'Detalhes de '+fixture.description,exact:true}).click();
  dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
  const message=dialog.getByRole('alert').filter({hasText:'Categoria aplicada automaticamente'});
  await message.scrollIntoViewIfNeeded();await message.screenshot({path:info.outputPath('automatic-category.png')});
  const after=await get(page,'/reports/summary?month=2026-11');
  expect(after.balance).toBe(before.balance);expect(after.cashFlow).toBe(before.cashFlow);expect(after.competence).toEqual(before.competence);
  expect((await get(page,feed)).total).toBe(total);expect(writes).toHaveLength(2);expect(external).toEqual([]);expect(errors).toEqual([]);
  await info.attach('invariants.json',{body:JSON.stringify({backgroundClassified:true,manualCorrectionPreserved:true,
    removalPreserved:true,explicitReenable:true,balancePreserved:true,totalsPreserved:true,movementCountPreserved:true}),contentType:'application/json'});
});

test('reutiliza uma decisão original da IA sem transformar previsões em histórico humano',async({page},info)=>{
  test.setTimeout(120000);
  await page.goto('/');await page.getByLabel('E-mail').fill('demo@javafinance.local');await page.getByLabel('Senha').fill('DemoLocal123!');
  await page.getByRole('button',{name:'Entrar',exact:true}).click();await expect(page.getByText('Demonstração local',{exact:true})).toBeVisible();
  expect((await get(page,'/banking/category-automation')).enabled).toBe(true);
  const original=seedAutomatic();
  const feed='/movements/feed?from=2026-11-01&to=2026-11-30&size=100';
  const entry=async(id:string)=>(await get(page,feed)).items.find((r:{id:string})=>r.id===id);
  await expect.poll(async()=>(await entry(original.id))?.categorySource,{timeout:60000,intervals:[2000]}).toBe('AUTO');
  expect((await get(page,'/banking/entries/'+original.id+'/assistance')).categoryState.method).toBe('LLM');
  const repeated=seedAutomatic(original.id);
  expect(repeated.description).toBe(original.description);
  const before=await get(page,'/reports/summary?month=2026-11');const count=(await get(page,feed)).total;
  await expect.poll(async()=>(await entry(repeated.id))?.categorySource,{timeout:60000,intervals:[2000]}).toBe('AUTO');
  const result=await get(page,'/banking/entries/'+repeated.id+'/assistance');
  expect(result.currentCategoryId).toBe(original.deliveryCategoryId);expect(result.categoryState.method).toBe('MEMORY');
  expect(result.categoryState.source).toBe('AUTO');expect(result.categoryState.confidence).toBe('LOW');
  const after=await get(page,'/reports/summary?month=2026-11');
  expect(after.balance).toBe(before.balance);expect(after.cashFlow).toBe(before.cashFlow);expect(after.competence).toEqual(before.competence);
  expect((await get(page,feed)).total).toBe(count);
  await info.attach('decision-reuse.json',{body:JSON.stringify({originalMethod:'LLM',repeatMethod:'MEMORY',stillUnconfirmed:true,balancePreserved:true}),contentType:'application/json'});
});
