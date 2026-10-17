# Guia: autenticação administrativa do Rivet

**Status:** implementado, testado e integrado à `main`.   
**Referência:** [Módulo `security`](../architecture/modules/security.md).  
**Escopo:** uso local do fluxo login → sessão → logout; não é um guia de exposição pública.

Este guia explica como provisionar o administrador inicial e testar a autenticação do Rivet pela API. A autenticação utiliza **sessões HTTP gerenciadas pelo Spring Security**, não JWT. O acesso administrativo permanecerá privado, inicialmente via Tailscale ou rede autenticada equivalente.

## 1. Requisitos e preparação

- Backend Rivet, PostgreSQL e Flyway configurados.
- JDK 21 e `curl` para os comandos abaixo.
- `jq` para extrair o token CSRF nos exemplos; instalação opcional.
- Perfil **diferente de `dev`**: com autenticação e proteção CSRF habilitadas.

Crie o seu `.env` local a partir de `.env.example`, preencha as variáveis `DB_*` e de storage e adicione, **somente na primeira inicialização do banco**:

```dotenv
RIVET_BOOTSTRAP_EMAIL=admin@example.com
RIVET_BOOTSTRAP_PASSWORD=substitua-por-uma-senha-forte
```

O `InitialAdminProvisioner` executa a cada inicialização, mas cria usuário apenas quando o banco está vazio. Com o primeiro usuário já criado, não recria nem redefine sua senha. Remova a senha do ambiente depois do provisionamento. **Se o banco estiver vazio e faltar alguma credencial, a aplicação falhará na inicialização.**

O Spring Boot importa o arquivo .env da raiz do projeto por meio de spring.config.import. As variáveis de bootstrap são mapeadas no application.properties e utilizadas pelo InitialAdminProvisioner na inicialização.

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

O login nativo do Spring Security recebe as credenciais do administrador por meio de **`application/x-www-form-urlencoded`**, não JSON.

Utilize o e-mail e a senha definidos no provisionamento inicial, substituindo os valores ilustrativos abaixo:

```bash
curl -i \
  -b cookies.txt \
  -c cookies.txt \
  -H "X-CSRF-TOKEN: $TOKEN" \
  --data-urlencode "email=admin@example.com" \
  --data-urlencode "password=sua-senha-aqui" \
  http://localhost:8080/api/auth/login
```

Esperado: `204 No Content` e sessão autenticada.

O comando reutiliza o cookie de sessão obtido anteriormente e envia o token CSRF no header `X-CSRF-TOKEN`. As credenciais são verificadas contra o administrador armazenado no PostgreSQL.

**Atenção:** os comandos são exemplos para desenvolvimento local. Fornecer senhas diretamente em argumentos de processo pode expô-las a ferramentas de inspeção. Em produção, utilize o formulário protegido do frontend e HTTPS.

Para verificar se a autenticação foi concluída, consulte:

```bash
curl -i -b cookies.txt http://localhost:8080/api/auth/me
```

Esperado: `200 OK`, com uma resposta JSON contendo o e-mail do administrador autenticado, sem expor o hash da senha.

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

**Login retorna `401`, mas o bootstrap funcionou:** confira se o e-mail e a senha enviados na requisição correspondem às credenciais do administrador já cadastrado no PostgreSQL. Reiniciar o backend com uma senha de bootstrap diferente não altera o hash de um administrador existente.

**Login retorna `403`:** o token CSRF pode estar ausente, incorreto, expirado ou associado a outra sessão. Busque `/csrf` com o mesmo cookie jar e repita o login.

**Login retorna `204`, mas `/me` retorna `401`:** confira `Set-Cookie`, persistência/reenvio do `JSESSIONID`, atributos `Secure`/`SameSite` e se a mesma sessão está sendo reutilizada. No navegador, examine DevTools → Network → headers; em HTTP local, a política `Secure` merece atenção.

**Com `dev` tudo parece liberado:** o perfil `dev` desativa autenticação/CSRF deliberadamente; a verificação deve ocorrer fora dele.

## 9. Verificações antes da produção

O fluxo de autenticação foi validado manualmente e por testes automatizados. Na feat/auth, foram aprovados 12 testes de autenticação e 117 testes no total. A revisão posterior identificou melhorias de configuração e normalização de e-mails, tratadas na fix/auth-hardening.

Permanecem pendentes a proteção contra provisionamento concorrente, a implementação de rate limiting em uma branch específica e a validação dos atributos dos cookies em um ambiente HTTPS real.

O controle administrativo deve permanecer privado, preferencialmente via Tailscale. Antes de qualquer exposição em produção, devem ser validados HTTPS, cookies de sessão, autenticação e autorização. A integração SSH também exigirá verificação da identidade do servidor remoto.