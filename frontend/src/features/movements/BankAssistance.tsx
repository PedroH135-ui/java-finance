import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Box, Button, Chip, Divider, MenuItem, Stack, TextField, Typography } from '@mui/material';
import { api, json } from '../../lib/api';
import type { Category, FinancialRecord } from '../../lib/types';
import { civilDate, money } from '../../lib/format';
import { Failure, Loading } from '../../components/Shared';

interface Assistance {
  entryVersion:number; currentCategoryId:string|null; canCategorize:boolean; unavailableReason:string;
  categorySuggestion:{categoryId:string|null;confidence:string;method:string;reason:string;evidenceCount:number;modelUsed:boolean};
  transferCandidates:{id:string;accountName:string;description:string;amount:string;date:string;version:number}[];
  transfer:{id:string;peerId:string;version:number;valid:boolean}|null;
}

export function BankAssistance({record,onChanged}:{record:FinancialRecord;onChanged:()=>void}) {
  const client=useQueryClient();
  const query=useQuery({queryKey:['bankAssistance',record.bankEntryId],queryFn:()=>api<Assistance>(`/banking/entries/${record.bankEntryId}/assistance`),staleTime:0});
  const categories=useQuery({queryKey:['categories'],queryFn:()=>api<Category[]>('/categories')});
  const [selected,setSelected]=useState<string|undefined>(),[peer,setPeer]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  async function apply(action:string,body:unknown) {
    if(busy)return;setBusy(true);setError('');
    try {
      await api(`/banking/entries/${record.bankEntryId}/${action}`,{method:'POST',body:json(body)});
      onChanged();
      await Promise.all(['movementFeed','bankAssistance','bankEntries','summary','budgets'].map(key=>client.invalidateQueries({queryKey:[key]})));
    } catch(e){setError((e as Error).message);} finally{setBusy(false);}
  }
  if(query.isPending)return <Loading/>;
  if(query.error)return <Failure error={query.error} retry={()=>void query.refetch()}/>;
  const data=query.data,suggestion=data.categorySuggestion;
  const choice=selected??data.currentCategoryId??suggestion.categoryId??'';
  const candidate=data.transferCandidates.find(c=>c.id===peer);
  return <Stack spacing={2}>
    <Divider/>
    {error&&<Alert severity="error">{error}</Alert>}
    <Typography variant="subtitle1" fontWeight={650}>Transferências entre suas contas</Typography>
    {data.transfer?<>
      <Alert severity={data.transfer.valid?'success':'warning'}>{data.transfer.valid?'Transferência própria identificada. Os dois registros ficam fora de renda e gastos, sem alterar os saldos.':'O banco mudou os dados usados na identificação. Os registros voltaram aos totais; confira e identifique novamente se forem uma transferência própria.'}</Alert>
      <Button disabled={busy} onClick={()=>void apply('transfer/undo',{version:data.transfer!.version})}>Desfazer identificação de transferência</Button>
    </>:data.transferCandidates.length>0?<>
      <Alert severity="info">Encontramos mesmo valor, direção oposta, outra conta vinculada e até dois dias de diferença. Isso é uma sugestão: confirme somente se as duas contas forem suas e representarem a mesma transferência.</Alert>
      {data.transferCandidates.length>1&&<Alert severity="warning">Há mais de um par possível. Escolha com atenção; nada será identificado automaticamente.</Alert>}
      <TextField select label="Operação na outra conta" value={peer} onChange={e=>setPeer(e.target.value)} disabled={busy}>
        {data.transferCandidates.map(c=><MenuItem key={c.id} value={c.id}>{c.accountName} · {civilDate(c.date)} · {money(c.amount)} · {c.description}</MenuItem>)}
      </TextField>
      <Button variant="outlined" disabled={busy||!candidate} onClick={()=>candidate&&void apply('transfer',{version:data.entryVersion,peerId:candidate.id,peerVersion:candidate.version})}>Confirmar transferência entre minhas contas</Button>
    </>:<Typography variant="body2" color="text.secondary">Nenhum par disponível. Para identificar um par, precisamos dos registros das duas contas vinculadas. Uma descrição de PIX sozinha não comprova uma transferência própria.</Typography>}
    <Divider/><Typography variant="subtitle1" fontWeight={650}>Categoria sugerida</Typography>
    {!data.canCategorize?<Typography variant="body2">{data.unavailableReason}</Typography>:<>
      {!data.currentCategoryId&&<Box>
        <Typography variant="body2">{suggestion.reason}</Typography>
        {suggestion.categoryId&&<Stack direction="row" gap={1} flexWrap="wrap" mt={1}>
          <Chip size="small" label={suggestion.method==='HISTORY'?'Seu histórico':suggestion.method==='RULE'?'Regra de descrição':'Modelo local'}/>
          <Chip size="small" label={'Consistência '+(suggestion.confidence==='HIGH'?'alta':suggestion.confidence==='MEDIUM'?'moderada':'baixa')}/>
          {suggestion.evidenceCount>0&&<Chip size="small" label={suggestion.evidenceCount+' exemplo(s) relevante(s)'}/>}
        </Stack>}
      </Box>}
      <Typography variant="caption" color="text.secondary">A sugestão não é certeza. Você pode escolher outra categoria; só aplicamos depois do seu clique.</Typography>
      {categories.isPending?<Loading/>:categories.error?<Failure error={categories.error}/>:<>
        <TextField select label="Categoria para este registro" value={choice} onChange={e=>setSelected(e.target.value)} disabled={busy}>
          <MenuItem value="">Escolha uma categoria</MenuItem>
          {categories.data.filter(c=>!c.archived&&c.kind===record.kind).map(c=><MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
        </TextField>
        <Button variant="outlined" disabled={busy||!choice} onClick={()=>void apply('category',{version:data.entryVersion,categoryId:choice})}>Aplicar categoria escolhida</Button>
        {data.currentCategoryId&&<Button disabled={busy} onClick={()=>void apply('category',{version:data.entryVersion,categoryId:null})}>Remover categoria</Button>}
      </>}
    </>}
  </Stack>;
}
