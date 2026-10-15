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
Vite para 7.3.7, Vitest para 4.1.11 e typescript-eslint para 8.71.1. Isso não substitui uma revisão de segurança
do aplicativo nem uma auditoria de dependências do backend/imagens Docker.

