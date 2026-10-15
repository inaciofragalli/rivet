# Módulo `security`

**Status:** autenticação administrativa implementada e validada manualmente na branch `feat/auth`; aguardando revisão da PR.  
**Package:** `io.github.inaciofragalli.rivet.security`  
**Objetivo:** proteger o plano de controle privado do Rivet antes das operações SSH e Docker reais.

## Responsabilidade

O módulo `security` integra a identidade de usuário do módulo `user` ao Spring Security e gerencia login, sessão HTTP, autorização, CSRF e logout. Seu propósito é restringir APIs administrativas do Rivet a um operador autenticado. Ele **não gerencia** o armazenamento JPA dos usuários nem a autenticação SSH dos servidores de destino.

A escolha para o MVP é **sessão HTTP server-side** em vez de JWT stateless ou HTTP Basic. Há um único administrador inicial, não existe cadastro público e o plano administrativo deve permanecer acessível apenas por rede privada/Tailscale (ou equivalente).

## Organização

```text
security/
├── DevSecurityConfig.java              # Apenas perfil dev; já existia na main
└── internal/
    ├── AuthenticatedUser.java           # Adaptador UserDetails
    ├── CustomUserDetailsService.java   # Consulta UserLookup por email
    ├── PasswordEncoderConfig.java      # Bean PasswordEncoder
    ├── InitialAdminProvisioner.java    # Bootstrap com ApplicationRunner
    ├── SecurityConfig.java             # FilterChain fora de dev
    ├── AuthController.java             # GET /csrf e GET /me
    └── MeResponse.java                 # Email do usuário autenticado
```

A estrutura acima reflete o desenho implementado ao longo da branch, **não uma nova leitura do working tree local**. Confirme nomes e localizações finais antes de publicar a documentação.

`AuthenticatedUser` implementa `UserDetails` sobre `user/UserAuthData`. `CustomUserDetailsService` implementa `UserDetailsService` e chama `UserLookup.findByEmail()`, evitando acesso a `user/internal/` e mantendo a verificação `ApplicationModules.verify()` válida.

## Autenticação por sessão

1. O cliente solicita `GET /api/auth/csrf`; o backend obtém/materializa o token CSRF associado à sessão HTTP.
2. O cliente envia `POST /api/auth/login` com corpo `application/x-www-form-urlencoded` (`email` e `password`), cookie da sessão e header `X-CSRF-TOKEN`.
3. O `formLogin` do Spring Security autentica por `AuthenticationManager`, com `UserDetailsService` e `PasswordEncoder`.
4. No sucesso, retorna `204 No Content`, com autenticação persistida na sessão. A sessão tem rotação de ID na autenticação (*session fixation protection*).
5. O frontend busca novamente `/api/auth/csrf` após login, porque o token anterior pode ter sido invalidado.
6. O cliente identifica a sessão por `GET /api/auth/me`, que retorna um `MeResponse` com apenas o email.
7. `POST /api/auth/logout` exige CSRF válido, invalida a sessão e retorna `204`. `GET /me` após logout retorna `401`.

O `formLogin()` nativo processa login e logout pela cadeia do Spring Security; o `AuthController` **não** cria manualmente JWT, cookie de autenticação ou sessão.

## Configuração CSRF

- O token é mantido por `HttpSessionCsrfTokenRepository` na sessão HTTP.
- O header esperado no fluxo descrito é `X-CSRF-TOKEN`.
- O handler padrão do Spring Security pode produzir tokens mascarados para mitigar BREACH; o endpoint `/csrf` devolve o valor que o cliente deve enviar, sem precisar de `CsrfCookieFilter`.
- Requisições que mudam estado (como `POST`, `PUT`, `PATCH`, `DELETE`) exigem token CSRF válido na configuração protegida, incluindo login e logout.
- O token precisa ser renovado após autenticação e encerramento de sessão.

**Importante:** o cookie da sessão é uma credencial de autenticação, enquanto o token CSRF é um mecanismo separado de proteção contra ações forjadas. Conhecer o token isoladamente não autentica o usuário.

## Autorização e políticas

No perfil protegido (`!dev`), `GET /api/auth/csrf` e o processamento de `POST /api/auth/login` são públicos para permitir autenticação; os demais endpoints exigem usuário autenticado, inclusive recursos de aplicações e servidores. Acesso não autenticado responde `401`; acesso autenticado, porém não autorizado, responde `403` quando aplicável. HTTP Basic e remember-me são desativados.

A migração de usuários não possui roles. **Não há RBAC nesta versão**. Qualquer usuário provisionado e autenticado tem, em princípio, acesso à superfície administrativa protegida; isso é aceitável apenas enquanto o bootstrap controlar uma conta administrativa única.

