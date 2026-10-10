# Rivet

**Build here. Run anywhere.**

O **Rivet** é uma plataforma de implantação e gerenciamento de aplicações em infraestrutura própria. A proposta é reunir, em uma interface, operações que normalmente exigiriam comandos manuais de Docker e SSH: registrar aplicações, implantar versões, acompanhar o estado dos contêineres e consultar o histórico de implantações.

O projeto começa como uma solução de **hospedagem pessoal**, pensada para funcionar em um único servidor Linux com recursos modestos. A longo prazo, poderá incluir integração com GitHub, builds automatizados, múltiplos servidores e recursos de observabilidade.

> **Estado do projeto:** desenvolvimento inicial. As funcionalidades de implantação descritas abaixo são objetivos do MVP, **não recursos já implementados**.

## Repositórios

O Rivet usa repositórios separados:

- **`rivet`** (este repositório): backend, API e lógica de gerenciamento da infraestrutura.
- **`rivet-frontend`**: painel web em React e TypeScript, desenvolvido separadamente.

## Stack do backend

- Java 21 e Spring Boot 4
- Gradle com Kotlin DSL
- Spring Web MVC, Spring Security e Validation
- Spring Data JPA, PostgreSQL e Flyway
- Spring Modulith (monólito modular)
- springdoc-openapi (contrato e documentação da API)
- JUnit e Testcontainers para testes

**Arquitetura planejada:** módulos de negócio como `applications`, `deployments` e `servers`, com responsabilidades separadas das integrações de Docker e SSH. Os módulos serão introduzidos conforme a implementação avançar.

## Objetivo do MVP

Permitir que um administrador implante, em um servidor Linux próprio, uma aplicação HTTP a partir de uma **imagem Docker já construída**, sem precisar executar manualmente todo o processo de implantação.

O fluxo pretendido é:

1. Preparar uma imagem Docker fora do servidor de destino.
2. Cadastrar o servidor e a aplicação no Rivet.
3. Solicitar uma implantação pelo painel ou pela API.
4. Transferir/carregar a imagem e iniciar o container por meio de uma conexão SSH confiável.
5. Verificar o resultado e registrar estado, erros e histórico da implantação.
6. Publicar, quando desejado, a aplicação via HTTPS; inicialmente, a configuração de túnel/domínio poderá ser manual.

O **painel administrativo permanece privado**, mesmo que as aplicações hospedadas sejam acessíveis pela internet.

**Fora do MVP:** builds automáticos a partir do GitHub, Kubernetes, múltiplos nós, equipes, cobrança e orquestração distribuída.

## Desenvolvimento local

### Pré-requisitos

- JDK 21
- PostgreSQL acessível localmente
- Git

Docker será necessário para executar ou testar funcionalidades de implantação e pode ser utilizado pelos testes com Testcontainers.

### Banco de dados

Crie um banco PostgreSQL e um usuário com permissão sobre ele. O esquema da aplicação será gerenciado por **migrações Flyway**; não utilize geração automática de tabelas pelo Hibernate.

### Variáveis de ambiente

Crie o arquivo `.env` na raiz do projeto com base em `.env.example`:

```bash
cp .env.example .env
```

Configure os valores de acordo com o PostgreSQL local:

```dotenv
DB_HOST=localhost
DB_PORT=5432
DB_NAME=rivet
DB_USERNAME=rivet
DB_PASSWORD=defina_sua_senha_local
```

As variáveis de conexão são **obrigatórias, sem valores de fallback**. O arquivo `.env` contém credenciais locais e **não deve ser versionado**. A importação pelo Spring Boot depende da configuração correspondente em `application.properties` e da execução a partir da raiz do projeto.

### Executar

Na raiz do repositório:

```bash
./gradlew bootRun
```

### Testar

```bash
./gradlew test --no-daemon
./gradlew build
```

Testes que inicializam o contexto Spring podem exigir um banco PostgreSQL de testes e variáveis `DB_*` apropriadas. O workflow de CI provisiona um PostgreSQL temporário para os testes.

## Organização

```text
rivet/
├── .github/workflows/       # Integração contínua
├── docs/                    # Documentação e decisões arquiteturais
├── src/
│   ├── main/
│   │   ├── java/             # Aplicação e futuros módulos de negócio
│   │   └── resources/        # Configuração e migrações Flyway
│   └── test/                 # Testes e recursos de teste
├── .env.example             # Modelo de configuração, sem credenciais reais
├── build.gradle.kts
└── settings.gradle.kts
```

A estrutura acima representa a **organização prevista**; alguns diretórios e arquivos poderão ser adicionados ao longo do desenvolvimento.

## Próximos marcos

1. Concluir a base do backend: segurança, banco, migrações e verificação de limites com Spring Modulith.
2. Implementar o cadastro de servidores e aplicações.
3. Criar o ciclo de implantação de imagens Docker por SSH, com persistência de estados e tratamento de falhas.
4. Expor o histórico e o estado observado das aplicações por meio da API e do painel.
5. Validar a implantação de uma aplicação real no servidor doméstico, com acesso HTTPS e administração privada.

Após o MVP, as evoluções candidatas incluem imagens em registry (por exemplo, GHCR), integração com GitHub Actions, atualização/rollback, stacks Docker Compose e suporte a outros servidores.