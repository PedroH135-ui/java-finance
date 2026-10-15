import { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, LinearProgress, Paper, Stack, TextField, Typography } from '@mui/material';
import { ContentCopy, EditOutlined } from '@mui/icons-material';
import { api, json } from '../../lib/api';
import { money, monthLabel, today } from '../../lib/format';
import { Empty, Failure, FormDialog, Loading, MoneyInput, PageHeader } from '../../components/Shared';

export interface BudgetRow {
  categoryId: string; name: string; archived: boolean; essential: boolean; limit: string | null;
  version: number | null; spent: string; planned: string; remaining: string | null; percent: string | null;
}
interface BudgetMonth {
  month: string; categories: BudgetRow[];
  totals: { limit: string; spent: string; planned: string; remaining: string; unbudgetedSpent: string; unbudgetedPlanned: string; configured: number };
}
function previousMonth(month: string) {
  const [year, value] = month.split('-').map(Number);
  return value === 1 ? `${year - 1}-12` : `${year}-${String(value - 1).padStart(2, '0')}`;
}
export function Budgets() {
  const [params, setParams] = useSearchParams();
  const month = params.get('month') ?? today().slice(0, 7);
  const validMonth = /^\d{4}-(0[1-9]|1[0-2])$/.test(month) && Number(month.slice(0, 4)) >= 1900 && Number(month.slice(0, 4)) <= 2200;
  const client = useQueryClient();
  const query = useQuery({ queryKey: ['budgets', month], queryFn: () => api<BudgetMonth>('/budgets?month=' + month), enabled: validMonth });
  const [editing, setEditing] = useState<BudgetRow | null>(null);
  const [copying, setCopying] = useState(false), [busy, setBusy] = useState(false);
  const [error, setError] = useState(''), [message, setMessage] = useState('');
  async function copy() {
    setBusy(true); setError(''); setMessage('');
    try {
      const result = await api<{ copied: number; skipped: number }>(`/budgets/${month}/copy`, { method: 'POST', body: json({ sourceMonth: previousMonth(month) }) });
      await client.invalidateQueries({ queryKey: ['budgets'] });
      setMessage(result.copied === 0 ? 'Nenhum limite novo foi copiado. O mês anterior pode estar sem limites; os existentes e as categorias arquivadas são preservados.' : `${result.copied} limite(s) copiado(s). ${result.skipped} ignorado(s) por já existir ou estar arquivado.`);
      setCopying(false);
    } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  return <>
    <PageHeader title="Orçamentos" subtitle="Planeje seus limites e acompanhe as despesas do mês de competência."
      action={<Button variant="outlined" startIcon={<ContentCopy/>} disabled={!validMonth || month === '1900-01' || query.isPending || !!query.error} onClick={() => { setError(''); setCopying(true); }}>Copiar mês anterior</Button>}/>
    <TextField label="Mês do orçamento" type="month" value={month} onChange={e => { if (e.target.value) { setParams({ month: e.target.value }); setMessage(''); setError(''); } }}
      slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: '1900-01', max: '2200-12' } }} sx={{ width: { xs: '100%', sm: 240 }, mb: 3 }}/>
    {!validMonth ? <Alert severity="warning">Selecione um mês válido entre 1900 e 2200.</Alert> : query.isPending ? <Loading/> : query.error ? <Failure error={query.error} retry={() => void query.refetch()}/> : query.data && <>
      {message && <Alert severity="info" sx={{ mb: 2 }}>{message}</Alert>}
      <Alert severity="info" sx={{ mb: 3 }}>Limites não reservam dinheiro nem alteram o saldo. Gastos são despesas efetivadas; previstos aparecem separadamente e cancelados não entram no cálculo.</Alert>
      <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2,minmax(0,1fr))', xl: 'repeat(4,minmax(0,1fr))' }, gap: 2, mb: 3 }}>
        {[
          ['Limites definidos', query.data.totals.limit], ['Gastos com limite', query.data.totals.spent],
          ['Restante dos limites', query.data.totals.remaining], ['Previstos com limite', query.data.totals.planned],
        ].map(([label, value]) => <Paper key={label} variant="outlined" sx={{ p: 2, minWidth: 0 }}>
          <Typography variant="body2" color="text.secondary">{label}</Typography>
          <Typography sx={{ fontSize: '1.35rem', fontWeight: 700, overflowWrap: 'anywhere', color: value.startsWith('-') ? 'error.main' : 'text.primary' }}>{money(value)}</Typography>
        </Paper>)}
      </Box>
      {(query.data.totals.unbudgetedSpent !== '0.00' || query.data.totals.unbudgetedPlanned !== '0.00') && <Alert severity="warning" sx={{ mb: 2 }}>
        Sem limite definido: {money(query.data.totals.unbudgetedSpent)} em gastos e {money(query.data.totals.unbudgetedPlanned)} previstos. Esses valores não estão nos totais com limite acima.
      </Alert>}
      {query.data.categories.length === 0 ? <Paper variant="outlined"><Empty title="Comece pelas categorias de despesa" action={<Button component={Link} to="/categories">Cadastrar categoria</Button>}>Crie uma categoria e depois defina quanto pretende gastar nela por mês.</Empty></Paper> : <Stack spacing={2}>
        {query.data.categories.map(row => <Paper component="section" aria-label={row.name} key={row.categoryId} variant="outlined" sx={{ p: { xs: 2, md: 3 }, minWidth: 0 }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" gap={1} mb={2}>
            <Box sx={{ minWidth: 0 }}><Typography component="h2" variant="h6" sx={{ overflowWrap: 'anywhere' }}>{row.name}</Typography>
              <Stack direction="row" gap={1} flexWrap="wrap">{row.essential && <Chip size="small" label="Essencial"/>}{row.archived && <Chip size="small" label="Arquivada"/>}
                <Chip size="small" color={row.remaining?.startsWith('-') ? 'error' : 'default'} label={row.limit === null ? 'Sem limite' : row.remaining?.startsWith('-') ? 'Limite ultrapassado' : row.remaining === '0.00' ? 'Limite atingido' : 'Dentro do limite'}/></Stack>
            </Box>
            <Button startIcon={<EditOutlined/>} disabled={row.archived && row.limit === null} onClick={() => setEditing(row)}>{row.limit === null ? 'Definir limite' : 'Editar limite'}</Button>
          </Stack>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: 'repeat(2,minmax(0,1fr))', md: 'repeat(4,minmax(0,1fr))' }, gap: 1, mb: 2 }}>
            {[
              ['Limite', row.limit === null ? 'Não definido' : money(row.limit)], ['Gasto efetivado', money(row.spent)],
              ['Previsto', money(row.planned)], ['Restante', row.remaining === null ? '—' : money(row.remaining)],
            ].map(([label, value]) => <Box key={label}><Typography variant="caption" color="text.secondary">{label}</Typography><Typography fontWeight={600} sx={{ overflowWrap: 'anywhere' }}>{value}</Typography></Box>)}
          </Box>
          {row.percent !== null && <><LinearProgress variant="determinate" value={Math.min(100, Number(row.percent))} color={row.remaining?.startsWith('-') ? 'error' : 'primary'} aria-label={`Uso do limite de ${row.name}`} aria-valuetext={`${row.percent}% utilizado`}/>
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.5 }}>{row.percent.replace('.', ',')}% utilizado · previstos não descontados do restante</Typography></>}
          {row.limit === '0.00' && <Typography variant="caption" color="text.secondary">Limite zero: nenhum gasto permitido neste planejamento.</Typography>}
          <Button component={Link} to={`/movements?kind=EXPENSE&basis=competence&month=${month}&category=${row.categoryId}`} size="small" sx={{ mt: 1 }}>Ver movimentações</Button>
        </Paper>)}
      </Stack>}
    </>}
    {editing && <BudgetForm row={editing} month={month} onClose={() => setEditing(null)}/>}
    <Dialog open={copying} onClose={() => { if (!busy) setCopying(false); }} aria-labelledby="copy-budgets-title">
      <DialogTitle id="copy-budgets-title">Copiar limites do mês anterior?</DialogTitle><DialogContent>
        {error && <Alert severity="error">{error}</Alert>}
        <Typography>Copiar de {validMonth ? monthLabel(previousMonth(month)) : '—'} para {validMonth ? monthLabel(month) : '—'}. Somente categorias ativas sem limite no destino serão adicionadas. Gastos e previstos não serão copiados.</Typography>
      </DialogContent><DialogActions><Button disabled={busy} onClick={() => setCopying(false)}>Voltar</Button><Button variant="contained" disabled={busy} onClick={() => void copy()}>{busy ? 'Copiando…' : 'Confirmar cópia'}</Button></DialogActions>
    </Dialog>
  </>;
}
function BudgetForm({ row, month, onClose }: { row: BudgetRow; month: string; onClose: () => void }) {
  const client = useQueryClient(), [error, setError] = useState('');
  const form = useForm<{ amount: string }>({ defaultValues: { amount: row.limit ?? '' } });
  const submit = form.handleSubmit(async value => {
    setError('');
    try {
      await api(`/budgets/${month}/categories/${row.categoryId}`, { method: 'PUT', body: json({ amount: value.amount, version: row.version }) });
      await client.invalidateQueries({ queryKey: ['budgets'] }); onClose();
    } catch (e) { setError((e as Error).message); }
  });
  return <FormDialog title={`Limite de ${row.name}`} dirty={form.formState.isDirty} busy={form.formState.isSubmitting} onClose={onClose}
    actions={<Button variant="contained" type="submit" form="budget-form" disabled={form.formState.isSubmitting}>Salvar limite</Button>}>
    <form id="budget-form" onSubmit={submit}><Stack gap={2} pt={1}>
      {error && <Alert severity="error">{error}</Alert>}
      <Typography>Orçamento de {monthLabel(month)}. Zero significa planejar não gastar nesta categoria.</Typography>
      <Controller name="amount" control={form.control} rules={{ required: 'Informe o limite.', validate: value => /^(0|[1-9][0-9]{0,14})(\.[0-9]{1,2})?$/.test(value) || 'Informe um valor válido com até duas casas decimais.' }}
        render={({ field, fieldState }) => <MoneyInput label="Limite mensal" value={field.value} onChange={field.onChange} error={!!fieldState.error} helperText={fieldState.error?.message}/>}/>
    </Stack></form>
  </FormDialog>;
}
