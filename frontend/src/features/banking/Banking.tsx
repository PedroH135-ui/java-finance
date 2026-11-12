import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, Checkbox, Chip, FormControlLabel, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { AddLink, Refresh } from '@mui/icons-material';
import { api, json } from '../../lib/api';
import type { Account, Category, BankConfig as Config, BankLink } from '../../lib/types';
import { civilDate, money, today } from '../../lib/format';
import { Empty, Failure, FormDialog, Loading, PageHeader, Panel } from '../../components/Shared';

interface Candidate { id: string; description: string; amount: string; date: string; kind: string }
interface Entry { id: string; linkId: string; accountName: string; description: string; amount: string; date: string; direction: string; providerStatus: string; state: string; movementId: string | null; version: number; candidates: Candidate[] }
const states: Record<string, string> = { REVIEW: 'Revisar', IMPORTED: 'Importado', MATCHED: 'Vinculado', IGNORED: 'Ignorado', CHANGED: 'Alterado no banco' };
const dateTime = (date: string | null) => date ? new Date(date).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' }) : 'Ainda não consultado';
function shift(date: string, days: number) { const value = new Date(date + 'T12:00:00Z'); value.setUTCDate(value.getUTCDate() + days); return value.toISOString().slice(0, 10); }

export function Banking() {
  const [params, setParams] = useSearchParams();
  const client = useQueryClient();
  const config = useQuery({ queryKey: ['bankConfig'], queryFn: () => api<Config>('/banking/config') });
  const ready = config.data?.ready ?? false;
  const links = useQuery({ queryKey: ['bankLinks'], queryFn: () => api<BankLink[]>('/banking/links'), enabled: ready, refetchInterval: ready ? 30000 : false });
  const [selected, setSelected] = useState(params.get('link') ?? ''), [page, setPage] = useState(0), [binding, setBinding] = useState(false);
  const bindingRequested = binding || params.get('new') === '1';
  function closeBinding() {
    setBinding(false);
    if (params.has('new')) {
      const next = new URLSearchParams(params); next.delete('new'); setParams(next, { replace: true });
    }
  }
  const [review, setReview] = useState<{ entry: Entry; action: string } | null>(null);
  const selectedId = links.data?.some(l => l.id === selected) ? selected : links.data?.[0]?.id ?? '';
  const selectedLink = links.data?.find(l => l.id === selectedId);
  const entries = useQuery({ queryKey: ['bankEntries', selectedId, page], queryFn: () => api<Entry[]>(`/banking/links/${selectedId}/entries?page=${page}`), enabled: ready && !!selectedId, refetchInterval: ready && !!selectedId ? 30000 : false });
  async function refresh() {
    await Promise.all(['bankLinks', 'bankEntries', 'accounts', 'movements', 'summary', 'budgets'].map(key => client.invalidateQueries({ queryKey: [key] })));
  }
  return <>
    <PageHeader title="Conexões bancárias" subtitle="Meu Pluggy pessoal · somente leitura · começando pela conta Nubank."
      action={<Button variant="contained" startIcon={<AddLink/>} disabled={!ready} onClick={() => setBinding(true)}>Vincular conta</Button>}/>
    <Alert severity="info" sx={{ mb: 3 }}>Não fazemos pagamentos nem transferências bancárias. Consultar os dados não altera seu saldo: cada importação precisa da sua revisão. Cartões e faturas não estão incluídos nesta etapa.</Alert>
    {config.isPending ? <Loading/> : config.error ? <Failure error={config.error} retry={() => void config.refetch()}/> : config.data && !ready ? <Panel title="Configuração inicial pendente" subtitle="Nenhuma conexão real foi ativada.">
      <Stack spacing={2}>
        <Typography>1. Crie sua conta no Meu Pluggy e autorize a conta do Nubank por lá. Não informe sua senha bancária neste sistema ou no chat.</Typography>
        <Typography>2. No Dashboard da Pluggy, utilize o conector MeuPluggy da aplicação pessoal, não o conector direto do banco e nem um plano comercial.</Typography>
        <Typography>3. As credenciais e os identificadores permitidos serão configurados somente no servidor. O código já está preparado; não cole chaves nesta tela.</Typography>
        <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>Identificador deste perfil para a configuração: {config.data.userId}</Typography>
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={1}><Button component="a" href="https://meu.pluggy.ai/api-guide" target="_blank" rel="noopener noreferrer" variant="outlined">Guia oficial do Meu Pluggy</Button>
          <Button startIcon={<Refresh/>} onClick={() => void config.refetch()}>Verificar configuração</Button></Stack>
      </Stack>
    </Panel> : <>
      {config.data?.automaticRead && <Alert severity="success" sx={{ mb: 2 }}>Atualização automática ativa: recebemos os avisos do Meu Pluggy e fazemos uma verificação diária de segurança, mesmo com o site fechado. Os lançamentos ficam para sua revisão; nenhum saldo é alterado automaticamente.</Alert>}
      <Typography color="text.secondary" mb={2}>O Meu Pluggy atualiza as conexões diariamente, não em tempo real. “Consultar dados” continua disponível como opção manual e não força atualização no banco.</Typography>
      {links.isPending ? <Loading/> : links.error ? <Failure error={links.error} retry={() => void links.refetch()}/> : links.data && <>
        {links.data.length === 0 ? <Paper variant="outlined"><Empty title="Vincule a conta já autorizada" action={<Button onClick={() => setBinding(true)}>Escolher conta</Button>}>Reaproveite uma conta do sistema e confirme a data inicial para não duplicar o saldo de abertura.</Empty></Paper> : <Stack spacing={2}>
          {links.data.map(link => <LinkCard key={link.id} link={link} automaticRead={!!config.data?.automaticRead} onUpdated={refresh}/>)}
        </Stack>}
        {selectedId && <Box mt={3}>
          <TextField select label="Conta para revisão" value={selectedId} onChange={event => { setSelected(event.target.value); setPage(0); }} fullWidth sx={{ mb: 2 }}>
            {links.data.map(link => <MenuItem key={link.id} value={link.id}>{link.name}{link.active ? '' : ' · leitura desativada'}</MenuItem>)}
          </TextField>
          {entries.isPending ? <Loading/> : entries.error ? <Failure error={entries.error} retry={() => void entries.refetch()}/> : entries.data && <Stack spacing={2}>
            {entries.data.length === 0 && <Paper variant="outlined"><Empty title="Nenhum lançamento nesta página">Consulte um período da conta vinculada. Nada será importado automaticamente.</Empty></Paper>}
            {entries.data.map(entry => <Paper component="section" aria-label={entry.description} variant="outlined" key={entry.id} sx={{ p: 2, minWidth: 0 }}>
              <Stack direction={{ xs: 'column', sm: 'row' }} gap={1} justifyContent="space-between"><Box sx={{ minWidth: 0 }}>
                <Typography fontWeight={650} sx={{ overflowWrap: 'anywhere' }}>{entry.description}</Typography>
                <Typography variant="body2" color="text.secondary">{civilDate(entry.date)} · {entry.direction === 'DEBIT' ? 'Saída' : 'Entrada'} · {entry.accountName}</Typography>
              </Box><Typography fontWeight={700}>{money(entry.amount)}</Typography></Stack>
              <Stack direction="row" gap={1} my={1} flexWrap="wrap"><Chip label={states[entry.state]} size="small" color={entry.state === 'CHANGED' ? 'warning' : 'default'}/><Chip size="small" label={entry.providerStatus === 'POSTED' ? 'Efetivado no banco' : 'Pendente no banco'}/></Stack>
              {entry.state === 'CHANGED' && <Alert severity="warning" sx={{ mb: 1 }}>O provedor alterou este registro. Um lançamento já vinculado não será corrigido automaticamente; confira o histórico antes de qualquer ajuste.</Alert>}
              {entry.candidates.length > 0 && <Alert severity="warning" sx={{ mb: 1 }}>Há lançamentos compatíveis no sistema. Revise antes de importar para evitar duplicidade.</Alert>}
              {['REVIEW', 'CHANGED'].includes(entry.state) && <Stack direction={{ xs: 'column', sm: 'row' }} gap={1}>
                <Button variant="outlined" disabled={!selectedLink?.active || entry.providerStatus !== 'POSTED' || !!entry.movementId} onClick={() => setReview({ entry, action: 'import' })}>Revisar importação</Button>
                {entry.candidates.length > 0 && <Button disabled={!selectedLink?.active || entry.providerStatus !== 'POSTED'} onClick={() => setReview({ entry, action: 'match' })}>Vincular existente</Button>}
                <Button disabled={!selectedLink?.active} onClick={() => setReview({ entry, action: 'ignore' })}>Ignorar</Button>
              </Stack>}
            </Paper>)}
            <Stack direction="row" justifyContent="space-between"><Button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</Button><Typography alignSelf="center">Página {page + 1}</Typography><Button disabled={entries.data.length < 50} onClick={() => setPage(value => value + 1)}>Próxima</Button></Stack>
          </Stack>}
        </Box>}
      </>}
    </>}
    {bindingRequested && ready && config.data && <LinkForm config={config.data} onClose={closeBinding} onUpdated={refresh}/>}
    {review && <ReviewForm entry={review.entry} action={review.action} onClose={() => setReview(null)} onUpdated={refresh}/>}
  </>;
}

