<div align="center">

# Rivet

### Plataforma self-hosted para gerenciar aplicações em infraestrutura própria

![Java](https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white)
![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white)

[![CI](https://github.com/inaciofragalli/rivet/actions/workflows/ci.yml/badge.svg)](https://github.com/inaciofragalli/rivet/actions/workflows/ci.yml)
![Repo size](https://img.shields.io/github/repo-size/inaciofragalli/rivet?style=flat-square)
![Last commit](https://img.shields.io/github/last-commit/inaciofragalli/rivet?style=flat-square)
[![Qodana](https://github.com/inaciofragalli/rivet/actions/workflows/qodana_code_quality.yml/badge.svg)](https://github.com/inaciofragalli/rivet/actions/workflows/qodana_code_quality.yml)

[Funcionalidades](#funcionalidades-disponíveis) •
[MVP](#objetivo-do-mvp) •
[Segurança](#segurança-e-limitações-atuais) •
[Desenvolvimento](#desenvolvimento-local) •
[Documentação](#documentação) •
[Roadmap](#próximas-etapas)

</div>

---

> **English summary:** Rivet is a self-hosted control plane for deploying and managing Docker applications on infrastructure you own. Its MVP focuses on a private admin panel, prebuilt Docker images, and a trusted Linux host; actual SSH-based deployment is still under development.

O Rivet é um projeto de plataforma self-hosted para implantar e gerenciar aplicações em infraestrutura própria. Sua proposta é centralizar tarefas que seriam feitas manualmente com Docker e SSH: cadastrar aplicações, registrar servidores, implantar versões e acompanhar deployments.

O MVP é desenvolvido para um servidor Linux modesto, com foco em aprendizado e evolução incremental. Integrações com GitHub, builds automáticos, múltiplos nós e observabilidade avançada ficam para etapas futuras.

> **Estado da versão documentada (`feat/auth`):** o backend cadastra e consulta aplicações e servidores, recebe arquivos de imagem Docker (`docker save`), armazena metadados e permite autenticação administrativa por sessão HTTP. O frontend já oferece upload, cadastro e listagem de aplicações. **Ainda não existe conexão SSH, `docker load` remoto, implantação, execução ou monitoramento de contêineres pelo Rivet.** A funcionalidade de autenticação está implementada na branch de trabalho e permanece sujeita à revisão da PR antes de integrar à `main`.

## Repositórios e tecnologias

- **Backend:** [`rivet`](https://github.com/inaciofragalli/rivet) — Java 21, Spring Boot 4.1.1, Gradle Kotlin DSL, Spring MVC/Security/Validation, Spring Data JPA, PostgreSQL, Flyway e Spring Modulith 2.1.1.
- **Frontend:** [`rivet-frontend`](https://github.com/inaciofragalli/rivet-frontend) — React, TypeScript, Vite e Axios, em repositório separado.
- **Testes e qualidade:** JUnit 5, MockMvc, Spring Security Test, Testcontainers, GitHub Actions, Qodana, CodeQL e Dependabot.
- **Documentação da API:** springdoc-openapi / Swagger UI.

O backend é um monólito modular. Os módulos com implementação nesta etapa são [`application`](docs/architecture/modules/application.md), [`image`](docs/architecture/modules/image.md), [`server`](docs/architecture/modules/server.md), [`user`](docs/architecture/modules/user.md) e [`security`](docs/architecture/modules/security.md). A [visão de arquitetura](docs/architecture/overview.md) descreve as fronteiras e os componentes planejados.

## Funcionalidades disponíveis

| Método | Endpoint | Comportamento |
| --- | --- | --- |
| `POST` | `/api/image-artifacts` | Recebe TAR/TAR.GZ de `docker save`, armazena arquivo e metadados; `201 Created` |
| `POST` | `/api/applications` | Cadastra aplicação por referência Docker ou UUID de artefato; `201 Created` |
| `GET` | `/api/applications` | Lista aplicações, inclusive lista vazia (`[]`); `200 OK` |
| `GET` | `/api/applications/{id}` | Consulta aplicação por UUID; `200 OK` ou `404 Not Found` |
| `POST` | `/api/servers` | Cadastra nome, host, porta e usuário SSH; `201 Created` |
| `GET` | `/api/servers` | Lista servidores cadastrados; `200 OK` |
| `GET` | `/api/servers/{id}` | Consulta servidor por UUID; `200 OK` ou `404 Not Found` |
| `GET` | `/api/auth/csrf` | Entrega token CSRF associado à sessão; `200 OK` |
| `POST` | `/api/auth/login` | Autentica email/senha via formulário e sessão HTTP; `204 No Content` |
| `GET` | `/api/auth/me` | Retorna email autenticado; `200 OK`, ou `401` sem login |
| `POST` | `/api/auth/logout` | Invalida a sessão autenticada; `204 No Content` |

As quatro rotas de autenticação acima pertencem à implementação em `feat/auth`; estão sujeitas à revisão final antes de seu merge. Login e logout são processados pelos filtros do Spring Security, não por métodos correspondentes no `AuthController`.

**Imagens e aplicações:** o upload recebe um arquivo de `docker save`, extrai nome, tag, arquitetura, SO e portas TCP declaradas, calcula SHA-256, armazena o arquivo localmente e persiste os metadados no PostgreSQL. O cadastro de aplicação exige `name`, `containerPort` e **exatamente uma** origem entre `image` e `imageArtifactId`. Nenhuma dessas operações inicia um contêiner.

**Servidores:** o cadastro registra `name`, `host`, `sshPort` e `sshUsername`. Não estabelece conexão, não verifica host key e não confirma disponibilidade ou presença do Docker.

**Segurança:** fora do perfil `dev`, endpoints administrativos exigem sessão autenticada; requisições que alteram estado são protegidas por CSRF. Não há registro público de usuários. O acesso ao painel deverá permanecer privado (Tailscale ou rede privada equivalente).

Erros de domínio tratados são retornados usando [Problem Details](https://www.rfc-editor.org/rfc/rfc9457) quando aplicável. Para detalhes, consulte a documentação específica de cada módulo.

## Exemplo rápido: imagem pronta → aplicação cadastrada

> Este exemplo é destinado **apenas ao ambiente local com perfil `dev`**, no qual a autenticação e a proteção CSRF são desabilitadas. Não exponha esse perfil à internet.

```bash
docker pull nginx:alpine
docker save -o nginx.tar nginx:alpine
curl -i -F 'file=@nginx.tar' http://localhost:8080/api/image-artifacts
```

O upload retorna `201 Created` e um `id` UUID. Use o identificador recebido:

```bash
curl -i -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "nginx-demo",
    "imageArtifactId": "<uuid-retornado-pelo-upload>",
    "containerPort": 80
  }'
```

Também é possível cadastrar uma referência Docker manualmente, sem upload:

```json
{
  "name": "taskhub",
  "image": "ghcr.io/exemplo/taskhub:v1",
  "containerPort": 8080
}
```

Para executar os mesmos comandos no perfil protegido, primeiro autentique-se e inclua o cookie de sessão e o header CSRF, conforme o [guia de autenticação](docs/guides/authentication.md). **Cadastro não equivale a deployment.**

## Objetivo do MVP

Um administrador deverá conseguir enviar uma imagem Docker previamente construída, implantá-la por meio do Rivet em um Ubuntu confiável, observar o resultado, consultar estado e logs do contêiner e acessar a aplicação por um hostname HTTPS configurado separadamente.

Fluxo de implantação **ainda planejado**:

1. Construir ou obter uma imagem Docker fora do servidor de destino.
2. Cadastrar a aplicação e o servidor no Rivet (cadastros já disponíveis).
3. Solicitar uma implantação durável pelo painel ou pela API.
4. Transferir/importar a imagem via SSH, com chave de autenticação e validação da identidade do host.
5. Iniciar ou substituir o contêiner, inspecionar o resultado e registrar estados e falhas.
6. Publicar a aplicação deliberadamente via HTTPS; configuração de domínio e túnel poderá ser manual inicialmente.

Ficam fora do MVP: builds automáticos de Dockerfile, Kubernetes, agentes remotos, equipes, cobrança e orquestração distribuída. O plano administrativo deverá permanecer privado mesmo quando as aplicações hospedadas forem públicas.

## Segurança e limitações atuais

- O perfil **`dev`** libera as requisições e desabilita CSRF para desenvolvimento local. **Nunca o exponha à internet ou a uma rede não confiável.**
- Fora de `dev`, a autenticação administrativa usa sessões HTTP e CSRF armazenado na sessão; o login recebe `application/x-www-form-urlencoded`, não JSON. Não utiliza JWT nem HTTP Basic como mecanismos de login do painel.
- O primeiro administrador é provisionado na inicialização a partir de credenciais externas (`RIVET_BOOTSTRAP_EMAIL` e `RIVET_BOOTSTRAP_PASSWORD`), sem endpoint de cadastro público. Não versione essas credenciais nem as mantenha ativas desnecessariamente após o bootstrap.
- A sessão deve usar `HttpOnly`, `Secure` em HTTPS e uma política `SameSite` apropriada no ambiente de produção. A conferência dos atributos em navegador/HTTP real permanece uma validação necessária antes da exposição.
- **Pendente de revisão de segurança da PR:** provisionamento concorrente, endurecimento do login (incluindo rate limiting) e testes HTTP reais de cookies. Testes MockMvc não comprovam o comportamento integral de um navegador.
- CORS do perfil `dev` cobre `/api/**` e utiliza `RIVET_CORS_ALLOWED_ORIGIN` (padrão `http://localhost:5173`). CORS não substitui autenticação.
- Upload limitado a 512 MiB; inspeção do conteúdo descompactado limitada a 2 GiB; cada arquivo de metadados limitado a 1 MiB. Esses controles não tornam uploads arbitrários seguros para exposição pública.
- Não há ainda SSH, autenticação do host remoto, execução Docker, persistência de deployments, verificação de prontidão ou histórico operacional.
- Não armazene chaves privadas SSH, senhas ou tokens em texto puro no banco. Mantenha backups do PostgreSQL e dos artefatos locais.

## Desenvolvimento local

### Pré-requisitos

- JDK 21 e Git.
- PostgreSQL para executar o backend.
- Docker/daemon acessível para Testcontainers e para preparar imagens nos exemplos.

O repositório contém o Gradle Wrapper; Gradle global não é obrigatório.

### Configurar ambiente

Na raiz do repositório:

```bash
cp .env.example .env
```

Preencha `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` e `RIVET_IMAGE_STORAGE_DIR`. Um `.env` local pode conter:

```dotenv
DB_HOST=localhost
DB_PORT=5432
DB_NAME=rivet
DB_USERNAME=rivet
DB_PASSWORD=defina_sua_senha_local
RIVET_IMAGE_STORAGE_DIR=./data/images
RIVET_CORS_ALLOWED_ORIGIN=http://localhost:5173

# Somente enquanto provisionar o administrador inicial
RIVET_BOOTSTRAP_EMAIL=admin@example.com
RIVET_BOOTSTRAP_PASSWORD=troque-por-uma-senha-forte

RIVET_SESSION_COOKIE_SECURE=false
```

`RIVET_SESSION_COOKIE_SECURE=false` permite testar sessões por HTTP localmente. Em produção, utilize true e HTTPS. O valor padrão configurado no backend é true.
O projeto importa opcionalmente `.env` como arquivo de propriedades por `spring.config.import=optional:file:./.env[.properties]`. Para garantir a disponibilização das variáveis `RIVET_BOOTSTRAP_*` ao `@Value` do bootstrap no desenvolvimento local, exporte-as para o processo, ou configure-as na IDE/ambiente de execução:

```bash
set -a
source .env
set +a
```

**Execute `source` somente em um `.env` local e confiável.** Nunca versione `.env`; ele já está no `.gitignore`. Depois de criar o administrador, retire a senha de bootstrap do ambiente. O banco guarda o hash, não a senha original.

### Executar

Para desenvolvimento local permissivo:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

Para **testar autenticação real**, inicie sem o perfil `dev`:

```bash
./gradlew bootRun
```

O backend responde por padrão em `http://localhost:8080`. O perfil protegido não fornece automaticamente uma página de login HTML; o formulário será implementado no frontend. Para o fluxo HTTP com `curl`, consulte o [guia de autenticação](docs/guides/authentication.md).

### Swagger/OpenAPI

- `http://localhost:8080/swagger-ui/index.html`
- `http://localhost:8080/v3/api-docs`

No perfil protegido, as rotas de documentação também exigem autenticação, salvo configuração explícita diferente. O springdoc documenta endpoints e modelos presentes no código; esta seção não substitui o OpenAPI gerado.

### Testes

```bash
./gradlew test --no-daemon
./gradlew build
```

Os testes de integração usam PostgreSQL 17 via Testcontainers e `@ServiceConnection`. O último resultado **relatado pelo Codex na branch `feat/auth`** foi de **117 testes aprovados**, incluindo **12 de autenticação**, sem execução independente nesta revisão documental. Consulte os resultados da CI para conferir a situação após o push e a PR.

## Documentação

| Documento | Conteúdo |
| --- | --- |
| [Visão da arquitetura](docs/architecture/overview.md) | Plano de controle, módulos, limites e fluxo futuro de deployment (Mermaid) |
| [Módulo `application`](docs/architecture/modules/application.md) | Cadastro e consulta de aplicações |
| [Módulo `image`](docs/architecture/modules/image.md) | Upload, inspeção TAR/GZIP, limites e persistência |
| [Módulo `server`](docs/architecture/modules/server.md) | Registro e consultas de servidores, sem SSH |
| [Módulo `user`](docs/architecture/modules/user.md) | Usuários, bootstrap e contratos de consulta/provisionamento |
| [Módulo `security`](docs/architecture/modules/security.md) | Sessões, login, CSRF, endpoints e limites de segurança |
| [Guia de upload](docs/guides/image-upload.md) | `docker save` → upload → cadastro |
| [Guia de autenticação](docs/guides/authentication.md) | Configuração e ciclo login → sessão → logout |
| [ADR 0001](docs/adr/0001-modulith-jpa.md) | Adiar persistência de eventos do Modulith via JPA |
| [ADR 0002](docs/adr/0002-admin-session-auth.md) | Sessões HTTP em vez de JWT stateless para o painel |
| [ADR 0003](docs/adr/0003-separate-build-and-deploy.md) | Separação entre build, entrega e implantação |

## Estrutura do repositório

```text
rivet/
├── .github/workflows/                 # CI e qualidade
├── docs/
│   ├── adr/                           # Decisões de arquitetura
│   ├── architecture/                  # Visão geral e documentos por módulo
│   └── guides/                        # Guias operacionais
├── src/
│   ├── main/
│   │   ├── java/io/github/inaciofragalli/rivet/
│   │   │   ├── application/           # Cadastro/consulta de aplicações
│   │   │   ├── image/                 # Recebimento de imagens Docker
│   │   │   ├── server/                # Cadastro de servidores
│   │   │   ├── user/                  # Dados e provisionamento de usuários
│   │   │   └── security/              # Autenticação, CSRF e sessões HTTP
│   │   └── resources/db/migration/    # Migrações Flyway V1–V6
│   └── test/                          # Testes de integração, segurança e modularidade
├── .env.example
├── build.gradle.kts
└── settings.gradle.kts
```

## Próximas etapas

1. Finalizar a `feat/auth`: documentação, PR, revisão de segurança e eventuais correções.
2. Implementar conexão SSH segura com Apache MINA SSHD, autenticação por chave e validação da host key (`feat/server-ssh`).
3. Desenvolver o módulo `deployment`: transferência/importação de imagem e execução Docker com estado persistido.
4. Expandir o frontend para autenticação, servidores e deployments; integrar logs/estado operacional e publicação deliberada via HTTPS.
5. Validar o primeiro deployment real no Ubuntu doméstico, com procedimentos de recuperação e administração privada.

Após o MVP: imagens versionadas em registry, integração GitHub/CI, builds automatizados, stacks Compose e melhorias de observabilidade.