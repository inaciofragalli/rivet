# Módulo `application`

**Status:** implementado (cadastro e consultas, inclusive vínculo com artefatos de imagem)  
**Package:** `io.github.inaciofragalli.rivet.application`  
**Atualizado em:** 2026-10-11

## Responsabilidade

O módulo `application` cadastra e consulta as configurações básicas das aplicações que poderão ser implantadas pelo Rivet. O cadastro pode usar uma **referência Docker informada diretamente** ou um **artefato já enviado** ao módulo `image`.

O registro de uma `Application` ainda não inicia um contêiner. Servidores, deployments, logs, estado de execução, histórico e publicação HTTPS serão tratados em etapas posteriores.

## Organização do módulo

```text
application/
├── ApplicationService.java
├── ApplicationResponse.java
├── ApplicationAlreadyExistsException.java
├── ApplicationNotFoundException.java
└── internal/
    ├── Application.java
    ├── ApplicationRepository.java
    ├── ApplicationController.java
    ├── CreateApplicationRequest.java
    └── ApplicationExceptionHandler.java
```

O Spring Modulith reconhece `application` como módulo a partir do package. Na raiz ficam `ApplicationService` e `ApplicationResponse`, que podem ser usados por outros módulos. A entidade JPA, o repositório, o controller e os demais detalhes de implementação ficam em `internal/`.

`ApplicationResponse` é um `record` independente da entidade JPA. A conversão acontece no método privado `toResponse` de `ApplicationService`. O teste `RivetModularityTests` verifica os limites entre os módulos.

A integração com o módulo de imagens passa pelos tipos públicos `ImageArtifactService`, `ImageArtifactResponse` e `DockerImageReference`, sem consultar diretamente a tabela de artefatos.

## Persistência

| Campo | Tipo | Regra |
| --- | --- | --- |
| `id` | UUID | Identificador gerado pelo JPA; chave primária |
| `name` | `VARCHAR(30)` | Obrigatório e único |
| `image` | `TEXT` | Referência Docker persistida; obrigatória mesmo no cadastro por artefato |
| `container_port` | `INTEGER` | Obrigatório, de 1 a 65535 |
| `image_artifact_id` | UUID opcional | Chave estrangeira para `image_artifacts.id` quando o cadastro utiliza upload |

O campo `image` guarda a referência configurada para a aplicação. No cadastro manual ela é recebida no corpo da requisição; no cadastro por artefato, é derivada de `image` e `tag` extraídos pelo módulo `image`.

A presença de `image_artifact_id` informa **a origem do cadastro**, não a imagem que está em execução no servidor. A entidade atual não possui `created_at`, estado de execução nem vínculo com um servidor.

As migrações Flyway envolvidas são:

- `V1__create_applications.sql`: cria a tabela `applications` com chave primária, unicidade do nome e limite de porta.
- `V2__rename_application_name_constraint.sql`: renomeia a constraint única para `uk_applications_name`, identificada no tratamento de erros de concorrência.
- `V3__create_image_artifacts.sql`: cria a tabela de artefatos, pertencente ao módulo `image`.
- `V4__link_application_image_artifact.sql`: adiciona `image_artifact_id` e sua chave estrangeira.

O Hibernate valida o mapeamento com `ddl-auto=validate`. Alterações do esquema entram em novas migrações; as já aplicadas não são reescritas.

## API HTTP

As rotas começam em `/api/applications`.

### `POST /api/applications`

Há **duas formas de cadastro**.

**1. Referência Docker informada manualmente:**

```json
{
  "name": "taskhub",
  "image": "ghcr.io/exemplo/taskhub:v1",
  "containerPort": 8080
}
```

**2. UUID de artefato previamente enviado por `POST /api/image-artifacts`:**

```json
{
  "name": "nginx-demo",
  "imageArtifactId": "550e8400-e29b-41d4-a716-446655440000",
  "containerPort": 80
}
```

O UUID no segundo exemplo é ilustrativo. No cadastro por artefato, o serviço consulta `ImageArtifactService.findById` e monta a referência de imagem a partir do nome e da tag armazenados; o cadastro falha com `404 Not Found` se o artefato não existir.

As validações de `CreateApplicationRequest` são:

