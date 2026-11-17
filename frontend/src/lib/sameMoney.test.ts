import { describe,it,expect } from 'vitest';
import { sameMoney } from './sameMoney';
describe('monetary eligibility without floating point',()=>{
 it('accepts different decimal scales',()=>{expect(sameMoney('480','480.00')).toBe(true);expect(sameMoney('-0.2','-0.20')).toBe(true);});
 it('distinguishes cents beyond safe integer precision',()=>{expect(sameMoney('999999999999999.90','999999999999999.91')).toBe(false);});
 it('rejects malformed values rather than enabling reconciliation',()=>{expect(sameMoney('NaN','NaN')).toBe(false);expect(sameMoney('1,00','1.00')).toBe(false);});
});
