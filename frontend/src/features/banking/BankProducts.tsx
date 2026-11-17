import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Alert,Box,Button,Checkbox,FormControlLabel,MenuItem,Paper,Stack,TextField,Typography } from '@mui/material';
import { api,json } from '../../lib/api';
import { refreshFinances } from '../../lib/financialQueries';
import { sameMoney } from '../../lib/sameMoney';
import { money,civilDate } from '../../lib/format';
import { Failure,Loading,FormDialog } from '../../components/Shared';
import { useBankProducts,type Card,type CardBill } from './useBankProducts';
import { CardChargeEditor } from './CardChargeEditor';
interface Link {id:string;itemId:string;name:string;active:boolean}
const states:Record<string,string>={NOT_READ:'Ainda não consultado',NO_DATA:'Nenhum dado retornado',READY:'Dados disponíveis',PARTIAL:'Cobertura parcial',UNAVAILABLE:'Acesso não disponível nesta conexão',ERROR:'Falha na leitura; dados anteriores podem estar desatualizados'};

export function BankProducts({links}:{links:Link[]}){
 const client=useQueryClient(),query=useBankProducts(),[error,setError]=useState(''),[busy,setBusy]=useState(false),[automatic,setAutomatic]=useState(false);
 const [bill,setBill]=useState<{card:Card;bill:CardBill}|null>(null),[charge,setCharge]=useState<string|null>(null),[readLink,setReadLink]=useState<Link|null>(null);
 async function read(){if(!readLink)return;setBusy(true);setError('');try{await api(`/banking/links/${readLink.id}/products/read`,{method:'POST',body:json({automatic})});await refreshFinances(client);setReadLink(null);}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 async function pause(link:Link){setBusy(true);setError('');try{await api(`/banking/links/${link.id}/products/pause`,{method:'POST'});await refreshFinances(client);}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 return <Paper variant="outlined" sx={{p:2,my:3}}><Stack spacing={2}>
  <Typography variant="h6" component="h2">Cartões, faturas e investimentos do banco</Typography>
  <Typography>Abrir esta seção mostra a última leitura salva. Uma leitura adicional consulta apenas produtos disponíveis no seu conector; não inicia pagamentos, aplicações ou importação de lançamentos.</Typography>
  {error&&<Alert severity="error">{error}</Alert>}
  {links.filter(l=>l.active).map(link=>{const state=query.data?.connections.find(s=>s.itemId===link.itemId);return <Box key={link.id}><Typography>{link.name} · Cartões: {states[state?.cardsStatus??'NOT_READ']} · Investimentos: {states[state?.investmentsStatus??'NOT_READ']}</Typography><Button disabled={busy} onClick={()=>{setReadLink(link);setAutomatic(state?.automatic??false);setError('');}}>Consultar cobertura e dados adicionais de {link.name}</Button>{state?.automatic&&<Button disabled={busy} onClick={()=>void pause(link)}>Pausar leituras adicionais automáticas</Button>}</Box>;})}
  {query.isPending?<Loading/>:query.error?<Failure error={query.error}/>:<>
   {query.data.cards.length===0&&<Typography>Nenhum cartão disponível na última leitura. Isso não significa fatura zero ou ausência de dívida.</Typography>}
   {query.data.cards.map(card=><Paper variant="outlined" sx={{p:2}} key={card.id}><Stack spacing={1}>
    <Typography variant="h6" component="h3">{card.name}</Typography><Typography variant="caption">Consulta em {new Date(card.checkedAt).toLocaleString('pt-BR')}</Typography>
    {!card.billsAvailable&&<Alert severity="warning">Não foi possível validar todas as faturas desta leitura. Valores incompatíveis não foram substituídos ou estimados; dados anteriores podem estar desatualizados. A conciliação fica indisponível enquanto a cobertura estiver incompleta.</Alert>}
    {card.bills.map(b=><Box key={b.id} sx={{borderTop:1,borderColor:'divider',pt:1}}><Typography>Fatura com vencimento em {civilDate(b.dueDate)} · {money(b.total)}</Typography><Typography>{b.detailCount} detalhes · soma líquida {money(b.detailedTotal)} · {b.pendingCount} pendentes · {b.reconciled?'Conciliada: consumo vem dos detalhes':'Não conciliada: detalhes ainda fora do consumo'}</Typography><Button onClick={()=>setBill({card,bill:b})}>Conferir fatura e pagamento</Button></Box>)}
    <Box component="details"><Box component="summary" sx={{cursor:'pointer',py:1}}>Ver detalhes e parcelas do cartão ({card.charges.length})</Box><ChargeList card={card} onSelect={setCharge}/></Box>
   </Stack></Paper>)}
   <Typography>Posições de investimentos retornadas: {query.data.positions.length}. Consulte os valores e datas no módulo Investimentos; valores ausentes não serão estimados.</Typography>
  </>}
  {readLink&&<FormDialog title="Consultar produtos adicionais" dirty={false} busy={busy} onClose={()=>setReadLink(null)} actions={<Button disabled={busy} onClick={()=>void read()}>Confirmar leitura adicional</Button>}><Stack spacing={2} pt={1}>
   <Typography>Vamos consultar cartões/faturas/detalhes e posições de investimentos desta conexão já autorizada. A cobertura depende do banco e do consentimento existente. Não serão enviados valores ao LLM nem criados lançamentos.</Typography>
   <FormControlLabel control={<Checkbox checked={automatic} onChange={(_,checked)=>setAutomatic(checked)} disabled={busy}/>} label="Repetir esta leitura junto às próximas atualizações bancárias"/>
   <Typography variant="caption">Sem esta opção, a consulta é somente sob demanda. Pode haver dados parciais; conciliação e categorias continuam revisáveis.</Typography>
   {error&&<Alert severity="error">{error}</Alert>}
  </Stack></FormDialog>}
  {bill&&<ReconcileBill card={bill.card} bill={bill.bill} onClose={()=>setBill(null)}/>}
  {charge&&<CardChargeEditor id={charge} onClose={()=>setCharge(null)}/>}
 </Stack></Paper>;
}
function ChargeList({card,onSelect}:{card:Card;onSelect:(id:string)=>void}){
 const [shown,setShown]=useState(20);
 return <Stack spacing={1}>{card.charges.slice(0,shown).map(c=><Box key={c.id}><Typography>{c.description} · {money(c.amount)} · {civilDate(c.date)} · {c.direction==='CREDIT'?'Crédito/estorno':'Débito'} · {c.status==='POSTED'?'Efetivado':'Pendente'}{c.installment&&c.installments?` · Parcela ${c.installment}/${c.installments}`:''}</Typography><Button onClick={()=>onSelect(c.id)}>Categorizar detalhe</Button></Box>)}{shown<card.charges.length&&<Button onClick={()=>setShown(shown+20)}>Mostrar mais detalhes</Button>}</Stack>;
}
function ReconcileBill({card,bill,onClose}:{card:Card;bill:CardBill;onClose:()=>void}){
 const client=useQueryClient(),query=useBankProducts(),[payment,setPayment]=useState(''),[confirmed,setConfirmed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const candidates=query.data?.payments.filter(p=>sameMoney(p.amount,bill.total))??[];
 async function reconcile(){const entry=candidates.find(p=>p.id===payment);if(!entry)return;setBusy(true);setError('');try{await api(`/banking/card-bills/${bill.id}/reconcile`,{method:'POST',body:json({entryId:entry.id,entryVersion:entry.version,revision:bill.revision,confirmed})});await refreshFinances(client);onClose();}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 async function undo(){setBusy(true);setError('');try{await api(`/banking/card-bills/${bill.id}/undo`,{method:'POST'});await refreshFinances(client);onClose();}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
 const matches=card.billsAvailable&&bill.detailCount>0&&bill.pendingCount===0&&sameMoney(bill.detailedTotal,bill.total);
 return <FormDialog title="Conferir fatura e pagamento" dirty={confirmed} busy={busy} onClose={onClose} actions={bill.reconciled?<Button disabled={busy} onClick={()=>void undo()}>Desfazer conciliação</Button>:<Button disabled={busy||!confirmed||!payment||!matches} onClick={()=>void reconcile()}>Confirmar conciliação da fatura</Button>}>
  <Stack spacing={2} pt={1}><Typography>{card.name} · vencimento {civilDate(bill.dueDate)} · total {money(bill.total)}</Typography>
   <Alert severity="info">Conciliar troca o pagamento pelos detalhes nos indicadores de consumo. O pagamento continua no fluxo de caixa e o saldo bancário permanece igual. A competência dos detalhes será o mês de vencimento da fatura.</Alert>
   {!matches&&<Alert severity="warning">A cobertura não está completa ou os totais não conferem. Não vamos conciliar pagamentos parciais, juros, tarifas ou compras ausentes por estimativa. Confira os detalhes antes de continuar.</Alert>}
   {!bill.reconciled&&<><TextField select label="Pagamento bancário correspondente" value={payment} onChange={e=>{setPayment(e.target.value);setConfirmed(false);}}><MenuItem value="">Escolha o pagamento</MenuItem>{candidates.map(p=><MenuItem key={p.id} value={p.id}>{p.description} · {civilDate(p.date)} · {money(p.amount)}</MenuItem>)}</TextField>
    {candidates.length===0&&<Typography>Nenhum pagamento elegível com esse total. A soma igual é apenas um filtro: confirme também a origem e a fatura correta.</Typography>}
    <FormControlLabel control={<Checkbox checked={confirmed} disabled={!matches} onChange={(_,checked)=>setConfirmed(checked)}/>} label="Conferi os detalhes, estornos e parcelas; a fatura está completa e este pagamento corresponde a ela"/>
   </>}
   {error&&<Alert severity="error">{error}</Alert>}
  </Stack>
 </FormDialog>;
}
