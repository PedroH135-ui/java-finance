import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Chip, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { Failure, FormDialog, Loading, MoneyInput } from '../../components/Shared';
import { api, json } from '../../lib/api';
import { civilDate, money } from '../../lib/format';

interface Commitment {
  id: string;
  description: string;
  accountName: string;
  dueDate: string;
  amount: string;
  status: 'EXPECTED' | 'IDENTIFIED' | 'REVIEW';
  entryId: string | null;
  bankDate: string | null;
}
interface Upcoming {
  from: string;
  through: string;
  accounts: { id: string; name: string }[];
  items: Commitment[];
  pendingTotal: string;
  reviewTotal: string;
}
const endpoint = '/budgets/commitments';
const labels = { EXPECTED: 'Previsto', IDENTIFIED: 'Identificado no extrato', REVIEW: 'Conferir no extrato' };

export function UpcomingCommitments() {
  const client = useQueryClient();
  const query = useQuery({
    queryKey: ['upcomingCommitments'],
    queryFn: ({ signal }) => api<Upcoming>(endpoint, { signal }),
    refetchInterval: 60_000,
  });
  const [adding, setAdding] = useState(false);
  const [removing, setRemoving] = useState<Commitment | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function remove() {
    if (!removing || busy) return;
    setBusy(true);
    setError('');
    try {
      await api(`${endpoint}/${removing.id}`, { method: 'DELETE' });
      await client.invalidateQueries({ queryKey: ['upcomingCommitments'] });
      setRemoving(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  const data = query.data;
  return <Paper component="section" aria-label="Próximos compromissos" variant="outlined" sx={{ p: { xs: 2, md: 3 }, mb: 3 }}>
    <Stack gap={2}>
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={1}>
        <div>
          <Typography component="h2" variant="h6">Próximos compromissos</Typography>
          <Typography color="text.secondary" variant="body2">
            {data ? `Próximos 30 dias · ${civilDate(data.from)} a ${civilDate(data.through)}` : 'Próximos 30 dias'}
          </Typography>
        </div>
        <Button variant="outlined" disabled={!data?.accounts.length} onClick={() => setAdding(true)}>Adicionar compromisso</Button>
      </Stack>
      {query.isPending ? <Loading/> : query.error ? <Failure error={query.error} retry={() => void query.refetch()}/> : data && <>
        <Typography fontWeight={600}>Ainda previsto {money(data.pendingTotal)}</Typography>
        {data.items.some(item => item.status === 'REVIEW') &&
          <Alert severity="warning">{money(data.reviewTotal)} para conferir no extrato. Valores ambíguos ficam fora do total previsto.</Alert>}
        {!data.accounts.length &&
          <Typography variant="body2">Vincule uma conta em <Link to="/banking">Conexões bancárias</Link> para cadastrar compromissos.</Typography>}
        {data.items.length === 0 && <Typography variant="body2" color="text.secondary">Nenhum compromisso neste período.</Typography>}
        {data.items.map(item => <Stack key={item.id} direction={{ xs: 'column', sm: 'row' }} gap={1} alignItems={{ sm: 'center' }}
          justifyContent="space-between" sx={{ borderTop: 1, borderColor: 'divider', pt: 1.5 }}>
          <div>
            <Typography fontWeight={600} sx={{ overflowWrap: 'anywhere' }}>{item.description} · {money(item.amount)}</Typography>
            <Typography variant="body2" color="text.secondary">{item.accountName} · vencimento em {civilDate(item.dueDate)}</Typography>
          </div>
          <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center">
            <Chip size="small" color={item.status === 'IDENTIFIED' ? 'success' : item.status === 'REVIEW' ? 'warning' : 'default'} label={labels[item.status]}/>
            {item.entryId && item.bankDate &&
              <Button size="small" component={Link} to={`/movements?entry=${item.entryId}&month=${item.bankDate.slice(0, 7)}`}>Ver lançamento</Button>}
            {item.status === 'REVIEW' &&
              <Button size="small" component={Link} to={`/movements?month=${item.dueDate.slice(0, 7)}`}>Abrir extrato</Button>}
            <Button size="small" aria-label={`Remover compromisso ${item.description}`} onClick={() => { setError(''); setRemoving(item); }}>Remover</Button>
          </Stack>
        </Stack>)}
      </>}
    </Stack>
    {adding && data && <CommitmentForm data={data} onClose={() => setAdding(false)}/>}
    {removing && <FormDialog title="Remover compromisso?" dirty={false} busy={busy} onClose={() => setRemoving(null)}
      actions={<Button color="error" disabled={busy} onClick={() => void remove()}>Remover compromisso</Button>}>
      {error && <Alert severity="error">{error}</Alert>}
      <Typography>Remover a previsão de {removing.description}. O lançamento bancário será preservado.</Typography>
    </FormDialog>}
  </Paper>;
}

function CommitmentForm({ data, onClose }: { data: Upcoming; onClose: () => void }) {
  const client = useQueryClient();
  const [accountId, setAccountId] = useState(data.accounts.length === 1 ? data.accounts[0].id : '');
  const [description, setDescription] = useState('');
  const [statementDescription, setStatementDescription] = useState('');
  const [dueDate, setDueDate] = useState(data.from);
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError('');
    try {
      await api(endpoint, { method: 'POST', body: json({
        accountId, description: description.trim(), statementDescription: statementDescription.trim() || description.trim(),
        dueDate, amount,
      }) });
      await client.invalidateQueries({ queryKey: ['upcomingCommitments'] });
      onClose();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return <FormDialog title="Novo compromisso" dirty={!!(description || statementDescription || amount)} busy={busy} onClose={onClose}
    actions={<Button type="submit" form="commitment-form" variant="contained" disabled={busy}>Salvar compromisso</Button>}>
    <Stack component="form" id="commitment-form" gap={2} pt={1} onSubmit={save}>
      {error && <Alert severity="error">{error}</Alert>}
      <TextField label="Nome do compromisso" value={description} onChange={e => setDescription(e.target.value)} required
        slotProps={{ htmlInput: { maxLength: 200 } }}/>
      <TextField label="Descrição no extrato" value={statementDescription} onChange={e => setStatementDescription(e.target.value)}
        helperText="Se for diferente do nome acima, informe como a cobrança aparece no banco."
        slotProps={{ htmlInput: { maxLength: 200 } }}/>
      <TextField select label="Conta vinculada" value={accountId} onChange={e => setAccountId(e.target.value)} required>
        {data.accounts.map(account => <MenuItem key={account.id} value={account.id}>{account.name}</MenuItem>)}
      </TextField>
      <TextField type="date" label="Vencimento" value={dueDate} onChange={e => setDueDate(e.target.value)} required
        slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: data.from, max: data.through } }}/>
      <MoneyInput label="Valor previsto" value={amount} onChange={setAmount}/>
      <Typography variant="body2" color="text.secondary">O vínculo usa conta, descrição, valor e até três dias de diferença entre vencimento e débito.</Typography>
    </Stack>
  </FormDialog>;
}
