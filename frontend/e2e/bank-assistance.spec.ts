import { test, expect, type Page } from '@playwright/test';

async function setup(page:Page,method='HISTORY',conflict=false) {
  let categoryId:string|null=null,paired=false,version=0;
  const writes:string[]=[];
  await page.route('**/api/**',async route=>{
    const request=route.request(),path=new URL(request.url()).pathname;
    if(request.method()!=='GET') {
      writes.push(path);
      const input=request.postDataJSON();
      if(conflict){await route.fulfill({status:409,json:{message:'O registro mudou. Feche os detalhes e consulte novamente.'}});return;}
      if(path.endsWith('/category')){expect(input.version).toBe(version);categoryId=input.categoryId;}
      else if(path.endsWith('/transfer/undo')){expect(input.version).toBe(0);paired=false;}
      else if(path.endsWith('/transfer')){expect(input).toMatchObject({version,peerId:'peer-b',peerVersion:0});paired=true;}
      else throw new Error('Unexpected mutation: '+path);
      version++;await route.fulfill({status:204});return;
    }
    let body:unknown;
    if(path==='/api/auth/me')body={id:'profile-test',name:'Perfil de teste',email:'test@example.test'};
    else if(path==='/api/auth/csrf')body={headerName:'X-CSRF-TOKEN',token:'synthetic-csrf'};
    else if(path==='/api/accounts')body=[{id:'account-a',name:'Banco A fictício',kind:'CHECKING',balance:'950.00',balanceSource:'BANK'}];
    else if(path==='/api/categories')body=[{id:'food',name:'Alimentação',kind:'EXPENSE',archived:false},{id:'other',name:'Outros gastos',kind:'EXPENSE',archived:false}];
    else if(path==='/api/movements/feed')body={items:[{id:'entry-a',description:'Mercado Alfa fictício',amount:'50.00',kind:paired?'TRANSFER':'EXPENSE',status:'CONFIRMED',source:'BANK',accountId:'account-a',categoryId,
      operationDate:'2026-11-11',competence:'2026-11-01',bankEntryId:'entry-a',bankLinkId:'link-a',bankEntryVersion:version,bankProviderStatus:'POSTED',bankState:'REVIEW',hidden:false}],total:1,pages:1,page:0,size:20};
    else if(path==='/api/banking/entries/entry-a/assistance')body={entryVersion:version,currentCategoryId:categoryId,canCategorize:!paired,unavailableReason:paired?'Identificado como transferência.':'',
      categorySuggestion:{categoryId:'food',method:method==='CLOUD'?'LLM':method,provider:method==='CLOUD'?'groq':'ollama',confidence:method==='HISTORY'?'HIGH':'LOW',reason:'Sugestão baseada em classificações anteriores semelhantes deste perfil.',evidenceCount:3,modelUsed:method!=='HISTORY'},
      transferCandidates:paired?[]:[{id:'peer-b',accountName:'Banco B fictício',description:'Entrada em outra conta',amount:'50.00',date:'2026-11-12',version:0},{id:'peer-c',accountName:'Banco C fictício',description:'Outro par possível',amount:'50.00',date:'2026-11-11',version:0}],
      transfer:paired?{id:'transfer-test',peerId:'peer-b',version:0,valid:true}:null};
    else throw new Error('Unexpected endpoint: '+path);
    await route.fulfill({json:body});
  });
  await page.goto('/#/movements?month=2026-11');
  await page.getByRole('button',{name:'Detalhes de Mercado Alfa fictício',exact:true}).click();
  return {writes,state:()=>({categoryId,paired,version})};
}

for(const method of ['HISTORY','LLM','CLOUD']) {
  test(`categoria ${method}: proposta explicável pode ser corrigida antes de aplicar`,async({page},info)=>{
    const {writes,state}=await setup(page,method);
    const dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
    await expect(dialog.getByText(method==='HISTORY'?'Seu histórico':method==='CLOUD'?'Modelo na nuvem':'Modelo local',{exact:true})).toBeVisible();
    await expect(dialog.getByLabel('Categoria para este registro')).toContainText('Alimentação');
    expect(writes).toHaveLength(0);expect(state().categoryId).toBeNull();
    await page.setViewportSize({width:390,height:1000});
    await dialog.getByLabel('Categoria para este registro').scrollIntoViewIfNeeded();
    await page.screenshot({path:info.outputPath(`categoria-${method}-390.png`),fullPage:false,animations:'disabled'});
    await dialog.getByRole('combobox',{name:/Categoria para este registro/}).click();
    await page.getByRole('option',{name:'Outros gastos',exact:true}).click();
    await dialog.getByRole('button',{name:'Aplicar categoria escolhida',exact:true}).click();
    await expect(dialog).toHaveCount(0);expect(state().categoryId).toBe('other');
    expect(writes).toEqual(['/api/banking/entries/entry-a/category']);
  });
}

test('transferência ambígua exige escolha e pode ser desfeita',async({page},info)=>{
  const {writes,state}=await setup(page);
  const dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
  await expect(dialog.getByText(/Há mais de um par possível/)).toBeVisible();
  await expect(dialog.getByRole('button',{name:'Confirmar transferência entre minhas contas',exact:true})).toBeDisabled();
  expect(writes).toHaveLength(0);
  await page.setViewportSize({width:1440,height:1000});
  await dialog.getByRole('combobox',{name:/Operação na outra conta/}).click();
  await page.getByRole('option',{name:/Banco B fictício/}).click();
  await page.screenshot({path:info.outputPath('transferencia-sugerida-1440.png'),fullPage:false,animations:'disabled'});
  await dialog.getByRole('button',{name:'Confirmar transferência entre minhas contas',exact:true}).click();
  await expect(dialog).toHaveCount(0);expect(state().paired).toBe(true);
  await page.getByRole('button',{name:'Detalhes de Mercado Alfa fictício',exact:true}).click();
  await expect(dialog.getByText(/Os dois registros ficam fora de renda e gastos/)).toBeVisible();
  await dialog.getByRole('button',{name:'Desfazer identificação de transferência',exact:true}).click();
  await expect(dialog).toHaveCount(0);expect(state().paired).toBe(false);
  expect(writes).toEqual(['/api/banking/entries/entry-a/transfer','/api/banking/entries/entry-a/transfer/undo']);
});

test('versão desatualizada preserva a escolha sem aplicar silenciosamente',async({page})=>{
  const {writes,state}=await setup(page,'HISTORY',true);
  const dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
  await dialog.getByRole('button',{name:'Aplicar categoria escolhida',exact:true}).click();
  await expect(dialog.getByText(/O registro mudou/)).toBeVisible();expect(state().categoryId).toBeNull();expect(writes).toHaveLength(1);
});
