import { useQuery } from '@tanstack/react-query';
import { api } from '../../lib/api';
export interface CardCharge {id:string;description:string;amount:string;date:string;direction:string;status:string;categoryId:string|null;version:number;billId:string|null;installment:number|null;installments:number|null;categorySource?:string|null;categoryMethod?:string|null;categoryResolution?:string|null}
export interface CardBill {id:string;externalId:string;dueDate:string;total:string;detailedTotal:string;detailCount:number;pendingCount:number;revision:string;reconciled:boolean}
export interface Card {id:string;name:string;billsAvailable:boolean;checkedAt:string;bills:CardBill[];charges:CardCharge[]}
export interface BankProductsSummary {
 connections:{itemId:string;automatic:boolean;cardsStatus:string;investmentsStatus:string;cardsCheckedAt:string|null;investmentsCheckedAt:string|null}[];
 cards:Card[];
 positions:{id:string;name:string;type:string;subtype:string;balance:string;date:string|null;profit:string|null;available:string|null;status:string;checkedAt:string}[];
 payments:{id:string;description:string;amount:string;date:string;version:number}[];
}
export function useBankProducts(){return useQuery({queryKey:['bankProducts'],queryFn:({signal})=>api<BankProductsSummary>('/banking/products',{signal})});}
