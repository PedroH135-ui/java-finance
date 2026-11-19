// Exact display-side eligibility check; the server still validates all amounts and ownership.
export function sameMoney(left:string,right:string){
 const cents=(value:string)=>{
  if(!/^-?\d+(\.\d{1,2})?$/.test(value))return null;
  const negative=value.startsWith('-'),[whole,fraction='']=value.replace(/^-/,'').split('.');
  return (BigInt(whole)*100n+BigInt(fraction.padEnd(2,'0')))*(negative?-1n:1n);
 };
 const a=cents(left),b=cents(right);return a!==null&&b!==null&&a===b;
}
