# Salário e módulos financeiros integrados — entrega local

17/11/2026 · branch `codex/salario-modulos-integrados`. Desenvolvimento e testes
com dados fictícios. **Não publicado**; produção registrada continua na V16.
Não houve consulta real ao Pluggy, mudança no Neon, push, PR ou merge nesta etapa.

## Comportamento implementado

- **É meu salário** nos detalhes de um recebimento bancário efetivado: aplica
  a categoria Salário e oferece uma regra pessoal opcional para recebimentos
  futuros na mesma conta, com a mesma descrição normalizada. Não usa LLM.
- A regra não reclassifica o histórico. Correção ou remoção humana tem
  prioridade; categoria arquivada não é reativada. Configurações permite pausar
  e reativar a regra; reativação vale para novos registros.
- **Salário previsto continua exclusivamente manual**, mesmo quando o histórico
  contém salários categorizados. Marcar um recebimento não muda a previsão.
- Os gastos vêm dos registros financeiros e de suas categorias/naturezas. Java
  faz a soma decimal; LLM/RAG ajuda a categorizar descrições quando necessário,
  sem calcular nem receber o saldo ou o planejamento completo.
- **Orçamento** mostra inicialmente categorias ativas com gasto, compromisso
  ou limite configurado. Há opção para ver/adicionar outras categorias e acesso
  aos registros sem categoria. Despesas pendentes sem categoria também contam
  nos compromissos do planejamento; ausência de categoria não significa gasto zero.
- **Investimentos** separa posições informadas pelo banco das posições manuais,
  mostra datas/limitações e permite abrir exatamente o registro sugerido. As
  sugestões são filtradas por regras antes do limite de 100 por período.
  Aplicações/resgates continuam exigindo confirmação da natureza financeira.
- A classificação, conciliação e edições invalidam os caches dos módulos
  relacionados, incluindo o contexto de metas, orçamento e dashboard.

## Integração adicional: cartão, faturas e posições

`BankProductsProvider` é um contrato separado do leitor de contas bancárias.
`PluggyProductsReader` usa o cliente read-only existente para consultar
`/accounts?type=CREDIT`, `/bills`, `/v2/transactions` e `/investments`.
Mantém allowlist de item, validação de conta/item, moeda, paginação limitada e
timeouts. Não segue URLs arbitrárias de respostas nem salva payloads brutos,
documentos, números de cartão ou credenciais.

Em Conexões bancárias, **Consultar cobertura e dados adicionais** inicia uma
leitura explícita. É possível optar por repeti-la após atualizações bancárias
futuras e pausá-la depois. Abrir a tela lê somente o cache. Falhas por produto
preservam os dados anteriores e informam sua possível desatualização; ausência de
dados não é apresentada como dívida ou posição zero. Lease com expiração e
verificação da tentativa evita gravações de leitores concorrentes/atrasados.

**Cobertura Nubank ainda não confirmada.** Os adaptadores estão implementados e
testados com respostas controladas. A disponibilidade real de faturas,
identificadores de fatura nas compras e posições depende do conector e do
consentimento. Teste local não prova que o banco real fornece todos esses campos.
Não solicitar nova conexão nem importar registros reais por inferência deste documento.

### Evitar despesa duplicada em faturas

1. A leitura salva detalhes do cartão separadamente. Eles ainda não entram no
   consumo e não geram lançamentos, postings ou alterações de saldo.
2. O usuário confere a fatura completa e escolhe seu pagamento bancário.
3. Java exige mesmo item, pagamento elegível/efetivado, versões atuais, detalhes
   efetivados e soma líquida exatamente igual ao total. Não estima compras
   ausentes, juros, pagamentos parciais ou diferenças.
4. Após confirmação, a projeção de **consumo** usa os detalhes/estornos no lugar
   do pagamento. O pagamento continua no fluxo de caixa. A competência dos
   detalhes usa o mês de vencimento da fatura.
5. Mudanças nos dados de origem invalidam a conciliação e exigem nova conferência.
   Desfazer mantém o histórico e volta a considerar o pagamento na projeção.

As categorias dos detalhes são corrigíveis. A fila existente pode categorizá-los
quando a preferência individual está ligada: correções e histórico privado
primeiro, recuperação RAG e até uma chamada LLM pelo classificador existente.
Abstenção terminal evita repetir consultas sem contexto novo. Usuário desativado
não chama o serviço cloud. Crédito/estorno reduz despesa; não vira receita.

## Desempenho e observabilidade

- Histórico de seis meses: uma consulta agrupada substitui as consultas mensais
  repetidas; totais/contagem do mês reutilizados. Outras consultas necessárias
  ao resumo permanecem. Não há medição comparativa de produção nesta entrega.
- Rotas React carregadas sob demanda; entrada principal caiu de aproximadamente
  1.094 KB para 637 KB, sem mudar dependências. Dashboard fica em outro chunk.
  O PWA ainda pré-carrega assets: isso não equivale a reduzir todo o tráfego.
  O aviso de chunk maior que 500 KB permanece.
- Busca em Movimentações tem debounce de 300 ms e cancelamento. Polling ocioso
  passa a 60 s, mantendo 5 s durante trabalho ativo e sem polling em segundo plano.
- Cada requisição recebe `X-Correlation-ID`. Logs registram módulo permitido,
  status, duração total, tempo de aquisição de conexão, quantidade/duração JDBC.
  Não registram SQL, parâmetros, descrição, valores, usuário ou query string.
  A correlação é propagada à chamada Python já instrumentada.
- Duração JDBC inclui ida/volta ao banco; aquisição de conexão mede espera mais
  obtenção. Não confundir isso com tempo exclusivo de execução no PostgreSQL.

## Migrações e validação local

