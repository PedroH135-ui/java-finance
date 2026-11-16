# Validação da IA com perfil real e módulos — 16/11/2026

O usuário autorizou ativar/testar a categorização no próprio perfil com movimentações reais, preservando correções e saldos. Depois esclareceu que o recurso deve continuar disponível para todos, com **ativação individual**, e não ativação global obrigatória.

Ao abrir a sessão autorizada, a preferência já estava ligada e havia categorias AUTO. Não foi desligada/reativada. Uma única ação **Tentar pendentes** foi executada pela interface; a geração persistida da fila concluiu, sem lote preso. Não houve deploy, mudança de preferências de outros usuários, importação, confirmação de lançamentos, criação de postings, definição de limites ou alterações de saldo.

Contagens, categorias aplicadas, distribuição mensal e pendências reais foram mostradas ao usuário na conversa. **Não registrar esses dados financeiros, descrições, valores ou IDs neste documento público.** Agregados e hashes operacionais ficam em `.tools/real-category-validation`, ignorado pelo Git. Apenas hashes gerados no PostgreSQL e agregados foram persistidos, sem dump ou linhas financeiras.

## Resultado técnico

- Registros reais apresentaram decisões com método LLM e reutilização MEMORY, confirmando a economia de chamadas em descrições repetidas. O método registrado não equivale, sozinho, a uma contagem exata de chamadas HTTP ao provedor.
- Casos sem categoria já haviam sido tentados; pendência semântica não é o mesmo que mensagem de fila pendente. Pagamentos de fatura, aplicações/resgates e PIX sem finalidade ficaram sem categoria, em vez de forçar uma classificação.
- Hashes dos campos financeiros, contas, movimentos, postings e saldos preservados. Hashes de escolhas manuais/correções/transferências também preservados durante a retomada. Não foram criadas escolhas manuais fictícias no perfil real para provocar corridas; a prioridade em conflitos continua coberta pelos testes locais anteriores.
- O contador do aviso da IA considera todo o histórico; a listagem respeita mês/paginação. Essa diferença precisa ser explicada na interface para evitar parecer erro de contagem.
- Contas mostrou saldo bancário e datas da última consulta/provedor. Categorizar não consulta novamente o banco e não torna o saldo uma informação em tempo real.
- Visão geral e Orçamentos mostraram os mesmos valores por categoria no mesmo mês de competência. Limites não definidos explicam os cartões de orçamento com limite zerados; os gastos continuam no grupo sem limite.
- Conexões bancárias mostrou leitura ativa e dados conferidos no dia. Não foi acionada nova consulta ao Nubank/Pluggy nesta validação. Os rótulos legados “Revisar” nessa tela refletem o fluxo bancário e ainda não exibem a categoria AUTO, mesmo quando Movimentações já a mostra.

## Melhorias identificadas, não implementadas nesta auditoria

1. Separar natureza financeira (transferências, faturas, investimentos e dívidas) de categoria de consumo antes de usar os totais como renda/gasto efetivo ou taxa de economia. Hoje entradas/saídas incluem esses registros, com aviso no dashboard. Não ocultar ou identificar transferências por conta própria para corrigir indicadores.
2. Alinhar Conexões bancárias com a categoria/origem AUTO/MANUAL mostrada em Movimentações, evitando interpretar “Revisar” como classificação ausente.
3. Mostrar o período do contador da IA e diferenciar casos já analisados sem evidência, erros temporários/cota e itens ainda na fila. A retomada mantém o cooldown atual, sem forçar chamadas repetidas ao LLM.
4. Melhorar reconhecimento de comerciantes conhecidos dentro de descrições de PIX com regras explícitas/histórico humano; a proteção atual é conservadora e não deve ser removida para inferir finalidade a partir de nomes de pessoas.

A melhoria anterior de mensagem para sessão expirada permanece apenas local em `dc91de0`, branch `codex/corrige-categorias-padrao`; esta validação não a publicou. Próxima ação depende do escopo autorizado na conversa. Não ativar novos usuários ou publicar alterações apenas por este relatório.
