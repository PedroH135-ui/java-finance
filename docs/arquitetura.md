# Arquitetura
Monólito modular por domínio em backend/src/main/java/br/com/javafinance. Cada domínio mantém controller, DTOs, serviço e persistência.
Controllers obtêm o proprietário exclusivamente do principal autenticado. IDs recebidos são sempre consultados junto com owner_id.
Chaves estrangeiras compostas reforçam no PostgreSQL o isolamento entre contas, categorias, lançamentos e postagens.

O frontend é uma SPA React com rotas hash, útil para servir todos os assets pelo Java sem fallback que esconda erros da API.
Componentes compartilhados: cabeçalho, painel, estados de erro/vazio, entrada monetária e diálogo com confirmação de descarte.
TanStack Query cuida do estado do servidor; o cache é apagado ao sair. Formulários usam React Hook Form e preservam dados em falhas.
A navegação oferece apenas recursos implementados. Há drawer em telas menores e listagens em cards no celular.

## Segurança
Spring Security autentica por e-mail normalizado e hash BCrypt via DelegatingPasswordEncoder.
Sessão HttpOnly/SameSite=Strict, CSRF padrão em sessão, token obtido antes das mutações.
COOKIE_SECURE deve ser true em qualquer implantação HTTPS futura. Nenhuma hospedagem é configurada neste projeto.
Registro desativado por padrão. Limite local: 20 tentativas de login/5 minutos e 5 cadastros/hora por IP direto.
Não se confia em X-Forwarded-For. O limitador é em memória e reinicia com a aplicação; isso é adequado à execução local, não um controle distribuído.
Troca de senha aumenta credential_version, invalidando todas as sessões na próxima requisição.
Senhas e tokens não são armazenados em localStorage.

## Integridade e concorrência
Movimentação é a unidade atômica. Transferência tem uma movimentação e duas postagens com sinais opostos.
Locks de conta são obtidos em ordem de UUID. Confirmação e cancelamento travam a movimentação.
Versão otimista detecta formulários desatualizados; request_key por usuário evita duplicação em reenvios.
Entidades confirmadas não têm edição destrutiva de valores: cancele com motivo e registre a correção.
Postagens e registros cancelados permanecem para auditoria. Nenhuma exclusão em cascata.
Flyway gerencia o schema. Hibernate apenas valida; não cria nem substitui tabelas automaticamente.

## Datas
LocalDate representa operação, competência, vencimento e efetivação. Competência usa o primeiro dia do mês.
Instant/timestamptz registra auditoria. O relógio de negócio usa America/Sao_Paulo.
Datas civis são transmitidas como AAAA-MM-DD, sem conversão UTC na interface.

## Entrada futura
source, external_reference e batch_id já existem no modelo, mas importadores e conciliação ainda não estão implementados.
Nenhum SDK, endpoint ou credencial de fornecedor bancário.

