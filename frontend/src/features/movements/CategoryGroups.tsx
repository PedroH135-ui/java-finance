import {useState} from 'react';
import {useMutation,useQueryClient} from '@tanstack/react-query';
import {Alert,Button,MenuItem,Paper,Stack,TextField,Typography} from '@mui/material';
import type {Category,FinancialRecord} from '../../lib/types';
import {api,json} from '../../lib/api';
import {money,civilDate} from '../../lib/format';
import {refreshFinances} from '../../lib/financialQueries';

export function CategoryGroups({records,categories}:{records:FinancialRecord[];categories:Category[]}) {
  const groups=new Map<string,FinancialRecord[]>();
  for(const r of records){
    if(r.source!=='BANK'||!r.bankEntryId||r.hidden||r.categoryId||r.categorySource||r.suggestedNature||
      r.financialNature||r.bankProviderStatus!=='POSTED'||!['INCOME','EXPENSE'].includes(r.kind))continue;
    const key=JSON.stringify([r.accountId,r.kind,r.description.toLocaleLowerCase('pt-BR').trim().replace(/\s+/g,' ')]);
    groups.set(key,[...(groups.get(key)??[]),r]);
  }
  return <>{Array.from(groups).filter(([,rows])=>rows.length>1).map(([key,rows])=><Review key={key} rows={rows} categories={categories}/>)}</>;
}

function Review({rows,categories}:{rows:FinancialRecord[];categories:Category[]}) {
  const [choice,setChoice]=useState('');
  const client=useQueryClient();
  const apply=useMutation({mutationFn:()=>api('/banking/category-groups',{method:'POST',body:json({
    categoryId:choice,entries:rows.map(r=>({id:r.bankEntryId,version:r.bankEntryVersion}))})}),
    onSettled:()=>refreshFinances(client)});
  return <Paper variant="outlined" sx={{p:2,mb:2}}><details><summary>Revisar {rows.length} registros de {rows[0].description} nesta página</summary>
    <Stack spacing={1} mt={1}>{rows.map(r=><Typography key={r.id}>{civilDate(r.operationDate)} · {r.description} · {money(r.amount)}</Typography>)}
      <TextField select label="Categoria para o grupo" value={choice} onChange={e=>setChoice(e.target.value)}>
        {categories.filter(c=>!c.archived&&c.kind===rows[0].kind).map(c=><MenuItem key={c.id} value={c.id}>{c.name}</MenuItem>)}
      </TextField>
      <Button disabled={!choice||apply.isPending} onClick={()=>apply.mutate()}>Confirmar categoria nos {rows.length} registros</Button>
      {apply.error&&<Alert severity="error">{apply.error.message}</Alert>}
    </Stack></details></Paper>;
}
