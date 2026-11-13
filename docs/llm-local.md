# Sugestões com LLM gratuito e LangChain em Python

## 1. Escolher uma execução local

O laboratório usa **Qwen2.5:1.5b**, executado por **Ollama v0.40.16**. Não usa uma API paga ou uma cota gratuita de provedor externo. O computador paga o custo em processamento, memória, energia e download. O modelo é pequeno para permitir experimentar na estação atual (i5, 16 GB de RAM, sem GPU dedicada); sua qualidade precisa ser avaliada.

O pacote portátil oficial tem cerca de 1,6 GB; o modelo quantizado, cerca de 986 MB. O instalador do projeto verifica SHA-256 e extrai apenas executável, bibliotecas CPU e licenças, poupando as bibliotecas de GPU. Binários, pesos e logs ficam ignorados em `.tools/ollama-local`. Não há instalação global nem alteração do PATH. Os arquivos existentes do projeto e o banco de produção não são usados pelo modelo.

Fontes: [modelo e licença](https://ollama.com/library/qwen2.5:1.5b), [Ollama portátil no Windows](https://docs.ollama.com/windows), [configuração local e privacidade](https://docs.ollama.com/faq).

## 2. Separar responsabilidades

O fluxo é:

1. React abre os detalhes de um registro e pede assistência ao Java.
2. Java verifica o perfil, busca categorias existentes e até 100 decisões anteriores do próprio perfil.
3. Java remove números, URLs e e-mails das descrições e envia somente descrições, categorias e exemplos para o Python em `127.0.0.1:8123`.
4. Python usa LangChain para recuperar contexto e decidir se precisa do LLM.
5. A sugestão volta ao React. Somente **Aplicar categoria escolhida** pede ao Java para gravar a categoria.

O contrato não envia valores, saldos, contas, datas, senhas ou tokens bancários ao modelo. Remover identificadores numéricos não anonimiza completamente um texto; esta etapa usa somente dados fictícios e execução local.

## 3. Entender a cadeia LangChain e o RAG

Em `category-ai/finance_ai/pipeline.py`:

```python
RunnableLambda(retrieve) | RunnableLambda(decide)
```

`RunnableLambda` transforma uma função Python em um passo de uma cadeia. O operador `|` passa o resultado do primeiro passo para o segundo.

`retrieve` compara as palavras da descrição com exemplos que o usuário já classificou. Recupera no máximo cinco exemplos relevantes. Ainda não precisamos de embeddings ou banco vetorial: descrições curtas permitem começar com uma busca lexical simples, sem outra chamada a modelo.

`decide` prioriza histórico muito semelhante e consistente; depois regras claras (como farmácia → Saúde). Se isso resolver, o LLM não é chamado. Transferências, estornos e outros termos financeiros ambíguos ficam sem categoria sugerida. Descrições genéricas, como PIX desconhecido, também se abstêm na ausência de histórico.

Nos casos restantes, o modelo recebe até 20 categorias, a descrição normalizada e os exemplos recuperados. **Recuperar esses exemplos e usá-los na geração é RAG.** Não estamos treinando ou alterando os pesos do modelo.

Exemplo: “Cantina Bela almoço” recupera “Cantina Bela jantar → Alimentação”. O modelo usa esse contexto para propor Alimentação, mesmo sem igualdade literal das descrições.

## 4. Controlar a resposta do modelo

`ChatOllama` integra Ollama ao LangChain. Usamos `with_structured_output(..., method="json_schema")` para pedir um objeto com uma categoria permitida ou `null`.

Os UUIDs reais são convertidos em chaves semânticas curtas, como `alimentacao` e `transporte`, antes da chamada. Isso reduz tokens e facilita a escolha pelo modelo pequeno. As chaves voltam aos IDs reais somente no Python; o Java valida novamente o ID e a compatibilidade da categoria.

Temperatura zero reduz variação, mas não garante acerto. Contexto de 2.048 tokens, saída de até 64 tokens e quatro threads limitam recursos. O modelo usa CPU e fica aquecido por até 30 minutos para evitar carregar os pesos a cada clique. Há prazo de oito segundos no cliente Python; o Java limita a assistência a dez segundos e mantém o fallback de histórico se o serviço falhar.

Uma resposta válida em JSON pode estar semanticamente errada. Por isso sugestões do LLM são apresentadas como de consistência baixa e continuam editáveis. O modelo não tem ferramentas, SQL ou acesso ao banco; não importa lançamentos nem altera saldos. LangSmith tracing e recursos cloud do Ollama ficam desativados.

Fontes: [ChatOllama](https://docs.langchain.com/oss/python/integrations/chat/ollama), [saída estruturada](https://reference.langchain.com/python/langchain-ollama/chat_models/ChatOllama/with_structured_output).

## 5. Iniciar e desligar

Na raiz do projeto, usando PowerShell 7, após preparar o [ambiente fictício](validacao-local.md):

```powershell
# Uma vez: pacote oficial com versão e checksum fixados.
pwsh -NoProfile -File scripts/install-review-ollama.ps1
# Uma vez: baixa os pesos e aquece o modelo local.
pwsh -NoProfile -File scripts/start-review-ollama.ps1 -Pull
# Habilita o LLM e reinicia somente o serviço Python de categorias.
pwsh -NoProfile -File scripts/start-review-local.ps1 -Llm qwen2.5:1.5b
```

A escolha fica no arquivo privado `.tools/local-review/config.json`. Depois basta executar `start-review-local.ps1`; ele inicia os serviços locais e aquece o modelo. Nenhum arquivo de ambiente de produção é carregado.

Para voltar a histórico e regras, sem apagar dados:

```powershell
pwsh -NoProfile -File scripts/start-review-local.ps1 -Llm off
```

Esse comando também descarrega o modelo da memória. O servidor Ollama local permanece disponível, sem pesos carregados; reativar não exige outro download.

Para recarregar código Python durante desenvolvimento, use `-RestartAi`. Não é necessário recompilar o Java ou React para alterar apenas o Python. `-Build` continua necessário quando alterar Java ou React.

## 6. Reproduzir os testes

```powershell
.tools/category-ai-venv/Scripts/python.exe -X utf8 -m pytest category-ai/tests -q
$env:PYTHONPATH = Join-Path $PWD 'category-ai'
.tools/category-ai-venv/Scripts/python.exe -X utf8 -m finance_ai.evaluate_local --model qwen2.5:1.5b
```

O avaliador chama o modelo real com oito casos fictícios e exibe categoria esperada, observada, origem da sugestão, uso de exemplos e duração. Não acessa banco ou Nubank. O retorno não zero indica que ao menos um caso não correspondeu ao esperado. Uma amostra pequena mede regressão neste laboratório, não comprova precisão geral em movimentações reais.

Para acrescentar um exemplo visível no sistema local:

```powershell
.tools/category-ai-venv/Scripts/python.exe -X utf8 scripts/seed-review-local.py --llm-examples
```

Esse comando só aceita o container local rotulado e o perfil fictício. Acrescenta um restaurante de R$ 35 e um estorno de R$ 35, sem sobrescrever registros existentes ou mudar os snapshots de saldo. Os totais iniciais passam a R$ 3.535 de entradas e R$ 1.030 de saídas; resultado de R$ 2.505 e saldo de R$ 3.705 permanecem iguais. O estorno fica sem sugestão para conferir sua natureza financeira manualmente.

Em http://127.0.0.1:8082, entre com o perfil de demonstração, selecione novembro de 2026 e abra **Restaurante Sabor da Vila** em Movimentações. Confira a origem “Modelo local”, a proposta e a possibilidade de escolher outra categoria. A aplicação não grava a sugestão ao abrir os detalhes.

### Resultados observados em 13/11/2026

Os 18 testes Python passaram. Na avaliação com o modelo real, sete dos oito casos corresponderam ao esperado: quatro resolvidos sem LLM e três pelo LLM. No quarto caso do LLM, o posto de gasolina, ele se absteve em vez de propor Transporte. Portanto, não atingimos 100% da amostra e não afirmamos precisão geral.

| Caso fictício | Resultado final | Origem | Duração observada |
|---|---|---|---|
| Mercado Alfa | Alimentação | Regra | 10 ms |
| Loja Exemplo com histórico igual | Alimentação | Histórico | 1 ms |
| Restaurante Sabor da Vila | Alimentação | LLM | 2.259 ms |
| Clínica odontológica Sorriso | Saúde | LLM | 2.713 ms |
| Posto de gasolina Avenida | Sem sugestão; esperado Transporte | LLM se absteve | 2.381 ms |
| Cantina Bela almoço com exemplo semelhante | Alimentação | LLM com exemplo recuperado | 2.614 ms |
| PIX desconhecido | Sem sugestão | Proteção determinística | 1 ms |
| Transferência entre minhas contas | Sem sugestão | Proteção determinística | 1 ms |

A primeira rodada com chaves opacas `c0`, `c1` errou duas categorias. Substituí-las por nomes semânticos corrigiu o restaurante e fez o posto se abster. Isso é um ajuste informado pela amostra de desenvolvimento; uma avaliação futura deve incluir casos novos, sem ajustar o prompt a cada erro.

No Chrome, usando React → Java → Python → Ollama reais, o restaurante exibiu Alimentação com selo “Modelo local” em cerca de 3,3 segundos. Abrir os detalhes não gravou a categoria. Aplicar pelo botão mudou o gasto de Alimentação no orçamento de R$ 250 para R$ 285, sem mudar os R$ 3.705 de saldo, os R$ 2.505 de resultado ou criar movimentos. A categoria foi removida depois, deixando o registro pronto para teste manual. Desligar o LLM, descarregar os pesos e reativar também foi validado.

O digest do modelo avaliado é `65ec06548149b04c096a120e4a6da9d4017ea809c91734ea5631e89f96ddc57b`; o launcher recusa um modelo com conteúdo diferente. As durações variam com a carga do computador e o aquecimento do modelo.

## 7. Antes de produção

Esta configuração funciona no computador local. Publicar Java/React na Vercel não disponibiliza automaticamente o Ollama deste computador. Python, modelo e comunicação privada precisarão de uma decisão de hospedagem e de recursos antes de qualquer habilitação em produção.

O modo de produção continua sem LLM habilitado por padrão. Não houve publicação, push, migração de produção ou consulta bancária nesta etapa. Precisão com uma amostra maior, tempo de resposta sob concorrência e infraestrutura continuam sendo critérios de uma futura revisão.
