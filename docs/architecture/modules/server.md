# Módulo `server`

**Status:** implementado (cadastro e consultas de servidores; conexão SSH ainda não implementada)  
**Package:** `io.github.inaciofragalli.rivet.server`  
**Atualizado em:** 2026-10-12

## Responsabilidade

O módulo `server` mantém o cadastro dos servidores Linux que poderão receber aplicações implantadas pelo Rivet. Nesta etapa, ele registra os dados necessários para identificar um host e preparar uma futura conexão SSH: nome, endereço, porta e usuário.

**Cadastrar um servidor não estabelece conexão, não verifica sua disponibilidade e não executa comandos remotos.** A verificação de SSH, a detecção do Docker instalado e a execução de containers serão implementadas em etapas posteriores.

O MVP é voltado a um único servidor confiável, mas a tabela e os endpoints permitem cadastrar mais de um registro. Isso não representa suporte implementado a deployments multi-host.

## Organização do módulo

```text
server/
├── ServerService.java
├── ServerResponse.java
├── ServerAlreadyExistsException.java
├── ServerNotFoundException.java
└── internal/
    ├── Server.java
    ├── ServerRepository.java
    ├── ServerController.java
    ├── CreateServerRequest.java
    └── ServerExceptionHandler.java
```

O Spring Modulith identifica `server` como um módulo a partir do pacote. `ServerService` e `ServerResponse` ficam na raiz para oferecer um contrato que possa ser usado por outros módulos. A entidade JPA, o repositório, o controller, o DTO de entrada e o tratamento HTTP permanecem em `internal/`.

`ServerResponse` é um `record` separado da entidade JPA. A conversão é feita pelo método privado `toResponse` de `ServerService`. Nesta etapa, o módulo não depende de `application` nem de `image`.

## Persistência

A migração Flyway `V5__create_servers.sql` cria a tabela `servers`:

| Coluna | Tipo | Regra |
| --- | --- | --- |
| `id` | `UUID` | Chave primária, gerada pelo JPA |
| `name` | `VARCHAR(30)` | Obrigatório e único; constraint `uk_servers_name` |
| `host` | `VARCHAR(255)` | Obrigatório; endereço IP ou hostname informado pelo usuário |
| `ssh_port` | `INTEGER` | Obrigatório; `CHECK` entre 1 e 65535 |
| `ssh_username` | `VARCHAR(30)` | Obrigatório |

O `host` é armazenado como texto, sem resolução de DNS ou teste de conectividade durante o cadastro. A porta SSH é armazenada separadamente, no campo `ssh_port`.

O esquema é controlado pelo Flyway, enquanto o Hibernate valida o mapeamento JPA com `spring.jpa.hibernate.ddl-auto=validate`. Alterações futuras no esquema devem usar novas migrações, sem reescrever a V5 já aplicada.

A tabela não guarda senhas, chaves privadas SSH, fingerprints de host, informações de hardware, estado online/offline ou vínculos com deployments.

## API HTTP

As rotas começam em `/api/servers`.

### `POST /api/servers`

Cadastra um servidor a partir de um JSON:

```json
{
  "name": "homelab",
  "host": "192.168.1.100",
  "sshPort": 22,
  "sshUsername": "ubuntu"
}
```

