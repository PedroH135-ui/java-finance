import { test,expect,type Page } from '@playwright/test';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

test.use({serviceWorkers:'block'});
test.skip(process.env.PLANNING_INCOME_E2E!=='1'||process.env.E2E_URL!=='http://127.0.0.1:8082','Only the guarded local fictitious application.');
async function get(page:Page,path:string){return page.evaluate(async path=>{const r=await fetch('/api'+path);if(!r.ok)throw new Error('Local API '+r.status);return r.json();},path);}

test('salário e gastos explicam aporte, redução necessária e prazo sem criar dinheiro',async({page},info)=>{
  const external:string[]=[],errors:string[]=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
    const url=new URL(route.request().url());
    if(url.protocol.startsWith('http')&&url.hostname!=='127.0.0.1'){external.push(url.hostname);await route.abort();return;}
    await route.continue();
  });
  const fixture=JSON.parse(readFileSync(resolve('../.tools/local-review/planning-income-fixture.json'),'utf8'));
  if(!fixture.syntheticOnly||!/^planning-income-[a-f0-9-]+@example\.test$/.test(fixture.email))throw new Error('Unexpected fictitious fixture.');
  const email=fixture.email;
  await page.goto('/');
  await page.getByLabel('E-mail').fill(email);await page.getByLabel('Senha').fill('DemoLocal123!');
  await page.getByRole('button',{name:'Entrar',exact:true}).click();
  await expect(page.getByRole('link',{name:'Metas e planejamento',exact:true})).toBeVisible();
  const month=new Date().toLocaleDateString('en-CA',{timeZone:'America/Sao_Paulo'}).slice(0,7);
  const report='/reports/summary?month='+month;const before=await get(page,report);
  await page.goto('/#/goals');
  await expect(page.getByText(/Não há meses anteriores com registros/)).toBeVisible();
  expect((await get(page,'/goals/finances')).available).toBeNull();
  await page.getByLabel('Salário líquido mensal previsto').fill('3000');
  await page.getByLabel('Gastos mensais previstos (opcional)').fill('2700');
  await page.getByRole('button',{name:'Salvar previsões do planejamento',exact:true}).click();
  await expect(page.getByText(/Previsões salvas/)).toBeVisible();
  await expect.poll(async()=>(await get(page,'/goals/finances')).available).toBe('300.00');
  async function send(message:string){await page.getByLabel('Converse sobre sua meta').fill(message);await page.getByRole('button',{name:'Enviar mensagem',exact:true}).click();await expect(page.getByLabel('Converse sobre sua meta')).toHaveValue('');}
  await send('Quero viajar e guardar 3000');await send('em 6 meses');await send('já tenho 600 reservado');
  await expect(page.getByRole('log')).toContainText('Faltam R$ 100.00 por mês');
  await expect(page.getByRole('log')).toContainText('prazo mínimo estimado seria 8 meses');
  await expect(page.getByText(/Falta economizar adicionalmente: R\$ 100,00/)).toBeVisible();
  expect(await get(page,'/goals')).toEqual([]);
  await send('Quanto ganho de salário e quanto gasto por mês?');
  await expect(page.getByRole('log')).toContainText('Salário líquido mensal: R$ 3000.00');
  await expect(page.getByRole('log')).toContainText('Gastos mensais estimados: R$ 2700.00');
  const context=await get(page,'/goals/finances');expect(context.currentIncome).toBe('0.00');expect(context.currentConsumption).toBe('0.00');
  const after=await get(page,report);expect(after.balance).toBe(before.balance);expect(after.cashFlow).toBe(before.cashFlow);expect(after.records).toBe(before.records);
  expect(external).toEqual([]);expect(errors).toEqual([]);
  await page.screenshot({path:info.outputPath('planning-income-local.png'),fullPage:true});
  await info.attach('planning-income-invariants.json',{body:JSON.stringify({onlyNewSyntheticUser:true,salary:3000,expenses:2700,available:300,target:3000,saved:600,months:6,requiredMonthly:400,gap:100,minimumMonths:8,noGoalSaved:true,balancePreserved:true,cashFlowPreserved:true,noIncomeOrExpenseCreated:true,externalRequests:0,pageErrors:0}),contentType:'application/json'});
});
