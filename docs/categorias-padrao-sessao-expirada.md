# Falha no botão por sessão expirada — 16/11/2026

Após o merge autorizado da PR #16 (`34c2098`), o usuário relatou falha em Adicionar categorias padrão. Nenhum novo deploy ocorreu por esse merge. Produção continua no deployment Java `dpl_8PAoHDgMV7uwXmwCFHQMj5kWSHZV`, código funcional `e9137ce`.

## Diagnóstico e resolução na sessão real

A requisição POST `/api/categories/defaults` retornou **401** às 20:04:43 UTC (17:04:43 em São Paulo). A consulta GET `/api/auth/me` também retornou 401 às 20:07:33 UTC. O frontend manteve a página previamente carregada e escondeu o motivo atrás de “Não foi possível adicionar. Tente novamente”. Não era evidência de falha SQL na criação das categorias.

A sessão foi renovada; GET `/api/auth/me` passou a 200 às 20:07:45 UTC. Uma única nova tentativa controlada no botão foi realizada e a tela confirmou **14 categorias adicionadas, nenhuma existente alterada**. Não repetir a tentativa original sem conferir a sessão. O aviso de método GET não suportado encontrado nos logs era de outro horário e não explica esse POST.

Somente categorias foram adicionadas ao perfil real. Hashes gerados no PostgreSQL antes/depois confirmaram dados financeiros, saldos, preferências da IA, correções e transferências preservados. A categoria foi excluída intencionalmente da comparação porque sua criação era o efeito solicitado. Nenhum LLM, importação ou ativação de IA foi acionado pelo procedimento. Evidências ficam em `.tools/default-categories-session-fix`, fora do Git; logs brutos não foram persistidos.

## Melhoria de mensagem, somente local

Branch `codex/corrige-categorias-padrao`, baseada no merge da #16. `Categories.tsx` diferencia 401, 403 e falhas gerais. Em 401, informa sessão expirada, impede repetir o POST e oferece **Entrar novamente**, que reconsulta o perfil pela infraestrutura existente; se continua não autenticado, a tela de login aparece. Não faz logout forçado nem repete automaticamente a escrita. Em 403, orienta atualizar para validar a ação, sem enfraquecer CSRF. Falhas gerais mantêm o aviso existente e a tentativa manual.

Nenhuma autenticação, cookie, duração de sessão, credencial, configuração ou código Java foi alterado. Adicionados cenários de navegador local com API fictícia para 401 no POST e 403 na obtenção de CSRF, verificando a ausência de repetição automática e o retorno à entrada. Manter também os cenários anteriores de sucesso, arquivadas e falha transitória.

Esta melhoria ainda não foi publicada ou enviada ao GitHub. Para revertê-la localmente por uma alteração posterior revisada, restaurar o comportamento anterior do componente; não há migração de banco. Preservar as categorias reais já adicionadas. Novos deploys, PRs e merges exigem autorização pertinente na conversa.

Validação local concluída: lint, três testes unitários frontend, build e cinco cenários Playwright passaram (incluindo 401/403 e os três fluxos anteriores). Permanece o aviso anterior de bundle grande. Gitleaks nas fontes sem achados; doze segredos comparados só em memória com 434 arquivos/recursos, zero correspondências; checagem de arquivos privados aprovada. Java não mudou, por isso a suíte de backend não foi repetida.
