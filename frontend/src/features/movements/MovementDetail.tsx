import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, Divider, Stack, TextField, Typography } from '@mui/material';
import { api,json } from '../../lib/api';
import type { Account,Category,Movement } from '../../lib/types';
import { civilDate,kindLabels,money,statusLabels,today } from '../../lib/format';
import { Failure,FormDialog,Loading } from '../../components/Shared';
export function MovementDetail({id,onClose}:{id:string;onClose:()=>void}) {
  const query=useQuery({queryKey:['movement',id],queryFn:()=>api<Movement>('/movements/'+id)});
  const audit=useQuery({queryKey:['audit',id],queryFn:()=>api<{action:string;reason:string;created_at:string}[]>('/movements/'+id+'/audit')});
  const accounts=useQuery({queryKey:['accounts'],queryFn:()=>api<Account[]>('/accounts')});
  const categories=useQuery({queryKey:['categories'],queryFn:()=>api<Category[]>('/categories')});
  const [action,setAction]=useState<'confirm'|'cancel'|null>(null);
  const m=query.data;
  return <>
    <Dialog open onClose={onClose} aria-labelledby="detail-title">
      <DialogTitle id="detail-title">Detalhes da movimentação</DialogTitle>
      <DialogContent dividers>{query.isPending?<Loading/>:query.error?<Failure error={query.error}/>:m&&<Stack spacing={2}>
        <Typography variant="h5" sx={{overflowWrap:'anywhere'}}>{m.description}</Typography><Typography className="money" variant="h5">{money(m.amount)}</Typography>
        <Stack direction="row" spacing={1}><Chip label={kindLabels[m.kind]} size="small"/><Chip label={statusLabels[m.status]} size="small"/></Stack>
        <Typography>Conta: {accounts.data?.find(a=>a.id===m.accountId)?.name??'Carregando…'}</Typography>
        {m.destinationId&&<Typography>Destino: {accounts.data?.find(a=>a.id===m.destinationId)?.name??'Carregando…'}</Typography>}
        {m.categoryId&&<Typography>Categoria: {categories.data?.find(c=>c.id===m.categoryId)?.name??'Carregando…'}</Typography>}
        <Typography>Operação: {civilDate(m.operationDate)} · Competência: {m.competence.slice(0,7).split('-').reverse().join('/')}</Typography>
        <Typography>Vencimento: {civilDate(m.dueDate)} · Efetivação: {civilDate(m.settledDate)}</Typography>
        <Typography variant="body2" sx={{whiteSpace:'pre-wrap',overflowWrap:'anywhere'}}>{m.notes||'Sem observações.'}</Typography>
        <Typography variant="body2">Origem: {m.source==='MANUAL'?'Cadastro manual':m.source}</Typography>
        <Alert severity="info">Para corrigir valores, cancele esta operação e registre a correta. O histórico é preservado. Transferências são revertidas nos dois lados.</Alert>
        <Divider/><Typography variant="h6">Histórico</Typography>
        {audit.isPending?<Loading/>:audit.error?<Failure error={audit.error}/>:audit.data?.map((event,i)=><Typography key={i} variant="body2">{new Date(event.created_at).toLocaleString('pt-BR')} — {event.reason}</Typography>)}
      </Stack>}</DialogContent>
      <DialogActions sx={{flexWrap:'wrap',p:2}}><Button onClick={onClose}>Fechar</Button>
        {m?.status==='PLANNED'&&<Button variant="contained" onClick={()=>setAction('confirm')}>Confirmar efetivação</Button>}
        {m&&m.kind!=='OPENING'&&m.status!=='CANCELLED'&&<Button color="error" onClick={()=>setAction('cancel')}>Cancelar movimentação</Button>}
      </DialogActions>
    </Dialog>
    {m&&action&&<ChangeForm movement={m} action={action} onClose={()=>setAction(null)}/>}
  </>;
}
function ChangeForm({movement,action,onClose}:{movement:Movement;action:'confirm'|'cancel';onClose:()=>void}) {
  const client=useQueryClient(),[reason,setReason]=useState(''),[date,setDate]=useState(today()),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  async function submit(e:React.FormEvent){e.preventDefault();if(busy)return;setBusy(true);setError('');
    try{await api('/movements/'+movement.id+'/'+action,{method:'POST',body:json({version:movement.version,reason,settledDate:action==='confirm'?date:null})});await client.invalidateQueries();onClose();}
    catch(e){setError((e as Error).message);}finally{setBusy(false);}
  }
  return <FormDialog title={action==='confirm'?'Confirmar efetivação':'Cancelar movimentação'} busy={busy} dirty={!!reason} onClose={onClose}
    actions={<Button variant="contained" color={action==='cancel'?'error':'primary'} type="submit" form="change-form" disabled={busy}>{busy?'Salvando…':'Confirmar'}</Button>}>
    <form id="change-form" onSubmit={submit}><Stack spacing={2} pt={1}>{error&&<Alert severity="error">{error}</Alert>}
      <Typography>{movement.description} · {money(movement.amount)}</Typography>
      {action==='confirm'&&<TextField label="Data de efetivação" type="date" value={date} onChange={e=>setDate(e.target.value)} required slotProps={{inputLabel:{shrink:true}}}/>}
      <TextField label="Motivo para o histórico" value={reason} onChange={e=>setReason(e.target.value)} required autoFocus multiline rows={2} slotProps={{htmlInput:{maxLength:300}}}/>
    </Stack></form>
  </FormDialog>;
}

