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

O adaptador usa a política `candidate` para testar o mesmo cache/limites por instância utilizados em produção. O programa aceita somente os oito casos fixados em `finance_ai/explain_demo.py`; não recebe dados financeiros reais como entrada.

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
