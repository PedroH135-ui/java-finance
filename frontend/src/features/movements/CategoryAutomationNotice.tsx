import { useEffect } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, FormControlLabel, Switch } from '@mui/material';
import { api } from '../../lib/api';

interface Status {enabled:boolean;running:boolean;categorized:number;awaiting:number;lastRun:string|null;available:boolean;version:number}

export function CategoryAutomationNotice() {
  const client=useQueryClient();
  const status=useQuery({queryKey:['categoryAutomation'],queryFn:()=>api<Status>('/banking/category-automation'),refetchInterval:query=>query.state.data?.enabled?5000:false});
  const change=useMutation({mutationFn:(enabled:boolean)=>api('/banking/category-automation',{method:'POST',body:JSON.stringify({enabled,version:status.data?.version})}),onSuccess:()=>Promise.all(['categoryAutomation','movementFeed','bankAssistance','summary','budgets'].map(key=>client.invalidateQueries({queryKey:[key]})))});
  const retry=useMutation({mutationFn:()=>api('/banking/category-automation/retry',{method:'POST'}),onSuccess:()=>client.invalidateQueries({queryKey:['categoryAutomation']})});
  const lastRun=status.data?.lastRun;
  useEffect(()=>{
    if(lastRun)void Promise.all(['movementFeed','bankAssistance','summary','budgets'].map(key=>client.invalidateQueries({queryKey:[key]})));
  },[lastRun,client]);
  if(!status.data?.available&&!status.data?.enabled)return null;
  return <Alert severity={change.isError||retry.isError?'error':'info'} sx={{mb:2}}>
    <FormControlLabel control={<Switch checked={change.isPending?(change.variables??status.data.enabled):status.data.enabled} disabled={change.isPending} onChange={(_,checked)=>change.mutate(checked)}/>} label="Categorizar automaticamente com IA"/>
    {status.data.enabled&&<> · {status.data.categorized} registro(s) categorizado(s) automaticamente no histórico completo. {status.data.awaiting} sem categoria.
    {' '}{status.data.running?'Processando…':'As classificações rodam em segundo plano.'}</>}
    {' '}Você pode corrigir ou remover nos detalhes. Suas escolhas têm prioridade e os saldos não mudam.
    {' '}Casos ambíguos continuam sem categoria.
    {' '}No máximo três etapas: histórico/correções, RAG e uma chamada ao LLM. Ao vincular um banco pela primeira vez, a organização começa automaticamente; uma desativação salva é respeitada.
    {!status.data.enabled&&' Ao ativar, descrições e exemplos deste perfil serão processados pela IA na nuvem, sem valores ou credenciais bancárias.'}
    {status.data.enabled&&<Button disabled={retry.isPending||status.data.running} onClick={()=>retry.mutate()}>Retomar processamento interrompido</Button>}
    {(change.isError||retry.isError)&&' Não foi possível concluir. Atualize a página e tente novamente.'}
  </Alert>;
}

export function CategoryAutomationSync(){
  const client=useQueryClient();
  const status=useQuery({queryKey:['categoryAutomation'],queryFn:()=>api<Status>('/banking/category-automation'),refetchInterval:query=>query.state.data?.enabled?5000:false});
  const lastRun=status.data?.lastRun;
  useEffect(()=>{if(lastRun)void Promise.all(['movementFeed','bankAssistance','summary','budgets','investments'].map(key=>client.invalidateQueries({queryKey:[key]})));},[lastRun,client]);
  return null;
}
