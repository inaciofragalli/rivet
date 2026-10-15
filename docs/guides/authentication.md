# Guia: autenticação administrativa do Rivet

**Status:** implementado e validado manualmente na branch de trabalho `feat/auth`; aguardando revisão da PR.  
**Referência:** [Módulo `security`](../architecture/modules/security.md).  
**Escopo:** uso local do fluxo login → sessão → logout; não é um guia de exposição pública.

Este guia explica como provisionar o administrador inicial e testar a autenticação do Rivet pela API. Ao contrário do Quizler, a autenticação utiliza **sessões HTTP gerenciadas pelo Spring Security**, não JWT. O acesso administrativo permanecerá privado, inicialmente via Tailscale ou rede autenticada equivalente.

## 1. Requisitos e preparação

- Backend Rivet, PostgreSQL e Flyway configurados.
- JDK 21 e `curl` para os comandos abaixo.
- `jq` para extrair o token CSRF nos exemplos; instalação opcional.
- Perfil **diferente de `dev`**: nesse perfil as requisições são liberadas e CSRF está desabilitado.

Crie seu `.env` local a partir de `.env.example`, preencha as variáveis `DB_*` e de storage e adicione, **somente na primeira inicialização do banco**:

```dotenv
RIVET_BOOTSTRAP_EMAIL=admin@example.com
RIVET_BOOTSTRAP_PASSWORD=substitua-por-uma-senha-forte
```

As credenciais devem permanecer fora do código-fonte e do Git. O `.env` está ignorado pelo repositório. Para carregá-las no processo via terminal Linux:

```bash
set -a
source .env
set +a
./gradlew bootRun
```

O comando `source .env` executa o conteúdo do arquivo como shell: utilize-o apenas com arquivo local confiável e sintaxe compatível. A importação opcional de `.env` pelo Spring não dispensa verificar se os valores `RIVET_BOOTSTRAP_*` foram resolvidos no processo de execução.

O `InitialAdminProvisioner` executa a cada inicialização, mas cria usuário apenas quando o banco está vazio. Com o primeiro usuário já criado, não recria nem redefine sua senha. Remova a senha do ambiente depois do provisionamento. **Se o banco estiver vazio e faltar alguma credencial, a aplicação falhará na inicialização.**

> Não use o perfil `dev` para validar segurança. Não exponha a instância em HTTP fora de uma máquina ou rede local confiável. Em produção, exija HTTPS e cookies adequados.

## 2. Receber token CSRF e cookie de sessão

```bash
TOKEN=$(curl -fsS \
  -c cookies.txt \
  http://localhost:8080/api/auth/csrf | jq -r '.token')
```

Esperado: `200 OK`, corpo JSON `{"token":"..."}` e cookie `JSESSIONID`. O token é usado no header `X-CSRF-TOKEN`. O cookie identifica a sessão HTTP; o token CSRF **não autentica** o usuário.

O arquivo `cookies.txt` é local e sensível. Não o versione nem compartilhe. Você pode usar `chmod 600 cookies.txt` após sua criação.

## 3. Confirmar bloqueio antes do login

```bash
curl -i -b cookies.txt http://localhost:8080/api/auth/me
```

Esperado: `401 Unauthorized`.

## 4. Fazer login

No terminal que executa o `curl`, certifique-se de que `RIVET_BOOTSTRAP_EMAIL` e `RIVET_BOOTSTRAP_PASSWORD` estão exportadas. O login nativo do Spring Security espera **`application/x-www-form-urlencoded`**, não JSON:

```bash
curl -i \
  -b cookies.txt \
  -c cookies.txt \
  -H "X-CSRF-TOKEN: $TOKEN" \
  --data-urlencode "email=$RIVET_BOOTSTRAP_EMAIL" \
  --data-urlencode "password=$RIVET_BOOTSTRAP_PASSWORD" \
  http://localhost:8080/api/auth/login
```

Esperado: `204 No Content` e sessão autenticada. Essas opções de `curl` são exemplos locais; fornecer senha em argumentos de processo pode expô-la a ferramentas de inspeção locais. Para produção, use o formulário protegido do frontend e HTTPS.

Em seguida, confirme:

```bash
curl -i -b cookies.txt http://localhost:8080/api/auth/me
```

Esperado: `200 OK`, com JSON que contém o email do usuário autenticado, sem hash de senha.

## 5. Renovar CSRF após o login

O Spring Security invalida o token CSRF antigo ao autenticar. Solicite um novo token, mantendo o cookie atual:

```bash
TOKEN=$(curl -fsS \
  -b cookies.txt \
  -c cookies.txt \
  http://localhost:8080/api/auth/csrf | jq -r '.token')
```

O frontend React precisará fazer a mesma renovação após login/logout. Os próximos `POST`, `PUT`, `PATCH` e `DELETE` devem enviar o token atualizado no header.

## 6. Fazer logout e confirmar invalidação

```bash
curl -i \
  -b cookies.txt \
  -c cookies.txt \
  -H "X-CSRF-TOKEN: $TOKEN" \
  -X POST http://localhost:8080/api/auth/logout
```

Esperado: `204 No Content`. Depois:

```bash
curl -i -b cookies.txt http://localhost:8080/api/auth/me
```

Esperado: `401 Unauthorized`. Remova o cookie jar local depois de testar:

```bash
rm -f cookies.txt
```

## 7. Respostas principais

| Operação | Resposta esperada |
| --- | --- |
| `GET /api/auth/csrf` | `200 OK` com token |
| `GET /api/auth/me` sem login | `401 Unauthorized` |
| `POST /api/auth/login` com credenciais corretas + CSRF | `204 No Content` |
| `POST /api/auth/login` com credenciais incorretas + CSRF | `401 Unauthorized` |
| `POST /api/auth/login` sem CSRF válido | `403 Forbidden` |
| `GET /api/auth/me` autenticado | `200 OK` com email |
| `POST /api/auth/logout` com CSRF válido | `204 No Content` |
| `GET /api/auth/me` após logout | `401 Unauthorized` |

## 8. Problemas comuns

**Login retorna `401`, mas o bootstrap funcionou:** confira se o terminal do `curl` realmente tem as variáveis exportadas. Reiniciar o backend com uma senha de bootstrap diferente **não altera** o hash de um administrador já criado.

**Login retorna `403`:** o token CSRF pode estar ausente, incorreto, expirado ou associado a outra sessão. Busque `/csrf` com o mesmo cookie jar e repita o login.

**Login retorna `204`, mas `/me` retorna `401`:** confira `Set-Cookie`, persistência/reenvio do `JSESSIONID`, atributos `Secure`/`SameSite` e se a mesma sessão está sendo reutilizada. No navegador, examine DevTools → Network → headers; em HTTP local, a política `Secure` merece atenção.

**Com `dev` tudo parece liberado:** o perfil `dev` desativa autenticação/CSRF deliberadamente; a verificação deve ocorrer fora dele.

## 9. Verificações antes da produção

O ciclo foi validado manualmente via `curl`; o último resultado **relatado** para a branch `feat/auth` foi de 12 testes automatizados de autenticação aprovados, com 117 testes totais. Ainda é necessário revisar o bootstrap para concorrência, garantir que cookies seguros são realmente enviados em HTTPS e verificar o comportamento HTTP/browser real. Rate limiting de login e endurecimento final serão avaliados na revisão de segurança da PR.

Não publique o controle administrativo antes dessa revisão. Mesmo com Tailscale, autenticação, CSRF, validação de host SSH e autorização rigorosa continuam necessários, pois o Rivet administrará operações privilegiadas.
