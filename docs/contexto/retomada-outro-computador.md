# Retomar em outro computador

Atualizado em 16/11/2026. Recuperação confirmada e V9 validada em branch isolada; leia [resultado e limites](../validacao-v9-neon.md). Produção continua V8 e o usuário pediu revisar o resultado antes de publicar. A alteração do schema já está escrita na V9; foi adiada sua aplicação em produção, não sua implementação.

Após essa validação, o experimento de categorias automáticas foi desenvolvido na branch local `codex/categorias-auto-local`, com V10. Essa nova branch ainda não foi enviada ao GitHub; o clone abaixo recupera a PR anterior, não a automação. Não considerar V10/automação testadas no Neon. Ver `handoff.md` e [explicação prática](../categorias-automaticas-local.md) antes de retomar.

**Atualização mais recente:** V9/V10/V11 e o JAR do Docker já passaram em uma nova cópia Neon; foi adicionada reutilização MEMORY para evitar LLM em repetições. A automação vetorial cloud ainda não está pronta, conforme [validação de produção](../validacao-producao-2026-11-16.md). A frase anterior sobre V10 não testada refere-se à primeira etapa local; foi superada pelo novo teste. A branch continua sem push.

## Leia primeiro

1. `AGENTS.md`, na raiz.
2. [Estado operacional](handoff.md).
3. [Explicação de LangChain e RAG](../langchain-rag-atual.md).
4. [Revisão de produção e rollback](../revisao-ia-producao.md).

## Recuperar o código

O trabalho local mais recente está na branch `codex/rag-vetorial-local`, com PR sobre `codex/ia-pronta-producao`, não na branch padrão do repositório. A base da PR não representa o Java publicado. Para uma cópia nova após o push:

```powershell
git clone --branch codex/rag-vetorial-local https://github.com/PedroH135-ui/java-finance.git
Set-Location java-finance
git status --short
```

Em uma cópia já existente, confira alterações locais antes de trocar de branch ou fazer pull. Não descartar arquivos ou usar reset para acompanhar estas instruções.

## O que não vem pelo Git

`.tools`, `.env`, tokens, dumps, ambientes virtuais e configurações locais das ferramentas são privados. Os scripts de publicação e evidências pontuais dentro de `.tools` também não acompanham o clone. Não presuma que existem no outro computador e não tente recuperá-los pelo repositório público.

Segredos `.clixml` usam proteção do Windows ligada ao usuário/computador; copiá-los não é um procedimento de migração de credenciais. Reautentique as CLIs pelos fluxos oficiais e configure credenciais localmente com entrada oculta quando necessário. Não colocar valores em comandos publicados ou no chat.

Instale JDK 21, Node 24, Python 3.13, Docker Desktop e PowerShell 7. Os scripts não instalam automaticamente todas essas ferramentas. Maven é obtido pelo wrapper; `.tools` com JDK/cache deste computador não existe no clone novo.

Para preparar testes Python e frontend, na raiz:

```powershell
python -m venv .tools/category-ai-venv
.tools/category-ai-venv/Scripts/python.exe -m pip install -r category-ai/requirements.lock
.tools/category-ai-venv/Scripts/python.exe -m pip install -r category-ai/requirements-dev.txt
npm --prefix frontend ci
```

Use Python 3.13 no comando inicial. Configure `JAVA_HOME` para o JDK 21 instalado e carregue `scripts/enter-dev.ps1` antes dos testes Java. Leia [ambiente Windows](../ambiente-windows.md) e [demonstração fictícia](../validacao-local.md) antes de subir a aplicação. Os testes unitários Python não precisam da chave Groq.

## Pendências para concluir a produção

| Pendência | Como confirmar a conclusão |
|---|---|
| Revalidar o projeto Neon correto | Identificado em 16/11 como `java-finance` / `twilight-field-61680463`; conferir correspondência novamente antes do backup, pois a configuração pode mudar |
| Revalidar recuperação recente | Ponto V8 confirmado em 16/11 e leitura exercitada por cópia filha; conferir estado/dados novos e preparar ponto recente antes do futuro deploy |
| Revalidar V9/compatibilidade | Flyway atualizado para 11.20.3 e migração isolada repetida sem aviso em 16/11; repetir em cópia nova se mudar dependências, SQL ou dados relevantes |
| Preparar o acesso Java → Python | Conferir hostname estável e mesmo token de serviço Production nos dois projetos; não reutilizar OIDC de Preview |
| Publicar Java revisado | Somente depois do backup; conferir Flyway V9 e saúde do deployment |
| Verificar os invariantes | Mesma quantidade de movimentos/postings e mesmos saldos antes/depois, em evidência privada; nenhuma categoria real aplicada no smoke |

A conexão já configurada na Vercel permite ao Java acessar o banco, mas não comprova recuperação nem dá à CLI Neon acesso ao proprietário. Prisma não remove essa necessidade. A migração continuará sendo aplicada pelo Flyway ao iniciar o Java; não criar outro schema concorrente nem executar o SQL manualmente para contornar o bloqueio.

**Não iniciar a versão nova do Java com as credenciais reais antes do backup.** Um deployment sem promover o domínio também pode iniciar o Flyway. Não executar o provisionamento antigo `scripts/cloud-provision.mjs` como atualização.

## Configuração futura da IA no Java

Os valores não secretos esperados são:

```text
CATEGORY_AI_ENABLED=true
CATEGORY_AI_URL=https://java-finance-category-ai-lab.vercel.app
CATEGORY_AI_ALLOWED_HOST=java-finance-category-ai-lab.vercel.app
CATEGORY_AI_TOKEN=<Secret igual ao token Production do Python>
```

`CATEGORY_AI_PREVIEW_OIDC` deve ficar ausente em Production. O Python já publicado precisa continuar em `CATEGORY_AI_MODE=production`, com Groq e token de serviço próprio, sem credenciais de banco/Pluggy.

O token Production não está no Git e não pode ser recuperado de variáveis Secret pela API Vercel. Se não houver uma cópia segura acessível, planeje rotação coordenada: novo token no Python e no Java, redeploy de ambos e smoke autenticado. Gerar outro token somente no Java quebraria a comunicação. A chave Groq deve ser obtida pelo fluxo oficial da conta; não usar chaves publicadas no chat ou no código como fonte de recuperação.

## Testes para repetir ao retomar

```powershell
. .\scripts\enter-dev.ps1
Push-Location backend
.\mvnw.cmd verify
Pop-Location
.tools/category-ai-venv/Scripts/python.exe -m pytest category-ai/tests -q
npm --prefix frontend run lint
npm --prefix frontend test
npm --prefix frontend run build
node scripts/check-public-files.mjs
```

Java/Testcontainers exige Docker e usa PostgreSQL descartável. Não apontar testes ao banco real. Na validação anterior passaram 85 testes Java, 35 Python, três frontend, três do provisionamento e seis cenários de navegador; os resultados históricos não substituem testar mudanças novas.

Depois de recuperar acesso e antes da publicação, conferir categoria manual, sugestão sem gravação, aplicar/remover categoria em ambiente isolado, confirmar/desfazer transferência, relatórios e saldos. Repetir a varredura de segredos e conferir os arquivos do commit antes de push. A branch padrão e os deployments não são atualizados automaticamente por estes documentos.

## Se for preciso interromper a IA

Desabilitar `CATEGORY_AI_ENABLED` no Java e redeployar a versão revisada mantém a seleção manual. Preservar V9 e as escolhas feitas pelo usuário. Não apagar tabelas, desfazer registros financeiros nem restaurar um backup sobre operações novas por rotina.
