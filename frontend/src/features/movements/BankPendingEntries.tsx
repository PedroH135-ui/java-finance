import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, Chip, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { api } from '../../lib/api';
import { civilDate, money } from '../../lib/format';
import { Empty, Failure, Loading } from '../../components/Shared';
import { useStoredBankLinks } from '../banking/useStoredBankLinks';

interface PendingEntry {
  id: string; linkId: string; accountName: string; description: string; amount: string;
  date: string; direction: string; providerStatus: string; state: string; movementId: string | null;
}

export function BankPendingEntries() {
  const [params, setParams] = useSearchParams();
  const banking = useStoredBankLinks();
  const links = banking.ready && !banking.error ? banking.links.data ?? [] : [];
  const selected = links.find(link => link.id === params.get('link')) ?? links[0];
  const requestedPage = Number(params.get('reviewPage') ?? 0);
  const page = Number.isInteger(requestedPage) && requestedPage >= 0 && requestedPage <= 10000 ? requestedPage : 0;
  const entries = useQuery({ queryKey: ['bankEntries', selected?.id, page, 'pending'],
    queryFn: () => api<PendingEntry[]>(`/banking/links/${encodeURIComponent(selected!.id)}/entries?page=${page}&pending=true`),
    enabled: !!selected, refetchInterval: selected ? 30000 : false });
  function chooseLink(id: string) {
    setParams(previous => { const next = new URLSearchParams(previous); next.set('link', id); next.delete('reviewPage'); return next; });
  }
  function choosePage(value: number) {
    setParams(previous => { const next = new URLSearchParams(previous); next.set('reviewPage', String(value)); return next; });
  }
  return <Stack spacing={2}>
    <Alert severity="info">Registros recebidos do banco, de todas as datas, que ainda precisam de revisão. Não entram no saldo interno nem nos totais de receitas e despesas até sua confirmação manual.</Alert>
    {banking.loading ? <Loading/> : banking.error ? <Failure error={banking.error} retry={() => { void banking.config.refetch(); if (banking.ready) void banking.links.refetch(); }}/>
      : !selected ? <Paper variant="outlined"><Empty title="Nenhuma conta bancária vinculada" action={<Button component={Link} to="/banking">Ver conexões bancárias</Button>}>Após vincular uma conta e receber dados, os registros para revisão aparecerão aqui.</Empty></Paper>
      : <>
        <TextField select label="Conta bancária para revisão" value={selected.id} onChange={event => chooseLink(event.target.value)}>
          {links.map(link => <MenuItem key={link.id} value={link.id}>{link.name}{!link.active ? ' · Leitura desativada' : ''}</MenuItem>)}
        </TextField>
        {!selected.active && <Alert severity="warning">A leitura desta conta está desativada. Os registros armazenados continuam visíveis; verifique a conexão para prosseguir com a revisão.</Alert>}
        {entries.isPending ? <Loading/> : entries.error ? <Failure error={entries.error} retry={() => void entries.refetch()}/>
          : <>
            {entries.data.length === 0 ? <Paper variant="outlined"><Empty title="Nenhum registro aguardando revisão nesta página">A consulta desta tela não busca novos dados no banco. Confira a conexão bancária ou volte à página anterior.</Empty></Paper>
              : entries.data.map(entry => <Paper key={entry.id} component="article" aria-label={entry.description} variant="outlined" sx={{p:2}}>
                <Stack direction={{xs:'column',sm:'row'}} gap={1} justifyContent="space-between">
                  <Box sx={{minWidth:0}}><Typography fontWeight={650} sx={{overflowWrap:'anywhere'}}>{entry.description}</Typography>
                    <Typography variant="body2" color="text.secondary">{civilDate(entry.date)} · {entry.direction === 'CREDIT' ? 'Entrada no banco' : 'Saída no banco'} · {entry.accountName}</Typography></Box>
                  <Typography className="money" fontWeight={650} sx={{flexShrink:0}}>{money(entry.amount)}</Typography>
                </Stack>
                <Stack direction="row" flexWrap="wrap" gap={1} mt={1.5}>
                  <Chip size="small" label={entry.state === 'CHANGED' ? 'Alterado no banco · revisar' : 'Aguardando revisão'} color="warning" variant="outlined"/>
                  <Chip size="small" label={entry.providerStatus === 'POSTED' ? 'Efetivado no banco' : 'Pendente no banco'} variant="outlined"/>
                </Stack>
                {entry.movementId && <Typography variant="body2" mt={1}>Este registro já possui vínculo interno. A alteração recebida do banco precisa ser conferida, sem criar outro lançamento.</Typography>}
                <Button component={Link} to={'/banking?link='+encodeURIComponent(selected.id)} sx={{mt:1}}>Revisar na conexão bancária</Button>
              </Paper>)}
            <Stack direction="row" justifyContent="space-between" alignItems="center" flexWrap="wrap" gap={1}>
              <Button disabled={page === 0} onClick={() => choosePage(page - 1)}>Anterior</Button>
              <Typography variant="body2">Página {page + 1} · {entries.data.length} registro(s) nesta página</Typography>
              <Button disabled={entries.data.length < 50} onClick={() => choosePage(page + 1)}>Próxima</Button>
            </Stack>
          </>}
      </>}
  </Stack>;
}
