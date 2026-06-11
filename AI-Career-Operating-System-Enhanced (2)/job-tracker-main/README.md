# AI Career Operating System

> AI-powered job intelligence platform for discovering, ranking and managing career opportunities.

# Job Tracker

Busca automática de vagas Java · Tech Lead · Staff Engineer, com filtro por IA e histórico de candidaturas.

## Stack

| Camada    | Tecnologia                                  |
|-----------|---------------------------------------------|
| Backend   | Java 21 + Quarkus (servidor HTTP embarcado) |
| Banco     | MySQL 8 + Flyway (schema + migrações)       |
| IA        | Claude Haiku (filtro de título + match LLM) |
| Scheduler | Quarkus Scheduler (a cada 15 min)           |
| Frontend  | React + Vite                                |

## Fontes de vagas

| ATS        | Empresas                                                                 |
|------------|--------------------------------------------------------------------------|
| Greenhouse | Stripe, Datadog, Figma, Dropbox, Duolingo, Robinhood, Pinterest, Elastic, MongoDB, Databricks, Cloudflare, Okta, Twilio, Reddit, Brex, Checkr, Affirm, Scale AI, GitLab, Grafana, PagerDuty, New Relic, Fastly, Temporal |
| Lever      | Plaid                                                                    |
| Ashby      | Notion, Confluent, Linear, Vercel, Mercury, Loom, Ramp, Snyk, Kong      |
| Remotive   | Busca por keyword: `java tech lead`, `java staff engineer`               |

> Para adicionar empresas, edite `backend/src/main/resources/companies.json`.

---

## Pré-requisitos

- Java 21+, Maven 3.9+
- Node 20+
- Docker + Docker Compose
- Chave da API Anthropic ([console.anthropic.com](https://console.anthropic.com) → API Keys)

---

## Dev local

### 1. Banco de dados (MySQL)

```bash
# Sobe o MySQL com volume persistente
docker compose up -d mysql

# O schema é criado automaticamente pelo Flyway na primeira inicialização do backend
# Não é necessário criar tabelas manualmente
```

### 2. Backend

Copie o arquivo de configuração e ajuste os valores:

```bash
cd backend
cp .env.example .env
# edite .env com sua chave da API (opcional) e dados do banco
mvn quarkus:dev
```

O backend sobe em `http://localhost:8080`.  
O Flyway aplica as migrações em `src/main/resources/db/migration/` automaticamente.

**Variáveis disponíveis no `.env`:**

| Variável            | Padrão                                     | Descrição                                      |
|---------------------|--------------------------------------------|------------------------------------------------|
| `ANTHROPIC_API_KEY` | _(vazio)_                                  | Opcional — sem ela, vagas são salvas sem score |
| `DB_URL`            | `jdbc:mysql://localhost:3306/jobs_tracker` | URL do banco                                   |
| `DB_USER`           | `jobs`                                     | Usuário do banco                               |
| `DB_PASSWORD`       | `jobs`                                     | Senha do banco                                 |

### 3. Frontend

```bash
cd frontend
npm install
npm run dev
```

O frontend sobe em `http://localhost:5173` com proxy para o backend.

### 4. Testar sync manual

```bash
curl -X POST http://localhost:8080/sync
```

---

## Migração de dados (DynamoDB → MySQL)

Se você já tem dados no DynamoDB Local e quer migrar para o MySQL:

```bash
# Instalar dependências do script
pip install boto3 mysql-connector-python

# Subir ambos os serviços
docker compose up -d

# Executar a migração
python3 scripts/migrate-dynamo-to-mysql.py
```

Variáveis opcionais para o script:

```bash
DYNAMO_ENDPOINT=http://localhost:8000 \
MYSQL_HOST=localhost \
MYSQL_PASSWORD=jobs \
python3 scripts/migrate-dynamo-to-mysql.py
```

O script é idempotente (`INSERT IGNORE` nos jobs) — pode ser executado mais de uma vez sem duplicar dados.  
Após a migração, o serviço `dynamodb-local` no `docker-compose.yml` pode ser removido.

---

## Schema do banco

O schema completo está documentado em `backend/src/main/resources/db/migration/V1__init.sql`.  
Cada tabela e coluna tem comentários explicando o propósito e os valores esperados.

| Tabela          | Descrição                                                |
|-----------------|----------------------------------------------------------|
| `jobs`          | Vagas indexadas que passaram no filtro de título e LLM   |
| `applications`  | Candidaturas registradas pelo usuário                    |
| `sync_records`  | Histórico de cada execução do sync                       |
| `user_profile`  | Perfil extraído do currículo (orienta o match do LLM)    |

---

## API

| Método  | Endpoint                                   | Descrição                  |
|---------|--------------------------------------------|----------------------------|
| GET     | /jobs                                      | Lista vagas (limit=50)     |
| GET     | /jobs/{id}                                 | Detalhe de uma vaga        |
| GET     | /applications                              | Histórico de candidaturas  |
| POST    | /applications/{jobId}/apply                | Registrar candidatura      |
| PATCH   | /applications/{jobId}/{appliedAt}/status   | Atualizar status           |
| POST    | /sync                                      | Trigger manual de sync     |
| GET     | /sync                                      | Histórico de syncs         |

---

## Personalizar empresas

Edite `backend/src/main/resources/companies.json`. Cada entrada:

```json
{ "name": "Empresa", "source": "greenhouse", "slug": "slug-no-ats", "domain": "empresa.com" }
```

Fontes suportadas: `greenhouse`, `lever`, `ashby`, `remotive`.

Para descobrir o slug:
- Greenhouse: `boards.greenhouse.io/<slug>`
- Lever: `jobs.lever.co/<slug>`
- Ashby: `jobs.ashbyhq.com/<slug>`

## Deploy (self-hosted)

O backend é um JAR executável padrão. Qualquer VPS, Fly.io, Railway ou servidor próprio serve.

```bash
cd backend
mvn package -DskipTests

# Sobe com variáveis de ambiente
ANTHROPIC_API_KEY=sk-ant-... \
DB_URL=jdbc:mysql://<host>:3306/jobs_tracker \
DB_USER=jobs \
DB_PASSWORD=... \
java -jar target/quarkus-app/quarkus-run.jar
```

O sync automático roda a cada 15 min via Quarkus Scheduler embutido.  
Para desabilitar o scheduler (ex: ambiente de staging): `SYNC_ENABLED=false`.

### Frontend

```bash
cd frontend
VITE_API_URL=https://<seu-backend> npm run build
# Faça deploy da pasta dist/ em qualquer CDN (Netlify, Vercel, Cloudflare Pages)
```


## Portfolio Highlights

- AI-assisted job matching
- Multi-source ATS integrations
- Automated synchronization pipelines
- Full-stack Java + React architecture

See `docs/` for architecture and roadmap.