- `name`: obrigatório, não apenas espaços, máximo de 30 caracteres; deve começar com letra ou dígito e conter apenas letras, dígitos, `_`, `.` e `-`.
- Fonte da imagem: **exatamente uma** entre `image` não vazia e `imageArtifactId` não nulo. `image: ""` com UUID válido é aceito como cadastro por artefato.
- `image`: quando informada e não vazia, precisa ser aprovada pelo validador `DockerImageReference`. O validador suporta um subconjunto de referências de imagem Docker e não aceita referências por digest (`@sha256:...`) nesta etapa.
- `containerPort`: inteiro obrigatório entre **1 e 65535**. O backend não escolhe automaticamente uma porta extraída de `exposedPorts`.

Cadastro bem-sucedido retorna **`201 Created`**, header `Location: /api/applications/{id}` e `ApplicationResponse` com:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "nginx-demo",
  "image": "nginx:alpine",
  "containerPort": 80,
  "imageArtifactId": "<uuid-do-artefato>"
}
```

O exemplo é ilustrativo. Quando o cadastro usa uma referência manual, `imageArtifactId` é `null`.

Dados inválidos resultam em `400 Bad Request`; nomes duplicados resultam em `409 Conflict`.

### `GET /api/applications/{id}`

Consulta uma aplicação pelo UUID e retorna `200 OK` com `ApplicationResponse`, incluindo `imageArtifactId`. Se o registro não existir, retorna `404 Not Found`; um UUID malformado resulta em `400 Bad Request`.

### `GET /api/applications`

Retorna `200 OK` com uma lista de `ApplicationResponse` ou `[]` quando não há registros. A API ainda não define paginação nem ordem garantida.

### Tratamento de erros

Os erros tratados pelo módulo usam `ProblemDetail`. O `ApplicationExceptionHandler` responde com `409` para `ApplicationAlreadyExistsException` e `404` para `ApplicationNotFoundException`. A tentativa de vincular um artefato inexistente resulta em `ImageArtifactNotFoundException`, tratada no módulo `image` como `404`.

O banco possui uma constraint única sobre `name`. Em uma disputa entre duas gravações, o handler reconhece `DataIntegrityViolationException` contendo `org.hibernate.exception.ConstraintViolationException` com SQLSTATE `23505` e constraint `uk_applications_name`, retornando `409 Conflict`. Outras violações são relançadas, sem classificação automática como duplicidade.

## Fluxo de cadastro

1. `ApplicationController` valida `CreateApplicationRequest` e seleciona o método do serviço conforme a fonte de imagem fornecida.
2. No cadastro por UUID, `ApplicationService.registerFromArtifact` consulta a interface pública de `ImageArtifactService` e deriva a referência de imagem.
3. O serviço verifica `existsByName` para rejeitar duplicidade em condições comuns.
4. Uma transação persiste a entidade via `ApplicationRepository`.
5. O serviço converte a entidade em `ApplicationResponse`; o controller responde com `201` e `Location`.

A checagem `existsByName` pode não detectar gravações concorrentes ainda não confirmadas. A constraint de unicidade do PostgreSQL é a garantia final de consistência do nome.

## Testes

- `ApplicationControllerIntegrationTest`: rotas, validações, duplicidade, consulta, lista vazia e concorrência; PostgreSQL 17 via Testcontainers e `@ServiceConnection`.
- `ApplicationImageArtifactIntegrationTest`: cadastro por artefato, UUID inexistente, fontes mutuamente exclusivas, imagem manual e `image` vazia com UUID.
- `ApplicationExceptionHandlerTest`: diferencia a constraint conhecida de outras violações.
- `RivetModularityTests`: verifica os limites dos módulos com `ApplicationModules.of(RivetApplication.class).verify()`.

O teste de concorrência usa `CountDownLatch` e verifica o comportamento externo (uma resposta `201`, outra `409`, um único registro). Não garante que as duas transações tenham passado pela consulta de duplicidade antes da gravação.

## Limites atuais

O cadastro de aplicação e o armazenamento de imagens são recursos de configuração; **a aplicação ainda não é implantada automaticamente**. Os futuros módulos de servidores e deployments deverão interagir com `application` por seus contratos públicos. O estado real de execução e o histórico de deployments pertencerão ao domínio correspondente.

A autenticação administrativa definitiva ainda não existe. O perfil `dev` libera o acesso para desenvolvimento local e **não deve ser exposto em produção**.

Para detalhes do armazenamento de imagens, consulte [Módulo `image`](image.md). Para reproduzir o fluxo HTTP, consulte [Guia de upload de imagens](../../guides/image-upload.md).