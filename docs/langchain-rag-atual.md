# Como LangChain e RAG funcionam hoje

Conferido no código em 13/11/2026. Este documento descreve a implementação existente, sem prometer funcionalidades futuras.

## O que está publicado

| Parte | Situação |
|---|---|
| Python FastAPI + LangChain + Groq na Vercel | Publicado e testado com dados fictícios |
| Integração Java/React de categorias e transferências desta entrega | Desenvolvida e testada, no GitHub; publicação pendente do backup e da V9 |
| Banco PostgreSQL de produção | Continua no Neon; conta/projeto proprietário ainda não identificado com segurança |
| Aplicação Java atualmente em uso | Continua na versão anterior; não está usando a nova integração Python |

O Python funciona na nuvem sem o computador ligado. A disponibilidade continua sujeita às cotas e ao funcionamento da Vercel e da Groq; não há garantia de uso gratuito ilimitado. O serviço não possui banco próprio nem credenciais Neon/Pluggy.

## Uma sugestão, do início ao fim

1. Na interface desenvolvida, abrir os detalhes de um registro bancário consulta `GET /api/banking/entries/{id}/assistance`. Essa leitura não aplica uma categoria.
2. O Java verifica proprietário e elegibilidade. A categorização atende registros efetivados, visíveis, sem lançamento associado e sem identificação de transferência. Se já existe categoria válida, não pede uma nova sugestão.
3. O Java consulta até 100 categorias ativas compatíveis com receita/despesa e até 100 registros anteriores categorizados, confirmados e visíveis, do mesmo proprietário e natureza. O histórico vem da view `financial_record`.
4. O Java normaliza os textos. Se encontra uma classificação exata e consistente no histórico, sugere diretamente; se há conflito ou natureza financeira ambígua, pode retornar sem sugestão. Esses casos não precisam de LLM.
5. Para consultar o Python, o Java seleciona no máximo 20 categorias e cinco exemplos semelhantes. Envia somente descrição normalizada, categorias e exemplos por HTTPS com token de serviço.
6. O Python executa a cadeia LangChain: `recuperar_exemplos → sugerir_categoria`. Primeiro recupera exemplos; depois tenta histórico semelhante, regras e, por último, o LLM.
7. A resposta contém categoria permitida ou ausência de sugestão, origem, consistência e quantidade de exemplos. O Java valida os campos e os IDs antes de devolver à tela.
8. A interface apresenta a sugestão. Somente clicar em **Aplicar categoria escolhida** faz o POST que grava a categoria. É possível escolher outra ou remover depois.

Ao aplicar, são atualizados categoria, descrição/direção de referência e versão do registro bancário. Isso permite usar a categoria nos relatórios e orçamentos sem criar outro lançamento ou descontar seu valor novamente. Concorrência e proprietário são conferidos no Java.

## Onde LangChain entra

LangChain é a biblioteca que organiza as etapas e a chamada ao modelo; ele não é o modelo nem a hospedagem.

Em `category-ai/finance_ai/pipeline.py`, a composição real é:

```python
RunnableLambda(retrieve, name="recuperar_exemplos") | RunnableLambda(decide, name="sugerir_categoria")
```

O operador `|` entrega a saída da primeira etapa à segunda. `RunnableLambda` transforma funções Python comuns em etapas da cadeia. Isso mantém cada decisão legível e testável; não há um agente livre escolhendo ferramentas ou executando SQL.

Em `finance_ai/cloud.py`, `ChatGroq` conecta LangChain à API da Groq, com o modelo configurado `openai/gpt-oss-20b`. `structured_classifier` usa `with_structured_output` e JSON Schema para restringir a saída a uma categoria fornecida ou `null`. Chaves semânticas, como `alimentacao`, representam as categorias no prompt; os UUIDs ficam fora do prompt do modelo.

O código também conserva um adaptador Ollama para testes locais. A publicação atual usa Groq; Ollama não é necessário para o serviço na nuvem.

## Que tipo de RAG existe

RAG significa recuperar contexto relevante antes de pedir uma resposta ao modelo. Aqui existe uma recuperação lexical simples: procurar descrições parecidas no histórico categorizado do próprio usuário e levar exemplos relevantes ao classificador.

Não há embeddings, índice vetorial, vector store, pgvector, busca semântica ou treinamento do modelo com seus dados. O Python não consulta o Neon: recebe o contexto previamente selecionado pelo Java.

A semelhança usa palavras em comum após normalização e remoção de palavras genéricas:

```text
similaridade = palavras em comum / palavras diferentes nos dois textos
```

É o índice de Jaccard. Exemplos com resultado de pelo menos `0,45` podem ser recuperados; até cinco são usados. Resultados a partir de `0,80` são tratados como fortes no Python. Histórico forte de uma única categoria pode resolver a sugestão; categorias conflitantes geram abstenção. A seleção Java procura preservar exemplos conflitantes ao limitar o contexto.