Um cadastro válido retorna **`201 Created`**, com o header `Location: /api/servers/{id}` e um `ServerResponse`:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "name": "homelab",
  "host": "192.168.1.100",
  "sshPort": 22,
  "sshUsername": "ubuntu"
}
```

O UUID é ilustrativo. O cadastro persiste apenas a configuração do servidor e não confirma que o endereço ou as credenciais SSH estejam corretos.

As validações implementadas em `CreateServerRequest` são:

- `name`: obrigatório, não apenas espaços, com no máximo 30 caracteres.
- `host`: obrigatório, não apenas espaços, com no máximo 255 caracteres.
- `sshPort`: inteiro obrigatório entre **1 e 65535**.
- `sshUsername`: obrigatório, não apenas espaços, com no máximo 30 caracteres.

Não existe, nesta etapa, um validador específico de sintaxe para IPv4, IPv6 ou hostname. O cadastro também não realiza consultas DNS.

Dados inválidos retornam **`400 Bad Request`**. Um nome já cadastrado retorna **`409 Conflict`**.

### `GET /api/servers/{id}`

Consulta um servidor por UUID e retorna **`200 OK`** com `ServerResponse`.

- UUID inexistente: **`404 Not Found`**.
- UUID malformado: **`400 Bad Request`**.

### `GET /api/servers`

Retorna **`200 OK`** com uma lista de `ServerResponse`, ou `[]` se não houver registros.

A API ainda não oferece paginação, filtragem nem uma ordem de retorno garantida.

### Tratamento de erros

`ServerExceptionHandler` trata as exceções de domínio e retorna `ProblemDetail`:

| Situação | Resposta |
| --- | --- |
| Dados inválidos no cadastro | `400 Bad Request` |
| Servidor não encontrado | `404 Not Found` |
| Nome de servidor duplicado | `409 Conflict` |
| UUID malformado na URL | `400 Bad Request` |

Para nomes duplicados, existem duas proteções:

1. `ServerService.register` chama `ServerRepository.existsByName` para rejeitar duplicações comuns.
2. A constraint `uk_servers_name` no PostgreSQL garante a unicidade mesmo com requisições concorrentes.

Quando o banco rejeita a inserção, o handler identifica uma `DataIntegrityViolationException` que contenha uma `ConstraintViolationException` do Hibernate com SQLSTATE `23505` e constraint `uk_servers_name`, convertendo o erro em `409 Conflict`. Outras violações de integridade não são classificadas automaticamente como duplicidade.

O `@RestControllerAdvice` do módulo usa `basePackageClasses = ServerController.class`, limitando sua aplicação aos controllers desse pacote. O `ApplicationExceptionHandler` também possui escopo próprio, evitando que ambos tentem interpretar a mesma exceção de integridade de módulos diferentes.

## Fluxo de cadastro

1. `ServerController` recebe o JSON e aplica `@Valid` a `CreateServerRequest`.
2. `ServerService.register`, em uma transação, verifica se o nome já existe.
3. O serviço instancia `Server` e o persiste com `ServerRepository.save`.
4. `toResponse` converte a entidade para `ServerResponse`.
5. O controller responde com `201 Created` e o header `Location`.

O cadastro não inicia processos de conexão em segundo plano e não consulta o Docker do host informado.

## Testes

`ServerControllerIntegrationTest` utiliza Spring Boot, MockMvc, o perfil `test` e PostgreSQL via Testcontainers. Os testes cobrem:

- Cadastro válido com `201 Created`, corpo JSON e header `Location`.
- Validação de nome, host, porta e usuário SSH, inclusive limites e campos ausentes.
- Nome duplicado com `409 Conflict`.
- Cadastro concorrente do mesmo nome, verificando uma resposta `201`, outra `409` e apenas um registro persistido.
- Consulta por UUID existente, inexistente e malformado.
- Listagem de servidores e lista vazia.

O teste `RivetModularityTests` verifica os limites entre os módulos do projeto. Para executar os testes de integração localmente é necessário um daemon Docker acessível ao Testcontainers.

## Segurança e limites atuais

A configuração `DevSecurityConfig` libera requisições no perfil `dev` para facilitar o desenvolvimento local. **Esse perfil não deve ser exposto à internet ou a redes não confiáveis.** CORS não substitui autenticação e autorização.

A implementação atual **não oferece**:

- Conexão ou autenticação SSH, teste de credenciais ou verificação de host key.
- Armazenamento e gerenciamento de chaves SSH.
- Detecção de sistema operacional, arquitetura, versão do Docker ou disponibilidade do servidor.
- Verificação se o destino SSH é permitido ou alcançável.
- Execução de comandos remotos, deployments, containers ou coleta de métricas.
- Atualização e exclusão de servidores por API.

Antes de executar operações remotas, será necessário implementar autenticação administrativa, autorizar os destinos de conexão e validar a identidade do host SSH. O cadastro atual não constitui uma autorização segura para se conectar a qualquer endereço fornecido.

## Próximas etapas

A próxima evolução prevista para `server` é verificar a conexão com um servidor confiável via SSH, com autenticação por chave e validação de host key, e consultar informações mínimas do ambiente Docker.

Posteriormente, o módulo `deployment` poderá consumir o contrato público de `server` para selecionar o destino de uma implantação, sem acessar diretamente a entidade ou o repositório interno. A execução e o histórico das implantações pertencerão ao módulo `deployment`, não ao cadastro de servidores.
