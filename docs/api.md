# API REST interna

## Conexões bancárias pessoais

Base `/api/banking`. Sessão e CSRF nas escritas. Configuração ausente não ativa acesso bancário.
- `GET /config`: ready, userId do próprio perfil, lista de itemIds autorizados e nome do provedor. Nunca retorna credenciais.
- `GET /items/{itemId}/accounts`: somente item permitido no servidor, contas BANK/BRL.
- `GET /links`: vínculos do proprietário pessoal designado.
- `POST /links`: `{itemId, externalAccountId, accountId, importFrom, confirmedBaseline:true}`; 201. A data inicial deve ser posterior à abertura da conta local.
- `POST /links/{id}/sync`: `{from,to}`; até 93 dias, 2.000 registros, tentativa limitada por vínculo. Retorna `{read}`. Não confirma movimentações.
- `GET /links/{id}/entries?page=0`: até 50 registros por página, sugestões de movimentos compatíveis para revisão.
- `POST /entries/{id}/ignore`: `{version}`; 204, sem alteração financeira.
- `POST /entries/{id}/match`: `{version,movementId}`; 204, liga a movimento efetivado compatível sem alterar saldo.
- `POST /entries/{id}/import`: `{version,categoryId,confirmedNature:true,reviewedDuplicates:false}`; 204. reviewedDuplicates deve ser true se houver possíveis duplicidades.
- `POST /links/{id}/disconnect`: desativa localmente e preserva histórico; não revoga o consentimento bancário.
- `POST /links/{id}/reconnect`: revalida disponibilidade da conta e reativa sem resetar histórico/data inicial.

PENDING não é importado/vinculado. Alterações no provedor geram CHANGED; nunca reescrevem automaticamente lançamentos já confirmados.
Outros perfis recebem 403, itens não permitidos recebem 404. Falhas do provedor são sanitizadas (502), sem respostas brutas ou chaves.
Detalhes de segurança, configuração e limites em `conexoes-bancarias.md`. Homologação de conexões reais deve ser registrada em ambiente privado.
Base /api. JSON UTF-8. Datas civis AAAA-MM-DD; competência AAAA-MM-01; valores monetários como strings, ex. "125.50".
Autenticação por cookie de sessão. Antes de POST/PATCH/PUT obtenha GET /auth/csrf e envie token no headerName retornado.
Erros de validação: {message, fields}. 401: sessão ausente; 403: CSRF/permissão; 404: registro não acessível; 409: concorrência/unicidade; 429: tentativas.

## Autenticação
- GET /auth/config: registrationEnabled.
- GET /auth/csrf: token e headerName.
- POST /auth/register: {email, name, password}. Retorna perfil (não abre sessão).
- POST /auth/login: form-urlencoded username (e-mail) e password. 204 em sucesso.
- POST /auth/logout: encerra sessão.
- GET /auth/me: {id, email, name}.
- PATCH /auth/me: {name}.
- POST /auth/password: {currentPassword, newPassword}; invalida sessões.

## Contas
GET/POST /accounts.
Criação: {name, institution, kind, openingBalance, openingDate}.
Tipos: CHECKING, SAVINGS, CASH, WALLET, INVESTMENT, OTHER. Moeda BRL.
PATCH /accounts/{id}: {name, institution, archived, version}. Saldo inicial imutável, corrigido com ADJUSTMENT.

## Categorias
GET/POST /categories.
Criação: {name, kind: INCOME|EXPENSE, essential}.
PATCH /categories/{id}: {name, essential, archived, version}. Natureza imutável.

## Movimentações
GET /movements?from=AAAA-MM-DD&to=AAAA-MM-DD: filtros opcionais account, category, kind, status, text.
Paginação page (base zero), size (1–100), sort (operationDate|amount|description|dueDate), direction (asc|desc).
Base do período: basis=operationDate (padrão), competence ou settledDate. Links de receitas/despesas do painel usam
basis=competence e status=CONFIRMED, além da natureza/categoria correspondente.
Retorna {items,total,page,size,pages}.
GET /movements/{id}; GET /movements/{id}/audit.

