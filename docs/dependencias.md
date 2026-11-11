# Dependências e licenças
Versões diretas fixadas em package.json e transitivas em package-lock.json.
Backend usa o BOM do Spring Boot 3.5.16 e Maven Wrapper 3.9.11; o PostgreSQL do Compose é 16.10-alpine.

Famílias principais:
- Java/OpenJDK Temurin: GPLv2 com Classpath Exception.
- Spring Boot/Security/Data, Maven e Flyway Community: Apache-2.0.
- PostgreSQL e pgJDBC: PostgreSQL License/BSD-2-Clause.
- React, TypeScript, Vite, Material UI Core/Icons, Recharts, React Hook Form, TanStack Query, React Router e react-number-format: licenças permissivas MIT (TypeScript: Apache-2.0).
- Testcontainers: MIT. Playwright: Apache-2.0. Vitest e ESLint: MIT.

Nenhum componente MUI X Pro/Premium é utilizado. Sem fontes ou scripts de CDN.
Docker Desktop é uma ferramenta do ambiente, não uma dependência comercial embutida; observe os termos aplicáveis à sua organização.
Docker Engine/Compose podem executar o projeto sem serviços pagos.

Referências técnicas consultadas:
- https://docs.spring.io/spring-boot/3.5/system-requirements.html
- https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
- https://vite.dev/guide/
Os metadados e arquivos LICENSE instalados devem ser revisados novamente ao atualizar dependências.

Auditoria npm executada em 07/10/2026: zero vulnerabilidades reportadas após atualizar React Router DOM para 7.18.4,
Vite para 7.3.7, Vitest para 4.1.11 e typescript-eslint para 8.71.1. Esse resultado é histórico. Isso não substitui uma revisão de segurança
do aplicativo nem uma auditoria de dependências do backend/imagens Docker.

Revisão de 10/11/2026: Vitest atualizado para 4.1.12. O uso transitivo de `minimatch` por
`@typescript-eslint/typescript-estree` é fixado em 11.0.6 para corrigir GHSA-g3hv-75wg-m249.
O override fica restrito ao linter; reavaliá-lo quando typescript-eslint adotar uma versão corrigida.
Lint, testes e build devem ser executados ao atualizar esse override. O CI audita também as ferramentas de desenvolvimento.

## Revisão dos PRs de manutenção — 11/11/2026

Os PRs #4, #6 e #13 foram incorporados: ações checkout/setup-node fixadas por SHA e react-number-format 5.4.5.
A combinação passou em lint, dois testes unitários, build e auditoria npm completa. Nove cenários do componente monetário
foram verificados no Chrome com dados fictícios, incluindo remoção do sinal negativo com prefixo, casas decimais e limpeza.
Os workflows dos três PRs também foram reexecutados e aprovados antes do merge.

As propostas automáticas gerais de versão ficam pausadas por `open-pull-requests-limit: 0`.
Alertas e PRs corretivos de segurança continuam ativos, com agrupamento por frontend/backend. Migrações de Java, Node,
Spring Boot, Material UI e demais versões principais exigem planejamento e testes próprios. Não há merge automático.
O CI atual valida o frontend e a verificação de segredos; mudanças de backend ou imagens exigem também testes Java e Docker.

