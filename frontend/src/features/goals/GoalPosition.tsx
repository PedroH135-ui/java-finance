import {useState} from 'react';
import {useMutation,useQuery,useQueryClient} from '@tanstack/react-query';
import {Alert,Button,MenuItem,Stack,TextField,Typography} from '@mui/material';
import {api,json} from '../../lib/api';
import {bankDateTime,money} from '../../lib/format';

interface Position {positionId:string;goalId:string;name:string;status:string;present:boolean;balance:string;missing:string;checkedAt:string}
const useLinks=()=>useQuery({queryKey:['goalPositions'],queryFn:()=>api<Position[]>('/goals/positions')});

export function PositionGoal({id}:{id:string}){
  const links=useLinks();
  const goals=useQuery({queryKey:['goals'],queryFn:()=>api<{id:string;title:string}[]>('/goals')});
  const client=useQueryClient(),[choice,setChoice]=useState<string>();
  const previous=links.data?.find(l=>l.positionId===id)?.goalId??'';
  const mutation=useMutation({mutationFn:()=>api('/goals/positions/'+id,{method:'PUT',body:json({goalId:choice||null,previousGoalId:previous||null})}),
    onSettled:()=>client.invalidateQueries({queryKey:['goalPositions']})});
  if(links.error||goals.error)return <Alert severity="error">Falha ao consultar metas vinculadas.</Alert>;
  if(!goals.data?.length||!links.data)return null;
  return <Stack spacing={1} mt={1}><TextField select label="Meta desta posição" value={choice??previous} onChange={e=>setChoice(e.target.value)}>
    <MenuItem value="">Sem vínculo</MenuItem>{goals.data.map(g=><MenuItem key={g.id} value={g.id}>{g.title}</MenuItem>)}</TextField>
    <Button disabled={mutation.isPending||choice===undefined||choice===previous} onClick={()=>mutation.mutate()}>Salvar vínculo com a meta</Button>
    {mutation.error&&<Alert severity="error">{mutation.error.message}</Alert>}
  </Stack>;
}
export function GoalPosition({id}:{id:string}){
  const links=useLinks(),p=links.data?.find(l=>l.goalId===id);
  const client=useQueryClient();
  const unlink=useMutation({mutationFn:(positionId:string)=>api('/goals/positions/'+positionId,{method:'PUT',body:json({goalId:null,previousGoalId:id})}),
    onSettled:()=>client.invalidateQueries({queryKey:['goalPositions']})});
  if(links.error)return <Alert severity="error">Falha ao consultar a posição da meta.</Alert>;
  if(!p)return null;
  return <Stack gap={1}><Typography>Posição vinculada · {p.name} · {p.present&&p.status==='ACTIVE'?
    money(p.balance)+' no banco · Faltam '+money(p.missing)+' em relação ao alvo':'Posição inativa ou ausente na última leitura'} · Consulta em {bankDateTime(p.checkedAt)}. Comparação independente do progresso informado.</Typography>
    <Button disabled={unlink.isPending} onClick={()=>unlink.mutate(p.positionId)}>Desvincular posição desta meta</Button>
    {unlink.error&&<Alert severity="error">{unlink.error.message}</Alert>}
  </Stack>;
}
