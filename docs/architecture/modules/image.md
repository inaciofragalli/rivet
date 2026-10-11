# Módulo `image`

**Status:** implementado (upload, inspeção, armazenamento e consulta interna de artefatos)  
**Package:** `io.github.inaciofragalli.rivet.image`  
**Atualizado em:** 2026-10-11

## Responsabilidade

O módulo `image` recebe arquivos de imagens Docker previamente construídas, extrai os metadados necessários para o cadastro de aplicações e mantém uma cópia local do arquivo recebido. O objetivo, nesta etapa, é disponibilizar um **artefato de imagem registrado** que possa ser associado a uma aplicação.

O upload **não** executa `docker load`, não inicia contêineres, não transfere o arquivo para outro servidor e não cria deployments. Essas operações ainda não foram implementadas.

## Organização

```text
image/
├── DockerImageReference.java
├── ImageArtifactNotFoundException.java
├── ImageArtifactResponse.java
├── ImageArtifactService.java
├── ImageMetadata.java
└── internal/
    ├── DecompressedSizeLimitInputStream.java
    ├── DockerImageInspector.java
    ├── ImageArchiveStorage.java
    ├── ImageArchiveTooLargeException.java
    ├── ImageArtifact.java
    ├── ImageArtifactController.java
    ├── ImageArtifactExceptionHandler.java
    ├── ImageArtifactRepository.java
    └── InvalidImageArchiveException.java
```

O pacote raiz contém os tipos usados pela integração com outros módulos. Os detalhes de inspeção TAR/GZIP, persistência, armazenamento e HTTP ficam em `internal/`. O módulo `application` usa `ImageArtifactService.findById` e `ImageArtifactResponse` para validar um artefato existente, sem depender diretamente do repositório ou da entidade JPA do módulo `image`.

## Contrato HTTP implementado

### `POST /api/image-artifacts`

Recebe `multipart/form-data` com um campo obrigatório chamado `file`, contendo um arquivo compatível com a saída de `docker save` (`.tar` ou `.tar.gz`). A detecção de GZIP é realizada pelos bytes iniciais, não pela extensão.

Um upload válido responde com **`201 Created`** e um objeto JSON com os seguintes campos:

| Campo | Tipo | Significado |
| --- | --- | --- |
| `id` | UUID | Identificador do artefato persistido |
| `image` | string | Nome/repositório da imagem, sem a tag final |
| `tag` | string ou `null` | Tag extraída da primeira entrada de `RepoTags` |
| `architecture` | string | Arquitetura informada no JSON de configuração |
| `os` | string | Sistema operacional informado no JSON de configuração |
| `exposedPorts` | lista de inteiros | Portas TCP declaradas em `config.ExposedPorts` |
| `archiveSizeBytes` | inteiro | Tamanho do arquivo recebido, em bytes |
| `sha256` | string | SHA-256 do arquivo recebido |

**Não existem endpoints HTTP públicos de listagem, download ou exclusão de artefatos nesta implementação.** A busca por UUID existe no serviço do módulo para uso interno, sobretudo por `application`.

O procedimento completo com exemplos de `docker save`, `curl` e cadastro de uma aplicação está em [Upload de imagens Docker](../../guides/image-upload.md).

## Formato e validação

`DockerImageInspector` lê o TAR em duas passagens:

1. Localiza e interpreta `manifest.json`, exigindo que o array contenha **exatamente uma imagem**. `RepoTags` deve conter pelo menos uma referência não vazia; quando há mais de uma tag, a implementação usa **a primeira**. O nome do arquivo de configuração vem de `Config`.
2. Localiza o JSON de configuração, exige os atributos `architecture` e `os` válidos e interpreta `config.ExposedPorts` quando fornecido. Somente portas `*/tcp` são coletadas; outras modalidades são ignoradas.

Cada arquivo de metadados lido (`manifest.json` e configuração) tem limite de **1 MiB**. `ExposedPorts`, quando presente e diferente de `null`, precisa ser um objeto JSON; portas TCP devem estar no intervalo de **1 a 65535**. Referências Docker passam pelo validador `DockerImageReference`, que rejeita entradas incompatíveis com o subconjunto de sintaxe atualmente implementado. Referências por digest (`@sha256:...`) não são suportadas pelo parser nesta etapa.

O módulo não verifica se a imagem é confiável ou segura, nem executa um scanner de vulnerabilidades das camadas. Também não valida se a arquitetura é compatível com o futuro servidor de destino.

## Limites e armazenamento

| Proteção/decisão | Comportamento atual |
| --- | --- |
| Arquivo recebido | Até **512 MiB** no streaming do armazenamento; multipart configurado com `max-file-size=512MB` e `max-request-size=520MB` |
| Conteúdo descompactado | Limite de **2 GiB** no fluxo inspecionado pelo TAR, inclusive para GZIP |
| Metadados TAR/JSON | Máximo de **1 MiB por arquivo** de metadados |
| Armazenamento | Diretório configurado por `RIVET_IMAGE_STORAGE_DIR` (`rivet.images.storage-dir`) |
| Identificador de arquivo | `<uuid>.archive`, independente da extensão enviada |
| Integridade | SHA-256 calculado sobre os bytes do arquivo armazenado |

