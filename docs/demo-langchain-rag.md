# Demonstração local de LangChain e RAG

Executa a cadeia Python real com um corpus fictício fixado no código. Não inicia Java, consulta banco ou acessa Vercel/Neon/Nubank. Não grava categorias ou saldos. Este teste não substitui a validação de ponta a ponta do Java/React.

## Executar

Prepare o ambiente Python conforme [retomada em outro computador](contexto/retomada-outro-computador.md). Na raiz, para testar sem nenhuma chamada externa:

```powershell
pwsh -NoProfile -File scripts/demo-rag-local.ps1
```

Para testar também o modelo real da Groq com os mesmos textos fictícios:

```powershell
# Somente se ainda não houver a chave protegida neste computador:
pwsh -NoProfile -File scripts/configure-review-groq.ps1
pwsh -NoProfile -File scripts/demo-rag-local.ps1 -UseGroq
```

A chave vem de `.tools/local-review/groq-key.clixml`, protegida pelo Windows, e passa somente em memória ao processo. O script restaura as variáveis de ambiente ao terminar. Não usa o token de serviço de produção nem modifica o ambiente publicado. A execução com `-UseGroq` consome a cota da Groq e envia apenas os textos fictícios fixados no código.

`index.html` e `result.json` ficam em `.tools/local-review/rag-demo-none` ou `rag-demo-groq`, fora do Git. Abra o HTML diretamente no navegador; expandir detalhes não faz chamadas. Não é necessário iniciar servidor para ler o relatório.

## O que o relatório mostra

Cada caso mostra histórico disponível, exemplos recuperados e descartados, pontuação, etapas LangChain, contexto entregue ao adaptador, contexto realmente enviado em HTTP e resultado. Um callback local registra `recuperar_exemplos` e `sugerir_categoria`.

Um observador temporário de `httpx.Client.send`, apenas neste processo, conta as requisições reais ao endpoint de classificação Groq e registra contexto fictício/status. Não registra cabeçalhos ou chave. A observação termina junto com o teste; não altera o servidor publicado.

O adaptador usa a política `candidate` para testar o mesmo cache/limites por instância utilizados em produção. O programa aceita somente os casos fixados em `finance_ai/explain_demo.py`: oito na suíte `classic` e 14 na suíte `nubank`; não recebe dados financeiros reais como entrada.

`modelUsed=true` não comprova nova chamada de rede: o adaptador pode responder pelo cache ou falhar. Confira o contador HTTP e o resultado. A repetição da cantina demonstra isso.

## Resultado observado em 13/11/2026

| Caso fictício | Recuperação | Decisão | HTTP Groq |
|---|---|---|---:|
| Mercado Alfa | Nenhum exemplo relevante | Regra → Alimentação | 0 |
| Loja Exemplo | Três exemplos iguais → Alimentação, pontuação 1,00 | Histórico → Alimentação | 0 |
| Cantina Bela almoço | Cantina Bela jantar → Alimentação, pontuação 0,50 | LLM → Alimentação | 1 |
| Mesma cantina, repetida | Mesmo exemplo | Cache → Alimentação | 0 |
| Restaurante Sabor da Vila | Nenhum exemplo relevante | LLM → Alimentação | 1 |
| Loja Exemplo com histórico conflitante | Alimentação e Transporte, ambos 1,00 | Nenhuma sugestão | 0 |
| PIX desconhecido | Nenhum | Nenhuma sugestão | 0 |
| Transferência entre minhas contas | Nenhum | Conferir natureza financeira | 0 |

Oito cenários aprovados, duas chamadas HTTP reais. Cantina: 5.836 ms na primeira execução e 1 ms na repetição em cache; restaurante: 329 ms. São observações, não promessa de desempenho ou resposta idêntica em execuções futuras. O modo sem provedor também passou com zero chamadas: os casos que dependeriam do modelo ficaram sem sugestão. Os 35 testes Python existentes também passaram.

Para a cantina, as palavras comuns são `{cantina, bela}` e a união é `{cantina, bela, almoco, jantar}`: `2/4 = 0,50`. A pontuação mede semelhança lexical, não probabilidade de acerto. Farmácia Horizonte e Taxi Lago foram descartados por não atingirem `0,45`.