POST /movements:
```json
{
  "requestKey": "UUID gerado uma vez por submissão",
  "description": "Mercado",
  "amount": "125.50",
  "kind": "EXPENSE",
  "status": "CONFIRMED",
  "accountId": "UUID da conta",
  "destinationId": null,
  "categoryId": "UUID da categoria",
  "operationDate": "2026-10-07",
  "competence": "2026-10-01",
  "dueDate": "2026-10-07",
  "settledDate": "2026-10-07",
  "notes": ""
}
```
Tipos disponíveis: INCOME, EXPENSE, TRANSFER, ADJUSTMENT. OPENING é interno.
PLANNED exige settledDate null. TRANSFER exige destino diferente da origem; não recebe categoria.
Reenvie a mesma requestKey somente para a mesma operação; uma chave por usuário é única.
A identidade da criação inclui situação e data de efetivação, e permanece preservada após edições e cancelamentos.
POST /movements/{id}/confirm: {version, reason, settledDate}.
POST /movements/{id}/cancel: {version, reason, settledDate:null}.
PATCH /movements/{id}: {version, description, notes, reason}; só descrição/observações de previsão.

## Dashboard
GET /reports/summary?month=AAAA-MM.
Saldo atual, competência, fluxo de caixa, taxa de economia, categorias, seis meses e próximas previsões.
Sempre restrito ao proprietário autenticado. Ver regras financeiras para critérios.

## Automação bancária

- `GET /banking/config` inclui `automaticRead` (somente true para proprietário configurado e segredos de automação presentes).
- `GET /banking/links` inclui `autoCheckedThrough` e `autoErrorAt`; não inclui chaves de API.
- `POST /banking/hooks/pluggy`: endpoint de máquina com cabeçalho `X-JavaFinance-Webhook`, sem sessão/CSRF.
  Corpo até 8 KiB: `{ "event": "item/updated", "eventId": "UUID", "itemId": "UUID" }`.
  Campos de identidade e URLs adicionais não são utilizados. Evento duplicado reaproveita a tarefa existente.
- `GET /banking/jobs/daily`: endpoint de máquina com `Authorization: Bearer <CRON_SECRET>`, recuperação diária.
- Automação: 204 concluído/ignorado, 401 não autorizado/configuração ausente, 400 corpo inválido, 413 corpo grande,
  503 trabalho pendente (preservado para nova tentativa, `Retry-After: 300`). Nenhuma resposta contém dados financeiros.
- Apenas estas duas rotas de máquina dispensam CSRF; as demais escritas bancárias continuam exigindo sessão e token CSRF.

## Orçamentos

- `GET /budgets?month=AAAA-MM`: categorias de despesa, limites, efetivados, previstos, restante, percentual e totais.
- `PUT /budgets/{month}/categories/{categoryId}`: `{ "amount": "800.00", "version": null }` para criar; ao editar, enviar a versão recebida. Retorna 204. Versão desatualizada/criação duplicada retorna 409.
- `POST /budgets/{month}/copy`: `{ "sourceMonth": "2026-09" }`; retorna `{ "copied": 2, "skipped": 1 }`. Preserva limites no destino e ignora categorias arquivadas. Repetir não duplica.

Período de 1900-01 a 2200-12. Limite aceita zero; `null` no resultado significa que nenhum limite foi definido.
`spent` contém despesas CONFIRMED por competência, `planned` contém PLANNED, canceladas são excluídas.
`remaining = limit - spent`; pode ser negativo. `percent` é null quando não há limite ou o limite é zero.
Totais limit/spent/planned/remaining abrangem somente categorias com limite; gastos sem limite são apresentados em unbudgetedSpent/unbudgetedPlanned.
Categorias arquivadas continuam visíveis se houver limite ou gasto/previsto no período; não recebem novos limites enquanto arquivadas.
Todas as operações exigem sessão; escritas exigem CSRF. Proprietário vem da sessão, nunca do corpo da requisição.

