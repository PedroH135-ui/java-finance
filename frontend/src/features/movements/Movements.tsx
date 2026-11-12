import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Add,FilterAltOff } from '@mui/icons-material';
import { Alert,Box,Button,Chip,MenuItem,Paper,Stack,Tab,Tabs,Table,TableBody,TableCell,TableContainer,TableHead,TableRow,TextField,Typography } from '@mui/material';
import { api } from '../../lib/api';
import type { Account,Category,MovementPage } from '../../lib/types';
import { civilDate,kindLabels,money,monthBounds,statusLabels,today } from '../../lib/format';
import { Empty,Failure,Loading,PageHeader } from '../../components/Shared';
import { MovementForm } from './MovementForm';
import { MovementDetail } from './MovementDetail';
import { BankPendingEntries } from './BankPendingEntries';
export function Movements() {
  const [params,setParams]=useSearchParams();
  const reviewing=params.get('tab')==='review';
  return <>
    <PageHeader title="Movimentações" subtitle="Seus lançamentos internos e os registros bancários que aguardam revisão." action={<Button variant="contained" startIcon={<Add/>} onClick={()=>setParams(previous=>{const next=new URLSearchParams(previous);next.delete('tab');next.set('new','1');return next;})}>Nova movimentação</Button>}/>
    <Tabs value={reviewing?'review':'records'} onChange={(_,value:string)=>setParams(previous=>{const next=new URLSearchParams(previous);if(value==='review'){next.set('tab','review');next.delete('new');next.delete('detail');}else next.delete('tab');return next;})} aria-label="Tipos de movimentações" sx={{mb:3}} variant="scrollable" scrollButtons="auto">
      <Tab id="movements-tab-records" aria-controls="movements-panel-records" value="records" label="Lançamentos internos"/>
      <Tab id="movements-tab-review" aria-controls="movements-panel-review" value="review" label="Aguardando revisão"/>
    </Tabs>
    <Box role="tabpanel" id="movements-panel-records" aria-labelledby="movements-tab-records" hidden={reviewing}>{!reviewing&&<MovementRecords/>}</Box>
    <Box role="tabpanel" id="movements-panel-review" aria-labelledby="movements-tab-review" hidden={!reviewing}>{reviewing&&<BankPendingEntries/>}</Box>
  </>;
}
function MovementRecords() {
  const [params,setParams]=useSearchParams(),[notice,setNotice]=useState('');
  const month=params.get('month')??today().slice(0,7),page=Number(params.get('page')??0);
  const queryParams=new URLSearchParams({...monthBounds(month),page:String(page),size:'20'});
  ['account','category','kind','status','text','sort','direction','basis'].forEach(k=>{if(params.get(k))queryParams.set(k,params.get(k)!);});
  const query=useQuery({queryKey:['movements',queryParams.toString()],queryFn:()=>api<MovementPage>('/movements?'+queryParams)});
  const accounts=useQuery({queryKey:['accounts'],queryFn:()=>api<Account[]>('/accounts')});
  const categories=useQuery({queryKey:['categories'],queryFn:()=>api<Category[]>('/categories')});
  const set=(key:string,value:string)=>setParams(p=>{const n=new URLSearchParams(p);if(value)n.set(key,value);else n.delete(key);if(key!=='page')n.delete('page');return n;},{replace:true});
  const active=['account','category','kind','status','text'].filter(k=>params.has(k));
  const closeForm=()=>set('new','');
  return <>
    {notice&&<Alert severity="success" onClose={()=>setNotice('')} sx={{mb:2}}>{notice}</Alert>}
    <Paper variant="outlined" sx={{p:2,mb:2}}>
      <Box sx={{display:'grid',gridTemplateColumns:{xs:'1fr',sm:'1fr 1fr',md:'repeat(4,minmax(0,1fr))'},gap:2}}>
        <TextField label="Mês do filtro" type="month" value={month} onChange={e=>{if(e.target.value)set('month',e.target.value);}} slotProps={{inputLabel:{shrink:true}}}/>
        <TextField select label="Filtrar pela data de" value={params.get('basis')??'operationDate'} onChange={e=>set('basis',e.target.value)}><MenuItem value="operationDate">Operação</MenuItem><MenuItem value="competence">Competência</MenuItem><MenuItem value="settledDate">Efetivação</MenuItem></TextField>
        <TextField label="Pesquisar descrição" value={params.get('text')??''} onChange={e=>set('text',e.target.value)}/>
        <TextField select label="Conta" value={params.get('account')??''} onChange={e=>set('account',e.target.value)}><MenuItem value="">Todas as contas</MenuItem>{accounts.data?.map(a=><MenuItem key={a.id} value={a.id}>{a.name}</MenuItem>)}</TextField>
        <TextField select label="Categoria" value={params.get('category')??''} onChange={e=>set('category',e.target.value)}><MenuItem value="">Todas as categorias</MenuItem>{categories.data?.map(c=><MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}</TextField>
        <TextField select label="Natureza" value={params.get('kind')??''} onChange={e=>set('kind',e.target.value)}><MenuItem value="">Todas</MenuItem>{Object.entries(kindLabels).map(([v,l])=><MenuItem key={v} value={v}>{l}</MenuItem>)}</TextField>
        <TextField select label="Situação" value={params.get('status')??''} onChange={e=>set('status',e.target.value)}><MenuItem value="">Todas</MenuItem>{Object.entries(statusLabels).map(([v,l])=><MenuItem key={v} value={v}>{l}</MenuItem>)}</TextField>
        <TextField select label="Ordenar por" value={params.get('sort')??'operationDate'} onChange={e=>set('sort',e.target.value)}><MenuItem value="operationDate">Data da operação</MenuItem><MenuItem value="amount">Valor</MenuItem><MenuItem value="description">Descrição</MenuItem><MenuItem value="dueDate">Vencimento</MenuItem></TextField>
        <TextField select label="Ordem" value={params.get('direction')??'desc'} onChange={e=>set('direction',e.target.value)}><MenuItem value="desc">Decrescente</MenuItem><MenuItem value="asc">Crescente</MenuItem></TextField>
      </Box>
      {active.length>0&&<Stack direction="row" alignItems="center" spacing={1} mt={2} flexWrap="wrap"><Typography variant="body2">{active.length} filtro(s) ativo(s)</Typography><Button startIcon={<FilterAltOff/>} onClick={()=>setParams({month})}>Limpar filtros</Button></Stack>}
    </Paper>
    {query.isPending?<Loading/>:query.error?<Failure error={query.error} retry={()=>void query.refetch()}/>:<Paper variant="outlined">
      {query.data.items.length===0?<Empty title="Nenhuma movimentação neste período">Cadastre uma movimentação ou ajuste os filtros para consultar seu histórico.</Empty>:<>
        <TableContainer sx={{display:{xs:'none',md:'block'}}}><Table aria-label="Movimentações"><TableHead><TableRow><TableCell>Descrição / conta</TableCell><TableCell>Operação</TableCell><TableCell>Natureza</TableCell><TableCell>Situação</TableCell><TableCell align="right">Valor</TableCell><TableCell>Detalhes</TableCell></TableRow></TableHead>
          <TableBody>{query.data.items.map(m=><TableRow key={m.id} hover>
            <TableCell sx={{maxWidth:260,overflowWrap:'anywhere'}}><Typography fontWeight={600} variant="body2">{m.description}</Typography><Typography variant="caption" color="text.secondary">{accounts.data?.find(a=>a.id===m.accountId)?.name}</Typography></TableCell>
            <TableCell>{civilDate(m.operationDate)}</TableCell><TableCell>{kindLabels[m.kind]}</TableCell><TableCell><Chip size="small" variant="outlined" label={statusLabels[m.status]} color={m.status==='CONFIRMED'?'success':m.status==='PLANNED'?'warning':'default'}/></TableCell>
            <TableCell align="right" className="money">{money(m.amount)}</TableCell><TableCell><Button onClick={()=>set('detail',m.id)} aria-label={'Detalhes de '+m.description}>Ver</Button></TableCell>
          </TableRow>)}</TableBody></Table></TableContainer>
        <Stack sx={{display:{md:'none'}}} divider={<Box sx={{borderBottom:1,borderColor:'divider'}}/>}>{query.data.items.map(m=><Box key={m.id} p={2}>
          <Typography fontWeight={650} sx={{overflowWrap:'anywhere'}}>{m.description}</Typography><Typography variant="body2" color="text.secondary">{civilDate(m.operationDate)} · {kindLabels[m.kind]}</Typography>
          <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={1} mt={1}><Typography className="money" fontWeight={650}>{money(m.amount)}</Typography><Chip size="small" label={statusLabels[m.status]}/><Button onClick={()=>set('detail',m.id)}>Detalhes</Button></Stack>
        </Box>)}</Stack>
      </>}
      <Stack direction="row" justifyContent="space-between" alignItems="center" p={2} gap={1} flexWrap="wrap">
        <Typography variant="body2" color="text.secondary">{query.data.total} registro(s) · Página {page+1} de {Math.max(query.data.pages,1)}</Typography>
        <Stack direction="row"><Button disabled={page===0} onClick={()=>set('page',String(page-1))}>Anterior</Button><Button disabled={page+1>=query.data.pages} onClick={()=>set('page',String(page+1))}>Próxima</Button></Stack>
      </Stack>
    </Paper>}
    {params.has('new')&&<MovementForm onClose={closeForm} onSuccess={()=>{closeForm();setNotice('Movimentação salva com sucesso.');}}/>}
    {params.get('detail')&&<MovementDetail id={params.get('detail')!} onClose={()=>set('detail','')}/>}
  </>;
}

