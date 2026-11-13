import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Alert, Button, Stack, TextField, Typography } from '@mui/material';
import { api, json } from '../../lib/api';
import type { FinancialRecord } from '../../lib/types';
import { civilDate, money } from '../../lib/format';
import { FormDialog } from '../../components/Shared';
import { BankAssistance } from './BankAssistance';

export function BankRecordDetail({record,onClose}:{record:FinancialRecord;onClose:()=>void}) {
  const client=useQueryClient();
  const [reason,setReason]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  async function changeVisibility() {
    if(busy)return; setBusy(true);setError('');
    try {
      await api(`/banking/entries/${record.bankEntryId}/visibility`,{method:'POST',body:json({version:record.bankEntryVersion,hidden:!record.hidden,reason})});
      onClose();
      await Promise.all(['movementFeed','bankEntries','summary','budgets'].map(key=>client.invalidateQueries({queryKey:[key]})));
    } catch(e) {setError((e as Error).message);} finally {setBusy(false);}
  }
  return <FormDialog title="Registro bancário" dirty={!!reason} busy={busy} onClose={onClose}
    actions={<Button variant="contained" color={record.hidden?'primary':'warning'} disabled={busy} onClick={()=>void changeVisibility()}>{record.hidden?'Restaurar registro':'Ocultar da lista e dos relatórios'}</Button>}>
    <Stack spacing={2} pt={1}>
      {error&&<Alert severity="error">{error}</Alert>}
      <Typography variant="h6" sx={{overflowWrap:'anywhere'}}>{record.description}</Typography>
      <Typography className="money">{money(record.amount)} · {civilDate(record.operationDate)}</Typography>
      <Typography>{record.bankProviderStatus==='POSTED'?'Efetivado no banco':'Pendente no banco · fora dos totais realizados'}</Typography>
      {!record.hidden&&<BankAssistance record={record} onChanged={onClose}/>}
      {record.status==='CANCELLED'&&<Alert severity="warning">O lançamento associado está cancelado nos relatórios. Restaurar a visibilidade não confirma esse lançamento.</Alert>}
      {record.bankState==='CHANGED'&&<Alert severity="warning">O banco atualizou este registro. Se houver uma classificação anterior, confira o vínculo para ajustar os dados classificados.</Alert>}
      <Alert severity="info">{record.hidden?'Restaurar devolve este registro à lista e aos relatórios, quando efetivado.':'Ocultar tira este registro da lista principal e dos relatórios. Ele continuará na aba Ocultas e poderá ser restaurado.'} O saldo informado pelo banco permanece igual.</Alert>
      <Typography variant="body2">Entradas e saídas sem categoria entram automaticamente. Transferências próprias, empréstimos e aplicações podem precisar ficar fora dos totais de renda e gastos.</Typography>
      <TextField label="Motivo (opcional)" value={reason} onChange={e=>setReason(e.target.value)} multiline rows={2} slotProps={{htmlInput:{maxLength:300}}}/>
      {!record.hidden&&<Button component={Link} to={'/banking?link='+encodeURIComponent(record.bankLinkId??'')} onClick={onClose}>Organizar categoria ou vínculo</Button>}
      {record.source!=='BANK'&&<Button component={Link} to={'/movements?detail='+record.id} onClick={onClose}>Ver lançamento associado</Button>}
    </Stack>
  </FormDialog>;
}
