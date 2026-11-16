import { test, expect, type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';

const base='https://java-finance-category-preview.vercel.app';
const allowed=process.env.CLOUD_CATEGORY_PREVIEW_E2E==='1'&&process.env.E2E_URL===base;
test.skip(!allowed,'Explicit opt-in: only the isolated synthetic cloud Preview.');
test.use({serviceWorkers:'block'});

async function api(page:Page,path:string,body?:unknown){
  return page.evaluate(async({path,body})=>{
    const headers:Record<string,string>={};
    if(body!==undefined){
      const csrf=await fetch('/api/auth/csrf').then(r=>r.json());headers[csrf.headerName]=csrf.token;headers['Content-Type']='application/json';
    }
    const response=await fetch('/api'+path,{method:body===undefined?'GET':'POST',headers,body:body===undefined?undefined:JSON.stringify(body)});
    if(!response.ok)throw new Error('Preview API '+response.status);
    return response.status===204?undefined:response.text().then(t=>t?JSON.parse(t):undefined);
  },{path,body});
}

test('fila cloud, opt-in, memória e correção humana sem alterar saldos',async({page},info)=>{
  test.setTimeout(300000);
  const fixture=JSON.parse(readFileSync(resolve('../.tools/cloud-category-preview/fixture.json'),'utf8'));
  const token=process.env.VERCEL_OIDC_TOKEN;
  expect(token).toBeTruthy();
  const writes:string[]=[],errors:string[]=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
    const request=route.request(),url=new URL(request.url());
    if(!url.protocol.startsWith('http'))return route.continue();
    if(url.origin!==base)return route.abort();
    if(request.method()==='POST'&&/\/entries\/[^/]+\/category$/.test(url.pathname))writes.push(url.pathname);
    return route.continue({headers:{...request.headers(),'x-vercel-trusted-oidc-idp-token':token!}});
  });
  await page.goto('/');
  await page.getByLabel('E-mail').fill(fixture.email);await page.getByLabel('Senha').fill('DemoCloud123!');
  await page.getByRole('button',{name:'Entrar',exact:true}).click();
  await expect(page.getByText('Demonstração na nuvem',{exact:true})).toBeVisible({timeout:60000});
  const feed=`/movements/feed?from=${fixture.from}&to=${fixture.to}&size=100`;
  const entry=async(id:string)=>(await api(page,feed)).items.find((r:{id:string})=>r.id===id);
  const money=async()=>({accounts:(await api(page,'/accounts')).map((a:{id:string;balance:string})=>({id:a.id,balance:a.balance})),summary:await api(page,`/reports/summary?month=${fixture.month}`)});
  const before=await money(),total=(await api(page,feed)).total;
  const previous=await api(page,'/banking/category-automation');
  if(previous.enabled)await api(page,'/banking/category-automation',{enabled:false,version:previous.version});
  expect((await api(page,'/banking/category-automation')).enabled).toBe(false);
  await page.goto('/#/movements?month='+fixture.month);
  await page.getByLabel('Categorizar automaticamente com IA').click();
  await expect.poll(async()=>(await api(page,'/banking/category-automation')).enabled,{timeout:30000}).toBe(true);
  console.info('cloud_step=activated');
  for(const key of ['mercado','assinatura','delivery']){
    await expect.poll(async()=>(await entry(fixture.entries[key].id))?.categorySource,{timeout:180000,intervals:[3000]}).toBe('AUTO');
  }
  expect((await entry(fixture.entries.mercado.id)).categoryId).toBe(fixture.categories.Mercado);
  expect((await entry(fixture.entries.assinatura.id)).categoryId).toBe(fixture.categories.Assinaturas);
  expect((await entry(fixture.entries.delivery.id)).categoryId).toBe(fixture.categories.Delivery);
  expect((await entry(fixture.entries.pix.id)).categoryId).toBeNull();expect(writes).toEqual([]);
  console.info('cloud_step=classified');
  const after=await money();expect(after.accounts).toEqual(before.accounts);
  expect(after.summary.balance).toEqual(before.summary.balance);expect(after.summary.cashFlow).toEqual(before.summary.cashFlow);expect(after.summary.competence).toEqual(before.summary.competence);
  expect((await api(page,feed)).total).toBe(total);
  const repeat=()=>execFileSync(resolve('../.tools/vector-rag-venv/Scripts/python.exe'),[resolve('../.tools/cloud-category-preview/add_repeat.py'),resolve('..')],{stdio:'pipe',env:process.env}).toString().trim();
  const memoId=repeat();
  await api(page,'/banking/category-automation/retry',{});
  await expect.poll(async()=>(await entry(memoId))?.categoryId,{timeout:120000,intervals:[3000]}).toBe(fixture.categories.Delivery);
  expect((await api(page,`/banking/entries/${memoId}/assistance`)).categoryState.method).toBe('MEMORY');
  console.info('cloud_step=memory');
  await page.getByLabel('Pesquisar descrição').fill(fixture.entries.delivery.description);
  await page.getByRole('button',{name:'Detalhes de '+fixture.entries.delivery.description,exact:true}).first().click();
  const dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
  await expect(dialog.getByText(/Categoria aplicada automaticamente/)).toBeVisible();
  await dialog.getByLabel('Categoria para este registro').click();await page.getByRole('option',{name:'Outros gastos',exact:true}).click();
  await dialog.getByRole('button',{name:'Salvar minha categoria',exact:true}).click();await expect(dialog).toHaveCount(0);
  const correctedId=repeat();await api(page,'/banking/category-automation/retry',{});
  await expect.poll(async()=>(await entry(correctedId))?.categoryId,{timeout:120000,intervals:[3000]}).toBe(fixture.categories['Outros gastos']);
  expect((await api(page,`/banking/entries/${correctedId}/assistance`)).categoryState.method).toBe('HISTORY');
  await page.getByLabel('Categorizar automaticamente com IA').click();
  await expect.poll(async()=>(await api(page,'/banking/category-automation')).enabled).toBe(false);
  expect((await money()).accounts).toEqual(before.accounts);expect(errors).toEqual([]);
  await page.screenshot({path:info.outputPath('cloud-categories.png'),fullPage:true});
  await info.attach('cloud-result.json',{body:JSON.stringify({optIn:true,queueWorked:true,automaticMarket:true,automaticSubscription:true,automaticDelivery:true,pixAbstained:true,memoryWithoutNewModel:true,humanCorrectionWon:true,disabled:true,balancePreserved:true,initialRecordCountPreserved:true}),contentType:'application/json'});
});