function LinkCard({ link, automaticRead, onUpdated }: { link: BankLink; automaticRead: boolean; onUpdated: () => Promise<void> }) {
  const [from, setFrom] = useState(link.importFrom > shift(today(), -30) ? link.importFrom : shift(today(), -30));
  const [to, setTo] = useState(today()), [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  async function action(name: string) {
    setBusy(true); setError(''); setNotice('');
    try {
      const result = await api<{ read: number } | undefined>(`/banking/links/${link.id}/${name}`, { method: 'POST', body: name === 'sync' ? json({ from, to }) : undefined });
      await onUpdated();
      setNotice(name === 'sync' ? `${result?.read ?? 0} registro(s) consultado(s). Revise a lista abaixo; seu saldo não foi alterado.` : name === 'disconnect' ? 'Leitura desativada aqui. Para revogar o consentimento, faça isso também no Meu Pluggy ou no banco.' : 'Leitura reativada. Histórico preservado.');
    } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  return <Panel title={link.name} subtitle={`Importação a partir de ${civilDate(link.importFrom)} · ${link.active ? 'Leitura ativa' : 'Leitura desativada'}`}>
    <Stack spacing={2}>
      {error && <Alert severity="error">{error}</Alert>}{notice && <Alert severity="info">{notice}</Alert>}
      {link.active && link.importFrom > today() && <Alert severity="info">A leitura começa em {civilDate(link.importFrom)}, respeitando o saldo de abertura. Não há período disponível para consulta antes dessa data.</Alert>}
      {automaticRead && link.active && <Typography variant="body2">Última leitura automática: {link.autoCheckedThrough ? `dados conferidos até ${civilDate(link.autoCheckedThrough)}` : 'Aguardando primeira atualização após a data inicial.'} A lista desta tela é atualizada a cada 30 segundos enquanto estiver aberta.</Typography>}
      {link.autoErrorAt && link.active && <Alert severity="warning">A última tentativa automática não foi concluída ({dateTime(link.autoErrorAt)}). O trabalho foi preservado para nova tentativa. Você também pode consultar manualmente; se o problema persistir, confira a autorização no Meu Pluggy.</Alert>}
      <Typography variant="body2">Última consulta: {dateTime(link.lastCheckedAt)}<br/>Atualização no provedor: {dateTime(link.providerUpdatedAt)}</Typography>
      <Typography variant="body2">Saldo informado pelo provedor: {link.reportedBalance === null ? 'Não disponível' : money(link.reportedBalance)}. Apenas informativo; pode diferir do saldo registrado aqui.</Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} gap={2}><TextField type="date" label="Consultar desde" value={from} onChange={event => setFrom(event.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: link.importFrom, max: today() } }}/>
        <TextField type="date" label="Consultar até" value={to} onChange={event => setTo(event.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: from, max: today() } }}/></Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }} gap={1}><Button variant="contained" disabled={busy || !link.active || !from || !to || from > to} onClick={() => void action('sync')}>{busy ? 'Aguarde…' : 'Consultar dados'}</Button>
        <Button disabled={busy} onClick={() => void action(link.active ? 'disconnect' : 'reconnect')}>{link.active ? 'Desativar leitura aqui' : 'Reativar leitura'}</Button></Stack>
    </Stack>
  </Panel>;
}

