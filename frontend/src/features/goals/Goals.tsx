import { useState } from 'react';
import { useQuery,useQueryClient } from '@tanstack/react-query';
import { Alert,Button,Paper,Stack,TextField,Typography } from '@mui/material';
import { api,json } from '../../lib/api';
import { money } from '../../lib/format';
import { Failure,Loading,PageHeader } from '../../components/Shared';
import { PlanningFinances,type Assessment,type PlanningContext } from './PlanningFinances';
interface State {target:string|null;saved:string|null;months:number|null;title:string}
interface Reply {message:string;state:State;monthly:string|null;available:string|null;ready:boolean;method:string;finances:PlanningContext;assessment:Assessment|null}
interface Goal {id:string;title:string;target:string;saved:string;months:number;monthly:string;version:number}
export function Goals(){
 const client=useQueryClient(),[message,setMessage]=useState(''),[reply,setReply]=useState<Reply|null>(null),[messages,setMessages]=useState<{role:string;text:string}[]>([]),[busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
 const goals=useQuery({queryKey:['goals'],queryFn:()=>api<Goal[]>('/goals')});
 const [progress,setProgress]=useState<Record<string,string>>({});
 async function chat(){if(!message.trim())return;setBusy(true);setError('');const text=message;try{const result=await api<Reply>('/goals/chat',{method:'POST',body:json({message:text,state:reply?.state??null})});setMessages(previous=>[...previous,{role:'Você',text},{role:'Planejador',text:result.message}].slice(-20));setReply(result);setMessage('');}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 async function save(){if(!reply?.ready)return;setBusy(true);setError('');try{await api('/goals',{method:'POST',body:json({...reply.state,confirmed:true})});await Promise.all(['goals','planningFinances'].map(key=>client.invalidateQueries({queryKey:[key]})));setReply({...reply,ready:false,assessment:null});setNotice('Meta salva, sem reservar ou movimentar dinheiro.');}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 async function update(goal:Goal){setBusy(true);setError('');try{await api('/goals/'+goal.id,{method:'PATCH',body:json({saved:progress[goal.id]??goal.saved,version:goal.version})});await Promise.all(['goals','planningFinances'].map(key=>client.invalidateQueries({queryKey:[key]})));setReply(previous=>previous?{...previous,ready:false,assessment:null}:null);setNotice('Progresso informado atualizado. Recalcule propostas anteriores. Nenhum saldo foi alterado.');}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 return <><PageHeader title="Metas e planejamento" subtitle="Converse para construir um plano; você decide o que salvar."/>
  <Alert severity="info" sx={{mb:2}}>Planejamento guiado: metas, prazo, valor já reservado, resumo do mês e consultas de compra. Esta primeira versão interpreta esses pedidos sem usar o LLM. Confira as informações antes de decidir.</Alert>
  {error&&<Alert severity="error" sx={{mb:2}}>{error}</Alert>}{notice&&<Alert severity="success" sx={{mb:2}}>{notice}</Alert>}
  <PlanningFinances assessment={reply?.assessment??null} onChanged={()=>setReply(previous=>previous?{...previous,ready:false,assessment:null}:null)}/>
  <Paper variant="outlined" sx={{p:2,mb:3}}><Stack spacing={2}>
   <Typography>Exemplo: “Quero viajar e guardar 3000”. Depois informe “em 6 meses” e, se quiser, “já tenho 600 reservado”.</Typography>
   <Stack spacing={1} role="log" aria-label="Conversa de planejamento">{messages.map((m,i)=><Typography key={i}><strong>{m.role}:</strong> {m.text}</Typography>)}</Stack>
   <TextField label="Converse sobre sua meta" value={message} onChange={e=>setMessage(e.target.value)} multiline rows={2} slotProps={{htmlInput:{maxLength:600}}}/>
   <Button variant="contained" disabled={busy||!message.trim()} onClick={()=>void chat()}>Enviar mensagem</Button>
   {reply?.ready&&<Button variant="outlined" disabled={busy} onClick={()=>void save()}>Confirmar e salvar meta</Button>}
   <Typography variant="caption">A sobra registrada pode incluir dados incompletos e naturezas ainda não confirmadas. O plano não é uma promessa de rentabilidade nem reserva bancária. A conversa fica nesta tela; apenas metas confirmadas são salvas.</Typography>
  </Stack></Paper>
  <Typography variant="h6" mb={2}>Minhas metas</Typography>
  {goals.isPending?<Loading/>:goals.error?<Failure error={goals.error}/>:<Stack spacing={2}>{goals.data.length===0?<Typography>Nenhuma meta salva.</Typography>:goals.data.map(g=><Paper key={g.id} variant="outlined" sx={{p:2}}><Stack spacing={1.5}><Typography variant="h6">{g.title}</Typography><Typography>Objetivo: {money(g.target)} · Progresso informado: {money(g.saved)}</Typography><Typography>Plano: {money(g.monthly)} por mês, em {g.months} meses de planejamento.</Typography><TextField label={'Valor já reservado para '+g.title} value={progress[g.id]??g.saved} onChange={e=>setProgress({...progress,[g.id]:e.target.value})}/><Button disabled={busy} onClick={()=>void update(g)}>Salvar progresso informado</Button></Stack></Paper>)}</Stack>}
 </>;
}
