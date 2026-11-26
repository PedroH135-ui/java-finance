import type { QueryClient } from '@tanstack/react-query';

// Every view derives from the same records; invalidate together after a classification or edit.
const financialKeys=['movementFeed','linkedBankRecord','bankEntries','bankAssistance','financialNature','summary','budgets','upcomingCommitments','investments','investmentPositions','goals','planningFinances','salaryRules','categories','accounts','bankProducts','goalPositions','cardCharge'];
export function refreshFinances(client:QueryClient){return Promise.all(financialKeys.map(key=>client.invalidateQueries({queryKey:[key]})));}
