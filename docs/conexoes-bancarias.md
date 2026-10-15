# Conexões bancárias — Meu Pluggy pessoal

## Estado desta entrega

Módulo implementado e validado com dados simulados. Registros de instâncias e homologação permanecem privados.

A visão geral oferece o atalho **Vincular meu banco**, que abre diretamente o formulário em `/#/banking?new=1`.
Se a configuração do servidor estiver pendente, mostra as orientações sem abrir o formulário nem consultar contas do provedor.
O atalho não cria vínculos ou importa lançamentos automaticamente. Dois testes isolados de interface cobrem os dois estados,
incluindo o fechamento do formulário e o layout de 390 px, sem acessar o banco de produção.

## Caminho pessoal gratuito

Conforme documentação consultada em 14/10/2026, o caminho pessoal permite até 5 conexões, sem uso comercial, com dados reais
e sem expirar junto ao trial comercial. Os bancos são autorizados primeiro no Meu Pluggy; no Dashboard escolhe-se **MeuPluggy**
para acessar essas conexões. Não selecionar diretamente instituição bancária nem contratar plano de produção comercial.
As condições podem mudar. A gratuidade não se estende automaticamente a oferecer integrações para clientes de um produto.

O provedor atualiza as conexões diariamente. Nosso botão consulta a cópia já disponível, não dispara atualização no banco.
Não usamos categorização/enriquecimento pagos, pagamentos, Pix, cartões, investimentos, identidade/CPF ou criação de conexões via API.

