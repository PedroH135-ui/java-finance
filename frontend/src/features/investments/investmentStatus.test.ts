import {describe,it,expect} from 'vitest';
import {investmentStatus,matchesPositionFilter} from './investmentStatus';
describe('posições bancárias',()=>{
 it('identifica resgate total sem chamar posição de ativa',()=>{expect(investmentStatus('TOTAL_WITHDRAWAL')).toBe('Resgatado totalmente');expect(matchesPositionFilter('TOTAL_WITHDRAWAL','ACTIVE')).toBe(false);expect(matchesPositionFilter('TOTAL_WITHDRAWAL','REDEEMED')).toBe(true);});
 it('pendente, vencido e desconhecido ficam disponíveis em todos sem entrar nos ativos ou resgatados',()=>{for(const state of ['PENDING','MATURED','UNKNOWN','']){expect(matchesPositionFilter(state,'ACTIVE')).toBe(false);expect(matchesPositionFilter(state,'REDEEMED')).toBe(false);expect(matchesPositionFilter(state,'ALL')).toBe(true);}});
 it('ativo aparece no filtro inicial e não no de resgatados',()=>{expect(matchesPositionFilter('ACTIVE','ACTIVE')).toBe(true);expect(matchesPositionFilter('ACTIVE','REDEEMED')).toBe(false);});
});
