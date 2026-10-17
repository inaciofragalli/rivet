# Módulo `security`

**Status:** autenticação administrativa implementada, testada e integrada à `main`; ajustes adicionais em `fix/auth-hardening`.  
**Package:** `io.github.inaciofragalli.rivet.security`  
**Objetivo:** proteger o plano de controle privado do Rivet antes da execução de operações SSH e Docker.

## Responsabilidade

O módulo `security` integra a identidade de usuário do módulo `user` ao Spring Security e gerencia login, sessões HTTP, autorização, proteção CSRF e logout.

Seu propósito é restringir as APIs administrativas do Rivet a um operador autenticado. Ele **não gerencia** a persistência JPA dos usuários nem a autenticação SSH dos servidores de destino.

A escolha para o MVP é **autenticação por sessão HTTP server-side**, em vez de JWT stateless ou HTTP Basic. O sistema possui um administrador inicial, não oferece cadastro público e deverá manter o painel acessível somente por uma rede privada, preferencialmente via Tailscale.

## Organização

```text
security/
├── DevSecurityConfig.java              # Configuração exclusiva do perfil dev
└── internal/
    ├── AuthenticatedUser.java           # Adaptador UserDetails
    ├── CustomUserDetailsService.java    # Consulta ao módulo user
    ├── PasswordEncoderConfig.java       # Bean PasswordEncoder
    ├── InitialAdminProvisioner.java     # Bootstrap do administrador
    ├── SecurityConfig.java              # FilterChain fora do perfil dev
    ├── AuthController.java              # GET /csrf e GET /me
    └── MeResponse.java                  # Identidade do usuário autenticado
```

`AuthenticatedUser` implementa `UserDetails` a partir de `UserAuthData`, contrato público do módulo `user`.

`CustomUserDetailsService` implementa `UserDetailsService` e utiliza `UserLookup.findByEmail()`. Essa separação evita dependências diretas de `security` sobre `user.internal`, preservando os limites definidos pelo Spring Modulith.

## Autenticação por sessão

1. O cliente solicita `GET /api/auth/csrf` e recebe um token associado à sessão HTTP.
2. O cliente envia `POST /api/auth/login` com `application/x-www-form-urlencoded`, contendo `email` e `password`, o cookie da sessão e o header `X-CSRF-TOKEN`.
3. O Spring Security valida as credenciais por meio de `AuthenticationManager`, `UserDetailsService` e `PasswordEncoder`.
4. Em caso de sucesso, retorna `204 No Content`. A autenticação é persistida na sessão e seu identificador é alterado para proteger contra *session fixation*.
5. O cliente solicita um novo token CSRF após o login, pois o token anterior é invalidado.
6. `GET /api/auth/me` permite consultar a identidade autenticada, retornando somente o e-mail.
7. `POST /api/auth/logout` exige CSRF válido, encerra a sessão e retorna `204 No Content`.

O Spring Security processa login e logout por meio de seus filtros. O `AuthController` não implementa manualmente autenticação, criação de sessões ou emissão de JWT.

## Configuração CSRF

- O token é armazenado na sessão por `HttpSessionCsrfTokenRepository`.
- O cliente envia o token por meio do header `X-CSRF-TOKEN`.
- O processamento padrão do Spring Security utiliza proteção contra BREACH por mascaramento de tokens.
- Requisições que alteram estado, incluindo `POST`, `PUT`, `PATCH` e `DELETE`, exigem CSRF válido no perfil protegido.
- Login e logout também exigem token CSRF.
- O token deve ser renovado depois da autenticação e da criação de uma nova sessão após o logout.

**Importante:** o cookie de sessão identifica a sessão autenticada. O token CSRF apenas protege contra requisições forjadas e, isoladamente, não concede autenticação.

## Autorização e políticas

No perfil protegido (`!dev`), os seguintes endpoints podem ser acessados sem autenticação:

| Método | Endpoint | Finalidade |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | Obtenção inicial do token CSRF |
| `POST` | `/api/auth/login` | Processamento da autenticação |
| `GET` | `/actuator/health` | Verificação de saúde da aplicação |

**Os demais endpoints exigem autenticação**, incluindo recursos de aplicações, imagens, servidores e `/actuator/info`.