- **V17** cria regras de salário e a origem `RULE`. `first_seen_at` não recebe
  backfill: linhas anteriores continuam sem timestamp, impedindo aplicação
  retroativa das regras.
- **V18** cria snapshots normalizados de produtos, classificação de detalhes,
  conciliação manual e projeção de consumo compatível com os registros antigos.
  `financial_record`, postings e cálculo de saldo não são substituídos.
- Java: **150 testes aprovados**, 44 unitários + 106 de integração, PostgreSQL
  descartável. Incluem proprietário/CSRF/versão, preferência desligada,
  correções prioritárias, regras futuras, cobertura parcial, conciliação,
  invalidação por mudança da origem, desfazer e invariantes financeiros.
- Frontend: lint e **6 testes aprovados**, build integrado gerado.
- Upgrade **V16→V18 no banco fictício já existente** aprovado: fingerprints das
  colunas anteriores e das projeções financeiras iguais antes/depois.
- **E2E integrado aprovado: 1 cenário, 29,1 s.** Salário manual/regra futura,
  previsão independente, leitura adicional, conciliação exata, categoria do
  cartão no orçamento, investimento confirmado e pausa/reativação da regra.
  Saldo fictício1000 preservado, consumo480, nenhuma movimentação manual,
  opt-out preservado, zero requisições externas e erros JavaScript. Duas
  rodadas anteriores corrigiram seletores do teste (link e moeda formatada),
  sem relaxar as verificações financeiras; cada rodada usou fixture nova.
- Inspeção adicional no navegador local confirmou a previsão manual3000,
  gastos previstos2700, capacidade300 e consumo480. Python local continuou
  com `modelEnabled=false`: este cenário não consumiu Groq.
- Amostra local autenticada, três leituras sequenciais por endpoint, mediana
  HTTP: dashboard376 ms, orçamento186 ms, planejamento254 ms, produtos41 ms.
  É uma amostra fictícia aquecida, sem comparação anterior e sem extrapolação
  para produção. Correlacionável aos agregados JDBC sem valores nos logs.
- Segurança:12 segredos conhecidos comparados com605 arquivos/recursos públicos
  e JAR, zero correspondências; Gitleaks1,57MB sem achados. Arquivos privados,
  fixtures geradas e evidências permanecem ignorados. Isso reduz o risco e não
  substitui nova revisão antes de enviar/publicar.

## Reproduzir a demonstração

Requer Docker, Java/Python/Node locais e os ambientes descritos em
`docs/rag-vetorial-local.md`. Configurações e evidências ficam em `.tools`,
ignoradas pelo Git. Não copiar configurações reais para este laboratório.

```powershell
# Com banco demo preparado e Python vetorial local UP, sem modelo:
$env:PYTHONPATH='vector-lab'
.tools/vector-rag-venv/Scripts/python.exe -m finance_vector.seed_modules --root .
pwsh -NoProfile -File scripts/start-review-local.ps1 -Build -Restart -VectorAi -SyntheticProducts
# Acesse http://127.0.0.1:8082
```

`-SyntheticProducts` exige o banco exato em loopback e um usuário
`modules-…@example.test`, seleciona provedores em memória e não faz chamadas
ao Pluggy/Groq. É incompatível com IA remota/automação do usuário demo.
O email fictício está em `.tools/local-review/modules-fixture.json`; senha
fictícia `DemoLocal123!`. O seed cria novo usuário; não deve ser repetido no meio
de um teste. O perfil `local-review` falha fora desse ambiente guardado.

```powershell
# Em frontend, com esse servidor ativo e fixture nova:
$env:MODULE_REVIEW_E2E='1'
$env:E2E_URL='http://127.0.0.1:8082'
npx playwright test e2e/modules-local.spec.ts
```

Não usar `cloud-provision.mjs`, conexão de produção, wrapper que resemeia a demo
vetorial ou credenciais reais para executar este cenário.

## Próxima publicação e rollback

Publicação **não autorizada por este arquivo**. Antes: rever diff/checks e
segredos, identificar banco correto, confirmar recuperação, restaurar/testar
V16→V18 em branch Neon isolada e validar PostgreSQL18.7. Iniciar Java com conexão
real já pode aplicar Flyway, mesmo sem promover o domínio.

Rollback precisa de gate próprio antes de produção: a V16 não conhece a origem
`CARD`. Não basta promover o Java antigo sobre a view nova. Preparar e testar na
branch isolada uma projeção compatível que mantenha os registros antigos e faça
os pagamentos com histórico de conciliação voltarem ao consumo, sem detalhes de
cartão. Restaurar cegamente a view V16 excluiria pagamentos já marcados como
fatura e poderia omitir o consumo; não usar esse atalho.
Depois desse gate, desativar leituras adicionais e promover Java/React anterior;
Python não mudou. Preservar V17/V18, regras, categorias/correções e histórico de
conciliação. Não executar down migration, `repair` ou estornar postings.
Conferir novamente consumo, orçamento, fluxo de caixa e saldos.
Recuperação de banco só com ponto restaurado e procedimento revisado, não para
apagar a nova estrutura como rotina.

CI anterior da PR17 permanece com pendência de dependência `picomatch`;
esta entrega não alterou dependências nem suprimiu a auditoria. Revalidar o
advisory oficial e o HEAD da PR antes de qualquer publicação/merge.

Contratos externos conferidos na documentação oficial: [paginação e v2](https://docs.pluggy.ai/en/reference/basic-concepts),
[metadados de compras](https://docs.pluggy.ai/en/docs/products/transactions),
[faturas](https://v2.docs.pluggy.ai/en/reference/bill/bills-list) e
[posições](https://docs.pluggy.ai/en/docs/products/investments).
