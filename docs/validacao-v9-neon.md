# Recuperação Neon e teste isolado da V9

Concluído em **16/11/2026, 11:05 BRT**, a pedido do usuário, sem publicação. Código validado: `467e1f9`, branch Git `codex/rag-vetorial-local`. Nenhuma dependência ou migração foi modificada nesta validação.

## Ponto de recuperação confirmado

| Item | Evidência |
|---|---|
| Projeto correto | `java-finance` / `twilight-field-61680463`, São Paulo |
| Branch de produção | `main` / `br-red-resonance-b6w00wvg` |
| Ponto preservado | `recovery-before-v9-20261116` / `br-shy-scene-b63u96kc` |
| Origem | Branch principal acima, LSN `0/235E9D0` |
| Criado | `2026-11-16T13:58:29Z` (10:58:29 BRT) |
| Estado confirmado | `ready`, sem compute, sem expiração configurada |
| Janela de histórico observada no projeto | 21.600 segundos (6 horas) |

É um ponto de recuperação por branch copy-on-write **dentro do mesmo projeto Neon**, não um dump exportado ou backup independente contra exclusão do projeto/conta. Foi preservado sem executar SQL nele. Não apagá-lo nem usá-lo como ambiente de desenvolvimento.

Sua capacidade de leitura foi exercitada criando uma segunda branch a partir dele. Antes da migração, essa cópia estava na V8, continha registros bancários e correspondia à produção pelas contagens e fingerprints financeiros comparados em memória/arquivo privado. Não foi feita restauração sobre a produção. O mecanismo de isolamento e a recuperação por branches estão descritos na [documentação oficial](https://neon.com/docs/postgres/backup-restore/branch-restore).

## Branch de teste e procedimento

`verify-v9-20261116` / `br-cold-leaf-b6xfl0i8`, filha do ponto de recuperação, com endpoint próprio `ep-quiet-brook-b6yrld61`. Compute mínimo de 0,25 CU; expira em **18/11/2026 às 14:00 UTC**. Após os testes, somente esse compute foi suspenso e confirmou estado `idle`; produção permaneceu `active`. O plano recusou customizar o timeout, então foi mantido o padrão do plano.

1. Conferir ancestralidade, prontidão, endpoint direto e banco `java_finance` antes de conectar. Nenhum `checkout`, `env pull` ou alteração de conexão Vercel/local.
2. Na produção, abrir somente transações com `default_transaction_read_only=on`; consultar schema e fingerprints, sem trazer descrições/valores/hashes de senha para os relatórios.
3. Na cópia recuperada, registrar a baseline V8 e os fingerprints das colunas financeiras existentes. O fingerprint de `bank_entry` usa as mesmas colunas V8 antes/depois, sem confundir colunas novas vazias com alteração de registro.
4. Executar **somente Flyway**, usando as bibliotecas do artefato Java local e os SQL versionados, com alvo V9 e `clean` desabilitado. O guard do runner rejeita qualquer endpoint diferente da branch de teste. Não iniciar Spring, jobs, sincronização Pluggy, IA ou importadores.
5. Na validação inicial, a única pendência era V9 (`RESOLVED_VERSIONED_MIGRATION_NOT_APPLIED`); nenhuma divergência de V1–V8 foi ignorada. Não executar `repair`, `baseline` ou desativar checksums. Migrar, validar estritamente e repetir `migrate`.
6. Comparar os dados da mesma branch antes/depois e consultar novamente a produção em modo somente leitura. Credenciais capturadas da CLI ficaram apenas na memória dos processos, sem argumentos de comando ou `.env`.

O runner usou verificação TLS `verify-full` com CA pública explícita. Um caminho temporário ASCII para o certificado contornou incompatibilidade do libpq Windows com o nome acentuado do workspace; TLS não foi enfraquecido. Runners e evidências operacionais estão em `.tools/neon-v9`, ignorado pelo Git, e **não acompanham um clone novo**.

## Resultado observado

| Verificação | Resultado |
|---|---|
| Migração isolada | V8 → V9; uma migração executada; 3.356 ms no `migrate` medido |
| Validação após migração | Nove migrações válidas |
| Segunda execução | Zero migrações; schema já atualizado |
| Contas, movimentos, postings, registros bancários, vínculos e categorias | Contagens e fingerprints das colunas existentes preservados |
| Saldos calculados de postings | Preservados por conta/proprietário |
| Saldos informados pelo banco | Preservados nos vínculos |
| View `financial_record` | Conteúdo preservado na comparação antes/depois |
| Novos campos categoria/transferência | Vazios; nenhuma escolha automática |
| Pares e view de transferências válidas | Vazios; nenhuma identificação automática |
| Constraints PostgreSQL | Validadas |
| Produção após teste | Schema V8, sem estrutura V9 e com fingerprints financeiros inalterados no intervalo observado |

Esses testes validam a migração e a preservação do conjunto real clonado; não substituem o smoke funcional do futuro deployment. Os testes funcionais locais já documentados cobrem escolha/remover categoria e identificação manual de transferências. Nenhum dado real foi enviado ao LLM.

## Alerta antes da publicação

O banco observado usa **PostgreSQL 18.7**. O **Flyway 11.7.2** empacotado emitiu aviso de que sua versão testada mais recente é PostgreSQL 17. A migração e a validação passaram, mas isso não elimina o aviso de compatibilidade. Avaliar uma versão compatível do Flyway em alteração separada e repetir a validação isolada antes de publicar uma atualização de dependências; nada foi atualizado silenciosamente.

## Próximo passo e recuperação

O usuário pediu para ver o resultado antes de publicar: **nenhum deploy, promoção de domínio, merge ou aplicação da V9 na produção foi realizado**. O Python publicado continua com a recuperação lexical; embeddings/pgvector continuam no laboratório local.

Antes de um futuro deploy, revalidar banco/endpoint/schema e o ponto de recuperação; criar um ponto recente se houver novas operações desde este teste. Preservar a recuperação atual. A execução do Java novo aplica Flyway mesmo sem promover domínio.

Para recuperação, criar outra branch filha do ponto V8 preservado e verificar dados/invariantes antes de considerar qualquer mudança de conexão. Não restaurar sobre a produção ativa nem perder operações posteriores ao ponto sem um plano explícito. Rollback funcional da IA permanece desligar `CATEGORY_AI_ENABLED`, mantendo a migração aditiva e escolhas manuais existentes. Não aplicar `DROP` de V9 como rotina de rollback.
