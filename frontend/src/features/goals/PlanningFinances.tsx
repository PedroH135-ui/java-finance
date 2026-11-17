import { useState } from 'react';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { Alert,Box,Button,Paper,Stack,Table,TableBody,TableCell,TableContainer,TableHead,TableRow,Typography } from '@mui/material';
import { api,json } from '../../lib/api';
import { money,monthLabel } from '../../lib/format';
import { Failure,Loading,MoneyInput } from '../../components/Shared';

interface Month {month:string;income:string;salary:string;consumption:string;records:number;uncategorized:number}
export interface PlanningContext {
  profile:{netSalary:string|null;monthlyExpenses:string|null;version:number;saved:boolean};
  currentMonth:string;currentIncome:string;currentSalary:string;currentConsumption:string;
  history:Month[];observedMonths:number;averageIncome:string|null;averageSalary:string|null;averageConsumption:string|null;
  monthlySalary:string|null;salarySource:string;monthlyExpenses:string|null;expenseSource:string;
  existingGoalContributions:string;monthlyResult:string|null;available:string|null;warnings:string[];
}
export interface Assessment {requiredMonthly:string;additionalSavingNeeded:string|null;maximumMonthlyConsumption:string|null;minimumMonths:string|null;canAssess:boolean;fitsEstimate:boolean}
const sources:Record<string,string>={USER:'informado por você',HISTORY:'estimado do histórico',UNKNOWN:'não identificado'};
const amount=(value:string|null)=>value===null?'Não informado':money(value);

export function PlanningFinances({assessment,onChanged}:{assessment:Assessment|null;onChanged:()=>void}){
  const client=useQueryClient();
  const query=useQuery({queryKey:['planningFinances'],queryFn:()=>api<PlanningContext>('/goals/finances')});
  const [salary,setSalary]=useState<string>(),[expenses,setExpenses]=useState<string>(),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
  if(query.isPending)return <Loading/>;
  if(query.error)return <Failure error={query.error}/>;
  const data=query.data;
  async function save(){
    setBusy(true);setError('');setNotice('');
    try{
      await api('/goals/finances',{method:'PUT',body:json({netSalary:salary??data.profile.netSalary,monthlyExpenses:expenses??data.profile.monthlyExpenses,version:data.profile.version})});
      await client.invalidateQueries({queryKey:['planningFinances']});setSalary(undefined);setExpenses(undefined);onChanged();
      setNotice('Previsões salvas. Recalcule sua meta enviando uma nova mensagem. Nenhuma receita, despesa ou saldo foi alterado.');
    }catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  return <Paper variant="outlined" sx={{p:2,mb:3}}><Stack spacing={2}>
    <Typography variant="h6" component="h2">Sua renda e seus gastos no planejamento</Typography>
    <Typography>Entradas registradas incluem outras receitas. Apenas a categoria Salário identifica salário; você pode informar o valor líquido esperado para melhorar a previsão.</Typography>
    {error&&<Alert severity="error">{error}</Alert>}{notice&&<Alert severity="success">{notice}</Alert>}
    <Box component="fieldset" disabled={busy} sx={{border:0,p:0,m:0}}><Stack spacing={2}>
      <MoneyInput label="Salário líquido mensal previsto" value={salary??data.profile.netSalary??''} onChange={setSalary} helperText="Deixe vazio para usar a média identificada nos meses anteriores."/>
      <MoneyInput label="Gastos mensais previstos (opcional)" value={expenses??data.profile.monthlyExpenses??''} onChange={setExpenses} helperText="Deixe vazio para usar o histórico. Compromissos maiores do mês continuam sendo considerados."/>
      <Button variant="outlined" disabled={busy} onClick={()=>void save()}>Salvar previsões do planejamento</Button>
    </Stack></Box>
    <Typography>Salário mensal considerado: <strong>{amount(data.monthlySalary)}</strong> · {sources[data.salarySource]}</Typography>
    <Typography>Gastos mensais considerados: <strong>{amount(data.monthlyExpenses)}</strong> · base {sources[data.expenseSource]}, ajustada aos compromissos do mês</Typography>
    <Typography>Aportes mensais de metas já salvas: {money(data.existingGoalContributions)}</Typography>
    <Typography>Resultado mensal estimado após gastos e metas: {amount(data.monthlyResult)}</Typography>
    <Typography fontWeight={650}>Capacidade estimada para uma nova meta: {amount(data.available)}</Typography>
    <Typography>Em {monthLabel(data.currentMonth)}: entradas registradas {money(data.currentIncome)}, salário categorizado {money(data.currentSalary)} e consumo registrado {money(data.currentConsumption)}.</Typography>
    <Typography variant="subtitle1" component="h3">Histórico dos três meses anteriores</Typography>
    {data.history.length===0?<Typography>Não há meses anteriores com registros. Informe as previsões para começar; não vamos presumir gastos zero.</Typography>:<>
      <Typography>Médias dos {data.observedMonths} meses com dados: entradas {amount(data.averageIncome)}, salário categorizado {amount(data.averageSalary)}, consumo {amount(data.averageConsumption)}. O mês atual ainda não entra nessa média.</Typography>
      <TableContainer><Table size="small" aria-label="Histórico mensal do planejamento"><TableHead><TableRow><TableCell>Mês</TableCell><TableCell>Entradas</TableCell><TableCell>Salário</TableCell><TableCell>Consumo</TableCell></TableRow></TableHead><TableBody>{data.history.map(m=><TableRow key={m.month}><TableCell>{monthLabel(m.month)}</TableCell><TableCell>{money(m.income)}</TableCell><TableCell>{money(m.salary)}</TableCell><TableCell>{money(m.consumption)}</TableCell></TableRow>)}</TableBody></Table></TableContainer>
    </>}
    {assessment&&<Alert severity={assessment.canAssess?(assessment.fitsEstimate?'success':'warning'):'info'}>
      Aporte necessário: {money(assessment.requiredMonthly)} por mês. {assessment.canAssess?<>
        Falta economizar adicionalmente: {amount(assessment.additionalSavingNeeded)} por mês. Para esse prazo, o consumo mensal precisa ficar até {amount(assessment.maximumMonthlyConsumption)} após considerar as outras metas.
        {assessment.minimumMonths!==null&&<> Prazo mínimo estimado com a capacidade atual: {assessment.minimumMonths} meses.</>}
      </>:'Complete salário e gastos mensais para comparar o plano com sua capacidade.'}
    </Alert>}
    {data.warnings.map(w=><Typography key={w} variant="body2" color="text.secondary">{w}</Typography>)}
  </Stack></Paper>;
}
