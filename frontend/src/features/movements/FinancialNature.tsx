import { useState } from 'react';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { Alert,Button,Checkbox,FormControlLabel,Link,MenuItem,Stack,TextField,Typography } from '@mui/material';
import { api,json } from '../../lib/api';
import { Failure,Loading } from '../../components/Shared';
import type { FinancialRecord } from '../../lib/types';
interface Status {confirmedNature:string|null;instrument:string|null;positionId:string|null;changed:boolean;suggestion:{nature:string;instrument:string;explanation:string;sourceUrl:string}|null}
const names:Record<string,string>={ORDINARY:'Renda ou consumo comum',OWN_TRANSFER:'Transferência entre minhas próprias contas',CARD_PAYMENT:'Pagamento de fatura do cartão',INVESTMENT_IN:'Aplicação em investimento',INVESTMENT_OUT:'Resgate de investimento'};
export function FinancialNature({record,onChanged}:{record:FinancialRecord;onChanged:()=>void}){
  const client=useQueryClient(),[choice,setChoice]=useState<string>(),[product,setProduct]=useState<string>(),[position,setPosition]=useState<string>(),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const status=useQuery({queryKey:['financialNature',record.bankEntryId],queryFn:()=>api<Status>(`/banking/entries/${record.bankEntryId}/nature`)});
  const positions=useQuery({queryKey:['investments'],queryFn:()=>api<{positions:{id:string;name:string;instrument:string}[]}>('/investments')});
  if(status.isPending)return <Loading/>;
  if(status.error)return <Failure error={status.error}/>;
  const data=status.data,nature=choice??data.confirmedNature??data.suggestion?.nature??'ORDINARY',instrument=product??(data.confirmedNature?data.instrument:data.suggestion?.instrument)??'OTHER';
  const investment=nature==='INVESTMENT_IN'||nature==='INVESTMENT_OUT';
  async function save(){setBusy(true);setError('');try{
    await api(`/banking/entries/${record.bankEntryId}/nature`,{method:'POST',body:json({version:record.bankEntryVersion,nature,instrument:investment?(instrument==='NONE'?'OTHER':instrument):'NONE',positionId:investment?((position??data.positionId)||null):null,confirmed})});
    await Promise.all(['movementFeed','financialNature','summary','budgets','investments','goals'].map(key=>client.invalidateQueries({queryKey:[key]})));onChanged();
  }catch(e){setError((e as Error).message);}finally{setBusy(false);}}
  return <Stack spacing={1.5}>
    <Typography variant="subtitle1" fontWeight={650}>Natureza financeira</Typography>
    {data.changed&&<Alert severity="warning">O banco mudou os dados. A confirmação anterior deixou de valer; confira novamente.</Alert>}
    {data.confirmedNature&&<Alert severity="success">Confirmado: {names[data.confirmedNature]}. {data.confirmedNature==='ORDINARY'?'Entra nos indicadores comuns.':'Fora dos indicadores de consumo e economia; permanece no extrato e no fluxo de caixa.'}</Alert>}
    {!data.confirmedNature&&data.suggestion&&<Alert severity="info">Identificação sugerida: {names[data.suggestion.nature]}. {data.suggestion.explanation} <Link href={data.suggestion.sourceUrl} target="_blank" rel="noreferrer">Fonte do contexto</Link></Alert>}
    <TextField select label="Natureza financeira do registro" value={nature} onChange={e=>{setChoice(e.target.value);setConfirmed(false);setPosition('');}} disabled={busy}>
      {Object.entries(names).map(([id,label])=><MenuItem key={id} value={id}>{label}</MenuItem>)}
    </TextField>
    {investment&&<>
      <TextField select label="Produto do investimento" value={instrument==='NONE'?'OTHER':instrument} onChange={e=>{setProduct(e.target.value);setPosition('');setConfirmed(false);}}>
        <MenuItem value="RDB">RDB · renda fixa</MenuItem><MenuItem value="CDB">CDB · renda fixa</MenuItem><MenuItem value="OTHER">Outro investimento</MenuItem>
      </TextField>
      <TextField select label="Vincular a investimento cadastrado (opcional)" value={position??data.positionId??''} onChange={e=>setPosition(e.target.value)}>
        <MenuItem value="">Sem vínculo</MenuItem>{positions.data?.positions.filter(p=>p.instrument===instrument).map(p=><MenuItem key={p.id} value={p.id}>{p.name}</MenuItem>)}
      </TextField>
    </>}
    <Typography variant="body2">A categoria organiza o gasto; esta confirmação decide se ele é renda/consumo. Não realiza transferência, compra, resgate ou alteração de saldo.</Typography>
    <FormControlLabel label="Confirmo a natureza escolhida e sua aplicação aos indicadores" control={<Checkbox checked={confirmed} onChange={(_,value)=>setConfirmed(value)}/>}/>
    {error&&<Alert severity="error">{error}</Alert>}
    <Button variant="outlined" disabled={!confirmed||busy} onClick={()=>void save()}>Confirmar natureza financeira</Button>
  </Stack>;
}
