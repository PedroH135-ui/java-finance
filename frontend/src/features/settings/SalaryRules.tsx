import { useState } from 'react';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { Alert,Button,Paper,Stack,Typography } from '@mui/material';
import { api,json } from '../../lib/api';
import { refreshFinances } from '../../lib/financialQueries';
import { Failure,Loading } from '../../components/Shared';
interface Rule {id:string;description:string;account:string;active:boolean;categoryActive:boolean;version:number;blockedByCorrection:boolean}
export function SalaryRules(){
 const client=useQueryClient(),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 const query=useQuery({queryKey:['salaryRules'],queryFn:({signal})=>api<Rule[]>('/banking/salary-rules',{signal})});
 async function toggle(rule:Rule){setBusy(true);setError('');try{await api('/banking/salary-rules/'+rule.id,{method:'PUT',body:json({version:rule.version,active:!rule.active})});await refreshFinances(client);}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 return <Paper variant="outlined" sx={{p:2,mt:3}}><Stack spacing={2}>
  <Typography variant="h6" component="h2">Regras de salário</Typography>
  <Typography>Criadas por você nos detalhes de um recebimento. Não alteram o salário previsto e não dependem da IA. Pausar afeta somente próximos recebimentos; correções manuais têm prioridade.</Typography>
  {error&&<Alert severity="error">{error}</Alert>}
  {query.isPending?<Loading/>:query.error?<Failure error={query.error}/>:query.data.length===0?<Typography>Nenhuma regra criada. Em Movimentações, abra um recebimento e escolha “É meu salário”.</Typography>:query.data.map(rule=><Paper key={rule.id} variant="outlined" sx={{p:2}}>
   <Typography sx={{overflowWrap:'anywhere'}}>{rule.description}</Typography>
   <Typography>{rule.account} · {rule.active?'Ativa':'Pausada'}{!rule.categoryActive?' · Categoria arquivada: a regra não será aplicada':''}</Typography>
   {rule.blockedByCorrection&&<Alert severity="warning">Uma correção manual para essa descrição tem prioridade. A regra não substituirá sua categoria corrigida; pause a regra se esse recebimento não for sempre salário.</Alert>}
   <Button disabled={busy} onClick={()=>void toggle(rule)}>{rule.active?'Pausar regra':'Ativar regra'}</Button>
  </Paper>)}
 </Stack></Paper>;
}
