import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Alert, Box, Button, Divider, Stack, Typography } from '@mui/material';
import { api } from '../../lib/api';
import { money } from '../../lib/format';
import type { BankConfig, BankLink } from '../../lib/types';
import { Panel } from '../../components/Shared';

function updatedAt(value: string | null) {
  if (!value || !Number.isFinite(Date.parse(value))) return 'Não informada';
  return new Date(value).toLocaleString('pt-BR', { timeZone: 'America/Sao_Paulo' }) + ' (Brasília)';
}

/** Only reads stored snapshots. Rendering this panel never queries the bank or imports entries. */
export function BankOverview() {
  const config = useQuery({ queryKey: ['bankConfig'], queryFn: () => api<BankConfig>('/banking/config') });
  const links = useQuery({ queryKey: ['bankLinks'], queryFn: () => api<BankLink[]>('/banking/links'), enabled: config.data?.ready === true });
  const loading = config.isPending || (config.data?.ready === true && links.isPending);
  const error = config.isError || (config.data?.ready === true && links.isError);
  const linked = config.data?.ready === true && (links.data?.length ?? 0) > 0;
  return <Box mb={3}><Panel title="Saldo informado pelo banco" subtitle="Último saldo recebido pelo Meu Pluggy. Separado do saldo interno e dos resultados do mês.">
    {loading ? <Typography role="status">Carregando saldo bancário…</Typography>
      : error ? <Alert severity="warning">Não foi possível carregar o saldo bancário. O saldo interno e os demais indicadores continuam disponíveis.</Alert>
      : linked ? <Stack spacing={2} divider={<Divider/>}>{links.data?.map(link => <Box key={link.id}>
        <Typography component="h3" variant="subtitle1" fontWeight={600} sx={{ overflowWrap: 'anywhere' }}>{link.name}</Typography>
        <Typography variant="h5" className="money">{link.reportedBalance == null ? 'Saldo não disponível' : money(link.reportedBalance)}</Typography>
        <Typography variant="body2" color="text.secondary" mt={1}>Atualização no banco: {updatedAt(link.providerUpdatedAt)}</Typography>
        <Typography variant="body2" color="text.secondary">Última consulta no sistema: {updatedAt(link.lastCheckedAt)}</Typography>
        {!link.active && <Typography variant="body2" color="text.secondary" mt={1}>Leitura desativada. Exibindo o último saldo armazenado.</Typography>}
      </Box>)}</Stack>
      : <Typography color="text.secondary">Vincule sua conta Nubank autorizada no Meu Pluggy para acompanhar o saldo informado pelo banco.</Typography>}
    <Typography variant="caption" color="text.secondary" display="block" mt={2}>Não é saldo em tempo real e não é somado ao saldo interno. Os lançamentos continuam sujeitos à sua revisão manual; nada é importado automaticamente.</Typography>
    <Button component={Link} to={linked || loading || error ? '/banking' : '/banking?new=1'} variant="outlined" sx={{ mt: 2 }}>
      {linked ? 'Ver conexão e revisar lançamentos' : loading || error ? 'Ver conexão bancária' : 'Vincular meu banco'}
    </Button>
  </Panel></Box>;
}
