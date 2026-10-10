# ADR 0001: Adiar persistência de eventos do Spring Modulith via JPA

- **Status:** Aceita para a implementação inicial
- **Data:** 2026-10-10
- **Contexto:** Backend Rivet / primeiro módulo `application`

## Contexto

O backend do Rivet usa Spring Modulith para organizar os módulos de negócio. O primeiro deles é `application`.

Durante a configuração do projeto, `spring-modulith-starter-jpa` adicionou a infraestrutura de persistência de publicações de eventos do Spring Modulith. A validação do banco passou a exigir uma estrutura para esses registros, incluindo `event_publication`, que ainda não existia nas migrações Flyway.

Isso conflitou com a configuração adotada no Rivet: o Flyway controla o esquema, e `spring.jpa.hibernate.ddl-auto=validate` faz o Hibernate apenas conferir se as tabelas correspondem ao mapeamento.

O projeto ainda não usa eventos de domínio que precisem de publicações persistidas. Não havia, portanto, um caso de uso que justificasse introduzir essa infraestrutura agora.

## Decisão

Adiar o uso de `spring-modulith-starter-jpa`.

Vamos manter o Spring Modulith para definir e verificar os limites dos módulos. O PostgreSQL continua sendo alterado por migrações Flyway, com o Hibernate em modo `validate`.

A persistência de publicações de eventos poderá ser adicionada quando houver uma necessidade concreta.

## Consequências

A decisão evita tabelas que o MVP ainda não utiliza e mantém o esquema explícito e versionado. A modularização e sua verificação por `RivetModularityTests` continuam funcionando.

Há uma limitação importante: o projeto não pode assumir que eventos terão entrega durável enquanto não houver um mecanismo de persistência configurado para isso. Se os módulos passarem a trocar eventos ou executar trabalho assíncrono, será necessário definir como lidar com falhas, repetições, persistência e recuperação.

## Quando revisar

A decisão deverá ser revista se deployments, jobs ou outros módulos precisarem publicar eventos que possam ser recuperados após falhas ou reinicializações.

Se adotarmos o starter JPA, primeiro será necessário criar e validar as migrações Flyway para a infraestrutura de eventos. Também precisaremos conferir sua compatibilidade com a versão usada do Spring Modulith e com as regras de transação do projeto.

## Alternativas consideradas

1. **Manter o starter JPA e criar as migrations agora:** seria possível, mas acrescentaria infraestrutura sem um caso de uso atual.
2. **Permitir que Hibernate crie as tabelas:** contraria a política de usar `ddl-auto=validate` com Flyway.
3. **Adiar a persistência de eventos via JPA:** decisão adotada.

## Referências internas

- `build.gradle.kts` — dependências atuais do Spring Modulith.
- `src/main/resources/application.properties` — configuração `ddl-auto=validate` e Flyway.
- `src/test/java/io/github/inaciofragalli/rivet/RivetModularityTests.java` — verificação dos módulos.