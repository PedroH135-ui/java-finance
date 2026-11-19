# Próximos compromissos no Orçamento

A tela mostra uma janela móvel de 30 dias, contando hoje no fuso de São Paulo.
O usuário informa conta vinculada, vencimento, valor e descrição do extrato.

A API Java consulta o extrato já sincronizado pelo Pluggy. O vínculo exige um
débito efetivado, visível, da mesma conta, com o mesmo valor e descrição e data
até três dias do vencimento. A quantidade de candidatos é conferida nos dois
sentidos. Só vínculos únicos recebem o status Identificado. Ambiguidades
ficam em um subtotal separado para conferência. O botão **Ver lançamento**
abre o registro original em Movimentações.

A V20 adiciona a tabela de previsões. Sincronizar e remover uma previsão não
cria lançamentos ou postings. Os totais de consumo e saldos continuam derivados
das fontes financeiras existentes. Esta entrega cobre previsões pontuais
cadastradas no Orçamento.

## Validação

- Backup lógico V19 criptografado e decriptado para verificação de integridade.
  Duas restaurações em bancos novos da branch Neon isolada existente. A cópia
  de recuperação está configurada como somente leitura por padrão.
- V19 → V20 em PostgreSQL 18.7 com Flyway 11.20.3. Uma migração aplicada,
  20 migrações validadas e zero reaplicações. A tabela nova iniciou vazia
  e os registros financeiros e preferências conferiram com a recuperação.
- 31 testes Java, 11 testes frontend, lint e build aprovados. A integração
  exercitou a rotina de sincronização do Pluggy com um provedor fictício.
  Repetir o débito não o duplicou. Uma transação pendente ou oculta não serviu
  como correspondência.
- Demonstração local com dados fictícios conferiu o total antes e depois e a
  abertura do débito correto pelo botão **Ver lançamento**.
- Gitleaks sobre o pacote de publicação e o diff de saída sem achados.
  Evidências de backup e logs ficam privados.

## Publicação em 19/11/2026

Java/React publicado no deployment `dpl_5oWxo8PZ748x3pna7beuu6n53dt1`, fonte
`39d71e7`, com a V20. A verificação após a migração conferiu os registros
financeiros com o backup antes de promover o domínio. Java e Python responderam
saudáveis, o novo bundle está no domínio público e a API devolveu 401 sem sessão.

O teste autenticado de ponta a ponta foi realizado localmente com dados
fictícios. A sessão de navegador em produção estava expirada, portanto a
verificação após a promoção cobriu saúde, artefato servido e acesso à API.
## Retorno de versão

Se for necessário retornar à aplicação anterior, validar a V19 da aplicação
em um candidato antes de promover, preservando a V20 e a tabela de previsões.
A migração é aditiva. A tabela pode continuar no banco até a aplicação voltar
a usá-la. Restaurar o backup integral exigiria tratar alterações dos usuários
posteriores ao backup.
