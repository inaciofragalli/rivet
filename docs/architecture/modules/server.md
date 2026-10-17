# Módulo `server`

**Status:** implementado (cadastro, consultas e teste de conectividade SSH autenticada; deployment ainda não implementado)
**Package:** `io.github.inaciofragalli.rivet.server`  
**Atualizado em:** 2026-10-17

## Responsabilidade

O módulo `server` mantém o cadastro dos servidores Linux que poderão receber aplicações implantadas pelo Rivet. Ele registra nome, endereço, porta e usuário SSH, além de permitir testar uma conexão SSH autenticada com um servidor já cadastrado.

**Cadastrar um servidor não estabelece conexão automaticamente.** A conexão acontece apenas quando o administrador solicita `POST /api/servers/{id}/test-connection`. Esse teste valida a negociação SSH, a identidade do host remoto e a autenticação pela chave configurada, mas **não executa comandos nem verifica a instalação do Docker**.

O MVP é voltado a um único servidor confiável, mas a tabela e os endpoints permitem cadastrar mais de um registro. Isso não representa suporte implementado a deployments multi-host.

## Organização do módulo

```text
server/
├── ServerService.java
├── ServerConnectionService.java
├── ServerResponse.java
├── ServerAlreadyExistsException.java
├── ServerNotFoundException.java
├── ServerConnectionException.java
└── internal/
    ├── Server.java
    ├── ServerRepository.java
    ├── ServerController.java
    ├── CreateServerRequest.java
    ├── ServerExceptionHandler.java
    └── ssh/
        ├── SshClientConfiguration.java
        └── MinaSshClient.java
```

O Spring Modulith identifica `server` como um módulo a partir do pacote. `ServerService`, `ServerConnectionService` e `ServerResponse` ficam na raiz como contratos do módulo. A entidade JPA, o repositório, o controller, o DTO de entrada, o tratamento HTTP e a implementação específica do Apache MINA permanecem em `internal/`.

`ServerResponse` é um `record` separado da entidade JPA. A conversão é feita pelo método privado `toResponse` de `ServerService`. `ServerConnectionService` usa esse DTO para localizar o destino e delegar a autenticação ao `MinaSshClient`, sem precisar acessar diretamente o repositório. Nesta etapa, o módulo não depende de `application` nem de `image`.

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

A tabela não guarda senhas, chaves privadas SSH, fingerprints de host, informações de hardware, estado online/offline ou vínculos com deployments. As credenciais do cliente SSH e as chaves confiáveis dos servidores são configuradas em arquivos externos ao banco de dados.

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

### `POST /api/servers/{id}/test-connection`

Solicita uma conexão SSH autenticada com o servidor identificado pelo UUID. O backend consulta o cadastro, estabelece a conexão, verifica a identidade do host por `known_hosts` e tenta autenticar usando a chave privada configurada para o Rivet.

- **`204 No Content`**: a sessão SSH foi autenticada com sucesso; não há corpo na resposta.
- **`404 Not Found`**: o servidor não existe no banco.
- **`502 Bad Gateway`**: falha na conexão, validação de host key ou autenticação SSH.
- **`401 Unauthorized`**: requisição não autenticada, fora do perfil `dev`.
- **`403 Forbidden`**: CSRF ausente ou inválido em requisição autenticada, fora do perfil `dev`.

Esse endpoint **não executa comandos remotos** e não confirma se o Docker está instalado ou funcional no destino. É uma operação disparada sob demanda, não um monitoramento contínuo de disponibilidade.

### Tratamento de erros

`ServerExceptionHandler` trata as exceções de domínio e retorna `ProblemDetail`:

| Situação | Resposta |
| --- | --- |
| Dados inválidos no cadastro | `400 Bad Request` |
| Servidor não encontrado | `404 Not Found` |
| Nome de servidor duplicado | `409 Conflict` |
| UUID malformado na URL | `400 Bad Request` |
| Falha de conexão/autenticação SSH | `502 Bad Gateway` |

Para nomes duplicados, existem duas proteções:

1. `ServerService.register` chama `ServerRepository.existsByName` para rejeitar duplicações comuns.
2. A constraint `uk_servers_name` no PostgreSQL garante a unicidade mesmo com requisições concorrentes.

Quando o banco rejeita a inserção, o handler identifica uma `DataIntegrityViolationException` que contenha uma `ConstraintViolationException` do Hibernate com SQLSTATE `23505` e constraint `uk_servers_name`, convertendo o erro em `409 Conflict`. Outras violações de integridade não são classificadas automaticamente como duplicidade.

