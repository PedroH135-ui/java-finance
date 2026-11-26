import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useDebounced } from '../../lib/useDebounced';
import { recordNature,categoryNote } from './recordLabels';
import { CardChargeEditor } from '../banking/CardChargeEditor';
import type { FinancialRecord } from '../../lib/types';
import { Add,FilterAltOff } from '@mui/icons-material';
import { Alert,Box,Button,Chip,MenuItem,Paper,Stack,Tab,Tabs,Table,TableBody,TableCell,TableContainer,TableHead,TableRow,TextField,Typography } from '@mui/material';
import { api } from '../../lib/api';
import type { Account,Category,FinancialRecordPage } from '../../lib/types';
import { civilDate,kindLabels,money,monthBounds,statusLabels,today } from '../../lib/format';
import { Empty,Failure,Loading,PageHeader } from '../../components/Shared';
import { MovementForm } from './MovementForm';
import { MovementDetail } from './MovementDetail';
import { BankRecordDetail } from './BankRecordDetail';
import { CategoryAutomationSync } from './CategoryAutomationNotice';
import { CategoryGroups } from './CategoryGroups';
export function Movements() {
  const [params,setParams]=useSearchParams();
  const hidden=params.get('hidden')==='true';
  return <>
    <PageHeader title="Movimentações" subtitle="Registros do banco e lançamentos manuais, juntos e sem aprovação prévia." action={<Button variant="contained" startIcon={<Add/>} onClick={()=>setParams(previous=>{const next=new URLSearchParams(previous);next.delete('hidden');next.set('new','1');return next;})}>Nova movimentação</Button>}/>
    <CategoryAutomationSync/>
    <Alert severity="info" sx={{mb:2}}>Registros efetivados do banco entram automaticamente nos relatórios como entradas e saídas. As categorias organizam esses registros. Pendentes não entram nos totais realizados. Você pode ocultar algo incorreto e restaurar depois, sem alterar o saldo bancário.</Alert>
    <Tabs value={hidden?'hidden':'all'} onChange={(_,value:string)=>setParams(previous=>{const next=new URLSearchParams(previous);next.delete('tab');next.delete('detail');next.delete('page');if(value==='hidden')next.set('hidden','true');else next.delete('hidden');return next;})} aria-label="Visibilidade das movimentações" sx={{mb:3}} variant="scrollable" scrollButtons="auto">
      <Tab value="all" label="Todas as movimentações"/>
      <Tab value="hidden" label="Ocultas"/>
    </Tabs>
    {!hidden&&<Button sx={{mb:2}} onClick={()=>setParams(p=>{const n=new URLSearchParams(p);n.set('category','uncategorized');n.delete('page');return n;})}>Mostrar sem categoria</Button>}
    <MovementRecords/>
  </>;
}
function MovementRecords() {
  const [params,setParams]=useSearchParams(),[notice,setNotice]=useState('');
  const [bankDetail,setBankDetail]=useState<FinancialRecordPage['items'][number]|null>(null);
  const [cardDetail,setCardDetail]=useState<string|null>(null);
  const linkedId=params.get('entry');
  const linkedRecord=useQuery({queryKey:['linkedBankRecord',linkedId],queryFn:({signal})=>api<FinancialRecord>(`/banking/entries/${linkedId}/record`,{signal}),enabled:!!linkedId});
  const month=params.get('month')??today().slice(0,7),page=Number(params.get('page')??0);
  const search=useDebounced(params.get('text')??'');
  const queryParams=new URLSearchParams({...monthBounds(month),page:String(page),size:'20',hidden:String(params.get('hidden')==='true')});
  ['account','category','kind','status','text','sort','direction','basis','consumption'].forEach(k=>{if(params.get(k))queryParams.set(k,params.get(k)!);});
  if(search)queryParams.set('text',search);else queryParams.delete('text');
  const query=useQuery({queryKey:['movementFeed',queryParams.toString()],queryFn:({signal})=>api<FinancialRecordPage>('/movements/feed?'+queryParams,{signal}),refetchInterval:60000});
  const accounts=useQuery({queryKey:['accounts'],queryFn:()=>api<Account[]>('/accounts')});
  const categories=useQuery({queryKey:['categories'],queryFn:()=>api<Category[]>('/categories')});
  const set=(key:string,value:string)=>setParams(p=>{const n=new URLSearchParams(p);if(value)n.set(key,value);else n.delete(key);if(key!=='page')n.delete('page');return n;},{replace:true});
  const active=['account','category','kind','status','text'].filter(k=>params.has(k));
  const closeForm=()=>set('new','');
  return <>
    {params.get('consumption')==='true'&&<Button onClick={()=>set('consumption','')}>Ver extrato completo, incluindo investimentos e faturas</Button>}
    {notice&&<Alert severity="success" onClose={()=>setNotice('')} sx={{mb:2}}>{notice}</Alert>}
    <Paper variant="outlined" sx={{p:2,mb:2}}>
      <Box sx={{display:'grid',gridTemplateColumns:{xs:'1fr',sm:'1fr 1fr',md:'repeat(4,minmax(0,1fr))'},gap:2}}>
        <TextField label="Mês do filtro" type="month" value={month} onChange={e=>{if(e.target.value)set('month',e.target.value);}} slotProps={{inputLabel:{shrink:true}}}/>
        <TextField select label="Filtrar pela data de" value={params.get('basis')??'operationDate'} onChange={e=>set('basis',e.target.value)}><MenuItem value="operationDate">Operação</MenuItem><MenuItem value="competence">Competência</MenuItem><MenuItem value="settledDate">Efetivação</MenuItem></TextField>
        <TextField label="Pesquisar descrição" value={params.get('text')??''} onChange={e=>set('text',e.target.value)}/>
        <TextField select label="Conta" value={params.get('account')??''} onChange={e=>set('account',e.target.value)}><MenuItem value="">Todas as contas</MenuItem>{accounts.data?.map(a=><MenuItem key={a.id} value={a.id}>{a.name}</MenuItem>)}</TextField>
        <TextField select label="Categoria" value={params.get('category')??''} onChange={e=>set('category',e.target.value)}><MenuItem value="">Todas as categorias</MenuItem><MenuItem value="uncategorized">Sem categoria</MenuItem>{categories.data?.map(c=><MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}</TextField>
        <TextField select label="Natureza" value={params.get('kind')??''} onChange={e=>set('kind',e.target.value)}><MenuItem value="">Todas</MenuItem>{Object.entries({...kindLabels,INCOME:'Entrada / receita',EXPENSE:'Saída / despesa'}).map(([v,l])=><MenuItem key={v} value={v}>{l}</MenuItem>)}</TextField>
        <TextField select label="Situação" value={params.get('status')??''} onChange={e=>set('status',e.target.value)}><MenuItem value="">Todas</MenuItem>{Object.entries(statusLabels).map(([v,l])=><MenuItem key={v} value={v}>{l}</MenuItem>)}</TextField>
        <TextField select label="Ordenar por" value={params.get('sort')??'operationDate'} onChange={e=>set('sort',e.target.value)}><MenuItem value="operationDate">Data da operação</MenuItem><MenuItem value="amount">Valor</MenuItem><MenuItem value="description">Descrição</MenuItem><MenuItem value="dueDate">Vencimento</MenuItem></TextField>
        <TextField select label="Ordem" value={params.get('direction')??'desc'} onChange={e=>set('direction',e.target.value)}><MenuItem value="desc">Decrescente</MenuItem><MenuItem value="asc">Crescente</MenuItem></TextField>
      </Box>
      {active.length>0&&<Stack direction="row" alignItems="center" spacing={1} mt={2} flexWrap="wrap"><Typography variant="body2">{active.length} filtro(s) ativo(s)</Typography><Button startIcon={<FilterAltOff/>} onClick={()=>setParams({month})}>Limpar filtros</Button></Stack>}
    </Paper>
    {params.get('category')==='uncategorized'&&query.data&&categories.data&&<CategoryGroups key={queryParams.toString()} records={query.data.items} categories={categories.data}/>}
    {query.isPending?<Loading/>:query.error?<Failure error={query.error} retry={()=>void query.refetch()}/>:<Paper variant="outlined">
      {query.data.items.length===0?<Empty title="Nenhuma movimentação neste período">Ajuste os filtros. Registros bancários recebidos aparecem aqui automaticamente. Na aba Ocultas você pode restaurar o que retirou.</Empty>:<>
        <TableContainer sx={{display:{xs:'none',md:'block'}}}><Table aria-label="Movimentações"><TableHead><TableRow><TableCell>Descrição / conta</TableCell><TableCell>Operação</TableCell><TableCell>Natureza</TableCell><TableCell>Situação</TableCell><TableCell align="right">Valor</TableCell><TableCell>Detalhes</TableCell></TableRow></TableHead>
          <TableBody>{query.data.items.map(m=><TableRow key={m.id} hover>
            <TableCell sx={{maxWidth:260,overflowWrap:'anywhere'}}><Typography fontWeight={600} variant="body2">{m.description}</Typography><Typography variant="caption" color="text.secondary">{accounts.data?.find(a=>a.id===m.accountId)?.name}{m.bankEntryId?' · Banco':''}</Typography><Typography variant="caption" display="block">{m.categoryId?categories.data?.find(c=>c.id===m.categoryId)?.name:['INCOME','EXPENSE'].includes(m.kind)?'Sem categoria':''}{categoryNote(m)}</Typography></TableCell>
            <TableCell>{civilDate(m.operationDate)}</TableCell><TableCell>{recordNature(m)??kindLabels[m.kind]}</TableCell><TableCell><Chip size="small" variant="outlined" label={m.status==='CANCELLED'?'Cancelado nos relatórios':m.bankProviderStatus?(m.bankProviderStatus==='POSTED'?'Efetivado no banco':'Pendente no banco'):statusLabels[m.status]} color={m.status==='CONFIRMED'?'success':m.status==='PLANNED'?'warning':'default'}/></TableCell>
            <TableCell align="right" className="money">{money(m.amount)}</TableCell><TableCell><Button onClick={()=>m.source==='CARD'?setCardDetail(m.id):m.bankEntryId?setBankDetail(m):set('detail',m.id)} aria-label={'Detalhes de '+m.description}>Ver</Button></TableCell>
          </TableRow>)}</TableBody></Table></TableContainer>
        <Stack sx={{display:{md:'none'}}} divider={<Box sx={{borderBottom:1,borderColor:'divider'}}/>}>{query.data.items.map(m=><Box key={m.id} p={2}>
          <Typography fontWeight={650} sx={{overflowWrap:'anywhere'}}>{m.description}</Typography><Typography variant="body2" color="text.secondary">{civilDate(m.operationDate)} · {recordNature(m)??kindLabels[m.kind]}</Typography>
          <Typography variant="caption">{m.categoryId?categories.data?.find(c=>c.id===m.categoryId)?.name:'Sem categoria'}{categoryNote(m)}</Typography>
          <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={1} mt={1}><Typography className="money" fontWeight={650}>{money(m.amount)}</Typography><Chip size="small" label={m.status==='CANCELLED'?'Cancelado nos relatórios':m.bankProviderStatus?(m.bankProviderStatus==='POSTED'?'Efetivado no banco':'Pendente no banco'):statusLabels[m.status]}/><Button onClick={()=>m.source==='CARD'?setCardDetail(m.id):m.bankEntryId?setBankDetail(m):set('detail',m.id)}>Detalhes</Button></Stack>
        </Box>)}</Stack>
      </>}
      <Stack direction="row" justifyContent="space-between" alignItems="center" p={2} gap={1} flexWrap="wrap">
        <Typography variant="body2" color="text.secondary">{query.data.total} registro(s) · Página {page+1} de {Math.max(query.data.pages,1)}</Typography>
        <Stack direction="row"><Button disabled={page===0} onClick={()=>set('page',String(page-1))}>Anterior</Button><Button disabled={page+1>=query.data.pages} onClick={()=>set('page',String(page+1))}>Próxima</Button></Stack>
      </Stack>
    </Paper>}
    {params.has('new')&&<MovementForm onClose={closeForm} onSuccess={()=>{closeForm();setNotice('Movimentação salva com sucesso.');}}/>}
    {linkedId&&linkedRecord.isPending&&<Loading/>}
    {linkedId&&linkedRecord.error&&<Failure error={linkedRecord.error}/>}
    {(bankDetail||linkedRecord.data&&linkedId)&&<BankRecordDetail record={(bankDetail??linkedRecord.data)!} onClose={()=>{setBankDetail(null);if(linkedId)set('entry','');}}/>}
    {cardDetail&&<CardChargeEditor id={cardDetail} onClose={()=>setCardDetail(null)}/>}
    {params.get('detail')&&<MovementDetail id={params.get('detail')!} onClose={()=>set('detail','')}/>}
  </>;
}

