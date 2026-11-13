# LLM na nuvem: laboratório Groq + LangChain

## Escolha e custo

Provedor: **GroqCloud**, plano **Free**. Modelo: **`openai/gpt-oss-20b`**. Integração Python: **`langchain-groq==1.1.3`**, com o SDK **`groq==0.37.1`**. Nenhuma dependência existente foi atualizada.

A escolha permite testar sem manter um modelo carregado no computador. O modelo suporta JSON Schema em modo estrito e tem integração oficial no LangChain. A cota gratuita é limitada; os limites exatos da conta aparecem no painel da Groq. Gratuidade não significa capacidade ilimitada ou garantia permanente de disponibilidade. Esta implementação não ativa cobrança, faz upgrade ou cria plano pago. Use uma chave de conta no Free.

Fontes verificadas em 13/11/2026: [plano Free e upgrade](https://console.groq.com/docs/billing-faqs), [limites de uso](https://console.groq.com/docs/rate-limits), [saída estruturada e modelos](https://console.groq.com/docs/structured-outputs), [ChatGroq](https://docs.langchain.com/oss/python/integrations/chat/groq).

## O que mudou em cada etapa

1. **Mantivemos o fluxo financeiro em Java.** Contas, lançamentos, saldos, validação de proprietário e gravação de categorias continuam no Java. O Python não acessa o banco.
2. **Mantivemos histórico e regras antes do LLM.** A cadeia LangChain recupera até cinco exemplos relevantes. Histórico consistente e regras claras evitam chamadas externas.
3. **Trocamos o adaptador de inferência.** `ChatGroq` chama a API HTTPS oficial; `ChatOllama` continua disponível como alternativa local. Ambos reutilizam `structured_classifier`, que converte IDs internos em chaves semânticas e valida a resposta.
4. **Restringimos o teste aos dados fictícios.** Em `finance_ai/cloud.py`, somente os quatro textos de teste explícitos, as seis categorias da demonstração e o exemplo fictício de cantina podem ser enviados. Outros textos, categorias personalizadas ou exemplos são recusados antes da chamada externa. Esse bloqueio não deve ser removido para uso real sem nova revisão.
5. **Identificamos a origem na tela.** O serviço retorna `provider=groq`; Java valida esse metadado e React mostra “Modelo na nuvem”. O LLM não escolhe esse metadado e não tem ferramentas para executar ações financeiras.
6. **Preservamos a decisão manual.** Abrir os detalhes apenas sugere. O usuário pode escolher outra categoria ou aplicar a proposta. Não há confirmação, importação ou mudança de saldo automática.

O RAG continua sendo a recuperação de exemplos seguida da geração com esses exemplos. Não treinamos pesos e não criamos banco vetorial. Para os textos curtos desta etapa, a busca lexical existente é suficiente para começar e reduz complexidade.

## Credencial e privacidade

A chave é solicitada por `Read-Host -AsSecureString` e salva usando DPAPI do Windows em `.tools/local-review/groq-key.clixml`. O arquivo é ignorado por Git e deploy. É vinculado ao usuário e ao computador atuais; não é um segredo portátil para produção.

O launcher descriptografa a chave em memória somente para iniciar o processo Python. Ela não vai para o frontend, argumentos de linha de comando, Java, banco ou Ollama. O script de avaliação usa a mesma proteção e remove a variável do processo ao terminar. Não há chaves em código, documentação ou relatórios.

DPAPI protege o arquivo em repouso; processos autorizados do mesmo usuário podem acessá-lo. Uma chave compartilhada pelo chat deve ser substituída antes de usar em produção; para substituir localmente, execute novamente o configurador e reinicie o Python com `-RestartAi`.

A requisição contém apenas descrição fictícia, nomes das categorias e os exemplos fictícios recuperados, sem valores, saldos, datas, contas, nomes de usuários ou credenciais bancárias. As descrições não são totalmente anonimizadas por uma simples normalização; por isso o laboratório usa uma lista explícita de textos fictícios.

A Groq informa que não retém conteúdo de inferência por padrão, mas há exceções para confiabilidade/abuso, com possibilidade de retenção temporária, e metadados de uso são mantidos. Não assumimos retenção zero da conta. [Política de dados e controles](https://console.groq.com/docs/your-data).

Tracing externo LangSmith permanece desativado. O cliente usa endpoint fixo `https://api.groq.com`, sem proxies ou redirecionamentos automáticos. Erros registram somente status HTTP permitido, nunca chave, prompt ou resposta bruta.

## Limites e comportamento de falha

- No máximo 24 tentativas externas por processo Python, incluindo falhas. Não há reinício automático para renovar esse orçamento.
- Zero retries automáticos, timeout de seis segundos e até 256 tokens de saída, com esforço de raciocínio baixo.
- No máximo 20 categorias e cinco exemplos no contexto.
- JSON Schema estrito limita a resposta a uma categoria permitida ou `null`; isso valida o formato, não garante a classificação correta.
- Erros, timeout, autenticação inválida ou limite 429 resultam em ausência de sugestão, preservando histórico/regras e o uso normal do sistema.
- A revisão da categoria continua manual e a consistência atribuída à resposta LLM continua baixa, sem interpretá-la como probabilidade calibrada.

O controle por processo é um limite de laboratório, não substitui cotas e controle de custo no provedor. Nenhum plano pago ou mecanismo de cobrança foi configurado nesta etapa.

## Executar neste computador

Com o [ambiente fictício](validacao-local.md) preparado:

```powershell
# Já instalado nesta estação; em outra estação, instale as dependências fixadas:
uv pip install --python .tools/category-ai-venv/Scripts/python.exe -r category-ai/requirements.lock

# Uma vez, ou para substituir a chave. Entrada oculta, sem passar segredo como argumento:
pwsh -NoProfile -File scripts/configure-review-groq.ps1

# Avaliação real: apenas dados fictícios, sem banco ou Nubank.
pwsh -NoProfile -File scripts/test-review-groq.ps1

# Habilitar Groq na versão local e atualizar Java/React:
pwsh -NoProfile -File scripts/start-review-local.ps1 -Llm groq -Build
```

O modo escolhido fica no arquivo privado de configuração. Para abrir novamente, basta `pwsh -NoProfile -File scripts/start-review-local.ps1`. Para recarregar Python ou substituir a chave, use `-RestartAi`.

Para voltar a histórico/regras, sem apagar dados:

```powershell
pwsh -NoProfile -File scripts/start-review-local.ps1 -Llm off
```

Ollama continua sendo uma alternativa: `-Llm qwen2.5:1.5b`. Ao trocar de Ollama para Groq, os pesos locais são descarregados da memória.

## Resultados da API real — 13/11/2026

Oito casos corresponderam ao esperado. Quatro não chamaram a API: Mercado Alfa por regra, Loja Exemplo por histórico, PIX desconhecido e transferência própria sem sugestão. Os quatro casos que chamaram a API foram:

| Descrição fictícia | Categoria | Contexto recuperado | Duração observada |
|---|---|---|---|
| Restaurante Sabor da Vila | Alimentação | Nenhum exemplo | 1.209 ms |
| Clínica odontológica Sorriso | Saúde | Nenhum exemplo | 964 ms |
| Posto de gasolina Avenida | Transporte | Nenhum exemplo | 1.854 ms |
| Cantina Bela almoço | Alimentação | Cantina Bela jantar → Alimentação | 378 ms |

É uma amostra pequena e já usada no desenvolvimento. O resultado não comprova precisão geral nem latência de produção. Uma próxima avaliação deve usar casos novos e avaliar abstenção, categorias conflitantes e diversidade de estabelecimentos.

O relatório de execução fica em `.tools/local-review/groq-evaluation.json`, somente com dados fictícios e durações. Testes automatizados de segurança usam um cliente substituto, sem consumir a API.

Também passaram 22 testes Python, cinco testes Java do contrato de assistência, cinco cenários de navegador (incluindo origem na nuvem, escolha manual e transferências), lint e build integrado. No fluxo real React → Java → Python → Groq, o restaurante mostrou Alimentação em cerca de 2,3 segundos. Abrir os detalhes não gravou a categoria; aplicar e remover pelo fluxo manual preservou o saldo de R$ 3.705, o resultado de R$ 2.505 e a quantidade de movimentos. O registro foi deixado sem categoria para exploração manual.

## O computador ainda precisa ficar ligado?

**O modelo agora é executado na nuvem.** Ollama e os pesos locais não são necessários para o modo Groq.

Nesta validação, Java, React, PostgreSQL e Python ainda executam no computador. A versão de teste em `127.0.0.1:8082` continua dependendo dele ligado. Para o sistema completo funcionar com o computador desligado, precisaremos hospedar o Python e conectar o backend de produção a ele com autenticação e transporte adequados. O Java hoje só aceita loopback para esse serviço, portanto essa etapa exige implementação e testes próprios.

Nenhum deploy, push, configuração de produção, dado financeiro real ou consulta bancária fez parte desta etapa. A chave DPAPI não pode ser copiada como solução de segredo de produção; a hospedagem escolhida deverá receber um segredo próprio.
