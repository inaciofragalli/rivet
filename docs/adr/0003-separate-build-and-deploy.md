# ADR 0003: Separar construção, entrega do artefato e implantação

- **Status:** Direção arquitetural confirmada para o MVP; executor ainda não implementado
- **Data:** 2026-10-15
- **Contexto:** Rivet / aplicação de imagens Docker em nó Ubuntu doméstico

## Contexto

O Rivet precisa implantar aplicações Docker em um servidor doméstico de recursos modestos. Construir imagens no próprio servidor adicionaria carga de CPU, RAM e disco, atrasando o objetivo inicial. No futuro, o sistema pode integrar GitHub Actions, registries e descoberta de Dockerfiles, mas o MVP não depende dessas funcionalidades.

O backend já recebe arquivos TAR/TAR.GZ exportados com `docker save`, valida metadados, calcula SHA-256 e mantém os artefatos locais. Também cadastra aplicações e servidores. **O fato de receber um artefato não significa que ele já esteja importado ou executando no nó de destino.**

## Decisão

Separar três responsabilidades:

1. **Build:** produzir imagem Docker previamente construída, inicialmente fora do Rivet.
2. **Entrega:** disponibilizar o arquivo da imagem ou sua referência no servidor de destino. Para o MVP, usar arquivo de `docker save` e transporte autenticado via SSH.
3. **Implantação:** importar e iniciar o contêiner Docker com opções restritas, verificar resultado e registrar transições duráveis.

O módulo `image` mantém o artefato e metadados; `application` mantém configuração lógica; `server` mantém o destino cadastrado. O futuro módulo `deployment` coordenará envio, execução e estado persistido, consumindo APIs públicas dos módulos existentes.

O executor inicial será simples e seguro, com validação da identidade da host key SSH, autenticação por chave e comandos permitidos. Não introduzir serviço de build, agente remoto ou sistema de filas distribuídas apenas para o MVP.

## Consequências

A máquina Linux de destino não precisa compilar o código. O mecanismo de deployment poderá futuramente receber imagens produzidas por CI/registry sem refazer toda a orquestração. As etapas e falhas podem ser documentadas separadamente (transferência, importação, inicialização e prontidão).

A separação exige contratos explícitos e cuidado com consistência entre banco de dados, arquivo local e estado Docker observado. A operação de transferência e execução deve ser idempotente ou reconciliável após queda. O banco registra intenção e histórico, mas a observação do runtime vem do Docker no destino.

## Alternativas consideradas

1. **Construir imagens diretamente no Ubuntu doméstico:** adiado por limitação de recursos e por não ser necessário ao MVP.
2. **Integrar imediatamente repositórios GitHub e builders automáticos:** adiado; criaria dependências antes do primeiro deployment funcional.
3. **Começar com um agente remoto próprio:** adiado; SSH atende ao primeiro destino confiável com menor complexidade.
4. **Usar imagem pronta, entregar e implantar por SSH:** decisão adotada.

## Quando revisar

Quando existir um deployment ponta a ponta, avaliar registries, GHCR, builds por CI, vários destinos e necessidades reais de um agente. Não confundir essa possibilidade de evolução com funcionalidades já disponíveis.

## Referências internas

- `docs/architecture/overview.md`
- `docs/architecture/modules/image.md`
- `docs/architecture/modules/application.md`
- `docs/architecture/modules/server.md`
