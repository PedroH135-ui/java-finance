# Progresso da V1
Referência: especificação fornecida pelo usuário em 07/10/2026. Todas as etapas continuam no objetivo.

## Etapa 1 — Fundação
Implementação da estrutura, segurança, contas, categorias, movimentações, transferências e dashboard inicial.
Fundação funcional validada em 07/10/2026: 2 testes unitários e 11 de integração PostgreSQL,
2 testes de interface, lint/build e 1 cenário completo de navegador passaram.
Execução integrada Java/React no Windows com PostgreSQL Docker confirmada; backup/restore fictício validado.
Consulte `validacao.md` para limites (imagem Docker da aplicação, acessibilidade completa e otimização de bundle).

## Etapa 2 — Controle — parcial
Orçamentos mensais e cópia implementados em 13/10/2026: módulo separado, limites por categoria, efetivados/previstos,
restante/percentual, histórico de categorias arquivadas, proteção de concorrência e cópia sem sobrescrita.
Ainda pendentes: orientação opcional de gastos; CSV/OFX com prévia, confirmação idempotente e revisão de correspondências; regras locais.
Taxa de economia básica já existe no dashboard, mas isso não conclui a etapa.

## Etapa 3 — Planejamento — pendente
Reserva, viver de renda, metas, alocações sem duplicidade e simuladores.
## Etapa 4 — Compromissos — pendente
Cartões, faturas, parcelas, pagamentos parciais, estornos, recorrências idempotentes, assinaturas, calendário, alertas e previsão.
## Etapa 5 — Visão completa — pendente
Investimentos manuais, avaliações, patrimônio histórico, dívidas, relatórios completos, exportação pessoal e CSV protegido.
## Etapa 6 — Consolidação — parcial
Testes de fundação, documentação e restore em banco descartável validados. Auditoria completa de acessibilidade, segurança e fluxos de todos os módulos ainda dependem da evolução.

## Próxima tarefa
Conexões bancárias pessoais implementadas em 14/10/2026, com leitura e importação após revisão. Testes automatizados usam simulações.
Configuração privada concluída; autenticação e descoberta de uma conta real MeuPluggy validadas sem consultar lançamentos.
Leitura automática acrescentada: avisos `item/updated`, recuperação diária, caixa de entrada persistida, retentativas,
cursor histórico e status na interface. Importação continua manual e revisada; não altera automaticamente o ledger.
Homologar o vínculo e a revisão de lançamentos com o usuário após a publicação. Ver `conexoes-bancarias.md`.
Orçamentos validados para esta publicação. Em seguida implementar importação CSV por lote em módulo próprio,
com prévia, revisão de duplicidades e confirmação; acrescentar OFX após validar um parser compatível.
Não começar por telas sem persistência. Recorrências, calendário e metas continuam separados e pendentes.

## Limitações conhecidas
- As opções do menu correspondem apenas à etapa implementada.
- Não são inseridos usuários, senhas ou movimentações de demonstração no banco de uso.
- Edição de valores confirmados exige cancelamento + novo registro.
- Sem recuperação de senha ou notificações externas. Integração bancária pessoal aguarda homologação do fluxo completo com o usuário; não inclui cartões ou pagamentos.
- Limitador de autenticação local em memória.