O endpoint `/actuator/health` é liberado para permitir verificações de disponibilidade por ferramentas de infraestrutura sem exigir uma sessão administrativa.

A política de segurança utiliza:

- `401 Unauthorized` para acesso não autenticado.
- `403 Forbidden` para acesso autenticado não autorizado ou requisições com CSRF inválido.
- HTTP Basic desabilitado.
- Remember-me desabilitado.

A implementação inicial não possui papéis ou permissões diferenciadas (**RBAC**). Todos os usuários autenticados possuem, em princípio, acesso à superfície administrativa protegida. Esse modelo é adequado apenas enquanto o provisionamento estiver restrito a um único administrador.

### Ambiente de desenvolvimento

`DevSecurityConfig` utiliza `@Profile("dev")`, libera as requisições e desabilita a proteção CSRF exclusivamente para desenvolvimento local.

O CORS desse perfil cobre `/api/**`, com origem configurada por `RIVET_CORS_ALLOWED_ORIGIN`, cujo padrão é `http://localhost:5173`.

`SecurityConfig` utiliza `@Profile("!dev")`, evitando a ativação simultânea das duas configurações.

**O perfil `dev` não deve ser exposto a redes não confiáveis nem utilizado para validar os mecanismos de segurança.** Os testes de autenticação utilizam o perfil `test`.

## Cookies, HTTPS e CORS

O Rivet utiliza o cookie `JSESSIONID` para identificar sessões HTTP.

As configurações estão definidas no `application.properties`:

```properties
server.servlet.session.cookie.http-only=true
server.servlet.session.cookie.same-site=lax
server.servlet.session.cookie.secure=${RIVET_SESSION_COOKIE_SECURE:true}
server.servlet.session.timeout=30m
```

Essas propriedades estabelecem:

| Propriedade | Comportamento |
| --- | --- |
| `HttpOnly` | Impede acesso ao cookie por JavaScript |
| `Secure` | Restringe o envio do cookie a conexões seguras |
| `SameSite=Lax` | Restringe o envio do cookie em determinados contextos entre sites |
| Timeout de 30 minutos | Define o período de inatividade da sessão |

A variável `RIVET_SESSION_COOKIE_SECURE` permite utilizar `false` em testes locais por HTTP. O valor padrão é `true`, destinado à operação com HTTPS.

A arquitetura prevê frontend e backend acessíveis pela mesma origem, com requisições `/api` encaminhadas ao Spring Boot.

Os atributos reais dos cookies, sua invalidação e o comportamento por trás de um proxy ou túnel HTTPS deverão ser verificados com servidor HTTP real e navegador. Os testes MockMvc não substituem essa validação.

## API HTTP

| Método | Rota | Contrato |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | `200 OK`, JSON contendo `token`; acesso anônimo permitido |
| `POST` | `/api/auth/login` | Form-urlencoded com `email`, `password` e CSRF; `204` no sucesso, `401` para credenciais inválidas |
| `GET` | `/api/auth/me` | `200 OK` com o e-mail autenticado; `401` sem autenticação |
| `POST` | `/api/auth/logout` | Exige CSRF, invalida a sessão e retorna `204 No Content` |
| `GET` | `/actuator/health` | Health check acessível sem autenticação; status conforme a saúde da aplicação |

Exemplo de resposta de `/api/auth/me`:

```json
{
    "email": "admin@example.com"
}
```

Login e logout não recebem JSON nesta implementação. O frontend deverá enviar os cookies de sessão, obter e renovar o token CSRF e tratar corretamente respostas `401` e `403`.

## Provisionamento inicial

`InitialAdminProvisioner` é um componente Spring que implementa `ApplicationRunner`.

Durante a inicialização, consulta `UserProvisioning.hasUsers()` e, quando o banco está vazio, utiliza as credenciais configuradas para criar o administrador inicial.

As propriedades são mapeadas no `application.properties`:

```properties
rivet.bootstrap.email=${RIVET_BOOTSTRAP_EMAIL:}
rivet.bootstrap.password=${RIVET_BOOTSTRAP_PASSWORD:}
```

O Spring Boot importa o arquivo `.env` da raiz do projeto por meio de:

```properties
spring.config.import=optional:file:./.env[.properties]
```

