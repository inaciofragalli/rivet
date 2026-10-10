# Rivet

O Rivet é uma plataforma para implantar e gerenciar aplicações em infraestrutura própria. A ideia é centralizar tarefas que hoje seriam feitas com Docker e SSH, como cadastrar aplicações e servidores, implantar versões e acompanhar o estado das implantações.

A primeira versão foi pensada para hospedagem pessoal em um servidor Linux modesto. Integrações com GitHub, builds automáticos, suporte a vários servidores e observabilidade mais avançada ficam para etapas posteriores.

> **Estado atual:** já é possível cadastrar aplicações e consultá-las pela API. Cadastro de servidores, operações SSH, execução de containers, histórico de deployments e autenticação administrativa definitiva ainda não foram implementados.

## Repositórios

O backend e o painel web ficam em repositórios separados:

- [`rivet`](https://github.com/inaciofragalli/rivet): este repositório, com a API e a futura lógica de gerenciamento da infraestrutura.
- [`rivet-frontend`](https://github.com/inaciofragalli/rivet-frontend): painel web em React e TypeScript.

## Tecnologias

- Java 21, Spring Boot 4.1.1 e Gradle Kotlin DSL
- Spring Web MVC, Spring Security e Bean Validation
- Spring Data JPA, PostgreSQL e Flyway
- Spring Modulith (monólito modular)
- springdoc-openapi (OpenAPI e Swagger UI)
- JUnit, MockMvc e Testcontainers para testes
- GitHub Actions para CI e Qodana para análise estática

O primeiro módulo de negócio, `application`, fica em `io.github.inaciofragalli.rivet.application`. Os módulos de servidores e deployments ainda serão desenvolvidos. Os detalhes do módulo estão em [application.md](docs/architecture/modules/application.md); o histórico da decisão sobre persistência de eventos está no [ADR 0001](docs/adr/0001-modulith-jpa.md).

## O que já funciona

| Método | Rota | Comportamento |
| --- | --- | --- |
| `POST` | `/api/applications` | Registra aplicação; retorna `201 Created` e `Location` |
| `GET` | `/api/applications/{id}` | Busca por UUID; retorna `200 OK` ou `404 Not Found` |
| `GET` | `/api/applications` | Lista aplicações; retorna `200 OK` (inclusive lista vazia) |

No cadastro são informados `name`, `image` e `containerPort`. O nome deve ser único, ter no máximo 30 caracteres e começar com letra ou número. Depois do primeiro caractere, aceita também `_`, `.` e `-`. A imagem é obrigatória, e a porta deve estar entre 1 e 65535. Dados inválidos retornam `400 Bad Request`; nomes já cadastrados retornam `409 Conflict`.

Por enquanto, a API guarda a configuração da aplicação. Ela ainda não inicia containers nem registra deployments. As respostas de erro seguem o formato *Problem Details*.

## Objetivo do MVP

O MVP deve permitir que um administrador implante uma aplicação HTTP em um servidor Linux próprio, usando uma imagem Docker já construída. O Rivet ficará responsável pelas operações que, sem a ferramenta, precisariam ser feitas manualmente.

O fluxo planejado é:

1. Preparar uma imagem Docker fora do servidor de destino.
2. Cadastrar a aplicação e o servidor no Rivet.
3. Solicitar uma implantação pela API ou pelo painel.
4. Transferir ou disponibilizar a imagem e iniciar o container por SSH, com autenticação por chave e verificação da *host key*.
5. Verificar o resultado, registrar estado e erros e permitir consultar o histórico.
6. Expor a aplicação por HTTPS quando desejado; inicialmente, a configuração de domínio/túnel poderá ser manual.

O painel administrativo deverá continuar privado, inclusive quando as aplicações implantadas estiverem acessíveis pela internet.

Ficam fora do MVP os builds automáticos a partir de repositórios GitHub, Kubernetes, múltiplos nós, equipes, cobrança e orquestração distribuída.

## Segurança

Existe uma configuração temporária para o perfil `dev` que permite todas as requisições e desabilita CSRF. Ela serve para desenvolvimento local. **Não use esse perfil em produção nem em ambientes expostos.**

A autenticação administrativa e a forma de guardar credenciais SSH ainda precisam ser definidas e implementadas. O planejamento prevê que chaves privadas, tokens e senhas não sejam versionados nem apareçam em logs. As conexões SSH também deverão verificar a identidade do servidor remoto.

## Desenvolvimento local

### Pré-requisitos

- JDK 21
- PostgreSQL local para **executar a aplicação**
- Docker instalado e daemon acessível para **executar os testes de integração com Testcontainers**
- Git

O repositório já inclui o Gradle Wrapper, então não é necessário instalar o Gradle separadamente.

### Banco de dados e configuração

Crie um banco PostgreSQL e um usuário com acesso a ele. O Flyway controla as migrações do esquema; o Hibernate fica com `spring.jpa.hibernate.ddl-auto=validate` e não cria as tabelas automaticamente.

Na raiz do projeto:

```bash
cp .env.example .env
```

Exemplo de configuração local:

```dotenv
DB_HOST=localhost
DB_PORT=5432
DB_NAME=rivet
DB_USERNAME=rivet
DB_PASSWORD=defina_sua_senha_local
```

As variáveis de conexão são obrigatórias e não têm valores padrão. O arquivo `.env` é ignorado pelo Git e não deve ser versionado com credenciais. O `application.properties` já faz a importação opcional a partir da raiz do projeto:

```properties
spring.config.import=optional:file:./.env[.properties]
```

### Executar

Para desenvolvimento local, usando a configuração de segurança exclusiva do perfil `dev`:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

Por padrão, a API fica em `http://localhost:8080`. O springdoc disponibiliza OpenAPI e Swagger UI nos caminhos padrão, que devem ser conferidos ao executar a aplicação caso haja mudanças na configuração.

Sem o perfil `dev`, vale a configuração restante do Spring Security. A autenticação administrativa própria do Rivet ainda está pendente.

### Documentação da API

Com o backend em execução, a documentação da API fica disponível em:

- [Swagger UI](http://localhost:8080/swagger-ui/index.html): interface para consultar e testar os endpoints.
- [OpenAPI JSON](http://localhost:8080/v3/api-docs): especificação OpenAPI gerada pelo Springdoc.

O Springdoc gera a documentação a partir dos controllers e modelos da aplicação. Atualmente, o módulo `application` disponibiliza endpoints para cadastrar, consultar e listar aplicações.

### Testar

Com Docker em execução:

```bash
./gradlew test --no-daemon
./gradlew build
```

Os testes de integração usam PostgreSQL 17 via Testcontainers e `@ServiceConnection`, com um banco separado do ambiente de desenvolvimento. No CI, não é preciso configurar um serviço PostgreSQL à parte nem fornecer segredos `DB_*`. A análise do Qodana roda em outro workflow.

## Estrutura do repositório

```text
rivet/
├── .github/workflows/             # CI e Qodana
├── docs/
│   ├── architecture/modules/      # Documentação de módulos implementados
│   └── adr/                       # Registros de decisões arquiteturais
├── src/
│   ├── main/
│   │   ├── java/io/github/inaciofragalli/rivet/
│   │   │   ├── application/       # Módulo application e pacote internal/
│   │   │   └── security/          # Configuração de segurança de desenvolvimento
│   │   └── resources/db/migration/ # Migrações Flyway
│   └── test/                     # Testes unitários, integração e modularidade
├── .env.example
├── build.gradle.kts
└── settings.gradle.kts
```

## Próximas etapas

1. Implementar cadastro e gerenciamento de servidores, preparando a integração SSH.
2. Implementar deployments de imagens prontas com verificação do estado real do container.
3. Integrar as operações com o painel React/TypeScript.
4. Validar um deployment real no servidor doméstico, com administração privada e publicação HTTPS.

Depois do MVP, estão previstas como possibilidades a integração com GitHub, builds automatizados, atualização/rollback, stacks Docker Compose e suporte a mais servidores.