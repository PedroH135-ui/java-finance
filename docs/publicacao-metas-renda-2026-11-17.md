# Publicação de salário e gastos no planejamento — 17/11/2026

Publicação de Java e Python autorizada explicitamente pelo usuário, condicionada a backup confirmado e V16 em branch isolada. Concluída em Production, sem importar lançamentos, criar postings, salvar uma meta fictícia, mudar previsões reais ou alterar saldos. Correções manuais e preferências anteriores foram preservadas. Esta documentação não autoriza merge, nova publicação, restauração ou ativação de usuários.

## Código e comportamento

- Implementação funcional `645d04b`, exportação exata do Git em `767bb95`, branch `codex/metas-renda-mensal`. [Implementação, fórmulas, fontes e limites](metas-renda-mensal-local.md).
- Em **Metas e planejamento**, salário líquido previsto e gastos mensais opcionais, histórico dos três meses anteriores com registros, distinção entre entradas e salário categorizado e cálculo conservador da capacidade mensal. O mês atual parcial não compõe a média; ausência de informação não vira zero.
- Contexto considera consumo atual e previsto, limites de orçamento e aportes dos outros planos. Mostra aporte exigido, diferença a economizar e prazo mínimo quando renda/gastos são conhecidos. Não promete dinheiro disponível nem presume juros.
- V16 adiciona somente `planning_income_profile`, sem preencher usuários existentes. Salvar previsões exige sessão, CSRF e versão; é informação de planejamento, não receita/despesa.
- Python/LangChain interpreta objetivo e resumo de salário/gastos sem LLM. Java calcula com decimal e dados do próprio usuário; contexto de renda/relatórios não vai à Groq nem aos embeddings. RAG/pgvector de categorias permanece separado e preservado.
- IA continua ativada por padrão apenas em novos cadastros/primeiro vínculo sem preferência; usuário pode desligar, opt-out salvo não é reativado. Cadastro público continua desabilitado. Natureza financeira e transferências próprias exigem confirmação manual.

## Banco correto e recuperação comprovada antes da V16

- Projeto Neon `java-finance` (`twilight-field-61680463`), branch principal `br-red-resonance-b6w00wvg`, banco `java_finance`. Identidade revalidada com configuração da release anterior, metadados Vercel sem alterações e guardas de host direto; credenciais não foram impressas ou incluídas neste relatório.
- Backup lógico V15 com cliente PostgreSQL18 e TLS verificado, criptografado por DPAPI. Decriptação e integridade do arquivo conferidas. Não ficou dump em texto claro no disco.
- Restauração real em **dois bancos novos**, `planning_recovery_20261117` e `planning_verify_20261117`, na branch isolada existente `br-round-hall-b668ttqy`. Nenhuma recuperação anterior foi sobrescrita ou excluída, nenhum plano alterado. A recuperação restaurada tem transações read-only por padrão; a branch foi conferida sem expiração.
- A mesma branch contém recuperações/verificações anteriores; não excluir como laboratório descartável. O backup DPAPI depende deste perfil Windows e não acompanha o Git: a restauração isolada validada é o ponto acessível pela conta Neon em outro computador.
- Hashes agregados antes/depois do dump e após restauração iguais. Conferidos account, movement, posting, bank_entry/link, category, financial_record, consumption_record, preferências/correções, pares de transferências, usuários, posições/naturezas de investimento, metas, orçamentos e saldos calculados. Nenhuma linha/valor real está na documentação pública.

## V16 isolada aprovada

PostgreSQL **18.7**, Flyway **11.20.3**. Migração **15→16** executada uma vez, em **2.285ms**; checksums validados e segunda execução com **zero** migrações. Nenhum aviso de incompatibilidade, constraint inválida ou alteração nos hashes protegidos. A nova tabela de perfis permaneceu vazia.

SQL da exportação Git, com bytes LF canônicos, foi comparado à versão testada localmente normalizando apenas CRLF/LF. Os mesmos arquivos/checksums foram usados no teste isolado e na publicação. Sem `repair`, edição de migração existente ou downgrade.

## Publicação e verificação em produção

Os candidatos foram construídos com `--prod --skip-domain`; saúde e banco foram conferidos antes da promoção. O início do Java candidato aplicou V16 antes da troca do domínio, comportamento esperado do Flyway. Python foi promovido primeiro por compatibilidade com o Java anterior; Java foi promovido após os gates.