function LinkForm({ config, onClose, onUpdated }: { config: Config; onClose: () => void; onUpdated: () => Promise<void> }) {
  const [item, setItem] = useState(config.items[0] ?? ''), [error, setError] = useState('');
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/accounts') });
  const remote = useQuery({ queryKey: ['bankDiscover', item], queryFn: () => api<{ accounts: { id: string; name: string; balance: string }[] }>(`/banking/items/${item}/accounts`), enabled: !!item });
  const form = useForm({ defaultValues: { externalAccountId: '', accountId: '', importFrom: today(), confirmedBaseline: false } });
  const submit = form.handleSubmit(async value => {
    setError('');
    try { await api('/banking/links', { method: 'POST', body: json({ ...value, itemId: item }) }); await onUpdated(); onClose(); }
    catch (e) { setError((e as Error).message); }
  });
  return <FormDialog title="Vincular conta bancária" dirty={form.formState.isDirty} busy={form.formState.isSubmitting} onClose={onClose}
    actions={<Button form="bank-link-form" type="submit" variant="contained" disabled={form.formState.isSubmitting || remote.isPending || !!remote.error || !!accounts.error}>Vincular para leitura</Button>}>
    <form id="bank-link-form" onSubmit={submit}><Stack spacing={2} pt={1}>
      {error && <Alert severity="error">{error}</Alert>}
      <Alert severity="warning">Confirme o saldo de abertura da conta local no fim do dia anterior à data inicial. Não use o saldo de hoje para importar novamente todo o histórico.</Alert>
      <TextField select label="Conexão autorizada" value={item} onChange={event => { setItem(event.target.value); form.setValue('externalAccountId', ''); }}>{config.items.map(id => <MenuItem key={id} value={id}>{id}</MenuItem>)}</TextField>
      {remote.isPending || accounts.isPending ? <Loading/> : remote.error ? <Failure error={remote.error}/> : accounts.error ? <Failure error={accounts.error}/> : <>
        <Controller name="externalAccountId" control={form.control} rules={{ required: true }} render={({ field }) => <TextField select required label="Conta disponível no Meu Pluggy" {...field}>{remote.data?.accounts.map(account => <MenuItem key={account.id} value={account.id}>{account.name} · {account.id.slice(-6)}</MenuItem>)}</TextField>}/>
        <Controller name="accountId" control={form.control} rules={{ required: true }} render={({ field }) => <TextField select required label="Conta no Java Finance" {...field} onChange={event => { field.onChange(event); const account = accounts.data?.find(a => a.id === event.target.value); if (account) form.setValue('importFrom', shift(account.openingDate, 1) > today() ? shift(account.openingDate, 1) : today()); }}>
          {accounts.data?.filter(a => !a.archived && ['CHECKING', 'SAVINGS', 'WALLET', 'OTHER'].includes(a.kind)).map(account => <MenuItem key={account.id} value={account.id}>{account.name}</MenuItem>)}</TextField>}/>
      </>}
      <Button component={RouterLink} to="/accounts">Consultar ou cadastrar conta local</Button>
      <TextField type="date" label="Data inicial de importação" required {...form.register('importFrom', { required: true })} slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: shift(today(), 1) } }}/>
      <Controller name="confirmedBaseline" control={form.control} rules={{ validate: value => value || 'Confira o saldo inicial antes de continuar.' }} render={({ field, fieldState }) => <Box><FormControlLabel control={<Checkbox checked={field.value} onChange={event => field.onChange(event.target.checked)}/>} label="Conferi a data e o saldo de abertura para evitar duplicidade."/>{fieldState.error && <Typography color="error">{fieldState.error.message}</Typography>}</Box>}/>
    </Stack></form>
  </FormDialog>;
}