O provisionamento utiliza `PasswordEncoder` para gerar o hash da senha antes de persistir o usuário.

Não existe endpoint público de cadastro.

- Se o banco estiver vazio e as credenciais estiverem ausentes, a aplicação falha durante a inicialização.
- Se já existir um usuário, o provisionamento não recria o administrador nem redefine sua senha.
- Após o provisionamento, as credenciais iniciais podem ser retiradas do ambiente.
- O arquivo `.env` deve permanecer fora do controle de versão.

### Normalização de e-mails

A `fix/auth-hardening` introduz normalização no módulo `user`, utilizando:

```java
email.strip().toLowerCase(Locale.ROOT)
```

A normalização é aplicada ao provisionamento e à consulta de usuários. A busca sem diferenciação de maiúsculas e minúsculas também permite localizar contas anteriormente cadastradas com capitalização diferente.

O objetivo é garantir que diferenças de caixa ou espaços nas extremidades não impeçam a autenticação.

A validação defensiva contra valores nulos, vazios ou compostos somente por espaços deve ser mantida e coberta por testes, além das restrições de validação da entidade.

### Limitação de concorrência

O fluxo inicial utiliza uma verificação de existência seguida da inserção do administrador.

Esse procedimento não garante exclusão mútua entre inicializações concorrentes de diferentes instâncias. O MVP prevê uma única instância do backend; o provisionamento concorrente permanece como limitação conhecida para revisão futura.

## Testes e evidências

O fluxo principal de autenticação foi validado manualmente com `curl` e Postman:

- Obtenção de CSRF: `200 OK`.
- Consulta de sessão anônima: `401 Unauthorized`.
- Login válido: `204 No Content`.
- Consulta autenticada: `200 OK`.
- Logout válido: `204 No Content`.
- Consulta após logout: `401 Unauthorized`.

A `feat/auth` foi integrada à `main` após uma execução relatada com **12 testes de autenticação aprovados e 117 testes totais aprovados**, incluindo a verificação de modularidade do Spring Modulith.

A suíte de autenticação contempla persistência do `SecurityContext`, alteração do identificador de sessão, proteção CSRF e invalidação da sessão no logout.

### Cobertura adicional da `fix/auth-hardening`

A revisão técnica identificou a necessidade dos seguintes testes:

- Persistência de e-mail normalizado no provisionamento inicial.
- Consulta por e-mail com diferenças de capitalização e espaços nas extremidades.
- Login real com e-mail não normalizado, utilizando MockMvc.
- Acesso anônimo a `GET /actuator/health`.
- Bloqueio anônimo de `GET /actuator/info`.
- Rejeição de entradas inválidas na normalização de e-mails.

Esses testes constituem critérios de validação da correção e não devem ser considerados aprovados antes de sua execução.

## Riscos e limites antes da exposição

- **Bootstrap concorrente:** o provisionamento inicial ainda não possui mecanismo de exclusão mútua entre instâncias.
- **Rate limiting:** proteção contra tentativas excessivas de login será implementada em uma branch específica, com Bucket4j como opção planejada.
- **Autenticação multifator:** MFA administrativo está planejado como requisito de segurança antes da disponibilização do MVP em ambiente real.
- **Cookies:** verificar `Secure`, `HttpOnly`, `SameSite`, expiração e invalidação em HTTPS real.
- **Ambiente:** impedir utilização do perfil `dev` em instâncias acessíveis por redes não confiáveis.
- **Credenciais:** proteger senhas e outros segredos, evitando exposição em logs, respostas HTTP e arquivos versionados.
- **Execução privilegiada:** a autenticação do painel não substitui validação de host key SSH, controle dos destinos remotos ou restrições sobre operações Docker.
- **Autorização futura:** novas contas administrativas exigirão um modelo explícito de permissões antes da ampliação do acesso.

O plano de controle deverá permanecer privado, preferencialmente via Tailscale. Somente aplicações explicitamente publicadas poderão ter acesso externo.

Para testar o fluxo completo, consulte o [Guia de autenticação](../../guides/authentication.md).

Para a justificativa arquitetural da autenticação por sessão, consulte o [ADR 0002](../../adr/0002-admin-session-auth.md).