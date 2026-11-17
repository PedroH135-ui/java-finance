# Salário, gastos mensais e capacidade de guardar — etapa local

17/11/2026. Branch `codex/metas-renda-mensal`, commit funcional local `645d04b`, baseada na versão já publicada. **Não publicada nem enviada ao GitHub.** A [PR17](https://github.com/PedroH135-ui/java-finance/pull/17) contém somente a organização V15 já em produção e está aberta, com checks de segredos/privacidade e frontend aprovados. Não houve merge ou novo deploy nesta etapa.

## Pedido e comportamento

O usuário pediu que o planejador conheça salário, gastos mensais e quanto precisa economizar para atingir uma meta. Antes, ele dividia o que faltava pelo prazo e comparava com a sobra registrada do mês atual, que pode estar parcial. Agora mostra as fontes e calcula uma estimativa mensal separada do saldo bancário.

Em **Metas e planejamento**:

- Perfil informativo com **Salário líquido mensal previsto** e **Gastos mensais previstos (opcional)**. Valores fornecidos pelo usuário têm prioridade sobre a média histórica. Limpar um campo volta ao histórico. Zero informado é diferente de informação ausente. Salvar não cria salário, despesa ou movimentação.
- Entradas totais e valores na categoria **Salário** aparecem separadamente, inclusive no mês atual. Não presume que PIX recebido, resgate, rendimentos ou qualquer outra receita sejam salário.
- Mostra os três meses de calendário anteriores que têm registros e as médias de entradas, salário categorizado e consumo. O mês atual, ainda parcial, não entra na média. Meses sem registros não são incluídos como zero; é exibida a quantidade de meses observados e a limitação de cobertura.
- Sem previsão fornecida, estima salário somente pelos registros na categoria Salário/Salários/Salário líquido. É uma indicação de categoria, não comprovação de renda recorrente. Não infere estabilidade por texto de transferência nem cria novas categorias.
- Usa `consumption_record`: naturezas patrimoniais saem somente depois de confirmação manual. Ocultos/cancelados e transferências próprias já pareadas não viram consumo/salário. Natureza inválida por mudança bancária volta à projeção comum.
- Para não subestimar o mês, gastos considerados são o maior entre a referência mensal (informada/histórica), o consumo atual + despesas previstas, e os limites configurados + despesas sem orçamento (realizadas/previstas).
- Aportes dos outros planos ainda não concluídos reduzem a capacidade de uma nova meta. Cada plano usa `(objetivo−reservado)/meses`, arredondado para cima em centavos. São compromissos de planejamento, não reservas bancárias.

## Exemplo fictício concreto

Salário3000, gastos2700 e nenhuma outra meta → capacidade300/mês. Para objetivo3000, reserva600 e6meses, faltam2400; aporte necessário400/mês. A resposta mostra a diferença100/mês e consumo máximo2600 para manter o prazo. Com capacidade300, o prazo mínimo estimado é8meses. Não recomenda um produto financeiro nem presume juros.

Se não houver salário ou gastos conhecidos, calcula quanto a meta exige, mas não diz que ela cabe na renda. Se não houver sobra positiva, explica que apenas aumentar o prazo não resolve. Arredondamento em decimal evita declarar um aporte menor por erro de ponto flutuante.

## Aplicação real no código

- V16 cria somente `planning_income_profile`, por proprietário: previsões opcionais, versão e atualização. Nenhum perfil é inserido para usuários existentes pela migração. Nada altera account/movement/posting/bank_link.
- `PlanningFinanceService`: consultas owner-scoped, snapshot REPEATABLE_READ, projeção por competência, fontes USER/HISTORY/UNKNOWN, orçamento e metas existentes, avaliação exata. Atualização do perfil com sessão/CSRF e versão; auditoria registra a ação sem os valores.
- `GET/PUT /api/goals/finances`: consultar e salvar o perfil. A própria tela mostra a previsão usada e suas limitações. Alterar perfil/progresso invalida a proposta; recalcular por nova mensagem antes de salvá-la.
- `GoalService`: recebe a intenção estruturada do Python e consulta o contexto do próprio usuário. Devolve salário/gastos/capacidade, aporte necessário, diferença e prazo mínimo; a interface explica as bases. Perguntas sobre salário/gastos são resumo, não mudança automática de perfil.
- `planning.py`: a cadeia LangChain determinística reconhece perguntas como “quanto ganho de salário?” e “quanto gasto por mês?”. Números nessas perguntas não sobrescrevem o objetivo. Nenhuma chamada LLM é adicionada.
- **Privacidade:** contexto financeiro e cálculos permanecem no Java. Não envia renda, saldos, relatórios, perfil ou extratos à Groq e não transforma valores monetários em embeddings. O Python recebe só o texto enviado e o estado da meta, como antes; não recebe o contexto obtido das consultas. Texto que o próprio usuário digitar pode conter valores; o interpretador próprio na nuvem não encaminha isso ao LLM.

## Limites e decisões

Média de registros não prova extrato completo. Receitas extraordinárias não elevam automaticamente a previsão salarial. Categoria Salário errada pode distorcer a estimativa: corrigir a categoria ou informar a previsão. Outros rendimentos recorrentes ainda não têm campo próprio. Metas usam o horizonte informado, não um cronograma automático com data final. Não é planejamento completo de caixa por vencimento, nem declaração de dinheiro disponível.

Referência manual de gastos não oculta compromissos maiores já registrados. Limites de orçamento são piso conservador para a projeção e não dinheiro gasto efetivamente; a interface identifica o ajuste. O contexto é uma fotografia: atualizar dados e recalcular antes de decidir. Salvar meta continua sendo ação explícita, sem reserva bancária.

## Validação e retomada

- Java:131 testes aprovados (41unit+90IT), incluindo sete novos casos de salário separado de PIX, média sem mês atual, perfil prioritário, diferença/prazo, orçamento, despesas futuras, outras metas, confirmação de investimento, informação ausente/zero, arredondamento, owner/CSRF/versão e ausência de valores na auditoria.
- V15→V16 com dados fictícios existentes: teste adicional aprovado; perfil continua vazio após migração, financial_record e preferência desativada preservados.
- Python:46 testes aprovados nesta etapa, teste legado Ollama fora da venv vetorial não foi repetido; frontend lint/três testes/build aprovados. Aviso de bundle grande permanece.
- E2E completo Java/Python/PostgreSQL local aprovado em13,2s: salário3000/gastos2700/capacidade300; meta3000/reserva600/6meses exige400, diferença100 e prazo mínimo8. Pergunta sobre salário/gastos devolveu os valores corretos. Nenhuma meta salva, zero requisições externas do navegador/erros JavaScript, nenhuma receita/despesa criada; saldo e fluxo iguais antes/depois. Captura e invariantes só em `.tools/planning-income`.
- Ambiente separado app8082/Postgres55434/Python8126/pgvector55435, Groq desligado. Não usar produção para reproduzir. O cadastro local também permanece desabilitado. `python -m finance_vector.seed_planning --root .`, na venv/PYTHONPATH do laboratório preparado, cria apenas um usuário fictício e duas categorias, após conferir container/banco/perfil. Reutiliza o hash do login fictício apenas dentro do PostgreSQL, sem expor seu valor. Não cria contas, movimentos, postings ou registros bancários. Executar esse seed antes de repetir o E2E, sem abrir o cadastro.
- Segredos: 12 valores protegidos comparados em memória com495arquivos/recursos, zero correspondências; Gitleaks fontes sem achados. Fixtures, senhas, hashes, dumps e logs reais não acompanham o código. O seed só escreve a identificação fictícia em arquivo ignorado.
- Após reiniciar Docker, os containers fictícios estavam parados. A proteção do seed bloqueou corretamente; foram iniciados somente `java-finance-review-db` e `java-finance-vector-lab`, após conferir labels e portas127.0.0.1. Nenhum container de outro projeto foi alterado.

Produção permanece V15, conforme `publicacao-organizacao-2026-11-16.md`; a PR17 não contém V16. Antes de publicar esta evolução: autorização explícita, backup do banco correto, V16 em branch isolada, contrato Python compatível e testes. Iniciar o Java com conexão real aplicaria Flyway. Não usar scripts antigos de provisionamento. Rollback futuro pode manter a tabela aditiva, preservando previsões; não executar downgrade destrutivo nem apagar dados. Não copiar credenciais, dumps, DPAPI ou `.tools` ao GitHub/outro computador.
