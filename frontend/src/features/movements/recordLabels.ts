import type { FinancialRecord } from '../../lib/types';
const natures:Record<string,string>={CARD_PAYMENT:'Pagamento de fatura',INVESTMENT_IN:'Aplicação em investimento',INVESTMENT_OUT:'Resgate de investimento',OWN_TRANSFER:'Transferência própria'};
const reasons:Record<string,string>={USER_CHOICE:'Escolha manual preservada',RULE_REVIEW:'O banco alterou o registro; confira sua regra',NEEDS_NATURE:'Aguardando confirmação da natureza',UNKNOWN_COUNTERPARTY:'Tipo de contraparte não informado pelo banco',INSUFFICIENT_CONTEXT:'Contexto insuficiente para categorizar',NOT_CLASSIFIED:'Categoria ainda não definida'};
export function recordNature(record:FinancialRecord){
 if(record.source==='CARD')return 'Detalhe de cartão conciliado';
 if(record.financialNature&&natures[record.financialNature])return natures[record.financialNature]+' · confirmada';
 if(record.suggestedNature&&natures[record.suggestedNature])return natures[record.suggestedNature]+' · identificada, não confirmada';
 return record.source==='BANK'?(record.kind==='TRANSFER'?'Transferência própria':record.kind==='EXPENSE'?'Saída no banco':'Entrada no banco'):null;
}
export function categoryNote(record:FinancialRecord){
 if(record.categoryId&&record.categorySource==='RULE')return ' · Sua regra';
 if(record.categoryId&&record.categorySource==='AUTO')return ' · Automática';
 if(!record.categoryId&&record.categoryPendingReason)return ' · '+(reasons[record.categoryPendingReason]??'Categoria pendente');
 return !record.categoryId&&record.categoryResolution==='NO_CATEGORY'?' · Contexto insuficiente para categorizar':'';
}
