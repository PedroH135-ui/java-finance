import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import { api, ApiError, json } from '../../lib/api';
import type { Account, Category } from '../../lib/types';
import { today } from '../../lib/format';
import { Failure, FormDialog, Loading, MoneyInput } from '../../components/Shared';
interface Values {
  description:string;amount:string;kind:string;status:string;accountId:string;destinationId:string;categoryId:string;
  operationDate:string;competence:string;dueDate:string;settledDate:string;notes:string;
}
export function MovementForm({onClose,onSuccess}:{onClose:()=>void;onSuccess:()=>void}) {
  const client=useQueryClient(),[error,setError]=useState(''),[requestKey]=useState(()=>crypto.randomUUID());
  const accounts=useQuery({queryKey:['accounts'],queryFn:()=>api<Account[]>('/accounts')});
  const categories=useQuery({queryKey:['categories'],queryFn:()=>api<Category[]>('/categories')});
  const form=useForm<Values>({defaultValues:{description:'',amount:'',kind:'EXPENSE',status:'CONFIRMED',accountId:'',destinationId:'',categoryId:'',operationDate:today(),competence:today().slice(0,7),dueDate:today(),settledDate:today(),notes:''}});
  const kind=form.watch('kind'),status=form.watch('status'),isOrdinary=['INCOME','EXPENSE'].includes(kind);
  const submit=form.handleSubmit(async values=>{
    setError('');
    try {
      await api('/movements',{method:'POST',body:json({...values,requestKey,competence:values.competence+'-01',
        categoryId:isOrdinary?values.categoryId:null,destinationId:kind==='TRANSFER'?values.destinationId:null,settledDate:status==='CONFIRMED'?values.settledDate:null})});
      await client.invalidateQueries();onSuccess();
    }catch(e){setError((e as Error).message);if(e instanceof ApiError)Object.entries(e.fields).forEach(([k,v])=>form.setError(k as keyof Values,{message:v}));}
  });
  function select(name:'kind'|'status'|'accountId'|'destinationId'|'categoryId',label:string,items:{id:string;name:string}[]) {
    return <Controller name={name} control={form.control} rules={{required:'Escolha uma opção.'}} render={({field,fieldState})=>
      <TextField select label={label} required {...field} error={!!fieldState.error} helperText={fieldState.error?.message}>
        {items.map(i=><MenuItem key={i.id} value={i.id}>{i.name}</MenuItem>)}
      </TextField>}/>;
  }
  return <FormDialog title="Nova movimentação" onClose={onClose} busy={form.formState.isSubmitting} dirty={form.formState.isDirty}
    actions={<Button variant="contained" type="submit" form="movement-form" disabled={form.formState.isSubmitting||accounts.isPending||!accounts.data?.some(a=>!a.archived)}>{form.formState.isSubmitting?'Salvando…':'Salvar movimentação'}</Button>}>
    {accounts.isPending||categories.isPending?<Loading/>:accounts.error||categories.error?<Failure error={(accounts.error||categories.error)!}/>:
      <form id="movement-form" onSubmit={submit}><Stack spacing={2.5} pt={1}>
        {error&&<Alert severity="error">{error}</Alert>}
        {!accounts.data?.some(a=>!a.archived)&&<Alert severity="info">Cadastre uma conta ativa em <Link to="/accounts">Contas</Link> antes de registrar movimentações.</Alert>}
        <Typography variant="body2" color="text.secondary">Campos com * são obrigatórios. Nas contas manuais, valores efetivados alteram o saldo calculado. Nas vinculadas, o saldo vem do banco. Evite cadastrar novamente uma operação já recebida do banco.</Typography>
        {select('kind','Natureza',[{id:'EXPENSE',name:'Despesa'},{id:'INCOME',name:'Receita'},{id:'TRANSFER',name:'Transferência entre minhas contas'},{id:'ADJUSTMENT',name:'Ajuste de saldo'}])}
        {kind==='TRANSFER'&&<Alert severity="info">As duas contas serão movimentadas juntas. Transferências não entram como receita ou despesa.</Alert>}
        {kind==='ADJUSTMENT'&&<Alert severity="warning">Informe a diferença a adicionar ou retirar e explique o motivo na descrição.</Alert>}
        <TextField label="Descrição" autoFocus required {...form.register('description',{required:'Descreva a movimentação.',maxLength:200})} error={!!form.formState.errors.description} helperText={form.formState.errors.description?.message}/>
        <Controller name="amount" control={form.control} rules={{required:'Informe o valor.'}} render={({field,fieldState})=><MoneyInput label="Valor *" value={field.value} onChange={field.onChange} signed={kind==='ADJUSTMENT'} error={!!fieldState.error} helperText={fieldState.error?.message}/>}/>
        {select('accountId',kind==='TRANSFER'?'Conta de origem':'Conta',accounts.data!.filter(a=>!a.archived))}
        {kind==='TRANSFER'&&select('destinationId','Conta de destino',accounts.data!.filter(a=>!a.archived&&a.id!==form.watch('accountId')))}
        {isOrdinary&&select('categoryId','Categoria',categories.data!.filter(c=>!c.archived&&c.kind===kind))}
        {isOrdinary&&!categories.data!.some(c=>!c.archived&&c.kind===kind)&&<Alert severity="info">Crie uma categoria de {kind==='INCOME'?'receita':'despesa'} em <Link to="/categories">Categorias</Link>.</Alert>}
        {select('status','Situação',[{id:'CONFIRMED',name:'Efetivado — já aconteceu'},{id:'PLANNED',name:'Previsto — ainda não aconteceu'}])}
        <Box sx={{display:'grid',gridTemplateColumns:{xs:'1fr',sm:'1fr 1fr'},gap:2}}>
          <TextField label="Data da operação" type="date" required slotProps={{inputLabel:{shrink:true}}} {...form.register('operationDate',{required:true})}/>
          <TextField label="Competência" type="month" required helperText="Mês ao qual a receita/despesa pertence." slotProps={{inputLabel:{shrink:true}}} {...form.register('competence',{required:true})}/>
          <TextField label="Vencimento" type="date" required slotProps={{inputLabel:{shrink:true}}} {...form.register('dueDate',{required:true})}/>
          {status==='CONFIRMED'&&<TextField label="Data de efetivação" type="date" required slotProps={{inputLabel:{shrink:true}}} {...form.register('settledDate',{required:true})}/>}
        </Box>
        <TextField label="Observações (opcional)" multiline rows={2} {...form.register('notes',{maxLength:1000})}/>
      </Stack></form>}
  </FormDialog>;
}

