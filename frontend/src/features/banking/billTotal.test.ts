import {describe,it,expect} from 'vitest';
import {originalBillTotal,billCanReconcile} from './billTotal';
describe('total original da fatura',()=>{
 it('preserva sinal e todas as casas sem arredondar',()=>{
  expect(originalBillTotal('1234.5600000000001')).toBe('R$ 1.234,5600000000001');
  expect(originalBillTotal('-4.1234567890123456789')).toBe('-R$ 4,1234567890123456789');
  expect(originalBillTotal('10')).toBe('R$ 10,00');
 });
 it('bloqueia conciliação de negativo, zero, precisão fracionária ou valor ausente',()=>{
  for(const value of ['-1.00','0.00','1.001','','NaN'])expect(billCanReconcile(value)).toBe(false);
  for(const value of ['1.00','0.01','5.0000'])expect(billCanReconcile(value)).toBe(true);
 });
});
