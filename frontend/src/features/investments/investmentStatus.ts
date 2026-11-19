export type PositionFilter='ACTIVE'|'REDEEMED'|'ALL';
export function investmentStatus(status:string){return ({ACTIVE:'Ativo',PENDING:'Pendente',TOTAL_WITHDRAWAL:'Resgatado totalmente',REDEEMED:'Resgatado',MATURED:'Vencido'} as Record<string,string>)[status]??'Situação não informada';}
export function matchesPositionFilter(status:string,filter:PositionFilter){return filter==='ALL'||(filter==='ACTIVE'?status==='ACTIVE':['TOTAL_WITHDRAWAL','REDEEMED'].includes(status));}
