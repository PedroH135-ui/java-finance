# Backup e restauração PostgreSQL
Exportação pessoal (ainda pendente) não substitui backup administrativo.
O backup contém dados de todos os usuários e hashes de senha: armazene fora do Git, com acesso restrito.

## Backup consistente
Com os serviços iniciados:
```powershell
docker compose exec db sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f /tmp/java-finance.dump'
docker compose cp db:/tmp/java-finance.dump ./java-finance.dump
```
Escolha um destino seguro fora do repositório; o exemplo é apenas ilustrativo.
Para evitar redirecionamento binário incorreto no PowerShell, use -f dentro do contêiner e docker compose cp.
Copie para outra unidade e registre a data. Não confunda backup com o volume do Docker.

## Restauração de verificação
Use exclusivamente um contêiner/banco descartável com nome novo.
Exemplo (substitua a senha de teste por uma gerada):
```powershell
docker run -d --name java-finance-restore-check -e POSTGRES_PASSWORD=SENHA_DESCARTAVEL -e POSTGRES_DB=restore_check postgres:16.10-alpine
docker cp ./java-finance.dump java-finance-restore-check:/tmp/restore.dump
docker exec java-finance-restore-check pg_restore -U postgres -d restore_check --no-owner --no-privileges --exit-on-error /tmp/restore.dump
docker exec java-finance-restore-check psql -U postgres -d restore_check -c 'SELECT count(*) FROM movement;'
```
Compare contagens, saldos e versão Flyway com a origem. Só declare backup válido depois de restaurar e verificar.
Nunca aponte pg_restore ao banco financeiro de uso sem autorização explícita.
Restauração de produção e limpeza de volumes não são automatizadas.

## Verificação feita nesta entrega

Em 07/10/2026, um dump do banco fictício `java_finance_e2e` foi restaurado em um banco novo,
`java_finance_restore_e2e_20261007`. Contagens de usuários (3), movimentos (6), lançamentos (7), auditorias (12),
migrações (3) e soma dos lançamentos (8000,00) coincidiram nesse snapshot.
Esse valor inclui saldos iniciais e lançamentos retidos de operações canceladas; não representa receita ou saldo disponível.
O banco pessoal não foi sobrescrito. Essa verificação não é um backup dos dados pessoais do usuário.

