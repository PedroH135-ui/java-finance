import { describe, expect, it } from 'vitest';
import { money, monthBounds, civilDate, bankDateTime } from './format';
describe('Contratos de exibição', () => {
  it('identifica datas bancárias ausentes e usa o horário de Brasília', () => {
    expect(bankDateTime(null)).toBe('Não informada');
    expect(bankDateTime('inválida')).toBe('Não informada');
    expect(bankDateTime('2026-11-12T01:23:39Z')).toBe('11/11/2026, 22:23:39 (Brasília)');
  });
  it('formata dinheiro sem perder centavos e sem somar floats', () => {
    expect(money('123456789012345.67')).toBe('R$ 123.456.789.012.345,67');
    expect(money('-0.10')).toBe('-R$ 0,10');
    expect(money('100')).toBe('R$ 100,00');
  });
  it('preserva datas civis e considera fevereiro bissexto', () => {
    expect(civilDate('2026-10-07')).toBe('07/10/2026');
    expect(monthBounds('2024-02')).toEqual({ from: '2024-02-01', to: '2024-02-29' });
  });
});

