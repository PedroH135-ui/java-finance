import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { Alert, Box, Button, Chip, Divider, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { Add, AccountBalanceOutlined, EditOutlined } from '@mui/icons-material';
import { api, ApiError, json } from '../../lib/api';
import type { Account } from '../../lib/types';
import { accountKinds, bankDateTime, civilDate, money, today } from '../../lib/format';
import { useStoredBankLinks } from '../banking/useStoredBankLinks';
import { Empty, Failure, FormDialog, Loading, MoneyInput, PageHeader } from '../../components/Shared';
interface Values { name:string; institution:string; kind:string; openingBalance:string; openingDate:string }
export function Accounts() {
  const query=useQuery({queryKey:['accounts'],queryFn:()=>api<Account[]>('/accounts')});
  const banking=useStoredBankLinks();
  const [editing,setEditing]=useState<Account|null|undefined>(undefined),[notice,setNotice]=useState('');
  return <>
    <PageHeader title="Contas" subtitle="O dinheiro que você tem, organizado por conta." action={<Button variant="contained" startIcon={<Add/>} onClick={()=>setEditing(null)}>Nova conta</Button>}/>
    {notice&&<Alert severity="success" onClose={()=>setNotice('')} sx={{mb:2}}>{notice}</Alert>}
    <Alert severity="info" sx={{mb:3}}>O saldo interno é calculado pelo saldo inicial e pelos lançamentos efetivados. O saldo informado pelo banco é exibido separadamente e não altera o saldo interno.</Alert>
    {banking.loading&&<Typography role="status" mb={2}>Carregando saldos bancários armazenados…</Typography>}
    {banking.error&&<Alert severity="warning" sx={{mb:2}}>Não foi possível carregar os saldos bancários. As contas e os saldos internos continuam disponíveis.</Alert>}
    {query.isPending?<Loading/>:query.error?<Failure error={query.error} retry={()=>void query.refetch()}/>:query.data.length===0?
      <Paper variant="outlined"><Empty title="Comece pela sua primeira conta" action={<Button variant="outlined" onClick={()=>setEditing(null)}>Cadastrar conta</Button>}>Pode ser sua conta corrente, carteira ou dinheiro em espécie.</Empty></Paper>:
      <Box sx={{display:'grid',gridTemplateColumns:{xs:'1fr',md:'repeat(2,minmax(0,1fr))',xl:'repeat(3,minmax(0,1fr))'},gap:2}}>
        {query.data.map(a=><Paper key={a.id} variant="outlined" sx={{p:3}} component="section" aria-label={'Conta '+a.name}>
          <Stack direction="row" justifyContent="space-between" alignItems="center"><AccountBalanceOutlined color="primary"/><Chip size="small" label={a.archived?'Arquivada':'Ativa'} variant="outlined"/></Stack>
          <Typography variant="h6" mt={2} sx={{overflowWrap:'anywhere'}}>{a.name}</Typography>
          <Typography color="text.secondary" variant="body2">{accountKinds[a.kind]}{a.institution?' · '+a.institution:''}</Typography>
          <Typography variant="body2" mt={2}>Saldo interno</Typography>
          <Typography variant="h5" className="money" my={1} sx={{fontSize:{xs:'1.1rem',sm:'1.4rem'}}}>{money(a.balance)}</Typography>
          <Typography variant="caption" color="text.secondary">Saldo realizado · desde {civilDate(a.openingDate)}</Typography>
          {banking.ready&&!banking.error&&banking.links.data?.filter(link=>link.accountId===a.id).map(link=><Box key={link.id} mt={2}>
            <Divider sx={{mb:2}}/>
            <Typography variant="body2" fontWeight={600}>Saldo informado pelo banco</Typography>
            <Typography variant="h5" className="money" my={1}>{link.reportedBalance==null?'Não disponível':money(link.reportedBalance)}</Typography>
            <Typography variant="body2" color="text.secondary" sx={{overflowWrap:'anywhere'}}>{link.name}</Typography>
            <Typography variant="caption" display="block">Atualização no banco: {bankDateTime(link.providerUpdatedAt)}</Typography>
            <Typography variant="caption" display="block">Última consulta no sistema: {bankDateTime(link.lastCheckedAt)}</Typography>
            {!link.active&&<Typography variant="caption" display="block">Leitura desativada · último saldo armazenado.</Typography>}
            <Typography variant="caption" color="text.secondary" display="block" mt={1}>Não é saldo em tempo real. Os lançamentos aguardam revisão manual.</Typography>
            <Button component={Link} to={'/movements?tab=review&link='+encodeURIComponent(link.id)} size="small" sx={{mt:1}}>Ver registros para revisão</Button>
          </Box>)}
          <Box mt={2}><Button startIcon={<EditOutlined/>} onClick={()=>setEditing(a)}>Editar conta</Button></Box>
        </Paper>)}
      </Box>}
    {editing!==undefined&&<AccountForm account={editing} onClose={()=>setEditing(undefined)} onSuccess={()=>{setEditing(undefined);setNotice('Conta salva com sucesso.');}}/>}
  </>;
}
function AccountForm({account,onClose,onSuccess}:{account:Account|null;onClose:()=>void;onSuccess:()=>void}) {
  const client=useQueryClient(),[error,setError]=useState(''),[archived,setArchived]=useState(account?.archived??false);
  const form=useForm<Values>({defaultValues:{name:account?.name??'',institution:account?.institution??'',kind:account?.kind??'CHECKING',openingBalance:'0',openingDate:today()}});
  const submit=form.handleSubmit(async values=>{
    setError('');
    try {
      await api('/accounts'+(account?'/'+account.id:''),{method:account?'PATCH':'POST',body:json(account?{name:values.name,institution:values.institution,archived,version:account.version}:values)});
      await client.invalidateQueries();onSuccess();
    } catch(e){setError((e as Error).message);if(e instanceof ApiError)Object.entries(e.fields).forEach(([k,v])=>form.setError(k as keyof Values,{message:v}));}
  });
  return <FormDialog title={account?'Editar conta':'Nova conta'} onClose={onClose} busy={form.formState.isSubmitting} dirty={form.formState.isDirty||archived!==(account?.archived??false)}
    actions={<Button variant="contained" type="submit" form="account-form" disabled={form.formState.isSubmitting}>{form.formState.isSubmitting?'Salvando…':'Salvar conta'}</Button>}>
    <form id="account-form" onSubmit={submit}><Stack spacing={2.5} pt={1}>
      {error&&<Alert severity="error">{error}</Alert>}
      <TextField label="Nome da conta" required autoFocus {...form.register('name',{required:'Dê um nome à conta.',maxLength:100})} error={!!form.formState.errors.name} helperText={form.formState.errors.name?.message}/>
      <TextField label="Instituição (opcional)" {...form.register('institution',{maxLength:100})}/>
      {!account&&<>
        <Controller name="kind" control={form.control} render={({field})=><TextField select label="Tipo de conta" {...field}>{Object.entries(accountKinds).map(([v,l])=><MenuItem key={v} value={v}>{l}</MenuItem>)}</TextField>}/>
        <Controller name="openingBalance" control={form.control} rules={{required:'Informe o saldo inicial.'}} render={({field,fieldState})=><MoneyInput label="Saldo inicial *" value={field.value} onChange={field.onChange} signed error={!!fieldState.error} helperText={fieldState.error?.message??'Pode ser negativo. Não será contado como receita.'}/>}/>
        <TextField label="Data do saldo inicial" type="date" required slotProps={{inputLabel:{shrink:true}}} {...form.register('openingDate',{required:true})}/>
      </>}
      {account&&<><Alert severity="info">Para corrigir o saldo, registre um ajuste em Movimentações. O histórico será preservado.</Alert>
        <Button color={archived?'primary':'warning'} variant="outlined" onClick={()=>setArchived(!archived)}>{archived?'Reativar conta ao salvar':'Arquivar conta ao salvar'}</Button>
        {archived&&<Typography variant="body2">A conta será arquivada. Seus registros e saldo continuam disponíveis.</Typography>}</>}
    </Stack></form>
  </FormDialog>;
}

