# Módulo `application`

**Status:** implementado (cadastro e consultas)  
**Package:** `io.github.inaciofragalli.rivet.application`  
**Atualizado em:** 2026-10-10

## Responsabilidade

O módulo `application` cadastra e consulta as configurações básicas das aplicações que poderão ser implantadas pelo Rivet.

O registro de uma `Application` ainda não inicia um contêiner. Servidores, deployments, logs, estado de execução, histórico e publicação HTTPS serão tratados em outras partes do sistema.

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

`ApplicationResponse` é um `record` que não depende da entidade JPA. A conversão acontece no método privado `toResponse` de `ApplicationService`. O teste `RivetModularityTests` verifica os limites entre os módulos.

## Persistência

| Campo | Tipo | Regra |
| --- | --- | --- |
| `id` | UUID | Identificador gerado pelo JPA; chave primária |
| `name` | `VARCHAR(30)` | Obrigatório e único |
| `image` | `TEXT` | Referência de imagem configurada; obrigatória |
| `container_port` | `INTEGER` | Obrigatório, de 1 a 65535 |

O campo `image` guarda a referência configurada para a aplicação. Ele não informa qual imagem está implantada em um servidor. Essa informação ficará com o futuro módulo de deployments. A entidade atual também não tem `created_at`, estado de execução ou vínculo com um servidor.

O esquema é definido por duas migrações Flyway:

- `V1__create_applications.sql`: cria a tabela `applications` com chave primária, unicidade do nome e limite de porta.
- `V2__rename_application_name_constraint.sql`: renomeia a constraint de unicidade para `uk_applications_name`, permitindo identificá-la no tratamento de erros.

O Hibernate valida o mapeamento com `ddl-auto=validate`. Alterações no esquema devem entrar em novas migrações; as que já foram aplicadas permanecem intactas.

## API HTTP

As rotas começam em `/api/applications`.

### `POST /api/applications`

Exemplo de payload:

```json
{
  "name": "taskhub",
  "image": "ghcr.io/exemplo/taskhub:v1",
  "containerPort": 8080
}
```

Um cadastro bem-sucedido retorna `201 Created`, o DTO com `id`, `name`, `image` e `containerPort`, e o header `Location` apontando para `/api/applications/{id}`.

O `CreateApplicationRequest` aplica as seguintes validações:

- `name`: obrigatório, não apenas espaços, máximo de 30 caracteres; deve começar com letra ou dígito e conter apenas letras, dígitos, `_`, `.` e `-`.
- `image`: obrigatória e não vazia; **não há ainda uma validação completa da sintaxe de referências OCI/Docker**.
- `containerPort`: inteiro obrigatório entre 1 e 65535.

Se os dados forem inválidos, a resposta é `400 Bad Request`. Se o nome já estiver cadastrado, é `409 Conflict`.

### `GET /api/applications/{id}`

Consulta uma aplicação pelo UUID. Retorna `200 OK` com `ApplicationResponse` quando encontrada e `404 Not Found` quando não existe. Um UUID malformado resulta em `400 Bad Request`.

### `GET /api/applications`

Retorna `200 OK` com uma lista de `ApplicationResponse` ou `[]` quando não há registros. A API ainda não define paginação nem uma ordem garantida para os resultados.

### Tratamento de erros

Os erros tratados pelo módulo usam `ProblemDetail`. O `ApplicationExceptionHandler` responde com `409` para `ApplicationAlreadyExistsException` e `404` para `ApplicationNotFoundException`.

O banco também tem uma constraint única sobre `name`. Se duas requisições tentarem gravar o mesmo nome, o handler reconhece a violação quando recebe um `DataIntegrityViolationException` contendo um `org.hibernate.exception.ConstraintViolationException` com SQLSTATE `23505` e constraint `uk_applications_name`. Nesse caso, retorna `409 Conflict`. Outras violações são relançadas, sem serem classificadas automaticamente como duplicidade.

## Fluxo de cadastro

1. O controller valida `CreateApplicationRequest` e chama `ApplicationService.register`.
2. O serviço consulta `existsByName` para rejeitar duplicidade em casos comuns.
3. Uma transação persiste a entidade via `ApplicationRepository`.
4. O serviço converte a entidade no `ApplicationResponse` público.
5. O controller responde com `201` e `Location`.

O `existsByName` pode não encontrar um cadastro que outra transação ainda está gravando. Por isso, a constraint do PostgreSQL continua necessária: ela impede a duplicidade mesmo quando as duas requisições chegam ao mesmo tempo.

## Testes

- `ApplicationControllerIntegrationTest`: cobre as rotas com MockMvc, incluindo validação, duplicidade, consultas, listagem vazia e concorrência. Usa PostgreSQL 17 via Testcontainers e `@ServiceConnection`.
- `ApplicationExceptionHandlerTest`: testa o tratamento da constraint conhecida e o comportamento diante de outra violação.
- `RivetModularityTests`: verifica os limites modulares com `ApplicationModules.of(RivetApplication.class).verify()`.

O teste de concorrência usa `CountDownLatch` e confere o resultado externo: uma resposta `201`, outra `409` e apenas um registro persistido. Ele não garante que as duas transações tenham passado pelo `existsByName` antes de tentar gravar.

## Limites atuais

Os futuros módulos de servidores e deployments deverão interagir com `application` por suas interfaces públicas. O histórico de implantações e o estado observado de cada contêiner ficarão no domínio de deployments.

Ainda não existe autenticação administrativa própria. O perfil `dev` reduz as restrições de segurança para desenvolvimento local e não deve ser usado em produção.