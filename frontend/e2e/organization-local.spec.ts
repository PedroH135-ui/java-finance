import { test,expect,type Page } from '@playwright/test';

test.use({serviceWorkers:'block'});
test.skip(process.env.ORGANIZATION_REVIEW_E2E!=='1'||process.env.E2E_URL!=='http://127.0.0.1:8082','Only the guarded fictitious local application.');

async function get(page:Page,path:string){
  return page.evaluate(async path=>{const r=await fetch('/api'+path);if(!r.ok)throw new Error('Local API '+r.status);return r.json();},path);
}
test('organização completa: PIX, confirmação de RDB, posição e meta sem mudar dinheiro',async({page},info)=>{
  const external:string[]=[],errors:string[]=[];
  page.on('pageerror',e=>errors.push(e.message));
  await page.route('**/*',async route=>{
    const url=new URL(route.request().url());
    if(url.protocol.startsWith('http')&&url.hostname!=='127.0.0.1'){external.push(url.hostname);await route.abort();return;}
    await route.continue();
  });
  await page.goto('/');
  await page.getByLabel('E-mail').fill('demo@javafinance.local');await page.getByLabel('Senha').fill('DemoLocal123!');
  await page.getByRole('button',{name:'Entrar',exact:true}).click();
  await expect(page.getByText(/Ambiente local de demonstração com dados fictícios/)).toBeVisible();
  const report='/reports/summary?month=2026-11',feed='/movements/feed?from=2026-11-01&to=2026-11-30&size=100';
  const original=(await get(page,feed)).items.find((r:{description:string})=>r.description==='Aplicação automática RDB fictícia');
  if(original.financialNature&&original.financialNature!=='ORDINARY'){
    await page.evaluate(async entry=>{
      const csrf=await(await fetch('/api/auth/csrf')).json();
      const response=await fetch('/api/banking/entries/'+entry.bankEntryId+'/nature',{method:'POST',headers:{'Content-Type':'application/json',[csrf.headerName]:csrf.token},body:JSON.stringify({version:entry.bankEntryVersion,nature:'ORDINARY',instrument:'NONE',confirmed:true})});
      if(!response.ok)throw new Error('Could not reset the explicitly fictitious nature.');
    },original);
  }
  const baseline=await get(page,report),movements=await get(page,'/movements?from=2026-11-01&to=2026-11-30');
  await page.goto('/#/settings');
  await expect(page.getByLabel('Categorizar automaticamente com IA')).toBeVisible();
  await expect(page.getByLabel('Categorizar automaticamente com IA')).toBeChecked();
  const pixCategory=(await get(page,'/categories')).find((c:{name:string;kind:string})=>c.name==='PIX'&&c.kind==='EXPENSE');
  await expect.poll(async()=>((await get(page,feed)).items.find((r:{description:string})=>r.description==='PIX enviado para pessoa fictícia'))?.categoryId,{timeout:45000}).toBe(pixCategory.id);
  const unknown=(await get(page,feed)).items.find((r:{description:string})=>r.description==='PIX destinatário não identificado fictício');
  expect(unknown.categoryId).toBeNull();
  await page.goto('/#/movements?month=2026-11');
  await expect(page.getByLabel('Categorizar automaticamente com IA')).toHaveCount(0);
  await page.getByRole('button',{name:'Detalhes de Aplicação automática RDB fictícia',exact:true}).click();
  const dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
  await expect(dialog.getByText('Natureza financeira',{exact:true})).toBeVisible();
  await dialog.getByLabel('Natureza financeira do registro').click();
  await page.getByRole('option',{name:'Aplicação em investimento',exact:true}).click();
  await dialog.getByLabel('Produto do investimento').click();
  await page.getByRole('option',{name:'RDB · renda fixa',exact:true}).click();
  await expect(dialog.getByLabel('Produto do investimento')).toContainText('RDB');
  expect((await get(page,report)).competence.expenses).toBe(baseline.competence.expenses);
  await dialog.getByLabel('Confirmo a natureza escolhida e sua aplicação aos indicadores').check();
  await dialog.getByRole('button',{name:'Confirmar natureza financeira',exact:true}).click();
  await expect(dialog).toHaveCount(0);
  const afterNature=await get(page,report);
  expect(Number(afterNature.competence.expenses)).toBe(Number(baseline.competence.expenses)-300);
  expect(afterNature.balance).toBe(baseline.balance);expect(afterNature.cashFlow).toBe(baseline.cashFlow);
  await page.goto('/#/investments');
  await expect(page.getByText('Aplicação automática RDB fictícia · RDB',{exact:true})).toBeVisible();
  const existing=(await get(page,'/investments')).positions.find((p:{name:string})=>p.name==='Caixinha fictícia de viagem');
  if(existing)await page.getByText('Caixinha fictícia de viagem',{exact:true}).locator('..').getByRole('button',{name:'Atualizar posição'}).click();
  else await page.getByRole('button',{name:'Cadastrar investimento',exact:true}).click();
  const position=page.getByRole('dialog',{name:existing?'Atualizar posição':'Cadastrar investimento',exact:true});
  await position.getByLabel('Nome do investimento').fill('Caixinha fictícia de viagem');
  await position.getByLabel('Instituição',{exact:true}).fill('Nubank fictício');
  await position.getByLabel('Valor atual informado').fill('800');
  await position.getByRole('button',{name:'Salvar posição informativa'}).click();await expect(position).toHaveCount(0);
  await expect(page.getByText('Caixinha fictícia de viagem',{exact:true})).toBeVisible();
  await page.screenshot({path:info.outputPath('investments-local.png'),fullPage:true});
  const goalsBefore=(await get(page,'/goals')).length;
  await page.goto('/#/goals');
  async function send(message:string){await page.getByLabel('Converse sobre sua meta').fill(message);await page.getByRole('button',{name:'Enviar mensagem'}).click();await expect(page.getByLabel('Converse sobre sua meta')).toHaveValue('');}
  await send('Quero viajar e guardar 3000');await send('em 6 meses');await send('já tenho 600 reservado');
  await expect(page.getByRole('log')).toContainText('400.00 por mês');
  expect((await get(page,'/goals')).length).toBe(goalsBefore);
  await page.getByRole('button',{name:'Confirmar e salvar meta'}).click();
  await expect.poll(async()=>(await get(page,'/goals')).length).toBe(goalsBefore+1);
  await page.screenshot({path:info.outputPath('goals-local.png'),fullPage:true});
  const after=await get(page,report);
  expect(after.balance).toBe(baseline.balance);expect(after.cashFlow).toBe(baseline.cashFlow);
  expect((await get(page,'/movements?from=2026-11-01&to=2026-11-30')).total).toBe(movements.total);
  expect(external).toEqual([]);expect(errors).toEqual([]);
  await info.attach('organization-invariants.json',{body:JSON.stringify({personPix:true,unknownAbstains:true,manualNature:true,expenseExcluded:300,positionInformative:true,monthlyPlan:400,balancePreserved:true,cashFlowPreserved:true,movementCountPreserved:true,externalRequests:0,pageErrors:0}),contentType:'application/json'});
});
