# Faturas: total original do banco — V19 local

17/11/2026. Branch `codex/corrige-cartoes-investimentos`. A V19 está testada
somente na branch Neon isolada; não publicada. Produção permanece V18.

## Diagnóstico confirmado

O diagnóstico detalhado `5aa621a` foi publicado com autorização específica em
`dpl_Ca6nozdgg7KSo5zCAcQnFWwsimWo`. Uma leitura controlada identificou rejeições
`BILL_TOTAL_PRECISION` e `BILL_TOTAL_NEGATIVE`. Não foram registrados valores,
respostas bancárias, descrições, credenciais ou tokens nos logs. Cartões e
detalhes estão disponíveis; os totais das faturas ainda são rejeitados nessa versão.
Os fingerprints financeiros, correções e preferências foram preservados.

O leitor aplicava ao snapshot bancário a restrição de centavos positivos dos
lançamentos internos. Isso impedia a consulta de totais originais com sinal
negativo ou frações de centavo. O significado bancário do sinal não é presumido.

## Correção preparada

- `PluggyReadClient.billTotal`: aceita números originais assinados e precisos,
  usando BigDecimal desde o JSON. Não arredonda, troca sinal nem preenche ausentes.
  Limites explícitos: módulo até 999999999999999,99 e escala até64; valores fora
  do domínio ou tipos inválidos continuam sinalizados como cobertura parcial.
- V19 amplia somente `bank_card_bill.total` para numeric sem arredondamento
  implícito, com constraint de faixa/escala. Recria as três views dependentes
  na transação Flyway, sem CASCADE, sem reescrever lançamentos ou saldos.
- O DTO entrega o total como texto decimal por `toPlainString`, inclusive
  valores muito pequenos, evitando notação exponencial e conversão para double.
- A tela formata todas as casas por operações textuais e BigInt. Por exemplo,
  **fictício** `1234.5600000000001` aparece `R$ 1.234,5600000000001`.
- Total zero, negativo ou com fração de centavo não pode ser conciliado.
  UI, serviço Java e view de reconciliação mantêm a proteção. As demais
  exigências de cobertura, pagamento exato e confirmação manual continuam.
- Moeda, propriedade, limites de resposta e caminhos Pluggy v2 permanecem.
  Nenhum efeito da IA sobre saldo ou confirmação foi introduzido.

## Recuperação e testes

Backup recente V18 da conexão direta do projeto Neon correto: dump PostgreSQL18
somente leitura, protegido por Windows DPAPI antes de persistir, decriptação
e digest conferidos. Restaurado em dois bancos novos da branch isolada existente,
sem redefinir ou apagar recuperações anteriores. Banco de recuperação configurado
como read-only. Material privado em `.tools/card-fix/v19`, nunca versionar.

- V18→V19 na branch isolada: uma migração, validação das19 aprovada, repetição
  com zero migrações; PostgreSQL18.7/Flyway11.20.3 sem aviso de compatibilidade.
- Fingerprints de todos os registros protegidos, caches, correções, preferências,
  saldos e views iguais após migração. Produção conferida por leitura: ainda V18.
- Fixtures SQL fictícias: sinal e precisão exatos; NaN, infinitos, excesso de
  faixa e escala rejeitados. Transações revertidas, sem resíduos.
- Compatibilidade: fatura fictícia de centavos da V18 manteve total, assinatura,
  soma dos detalhes e cobertura depois da V19; transação revertida.
- Java:47 testes unitários aprovados, incluindo22 do leitor e MoneyTest.
- Frontend:11 testes, lint e build aprovados. Quatro E2E locais aprovados:
  sinal/precisão na tela, bloqueio de conciliação, cobertura parcial e filtros.
- Varredura Gitleaks dos11 arquivos públicos da entrega: zero achados;
  conferência de arquivos privados versionados aprovada, diff sem erros.
- A suíte completa com Testcontainers não foi concluída: Docker local sem
  resposta na tentativa anterior. Não declarar todos os testes de integração
  aprovados; os testes SQL isolados não substituem toda essa suíte.

## Publicação e rollback

A autorização mais recente cobriu somente o diagnóstico `5aa621a` e uma leitura.
A V19 requer nova autorização específica antes de qualquer candidato que use
o banco real: iniciar Java pode aplicar Flyway mesmo sem promover o domínio.
Após aprovação, revalidar backup/fingerprints/SQL, construir fonte canônica,
conferir candidato e configuração, promover e fazer uma leitura controlada.
Não importar movimentos, confirmar faturas nem alterar saldos ou correções.
Python e suas configurações não precisam mudar.

Não converter numeric novamente para numeric(19,2): isso arredondaria os dados.
Não fazer down migration, repair ou apagar snapshots. Não promover cegamente
uma interface antiga que trunque a precisão. Se houver falha após o rollout,
interromper novas leituras adicionais e preparar rollback de aplicação que
preserve a V19 e o formatador exato; pode reintroduzir a rejeição conservadora
do leitor anterior. Antes de promover esse rollback, testar o artefato com
totais assinados/precisos na branch isolada. Restauração integral do backup
exige avaliação/autorização própria e consideração das alterações humanas
posteriores; não é parte automática desse procedimento.

Sem push, PR ou merge nesta entrega. Dependências e workflow preservados.
