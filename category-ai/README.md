# Transferências e categorias — laboratório local

Esta etapa mantém Java como responsável pelos dados financeiros. Python/LangChain só propõe categorias. Nada é publicado, categorizado ou identificado automaticamente por uma resposta do modelo.

O laboratório também oferece [Groq na nuvem com cota gratuita](../docs/llm-nuvem.md), restrita a exemplos fictícios. O guia explica a chave protegida, os testes reais e o que falta hospedar para desligar o computador. Ollama permanece como alternativa local.

## 1. Identificar transferências no Java

`BankAssistanceService` procura registros do mesmo perfil, em contas vinculadas diferentes, com direções opostas, mesmo valor decimal, status efetivado e até dois dias de diferença. Uma descrição de PIX, isoladamente, não comprova transferência própria. Com apenas uma conta vinculada pode não haver par disponível.

A lista é uma sugestão. O usuário escolhe o outro registro e confirma. Os dois passam a ser `TRANSFER` na consulta `financial_record`; deixam de compor renda, gastos e orçamento. Continuam visíveis nas movimentações e os saldos bancários permanecem iguais. Não são criados movimentos ou postings. Há uma ação para desfazer, que restaura as categorias anteriores quando ainda compatíveis.

A migração V9 armazena os pares e os dados que fundamentaram a decisão. Se o banco mudar valor, data, direção, status ou descrição, a identificação deixa de excluir os registros dos totais. A interface avisa e permite desfazer/conferir. Ocultar/restaurar, por si só, não invalida um par.

## 2. Recuperar exemplos no Python

O Java consulta até 100 classificações **escolhidas pelo usuário**, do mesmo perfil, da mesma natureza e de categorias ativas. Registros ocultos, cancelados ou sem categoria não ensinam uma escolha. Não há treinamento dos parâmetros de um modelo.

Antes de enviar ao serviço local, o Java normaliza as descrições e remove números, e-mails e URLs. O contrato não contém saldo, valor, datas, conta, usuário ou credenciais do banco. Nomes eventualmente presentes numa descrição não são completamente anonimizados; por isso este protótipo só permite comunicação com `127.0.0.1`.

`retrieve()` compara palavras relevantes das descrições e recupera no máximo cinco exemplos. Para descrições curtas de estabelecimentos, essa busca lexical permite começar sem embeddings, banco vetorial ou chamadas extras. Isso é recuperação de contexto. **Só vira RAG com geração quando o modelo usa esses exemplos para sugerir uma resposta.**