O caminho de armazenamento é convertido para um caminho absoluto e normalizado. O upload é copiado primeiro para `image-*.tmp` no diretório configurado. Após inspeção e cálculo de checksum, o arquivo é promovido por uma operação de movimentação atômica para `<uuid>.archive`; os metadados são então gravados em PostgreSQL.

Em caso de falha no processamento ou na persistência, o serviço **tenta** excluir o arquivo temporário ou promovido. Essa compensação é por melhor esforço: sistema de arquivos e banco de dados não participam de uma transação distribuída.

Na inicialização da aplicação (`ApplicationReadyEvent`), `ImageArchiveStorage` também tenta remover arquivos regulares `image-*.tmp` com mais de **duas horas**. Não segue links simbólicos nessa varredura. Não há limpeza periódica, coleta de arquivos definitivos órfãos ou política de retenção de artefatos nesta etapa.

> Os limites de tamanho reduzem riscos, mas não substituem isolamento, autenticação, limites de CPU/tempo e outras proteções necessárias antes de receber uploads não confiáveis em um serviço exposto à internet.

## Persistência

A migração `V3__create_image_artifacts.sql` cria `image_artifacts`:

| Coluna | Tipo | Descrição |
| --- | --- | --- |
| `id` | UUID | Chave primária |
| `image_name` | VARCHAR(512) | Nome da imagem |
| `image_tag` | VARCHAR(128), opcional | Tag |
| `architecture` | VARCHAR(64) | Arquitetura |
| `os` | VARCHAR(64) | Sistema operacional |
| `exposed_ports` | JSONB | Array de portas TCP |
| `archive_size_bytes` | BIGINT | Tamanho positivo do arquivo |
| `sha256` | VARCHAR(64) | Hash hexadecimal com 64 caracteres |
| `created_at` | TIMESTAMPTZ | Momento de criação |

A migração `V4__link_application_image_artifact.sql` acrescenta a coluna opcional `applications.image_artifact_id`, com chave estrangeira para `image_artifacts.id`. O vínculo representa a **origem da imagem escolhida no cadastro**, não um deployment realizado.

O schema é gerenciado pelo Flyway; Hibernate usa `spring.jpa.hibernate.ddl-auto=validate`.

## Tratamento de erros

| Situação | Resposta |
| --- | --- |
| Arquivo multipart vazio | `400 Bad Request` |
| TAR/GZIP inválido, manifesto/configuração inválidos ou metadados incompatíveis | `400 Bad Request` com `ProblemDetail` |
| Limite de upload ou de descompactação excedido | `413 Content Too Large` com `ProblemDetail` |
| UUID de artefato inexistente ao associar uma aplicação | `404 Not Found` com `ProblemDetail` |

Falhas inesperadas de armazenamento ou banco não são apresentadas como validações do arquivo e podem resultar em erro de servidor. Não há contrato de erro específico para cada falha de infraestrutura.

## Segurança e desenvolvimento local

`DevSecurityConfig` é ativo **somente no perfil `dev`**. Ele permite qualquer requisição e desabilita CSRF. O mesmo perfil aplica CORS a `/api/**`, com origem configurável em `RIVET_CORS_ALLOWED_ORIGIN` (padrão `http://localhost:5173`), incluindo requisições `OPTIONS`.

**Não exponha o backend com o perfil `dev` ativo à internet ou a redes não confiáveis.** A autenticação administrativa e a autorização de uploads ainda não estão implementadas. CORS não é um mecanismo de autenticação.

## Testes relevantes

- `DockerImageInspectorTest`: TAR/GZIP, manifesto, JSON de configuração, tags, portas e limite descompactado.
- `ImageArchiveStorageTest`: arquivos temporários, limite de tamanho e limpeza de arquivos antigos.
- `ImageArtifactServiceTest`: limpeza em caso de falha de persistência.
- `ImageArtifactControllerIntegrationTest`: upload, persistência e erros HTTP.
- `ImageArtifactLimitTest`: resposta HTTP 413 para upload acima do limite.
- `DockerImageReferenceTest`: validação de nomes e tags de referências Docker.
- `ApplicationImageArtifactIntegrationTest`: registro de aplicação com artefato, exclusividade das fontes e erros de referência.
- `CorsConfigurationTest`: preflight de aplicação e upload no perfil `dev`.

Os testes de integração usam PostgreSQL por Testcontainers. Para executá-los localmente é necessário ter um daemon Docker acessível.

## Limites atuais e próximos consumidores

A API ainda não oferece download do TAR, `docker load`, agendamento de deploy, execução remota, consulta de imagens do Docker host, remoção de artefatos ou análise de compatibilidade de plataforma. O primeiro consumidor implementado é o cadastro de aplicações. O frontend poderá usar o resultado do upload para exibir os metadados e enviar `imageArtifactId` ao cadastrar uma aplicação.