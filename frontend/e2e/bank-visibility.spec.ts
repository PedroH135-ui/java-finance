import { test, expect } from '@playwright/test';

for (const fails of [false,true]) {
  test(`ocultar registro: ${fails?'falha preserva registro':'retirada e restauração sem importar ou alterar saldo'}`, async ({page},info)=>{
    let hidden=false,version=0;
    const writes:string[]=[];
    await page.route('**/api/**',async route=>{
      const request=route.request(),url=new URL(request.url()),path=url.pathname;
      if(request.method()!=='GET') {
        writes.push(path);
        expect(path).toBe('/api/banking/entries/entry-test/visibility');
        const input=request.postDataJSON();expect(input.version).toBe(version);
        if(fails){await route.fulfill({status:503,json:{message:'Não foi possível ocultar agora.'}});return;}
        hidden=input.hidden;version++;
        await route.fulfill({status:204});return;
      }
      let body:unknown;
      if(path==='/api/auth/me')body={id:'profile-test',name:'Perfil de teste',email:'test@example.test'};
      else if(path==='/api/auth/csrf')body={headerName:'X-CSRF-TOKEN',token:'synthetic-csrf'};
      else if(path==='/api/accounts')body=[{id:'account-test',name:'Nubank fictício',kind:'CHECKING',balance:'950.25',balanceSource:'BANK'}];
      else if(path==='/api/categories')body=[];
      else if(path==='/api/banking/entries/entry-test/assistance')body={entryVersion:version,currentCategoryId:null,canCategorize:false,unavailableReason:'Nenhuma categoria configurada.',categorySuggestion:{categoryId:null,confidence:'NONE',method:'NONE',reason:'Sem evidência.',evidenceCount:0,modelUsed:false},transferCandidates:[],transfer:null};
      else if(path==='/api/movements/feed') {
        const visible=url.searchParams.get('hidden')===String(hidden);
        body={items:visible?[{id:'entry-test',description:'Mercado fictício',amount:'50.25',kind:'EXPENSE',status:'CONFIRMED',source:'BANK',
          accountId:'account-test',categoryId:null,operationDate:'2026-11-11',competence:'2026-11-01',bankEntryId:'entry-test',bankLinkId:'link-test',
          bankEntryVersion:version,bankProviderStatus:'POSTED',bankState:'REVIEW',hidden}]:[],total:visible?1:0,pages:visible?1:0,page:0,size:20};
      } else throw new Error('Unexpected simulated endpoint: '+path);
      await route.fulfill({json:body});
    });
    await page.goto('/#/movements?month=2026-11');
    await page.getByRole('button',{name:'Detalhes de Mercado fictício',exact:true}).click();
    const dialog=page.getByRole('dialog',{name:'Registro bancário',exact:true});
    await expect(dialog.getByText(/O saldo informado pelo banco permanece igual/)).toBeVisible();
    await dialog.getByLabel('Motivo (opcional)').fill('Registro que não deve entrar nos relatórios');
    await page.setViewportSize({width:390,height:1000});
    await page.screenshot({path:info.outputPath('ocultar-registro-390.png'),fullPage:false});
    await dialog.getByRole('button',{name:'Ocultar da lista e dos relatórios',exact:true}).click();
    if(fails){
      await expect(dialog.getByText('Não foi possível ocultar agora.',{exact:true})).toBeVisible();
      expect(hidden).toBe(false);expect(version).toBe(0);return;
    }
    await expect(dialog).toHaveCount(0);
    await expect(page.getByText('Nenhuma movimentação neste período',{exact:true})).toBeVisible();
    await page.getByRole('tab',{name:'Ocultas',exact:true}).click();
    await page.setViewportSize({width:1440,height:1000});
    await page.getByRole('button',{name:'Detalhes de Mercado fictício',exact:true}).click();
    await dialog.getByRole('button',{name:'Restaurar registro',exact:true}).click();
    await expect(dialog).toHaveCount(0);
    await page.getByRole('tab',{name:'Todas as movimentações',exact:true}).click();
    await expect(page.getByRole('table',{name:'Movimentações',exact:true}).getByText('Mercado fictício',{exact:true})).toBeVisible();
    expect(hidden).toBe(false);expect(version).toBe(2);
    expect(writes).toEqual(['/api/banking/entries/entry-test/visibility','/api/banking/entries/entry-test/visibility']);
  });
}
