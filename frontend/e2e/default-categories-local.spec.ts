import { test, expect, type Page } from '@playwright/test';

test.use({serviceWorkers:'block'});
test.skip(process.env.DEFAULT_CATEGORIES_LOCAL_E2E!=='1'||process.env.E2E_URL!=='http://127.0.0.1:8082',
  'Somente perfil fictício no Java local explicitamente habilitado.');

async function get(page:Page,path:string){
  return page.evaluate(async path=>{
    const response=await fetch('/api'+path);
    if(!response.ok)throw new Error('API local '+response.status);
    return response.json();
  },path);
}

test('botão com Java e PostgreSQL locais: idempotente, preserva escolhas, saldos e opt-in',async({page},info)=>{
  await page.route('**/*',async route=>{
    const url=new URL(route.request().url());
    if(url.protocol.startsWith('http')&&url.hostname!=='127.0.0.1'){await route.abort();return;}
    await route.continue();
  });
  await page.goto('/');
  await page.getByLabel('E-mail').fill('demo@javafinance.local');
  await page.getByLabel('Senha').fill('DemoLocal123!');
  await page.getByRole('button',{name:'Entrar',exact:true}).click();
  await expect(page.getByText(/Ambiente local de demonstração com dados fictícios/)).toBeVisible();
  expect((await get(page,'/auth/me')).email).toBe('demo@javafinance.local');
  const summary=await get(page,'/reports/summary?month=2026-11');
  const accounts=await get(page,'/accounts');
  const preference=await get(page,'/banking/category-automation');
  const before=await get(page,'/categories');
  await page.getByRole('link',{name:'Categorias',exact:true}).click();
  const button=page.getByRole('button',{name:'Adicionar categorias padrão',exact:true});
  await expect(button).toBeEnabled();await button.click();
  await expect(page.getByRole('status')).toContainText('Nenhuma categoria existente foi alterada.');
  const first=await get(page,'/categories');
  expect(first).toEqual(expect.arrayContaining(before));
  await button.click();
  await expect(page.getByRole('status')).toContainText('Todas as categorias padrão já estão presentes.');
  expect(await get(page,'/categories')).toEqual(first);
  expect(await get(page,'/accounts')).toEqual(accounts);
  expect(await get(page,'/reports/summary?month=2026-11')).toEqual(summary);
  expect(await get(page,'/banking/category-automation')).toEqual(preference);
  await page.screenshot({path:info.outputPath('categorias-padrao-java-local.png'),fullPage:true});
});
