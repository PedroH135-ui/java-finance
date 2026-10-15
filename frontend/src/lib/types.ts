export interface Profile { id: string; email: string; name: string }
export interface Account { id: string; name: string; institution: string; kind: string; currency: string; openingDate: string; balance: string; archived: boolean; version: number }
export interface Category { id: string; name: string; kind: string; essential: boolean; archived: boolean; version: number }
export interface Movement {
  id: string; description: string; amount: string; kind: string; status: string; accountId: string; destinationId: string | null;
  categoryId: string | null; operationDate: string; competence: string; dueDate: string; settledDate: string | null;
  notes: string; source: string; externalReference: string | null; batchId: string | null; createdAt: string; version: number;
}
export interface Summary {
  month: string; today: string; balance: string;
  competence: { income: string; expenses: string; result: string; savingsRate: string | null };
  cashFlow: string; categories: { id: string; name: string; total: string }[];
  evolution: { month: string; income: string; expenses: string; records: number }[];
  upcoming: { id: string; description: string; dueDate: string; amount: string; kind: string }[];
  accountCount: number; records: number;
}
export interface MovementPage { items: Movement[]; total: number; page: number; size: number; pages: number }