test('isola a ativação e os registros de outro usuário no Preview',async({page})=>{
  const fixture=JSON.parse(readFileSync(resolve('../.tools/cloud-category-preview/fixture.json'),'utf8'));
  const token=process.env.VERCEL_OIDC_TOKEN;expect(token).toBeTruthy();
  await page.route('**/*',route=>{
    const url=new URL(route.request().url());
    if(url.protocol.startsWith('http')&&url.origin!==base)return route.abort();
    return route.continue({headers:{...route.request().headers(),'x-vercel-trusted-oidc-idp-token':token!}});
  });
  await page.goto('/');await page.getByLabel('E-mail').fill(fixture.peerEmail);
  await page.getByLabel('Senha').fill('DemoCloud123!');await page.getByRole('button',{name:'Entrar',exact:true}).click();
  await expect(page.getByText('Segundo perfil fictício',{exact:true})).toBeVisible({timeout:60000});
  expect((await api(page,'/banking/category-automation')).enabled).toBe(false);
  expect((await api(page,`/movements/feed?from=${fixture.from}&to=${fixture.to}`)).total).toBe(0);
  const status=await page.evaluate(async id=>(await fetch(`/api/banking/entries/${id}/assistance`)).status,fixture.entries.delivery.id);
  expect(status).toBe(404);
});
