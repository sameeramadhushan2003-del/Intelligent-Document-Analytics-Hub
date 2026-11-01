# Windows PowerShell setup guide

Use this guide for a fresh development environment or to restart the existing application. Do not recreate an existing database, overwrite an account, rotate its signing key accidentally, or rerun applied SQL files manually. Commands here are instructions; documentation work does not execute provisioning.

## 1. Tools

- Install/configure a JDK supporting `--release 21`; JDK 21 is the baseline. This workspace has also compiled on JDK 26. Check `java -version` and `javac -version` and point `JAVA_HOME` at your selected JDK if needed.
- Use `backend/mvnw.cmd`; `.mvn/wrapper/maven-wrapper.properties` selects Maven 3.10.0. No standalone Maven upgrade is needed. First use needs access to Maven repositories; subsequent offline runs require cached artifacts.
- Install Node satisfying Vite's `^20.19.0 || >=22.12.0` engine requirement and npm. Check `node --version` and `npm.cmd --version`. Use `.cmd` on Windows if script execution policy blocks the PowerShell shim.
- Install PostgreSQL 18 and pgvector built for the same installation/architecture, and install Ollama. Git is useful for source review. No larger language model is required.

Start services in this order: **PostgreSQL, Ollama, Spring Boot, React**.

## 2. PostgreSQL and pgvector

For the existing Windows installation:

```powershell
Get-Service -Name '*postgres*'
# Only if this is your service and it is stopped; may require administrator rights.
Start-Service -Name postgresql-x64-18
& 'C:\Program Files\PostgreSQL\18\bin\pg_isready.exe' -h localhost -p 5432
```

Adjust the path/service name to your installed instance. The application's configured URL is `jdbc:postgresql://localhost:5432/document_analytics_hub`; its configured role is `document_hub_app`. For a different authorized setup, Spring Boot's standard `SPRING_DATASOURCE_URL` and `SPRING_DATASOURCE_USERNAME` overrides are available, but do not replace an existing working launch configuration.

### Fresh installation only

As the PostgreSQL administrator, open psql with an interactive password prompt:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\psql.exe' -h localhost -U postgres -d postgres -W
```

Only if the application role and database do **not** already exist, provision them interactively:

```sql
CREATE ROLE document_hub_app LOGIN;
\password document_hub_app
CREATE DATABASE document_analytics_hub OWNER document_hub_app;
```

`\password` prompts without putting a password into SQL source. Existing users should verify their setup rather than execute these statements or reset role passwords.

pgvector's extension files must be available to PostgreSQL first. Follow the [official pgvector Windows installation instructions](https://github.com/pgvector/pgvector#windows), using Visual Studio C++ tools and the matching PostgreSQL root. Its `nmake` instructions run in an **x64 Native Tools Command Prompt**, not ordinary PowerShell. This project does not vendor pgvector binaries or require rebuilding an already-working installation.

For a new database, an administrator can enable the extension before application startup:

```sql
\connect document_analytics_hub
CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA public;
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
```

This avoids granting extension-installation/superuser rights to the runtime application role. V7 uses the same `IF NOT EXISTS` statement. Ensure the migration role can create tables in `public`; database ownership supplies this in a standard fresh PostgreSQL setup. Do not use superuser credentials as application credentials.

### Existing Flyway migrations

| Migration | Purpose |
| --- | --- |
| V1 | Documents table |
| V2 | User accounts |
| V3 | Ownership and private storage metadata |
| V4 | Extracted text |
| V5 | Allow extracted document status |
| V6 | Document chunks and constraints |
| V7 | Enable pgvector |
| V8 | Chunk embeddings and model/dimension constraints |

Normal Spring Boot startup applies pending migrations. Hibernate is configured with `spring.jpa.hibernate.ddl-auto=none`. Do not edit already-applied migrations, clear Flyway history, run `clean`, or reset the database to resolve a checksum error. Investigate differences first. For verification against an existing schema, disable migration execution with the command below.

## 3. Ollama

Install/open the Windows application using the [official Ollama quickstart](https://docs.ollama.com/quickstart). Use local inference; no cloud account/API key is needed for these models. If the server is not already running, start `ollama serve` in its own terminal.

```powershell
ollama list
# Download only missing models.
ollama pull all-minilm
ollama pull qwen2.5:0.5b
Invoke-RestMethod -Uri 'http://localhost:11434/api/tags'
```

Model names in code are `all-minilm` and `qwen2.5:0.5b`. Ollama may list the embedding model as `all-minilm:latest`. Do not replace either with a larger model on this 8 GB laptop. Keep live inference checks sequential.

## 4. Private backend configuration

Read `backend/src/main/resources/application.properties`. Required variables:

| Variable | Purpose |
| --- | --- |
| `DB_PASSWORD` | Existing authorized PostgreSQL application-role password |
| `JWT_SECRET_BASE64` | Stable Base64 signing key, decoding to at least 32 bytes |
| `DOCUMENT_STORAGE_DIR` | Optional private upload root; defaults to `${user.home}/DocumentAnalyticsHub/private-uploads` |

The Ollama service property is `app.ollama.base-url`, default `http://localhost:11434`; override through the normal Spring property mechanism only when necessary. `SPRING_DATASOURCE_URL`/`SPRING_DATASOURCE_USERNAME` are standard Spring overrides, not custom variables required by this repository. Never put keys in frontend `VITE_*` settings.