Referências oficiais:
- [Guia pessoal](https://meu.pluggy.ai/api-guide)
- [Limites de uso pessoal](https://docs.pluggy.ai/en/docs/guides/meu-pluggy-personal-use)
- [Dashboard: conector e itemId](https://docs.pluggy.ai/en/docs/guides/dashboard-connectors)
- [Especificação da API](https://docs.pluggy.ai/openapi/pluggy-api.json)

## Configuração privada

1. Entrar no [Dashboard](https://dashboard.pluggy.ai) com a conta do Meu Pluggy.
2. Em Applications → aplicação pessoal → Demo → Connect Account, escolher **MeuPluggy** e conectar o portal pessoal.
3. Em Connected Items, copiar o itemId correspondente à conta do instituição bancária. A listagem GET /v2/items não é usada/não está disponível neste caminho pessoal.
4. Obter Client ID e Client Secret da aplicação. Nunca compartilhar esses valores no chat, prints, traces ou arquivos públicos.
5. Preencher as três variáveis correspondentes em `.env.banking.local`, que é ignorado pelo Git, Docker e Vercel.
6. Conferir o `PLUGGY_OWNER_ID` do perfil correto do Java Finance antes de ativar. Não confundir com itemId nem com ID de conta bancária.
7. Na Vercel, definir as quatro variáveis somente no ambiente de produção do backend. Não usar prefixo `VITE_` nem colocar tokens no frontend.

Variáveis: `PLUGGY_CLIENT_ID`, `PLUGGY_CLIENT_SECRET`, `PLUGGY_OWNER_ID` (UUID do usuário), `PLUGGY_ITEM_IDS` (UUIDs separados por vírgula, máximo 5).
O app inicia com integração desativada se a configuração estiver vazia. Outros perfis não podem consultar nem modificar os vínculos desse proprietário.
O script local lê `.env` seguido de `.env.banking.local`; o Compose permite passar as variáveis no ambiente do serviço app.
Compose não lê `.env.banking.local` automaticamente: fornecê-lo explicitamente como segundo `--env-file`, preservando o `.env` principal.
Não executar `docker compose config`/`vercel env pull` publicando a saída com segredos. Não registrar objetos de configuração em logs.

## Fluxo financeiro

1. Vincular uma conta BANK/BRL disponível a uma conta local ativa (corrente, poupança, carteira ou outra).
2. Conferir o saldo de abertura no fim do dia anterior à data inicial. A importação só pode começar depois da abertura.
   Se a conta local abriu hoje com o saldo atual, iniciar amanhã; para importar histórico, conferir antes a abertura histórica correta.
3. Consultar até 93 dias por vez, até hoje; máximo 2.000 registros por consulta e uma tentativa por minuto por vínculo.
4. Os registros entram em revisão: consultar **não** cria movimentações nem altera saldo. O saldo do provedor é apenas informativo.
5. POSTED pode ser importado após categoria e confirmação de natureza. CREDIT → receita e DEBIT → despesa somente após confirmação humana.
   Transferência própria, investimento, empréstimo, estorno ou pagamento de fatura não deve ser tratado automaticamente como renda/consumo.
6. Antes de importar, revisar sugestões compatíveis da mesma conta/valor e até três dias de diferença. São sugestões, não garantia de identificação de todas as duplicidades.
   Vincular uma movimentação efetivada existente não altera o saldo. Transferências próprias podem ser vinculadas nas duas contas, cada lado ao mesmo registro interno.
7. PENDING não pode ser importado nem vinculado. Reconsultar o mesmo ID não duplica. Se dados revisados mudarem no provedor, estado CHANGED exige revisão manual;
   o sistema não modifica nem cancela o lançamento financeiro já confirmado. Alterações retroativas fora do período consultado não são detectadas até esse período ser consultado novamente.
8. Ignorar não cria nem cancela lançamentos. Cancelar um lançamento financeiro existente continua sendo uma ação explícita em Movimentações.

Identificação é por vínculo e ID externo, não apenas descrição. IDs alterados pelo provedor dependem de revisão de correspondências.
Há leitura automática por webhook/cron, mas não reconciliação automática de saldos nem importação de cartões/faturas.
O frontend não soma dinheiro com Number; cálculos monetários permanecem em BigDecimal/NUMERIC e decimais textuais.

## Desativar e privacidade

“Desativar leitura aqui” impede novas consultas/importações no app, mas preserva o histórico. Não revoga o consentimento no provedor:
revogar também no Meu Pluggy/banco quando desejado. Reativação exige que a conexão ainda esteja disponível na lista permitida.
Não persistimos senhas bancárias, chaves de API, números completos de conta, CPF, resposta bruta ou dados de identidade nas tabelas deste módulo.
Persistimos identificadores, descrições, valores, datas, vínculo e auditoria necessários à revisão financeira. São dados privados do proprietário.
As respostas autenticadas não são armazenadas pelo service worker; o cache offline continua restrito a arquivos públicos.

## Atualização automática — 14/10/2026

- Evento `item/updated` do MeuPluggy aciona `POST /api/banking/hooks/pluggy`. O provedor envia um segredo independente no
  cabeçalho `X-JavaFinance-Webhook`. O endpoint não aceita a sessão do navegador como autorização, não segue URLs do payload,
  ignora `clientUserId`, aceita só itens explicitamente configurados e limita o corpo a 8 KiB. Outros eventos são ignorados.
- Configurar `PLUGGY_WEBHOOK_SECRET` e `CRON_SECRET` independentes, aleatórios, com pelo menos 32 caracteres, somente no backend.
  Sem ambos, a automação fica desativada. As rotas normais continuam exigindo sessão e CSRF.
- O cron da Vercel chama `GET /api/banking/jobs/daily` com `Authorization: Bearer <CRON_SECRET>` uma vez por dia, às 09:00 UTC
  (06:00 de São Paulo, sujeito à janela de execução do plano). Recupera avisos perdidos e tarefas pendentes mesmo sem o site aberto.
- Migração aditiva V7: caixa de entrada de eventos deduplicados, tarefas persistidas por proprietário/item, gerações e lease de 10 minutos.
  A notificação é persistida antes da consulta. Retentativas não duplicam eventos, lançamentos ou processamento simultâneo.
  Se o processo for interrompido, a tarefa volta a poder ser executada após expirar o lease.
- O handler tenta concluir a leitura na própria requisição. Se demorar mais de 10 segundos, o provedor pode considerar falha e
  reenviar (até três tentativas segundo a documentação). A fila e o cron mantêm a recuperação independente dessas retentativas.
  Não há promessa de tempo real, execução exatamente uma vez ou entrega infalível do webhook.
- A primeira consulta respeita `importFrom`. Históricos grandes avançam em janelas de até 93 dias, com sobreposição de 7 dias;
  depois de em dia, são reconferidos os últimos 93 dias. O cursor só avança após sucesso. Limite de 2.000 registros por janela:
  se ultrapassado, a tentativa fica pendente e exige consulta manual em período menor; não avança silenciosamente.
- Contas desativadas e datas futuras não são consultadas. “Desativar leitura aqui” interrompe também a automação.
- Os dados novos entram somente em revisão. A automação não chama importação, categorização paga, pagamento ou atualização forçada do banco.
  Exclusões no provedor não cancelam automaticamente registros já persistidos; alterações além da janela exigem revisão histórica manual.
- A página bancária recarrega os registros locais a cada 30 segundos enquanto visível, sem consultar a Pluggy pelo navegador.
  Mostra cobertura da última leitura automática, data inicial futura e aviso de falha. O saldo do sistema continua baseado no ledger.

Registrar apenas um webhook `item/updated` para a URL HTTPS de produção; antes de criar, listar assinaturas e reaproveitar a
assinatura deste endpoint se existir. Não usar `all`, não remover assinaturas de outros sistemas e nunca inserir segredo na URL.
É necessário registrar o webhook além de configurar as variáveis; a presença das variáveis não prova que a assinatura existe.

Referências: [MeuPluggy e webhooks](https://docs.pluggy.ai/en/docs/guides/meu-pluggy-personal-use),
[eventos e retentativas](https://v2.docs.pluggy.ai/en/reference/webhooks),
[cron e proteção na Vercel](https://vercel.com/docs/cron-jobs/manage-cron-jobs).

## Implementação e testes

- Backend: pacote `banking`; client HTTP com host fixo `api.pluggy.ai`, GET para dados financeiros e POST apenas para autenticação do provedor.
- Migração V6 aditiva: `bank_link` e `bank_entry`, chaves de proprietário, referência externa única, versão e vínculo de movimentação.
- Reutiliza `MovementService`/ledger para importação confirmada, sem criar um saldo paralelo. O vínculo a lançamento existente não o reescreve.
- Cliente testado com HTTP simulado conforme a especificação oficial; backend com PostgreSQL dedicado e provedor Mockito.
- Testes de tela usam simulações apenas nas rotas bancárias; cadastro/login/contas/categorias usam o backend descartável real.
- Não executar cenários de cadastro/escrita no domínio de produção. Não publicar traces de dados reais ou segredos.

Limitação: o comportamento real do instituição bancária/Meu Pluggy ainda precisa ser validado após configurar as credenciais pessoais.
