import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Alert,Button,Checkbox,FormControlLabel,Stack,Typography } from '@mui/material';
import { api,json } from '../../lib/api';
import { refreshFinances } from '../../lib/financialQueries';
import type { FinancialRecord } from '../../lib/types';

export function SalaryAction({record,onChanged}:{record:FinancialRecord;onChanged:()=>void}){
 const client=useQueryClient(),[open,setOpen]=useState(false),[future,setFuture]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 if(record.kind!=='INCOME'||record.source!=='BANK'||record.hidden||record.bankProviderStatus!=='POSTED'||(record.financialNature&&record.financialNature!=='ORDINARY'))return null;
 async function mark(){setBusy(true);setError('');try{
  await api(`/banking/entries/${record.bankEntryId}/salary`,{method:'POST',body:json({version:record.bankEntryVersion,futureReceipts:future})});
  await refreshFinances(client);onChanged();
 }catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 return <Stack spacing={1}>
  <Button variant="outlined" disabled={busy} onClick={()=>setOpen(!open)}>É meu salário</Button>
  {open&&<>
   <Typography>Este recebimento ficará na categoria Salário. Seu salário mensal previsto continua manual; nenhum saldo será alterado.</Typography>
   <FormControlLabel control={<Checkbox checked={future} disabled={busy} onChange={(_,checked)=>setFuture(checked)}/>} label="Criar regra para próximos recebimentos com a mesma descrição nesta conta"/>
   <Typography variant="caption">A regra é sua, funciona sem LLM e sem ativar IA. Vale para novos registros a partir de hoje; não reclassifica o histórico. Você pode pausá-la nas Configurações.</Typography>
   {error&&<Alert severity="error">{error}</Alert>}
   <Button variant="contained" disabled={busy} onClick={()=>void mark()}>Confirmar salário recebido</Button>
  </>}
 </Stack>;
}
