import { useState } from 'react';
import { Link,useSearchParams } from 'react-router-dom';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { Alert,Button,MenuItem,Paper,Stack,TextField,Typography } from '@mui/material';
import { api,json } from '../../lib/api';
import { civilDate,money,today } from '../../lib/format';
import { Failure,Loading,PageHeader,FormDialog } from '../../components/Shared';
import { BankInvestmentPositions } from './BankInvestmentPositions';
import { refreshFinances } from '../../lib/financialQueries';
interface Position {id:string;name:string;institution:string;instrument:string;reportedValue:string;valueDate:string;version:number}
interface Summary {positions:Position[];flows:{id:string;description:string;amount:string;date:string;nature:string;instrument:string}[];totalFlows:number;contributions:string;withdrawals:string;netContributions:string;suggestions:{id:string;description:string;instrument:string;nature:string}[]}
export function Investments(){
 const [params,setParams]=useSearchParams(),month=params.get('month')??today().slice(0,7);
 const client=useQueryClient(),[editing,setEditing]=useState<Position|null|undefined>(),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 const [name,setName]=useState(''),[institution,setInstitution]=useState(''),[instrument,setInstrument]=useState('RDB'),[value,setValue]=useState('0'),[date,setDate]=useState(today());
 const query=useQuery({queryKey:['investments',month],queryFn:({signal})=>api<Summary>('/investments?month='+encodeURIComponent(month),{signal})});
 function open(p:Position|null){setEditing(p);setName(p?.name??'');setInstitution(p?.institution??'');setInstrument(p?.instrument??'RDB');setValue(p?.reportedValue??'0');setDate(p?.valueDate??today());setError('');}
 async function save(){setBusy(true);setError('');try{await api('/investments'+(editing?'/'+editing.id:''),{method:editing?'PATCH':'POST',body:json({name,institution,instrument,reportedValue:value,valueDate:date,version:editing?.version??0})});await refreshFinances(client);setEditing(undefined);}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 return <><PageHeader title="Investimentos" subtitle="Posições informadas e aplicações identificadas, sem movimentar dinheiro." action={<Button variant="contained" onClick={()=>open(null)}>Cadastrar investimento</Button>}/>
  <BankInvestmentPositions/>
  <TextField label="Mês dos fluxos de investimento" type="month" value={month} onChange={e=>{if(e.target.value)setParams({month:e.target.value});}} slotProps={{inputLabel:{shrink:true}}} sx={{mb:2}}/>
  <Alert severity="info" sx={{mb:2}}>Posições manuais ficam separadas das posições do banco. Confirme aplicações e resgates em Movimentações; aportes líquidos não são saldo atual nem rendimento.</Alert>
  {query.isPending?<Loading/>:query.error?<Failure error={query.error}/>:<Stack spacing={2}>
   <Paper variant="outlined" sx={{p:2}}><Typography>Aportes identificados: {money(query.data.contributions)} · Resgates: {money(query.data.withdrawals)}</Typography><Typography>Aportes líquidos identificados: {money(query.data.netContributions)}</Typography><Typography variant="caption">Somente naturezas confirmadas; resgates podem incluir principal e rendimentos.</Typography></Paper>
   {query.data.positions.map(p=><Paper key={p.id} variant="outlined" sx={{p:2}}><Typography variant="h6">{p.name}</Typography><Typography>{p.institution} · {p.instrument}</Typography><Typography>Valor informado: {money(p.reportedValue)} em {civilDate(p.valueDate)}</Typography><Button onClick={()=>open(p)}>Atualizar posição</Button></Paper>)}
   <Typography variant="h6">Identificações aguardando confirmação</Typography>
   <Typography variant="caption">Contexto público de produtos, sem LLM. Mostramos até 100 identificações do período; outros tipos de registro não ocupam esse limite.</Typography>
   {query.data.suggestions.map(s=><Paper key={s.id} variant="outlined" sx={{p:2}}><Typography>{s.description} · {s.instrument}</Typography><Typography>{s.nature==='INVESTMENT_IN'?'Possível aplicação':'Possível resgate'} · ainda não alterou indicadores</Typography><Button component={Link} to={'/movements?month='+encodeURIComponent(month)+'&entry='+s.id}>Revisar este registro em Movimentações</Button></Paper>)}
   <Typography variant="h6">Aplicações e resgates confirmados</Typography>
   {query.data.flows.length===0?<Typography>Nenhuma natureza de investimento confirmada. Os registros sugeridos continuam em Movimentações.</Typography>:query.data.flows.map(f=><Paper key={f.id} variant="outlined" sx={{p:2}}><Typography>{f.description} · {f.instrument}</Typography><Typography>{f.nature==='INVESTMENT_IN'?'Aplicação':'Resgate'} · {money(f.amount)} · {civilDate(f.date)}</Typography></Paper>)}
   {query.data.totalFlows>100&&<Typography>Mostrando os 100 registros mais recentes. Totais consideram todos os registros confirmados.</Typography>}
  </Stack>}
  {editing!==undefined&&<FormDialog title={editing?'Atualizar posição':'Cadastrar investimento'} onClose={()=>setEditing(undefined)} dirty={!!name} busy={busy} actions={<Button variant="contained" disabled={busy||!name.trim()||!institution.trim()} onClick={()=>void save()}>Salvar posição informativa</Button>}>
   <Stack spacing={2} pt={1}>{error&&<Alert severity="error">{error}</Alert>}<TextField label="Nome do investimento" value={name} onChange={e=>setName(e.target.value)} slotProps={{htmlInput:{maxLength:100}}}/><TextField label="Instituição" value={institution} onChange={e=>setInstitution(e.target.value)} slotProps={{htmlInput:{maxLength:100}}}/><TextField select label="Tipo do produto" disabled={!!editing} value={instrument} onChange={e=>setInstrument(e.target.value)}><MenuItem value="RDB">RDB · renda fixa</MenuItem><MenuItem value="CDB">CDB · renda fixa</MenuItem><MenuItem value="OTHER">Outro investimento</MenuItem></TextField><TextField label="Valor atual informado" value={value} onChange={e=>setValue(e.target.value)}/><TextField label="Data da posição" type="date" value={date} onChange={e=>setDate(e.target.value)} slotProps={{inputLabel:{shrink:true}}}/><Typography>Este cadastro não aplica ou transfere dinheiro e não altera suas contas.</Typography></Stack>
  </FormDialog>}
 </>;
}
