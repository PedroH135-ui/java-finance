import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Controller, useForm } from 'react-hook-form';
import { useSearchParams } from 'react-router-dom';
import { Alert, Box, Button, Checkbox, Chip, FormControlLabel, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { AddLink, Refresh } from '@mui/icons-material';
import { api, json } from '../../lib/api';
import type { Account, Category, BankConfig as Config, BankLink } from '../../lib/types';
import { civilDate, money, today } from '../../lib/format';
import { Empty, Failure, FormDialog, Loading, PageHeader, Panel } from '../../components/Shared';
import { BankProducts } from './BankProducts';

interface Candidate { id: string; description: string; amount: string; date: string; kind: string }
interface Entry { id: string; linkId: string; accountName: string; description: string; amount: string; date: string; direction: string; providerStatus: string; state: string; movementId: string | null; version: number; candidates: Candidate[] }
const states: Record<string, string> = { REVIEW: 'Revisar', IMPORTED: 'Classificado', MATCHED: 'Vinculado', IGNORED: 'Ignorado', CHANGED: 'Alterado no banco' };
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
    await Promise.all(['bankLinks', 'bankEntries', 'accounts', 'movements', 'movementFeed', 'summary', 'budgets', 'upcomingCommitments'].map(key => client.invalidateQueries({ queryKey: [key] })));
  }
  return <>
    <PageHeader title="Conexões bancárias" subtitle="Meu Pluggy pessoal · somente leitura · começando pela conta Nubank."
      action={<Button variant="contained" startIcon={<AddLink/>} disabled={!ready || !links.data} onClick={() => setBinding(true)}>Vincular conta</Button>}/>
    <Alert severity="info" sx={{ mb: 3 }}>Não fazemos pagamentos nem transferências bancárias. O saldo exibido vem do banco; registros efetivados entram nos relatórios automaticamente, sem novos descontos. Categorizar é opcional. Cartões, faturas e posições de investimentos têm consulta adicional abaixo, conforme a cobertura da conexão.</Alert>
    {config.isPending ? <Loading/> : config.error ? <Failure error={config.error} retry={() => void config.refetch()}/> : config.data && !ready ? <Panel title="Prepare sua conexão bancária" subtitle="Autorize seu banco no Meu Pluggy e volte para vincular a conta.">
      <Stack spacing={2}>
        <Stack direction={{ xs: 'column', sm: 'row' }} gap={1}>
          <Button component="a" href="https://meu.pluggy.ai/" target="_blank" rel="noopener noreferrer" variant="contained">Abrir Meu Pluggy</Button>
          <Button startIcon={<Refresh/>} onClick={() => void config.refetch()}>Verificar se posso vincular</Button>
        </Stack>
        <Typography variant="body2">Se o banco já foi autorizado e não aparece, a conexão precisa ser liberada para seu perfil no Java Finance.</Typography>
        <Box component="details">
          <Box component="summary" sx={{ cursor: 'pointer' }}>Informações para configuração</Box>
          <Typography variant="body2" sx={{ mt: 1, overflowWrap: 'anywhere' }}>Seu identificador no Java Finance: {config.data.userId}</Typography>
          <Button component="a" href="https://meu.pluggy.ai/api-guide" target="_blank" rel="noopener noreferrer">Guia oficial do Meu Pluggy</Button>
        </Box>
      </Stack>
    </Panel> : <>
      {config.data?.automaticRead && <Alert severity="success" sx={{ mb: 2 }}>Atualização automática ativa: recebemos os avisos do Meu Pluggy e fazemos uma verificação diária de segurança, mesmo com o site fechado. O saldo exibido acompanha o banco. Registros efetivados aparecem nas movimentações e relatórios; as categorias podem ser organizadas depois.</Alert>}
      <Typography color="text.secondary" mb={2}>O Meu Pluggy atualiza as conexões diariamente, não em tempo real. “Consultar dados” continua disponível como opção manual e não força atualização no banco.</Typography>
      {links.isPending ? <Loading/> : links.error ? <Failure error={links.error} retry={() => void links.refetch()}/> : links.data && <>
        {links.data.length === 0 ? <Paper variant="outlined"><Empty title="Vincule a conta já autorizada" action={<Button onClick={() => setBinding(true)}>Escolher conta</Button>}>Reaproveite uma conta do sistema e confirme a data inicial para evitar duplicidade nos relatórios.</Empty></Paper> : <Stack spacing={2}>
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
              {entry.candidates.length > 0 && <Alert severity="warning" sx={{ mb: 1 }}>Há lançamentos compatíveis no sistema. Revise antes de classificar para evitar duplicidade.</Alert>}
              {['REVIEW', 'CHANGED'].includes(entry.state) && <Stack direction={{ xs: 'column', sm: 'row' }} gap={1}>
                <Button variant="outlined" disabled={!selectedLink?.active || entry.providerStatus !== 'POSTED' || !!entry.movementId} onClick={() => setReview({ entry, action: 'import' })}>Revisar classificação</Button>
                {entry.candidates.length > 0 && <Button disabled={!selectedLink?.active || entry.providerStatus !== 'POSTED'} onClick={() => setReview({ entry, action: 'match' })}>Vincular existente</Button>}
                <Button disabled={!selectedLink?.active} onClick={() => setReview({ entry, action: 'ignore' })}>Ignorar</Button>
              </Stack>}
            </Paper>)}
            <Stack direction="row" justifyContent="space-between"><Button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Anterior</Button><Typography alignSelf="center">Página {page + 1}</Typography><Button disabled={entries.data.length < 50} onClick={() => setPage(value => value + 1)}>Próxima</Button></Stack>
          </Stack>}
        </Box>}
      </>}
    </>}
    <BankProducts links={ready?links.data??[]:[]}/>
    {bindingRequested && ready && config.data && links.data && <LinkForm config={config.data} links={links.data} onClose={closeBinding}
      onRefreshConfig={async () => { await config.refetch(); }} onUpdated={refresh}/>}
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
      setNotice(name === 'sync' ? `${result?.read ?? 0} registro(s) consultado(s). Saldo exibido conforme o banco. Revise a lista abaixo para classificar; nada foi confirmado automaticamente.` : name === 'disconnect' ? 'Leitura desativada aqui. Para revogar o consentimento, faça isso também no Meu Pluggy ou no banco.' : 'Leitura reativada. Histórico preservado.');
    } catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }
  return <Panel title={link.name} subtitle={`Registros a partir de ${civilDate(link.importFrom)} · ${link.active ? 'Leitura ativa' : 'Leitura desativada'}`}>
    <Stack spacing={2}>
      {error && <Alert severity="error">{error}</Alert>}{notice && <Alert severity="info">{notice}</Alert>}
      {link.active && link.importFrom > today() && <Alert severity="info">A leitura começa em {civilDate(link.importFrom)}, respeitando a data inicial escolhida. Não há período disponível para consulta antes dessa data.</Alert>}
      {automaticRead && link.active && <Typography variant="body2">Última leitura automática: {link.autoCheckedThrough ? `dados conferidos até ${civilDate(link.autoCheckedThrough)}` : 'Aguardando primeira atualização após a data inicial.'} A lista desta tela é atualizada a cada 30 segundos enquanto estiver aberta.</Typography>}
      {link.autoErrorAt && link.active && <Alert severity="warning">A última tentativa automática não foi concluída ({dateTime(link.autoErrorAt)}). O trabalho foi preservado para nova tentativa. Você também pode consultar manualmente; se o problema persistir, confira a autorização no Meu Pluggy.</Alert>}
      <Typography variant="body2">Última consulta: {dateTime(link.lastCheckedAt)}<br/>Atualização no provedor: {dateTime(link.providerUpdatedAt)}</Typography>
      <Typography variant="body2">Saldo informado pelo provedor: {link.reportedBalance === null ? 'Não disponível' : money(link.reportedBalance)}. Este é o saldo principal da conta vinculada; a classificação não o altera.</Typography>
      <Stack direction={{ xs: 'column', sm: 'row' }} gap={2}><TextField type="date" label="Consultar desde" value={from} onChange={event => setFrom(event.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: link.importFrom, max: today() } }}/>
        <TextField type="date" label="Consultar até" value={to} onChange={event => setTo(event.target.value)} slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: from, max: today() } }}/></Stack>
      <Stack direction={{ xs: 'column', sm: 'row' }} gap={1}><Button variant="contained" disabled={busy || !link.active || !from || !to || from > to} onClick={() => void action('sync')}>{busy ? 'Aguarde…' : 'Consultar dados'}</Button>
        <Button disabled={busy} onClick={() => void action(link.active ? 'disconnect' : 'reconnect')}>{link.active ? 'Desativar leitura aqui' : 'Reativar leitura'}</Button></Stack>
    </Stack>
  </Panel>;
}

