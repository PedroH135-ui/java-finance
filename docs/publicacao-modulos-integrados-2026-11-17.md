# Salário e módulos integrados publicados — 17/11/2026

Publicação e atualização do GitHub autorizadas explicitamente pelo usuário,
condicionadas a backup confirmado e V17/V18 em branch isolada. Java/React
publicado; Python preservado. Sem merge na branch padrão.

## Resultado funcional

- **É meu salário** categoriza o recebimento escolhido; regra pessoal opcional
  vale para novos recebimentos com a mesma descrição na mesma conta. Correções
  e remoções têm prioridade. Pausa/reativação em Configurações.
- Salário mensal previsto exclusivamente manual; marcar recebimento não o
  substitui. Gastos/compromissos são somados no Java, sem enviar o planejamento
  financeiro à Groq. Categorias dos detalhes de cartão reutilizam a fila e o
  classificador existentes, com preferência individual e prioridade humana.
- Orçamento prioriza categorias em uso, preserva acesso às demais e destaca
  registros sem categoria; módulos invalidam caches relacionados.
- Consultas do histórico do dashboard agrupadas, rotas React sob demanda,
  busca com debounce/cancelamento e menor polling ocioso. Logs contêm somente
  correlação, módulo, status e durações/agregados JDBC, sem dados financeiros.
- Leitura adicional opcional de cartões/faturas/posições, com cache, datas e
  estados de cobertura. Não gera postings, pagamentos ou aplicações. Faturas
  exigem conciliação manual e cobertura/totais exatos para substituir o pagamento
  pelos detalhes no consumo, mantendo o pagamento no caixa e o saldo igual.

## Recuperação confirmada antes das migrações

Projeto Neon **java-finance**, PostgreSQL18.7, região São Paulo. A configuração
Vercel permaneceu com os mesmos metadados de variáveis/segredos da publicação
anterior verificada; endpoint direto guardado e identidade do projeto/branch
conferidos. Senhas e conexões não foram impressas ou colocadas no Git.

Backup atual da **V16**, `pg_dump`18 com TLS verificado, criptografado por DPAPI.
Decriptação e digest do arquivo gravado conferidos, inclusive novamente ao
fechar a publicação. Não foi mantido dump de dados em texto claro no disco.

Restauração real em dois bancos **novos** na branch isolada existente
`br-round-hall-b668ttqy`, sem expiração:

- `modules_recovery_20261117_v16`: recuperação V16, read-only por padrão,
  configuração conferida em uma conexão nova.
- `modules_verify_20261117_v18`: teste das migrações e da projeção de rollback.

Nenhuma branch/recuperação anterior foi redefinida, removida ou sobrescrita.
Não excluir essa branch como laboratório antigo. A restauração isolada permite
retomar pela conta Neon em outro computador; o arquivo DPAPI depende do perfil
Windows e permanece privado.

Fingerprints das colunas anteriores, contas, lançamentos/postings, registros e
vínculos bancários, categorias, correções/preferências, usuários, transferências,
investimentos/naturezas, metas, orçamentos, perfil de planejamento, contexto
financeiro, saldos calculados e projeções iguais após dump/restauração. Somente
hashes/booleanos foram extraídos para as evidências privadas, sem linhas reais
na documentação pública.

## V17/V18 e rollback testados isoladamente

Flyway11.20.3/PostgreSQL18.7, SQL canônico exportado do Git e comparado ao artefato
testado localmente, normalizando apenas CRLF/LF. Dezoito checksums registrados;
nenhuma migração antiga modificada ou `repair` executado.

| Gate | Resultado |
|---|---|
| V16→V17 | Uma migração, 2.227ms |
| V17→V18 | Uma migração, 2.604ms |
| Validação e repetição | Aprovada, zero migrações na repetição |
| Compatibilidade PostgreSQL18.7 | Sem aviso de versão não suportada |
| Dados financeiros/correções/preferências | Fingerprints preservados |
| Constraints | Todas validadas |
| Tabelas novas de regras/produtos | Vazias após migração |

Teste adicional de rollback na mesma cópia isolada: fixture inteiramente
fictícia, fatura400+100−20=480, saldo1000 e nenhum posting. A projeção V18 mostrou
três detalhes e consumo480; o [SQL compatível](sql/rollback-v18-consumption.sql)
mostrou o pagamento uma vez, sem origem CARD, mantendo consumo480/caixa/saldo.
A projeção canônica foi reaplicada e voltou aos três detalhes. Todo o cenário e
o DDL foram revertidos por transação; fingerprints reais restaurados continuaram
iguais. A recuperação V16 não foi usada como banco de testes mutável.

## Publicação e verificação

Fonte funcional/rollback exportada do commit **`2575d5d`**, branch
`codex/salario-modulos-integrados`; commits anteriores `b5d06c4`, `0ce9b3f` e
`a58cdf5`. Exportação contém arquivos versionados, sem `.tools`, envs, dumps ou
configurações locais. A aplicação foi construída no container da Vercel com a
configuração Production existente, `--prod --skip-domain`.

| Serviço | Deployment em uso |
|---|---|
| Java/React, novo | `dpl_2rzkbwNhztw62SrYXR8aspQFCH2b` |
| Python, preservado | `dpl_FAfZ4jeHmEXjfJSmSuqBThAeQ9Ji` |

