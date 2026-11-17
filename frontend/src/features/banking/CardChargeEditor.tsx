import { useState } from 'react';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { Alert,Button,MenuItem,Stack,TextField,Typography } from '@mui/material';
import { api,json } from '../../lib/api';
import { refreshFinances } from '../../lib/financialQueries';
import type { Category } from '../../lib/types';
import { money,civilDate } from '../../lib/format';
import { FormDialog,Loading,Failure } from '../../components/Shared';
import type { CardCharge } from './useBankProducts';

export function CardChargeEditor({id,onClose}:{id:string;onClose:()=>void}){
 const client=useQueryClient(),[selected,setSelected]=useState<string>(),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 const products=useQuery({queryKey:['cardCharge',id],queryFn:({signal})=>api<CardCharge>(`/banking/card-charges/${id}`,{signal})});
 const categories=useQuery({queryKey:['categories'],queryFn:()=>api<Category[]>('/categories')});
 const charge=products.data;
 async function save(){if(!charge)return;setBusy(true);setError('');try{await api(`/banking/card-charges/${id}/category`,{method:'POST',body:json({version:charge.version,categoryId:(selected??charge.categoryId)||null})});await refreshFinances(client);onClose();}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 return <FormDialog title="Detalhe do cartão" dirty={selected!==undefined} busy={busy} onClose={onClose} actions={<Button disabled={busy||!charge||selected===undefined} onClick={()=>void save()}>Salvar categoria do detalhe</Button>}>
  {products.isPending?<Loading/>:products.error?<Failure error={products.error}/>:!charge?<Alert severity="warning">Detalhe não disponível na leitura atual. Abra Conexões bancárias para conferir a cobertura.</Alert>:<Stack spacing={2} pt={1}>
   <Typography>{charge.description}</Typography><Typography>{money(charge.amount)} · {civilDate(charge.date)} · {charge.direction==='CREDIT'?'Crédito/estorno no cartão':'Débito no cartão'}</Typography>
   <Typography>{charge.status==='POSTED'?'Efetivado':'Pendente'}{charge.installment&&charge.installments?` · Parcela ${charge.installment} de ${charge.installments}`:''}</Typography>
   <Alert severity="info">Este é um detalhe do cartão, sem desconto adicional na conta bancária. Entra no consumo somente quando a fatura completa é conciliada manualmente. Créditos conciliados reduzem o consumo, sem virar salário.</Alert>
   {charge.categorySource==='AUTO'&&<Typography>Categoria automática, disponível para correção por você.</Typography>}
   {charge.categorySource==='MANUAL'&&<Alert severity="success">Sua escolha manual tem prioridade; a automação não substituirá esta categoria.</Alert>}
   {!charge.categoryId&&charge.categoryResolution==='NO_CATEGORY'&&<Typography>Contexto insuficiente para classificar automaticamente. Você pode escolher uma categoria.</Typography>}
   {categories.isPending?<Loading/>:categories.error?<Failure error={categories.error}/>:<TextField select label="Categoria do detalhe do cartão" value={selected??charge.categoryId??''} onChange={e=>setSelected(e.target.value)}><MenuItem value="">Sem categoria</MenuItem>{categories.data.filter(c=>!c.archived&&c.kind==='EXPENSE').map(c=><MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}</TextField>}
   {error&&<Alert severity="error">{error}</Alert>}
  </Stack>}
 </FormDialog>;
}
