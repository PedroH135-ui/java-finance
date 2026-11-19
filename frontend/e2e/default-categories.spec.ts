import { test, expect } from '@playwright/test';

test.skip(!/^http:\/\/127\.0\.0\.1:\d+$/.test(process.env.E2E_URL??''),'Somente servidor local com API fictícia interceptada.');
test.use({serviceWorkers:'block'});

for(const status of [401,403]){
  test(`categorias padrão: sessão/CSRF ${status} sem repetição automática`,async({page})=>{
    let expired=false,writes=0;
    await page.route('**/*',async route=>{
      const request=route.request(),url=new URL(request.url());
      if(url.hostname!=='127.0.0.1'){await route.abort();return;}
      if(!url.pathname.startsWith('/api/')){await route.continue();return;}
      if(url.pathname==='/api/auth/me'){
        await route.fulfill(expired?{status:401,json:{message:'Entre novamente.'}}:{json:{id:'owner-test',name:'Fictício',email:'demo@example.test'}});return;
      }
      if(url.pathname==='/api/auth/config'){await route.fulfill({json:{registrationEnabled:false,hosting:'local'}});return;}
      if(url.pathname==='/api/categories'){await route.fulfill({json:[]});return;}
      if(url.pathname==='/api/auth/csrf'){
        await route.fulfill(status===403?{status:403,json:{message:'Ação recusada.'}}:{json:{token:'fictitious-test-token',headerName:'X-CSRF-TOKEN'}});return;
      }
      expect(url.pathname).toBe('/api/categories/defaults');writes++;expired=true;
      await route.fulfill({status:401,json:{message:'Entre na sua conta para continuar.'}});
    });
    await page.goto('/#/categories');
    const add=page.getByRole('button',{name:'Adicionar categorias padrão',exact:true});
    await expect(add).toBeEnabled();await add.click();
    if(status===401){
      await expect(page.getByRole('alert').filter({hasText:'Sua sessão expirou.'})).toBeVisible();
      await expect(add).toBeDisabled();expect(writes).toBe(1);
      await page.getByRole('button',{name:'Entrar novamente',exact:true}).click();
      await expect(page.getByLabel('E-mail')).toBeVisible();
      expect(writes).toBe(1);
    }else{
      await expect(page.getByText('Não foi possível validar esta ação. Atualize a página e tente novamente.',{exact:true})).toBeVisible();
      expect(writes).toBe(0);
    }
  });
}

for(const scenario of ['empty','existing','retry'] as const){
  test(`categorias padrão: ${scenario}`,async({page},info)=>{
    const market={id:'market-test',name:'mercado',kind:'EXPENSE',essential:true,archived:true,version:3};
    const salary={id:'salary-test',name:'Salário',kind:'INCOME',essential:false,archived:false,version:0};
    let categories=scenario==='empty'?[]:[market];
    let attempts=0;
    const writes:string[]=[];
    await page.route('**/*',async route=>{
      const request=route.request(),url=new URL(request.url());
      if(url.hostname!=='127.0.0.1'){await route.abort();return;}
      if(!url.pathname.startsWith('/api/')){await route.continue();return;}
      if(request.method()!=='GET'){
        writes.push(url.pathname);expect(url.pathname).toBe('/api/categories/defaults');expect(request.method()).toBe('POST');
        attempts++;
        if(scenario==='retry'&&attempts===1){await route.fulfill({status:503,json:{message:'Falha fictícia'}});return;}
        const added=categories.some(c=>c.id===salary.id)?0:1;
        if(added)categories=[...categories,salary];
        await route.fulfill({json:{added,existing:1-added}});return;
      }
      if(url.pathname==='/api/auth/csrf')await route.fulfill({json:{token:'fictitious-test-token',headerName:'X-CSRF-TOKEN'}});
      else if(url.pathname==='/api/auth/me')await route.fulfill({json:{id:'owner-test',name:'Perfil fictício',email:'demo@example.test'}});
      else if(url.pathname==='/api/categories')await route.fulfill({json:categories});
      else throw new Error('Endpoint não previsto: '+url.pathname);
    });
    await page.goto('/#/categories');
    const button=page.getByRole('button',{name:'Adicionar categorias padrão',exact:true});
    await expect(button).toBeEnabled();
    await button.click();
    if(scenario==='retry'){
      await expect(page.getByText(/Não foi possível adicionar/)).toBeVisible();
      expect(categories).toEqual([market]);
      await button.click();
    }
    await expect(page.getByRole('status')).toContainText('1 categoria(s) adicionada(s)');
    await expect(page.getByText('Salário',{exact:true})).toBeVisible();
    await button.click();
    await expect(page.getByRole('status')).toContainText('Todas as categorias padrão já estão presentes');
    if(scenario!=='empty'){
      expect(categories.find(c=>c.id===market.id)).toEqual(market);
      await expect(page.getByText('Arquivada',{exact:true})).toBeVisible();
    }
    expect(writes).toEqual(Array(scenario==='retry'?3:2).fill('/api/categories/defaults'));
    await page.setViewportSize({width:390,height:950});
    await page.screenshot({path:info.outputPath('categorias-padrao-local.png'),fullPage:true});
  });
}
