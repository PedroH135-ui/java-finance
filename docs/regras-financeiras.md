# Regras financeiras da fundação
## Fonte de verdade
O saldo de uma conta é a soma de posting.amount de movimentações CONFIRMED cuja settled_date seja até a data consultada.
Não existe coluna de saldo atual que possa divergir do histórico. OPENING registra o saldo inicial como postagem própria.
Contas arquivadas continuam no consolidado; arquivar não faz dinheiro desaparecer.

## Três visões
- Competência: INCOME menos EXPENSE efetivados na competência escolhida. Previstos e cancelados ficam de fora.
- Caixa: postagens pela data de efetivação. No consolidado, OPENING e TRANSFER ficam fora do fluxo do período; ADJUSTMENT é mostrado como correção de caixa.
- Patrimônio: ainda não implementado. Não chamar saldo de contas de patrimônio líquido.

Transferência própria conserva o consolidado. Seus dois efeitos são confirmados ou cancelados juntos.
Previsões não possuem postagens. Confirmar gera as postagens uma única vez.
Saldo inicial e ajustes não são receita mensal. Ajustes aceitam diferenças positivas ou negativas, com descrição e auditoria.
Receitas/despesas/transferências aceitam valores positivos. Efetivação não pode anteceder operação ou abertura da conta nem ser futura.
Saldo negativo é permitido. O sistema não inventa limite de crédito, juros ou bloqueios.

## Dinheiro
BigDecimal no Java; NUMERIC(19,2) no PostgreSQL. JSON sempre apresenta valores monetários como strings decimais.
Entradas aceitam até 15 dígitos inteiros e duas casas decimais; excesso de casas é rejeitado, não arredondado silenciosamente.
Percentuais derivados usam duas casas e HALF_UP. Taxa de economia = (receitas - despesas) / receitas * 100.
Receita zero: taxa null/indisponível. Economia negativa é válida.
O frontend não soma valores com Number. Converte para Number somente as coordenadas dos gráficos; tabelas apresentam os decimais exatos recebidos.
As máscaras monetárias de entrada usam vírgula para decimal e ponto para milhar.

## Orçamentos mensais

Um limite por usuário/categoria de despesa/mês. Limites não são lançamentos nem reservas: não criam postagens e não alteram saldo.
O gasto usa competência e somente despesas efetivadas. Previstos são separados; cancelados, transferências, receitas e saldos iniciais não entram.
Zero significa planejar não gastar; ausência de limite é representada por null. Não se divide por zero para calcular percentual.
O restante pode ser negativo. Um excedente em uma categoria não desaparece mesmo se outra categoria tiver sobra.
A cópia do mês anterior copia somente limites para categorias ativas sem limite no destino, nunca movimentações.
Limites existentes não são sobrescritos; edições usam versão para impedir perda de atualizações concorrentes.
Totais de orçamento abrangem categorias com limite; os gastos das demais são mostrados separadamente.

## Módulos seguintes
Aportes, resgates, empréstimos e pagamentos de cartão ainda NÃO possuem tipos próprios na interface.
Não registre essas operações como receitas/despesas de consumo para simular módulos ausentes.
Cartões deverão separar competência das parcelas, caixa do pagamento e obrigação total futura.
Metas e reservas serão alocações de recursos, nunca ativos duplicados.