Referência: [retrieval no LangChain](https://docs.langchain.com/oss/python/langchain/retrieval).

## 3. Montar a cadeia LangChain

Em `finance_ai/pipeline.py`, a composição é:

```python
RunnableLambda(retrieve, name="recuperar_exemplos") | RunnableLambda(decide, name="sugerir_categoria")
```

O primeiro passo recupera exemplos; o segundo decide como propor uma categoria:

1. Histórico consistente e muito semelhante: sugere a escolha anterior.
2. Regra clara de estabelecimento: sugere uma categoria existente, por exemplo supermercado → Alimentação.
3. Se ainda houver dúvida e um modelo local estiver configurado: envia no máximo 20 categorias permitidas, a descrição normalizada e até cinco exemplos.
4. Sem evidência, histórico conflitante, natureza financeira ambígua ou resposta inválida: não sugere uma categoria.

`RunnableLambda` permite compor funções normais de Python em uma cadeia inspecionável: [referência](https://reference.langchain.com/python/langchain-core/runnables/base/RunnableLambda).

## 4. Modelo local opcional

O adaptador usa `ChatOllama.with_structured_output(ModelChoice)`. A resposta só pode propor um ID de categoria permitido ou `null`. Não recebe ferramentas de banco, SQL ou de movimentação financeira. Descrições são tratadas como dados não confiáveis. Respostas de modelo recebem indicação de consistência baixa; confiança aqui é uma heurística, não uma probabilidade calibrada.

O laboratório local pode habilitar Qwen2.5:1.5b via Ollama portátil. Consulte o [passo a passo do LLM local](../docs/llm-local.md) para entender a configuração, iniciar o modelo e reproduzir a avaliação real com dados fictícios. Os pesos ficam em `.tools`, fora do Git e do deploy. Quando `CATEGORY_AI_MODEL` ficar vazio, a cadeia continua com histórico e regras. Nomes de modelo cloud/URLs são rejeitados. Tracing externo do LangSmith é desativado no processo.

Referência: [ChatOllama no LangChain](https://docs.langchain.com/oss/python/integrations/chat/ollama).

## 5. Aplicar uma escolha

O React exibe a origem da proposta e permite escolher outra categoria. Somente o botão **Aplicar categoria escolhida** grava a escolha. O Java valida novamente proprietário, versão, status, visibilidade e compatibilidade da categoria. É possível corrigir ou remover a categoria depois.

Os relatórios leem a categoria diretamente do registro bancário; não há importação duplicada nem desconto adicional. A escolha aceita passa a integrar o histórico para consultas futuras. Uma mudança de descrição/direção no banco invalida a associação da categoria até uma nova escolha.

## Executar os exemplos e testes

Comandos a partir da raiz do repositório, em PowerShell 7; Python 3.13 foi usado na validação:

```powershell
uv venv .tools/category-ai-venv --python 3.13
uv pip install --python .tools/category-ai-venv/Scripts/python.exe -r category-ai/requirements.lock
.tools/category-ai-venv/Scripts/python.exe -X utf8 -m pytest category-ai/tests -q
Push-Location category-ai
../.tools/category-ai-venv/Scripts/python.exe -X utf8 -m finance_ai.demo
../.tools/category-ai-venv/Scripts/python.exe -X utf8 -m finance_ai.demo_http
Pop-Location
```

O exemplo HTTP inicia uma API temporária em loopback com token aleatório em memória, envia três casos fictícios e encerra o servidor. Resultados esperados: `Mercado Alfa → RULE`, `Loja Exemplo → HISTORY`, `Pix desconhecido → NONE`. Não consulta banco de dados ou Nubank.

Para conectar ao backend **local**, configure no mesmo terminal antes de iniciar os processos:

```powershell
$env:CATEGORY_AI_TOKEN = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$env:CATEGORY_AI_ENABLED = 'true'
$env:CATEGORY_AI_URL = 'http://127.0.0.1:8123'
$financePython = Join-Path $PWD '.tools/category-ai-venv/Scripts/python.exe'
$financeAiProcess = Start-Process -WindowStyle Hidden -FilePath $financePython -ArgumentList '-m uvicorn finance_ai.app:app --app-dir category-ai --host 127.0.0.1 --port 8123 --no-access-log' -PassThru
# Inicie agora o backend local pelo fluxo habitual, para herdar as mesmas variáveis.
# Ao terminar o laboratório:
# Stop-Process -Id $financeAiProcess.Id
```

O endpoint exige token, limita corpo/contexto e aceita no máximo duas execuções simultâneas. Não possui conexão com banco de dados. Se estiver indisponível, o Java usa histórico exato ou se abstém; isso não bloqueia a listagem de movimentações.

## O que foi demonstrado e o que falta antes de produção

As regras Java e a API Python são executadas nos testes. A chamada HTTP da demonstração usa a cadeia LangChain real e dados fictícios. Os testes de contrato usam um substituto controlado; o avaliador `finance_ai.evaluate_local` chama um modelo real instalado localmente. Os cenários de navegador também usam dados fictícios.

O processo Python é local e opcional. Ainda não integra o Dockerfile/deploy de produção. Antes de habilitá-lo em produção, é preciso decidir a hospedagem e a comunicação privada entre Java e Python. O laboratório Groq permite chamadas externas somente com textos fictícios explicitamente permitidos; não libera histórico real. A configuração Java vem desativada por padrão. Nenhuma mudança em produção ou push ao GitHub faz parte desta etapa.

Limitações: pares com tarifa, moedas distintas ou datas mais distantes não são confirmados por esta regra. Pares ambíguos precisam de escolha. A busca de histórico está limitada às 100 escolhas mais recentes; textos genéricos podem não gerar sugestão. Os recursos de ocultar e de vincular lançamentos existentes continuam disponíveis.
