# Cartões/faturas e filtros de investimentos — 17/11/2026

## Diagnóstico e filtro publicados

Branch local `codex/corrige-cartoes-investimentos`, commit `dbf9f4c`.
Após testes, usuário autorizou explicitamente publicar esse diagnóstico/filtro
e executar uma nova leitura controlada. A primeira tentativa de deployment foi
barrada pela revisão automática por falta dessa autorização específica; não foi
contornada. Com a autorização posterior, build Production com skip-domain,
saúde UP pela sessão Vercel existente e promoção do mesmo candidato.

Java atual: `dpl_H7WCSzgNP2iD3py5mE7ufbkaxDaL`.
Anterior: `dpl_2rzkbwNhztw62SrYXR8aspQFCH2b`. Python preservado no deployment
`dpl_FAfZ4jeHmEXjfJSmSuqBThAeQ9Ji`. Schema V18, sem migrações nesta etapa.

- O diagnóstico agora preserva etapa e campo permitido, sem payloads/valores.
- Consulta controlada localizou a rejeição em **BILLS / AMOUNT**: total da fatura
  rejeitado pelo leitor. Não prova se o valor é negativo, formato incompatível
  ou precisão; não inventar essa conclusão nem copiar dados financeiros ao Git.
- Ativos é o filtro inicial das posições bancárias; Resgatados e Todos incluem
  o restante conforme a situação informada. `TOTAL_WITHDRAWAL` agora aparece
  como **Resgatado totalmente**. Pendente/desconhecido/vencido não vira ativo.
- Filtro e rótulos conferidos na sessão real. Dados financeiros, correções,
  preferências e projeções mantiveram os fingerprints anteriores após a leitura;
  nenhuma conciliação criada, repetição automática desligada.

## Correção adicional do leitor — somente local

Um total rejeitado não deve impedir a leitura de todo o cartão e das demais
faturas válidas. O leitor agora isola a fatura não validável e sinaliza cobertura
parcial. Não arredonda totais, troca sinal, substitui por zero ou tenta gravar
valores rejeitados. Mantém validações de moeda, propriedade e limites.

A tela explica que a cobertura é incompleta e dados anteriores podem estar
desatualizados. `billsAvailable=false` mantém a conciliação indisponível mesmo
quando há um pagamento com valor compatível. Não se considera a fatura rejeitada
validada; o formato/semântica daquele total ainda exigem investigação específica.
Falhas de autorização/rate limit mantêm as proteções existentes.

Esta correção adicional ainda **não foi publicada**. A autorização recebida
nomeou `dbf9f4c`, de diagnóstico e filtro; não inferir autorização de publicação
deste próximo comportamento por este documento. Não houve push/PR/merge.

## Validação

- Diagnóstico/filtro:20 testes do cliente Pluggy, frontend lint/9 testes/build e
  E2E de filtro com dados fictícios aprovados antes da publicação.
- Correção adicional:21 testes do cliente aprovados. Casos fictícios de total
  negativo, fração não representável em centavos e tipo não numérico isolam a
  fatura rejeitada e preservam a válida, sem registrar valores ou segredos.
- Frontend lint/9 testes/build aprovados. Dois E2E em8,6s: filtros e cobertura
  parcial bloqueando confirmação, com pagamento/total compatíveis, sem POSTs.
- Regressão Java completa **não concluída**: Docker local não respondeu, inclusive
  ao docker info. Somente o JVM de teste identificado foi interrompido. Não
  reiniciamos o Docker compartilhado, não tratamos essa execução como aprovada.
- Fontes do primeiro pacote verificadas com Gitleaks, sem achados; repetir para
  o próximo commit. Dependências e workflow não mudaram; pendência picomatch
  anterior continua separada e não foi suprimida.

## Retomada e rollback

Evidências privadas `.tools/card-fix`, nunca enviar ao Git. Fonte do primeiro
deployment em `source`; não a confundir com o leitor adicional em trabalho.
Antes de publicar o próximo commit: autorização específica, revisão de segredos,
construção/candidato, conferência de saúde/configuração e fingerprints, promoção
e uma leitura controlada sem importação, conciliação ou mudança de saldos.

Rollback desta etapa é somente da aplicação para o Java anterior V18,
preservando cache/categorias/correções e tabelas. Não executar a projeção de
rollback V18→V16, down migration, repair ou provisionamento. O Python não mudou.
