import { useEffect } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert } from '@mui/material';
import { api } from '../../lib/api';

interface Status {enabled:boolean;running:boolean;categorized:number;awaiting:number;lastRun:string|null}

export function CategoryAutomationNotice() {
  const client=useQueryClient();
  const status=useQuery({queryKey:['categoryAutomation'],queryFn:()=>api<Status>('/banking/category-automation'),refetchInterval:3000});
  const lastRun=status.data?.lastRun;
  useEffect(()=>{
    if(lastRun)void Promise.all(['movementFeed','bankAssistance','summary','budgets'].map(key=>client.invalidateQueries({queryKey:[key]})));
  },[lastRun,client]);
  if(!status.data?.enabled)return null;
  return <Alert severity="info" sx={{mb:2}}>
    Classificação automática local ativada · {status.data.categorized} registro(s) categorizado(s) automaticamente.
    {' '}{status.data.running?'Processando…':'As classificações rodam em segundo plano.'}
    {' '}Você pode corrigir ou remover nos detalhes. Suas escolhas têm prioridade e os saldos não mudam.
    {' '}Casos ambíguos continuam sem categoria.
  </Alert>;
}
