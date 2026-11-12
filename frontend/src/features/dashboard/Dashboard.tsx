import { Link, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Add, ArrowDownward, ArrowUpward, AccountBalanceWalletOutlined, TrendingUp } from '@mui/icons-material';
import { Alert, Box, Button, Divider, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material';
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { api } from '../../lib/api';
import type { Summary } from '../../lib/types';
import { civilDate, money, monthLabel, today } from '../../lib/format';
import { Empty, Failure, Loading, PageHeader, Panel } from '../../components/Shared';
import { BankOverview } from '../banking/BankOverview';
export function Dashboard() {
  const [params,setParams]=useSearchParams(),month=params.get('month')??today().slice(0,7);
  // Reads stored balances only; never triggers bank synchronization or classification.
  const query=useQuery({queryKey:['summary',month],queryFn:()=>api<Summary>('/reports/summary?month='+month),refetchInterval:60000});
  if(query.isPending)return <Loading/>;
  if(query.error)return <Failure error={query.error} retry={()=>void query.refetch()}/>;
  const d=query.data;
  const metrics=[
    {title:'Saldo das contas',value:d.balance,caption:'Saldo bancário nas vinculadas · calculado nas manuais',icon:<AccountBalanceWalletOutlined/>,color:'#174bbd',link:'/accounts'},
    {title:'Receitas do mês',value:d.competence.income,caption:'Receitas efetivadas · competência',icon:<ArrowUpward/>,color:'#16734f',link:'/movements?kind=INCOME&basis=competence&status=CONFIRMED&month='+month},
    {title:'Despesas do mês',value:d.competence.expenses,caption:'Despesas efetivadas · competência',icon:<ArrowDownward/>,color:'#b33440',link:'/movements?kind=EXPENSE&basis=competence&status=CONFIRMED&month='+month},
    {title:'Resultado do mês',value:d.competence.result,caption:'Receitas menos despesas · competência',icon:<TrendingUp/>,color:'#174bbd',link:'/movements?basis=competence&status=CONFIRMED&month='+month},
  ];
  // Number é usado somente para coordenadas dos gráficos. Os totais vêm prontos do backend.
  const chart=d.evolution.map(m=>({month:monthLabel(m.month),Receitas:m.records?Number(m.income):null,Despesas:m.records?Number(m.expenses):null}));
  return <>
    <PageHeader title="Seu mês, com clareza" subtitle="Uma visão do presente para decidir os próximos passos." action={<Button component={Link} to="/movements?new=1" variant="contained" startIcon={<Add/>}>Nova movimentação</Button>}/>
    <Stack direction={{xs:'column',sm:'row'}} justifyContent="space-between" alignItems={{sm:'center'}} gap={2} mb={3}>
      <Box><Typography variant="overline" color="primary">VISÃO GERAL</Typography><Typography variant="body2" color="text.secondary">BRL · Saldos das contas e registros revisados</Typography></Box>
      <TextField label="Mês de competência" type="month" value={month} onChange={e=>{if(e.target.value)setParams({month:e.target.value});}} slotProps={{inputLabel:{shrink:true}}} sx={{width:{xs:'100%',sm:220}}}/>
    </Stack>
    {d.accountCount===0&&<BankOverview/>}
    {d.balanceComplete===false&&<Alert severity="warning" sx={{mb:3}}>Há uma conta vinculada sem saldo bancário disponível. O total fica indisponível até receber esse saldo, sem substituí-lo por um valor calculado.</Alert>}
    {d.accountCount===0&&<Alert severity="info" sx={{mb:3}} action={<Button component={Link} to="/accounts">Cadastrar conta</Button>}>Seu espaço está pronto. Comece cadastrando uma conta com o saldo que você já possui.</Alert>}
    <Box sx={{display:'grid',gridTemplateColumns:{xs:'1fr',sm:'repeat(2,minmax(0,1fr))',xl:'repeat(4,minmax(0,1fr))'},gap:2,mb:3}}>
      {metrics.map(m=><Paper key={m.title} variant="outlined" sx={{p:2.5,minWidth:0}}>
        <Stack direction="row" justifyContent="space-between" mb={2}><Typography fontWeight={600} variant="body2">{m.title}</Typography><Box sx={{color:m.color}}>{m.icon}</Box></Stack>
        <Typography component={Link} to={m.link} className="money" sx={{display:'block',color:'text.primary',textDecoration:'none',fontSize:{xs:'1.35rem',sm:'1.55rem'},fontWeight:750,letterSpacing:'-0.04em'}}>{m.value==null?'Não disponível':money(m.value)}</Typography>
        <Typography variant="caption" color="text.secondary" sx={{display:'block',mt:1}}>{m.caption}</Typography>
      </Paper>)}
    </Box>
    <Box sx={{display:'grid',gridTemplateColumns:{xs:'1fr',lg:'minmax(0,1.6fr) minmax(0,1fr)'},gap:3,mb:3}}>
      <Panel title="Receitas e despesas" subtitle="Últimos seis meses · valores por competência">
        {d.evolution.every(m=>m.records===0)?<Empty title="Seu histórico começa com o primeiro registro">Cadastre receitas e despesas para acompanhar sua evolução. Não há histórico anterior informado.</Empty>:
          <><Box sx={{height:260,minWidth:0}} aria-label="Gráfico de receitas e despesas por competência">
            <ResponsiveContainer width="100%" height="100%"><BarChart data={chart} margin={{left:0,right:8,top:10,bottom:0}}><CartesianGrid vertical={false} stroke="#e9edf5"/>
              <XAxis dataKey="month" tick={{fontSize:11}} axisLine={false} tickLine={false}/><YAxis width={52} tick={{fontSize:11}} tickFormatter={v=>new Intl.NumberFormat('pt-BR',{notation:'compact'}).format(v)}/>
              <Tooltip formatter={v=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(Number(v))}/><Legend/>
              <Bar dataKey="Receitas" fill="#174bbd" radius={[4,4,0,0]} isAnimationActive={false}/><Bar dataKey="Despesas" fill="#8e9daf" radius={[4,4,0,0]} isAnimationActive={false}/>
            </BarChart></ResponsiveContainer>
          </Box><Box component="details" mt={2}><Box component="summary" sx={{cursor:'pointer',color:'primary.main',py:1}}>Consultar valores do gráfico</Box>
            <Box sx={{overflowX:'auto'}}><Table size="small" aria-label="Valores por competência"><TableHead><TableRow><TableCell>Mês</TableCell><TableCell align="right">Receitas</TableCell><TableCell align="right">Despesas</TableCell></TableRow></TableHead><TableBody>{d.evolution.map(m=><TableRow key={m.month}><TableCell>{monthLabel(m.month)}</TableCell><TableCell align="right">{m.records?money(m.income):'Sem registros'}</TableCell><TableCell align="right">{m.records?money(m.expenses):'Sem registros'}</TableCell></TableRow>)}</TableBody></Table></Box>
          </Box></>}
      </Panel>
      <Panel title="Gastos por categoria" subtitle={monthLabel(month)+' · despesas efetivadas'}>
        {d.categories.length===0?<Empty title="Sem despesas neste mês">Suas categorias aparecerão aqui conforme você registrar gastos.</Empty>:
          <Stack divider={<Divider/>}>{d.categories.map(c=><Stack key={c.id} direction="row" justifyContent="space-between" gap={2} py={1.5}>
            <Typography component={Link} to={'/movements?category='+c.id+'&basis=competence&status=CONFIRMED&kind=EXPENSE&month='+month} variant="body2" sx={{overflowWrap:'anywhere'}}>{c.name}</Typography><Typography className="money" variant="body2" fontWeight={650}>{money(c.total)}</Typography>
          </Stack>)}</Stack>}
      </Panel>
    </Box>
    <Box sx={{display:'grid',gridTemplateColumns:{xs:'1fr',lg:'minmax(0,1.6fr) minmax(0,1fr)'},gap:3}}>
      <Panel title="Próximos pagamentos e recebimentos" subtitle="Previsões em aberto · ordenadas pelo vencimento">
        {d.upcoming.length===0?<Empty title="Nenhum compromisso previsto">Registre uma movimentação como prevista para acompanhar o vencimento aqui.</Empty>:
          <Stack divider={<Divider/>}>{d.upcoming.map(m=><Stack key={m.id} direction={{xs:'column',sm:'row'}} gap={1} py={1.5} justifyContent="space-between">
            <Box><Typography component={Link} to={'/movements?detail='+m.id} fontWeight={600} sx={{overflowWrap:'anywhere'}}>{m.description}</Typography><Typography variant="body2" color={m.dueDate<d.today?'error':'text.secondary'}>{m.dueDate<d.today?'Atrasado · ':''}{civilDate(m.dueDate)}</Typography></Box>
            <Typography className="money">{money(m.amount)}</Typography>
          </Stack>)}</Stack>}
      </Panel>
      <Panel title="Entenda seu resultado" subtitle="Cada indicador tem uma base diferente">
        <Typography variant="body2" color="text.secondary">Taxa de economia</Typography>
        <Typography variant="h4" mt={1}>{d.competence.savingsRate===null?'Indisponível':d.competence.savingsRate.replace('.',',')+'%'}</Typography>
        <Typography variant="body2" color="text.secondary" mt={1}>{d.competence.savingsRate===null?'É preciso ter receita positiva no período para calcular a taxa.':'Resultado dividido pelas receitas elegíveis do mês. Pode ser negativo.'}</Typography>
        <Divider sx={{my:2}}/><Typography variant="body2" fontWeight={600}>Fluxo de caixa do mês: {money(d.cashFlow)}</Typography>
        <Typography variant="body2" color="text.secondary" mt={1}>Considera a data de efetivação. Exclui saldo inicial e transferências próprias; inclui ajustes de saldo.</Typography>
        <Typography variant="caption" color="text.secondary" sx={{display:'block',mt:2}}>O saldo não representa seu patrimônio líquido. Cartões, investimentos e dívidas ainda não fazem parte desta etapa.</Typography>
      </Panel>
    </Box>
  </>;
}