| Serviço | Deployment publicado | Endereço |
|---|---|---|
| Java/React | `dpl_F99qaevGVVfwhbkkfAwmQieTqVM6` | [Java Finance](https://java-finance.vercel.app) |
| Python | `dpl_FAfZ4jeHmEXjfJSmSuqBThAeQ9Ji` | [Serviço de categorias e interpretação](https://java-finance-category-ai-lab.vercel.app) |

- Ambos READY; domínios estáveis apontam para esses deployments. Saúde Java/Python UP, Python Production/PGVECTOR384. Framework, vínculo Git e metadados de todas as variáveis/segredos preservados. Nenhuma nova regra de acesso, bypass, integração ou credencial criada.
- API Python autenticada com textos **fictícios**: três perguntas de salário/gastos retornaram SUMMARY preservando objetivo existente, inclusive com número no texto. Meta3000/reserva600/6meses interpretada corretamente. Todas com `modelUsed:false`, zero chamadas LLM, acesso financeiro ou escrita no banco pelo teste. Sem autenticação:401.
- Java: GET sem sessão em metas/perfil/contas/movimentações/investimentos retornou401, PUT de perfil sem sessão/CSRF403. Cadastro continua fechado. Bundle novo contém os campos previstos.
- Após login renovado, tela autenticada mostrou perfil, fontes, histórico e advertências. Pergunta sobre salário/gastos retornou contexto do próprio perfil. Quando salário não foi identificado, a interface manteve desconhecido e pediu complementação, sem chamar outras entradas de salário.
- Meta **explicitamente fictícia**3000/600/6meses calculou400/mês pela tela. Ausência de renda gerou aviso de impossibilidade de avaliar a capacidade. **Não** clicamos em salvar previsões/meta, não informamos salário fictício no perfil real nem alteramos progresso; recarregar apagou a conversa efêmera.
- Gate final após smoke autenticado: schema16, constraints válidas, todos os hashes financeiros/correções/preferências anteriores iguais; nova tabela de perfis vazia. A gravação de perfil foi testada no ambiente local fictício, não no usuário real de produção.
- Consulta pontual CLI error/fatal dos dois deployments retornou zero registros, sem paginação ou intervalos excluídos, entre aproximadamente17:17–17:32UTC de17/11. Não equivale a monitoramento contínuo. Nenhum log bruto ou dado financeiro foi publicado.
- Captura para entrega foi bloqueada pela revisão automática por risco de incluir valores financeiros; resultado documentado em texto. Não compartilhar imagens privadas da sessão.

## Testes da implementação preservados nesta release

Java131 aprovados (41unit+90IT, sete novos de planejamento), teste adicional V15→V16 com dados fictícios existentes, Python46, frontend lint/três testes/build e E2E completo Java/Python/PostgreSQL local em13,2s. O E2E validou salário3000/gastos2700/capacidade300 e meta3000/600/6→400/mês, diferença100 e prazo8, preservando saldo e fluxo. [Detalhes e limitações](metas-renda-mensal-local.md).

Esta publicação repetiu recuperação/restauração, migração isolada com SQL canônico, gates de banco, smoke dos dois serviços e verificação da tela. Não se atribui nova execução da suíte local inteira à operação de deploy. Aviso existente de bundle principal ~1,09MB antes de gzip continua como melhoria futura.

## Segurança e GitHub

Fontes exportadas dos dois serviços e commits de saída verificados com Gitleaks; sem achados. Checagem de arquivos privados aprovada. Varredura final incluiu a documentação de publicação: comparação em memória de12segredos conhecidos com496arquivos/recursos, zero correspondências; Gitleaks das fontes sem achados. Segredos, `.tools`, `.env`, sessões, dumps, DPAPI e logs financeiros ficam fora do Git.

Código e relatório enviados em `codex/metas-renda-mensal` e incorporados à [PR17](https://github.com/PedroH135-ui/java-finance/pull/17), já aberta com a organização V15, por avanço normal da sua cabeça `codex/organizacao-financeira-local`. Inclui V16, contexto, testes e rollback, sem merge ou novo deploy pelo envio. O CI remoto testa segurança/privacidade e frontend; a suíte Java/Python e migração isolada têm evidências locais descritas acima. Verificar os checks do HEAD final da PR antes de qualquer merge futuro.

## Rollback

Se houver regressão, revalidar a identidade/projetos e o impacto, então promover os deployments imediatamente anteriores:

- Java **`dpl_HzvgiTtJqCrbY2RRmZg2iNa9ys58`**, hostname `java-finance-enlk6axmd-pedro135he-7663s-projects.vercel.app`.
- Python **`dpl_CQ2R7PSbVpFA2219vPnntCRZi6JW`**, hostname `java-finance-category-ai-nhhsbreh9-pedro135he-7663s-projects.vercel.app`. Usa a chave Groq atual; não promover artefatos antigos com a chave revogada.

V16 é aditiva e pode permanecer com os perfis informados: o código anterior ignora essa tabela. Não apagar previsões, desfazer categorias/correções, remover tabelas ou restaurar produção inteira automaticamente. Rollback da aplicação retorna ao cálculo anterior de planejamento; não há estorno ou reaplicação financeira. Recuperação integral requer avaliar escritas posteriores e autorização específica. Não executar `scripts/cloud-provision.mjs` para atualizar/recuperar.

## Para usar

Abra **Metas e planejamento**, confira o histórico e informe seu salário líquido esperado quando necessário. Gastos previstos são opcionais; compromissos maiores já existentes continuam considerados. Salve previsões e envie nova mensagem para recalcular. Exemplo fictício: “Quero viajar e guardar3000”, “em6meses”, “já tenho600 reservado”. Salvar meta exige confirmação separada, sem movimentar dinheiro.