Falhas de I/O da conexão SSH são encapsuladas em `ServerConnectionException` e convertidas em `502 Bad Gateway`. A resposta apresenta uma mensagem genérica e não envia os detalhes técnicos ao consumidor da API.

O `@RestControllerAdvice` do módulo usa `basePackageClasses = ServerController.class`, limitando sua aplicação aos controllers desse pacote. O `ApplicationExceptionHandler` também possui escopo próprio, evitando que ambos tentem interpretar a mesma exceção de integridade de módulos diferentes.

## Fluxo de cadastro

1. `ServerController` recebe o JSON e aplica `@Valid` a `CreateServerRequest`.
2. `ServerService.register`, em uma transação, verifica se o nome já existe.
3. O serviço instancia `Server` e o persiste com `ServerRepository.save`.
4. `toResponse` converte a entidade para `ServerResponse`.
5. O controller responde com `201 Created` e o header `Location`.

O cadastro não inicia processos de conexão em segundo plano e não consulta o Docker do host informado.

## Integração SSH

A conexão SSH é implementada com **Apache MINA SSHD**. O backend atua como cliente SSH; o servidor Linux de destino deve estar executando um servidor SSH (por exemplo, OpenSSH), permitir autenticação por chave pública e conter a chave pública correspondente no `authorized_keys` do usuário remoto.

### Configuração externa

A `.env.example` disponibiliza duas variáveis de configuração:

```dotenv
# SSH
RIVET_SSH_PRIVATE_KEY_PATH=/caminho/absoluto/para/chave_privada
RIVET_SSH_KNOWN_HOSTS_PATH=/caminho/absoluto/para/known_hosts
```

Esses valores são **placeholders** e devem ser substituídos no `.env` local por caminhos válidos, sem versionar segredos ou depender de caminhos específicos do computador de desenvolvimento.

| Propriedade Spring | Variável no `.env` | Comportamento |
| --- | --- | --- |
| `rivet.ssh.private-key-path` | `RIVET_SSH_PRIVATE_KEY_PATH` | Caminho da chave privada usada para autenticação. Não há caminho de chave privada padrão. |
| `rivet.ssh.known-hosts-path` | `RIVET_SSH_KNOWN_HOSTS_PATH` | Caminho do arquivo de host keys confiáveis. Se a variável não for informada, a configuração usa `${user.home}/.ssh/known_hosts`. |

O fallback é apenas para o **caminho do arquivo `known_hosts`**. Ele não autoriza hosts desconhecidos. Se a variável for preenchida com um placeholder literal, esse placeholder é usado como caminho e **não** aciona o fallback.

Na versão implementada, a chave Ed25519 dedicada ao Rivet é carregada do disco por `FileKeyPairProvider`. As dependências criptográficas necessárias ao carregamento desse formato foram adicionadas ao backend. A chave privada deve ser legível somente pelo usuário do processo e não deve ser registrada em logs, incluída no Git ou armazenada em texto puro no PostgreSQL.

### Cliente e autenticação

- `SshClientConfiguration`: prepara um `SshClient` reutilizável, inicializado e encerrado pelo ciclo de vida do Spring. Configura o arquivo `known_hosts`, a identidade de autenticação e desabilita resolução automática de configurações de host do SSH local.
- `KnownHostsServerKeyVerifier`, combinado com `RejectAllServerKeyVerifier`: aceita chaves de hosts previamente confiáveis e rejeita hosts desconhecidos ou com chaves divergentes. A fingerprint precisa ser conferida por um canal confiável antes de cadastrar uma nova host key.
- `MinaSshClient`: abre uma sessão usando `sshUsername`, `host` e `sshPort`, com timeout de **10 segundos para conexão** e **10 segundos para autenticação**. A sessão é fechada ao terminar, inclusive quando ocorre erro.
- `ServerConnectionService`: localiza o servidor por UUID usando `ServerService.findById`, solicita a verificação SSH e traduz falhas de I/O em `ServerConnectionException`.

### Fluxo de teste de conectividade

1. `ServerController` recebe `POST /api/servers/{id}/test-connection`.
2. `ServerConnectionService` consulta `ServerService.findById` e obtém `ServerResponse`.
3. `MinaSshClient` estabelece a conexão usando o `SshClient` configurado.
4. O MINA valida a host key contra o arquivo `known_hosts` e autentica com a chave privada configurada.
5. Em caso de sucesso, a sessão é encerrada e o controller retorna `204 No Content`. Em caso de falha SSH, retorna `502 Bad Gateway`, sem detalhes internos.