function ReviewForm({ entry, action, onClose, onUpdated }: { entry: Entry; action: string; onClose: () => void; onUpdated: () => Promise<void> }) {
  const [error, setError] = useState('');
  const categories = useQuery({ queryKey: ['categories'], queryFn: () => api<Category[]>('/categories'), enabled: action === 'import' });
  const form = useForm({ defaultValues: { categoryId: '', movementId: '', confirmedNature: false, reviewedDuplicates: false } });
  const submit = form.handleSubmit(async value => {
    setError('');
    try { await api(`/banking/entries/${entry.id}/${action}`, { method: 'POST', body: json({ ...value, categoryId: value.categoryId || null, movementId: value.movementId || null, version: entry.version }) }); await onUpdated(); onClose(); }
    catch (e) { setError((e as Error).message); }
  });
  const income = entry.direction === 'CREDIT';
  return <FormDialog title={action === 'import' ? 'Revisar importação' : action === 'match' ? 'Vincular a lançamento existente' : 'Ignorar lançamento'} dirty={form.formState.isDirty} busy={form.formState.isSubmitting} onClose={onClose}
    actions={<Button variant="contained" form="bank-review-form" type="submit" disabled={form.formState.isSubmitting || (action === 'import' && (categories.isPending || !!categories.error))}>{action === 'import' ? 'Confirmar importação' : action === 'match' ? 'Confirmar vínculo' : 'Confirmar ignorar'}</Button>}>
    <form id="bank-review-form" onSubmit={submit}><Stack spacing={2} pt={1}>
      {error && <Alert severity="error">{error}</Alert>}
      <Typography sx={{ overflowWrap: 'anywhere' }}>{entry.description} · {civilDate(entry.date)} · {money(entry.amount)}</Typography>
      {action === 'ignore' ? <Typography>O registro será ignorado nesta revisão. Nenhum lançamento financeiro será criado ou cancelado.</Typography> : action === 'match' ? <>
        <Typography>O vínculo não altera o saldo nem duplica a movimentação existente. Confira os detalhes antes de confirmar.</Typography>
        <Controller name="movementId" control={form.control} rules={{ required: true }} render={({ field }) => <TextField select required label="Lançamento existente" {...field}>{entry.candidates.map(candidate => <MenuItem key={candidate.id} value={candidate.id}>{civilDate(candidate.date)} · {candidate.description} · {money(candidate.amount)}</MenuItem>)}</TextField>}/>
      </> : <>
        <Alert severity="warning">Será registrada uma {income ? 'receita' : 'despesa'} efetivada, por competência de {entry.date.slice(0, 7)}. Isso altera seu saldo local. Transferências próprias, empréstimos, investimentos e pagamentos de fatura não devem ser classificados assim.</Alert>
        {categories.isPending ? <Loading/> : categories.error ? <Failure error={categories.error}/> : <Controller name="categoryId" control={form.control} rules={{ required: true }} render={({ field }) => <TextField select required label="Categoria" {...field}>{categories.data?.filter(c => !c.archived && c.kind === (income ? 'INCOME' : 'EXPENSE')).map(category => <MenuItem key={category.id} value={category.id}>{category.name}</MenuItem>)}</TextField>}/>}
        <Controller name="confirmedNature" control={form.control} rules={{ validate: value => value || 'Confirme a natureza da operação.' }} render={({ field, fieldState }) => <Box><FormControlLabel control={<Checkbox checked={field.value} onChange={event => field.onChange(event.target.checked)}/>} label="Confirmei que esta operação é uma receita/despesa real."/>{fieldState.error && <Typography color="error">{fieldState.error.message}</Typography>}</Box>}/>
        {entry.candidates.length > 0 && <>
          <Alert severity="warning">Possíveis duplicidades: {entry.candidates.map(c => `${civilDate(c.date)} · ${c.description} · ${money(c.amount)}`).join('; ')}. Prefira vincular o lançamento existente quando representar a mesma operação.</Alert>
          <Controller name="reviewedDuplicates" control={form.control} rules={{ validate: value => value || 'Revise as possíveis duplicidades.' }} render={({ field, fieldState }) => <Box><FormControlLabel control={<Checkbox checked={field.value} onChange={event => field.onChange(event.target.checked)}/>} label="Revisei as possíveis duplicidades e são operações distintas."/>{fieldState.error && <Typography color="error">{fieldState.error.message}</Typography>}</Box>}/>
        </>}
      </>}
    </Stack></form>
  </FormDialog>;
}