function LinkForm({ config, links, onClose, onRefreshConfig, onUpdated }: {
  config: Config; links: BankLink[]; onClose: () => void;
  onRefreshConfig: () => Promise<void>; onUpdated: () => Promise<void>;
}) {
  const [item, setItem] = useState(config.items[0] ?? ''), [error, setError] = useState('');
  const [source, setSource] = useState(''), [destination, setDestination] = useState('');
  const accounts = useQuery({ queryKey: ['accounts'], queryFn: () => api<Account[]>('/accounts') });
  const remote = useQuery({
    queryKey: ['bankDiscover', item],
    queryFn: ({ signal }) => api<{ accounts: { id: string; name: string; type: string; currency: string }[] }>(
      `/banking/items/${item}/accounts`, { signal }),
    enabled: !!item,
  });
  const localChoices = accounts.data?.filter(a => !a.archived && a.currency === 'BRL' &&
    ['CHECKING', 'SAVINGS', 'WALLET', 'OTHER'].includes(a.kind) && !a.bankLinkId && !links.some(l => l.accountId === a.id)) ?? [];
  const bankChoices = remote.data?.accounts.filter(a => a.type === 'BANK' && a.currency === 'BRL' &&
    !links.some(l => l.externalAccountId === a.id)) ?? [];
  const externalAccountId = bankChoices.some(a => a.id === source) ? source : bankChoices.length === 1 ? bankChoices[0].id : '';
  const accountId = destination || (localChoices.length === 0 ? 'new' : '');
  const local = localChoices.find(a => a.id === accountId);
  const minDate = local ? shift(local.openingDate, 1) : '1900-01-02';
  const form = useForm({ defaultValues: { importFrom: today(), confirmedBaseline: false } });
  const canSubmit = !!accounts.data && !!remote.data && !accounts.error && !remote.error &&
    !!externalAccountId && (accountId === 'new' || !!local);
  function clearConfirmation() { form.setValue('confirmedBaseline', false); }
  const submit = form.handleSubmit(async value => {
    if (!canSubmit) return;
    setError('');
    const request = { ...value, itemId: item, externalAccountId };
    try {
      await api(accountId === 'new' ? '/banking/links/new-account' : '/banking/links', {
        method: 'POST', body: json(accountId === 'new' ? request : { ...request, accountId }),
      });
      await onUpdated();
      onClose();
    } catch (e) {
      setError((e as Error).message);
    }
  });
  return <FormDialog title="Vincular conta bancária" dirty={form.formState.isDirty || !!source || !!destination}
    busy={form.formState.isSubmitting} onClose={onClose}
    actions={<Button form="bank-link-form" type="submit" variant="contained"
      disabled={form.formState.isSubmitting || !canSubmit}>Vincular para leitura</Button>}>
    <form id="bank-link-form" onSubmit={submit}><Stack spacing={2} pt={1}>
      {error && <Alert severity="error">{error}</Alert>}
      {config.items.length > 1 && <TextField select label="Conexão autorizada" value={item} onChange={event => {
        setItem(event.target.value); setSource(''); clearConfirmation();
      }}>{config.items.map((id, index) => <MenuItem key={id} value={id}>Conexão {index + 1} · {id.slice(-6)}</MenuItem>)}</TextField>}
      {remote.isPending || accounts.isPending ? <Loading/> : remote.error ? <Failure error={remote.error} retry={() => void remote.refetch()}/> :
        accounts.error ? <Failure error={accounts.error} retry={() => void accounts.refetch()}/> :
        bankChoices.length === 0 ? <Stack spacing={1.5}>
          <Alert severity="info">Não há outra conta disponível nesta conexão. Contas já vinculadas aparecem em Conexões bancárias.</Alert>
          <Typography variant="body2">Autorize a nova conta no Meu Pluggy e atualize a lista. Se ela ainda não aparecer, a conexão pode precisar ser liberada para seu perfil.</Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
            <Button component="a" href="https://meu.pluggy.ai/" target="_blank" rel="noopener noreferrer" variant="outlined">
              Abrir Meu Pluggy
            </Button>
            <Button type="button" startIcon={<Refresh/>} disabled={remote.isFetching}
              onClick={() => void Promise.all([remote.refetch(), onRefreshConfig()])}>
              {remote.isFetching ? 'Atualizando…' : 'Atualizar lista'}
            </Button>
          </Stack>
        </Stack> : <>
        {bankChoices.length === 1 ? <Typography fontWeight={600}>{bankChoices[0].name}</Typography> :
          <TextField select required label="Conta disponível no Meu Pluggy" value={externalAccountId} onChange={event => {
            setSource(event.target.value); clearConfirmation();
          }}>{bankChoices.map(account => <MenuItem key={account.id} value={account.id}>{account.name} · {account.id.slice(-6)}</MenuItem>)}</TextField>}
        {localChoices.length === 0 ? <Typography variant="body2">Uma nova conta será criada com o nome e o saldo informados pelo banco.</Typography> :
          <TextField select required label="Conta no Java Finance" value={accountId} onChange={event => {
            const id = event.target.value;
            setDestination(id);
            const selected = localChoices.find(a => a.id === id);
            const afterOpening = selected ? shift(selected.openingDate, 1) : today();
            form.setValue('importFrom', afterOpening > today() ? afterOpening : today());
            clearConfirmation();
          }}>
            <MenuItem value="new">Criar nova conta com os dados do banco</MenuItem>
            {localChoices.map(account => <MenuItem key={account.id} value={account.id}>Usar {account.name}</MenuItem>)}
          </TextField>}
        <TextField type="date" label="Data inicial dos registros" required {...form.register('importFrom', {
          required: true, onChange: clearConfirmation,
        })} slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: minDate, max: shift(today(), 1) } }}/>
        <Controller name="confirmedBaseline" control={form.control} rules={{ validate: value => value || 'Confirme a data inicial.' }}
          render={({ field, fieldState }) => <Box>
            <FormControlLabel control={<Checkbox checked={field.value} onChange={event => field.onChange(event.target.checked)}/>}
              label={accountId === 'new' ? 'Conferi a conta e a data inicial da leitura.' : 'Conferi a data e os lançamentos existentes para evitar duplicidade.'}/>
            {fieldState.error && <Typography color="error">{fieldState.error.message}</Typography>}
          </Box>}/>
        <Typography color="text.secondary" variant="body2">A conta exibirá o saldo do banco. A categorização automática pode processar descrições na nuvem, sem valores ou documentos. Gerencie sua preferência em Configurações.</Typography>
      </>}
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
  return <FormDialog title={action === 'import' ? 'Revisar classificação' : action === 'match' ? 'Vincular a lançamento existente' : 'Ignorar lançamento'} dirty={form.formState.isDirty} busy={form.formState.isSubmitting} onClose={onClose}
    actions={<Button variant="contained" form="bank-review-form" type="submit" disabled={form.formState.isSubmitting || (action === 'import' && (categories.isPending || !!categories.error))}>{action === 'import' ? 'Confirmar classificação' : action === 'match' ? 'Confirmar vínculo' : 'Confirmar ignorar'}</Button>}>
    <form id="bank-review-form" onSubmit={submit}><Stack spacing={2} pt={1}>
      {error && <Alert severity="error">{error}</Alert>}
      <Typography sx={{ overflowWrap: 'anywhere' }}>{entry.description} · {civilDate(entry.date)} · {money(entry.amount)}</Typography>
      {action === 'ignore' ? <Typography>O registro será ignorado nesta revisão. Nenhum lançamento financeiro será criado ou cancelado.</Typography> : action === 'match' ? <>
        <Typography>O vínculo não altera o saldo nem duplica a movimentação existente. Confira os detalhes antes de confirmar.</Typography>
        <Controller name="movementId" control={form.control} rules={{ required: true }} render={({ field }) => <TextField select required label="Lançamento existente" {...field}>{entry.candidates.map(candidate => <MenuItem key={candidate.id} value={candidate.id}>{civilDate(candidate.date)} · {candidate.description} · {money(candidate.amount)}</MenuItem>)}</TextField>}/>
      </> : <>
        <Alert severity="warning">Será registrada uma {income ? 'receita' : 'despesa'} efetivada, por competência de {entry.date.slice(0, 7)}. O registro já faz parte dos relatórios. Esta ação define a categoria, sem duplicar o registro nem descontar o valor novamente do saldo bancário. Transferências próprias, empréstimos, investimentos e pagamentos de fatura não devem ser classificados assim.</Alert>
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
