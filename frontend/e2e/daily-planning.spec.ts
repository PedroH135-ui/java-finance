import {test,expect} from '@playwright/test';

test('Hoje mostra vencidos e permite simular um gasto adicional',async({page})=>{
  const requests:string[]=[];
  await page.route('**/api/**',async route=>{
    const req=route.request(),path=new URL(req.url()).pathname;
    requests.push(req.method()+' '+path);
    const fixtures:Record<string,unknown>={
      '/api/auth/csrf':{headerName:'X-Test-CSRF',token:'fixture'},
      '/api/auth/me':{id:'demo',name:'Perfil fictício',email:'demo@example.test'},
      '/api/reports/summary':{today:'2026-11-26',balance:'1000.00',accountCount:1,competence:{income:'0',expenses:'0',result:'0',savingsRate:null},cashFlow:'0',categories:[],evolution:[],upcoming:[]},
      '/api/accounts':[], '/api/budgets':{categories:[{categoryId:'delivery',name:'Delivery',remaining:'180',archived:false}]},
      '/api/goals/finances':{existingGoalContributions:'500',monthlyResult:'620'},
      '/api/budgets/commitments':{items:[{id:'one',dueDate:'2026-11-25',status:'EXPECTED',description:'Internet fictícia',amount:'99'},
        {id:'two',dueDate:'2026-11-26',status:'IDENTIFIED',description:'Conta já paga',amount:'49'}]},
    };
    if(path==='/api/goals/simulate'){
      expect(req.postDataJSON()).toEqual({amount:'150',categoryId:'delivery'});
      return route.fulfill({json:{remainingBudget:'30.00',afterGoals:'470.00'}});
    }
    return fixtures[path]?route.fulfill({json:fixtures[path]}):route.abort();
  });
  await page.goto('/#/');
  await expect(page.getByText(/Internet fictícia.*Vencido/)).toBeVisible();
  await expect(page.getByText('Conta já paga')).toHaveCount(0);
  await page.getByText('Simular uma compra',{exact:true}).click();
  await page.getByLabel('Valor da compra').fill('150');
  await page.getByLabel('Categoria da compra').click();
  await page.getByRole('option',{name:'Delivery'}).click();
  await page.getByRole('button',{name:'Simular',exact:true}).click();
  await expect(page.getByRole('status')).toContainText('R$ 470,00');
  expect(requests.filter(r=>!r.startsWith('GET'))).toEqual(['POST /api/goals/simulate']);
});