O contexto real dessa chamada continha `cantina bela almoco`, as categorias Alimentação/Saúde/Transporte como chaves semânticas e o exemplo `cantina bela jantar → alimentacao`. Não continha valores, saldos, contas ou dados reais.

## Limites

Sinônimos ou descrições muito diferentes podem não recuperar exemplos úteis. Embeddings e busca vetorial continuam fora da implementação. O teste valida a cadeia Python isolada com um corpus pequeno; não demonstra acurácia geral sobre transações reais.

## Mercado, delivery, assinaturas e PIX fictícios

Para executar os cenários inspirados em movimentações comuns, sem copiar extrato ou presumir o formato exato do Nubank:

```powershell
pwsh -NoProfile -File scripts/demo-rag-local.ps1 -Suite nubank
pwsh -NoProfile -File scripts/demo-rag-local.ps1 -Suite nubank -UseGroq
```

Os relatórios ficam no subdiretório `nubank` de cada relatório do provedor. A lista de categorias é fictícia e inclui Alimentação, Delivery, Assinaturas, Transporte, Saúde e Outros gastos. Delivery só existe como opção desse corpus; nada foi criado no cadastro de categorias do usuário.

Resultado observado em 13/11/2026: 14 cenários aprovados, três requisições HTTP à Groq. O classificador, suas regras e a configuração de produção não foram alterados para obter esses resultados.

| Descrição fictícia | Exemplo recuperado | Origem da sugestão | Categoria | HTTP Groq |
|---|---|---|---|---:|
| COMPRA CARTAO SUPERMERCADO PRACA 1234 | Nenhum | Regra | Alimentação | 0 |
| SUPERMERCADO PRACA LOJA NORTE | SUPERMERCADO PRACA LOJA CENTRO, 0,60 | Regra ainda suficiente | Alimentação | 0 |
| IFOOD LANCHONETE VILA | IFOOD LANCHONETE CENTRO, 0,50 | RAG + LLM | Delivery | 1 |
| Mesmo iFood, repetido | Mesmo exemplo | Cache | Delivery | 0 |
| IFOOD PIZZARIA ALAMEDA | Três classificações iguais, 1,00 | Histórico | Delivery | 0 |
| NETFLIX.COM 123456 | Nenhum | Regra | Assinaturas | 0 |
| SPOTIFY P123456 | Nenhum | Regra | Assinaturas | 0 |
| STREAMPLAY PLANO MENSAL | STREAMPLAY PLANO ANUAL, 0,50 | RAG + LLM | Assinaturas | 1 |
| PIX ENVIADO MERCADO PRACA | Nenhum | Regra do estabelecimento | Alimentação | 0 |
| PIX ENVIADO MARMITARIA SOL | Três classificações iguais, 1,00 | Histórico | Alimentação | 0 |
| PIX ENVIADO JOAO SILVA | Nenhum | LLM consultado e se absteve | Nenhuma | 1 |
| PIX DESCONHECIDO | Nenhum | Informação insuficiente | Nenhuma | 0 |
| PIX TRANSFERENCIA ENTRE MINHAS CONTAS | Nenhum | Natureza financeira ambígua | Nenhuma | 0 |
| iFood com histórico Alimentação/Delivery conflitante | Duas categorias para descrição igual | Abstenção por conflito | Nenhuma | 0 |

O iFood novo levou 767 ms e a repetição em cache 3 ms nesta execução. A assinatura fictícia levou 284 ms e o PIX só com nome 367 ms. Tempos e respostas são observações, não garantia para casos futuros.

O PIX só com pessoa revela um limite: não há hoje um filtro determinístico específico para distinguir nome de pessoa antes do modelo. O código consultou o LLM, que retornou `null` neste caso. Isso não garante que todo PIX pessoal será reconhecido corretamente. Uma possível melhoria é avaliar esse filtro com exemplos autorizados; não foi implementada neste teste.

PIX é um meio de pagamento, não uma categoria financeira. Um estabelecimento conhecido pode sugerir uma categoria; um nome de pessoa sozinho não permite saber se foi compra, presente, reembolso ou transferência. Nenhum desses exemplos confirma transferência própria automaticamente.
