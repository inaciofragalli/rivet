# Guia: upload de imagens Docker

**Status:** fluxo disponível na API do Rivet desde a PR #3 (`feat/image-upload`).  
**Atualizado em:** 2026-10-11

Este guia apresenta o fluxo **já implementado**: preparar uma imagem Docker, enviá-la ao Rivet e cadastrar uma aplicação apontando para o artefato recebido. O resultado é um registro no banco e um arquivo preservado em disco — **não um container em execução**.

## 1. Preparar o ambiente

Requisitos para este exemplo:

- Backend Rivet em execução com PostgreSQL configurado.
- Docker disponível na máquina que vai **gerar o arquivo da imagem**.
- `curl`; `jq` é opcional para extrair o UUID automaticamente.
- Espaço disponível no diretório de armazenamento configurado.

Na raiz do projeto, configure o `.env` local a partir do `.env.example`. Não versione esse arquivo:

```bash
cp .env.example .env
```

Preencha `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` e `RIVET_IMAGE_STORAGE_DIR`. Para o desenvolvimento local com o frontend Vite, a configuração padrão permite a origem `http://localhost:5173`; se necessário, defina também `RIVET_CORS_ALLOWED_ORIGIN`.

Inicie a aplicação **somente para desenvolvimento local**:

```bash
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

> O perfil `dev` permite requisições sem autenticação e desabilita CSRF. **Não publique essa instância na internet.**

## 2. Gerar o TAR com `docker save`

Por exemplo, usando a imagem pública do Nginx:

```bash
docker pull nginx:alpine
docker save -o nginx.tar nginx:alpine
```

Também é possível compactar o arquivo:

```bash
gzip -c nginx.tar > nginx.tar.gz
```

O Rivet aceita TAR puro ou TAR compactado em GZIP e identifica a compactação pelo conteúdo. Um `Dockerfile`, um diretório de projeto ou um ZIP arbitrário **não** substituem o arquivo gerado por `docker save`.

## 3. Fazer o upload

```bash
curl -i \
  -F 'file=@nginx.tar' \
  http://localhost:8080/api/image-artifacts
```

Para a versão GZIP, substitua `nginx.tar` por `nginx.tar.gz`.

Em caso de sucesso, a API retorna **201 Created** e uma resposta JSON semelhante a:

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "image": "nginx",
  "tag": "alpine",
  "architecture": "amd64",
  "os": "linux",
  "exposedPorts": [80],
  "archiveSizeBytes": 12345678,
  "sha256": "<sha256-hexadecimal-de-64-caracteres>"
}
```

Os valores acima são **ilustrativos**: UUID, arquitetura, portas, tamanho e checksum reais dependem da imagem e do arquivo enviado. Guarde o `id` retornado; ele identifica o artefato no backend. O campo `exposedPorts` reflete metadados da imagem, **não publica portas no host**.

## 4. Cadastrar uma aplicação com o artefato

Use o UUID de verdade recebido no passo anterior:

```bash
curl -i \
  -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "nginx-demo",
    "imageArtifactId": "550e8400-e29b-41d4-a716-446655440000",
    "containerPort": 80
  }'
```

Troque o UUID de exemplo pelo valor retornado no upload. O cadastro retorna **201 Created**, com `Location: /api/applications/{id}` e os campos `id`, `name`, `image`, `containerPort` e `imageArtifactId`. A referência `image` será derivada dos metadados do artefato, por exemplo `nginx:alpine`.

O campo `containerPort` **continua obrigatório** e deve ser informado pelo cliente. Uma porta declarada em `exposedPorts` pode ajudar a escolher esse valor, mas a API não seleciona nem valida automaticamente a correspondência entre as duas informações.

### Alternativa: cadastro por referência de imagem

É possível cadastrar diretamente uma referência Docker, sem upload:

```bash
curl -i \
  -X POST http://localhost:8080/api/applications \
  -H 'Content-Type: application/json' \
  -d '{
    "name": "outra-app",
    "image": "ghcr.io/exemplo/app:v1",
    "containerPort": 8080
  }'
```

O cadastro aceita **exatamente uma** destas opções: `image` não vazia **ou** `imageArtifactId`. Fornecer as duas ou nenhuma resulta em `400 Bad Request`. Se `image` estiver vazia e `imageArtifactId` for fornecido, o UUID é usado como fonte. Uma referência manual passa pelo validador atual de nomes/tags Docker, que não cobre todas as formas possíveis de referências OCI.

## 5. Consultar o registro da aplicação

```bash
curl http://localhost:8080/api/applications
curl http://localhost:8080/api/applications/<uuid-da-aplicacao>
```

As consultas incluem `imageArtifactId` quando a aplicação está vinculada a um upload; no cadastro manual ele vem como `null`.

**Atenção:** a versão atual não oferece `GET /api/image-artifacts/{id}`, listagem de artefatos nem download de arquivos pelo HTTP.

## Erros frequentes

| Situação | Resultado esperado |
| --- | --- |
| Campo `file` vazio | `400 Bad Request` |
| Não é um TAR/GZIP Docker válido | `400 Bad Request` |
| Manifesto sem uma única imagem, configuração/portas inválidas | `400 Bad Request` |
| Arquivo acima do limite ou conteúdo descompactado excessivo | `413 Content Too Large` |
| `imageArtifactId` desconhecido | `404 Not Found` ao cadastrar a aplicação |
| `image` e `imageArtifactId` enviados juntos | `400 Bad Request` |
| Nome de aplicação duplicado | `409 Conflict` |

Os erros tratados usam o formato [Problem Details](https://www.rfc-editor.org/rfc/rfc9457). Não há garantia de que toda falha de sistema de arquivos ou de banco será convertida em uma dessas respostas de validação.

## Limites importantes

- O arquivo recebido é limitado a **512 MiB**; o multipart tem limite de `512MB` para o arquivo e `520MB` para a requisição.
- A inspeção limita o conteúdo descompactado a **2 GiB** e cada arquivo de metadados lido a **1 MiB**.
- O manifesto deve representar **uma imagem**; se houver várias `RepoTags`, o Rivet usa a primeira.
- Somente portas TCP declaradas em `ExposedPorts` entram na resposta.
- O arquivo fica no diretório `RIVET_IMAGE_STORAGE_DIR`, com nome `<uuid>.archive`.
- O serviço limpa arquivos temporários antigos na inicialização, mas ainda não tem remoção pública de artefatos ou uma política completa de retenção.
- O sistema ainda **não** faz análise de malware/vulnerabilidades, instalação da imagem no Docker host, autenticação administrativa definitiva ou deployment.

Para detalhes internos e decisões de implementação, consulte [Módulo `image`](../architecture/modules/image.md).