// Preserve the bank snapshot exactly; ordinary ledger formatting remains cent based.
export function originalBillTotal(value:string){
 if(!/^-?\d+(\.\d+)?$/.test(value))return 'Total não disponível';
 const negative=value.startsWith('-'),[whole,fraction='']=value.replace(/^-/,'').split('.');
 return `${negative?'-':''}R$ ${BigInt(whole).toLocaleString('pt-BR')},${fraction.padEnd(2,'0')}`;
}
export function billCanReconcile(value:string){
 if(!/^\d+(\.\d+)?$/.test(value))return false;
 const [whole,fraction='']=value.split('.');
 return !/[1-9]/.test(fraction.slice(2))&&(BigInt(whole)>0n||/[1-9]/.test(fraction));
}
