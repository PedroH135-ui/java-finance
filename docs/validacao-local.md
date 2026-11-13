# Demonstração local isolada

Este ambiente serve para validar transferências e sugestões de categorias antes de decidir por uma publicação. Usa dados inteiramente fictícios de novembro de 2026. Não consulta o Nubank nem usa credenciais ou dados de produção.

## Abrir novamente

Com Docker Desktop iniciado, na raiz do repositório:

```powershell
pwsh -NoProfile -File scripts/start-review-local.ps1
```

Abra http://127.0.0.1:8082. Perfil fictício: `demo@javafinance.local`, senha de demonstração `DemoLocal123!`. Essa senha pública é exclusiva deste ambiente de teste, restrito ao computador. Novos cadastros ficam desativados depois da preparação.

Após editar o código, execute o mesmo comando com `-Build`; ele recompila React e Java e reinicia somente o Java desta demonstração. `-Restart` reinicia sem compilar. Nenhum desses comandos semeia novamente ou apaga os dados.

O banco fica no container `java-finance-review-db`, volume `java_finance_review_pg`, porta `127.0.0.1:55434`. O serviço Python/LangChain escuta somente em `127.0.0.1:8123`. Configuração e logs locais ficam em `.tools/local-review`, ignorada pelo Git e pelo deploy. O launcher não carrega arquivos `.env` nem credenciais da nuvem.

## Preparação em uma base nova

Pré-requisitos desta estação: JDK 21 em `.tools`, Maven e dependências em cache, dependências do frontend instaladas e ambiente Python `.tools/category-ai-venv` com `category-ai/requirements.lock` instalado. O launcher verifica o ambiente antes de iniciar. Para preparar uma base vazia:

```powershell
pwsh -NoProfile -File scripts/start-review-local.ps1 -Build
# Aguarde a página de login estar disponível antes de executar:
.tools/category-ai-venv/Scripts/python.exe scripts/seed-review-local.py
pwsh -NoProfile -File scripts/start-review-local.ps1 -Restart
```

O seed recusa bancos com usuários existentes e aceita apenas o container rotulado e a porta local esperados. Ele cria o perfil pela API e as conexões simuladas diretamente no PostgreSQL local. Não chama endpoints de sincronização ou importação bancária.

## Conferência inicial — novembro de 2026

| Conta fictícia | Saldo | Origem |
|---|---:|---|
| Nubank — DEMONSTRAÇÃO | R$ 3.025,00 | Snapshot bancário simulado |
| Banco B — DEMONSTRAÇÃO | R$ 600,00 | Snapshot bancário simulado |
| Carteira — DEMONSTRAÇÃO | R$ 80,00 | R$ 100 iniciais menos R$ 20 de ônibus |
| Total | R$ 3.705,00 | Soma das contas |

Antes de identificar a transferência entre as duas contas, os relatórios mostram receitas de R$ 3.500,00, despesas de R$ 995,00 e resultado de R$ 2.505,00. A transferência de R$ 500,00 ainda aparece nos dois lados para permitir testar a identificação.

Em Movimentações, abra os detalhes da transferência e confirme o par da outra conta. As receitas devem passar para R$ 3.000,00 e as despesas para R$ 495,00, preservando resultado e saldos. É possível desfazer a identificação.

Para testar categorias, abra o registro Mercado Alfa de R$ 75,00: Alimentação deve ser sugerida a partir do exemplo de R$ 250,00 já classificado. Farmácia Beta deve sugerir Saúde por regra. PIX desconhecido deve ficar sem sugestão. Aplicar uma categoria modifica relatórios e orçamento, sem modificar saldos. Há dois registros ocultos e uma compra pendente para testar os filtros; aluguel de R$ 150,00 está planejado e não afeta o saldo realizado.

Java mantém a validação financeira e o acesso ao banco. Python recebe descrições e exemplos limitados do perfil; LangChain organiza recuperação de histórico e decisão. Histórico e regras funcionam sem consumir um LLM. O laboratório também pode habilitar um [LLM gratuito executado localmente](llm-local.md), com exemplos adicionais de efeito líquido zero. Não há provedor externo de IA habilitado.

Esses testes validam o comportamento local; não provam acesso real ao Nubank nem um deploy na Vercel. A publicação continuará sendo uma etapa posterior, após revisão.

Verificado em 13/11/2026 com Chrome de teste e os serviços reais locais: login, saldos, relatórios, dois registros ocultos, sugestão por histórico, sugestões por regra, ausência de sugestão no PIX desconhecido e identificação/desfazer da transferência. O ciclo de transferência preservou R$ 3.705,00 em saldos e R$ 2.505,00 de resultado. O cenário foi devolvido ao estado inicial para exploração manual. Build integrado e lint passaram; o build mantém o aviso existente de tamanho do bundle frontend.
