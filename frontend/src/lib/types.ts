export interface Profile { id: string; email: string; name: string }
export interface BankConfig { ready: boolean; userId: string; items: string[]; provider: string; automaticRead?: boolean }
export interface BankLink {
  id: string; name: string; accountId: string; importFrom: string; active: boolean;
  lastCheckedAt: string | null; providerUpdatedAt: string | null; reportedBalance: string | null;
  autoCheckedThrough?: string | null; autoErrorAt?: string | null;
}
export interface Account { id: string; name: string; institution: string; kind: string; currency: string; openingDate: string; balance: string | null; archived: boolean; version: number;
  balanceSource?: 'BANK' | 'CALCULATED'; bankUpdatedAt?: string | null; bankCheckedAt?: string | null; bankReadActive?: boolean; bankLinkId?: string | null }
export interface Category { id: string; name: string; kind: string; essential: boolean; archived: boolean; version: number }
export interface Movement {
  id: string; description: string; amount: string; kind: string; status: string; accountId: string; destinationId: string | null;
  categoryId: string | null; operationDate: string; competence: string; dueDate: string; settledDate: string | null;
  notes: string; source: string; externalReference: string | null; batchId: string | null; createdAt: string; version: number;
}
export interface Summary {
  month: string; today: string; balance: string | null; balanceComplete?: boolean; unclassifiedBankRecords?: number;
  competence: { income: string; expenses: string; result: string; savingsRate: string | null };
  cashFlow: string; categories: { id: string | null; name: string; total: string }[];
  evolution: { month: string; income: string; expenses: string; records: number }[];
  upcoming: { id: string; description: string; dueDate: string; amount: string; kind: string }[];
  accountCount: number; records: number;
}
export interface MovementPage { items: Movement[]; total: number; page: number; size: number; pages: number }


export interface FinancialRecord extends Omit<Movement, 'externalReference' | 'batchId' | 'createdAt'> {
  bankEntryId: string | null; bankLinkId: string | null; bankEntryVersion: number | null;
  bankProviderStatus: string | null; bankState: string | null; hidden: boolean;
}
export interface FinancialRecordPage { items: FinancialRecord[]; total: number; page: number; size: number; pages: number }