A conexão SSH real foi validada contra um **Dell com Ubuntu Server**, e o endpoint respondeu `204 No Content`. Isso comprova a conectividade e a autenticação nesse ambiente, não a capacidade de executar deployments.

## Testes

`ServerControllerIntegrationTest` utiliza Spring Boot, MockMvc, o perfil `test` e PostgreSQL via Testcontainers. Os testes cobrem:

- Cadastro válido com `201 Created`, corpo JSON e header `Location`.
- Validação de nome, host, porta e usuário SSH, inclusive limites e campos ausentes.
- Nome duplicado com `409 Conflict`.
- Cadastro concorrente do mesmo nome, verificando uma resposta `201`, outra `409` e apenas um registro persistido.
- Consulta por UUID existente, inexistente e malformado.
- Listagem de servidores e lista vazia.

A implementação SSH é coberta também por:

- `ServerConnectionServiceTest`: delegação ao cliente, servidor inexistente e encapsulamento de falhas de I/O.
- `ServerConnectionControllerIntegrationTest`: respostas `204`, `404` e `502`, proteção de autenticação/CSRF e ausência de vazamento de detalhes de SSH.
- `MinaSshClientTest`: conexão e autenticação, propagação de falhas e fechamento da sessão.
- `SshHostKeyVerifierTest`: aceitação de host conhecido e rejeição de chave divergente ou host desconhecido.

Os testes de unidade e de verificação de host key não se conectam ao Dell; os testes HTTP usam mocks do transporte SSH e PostgreSQL via Testcontainers. Os testes dessa etapa foram executados pelo desenvolvedor com resultado positivo. Um teste contra servidor SSH embarcado ainda pode ampliar a cobertura de integração ponta a ponta.

O teste `RivetModularityTests` verifica os limites entre os módulos do projeto. Para executar os testes de integração localmente é necessário um daemon Docker acessível ao Testcontainers.

## Segurança e limites atuais

Fora do perfil `dev`, a autenticação administrativa por sessão HTTP e a proteção CSRF também se aplicam ao `POST /api/servers/{id}/test-connection`. A configuração `DevSecurityConfig` libera requisições e desabilita CSRF em `dev` para facilitar o desenvolvimento local. **Esse perfil não deve ser exposto à internet ou a redes não confiáveis.** CORS não substitui autenticação e autorização.

A conexão SSH valida a identidade do servidor por `known_hosts` e usa uma chave privada externa, sem armazená-la no banco. A resposta HTTP não revela as mensagens internas de erro do SSH. Para a integração atual, a proteção da chave em repouso e do usuário remoto depende da configuração e das permissões do sistema operacional.

A implementação atual **não oferece**:

- Gerenciamento de múltiplas identidades SSH, rotação de chaves ou gerenciamento de passphrases pela API.
- Política de autorização/restrição de destinos SSH (por exemplo, allowlist de hosts/redes); cadastros e testes de conexão não devem ficar expostos a usuários não confiáveis.
- Detecção de sistema operacional, arquitetura, versão do Docker ou disponibilidade operacional contínua do servidor.
- Execução de comandos remotos, transferência de imagens, deployments, gerenciamento de contêineres ou coleta de métricas.
- Atualização e exclusão de servidores por API.

**Ponto de atenção:** um destino cadastrado pode indicar um endereço arbitrário. Mesmo com host key verificada, o teste de conexão pode gerar tráfego de rede de saída para esse destino. Antes de ampliar o acesso administrativo ou incluir operações privilegiadas, é necessário definir uma política de destinos autorizados e restringir os privilégios da conta SSH remota.

## Próximas etapas

A próxima evolução do módulo `server` é realizar consultas remotas **controladas e pré-definidas**, começando pela verificação de `docker version`, sem oferecer execução arbitrária de comandos pela API. Depois, poderão ser adicionadas verificações de disponibilidade e capacidades do host.

O futuro módulo `deployment` poderá consumir o contrato público de `server` para selecionar o destino de uma implantação, sem acessar diretamente a entidade ou o repositório interno. A transferência de imagens, a execução e o histórico das implantações pertencerão ao fluxo de `deployment`, e não ao cadastro de servidores.