### Ambiente de desenvolvimento

`DevSecurityConfig`, na raiz de `security/`, usa `@Profile("dev")`, permite todas as requisições e desativa CSRF deliberadamente. Seu CORS cobre `/api/**` com origem configurada por `RIVET_CORS_ALLOWED_ORIGIN` (padrão `http://localhost:5173`).

`SecurityConfig` usa `@Profile("!dev")` para evitar cadeias conflitantes. **Não exponha `dev` a redes não confiáveis e não use esse perfil para validar segurança.** Testes de autenticação rodam com perfil `test`, não `dev`.

## Cookies, HTTPS e CORS

O cookie `JSESSIONID` identifica a sessão e precisa de política segura: `HttpOnly`, `Secure` em HTTPS, `SameSite` apropriado e tempo limite definido. Na infraestrutura alvo, preferimos frontend e backend pela mesma origem, com `/api` encaminhado ao Spring Boot. O acesso administrativo ficará privado, enquanto apenas aplicações explicitamente publicadas terão URLs externas.

O comportamento exato de atributos de cookie e do navegador deve ser verificado em requisições HTTP reais antes da exposição. **O teste MockMvc não substitui essa verificação.** A aplicação em HTTP local pode exigir configuração adequada de desenvolvimento; não enfraqueça cookies de produção para acomodar o teste local.

## API HTTP

| Método | Rota | Contrato |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | `200 OK`, JSON com `token`; público para obter CSRF antes do login |
| `POST` | `/api/auth/login` | Form-urlencoded `email`/`password` + CSRF, `204` no sucesso, `401` na falha de credenciais |
| `GET` | `/api/auth/me` | `200 OK`, JSON com `email`; `401` se não autenticado |
| `POST` | `/api/auth/logout` | CSRF obrigatório, invalida sessão, `204 No Content` |

Exemplo da resposta do `/me`:

```json
{
  "email": "admin@example.com"
}
```

Login/logout não aceitam JSON nesta etapa. O frontend deverá enviar cookies (`withCredentials` quando necessário), obter e renovar CSRF corretamente e distinguir `401` (não autenticado) de `403` (acesso negado ou CSRF inválido).

## Provisionamento inicial

`InitialAdminProvisioner` é um `@Component` que implementa `ApplicationRunner`. Na inicialização, consulta `UserProvisioning.hasUsers()` e, se não houver contas, obtém `rivet.bootstrap.email` e `rivet.bootstrap.password` de configuração externa, codifica a senha com o `PasswordEncoder` e chama `UserProvisioning.createInitialAdmin`.

Não existe rota pública `/register`. Se as credenciais iniciais estiverem ausentes com banco vazio, a aplicação falha ao iniciar. Quando já existe um usuário, a inicialização não redefine automaticamente a senha. O `.env` é local e ignorado pelo Git; variáveis de ambiente ou segredo dedicado devem ser preferidos na operação.

O provisionamento concorrente permanece um ponto de revisão: um `count() == 0` seguido de `save()` não fornece exclusão mútua transacional por si só.

## Testes e evidências

O fluxo foi verificado manualmente com `curl`:

- `/csrf`: `200`;
- `/me` antes do login: `401`;
- `/login` válido: `204`;
- `/me` autenticado: `200`;
- `/logout`: `204`;
- `/me` após logout: `401`.

O último parecer do Codex relatou **12 testes automatizados de autenticação aprovados** e **117 testes totais aprovados**, incluindo Spring Modulith. Foram relatadas verificações de persistência do `SecurityContext`, rotação de sessão e invalidação de CSRF. Os testes e a branch local não foram executados novamente para esta documentação.

## Riscos e limites antes da exposição

- Revisar provisionamento simultâneo do administrador inicial e tratamento de falhas de configuração.
- Verificar flags reais do `JSESSIONID` e invalidação de cookie/sessão com servidor HTTP e navegador, não só MockMvc.
- Avaliar rate limiting do login, auditoria de ações privilegiadas e regras explícitas de autorização para futuras contas.
- Garantir que o perfil `dev` nunca seja ativado no controlador acessível remotamente.
- Proteger credenciais e evitar segredos em logs e arquivos versionados.
- A autenticação do painel não substitui autorização de execução de comandos, verificação de host key SSH, isolamento de Docker ou Tailscale.

Para um procedimento reproduzível, consulte [Guia de autenticação](../../guides/authentication.md). Para a justificativa da abordagem, consulte [ADR 0002](../../adr/0002-admin-session-auth.md).
