# Categorias padrão — validação local em 16/11/2026

Branch `codex/categorias-padrao`, baseada no merge `647dbc4` da PR #15. A PR #14 foi fechada como substituída. Esta mudança é local: sem push, PR, migração ou deploy. Produção continua na versão anterior; não adicionar categorias reais nem ativar usuários por este documento.

## Comportamento

No cadastro permitido pela configuração existente, `UserService.register` chama `CategoryService.addDefaults` na mesma transação: usuário e categorias são criados juntos. O cadastro de produção continua desabilitado; este código não altera essa configuração.

Em Categorias, o botão **Adicionar categorias padrão** usa `POST /api/categories/defaults`, com sessão e CSRF existentes. O proprietário vem da sessão; o cliente não escolhe outro usuário. O botão funciona tanto no estado vazio quanto com categorias existentes e informa quantas adicionou ou que todas já estão presentes. Falhas permitem nova tentativa.

São dez despesas: Mercado, Restaurantes e delivery, Moradia, Transporte, Saúde, Educação, Assinaturas, Lazer, Compras e Outros gastos. São quatro receitas: Salário, Trabalho extra, Rendimentos e Outras receitas. Todas começam ativas e sem marcação essencial, que o usuário pode escolher depois.

## Preservação e duplicação

O preenchimento considera nome normalizado e natureza: ignora maiúsculas, acentos e espaços extras. Categorias arquivadas também contam como existentes: não são reativadas nem substituídas. Nome, ID, versão, natureza e marcação essencial existentes permanecem intactos. Sinônimos diferentes, como Delivery e Restaurantes e delivery, não são mesclados automaticamente.

Um lock transacional na linha do proprietário serializa chamadas simultâneas ao preenchimento. O índice único existente e `ON CONFLICT DO NOTHING` protegem também a colisão exata com inserções concorrentes. Não é preciso alterar o schema V13. Uma chamada posterior pode adicionar novamente um padrão cujo nome foi renomeado; renomear não mantém uma identidade oculta de template. Isso não modifica a categoria renomeada.

Adicionar categorias não chama o Python/LLM, não habilita preferências, não dispara jobs, não aplica categorias a registros existentes e não cria movimentos/postings ou altera saldos. Se a IA já estiver ativada pelo próprio usuário, poderá usar as categorias novas em processamentos futuros; correções humanas continuam prioritárias.

## Validação

- Java: 40 testes unitários + 77 integrações, todos aprovados, usando PostgreSQL descartável local; quatro integrações novas cobrem cadastro, duplicação normalizada/arquivadas, concorrência e sessão/CSRF/isolamento. Preservação de saldos e contagens financeiras verificada.
- Fixtures antigas de categorização passaram a limpar apenas o catálogo fictício antes de construir seus candidatos controlados. Teste de orçamento identifica a categoria pelo ID, sem pressupor catálogo vazio ou primeira posição.
- Frontend: lint, três testes unitários e build aprovados. Continua o aviso anterior de bundle maior que 500 kB.
- Três cenários de navegador com API fictícia local: vazio, existente/arquivada e falha/repetição. Conexões externas bloqueadas.
- E2E adicional com Java/PostgreSQL reais locais: perfil `demo@javafinance.local`, botão repetido, categorias anteriores preservadas, relatório, contas e preferência da IA comparados antes/depois. Evidências ficam em `.tools`, fora do Git.
- Segurança: Gitleaks nas fontes públicas sem achados; doze segredos protegidos comparados somente em memória com 432 arquivos/recursos públicos e do JAR local atual, zero correspondências. Checagem de arquivos privados versionados aprovada. Evidências e segredos continuam fora do Git.

## Conferir e reverter

Abra `http://127.0.0.1:8082/#/categories` no ambiente fictício. O LLM clássico local está desligado para esta validação. Não confundir o endereço local com produção. Para repetir, use `scripts/start-review-local.ps1 -Build -Llm off`; testes de cadastro habilitam registro apenas no contexto de teste descartável.

Rollback de código não exige rollback de banco: não há nova migração. Voltar às fontes anteriores remove o preenchimento e o botão; categorias já criadas permanecem como categorias normais, preservando referências. Não apagar categorias em massa. Arquivar uma categoria pela interface é a opção reversível, caso o usuário queira deixá-la de usar. Publicação, envio e merge exigem o escopo autorizado na conversa atual.
