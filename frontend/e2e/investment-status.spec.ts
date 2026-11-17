import {test,expect} from '@playwright/test';
test.use({serviceWorkers:'block'});
test.skip(process.env.E2E_URL!=='http://127.0.0.1:4174','Fictitious local preview only');
test('fatura parcial mantém conciliação bloqueada mesmo com pagamento e total compatíveis',async({page})=>{
 const writes:string[]=[];
 await page.route('**/api/**',async route=>{
  const req=route.request(),path=new URL(req.url()).pathname;
  if(req.method()!=='GET'){writes.push(req.method());await route.abort();return;}
  let body:unknown;
  if(path==='/api/auth/me')body={id:'fictional',name:'Fictício',email:'demo@example.test'};
  else if(path==='/api/banking/config')body={ready:true};
  else if(path==='/api/banking/links')body=[];
  else if(path==='/api/banking/products')body={connections:[],positions:[],payments:[{id:'payment',description:'Pagamento fictício',amount:'500.00',date:'2026-11-17',version:0}],cards:[{id:'card',name:'Cartão fictício',checkedAt:'2026-11-17T12:00:00Z',billsAvailable:false,charges:[],bills:[{id:'bill',externalId:'bill',dueDate:'2026-11-17',total:'500.00',detailedTotal:'500.00',detailCount:1,pendingCount:0,revision:'123',reconciled:false}]}]};
  else throw new Error('Unexpected local API '+path);
  await route.fulfill({json:body});
 });
 await page.goto('/#/banking');
 await expect(page.getByText(/Não foi possível validar todas as faturas desta leitura/)).toBeVisible();
 await page.getByRole('button',{name:'Conferir fatura e pagamento',exact:true}).click();
 await page.getByRole('combobox',{name:/Pagamento bancário correspondente/}).click();
 await page.getByRole('option',{name:/Pagamento fictício/}).click();
 await expect(page.getByRole('checkbox',{name:/Conferi os detalhes/})).toBeDisabled();
 await expect(page.getByRole('button',{name:'Confirmar conciliação da fatura',exact:true})).toBeDisabled();
 expect(writes).toEqual([]);
});
test('ativos por padrão, resgate total identificado e filtros sem gravações',async({page})=>{
 const writes:string[]=[],external:string[]=[];
 await page.route('**/*',async route=>{
  const req=route.request(),u=new URL(req.url());
  if(u.hostname!=='127.0.0.1'){external.push(u.hostname);await route.abort();return;}
  if(!u.pathname.startsWith('/api/')){await route.continue();return;}
  if(req.method()!=='GET'){writes.push(req.method());await route.abort();return;}
  let body:unknown;
  if(u.pathname==='/api/auth/me')body={id:'fictional',name:'Demonstração fictícia',email:'demo@example.test'};
  else if(u.pathname==='/api/banking/products')body={connections:[],cards:[],payments:[],positions:['ACTIVE','TOTAL_WITHDRAWAL','PENDING'].map((status,i)=>({id:String(i),name:['CDB ativo fictício','CDB resgatado fictício','CDB pendente fictício'][i],type:'FIXED_INCOME',subtype:'CDB',balance:'100.00',date:'2026-11-17',profit:null,available:null,status,checkedAt:'2026-11-17T12:00:00Z'}))};
  else if(u.pathname==='/api/investments')body={positions:[],flows:[],suggestions:[],totalFlows:0,contributions:'0.00',withdrawals:'0.00',netContributions:'0.00'};
  else throw new Error('Unexpected local API '+u.pathname);
  await route.fulfill({json:body});
 });
 await page.goto('/#/investments');
 await expect(page.getByText('CDB ativo fictício',{exact:true})).toBeVisible();
 await expect(page.getByText('CDB resgatado fictício',{exact:true})).toHaveCount(0);
 await expect(page.getByText('CDB pendente fictício',{exact:true})).toHaveCount(0);
 await page.getByRole('combobox',{name:/^Situação das posições do banco/}).click();await page.getByRole('option',{name:'Resgatados',exact:true}).click();
 await expect(page.getByText('CDB resgatado fictício',{exact:true})).toBeVisible();await expect(page.getByText(/Resgatado totalmente/)).toBeVisible();
 await expect(page.getByText('CDB ativo fictício',{exact:true})).toHaveCount(0);
 await page.getByRole('combobox',{name:/^Situação das posições do banco/}).click();await page.getByRole('option',{name:'Todos (inclui pendentes)',exact:true}).click();
 await expect(page.getByText('CDB pendente fictício',{exact:true})).toBeVisible();await expect(page.getByText('CDB ativo fictício',{exact:true})).toBeVisible();
 expect(writes).toEqual([]);expect(external).toEqual([]);
});