In the same PowerShell session that will launch the backend, use your trusted private launch configuration or secure prompts:

```powershell
$databasePassword = Read-Host 'Existing application database password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $databasePassword).Password
$signingKey = Read-Host 'Existing Base64 JWT signing key' -AsSecureString
$env:JWT_SECRET_BASE64 = [System.Net.NetworkCredential]::new('', $signingKey).Password
# Optional: use an approved absolute private directory, outside this repository.
# $env:DOCUMENT_STORAGE_DIR = 'C:\DocumentAnalyticsHub\private-uploads'
```

Do not echo these variables, place literal values in shell history, export them in screenshots/HAR files or check them into Git. The environment belongs to the launching process; a different terminal/IDE must receive its own private configuration. Spring Boot does not automatically read a `.env` file.

For a **fresh installation only**, if no signing key exists, generate one without displaying it, then persist it through your approved private secret-management mechanism:

```powershell
$signingBytes = New-Object byte[] 32
$keyGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$keyGenerator.GetBytes($signingBytes)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($signingBytes)
$keyGenerator.Dispose()
```

Do not run this on every restart of an existing installation; changing the key invalidates existing tokens. This guide does not provide or store a shared demo key.

## 5. Compile and launch Spring Boot

```powershell
Set-Location -LiteralPath 'C:\Intelligent-Document-Analytics-Hub\backend'
.\mvnw.cmd -B compile
.\mvnw.cmd spring-boot:run
```

For verification of an **already-provisioned** database without migrations or Hibernate schema changes:

```powershell
.\mvnw.cmd spring-boot:run '-Dspring-boot.run.arguments=--spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=none'
```

The no-migration mode does not initialize a fresh database. Keep private configuration in the launching session and do not start another backend when port 8080 is already in use.

The earlier PowerShell wrapper null-target issue was repaired in `mvnw.cmd`. If wrapper startup still fails but its Maven distribution is already cached, reuse that executable:

```powershell
$mavenCache = Join-Path $env:USERPROFILE '.m2\wrapper\dists\apache-maven-3.10.0'
$cachedMaven = Get-ChildItem -LiteralPath $mavenCache -Recurse -Filter mvn.cmd |
    Select-Object -First 1 -ExpandProperty FullName
if (-not $cachedMaven) { throw 'No cached Maven distribution found; use the project wrapper.' }
& $cachedMaven "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" -B compile
```

Use `-o` only when all required dependencies/plugins are already cached. The explicit repository path also helps in restricted launchers where Java resolves its default home incorrectly. No Maven reinstall or upgrade is needed just to use the cache.

## 6. Launch React

In another terminal:

```powershell
Set-Location -LiteralPath 'C:\Intelligent-Document-Analytics-Hub\frontend'
npm.cmd ci
npm.cmd run dev
```

Open `http://localhost:5173`. Vite proxies `/api` to `http://localhost:8080`; existing direct document actions use that backend origin and CORS permits the localhost frontend. Do not assume changing the frontend hostname/port will work without updating the development configuration.

```powershell
Invoke-RestMethod -Uri 'http://localhost:8080/api/health'
Invoke-RestMethod -Uri 'http://localhost:5173/api/health'
```

Health reports that the web application is running; it is not a comprehensive model/database readiness probe. Register a synthetic demo account only when authorized, or sign in with an approved existing one. Do not create/change real accounts for setup verification.

## 7. Validate and troubleshoot

Follow [final verification](final-verification.md) for the selected non-database test command and pending authenticated browser checks. `npm run build` creates `frontend/dist`; `npm run preview` alone does not configure production backend routing.

- **Database authentication failure:** verify the existing role/database, private password and launch environment. Do not reset accounts or disguise the error with a Hibernate dialect override.
- **Missing JWT/password placeholder:** configure the process that actually starts Java, including IDE run settings where applicable.
- **Extension missing:** check pgvector matches PostgreSQL 18's installation path/architecture and is enabled in this database.
- **Flyway error:** preserve existing data/history; compare applied SQL and schema before making changes.
- **Ollama connection/model error:** check `/api/tags`, exact model names and service port; use sequential requests and allow CPU time.
- **401/404/409:** respectively reauthenticate, confirm approved ownership/existence, or generate the required derived data for an approved test document.
- **Frontend 502/CORS/network error:** verify port 8080, `/api` proxy and `http://localhost:5173` origin.
- **Blank scanned PDF:** OCR is not implemented; use a text-bearing PDF or approved TXT/DOCX fixture.

Never paste passwords, tokens, private source text or full diagnostic dumps into public issue reports.
