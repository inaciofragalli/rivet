# ADR 0002: Autenticação administrativa por sessão HTTP

- **Status:** Adotada na implementação `feat/auth`; revisão de segurança pendente na PR
- **Data:** 2026-10-15
- **Contexto:** Rivet / proteção do plano de controle do MVP

## Contexto

O Rivet é um plano de controle self-hosted capaz de gerenciar operações potencialmente privilegiadas sobre Docker e servidores Linux. O MVP terá somente um administrador e o painel deverá ser acessível apenas por rede privada/Tailscale ou equivalente. O backend usa Spring MVC e Spring Security; o frontend React/TypeScript será seu cliente web.

O Quizler, outro projeto do autor, já utilizava JWT stateless em cookie HttpOnly, mas o Rivet não precisa de autenticação distribuída ou APIs públicas para múltiplos clientes nesta etapa. Logout, invalidação de credenciais e redução da complexidade de segurança são prioridades.

## Decisão

Usar a autenticação nativa do Spring Security com **sessão HTTP no servidor e cookie de sessão**, sem JWT stateless ou HTTP Basic como mecanismo principal do painel.

- Login com email e senha via `POST /api/auth/login` (`application/x-www-form-urlencoded`).
- Sessão persistida no servidor, com proteção contra *session fixation* e invalidação ao fazer logout.
- Token CSRF armazenado na sessão por `HttpSessionCsrfTokenRepository`, obtido por `GET /api/auth/csrf` e enviado em `X-CSRF-TOKEN`.
- Cookie de sessão HttpOnly, Secure em HTTPS e SameSite apropriado; política de HTTPS obrigatória na exposição real.
- Endpoint `GET /api/auth/me` para o frontend identificar sessão ativa.
- Senhas codificadas por `PasswordEncoder`; administrador inicial provisionado a partir de segredo externo, sem cadastro público.
- `user/` expõe contratos mínimos a `security/`, sem compartilhar entidade JPA ou repository internos.
- Perfil `dev` isolado para ambiente local e nunca exposto; configuração protegida aplicada fora desse perfil.

## Consequências

A sessão permite revogação direta no servidor e dispensa emissão, assinatura, rotação e listas de revogação de JWT. Como o MVP tem um único controlador, a manutenção de estado de sessão é aceitável. Se o produto escalar horizontalmente, será necessário avaliar compartilhamento de sessões ou afinidade.

CSRF continua necessário porque cookies de sessão são enviados automaticamente pelo navegador. O frontend precisa buscar token inicial e atualizá-lo após login/logout. O `formLogin()` nativo usa corpo de formulário, diferentemente das APIs JSON usadas no restante do projeto.

O plano de controle deve continuar privado. Sessões não substituem controle de acesso à rede, verificação SSH, proteção da API Docker nem mecanismos de autorização de comandos.

## Alternativas consideradas

1. **JWT stateless em cookie HttpOnly:** viável, mas aumentaria a complexidade de logout/revogação e não traz benefício proporcional no MVP.
2. **HTTP Basic:** simples, mas inadequado à experiência de login/logout do painel React.
3. **Sessão HTTP server-side com Spring Security:** alternativa adotada.

## Quando revisar

Reavaliar se surgirem APIs externas, clientes móveis, autenticação via provedor externo, vários controladores, suporte a equipes ou exigências de autenticação delegada. Essas evoluções **não exigem** substituir antecipadamente o modelo `User` da primeira versão.

Antes do merge e da exposição, a revisão da PR deverá cobrir provisionamento concorrente, rate limiting, atributos reais dos cookies e testes de integração HTTP/browser. Não interpretar a decisão de autenticação como aprovação automática de segurança para produção.

## Referências internas

- `docs/architecture/modules/security.md`
- `docs/architecture/modules/user.md`
- `docs/guides/authentication.md`
- `src/main/java/io/github/inaciofragalli/rivet/security/`
