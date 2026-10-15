// Dinheiro é transportado como decimal textual. Não usamos ponto flutuante para somar ou formatar.
export function money(value: string): string {
  const negative = value.startsWith('-');
  const [integer, fraction = ''] = value.replace(/^-/, '').split('.');
  return (negative ? '-R$ ' : 'R$ ') + BigInt(integer || '0').toLocaleString('pt-BR') + ',' + fraction.padEnd(2, '0').slice(0, 2);
}
export const today = () => new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Sao_Paulo', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
export const civilDate = (value: string | null) => value ? value.split('-').reverse().join('/') : '—';
export function monthBounds(month: string) {
  const [year, m] = month.split('-').map(Number);
  const last = new Date(Date.UTC(year, m, 0)).getUTCDate();
  return { from: month + '-01', to: month + '-' + last };
}
export const monthLabel = (value: string) => new Intl.DateTimeFormat('pt-BR', { month: 'short', year: 'numeric', timeZone: 'UTC' }).format(new Date(value + '-01T12:00:00Z'));
export const kindLabels: Record<string,string> = { INCOME: 'Receita', EXPENSE: 'Despesa', TRANSFER: 'Transferência', OPENING: 'Saldo inicial', ADJUSTMENT: 'Ajuste de saldo' };
export const statusLabels: Record<string,string> = { PLANNED: 'Previsto', CONFIRMED: 'Efetivado', CANCELLED: 'Cancelado' };
export const accountKinds: Record<string,string> = { CHECKING: 'Conta corrente', SAVINGS: 'Poupança', CASH: 'Dinheiro', WALLET: 'Carteira digital', INVESTMENT: 'Conta de investimento', OTHER: 'Outra conta' };

