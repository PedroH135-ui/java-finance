# Proposta de integração e desempenho dos módulos

17/11/2026. Auditoria do código V16 e da sessão autenticada em produção. **Proposta para revisão, sem implementação funcional, migração, publicação, alteração de preferência, categoria, natureza ou saldo.** Navegação, filtro Sem categoria e detalhes foram usados somente para leitura. Dados individuais, nomes de contrapartes, valores reais e contagens privadas não estão neste documento.

Pedido mais recente: salário somente informado pelo usuário; gastos identificados automaticamente com custo controlado; melhorar carregamento, Investimentos, Orçamentos, faturas e coerência entre módulos. Não aplicar essas propostas em produção por inferência deste documento. A versão publicada continua sendo a V16 do relatório de publicação.

## Decisão recomendada para salário e gastos

**Salário previsto será exclusivamente manual.** O campo continua editável, pode ficar vazio e não será preenchido nem substituído pelo histórico. Entradas observadas podem continuar em um histórico separado, claramente rotuladas; não alimentam salário previsto. Hoje ainda existe fallback HISTORY em `PlanningFinanceService.java:79`: é comportamento atual a remover em uma implementação futura, não uma alteração já executada.

**Gastos realizados vêm dos registros bancários/manuais; IA identifica categoria e sugere natureza, Java calcula valores.** RAG recupera exemplos/contexto, não extratos completos para somar em texto. Não pedir ao LLM o total mensal, saldo ou rendimento. A [documentação de recuperação do LangChain](https://docs.langchain.com/oss/python/deepagents/retrieval) distingue recuperação de contexto de geração; não exige substituir consultas SQL por vetores.

Ordem proposta: correção explícita do usuário → regras determinísticas/provedor → exemplos pessoais fortes no pgvector → no máximo uma chamada LLM quando necessária → pendência com motivo. Manter opt-out, isolamento por usuário e decisões automáticas fora do histórico humano. Atualização de conhecimento deve permitir reavaliar pendências específicas, sem pagar novamente por todos os registros a cada abertura da tela.

Separar três grandezas na interface:

- Consumo realizado no período: soma exata, inclusive despesas ainda sem categoria.
- Compromissos/previstos e limites de orçamento: referência de planejamento, sem chamar limite de dinheiro gasto.
- Previsão mensal: estimativa baseada em histórico disponível, recorrências e compromissos. Mostrar cobertura e base, sem salário presumido ou gasto zero por mês ausente.

## Evidências e propostas mínimas

| Tema | Evidência atual | Próxima alteração proposta | Como validar |
|---|---|---|---|
| Dashboard | `reports/ReportService.java:42–45`: seis iterações, cada uma chama totais e count, pelo menos12 SQLs apenas no histórico; outras consultas seguem no mesmo request | Um agrupamento por competência devolvendo somas e contagens dos seis meses; reutilizar o resultado do mês atual | Comparar respostas e número de SQLs antes/depois, mesmos filtros, valores e ausência de dados |
| Carregamento inicial | `frontend/src/App.tsx:8–16`: módulos importados juntos; build anterior ~1,09MB sem gzip | Divisão por rota, gráficos carregados sob demanda e skeleton por seção | Tamanho dos chunks, tempo até conteúdo, navegação e falha isolada de seção |
| Requisições repetidas | `CategoryAutomationNotice.tsx:33`: status a cada5s mesmo sem trabalho em andamento; `Movements.tsx:35`: lista a cada30s | Polling mais frequente só durante processamento, recuo quando ocioso e sem polling em aba oculta; atualizar por versão/evento | Contar requests/minuto em cenário ocioso e ativo, atualização continua chegando |
| Busca | `Movements.tsx:47`: texto muda o filtro e a query a cada tecla | Debounce curto e cancelamento de request anterior | Digitação rápida gera uma consulta útil e não mostra resposta antiga |
| Detalhes | `FinancialNature.tsx:12`: todo detalhe consulta o resumo completo de investimentos | Buscar só posições necessárias quando a natureza for investimento, endpoint leve | Detalhe de fatura não pede investimentos; seleção de posição ainda funciona |
| Planejamento desatualizado | Confirmação em `FinancialNature.tsx:19`, categoria em `BankAssistance.tsx:28`, orçamento em `Budgets.tsx:99` não invalidam `planningFinances` | Centralizar mapa de invalidação para categoria, visibilidade, natureza, orçamento e metas | Corrigir/ocultar/confirmar e consultar outro módulo sem recarregar a página |
| Orçamento poluído | `BudgetService.java:45`: NOT archived inclui todas as despesas ativas, mesmo zeradas; frontend mostra a lista inteira | Visualização principal com categorias ativas relevantes no mês; seleção secundária para planejar outra categoria | Categoria zerada sem limite não aparece; com gasto, previsto ou limite aparece; histórico arquivado não some dos totais |
| Investimentos incompletos | `InvestmentService.java:21–29`: posição informada manualmente, fluxos só confirmados, sugestões nos100 registros mais recentes | Agrupar por instituição/produto, dar acesso direto ao registro por ID, separar aplicações sugeridas/confirmadas de posição atual e paginar | Registro antigo não desaparece por corte arbitrário; nenhuma soma de aportes se passa por patrimônio |
| Fatura sem categoria | `CategoryAdvisor.java:143` e `pipeline.py:60,92` abstêm diante de fatura/aplicação; V14 já contém regra de natureza | Mostrar tipo financeiro sugerido na lista e motivo da pendência; confirmar/reverter sem abrir uma sequência de diálogos | Regra clara não chama LLM, confirmação preservada, cancelamento/mudança do banco invalida confirmação |
| Compras do cartão ausentes | `PluggyReadClient.java:83–89` consulta e aceita só contas BANK; não lê CREDIT, faturas ou produtos de investimentos | Investigar cobertura do item/conector, depois construir integração específica de cartão/fatura/posição, idempotente | Compra e pagamento não duplicam consumo, parcelas e pagamentos parciais conciliados, ausência de cobertura explicitada |

As referências de linha correspondem ao código auditado, antes de implementar esta proposta. Não são medições de latência ou planos de execução do banco.

## Medição antes de alterar infraestrutura

Não medimos percentis dos endpoints autenticados nem executamos EXPLAIN nesta auditoria. Cold start, espera pelo pool e custo das views são hipóteses a separar; não atribuir a lentidão à Groq ou ao Neon sem evidência. O pool cloud configurado tem máximo4, mínimo0, timeout30s e descarte ocioso60s; isso não prova saturação ou suspensão do banco. Não aumentar pool, desativar suspensão ou contratar plano sem medir.

Instrumentar duração total HTTP, espera por conexão, quantidade/tempo agregado de SQL, chamada Python, embeddings/recuperação e LLM, com um correlation ID propagado. Já há tempos de leitura bancária e de chamada ao advisor; faltam medições completas da navegação. Registrar rota normalizada, status, duração e resultado técnico, sem corpo, query com descrições, parâmetros SQL, IDs bancários, valores, senhas ou tokens.

Comparar primeira carga após ociosidade com cargas quentes, especialmente summary, feed, budgets, investments e goals/finances. Medir p50/p95 e requests por navegação com o mesmo conjunto fictício e, depois, leitura controlada real. EXPLAIN ANALYZE somente em cópia isolada, sem alterações em produção; índices devem seguir plano e cardinalidade observados. Cache privado por usuário/período/versão, nunca CDN compartilhada com dados autenticados.

## Sem categoria: resolver a causa

Na sessão real apareceram tipos distintos sob o mesmo rótulo Analisado, sem categoria. Os termos fatura/aplicação são bloqueados deliberadamente antes do classificador comum; isso não demonstra falha da Groq. PIX também pode ficar sem categoria se o provedor não fornecer PERSON/BUSINESS. `PluggyReadClient.java:121–124` persiste apenas o tipo inferido de metadados, nunca o número de documento; nome de pessoa ou empresa não basta para provar esse tipo. Não reler o banco ou chamar LLM indiscriminadamente para tentar adivinhar.

Proposta de motivos visíveis: aguardando natureza financeira; tipo de contraparte não informado; categoria compatível ausente; contexto insuficiente/conflitante; serviço temporariamente indisponível; removida/bloqueada pelo usuário. Distinguir sem informação de erro técnico e de escolha manual. Uma correção pode oferecer aplicar a registros equivalentes do próprio perfil, com visualização dos afetados e respeito às correções anteriores. Não aplicar em massa sem ação explícita.

PIX de pessoa comprovada → PIX, permitindo correção. PIX de empresa comprovada → histórico/RAG e uma chamada LLM se necessário. UNKNOWN → pendência explicada; não usar LLM para adivinhar identidade por nomes. Falta de finalidade não justifica inventar uma categoria específica de consumo.

## Faturas: identificação e conciliação

Hoje já há sugestão correta de CARD_PAYMENT nos detalhes e confirmação manual obrigatória para mudar indicadores. Melhorar a apresentação e não tratar esse caso como fracasso do classificador de despesas.

O ponto crítico é cobertura: integração BANK traz pagamento da fatura, sem garantir as compras do cartão. Excluir esse pagamento sem trazer as compras pode subestimar gastos; contar pagamento junto com as compras duplica o consumo. Até conciliar, exibir separadamente pagamento de cartão não detalhado/cobertura incompleta. Identificar o tipo por regra sem LLM; manter confirmação manual da natureza nesta etapa e informar o efeito nos indicadores.

Futuro fluxo: conta/cartão → fatura → compras/parcelas → pagamento correspondente. Compras compõem consumo; pagamento conciliado é quitação de obrigação e continua no fluxo de caixa. Juros, tarifas, estornos, parcelas e pagamento parcial precisam de regras/testes próprios. Não esconder esses valores dentro de uma exclusão genérica de fatura. Categoria e natureza são dimensões diferentes, não duas formas de descontar o saldo.

## Investimentos úteis e conectados

Primeira melhoria: lista agrupada por instituição/produto, quantidade de identificações pendentes, data/fonte da posição, aplicações e resgates por período. Criar ou associar posição a partir da identificação, sem reescrever descrições ou duplicar fluxo. Link por ID em vez de pesquisa textual ambígua em registros repetidos. Quando só houver fluxo conhecido, mostrar posição desconhecida, sem chamar aporte líquido de saldo/rendimento.

Depois: verificar se o conector/item/consentimento atual oferece produtos de investimentos e quais campos retornam. A [Pluggy documenta posição, saldo e data de referência](https://docs.pluggy.ai/en/docs/products/investments), mas [cobertura do conector e recursos daquele consentimento](https://docs.pluggy.ai/en/docs/connections/investments-coverage) precisam ser conferidos. Não prometer que todas as Caixinhas, rendimentos ou liquidez estejam disponíveis, nem presumir que todo produto do Nubank seja do mesmo tipo. Nenhuma consulta nova a produtos bancários foi feita nesta auditoria.

Associar uma meta à parcela informativa de uma posição permite evitar contar o mesmo dinheiro em várias metas. Usar vínculo/limite de alocação, sem transferir ou reservar dinheiro no banco. Rentabilidade só com dados suficientes, método explícito e posição confiável.

## Orçamento focado e vínculo entre módulos

Regra de exibição recomendada: categoria de consumo ativa com gasto realizado positivo, despesa prevista positiva **ou limite configurado**, inclusive limite zero explicitamente salvo. Mostrar só categorias com gasto excluiria o planejamento antes da primeira compra. Categorias arquivadas com histórico devem continuar contabilizadas e acessíveis na visão histórica, sem reaparecer como escolha ativa. Sem categoria deve ser um grupo visível com acesso à pendência, não dinheiro omitido.

Resumo: gasto realizado, previsto, limite, restante e categorias acima do limite. Valor planejado/limite não é gasto realizado. Limite pode ser sugerido pelo histórico e ajustado à renda manual/metas, mas precisa de aprovação do usuário antes de salvar. Evitar criar centenas de limites automaticamente sem orçamento pessoal definido.

Os módulos já compartilham `financial_record` e `consumption_record`; melhorar a integração existente, sem copiar lançamentos entre módulos:

```text
Banco / registros manuais
          ↓
Registro único + categoria + natureza + visibilidade
          ├── Extrato e Contas: fluxo completo / saldo da fonte correta
          ├── Consumo válido: Dashboard, Orçamento e Relatórios
          ├── Aplicação / resgate: Investimentos
          └── Salário manual + consumo/compromissos + metas: Planejamento
```

Usar período e competência explícitos e consistentes. Movimentações usa operação por padrão, enquanto orçamento/relatórios usam competência; períodos diferentes podem parecer divergência sem estar errados. Links devem preservar os filtros, e alterações devem atualizar as queries dependentes, inclusive planningFinances. Saldo bancário não precisa nem deve esperar categorização ou ser recalculado pela IA.

## Ordem de entrega e regressão

1. Instrumentação, agrupamento SQL do dashboard, carregamento por rota, busca com debounce e polling condicionado. Comparar antes/depois sem prometer um ganho numérico ainda não medido.
2. Salário exclusivamente manual, cache coerente e orçamento relevante com grupo Sem categoria e competência explícita.
3. Motivos de pendência, identificação de fatura/investimento na lista e navegação direta, preservando confirmações e reversão.
4. Cobertura e conciliação reais de cartão/faturas e posição de investimentos, por integração separada e testada. Qualquer nova migração exige recuperação confirmada e branch isolada.

Em cada entrega, testar owner/opt-out/correção humana, saldo manual e bancário, filtros, categoria arquivada, limite zero, ocultação/restauração, natureza alterada pelo banco e atualização entre telas. Testar ausência e falha de IA sem travar leitura; gastar no máximo uma chamada quando o caso exigir. Casos sintéticos de fatura: só pagamento disponível, compras+pagamento, parcial, juros/tarifas, estorno e parcelas. Investimentos: aplicações/resgates repetidos não duplicam posição, fluxo não inventa rendimento, dinheiro de meta não é contado duas vezes.

Rollback deve voltar código/visibilidade/cache da etapa, sem desfazer correções, apagar registros ou mexer em saldos. A PR17 continua sendo a release anterior V16, com audit de picomatch pendente; esta proposta não foi enviada nem incorporada à PR e não resolve aquele advisory.
