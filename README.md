# IAM Service

Сервис **идентификации и управления доступом** (Identity and Access Management) для экосистемы Courage Gang: регистрация и вход по email/паролю, выпуск **JWT** и **refresh-сессий**, мультиарендность через **организации**, **RBAC** на уровне членства, приглашения, внешний вход **OIDC** (Google, GitHub), внутренний **introspect** токенов для других сервисов. Контракт HTTP описан в **OpenAPI 3.1**; реализация на **Micronaut 4** и **Java 21**, хранение данных в **PostgreSQL**, схема БД накатывается **Flyway**.

Базовый URL приложения (контекст Micronaut): **`/v1/iam`**. Порт по умолчанию: **8080**.

---

## Содержание

- [Назначение и границы](#назначение-и-границы)
- [Технологический стек](#технологический-стек)
- [Возможности](#возможности)
- [HTTP API и документация](#http-api-и-документация)
- [Метрики и золотые сигналы](#метрики-и-золотые-сигналы)
- [Аутентификация запросов](#аутентификация-запросов)
- [Модель данных (кратко)](#модель-данных-кратко)
- [Конфигурация](#конфигурация)
- [Сборка и запуск](#сборка-и-запуск)
- [Поведение OIDC callback](#поведение-oidc-callback)
- [Ошибки API](#ошибки-api)
- [Структура проекта](#структура-проекта)
- [Тесты и покрытие кода](#тесты-и-покрытие-кода)
- [Ограничения и дальнейшее развитие](#ограничения-и-дальнейшее-развитие)

---

## Назначение и границы

Сервис отвечает за:

- жизненный цикл **пользователя** (email, профиль, пароль, верификация email);
- **сессии** на основе refresh-токена (выдача, ротация при refresh, отзыв, список активных сессий);
- **организации** (создание, чтение, обновление), **членство** и **роли** внутри организации;
- **приглашения** в организацию и их принятие текущим пользователем;
- настройки **IdP** организации (метаданные в БД, тест доступности URL);
- **внутреннюю** проверку access-токена (introspect) для шлюзов и микросервисов.

Сервис **не** реализует полноценный **SAML SSO** для организаций: соответствующие маршруты отвечают **501 Not Implemented** с телом ошибки в общем формате API.

---

## Технологический стек

| Компонент | Выбор |
|-----------|--------|
| Язык | Java **21** |
| Фреймворк | **Micronaut** 4.7.x (Netty) |
| API | REST, валидация **Jakarta Validation** |
| Сериализация | **Micronaut Serde** (Jackson) |
| БД | **PostgreSQL** 16 (рекомендуется) |
| Доступ к БД | **JDBC** (HikariCP), репозитории вручную |
| Миграции | **Flyway** |
| JWT | **Nimbus JOSE + JWT**, подпись **HS256** (ключ из SHA-256 строки `jwt-secret`) |
| Пароли | **BCrypt** (Spring Security Crypto, strength 12) |
| Контейнер | **Docker**: multi-stage сборка **Gradle** → **shadow JAR**, runtime **Eclipse Temurin 21 JRE Alpine** |

OpenAPI-файл упаковывается в JAR (`META-INF/swagger`) для просмотра в **Swagger UI** встроенными средствами Micronaut OpenAPI.

---

## Возможности

### Аутентификация (`/auth`)

- **Регистрация** — создание пользователя, хэш пароля, опционально создание организации и членство с ролью `owner`, выдача пары access/refresh.
- **Вход** — проверка email/пароля, учёт попыток входа (`login_attempts`), выдача токенов.
- **Refresh** — по действующему refresh-токену: отзыв старой сессии, выдача новой пары (ротация в рамках **family_id**).
- **Logout** — отзыв сессии по refresh-токену.
- **Forgot / reset password** — одноразовые токены с хранением только **SHA-256** от секрета в БД.
- **Verify email** — подтверждение email одноразовым токеном.
- **Switch org** — перевыпуск access/refresh для выбранной организации (требует JWT и активное членство).

### Профиль и сессии пользователя (`/me`)

- **GET /me** — публичные данные пользователя и список организаций с ролями.
- **PATCH /me** — display name, locale.
- **POST /me/password** — смена пароля с проверкой текущего; после смены отзываются все refresh-сессии.
- **GET /me/sessions**, **DELETE /me/sessions/{id}** — список и отзыв своих refresh-сессий.

### Организации (`/organizations`)

Операции выполняются от имени пользователя из JWT; права проверяются по **permission keys**, назначенным ролям членства (см. сиды Flyway `V2__iam_seed_rbac.sql`).

- CRUD организации (создание автоматически добавляет создателя как `owner`).
- Список участников, изменение статуса и ролей, удаление членства (с защитой «последний owner»).
- Приглашения: список, создание (токен в БД только в виде хэша), отзыв.
- **IdP**: чтение и обновление конфигурации организации, тест HTTP GET по переданному или сохранённому `metadataUrl`.

### Приглашения (`/invites`)

- **POST /invites/accept** — принятие приглашения (JWT обязателен); проверка токена, совпадения email, создание членства и ролей.

### Внутренний API (`/internal`)

- **POST /internal/token/introspect** — по телу запроса с access-токеном возвращает `active`, `sub`, `orgId`, `roles`, **агрегированный список permissions** по ролям JWT, `exp`. Невалидный токен даёт `active: false` и пустые поля (без HTTP-ошибки), кроме специального демонстрационного случая токена `"invalid"` в спецификации.

### OIDC (`/auth/oidc/{provider}/…`)

- **GET …/start** — генерация `state`, сохранение в `oauth_oidc_states`, редирект на Google или GitHub. Опциональный query-параметр **`redirect_after`** задаёт URL приложения после успешного входа (если не задан — используется запасной URL в коде).
- **GET …/callback** — обмен `code` на токены, получение профиля / email, сопоставление или создание пользователя, связка **внешней идентичности**, выпуск сессии. Подробнее см. [ниже](#поведение-oidc-callback).

Провайдеры в пути: **`google`**, **`github`**. Требуются переменные окружения клиента и **redirect URI**, согласованные с консолью провайдера.

---

## HTTP API и документация

- **Канон контракта:** [`../api-contracts/iam/openapi.yaml`](../api-contracts/iam/openapi.yaml) (ADR-001 в `cursor-context`). Локальная копия: [`openapi/openapi.yaml`](openapi/openapi.yaml) — синхронизировать при изменении API.
- После запуска сервиса:
  - корень: **`GET /v1/iam/`** — JSON с подсказками на Swagger UI и health;
  - Swagger UI: путь из ответа корня, типично **`/v1/iam/swagger/views/swagger-ui/index.html`**;
  - health (Micronaut Management): **`/v1/iam/health`**;
  - метрики **Prometheus** (текстовый exposition): **`GET /v1/iam/metrics`** (`text/plain; version=0.0.4`).

### Метрики и золотые сигналы

Каждый HTTP-эндпоинт учитывается в **`http.server.requests`** (Micrometer): латентность с перцентилями и гистограммой задаётся в [`application.yml`](src/main/resources/application.yml) (`micronaut.metrics.binders.web.server`). Исходящие вызовы к внешним системам дополнительно измеряются таймером **`iam.integration.http`** с тегами `integration`, `operation`, `status`, `outcome` (OIDC Google/GitHub, проверка URL IdP при тесте метаданных).

| Золотой сигнал | Примеры метрик |
|----------------|----------------|
| Задержка | `http_server_requests_seconds_*`, `iam_integration_http_seconds_*` |
| Трафик | счётчики запросов у `http.server.requests`, `iam.integration.http` |
| Ошибки | теги статуса / `outcome` у HTTP; 4xx/5xx в `http.server.requests` |
| Насыщение | пул БД `hikaricp_*`, процессор и JVM (`process.cpu.usage`, `jvm.threads.*` и др.) |

**Prometheus scrape:** пример конфигурации с `job_name: iam-service` — [`deploy/prometheus-scrape.example.yml`](deploy/prometheus-scrape.example.yml). В Grafana-дашборде переменная **`job`** подхватывает все значения `label_values(up, job)`; для панелей выберите **`iam-service`** (или **All**).

---

## Аутентификация запросов

Защищённые маршруты (всё, кроме явно «публичных») требуют заголовок:

```http
Authorization: Bearer <access_jwt>
```

Глобальный фильтр **`JwtAuthFilter`** для публичных префиксов **не** требует JWT, в том числе:

- `/`, `/health`, **`/metrics`** (экспорт **Prometheus**), при необходимости `/prometheus`, `/swagger`;
- `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/logout`, forgot/reset/verify;
- `/auth/oidc/`, `/auth/sso/`;
- `/internal/`.

Маршрут **`/auth/switch-org`** не входит в публичный список: для него нужен валидный Bearer.

После успешной проверки JWT в атрибуты запроса кладутся:

- `iam.userId` — строка UUID пользователя;
- `iam.orgId` — строка UUID организации из claim `org` в JWT (если claim задан).

Контроллеры читают идентификатор пользователя через **`@RequestAttribute(SecurityAttributes.USER_ID)`**.

Access-токен содержит: `sub` (user id), опционально `org`, массив **`roles`** (ключи ролей в контексте организации).

---

## Модель данных (кратко)

Основные сущности (см. [`V1__iam_schema.sql`](src/main/resources/db/migration/V1__iam_schema.sql) и последующие миграции):

| Сущность | Назначение |
|----------|------------|
| `users`, `user_passwords` | Пользователь и учётные данные |
| `user_external_identities` | Связка пользователя с OIDC (`provider` + `subject`) |
| `organizations` | Организация, уникальный `slug` |
| `organization_memberships` | Членство пользователя в организации |
| `roles`, `permissions`, `role_permissions` | Справочник RBAC |
| `membership_roles` | Роли в рамках членства |
| `refresh_sessions` | Сессии refresh (в т.ч. `family_id`, хэш refresh-токена) |
| `organization_invites`, `organization_invite_roles` | Приглашения |
| `organization_idps` | Конфиг IdP организации |
| `oauth_oidc_states` | Одноразовый state для OIDC |
| `login_attempts` | Журнал попыток входа |
| Токен-таблицы | Email verification, password reset (хранение хэшей) |

Дополнительно: миграция **`V3__refresh_token_hash_oidc_state.sql`** — хэш refresh-токена и таблица OIDC state.

---

## Конфигурация

Файл по умолчанию: [`src/main/resources/application.yml`](src/main/resources/application.yml).

### Переменные окружения: база данных

| Переменная | По умолчанию | Описание |
|------------|----------------|----------|
| `DB_HOST` | `postgres` | Хост PostgreSQL |
| `DB_PORT` | `5432` | Порт |
| `DB_NAME` | `iam` | Имя БД |
| `DB_USER` | `iam` | Пользователь |
| `DB_PASSWORD` | `iam` | Пароль |

### Переменные окружения: IAM

| Переменная | Описание |
|------------|----------|
| `JWT_SECRET` | Секрет для подписи JWT (строка **не короче 32 символов**; проверяется при старте, см. `IamProperties`) |
| `OIDC_GOOGLE_CLIENT_ID`, `OIDC_GOOGLE_CLIENT_SECRET`, `OIDC_GOOGLE_REDIRECT_URI` | OAuth2/OIDC Google |
| `OIDC_GITHUB_CLIENT_ID`, `OIDC_GITHUB_CLIENT_SECRET`, `OIDC_GITHUB_REDIRECT_URI` | OAuth GitHub |

Параметры TTL в `application.yml` под ключом **`iam`**:

- `jwt-access-ttl-seconds` (по умолчанию 900);
- `refresh-ttl-seconds` (по умолчанию 30 суток в секундах).

Для **production** обязательно задайте собственный **`JWT_SECRET`**; значение по умолчанию в YAML только для локальной разработки.

---

## Сборка и запуск

### Только Docker (рекомендуемый способ)

Сборка описана в [`Dockerfile`](Dockerfile): стадия **Gradle** собирает **shadowJar** (`*-all.jar`), финальный образ содержит только JRE и JAR.

Из каталога **`services/iam-service`**:

```bash
docker compose up --build
```

Поднимаются **postgres** (порт **5433** снаружи → 5432 в контейнере) и **iam** на порту **8080**. Сервис `iam` ждёт `healthy` у Postgres.

Для OIDC добавьте в `docker-compose` или в окружение хоста переменные Google/GitHub и при необходимости **`JWT_SECRET`**.

### Локальная сборка Gradle (без Docker)

В каталоге сервиса должен быть Gradle Wrapper или установлен Gradle. Типичные команды:

```bash
gradle shadowJar
# или ./gradlew shadowJar
```

Запуск JAR требует доступной PostgreSQL и тех же переменных, что и в Docker.

---

## Поведение OIDC callback

После успешной аутентификации у провайдера сервис:

1. Проверяет и «съедает» **state** из БД (защита от CSRF, ограниченный срок жизни).
2. Находит или создаёт пользователя, при необходимости создаёт запись внешней идентичности.
3. Выпускает пару **access / refresh** так же, как при обычном входе.
4. Отвечает **302** на URL из `redirect_after` (или запасной URL), добавляя к нему **фрагмент** `#` с параметрами в стиле OAuth: `access_token`, `refresh_token`, `token_type`, `expires_in` (значения URL-encoded). Клиентское приложение должно считать токены из fragment и **не** логировать полный URL.

Если конфигурация провайдера не задана, возможен ответ **503** с кодом ошибки в теле `IamApiException`.

---

## Ошибки API

Исключения **`IamApiException`** преобразуются в JSON с телом **`ErrorBody`** (код, сообщение, HTTP-статус по смыслу операции). Обработчик: `IamExceptionHandler`.

---

## Структура проекта

```
services/iam-service/
├── Dockerfile
├── docker-compose.yml
├── build.gradle.kts
├── openapi/
│   └── openapi.yaml          # контракт API
└── src/main/
    ├── java/com/couragegang/iam/
    │   ├── Application.java
    │   ├── api/controller/   # REST-контроллеры
    │   ├── api/dto/          # модели запросов/ответов
    │   ├── config/           # IamProperties
    │   ├── error/            # IamApiException, обработчик
    │   ├── repo/             # JDBC-репозитории
    │   ├── security/         # JWT, фильтр, пароли, хэши
    │   └── service/          # бизнес-логика
    └── resources/
        ├── application.yml
        └── db/migration/     # Flyway SQL
```

---

## Тесты и покрытие кода

- **JUnit 5** и **Mockito** для unit-тестов сервисов, безопасности и контроллеров; **MockWebServer** (OkHttp) для проверки `OrganizationService.idpTest` по HTTP.
- **JaCoCo**: задача `check` включает **`jacocoTestCoverageVerification`** с порогом **не менее 80 % по метрике branch** для основного кода.
- В расчёт покрытия **не входят** (исключены в `build.gradle.kts`): каталог `api/dto` (только данные), все классы `repo` (JDBC), точка входа `Application`, класс **`OidcService`** (сетевой OAuth; для него при необходимости добавляют отдельные интеграционные или контрактные тесты).

Команды (из каталога репозитория, **JDK 21**):

```bash
# Первый раз, если нет gradlew:
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bootstrap-gradle-wrapper.ps1

./gradlew test
./gradlew jacocoTestReport
./gradlew check
```

На Windows: `gradlew.bat` вместо `./gradlew`. Сборка в Docker (`docker compose up --build`) JVM на хосте не требует.

Отчёт HTML: `build/reports/jacoco/test/html/index.html`. В Docker-образе по умолчанию выполняется только сборка **shadowJar** без тестов.

---

## Ограничения и дальнейшее развитие

- **SAML** для организаций пока не реализован (**501**).
- Секреты IdP организации в БД хранятся в упрощённом виде (не промышленное шифрование); для production нужен KMS или внешнее хранилище секретов.
- **Introspect** рассчитан на доверенную внутреннюю сеть; при выносе наружу нужны отдельные политики и rate limiting.
- Callback OIDC передаёт токены во **fragment** — осознанный компромисс для SPA; при необходимости можно заменить на обмен по **one-time code** и отдельный backend-for-frontend endpoint.
- Список участников организации в API поддерживает параметр **cursor** в спецификации; в текущей реализации курсор может не использоваться — пагинация расширяема.

Для регрессии по БД имеет смысл добавить интеграционные тесты (например, **Testcontainers** + PostgreSQL).
