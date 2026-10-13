
<div align="center">

# Rivet

### Plataforma self-hosted para gerenciar aplicações em infraestrutura própria

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)

![Repo size](https://img.shields.io/github/repo-size/inaciofragalli/rivet?style=flat-square)
![Last commit](https://img.shields.io/github/last-commit/inaciofragalli/rivet?style=flat-square)
![Issues](https://img.shields.io/github/issues/inaciofragalli/rivet?style=flat-square)

[Funcionalidades](#funcionalidades-disponíveis) •
[MVP](#objetivo-do-mvp) •
[Segurança](#segurança-e-limitações-atuais) •
[Desenvolvimento](#desenvolvimento-local) •
[Documentação](#documentação) •
[Roadmap](#próximas-etapas)

</div>

---

O Rivet é um projeto de plataforma self-hosted para implantar e gerenciar aplicações em infraestrutura própria. A proposta é centralizar tarefas que, sem a ferramenta, seriam feitas com Docker e SSH: cadastrar aplicações, registrar servidores, implantar versões e acompanhar deployments.

O MVP está sendo desenvolvido para um servidor Linux modesto, com foco em aprendizado e evolução incremental. Integrações com GitHub, builds automáticos, múltiplos servidores e observabilidade avançada ficam para etapas futuras.

> **Estado atual:** o backend já cadastra e consulta aplicações, aceita upload de arquivos de imagem Docker (`docker save`) e vincula um artefato enviado a uma aplicação. **Ainda não implanta imagens nem inicia contêineres.** Cadastro de servidores, SSH, deployments, histórico de execução e autenticação administrativa definitiva continuam pendentes.

## Repositórios e tecnologias

- **Backend:** [`rivet`](https://github.com/inaciofragalli/rivet) — Java 21, Spring Boot 4.1.1, Gradle Kotlin DSL, Spring MVC/Security/Validation, Spring Data JPA, PostgreSQL, Flyway e Spring Modulith.
- **Frontend:** [`rivet-frontend`](https://github.com/inaciofragalli/rivet-frontend) — painel React + TypeScript, desenvolvido separadamente.
- **Testes e qualidade:** JUnit, MockMvc, Testcontainers, GitHub Actions e Qodana.
- **Documentação da API:** springdoc-openapi e Swagger UI.

A organização do backend segue um monólito modular. Os módulos implementados até o momento são [`application`](docs/architecture/modules/application.md) e [`image`](docs/architecture/modules/image.md). As decisões arquiteturais estão registradas em [`docs/adr/`](docs/adr/).

## Funcionalidades disponíveis

| Método | Endpoint | Comportamento |
| --- | --- | --- |
| `POST` | `/api/image-artifacts` | Recebe TAR/TAR.GZ de `docker save`, armazena e retorna metadados; `201 Created` |
| `POST` | `/api/applications` | Cadastra aplicação por referência Docker ou UUID de artefato; `201 Created` e `Location` |
| `GET` | `/api/applications/{id}` | Consulta aplicação por UUID; `200 OK` ou `404 Not Found` |
| `GET` | `/api/applications` | Lista aplicações, incluindo lista vazia (`[]`); `200 OK` |

**Upload de imagens:** o arquivo deve conter um manifesto válido de uma imagem Docker. O backend extrai nome, tag, sistema operacional, arquitetura e portas TCP declaradas; calcula SHA-256; e registra o artefato em PostgreSQL. O arquivo original é mantido em `RIVET_IMAGE_STORAGE_DIR`, sem ser carregado no daemon Docker.

**Cadastro de aplicações:** requer `name`, `containerPort` e **uma** fonte de imagem: `image` (referência Docker manual) **ou** `imageArtifactId` (UUID de upload já feito). A resposta passa a incluir `imageArtifactId`, que será `null` no cadastro manual. O nome é único e tem no máximo 30 caracteres; `containerPort` fica entre 1 e 65535.

As respostas de erro tratadas usam [Problem Details](https://www.rfc-editor.org/rfc/rfc9457). Não existem, por enquanto, endpoints públicos para listar, baixar ou excluir artefatos de imagem.

**Guia de ponta a ponta:** [Upload de imagens Docker e cadastro de aplicações](docs/guides/image-upload.md).

## Exemplo rápido: imagem pronta → aplicação cadastrada

O exemplo pressupõe uma instância de desenvolvimento local do Rivet em `http://localhost:8080`.

```bash
docker pull nginx:alpine
docker save -o nginx.tar nginx:alpine
curl -i -F 'file=@nginx.tar' http://localhost:8080/api/image-artifacts
```

O upload retorna `201 Created` e um `id` UUID. Use esse UUID na próxima requisição:

```bash
curl -i -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "nginx-demo",
    "imageArtifactId": "<uuid-retornado-pelo-upload>",
    "containerPort": 80
  }'
```

Substitua `<uuid-retornado-pelo-upload>` pelo UUID recebido (a string com os sinais `<` e `>` é apenas um marcador de exemplo).

Também é possível cadastrar uma aplicação sem fazer upload:

```json
{
  "name": "taskhub",
  "image": "ghcr.io/exemplo/taskhub:v1",
  "containerPort": 8080
}
```

**Nenhum desses cadastros executa contêineres.** O próximo fluxo de produto será a integração do frontend com a API existente; a execução remota será implementada depois.

## Objetivo do MVP

O MVP deverá permitir que um administrador implante uma aplicação HTTP em seu próprio servidor Linux, usando uma imagem Docker já construída.

Fluxo planejado (as etapas de deployment abaixo **ainda não foram implementadas**):

1. Construir ou obter uma imagem Docker fora do servidor de destino.
2. Cadastrar a aplicação e registrar um servidor no Rivet.
3. Solicitar a implantação pelo painel ou pela API.
4. Transferir/disponibilizar a imagem e iniciar o contêiner por SSH, com autenticação por chave e verificação da identidade do servidor (*host key*).
5. Verificar o resultado, registrar estado/erros e permitir consultar o histórico.
6. Publicar o serviço por HTTPS quando necessário; configuração de domínio/túnel poderá ser manual inicialmente.

O painel administrativo deverá permanecer privado, mesmo quando as aplicações implantadas estiverem acessíveis publicamente.

Ficam fora do MVP: builds automáticos a partir de repositórios GitHub, Kubernetes, múltiplos nós, equipes, cobrança e orquestração distribuída.

## Segurança e limitações atuais

- O perfil **`dev`** libera todas as requisições e desabilita CSRF; existe exclusivamente para desenvolvimento local. **Não exponha a API com esse perfil à internet ou a uma rede não confiável.**
- O CORS do perfil `dev` é aplicado a `/api/**`, com origem configurável por `RIVET_CORS_ALLOWED_ORIGIN` (padrão: `http://localhost:5173`). CORS não equivale a autenticação.
- O upload limita o arquivo recebido a **512 MiB**; a inspeção limita o conteúdo descompactado a **2 GiB** e cada arquivo de metadados a **1 MiB**. Esses limites não tornam uploads arbitrários seguros para exposição pública.
- A autenticação administrativa, a autorização de upload, a guarda de chaves SSH e a validação da identidade do servidor remoto ainda dependem de implementação.
- O backend não inspeciona vulnerabilidades das camadas da imagem, não executa `docker load` nem publica portas no host.
- O diretório de armazenamento de imagens precisa ser protegido pelo sistema operacional e contar com espaço suficiente. Não há exclusão pública, retenção automática de arquivos definitivos ou limpeza completa de órfãos.

Consulte [a documentação do módulo `image`](docs/architecture/modules/image.md) para os limites e decisões detalhados.

## Desenvolvimento local

### Pré-requisitos

- JDK 21.
- PostgreSQL local para **executar o backend**.
- Docker instalado e daemon acessível para **executar os testes de integração via Testcontainers** e para gerar imagens com `docker save` nos exemplos.
- Git.

O projeto inclui o Gradle Wrapper; não é necessário instalar Gradle globalmente.

### Configurar banco e armazenamento

Crie o banco PostgreSQL e um usuário com acesso a ele. O Flyway gerencia o esquema e o Hibernate valida o mapeamento com `spring.jpa.hibernate.ddl-auto=validate`.

Na raiz do repositório:

```bash
cp .env.example .env
```


O arquivo `.env.example` define os campos que precisam ser configurados. Exemplo de `.env` **local**, com senha ilustrativa:

```dotenv
DB_HOST=localhost
DB_PORT=5432
DB_NAME=rivet
DB_USERNAME=rivet
DB_PASSWORD=defina_sua_senha_local

RIVET_IMAGE_STORAGE_DIR=./data/images
RIVET_CORS_ALLOWED_ORIGIN=http://localhost:5173
```

As variáveis de conexão com o PostgreSQL e `RIVET_IMAGE_STORAGE_DIR` são obrigatórias. `RIVET_CORS_ALLOWED_ORIGIN` é opcional e utiliza `http://localhost:5173` como padrão, permitindo configurar a origem do frontend durante o desenvolvimento.

A importação opcional do arquivo ocorre pela propriedade:

```properties
spring.config.import=optional:file:./.env[.properties]
```

Como o caminho é relativo, execute o backend a partir da raiz do repositório ou forneça as variáveis por outro mecanismo de configuração.

### Executar

Para desenvolvimento local:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

Por padrão, o backend responde em `http://localhost:8080`. Sem `dev`, vale a configuração padrão restante do Spring Security; a autenticação administrativa própria do Rivet ainda não foi construída.

### Swagger/OpenAPI

Com o backend em execução:

- [Swagger UI](http://localhost:8080/swagger-ui/index.html)
- [OpenAPI JSON](http://localhost:8080/v3/api-docs)

O springdoc gera a documentação a partir dos controllers e modelos atuais dos módulos `application` e `image`.

### Testes

Com o daemon Docker acessível:

```bash
./gradlew test --no-daemon
./gradlew build
```

Os testes de integração usam PostgreSQL 17 via Testcontainers e `@ServiceConnection`, com um banco separado do ambiente de desenvolvimento. No CI não é preciso disponibilizar um serviço PostgreSQL separado nem segredos `DB_*`. A análise do Qodana roda em outro workflow.

## Documentação

| Documento | Conteúdo |
| --- | --- |
| [Módulo `application`](docs/architecture/modules/application.md) | Cadastro, validação, persistência, endpoints e integração com imagens |
| [Módulo `image`](docs/architecture/modules/image.md) | Inspeção TAR/GZIP, armazenamento, limites, persistência e segurança |
| [Guia de upload](docs/guides/image-upload.md) | Fluxo prático `docker save` → upload → cadastro |
| [ADR 0001](docs/adr/0001-modulith-jpa.md) | Decisão de adiar persistência de eventos do Spring Modulith via JPA |

## Estrutura do repositório

```text
rivet/
├── .github/workflows/                  # CI e Qodana
├── docs/
│   ├── adr/                            # Decisões arquiteturais
│   ├── architecture/modules/           # Documentação dos módulos implementados
│   └── guides/                         # Guias de uso
├── src/
│   ├── main/
│   │   ├── java/io/github/inaciofragalli/rivet/
│   │   │   ├── application/            # Cadastro/consulta de aplicações
│   │   │   ├── image/                  # Upload e registro de artefatos Docker
│   │   │   ├── server/                 # Cadastro/consulta de servidores
│   │   │   └── security/               # Configuração de segurança de desenvolvimento
│   │   └── resources/db/migration/     # Migrações Flyway
│   └── test/                           # Testes unitários, integração e modularidade
├── .env.example
├── build.gradle.kts
└── settings.gradle.kts
```

## Próximas etapas

1. Integrar upload, visualização de metadados e cadastro de aplicações no painel React/TypeScript.
2. Implementar cadastro e gerenciamento de servidores, preparando a integração SSH.
3. Implementar deployments de imagens prontas com verificação do estado real do contêiner.
4. Validar um deployment real no servidor doméstico, com administração privada e publicação HTTPS.

Após o MVP poderão ser avaliados integração com GitHub, builds automatizados, atualização/rollback, stacks Docker Compose e suporte a mais servidores.