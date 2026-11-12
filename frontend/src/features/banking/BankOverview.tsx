import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Alert, Box, Button, Divider, Stack, Typography } from '@mui/material';
import { api } from '../../lib/api';
import { bankDateTime, money } from '../../lib/format';
import type { BankConfig, BankLink } from '../../lib/types';
import { Panel } from '../../components/Shared';

/** Only reads stored snapshots. Rendering this panel never queries the bank or imports entries. */
export function BankOverview() {
  const config = useQuery({ queryKey: ['bankConfig'], queryFn: () => api<BankConfig>('/banking/config') });
  const links = useQuery({ queryKey: ['bankLinks'], queryFn: () => api<BankLink[]>('/banking/links'), enabled: config.data?.ready === true });
  const loading = config.isPending || (config.data?.ready === true && links.isPending);
  const error = config.isError || (config.data?.ready === true && links.isError);
  const linked = config.data?.ready === true && (links.data?.length ?? 0) > 0;
  return <Box mb={3}><Panel title="Saldo informado pelo banco" subtitle="Último saldo recebido pelo Meu Pluggy. Usado como saldo principal nas contas vinculadas.">
    {loading ? <Typography role="status">Carregando saldo bancário…</Typography>
      : error ? <Alert severity="warning">Não foi possível carregar o saldo bancário. Confira a conexão bancária e tente novamente.</Alert>
      : linked ? <Stack spacing={2} divider={<Divider/>}>{links.data?.map(link => <Box key={link.id}>
        <Typography component="h3" variant="subtitle1" fontWeight={600} sx={{ overflowWrap: 'anywhere' }}>{link.name}</Typography>
        <Typography variant="h5" className="money">{link.reportedBalance == null ? 'Saldo não disponível' : money(link.reportedBalance)}</Typography>
        <Typography variant="body2" color="text.secondary" mt={1}>Atualização no banco: {bankDateTime(link.providerUpdatedAt)}</Typography>
        <Typography variant="body2" color="text.secondary">Última consulta no sistema: {bankDateTime(link.lastCheckedAt)}</Typography>
        {!link.active && <Typography variant="body2" color="text.secondary" mt={1}>Leitura desativada. Exibindo o último saldo armazenado.</Typography>}
      </Box>)}</Stack>
      : <Typography color="text.secondary">Vincule sua conta Nubank autorizada no Meu Pluggy para acompanhar o saldo informado pelo banco.</Typography>}
    <Typography variant="caption" color="text.secondary" display="block" mt={2}>Não é saldo em tempo real. Os registros aguardam sua revisão para categorias e relatórios, sem descontar os valores novamente.</Typography>
    <Button component={Link} to={linked || loading || error ? '/banking' : '/banking?new=1'} variant="outlined" sx={{ mt: 2 }}>
      {linked ? 'Ver conexão e revisar lançamentos' : loading || error ? 'Ver conexão bancária' : 'Vincular meu banco'}
    </Button>
  </Panel></Box>;
}