Essa escolha é simples, inspecionável e suficiente para começar com descrições curtas. A limitação é que palavras diferentes com o mesmo significado podem não ser reconhecidas. A consulta também considera apenas os 100 registros elegíveis mais recentes; não representa todo o histórico do usuário.

## Por que isso reduz chamadas

- Histórico exato consistente pode ser resolvido no próprio Java.
- Histórico semelhante e regras podem ser resolvidos no Python sem geração.
- Regras cobrem exemplos como mercado/padaria, transporte e farmácia, se existir uma categoria compatível.
- Somente os casos restantes elegíveis chegam ao LLM, com contexto limitado.
- Respostas do modelo ficam em cache de memória por até cinco minutos, até 128 contextos. Mudanças nas categorias ou exemplos alteram a chave do cache.

Recuperar exemplos não torna tokens gratuitos: os exemplos enviados ao LLM também consomem tokens. A economia vem de evitar chamadas quando possível e limitar o tamanho do contexto. O modelo não aprende permanentemente ao aplicar uma categoria: ela poderá aparecer como exemplo em uma consulta posterior do seu histórico.

## Exemplo fictício

Para `Mercado Alfa`, uma categoria Alimentação disponível pode ser sugerida pela regra, sem LLM. Para `Restaurante Sabor da Vila`, sem histórico suficiente e sem regra que resolva, o modelo pode escolher Alimentação entre as categorias permitidas.

Se o usuário escolher manualmente a categoria e uma descrição igual reaparecer, o Java poderá reutilizar esse histórico. Se houver escolhas conflitantes para a mesma descrição, ele não deve inventar certeza.

Os rótulos alta/moderada/baixa são heurísticas. Não são probabilidades calibradas de acerto. Uma sugestão do LLM recebe consistência baixa, mesmo quando parece plausível.

## Segurança e limites atuais

O contrato não envia valores, saldos, datas, IDs de conta/usuário, senhas ou tokens bancários. Descrições, nomes de categorias e exemplos são normalizados, removendo números, e-mails e URLs. Isso não garante anonimização: nomes de estabelecimentos ou pessoas podem permanecer e, quando o LLM for acionado, serão processados pela Groq.

O Python exige Bearer token, limita corpo a 64 KiB e duas requisições simultâneas por instância. A chamada à Groq tem timeout de seis segundos, sem retries automáticos. Em produção há limite de 12 tentativas por minuto por instância e pausa de 30 segundos após falha do provedor. Cache e limites são locais à instância, não cotas globais.

O Java limita a chamada ao serviço a dez segundos e também aplica pausa após falhas. Falha da IA deixa a escolha manual disponível. Logs usam IDs de correlação, status e duração; tracing externo de prompts fica desativado.

## Transferências não usam LLM

O Java procura operações do mesmo proprietário em contas diferentes, de direções opostas, mesmo valor e datas com até dois dias de diferença. A interface mostra candidatos; o usuário confirma ou desfaz a identificação.

Depois da confirmação, a view pode excluir o par dos totais de renda/gastos, sem mexer nos saldos. Se os dados bancários usados na identificação mudarem, o par deixa de ser considerado válido e precisa ser conferido. Uma descrição de PIX sozinha não comprova uma transferência própria.

## O que mudou nesta entrega e o que ainda não existe

Foram desenvolvidos o painel de assistência nos detalhes das movimentações, a escolha/remover categoria de registro bancário, a identificação/desfazer transferência própria, as estruturas da V9 e o serviço Python remoto. O serviço começou com suporte a LLM local e ganhou adaptador Groq, autenticação própria e configuração de produção. Histórico e regras continuam antes do modelo.

Não foram desenvolvidos um chatbot financeiro, planejamento de compras, metas de poupança com aconselhamento, recomendações de investimentos, embeddings ou um RAG de documentos. Essas são ideias futuras. Não devem ser consideradas prontas por existir LangChain.

## Arquivos para estudar

| Arquivo | Responsabilidade |
|---|---|
| `backend/.../banking/BankAssistanceService.java` | Consultas do contexto, elegibilidade e alterações manuais |
| `backend/.../banking/CategoryAdvisor.java` | Normalização, histórico exato, contexto mínimo e chamada HTTPS |
| `category-ai/finance_ai/pipeline.py` | Cadeia LangChain, recuperação, regras e saída estruturada |
| `category-ai/finance_ai/cloud.py` | Groq, cache, cotas por instância e backoff |
| `category-ai/finance_ai/app.py` | FastAPI, autenticação, limites e correlação |
| `frontend/src/features/movements/BankAssistance.tsx` | Sugestões e botões de confirmação |
| `backend/src/main/resources/db/migration/V9__bank_transfer_and_categories.sql` | Persistência de categorias/pares e views de relatórios |

Para concluir a publicação, siga [retomada em outro computador](contexto/retomada-outro-computador.md). Não aplicar a V9 antes de confirmar o backup correto.