O candidato protegido foi conferido pela sessão Vercel já autenticada: saúde
UP, hosting cloud, cadastro fechado. A tentativa HTTP sem autenticação alcançou
o SSO; não foi tratada como falha do Java nem como smoke aprovado. Não criamos
bypass, Trusted Sources, variáveis ou permissões novas. Schema18 e fingerprints
foram conferidos antes da promoção; o Flyway aplicou V17/V18 ao iniciar o
candidato, antes de trocar o domínio, como previsto.

Promovido o mesmo build para [Java Finance](https://java-finance.vercel.app).
Domínios Java/Python READY e metadados de variáveis/segredos, framework e vínculo
Git preservados. Python não foi reconstruído nem promovido nesta etapa.

Smoke público final aprovado: saúdeUP, correlação UUID, cadastro fechado,
GETs protegidos401, POSTs sem CSRF403, seis novos bundles de rota presentes e
banner de demonstração local ausente. As requisições negativas foram barradas
antes de consultar produtos ou alterar registros.

Na sessão real já aberta: autenticação preservada, novo filtro de sem categoria,
regras de salário em Configurações, previsão exclusivamente manual em Metas,
painel/botão da consulta adicional e posições do banco em Investimentos.
**Somente leitura:** não marcamos salário, não criamos regras/previsões,
não categorizamos registros reais, não acionamos consulta Pluggy/LLM nem
conciliamos faturas. Tela deixada em Conexões bancárias.

Gate após promoção e inspeção: schema18, constraints válidas, fingerprints
financeiros/correções/preferências anteriores iguais e tabelas novas vazias.
Consulta de logs error/fatal do novo Java entre19:44:20–19:59:20UTC retornou zero
registros, sem paginação restante ou intervalos excluídos. Não é monitoramento
contínuo nem garantia de cobertura do conector.

## Testes e limites

Implementação local aprovada antes desta publicação: **150 testes Java**
(44unit+106IT), frontend lint/**seis testes**/build, upgrade fictício16→18 e E2E
integrado em29,1s. O E2E prova salário/regra, previsão manual, cartão/fatura,
categoria no orçamento, investimento e Configurações, preservando saldo1000,
sem movimentos manuais, requisições externas ou erros JavaScript.
[Detalhes locais](salario-modulos-integrados-local.md).

Esta operação acrescentou restauração atual, Flyway em PostgreSQL18.7, rollback
isolado, construção cloud e smoke/inspeção reais; não afirma nova execução da
suíte local inteira. Python/modelos não mudaram. Leitura real do Nubank para
faturas e posições **ainda não foi executada**; cobertura depende do conector e
do consentimento. A consulta adicional é optativa e não importa lançamentos.

O PWA ainda pré-carrega assets e há aviso de chunk maior que500KB. A redução do
bundle de entrada e das consultas não é um benchmark de produção. Resultados
das três amostras locais não são extrapolados à nuvem.

## Segurança e GitHub

Fontes exportadas verificadas com Gitleaks, sem achados; checagem de arquivos
privados aprovada. Comparação em memória de12 segredos conhecidos com606
arquivos/recursos públicos e JAR, zero correspondências antes da publicação.
Repetir ao fechar o contexto e enviar ao GitHub. Arquivos privados, sessões,
DPAPI, dumps e evidências reais permanecem fora do Git.

Destino autorizado: [PR17](https://github.com/PedroH135-ui/java-finance/pull/17),
por avanço normal de sua branch, sem force-push, merge ou novo deploy. A PR
também contém as entregas cloud anteriores. Conferir
o HEAD final e seus checks, sem atribuir resultados antigos ao novo commit.

**Pendência de CI preexistente:** `picomatch` continua sem versão corrigida no
[GHSA-fc5q-39xc-vjpf](https://github.com/advisories/GHSA-fc5q-39xc-vjpf), reconsultado
nesta operação. A dependência está nas ferramentas Node de build; a imagem final
contém JRE/estáticos. Isso delimita a exposição observada, sem resolver o aviso.
Não alteramos lockfile, reduzimos auditoria ou suprimimos o check. Manter a PR
sem merge enquanto o problema não tiver encaminhamento revisado.

## Procedimento de rollback em caso de regressão

Deployment anterior Java/React: **`dpl_F99qaevGVVfwhbkkfAwmQieTqVM6`**, hostname
`java-finance-33ozs656q-pedro135he-7663s-projects.vercel.app`.
Python atual permanece; não promover artefatos com chave revogada.

1. Conferir projeto/branch e recuperação, interromper leituras adicionais e
   evitar novas conciliações durante a troca.
2. Aplicar o [SQL compatível testado](sql/rollback-v18-consumption.sql) em
   transação controlada. Ele mantém a forma antiga da view, troca os detalhes
   pelo pagamento no consumo e preserva as outras naturezas confirmadas.
3. Promover o Java anterior e conferir novamente consumo, fluxo e saldos.
4. Manter V17/V18, regras, categorias/correções, posições e conciliações.
   Os detalhes categorizados ficam guardados, mas a interface antiga perde
   temporariamente essa granularidade. Não apagar dados, executar down migration
   ou `repair`, nem restaurar a produção inteira como rotina.
5. Para retornar à versão nova, testar/reaplicar a definição canônica da view V18
   e promover o build novo. Não editar o arquivo Flyway já aplicado.

Não basta promover V16 sobre a view com origem CARD, nem restaurar cegamente a
view V16 que excluiria pagamentos já conciliados. Recuperação integral exige
avaliar escritas posteriores e procedimento específico. Nunca executar
`scripts/cloud-provision.mjs` para atualizar ou recuperar esta aplicação.
