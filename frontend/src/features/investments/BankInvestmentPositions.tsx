import { Alert,Paper,Stack,Typography } from '@mui/material';
import { Link } from 'react-router-dom';
import { useBankProducts } from '../banking/useBankProducts';
import { money,civilDate } from '../../lib/format';
import { Failure,Loading } from '../../components/Shared';
const statuses:Record<string,string>={ACTIVE:'Ativo',PENDING:'Pendente',MATURED:'Vencido',REDEEMED:'Resgatado'};
export function BankInvestmentPositions(){
 const query=useBankProducts();
 return <Paper variant="outlined" sx={{p:2,my:3}}><Stack spacing={2}>
  <Typography variant="h6" component="h2">Posições informadas pelo banco</Typography>
  <Typography>Valores da última leitura adicional, separados das posições manuais para não contar o mesmo investimento duas vezes. <Link to="/banking">Conferir cobertura e atualização</Link></Typography>
  {query.isPending?<Loading/>:query.error?<Failure error={query.error}/>:<>
   {query.data.connections.some(c=>['ERROR','UNAVAILABLE'].includes(c.investmentsStatus))&&<Alert severity="warning">Há uma conexão sem leitura atual disponível. As posições salvas podem estar desatualizadas.</Alert>}
   {query.data.positions.length===0?<Typography>Nenhuma posição retornada até agora. Isso não significa saldo investido zero.</Typography>:query.data.positions.map(p=><Paper key={p.id} variant="outlined" sx={{p:2}}><Typography fontWeight={650}>{p.name}</Typography><Typography>{p.subtype||p.type} · Fonte: banco · {p.status?(statuses[p.status]??'Situação informada pelo banco'): 'Situação não informada'}</Typography><Typography>Valor da posição informado: {money(p.balance)} · referência {p.date?civilDate(p.date):'não informada'}</Typography><Typography>Rendimento informado pelo provedor: {p.profit===null?'Não disponível':money(p.profit)} · Disponível para resgate informado: {p.available===null?'Não disponível':money(p.available)}</Typography><Typography variant="caption">Consulta em {new Date(p.checkedAt).toLocaleString('pt-BR')}. Não calculamos rentabilidade nem liquidez ausente.</Typography></Paper>)}
  </>}
 </Stack></Paper>;
}
