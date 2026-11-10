# Segurança e privacidade

Para comunicar uma vulnerabilidade, use [Report a vulnerability](https://github.com/PedroH135-ui/java-finance/security/advisories/new). Não publique segredos, dados pessoais ou provas com dados reais em issues.

- Configure credenciais somente no ambiente privado do servidor; nunca use variáveis de frontend para segredos.
- Use apenas valores fictícios em exemplos e testes. Não publique dados de instâncias, nomes de contas administrativas, caminhos pessoais, backups ou traces.
- Configure um e-mail noreply do GitHub no Git local antes de criar commits. Alterar essa configuração não remove endereços já presentes no histórico.
- Os workflows verificam arquivos privados, segredos e dependências. A detecção automatizada não substitui revisão.
- Se uma credencial real for exposta, revogue-a e substitua-a. Apagar apenas o arquivo ou mudar a visibilidade não invalida cópias já obtidas.
- A limpeza de dados no histórico exige coordenação com colaboradores, preservação de backup privado e autorização para atualizar as referências remotas.
- Após reescrever o histórico, faça um novo clone ou transplante somente o trabalho ainda não publicado; não faça merge do histórico antigo. Referências de pull requests e caches do GitHub podem preservar commits antigos e exigem tratamento pelo suporte da plataforma. Veja o [procedimento oficial](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/removing-sensitive-data-from-a-repository).

Execute `node scripts/check-public-files.mjs` e uma varredura Gitleaks com `--redact=100` antes de publicar. Não envie relatórios contendo valores sensíveis para logs ou artefatos públicos.

