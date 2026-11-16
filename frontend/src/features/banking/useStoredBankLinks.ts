import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
import type { BankConfig, BankLink } from '../../lib/types';

/** Polls our stored snapshots, never the provider. Shares the cache with the banking screen. */
export function useStoredBankLinks() {
  const config = useQuery({ queryKey: ['bankConfig'], queryFn: () => api<BankConfig>('/banking/config') });
  const ready = config.data?.ready === true;
  const links = useQuery({ queryKey: ['bankLinks'], queryFn: () => api<BankLink[]>('/banking/links'), enabled: ready,
    refetchInterval: ready ? 30000 : false });
  return { config, links, ready, loading: config.isPending || (ready && links.isPending),
    error: config.error || (ready ? links.error : null) };
}
