import {useState} from 'react';
import {useQuery,useMutation} from '@tanstack/react-query';
import {Link} from 'react-router-dom';
import {Button,MenuItem,Stack,TextField,Typography} from '@mui/material';
import {Failure,Loading,MoneyInput,Panel} from '../../components/Shared';
import {api,json} from '../../lib/api';
import {bankDateTime,civilDate,money} from '../../lib/format';
import type {Account} from '../../lib/types';
import type {Upcoming} from '../budgets/UpcomingCommitments';
import type {BudgetMonth} from '../budgets/Budgets';
import type {PlanningContext} from '../goals/PlanningFinances';

export function DailyOverview({day}:{day:string}){
  const month=day.slice(0,7);
  const accounts=useQuery({queryKey:['accounts'],queryFn:()=>api<Account[]>('/accounts')});
  const commitments=useQuery({queryKey:['upcomingCommitments'],queryFn:()=>api<Upcoming>('/budgets/commitments'),refetchInterval:60000});
  const budget=useQuery({queryKey:['budgets',month],queryFn:()=>api<BudgetMonth>('/budgets?month='+month)});
  const plan=useQuery({queryKey:['planningFinances'],queryFn:()=>api<PlanningContext>('/goals/finances')});
  const [amount,setAmount]=useState(''),[category,setCategory]=useState('');
  const simulation=useMutation({mutationFn:()=>api<{afterGoals:string|null;remainingBudget:string|null}>('/goals/simulate',{
    method:'POST',body:json({categoryId:category,amount})})});
  const end=new Date(day+'T12:00:00Z');
  end.setUTCDate(end.getUTCDate()+6);
  const due=commitments.data?.items.filter(i=>i.dueDate<=end.toISOString().slice(0,10)&&i.status!=='IDENTIFIED');
  const categories=budget.data?.categories.filter(c=>!c.archived)??[];
  return <Panel title="Hoje" subtitle={civilDate(day)}>
    <Stack gap={1.5} mb={2}>
      {accounts.error&&<Failure error={accounts.error}/>}
      {accounts.data?.filter(a=>!a.archived&&a.balanceSource==='BANK').map(a=><Typography key={a.id}>
        <Link to="/accounts">{a.name}</Link> · {a.balance===null?'Saldo indisponível':money(a.balance)} · Banco atualizado em {bankDateTime(a.bankUpdatedAt??null)}
      </Typography>)}
      <Typography fontWeight={600}><Link to="/budgets">Compromissos vencidos e dos próximos sete dias</Link></Typography>
      {commitments.isPending?<Loading/>:commitments.error?<Failure error={commitments.error}/>:due?.length?due.map(i=><Typography key={i.id+i.dueDate}>
        {i.description} · {money(i.amount)} · {civilDate(i.dueDate)} · {i.status==='REVIEW'?'Conferir no extrato':i.dueDate<day?'Vencido':'Previsto'}
      </Typography>):<Typography>Nenhum compromisso do orçamento pendente nesse período.</Typography>}
      <Typography fontWeight={600}><Link to={'/budgets?month='+month}>Limites disponíveis neste mês</Link></Typography>
      {budget.error&&<Failure error={budget.error}/>}
      {categories.filter(c=>c.remaining!==null).map(c=><Typography key={c.categoryId}>{c.name} · {money(c.remaining!)}</Typography>)}
      {budget.data&&!categories.some(c=>c.remaining!==null)&&<Typography>Nenhum limite ativo configurado para este mês.</Typography>}
      {plan.error&&<Failure error={plan.error}/>}
      {plan.data&&<Typography><Link to="/goals">Planejamento mensal</Link> · Aportes previstos {money(plan.data.existingGoalContributions)} · Margem após gastos e metas {plan.data.monthlyResult===null?'indisponível até informar renda e gastos':money(plan.data.monthlyResult)}</Typography>}
      <details><summary>Simular uma compra</summary><Stack gap={1.5} mt={2}>
        <Typography>Simule um gasto adicional à previsão do mês atual.</Typography>
        <MoneyInput label="Valor da compra" value={amount} onChange={v=>{setAmount(v);simulation.reset();}}/>
        <TextField select label="Categoria da compra" value={category} onChange={e=>{setCategory(e.target.value);simulation.reset();}}>
          {categories.map(c=><MenuItem key={c.categoryId} value={c.categoryId}>{c.name}</MenuItem>)}
        </TextField>
        <Button variant="outlined" disabled={!amount||!category||simulation.isPending} onClick={()=>simulation.mutate()}>Simular</Button>
        {simulation.error&&<Failure error={simulation.error}/>}
        {simulation.data&&<Typography role="status">Limite da categoria após a compra {simulation.data.remainingBudget===null?'sem limite definido':money(simulation.data.remainingBudget)} · Margem após os aportes {simulation.data.afterGoals===null?'indisponível até completar o planejamento':money(simulation.data.afterGoals)}</Typography>}
      </Stack></details>
    </Stack>
  </Panel>;
}
