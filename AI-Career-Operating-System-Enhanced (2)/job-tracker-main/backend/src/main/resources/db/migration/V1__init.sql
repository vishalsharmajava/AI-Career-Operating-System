-- ============================================================
-- V1 — Schema inicial do Jobs Tracker
--
-- Modelo de dados:
--
--   jobs            → vagas indexadas via sync (Greenhouse / Lever / Ashby / Remotive)
--   applications    → candidaturas registradas pelo usuário
--   sync_records    → histórico de cada execução do sync
--   user_profile    → perfil extraído do currículo (1 linha; orienta o match)
--
-- Convenções:
--   • Datas armazenadas como VARCHAR(50) no formato ISO 8601 (ex: 2025-01-15T10:30:00Z)
--     para manter compatibilidade direta com as APIs externas sem conversão.
--   • Colunas de listas (keyPoints, skills, etc.) usam tipo JSON nativo do MySQL 8.
--   • IDs de vagas seguem o padrão  source#externalId  (ex: "greenhouse#7532733").
-- ============================================================


-- ------------------------------------------------------------
-- jobs
--
-- Cada linha é uma vaga que passou pelo filtro de título E pelo
-- match do LLM (Claude Haiku). Vagas irrelevantes nunca chegam aqui.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS jobs (

    -- Chave primária composta pelo ATS de origem e o ID externo da vaga.
    -- Formato: source#externalId  →  ex: "greenhouse#7532733", "lever#abc-123"
    id             VARCHAR(255)  NOT NULL,

    -- ATS de origem: greenhouse | lever | ashby | remotive
    source         VARCHAR(50)   NOT NULL,

    -- ID original da vaga no ATS (sem prefixo de source)
    external_id    VARCHAR(255),

    -- Título da vaga exatamente como retornado pelo ATS
    title          VARCHAR(500),

    -- Nome da empresa
    company        VARCHAR(255),

    -- Domínio da empresa, usado para buscar o logo via Clearbit
    -- Ex: "stripe.com", "datadoghq.com"
    company_domain VARCHAR(255),

    -- URL da página de detalhes da vaga no site do ATS
    url            TEXT,

    -- URL direta para o formulário de candidatura (pode ser igual a url)
    apply_url      TEXT,

    -- URL do logo da empresa (Clearbit ou logo do ATS como fallback)
    logo_url       TEXT,

    -- Localização textual retornada pelo ATS. Ex: "New York, NY", "Remote"
    location       VARCHAR(255),

    -- true se a vaga for remota ou se "remote" aparecer na location
    remote         BOOLEAN       NOT NULL DEFAULT FALSE,

    -- Resumo gerado pelo LLM após análise da descrição original.
    -- Substituído pelo texto bruto antes do match e pelo resumo gerado após.
    summary        TEXT,

    -- Score de compatibilidade atribuído pelo LLM (0–100).
    -- Vagas com score < 40 são descartadas e nunca chegam a esta tabela.
    match_score    INT           NOT NULL DEFAULT 0,

    -- Lista JSON de até 5 pontos-chave da vaga gerados pelo LLM.
    -- Ex: ["Liderança de time distribuído", "Stack Java + Kafka"]
    key_points     JSON,

    -- Lista JSON de até 5 requisitos técnicos extraídos pelo LLM.
    -- Ex: ["Java 17+", "Microserviços", "5+ anos de experiência"]
    requirements   JSON,

    -- Data de publicação da vaga no ATS (ISO 8601).
    -- Usado para filtro de delta no sync (busca apenas vagas após este campo).
    posted_at      VARCHAR(50),

    -- Data em que o sync encontrou esta vaga pela primeira vez.
    -- Serve como fallback de ordenação quando posted_at está ausente.
    first_seen_at  VARCHAR(50),

    -- true após o usuário registrar uma candidatura via POST /applications/{jobId}/apply
    applied        BOOLEAN       NOT NULL DEFAULT FALSE,

    PRIMARY KEY (id),
    INDEX idx_jobs_first_seen_at (first_seen_at),
    INDEX idx_jobs_match_score   (match_score),
    INDEX idx_jobs_source        (source),
    INDEX idx_jobs_applied       (applied)
);


-- ------------------------------------------------------------
-- applications
--
-- Registro de candidaturas feitas pelo usuário. Uma vaga pode ter
-- múltiplas candidaturas (ex: reaplicação após mudança de status).
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS applications (

    -- PK auto-increment; não exposto na API (marcado @JsonIgnore no modelo).
    id         BIGINT        NOT NULL AUTO_INCREMENT,

    -- Referência à vaga em jobs.id. Não é FK com restrição para permitir
    -- que candidaturas sejam mantidas mesmo se a vaga for removida.
    job_id     VARCHAR(255)  NOT NULL,

    -- Snapshot do título da vaga no momento da candidatura.
    -- Guardado aqui para exibir no histórico mesmo se a vaga for removida.
    job_title  VARCHAR(500),

    -- Snapshot do nome da empresa no momento da candidatura.
    company    VARCHAR(255),

    -- URL de candidatura usada (cópia de jobs.apply_url no momento do apply).
    apply_url  TEXT,

    -- Data/hora da candidatura em ISO 8601. Usado como parte do path
    -- na API PATCH /applications/{jobId}/{appliedAt}/status
    applied_at VARCHAR(50)   NOT NULL,

    -- Notas livres do usuário sobre a candidatura (opcional).
    notes      TEXT,

    -- Status atual da candidatura.
    -- Valores: applied | interviewing | offer | rejected | withdrawn
    status     VARCHAR(50)   NOT NULL DEFAULT 'applied',

    PRIMARY KEY (id),
    INDEX idx_applications_job_id     (job_id),
    INDEX idx_applications_applied_at (applied_at),
    INDEX idx_applications_status     (status)
);


-- ------------------------------------------------------------
-- sync_records
--
-- Um registro por execução do sync (automático via EventBridge ou
-- manual via POST /sync). Usado para calcular o delta na próxima
-- execução: só busca vagas publicadas após o último sync bem-sucedido.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sync_records (

    -- PK auto-increment; não exposto na API.
    id              BIGINT       NOT NULL AUTO_INCREMENT,

    -- Timestamp de início do sync em ISO 8601.
    synced_at       VARCHAR(50)  NOT NULL,

    -- Corte de data usado nesta execução:
    --   max(último sync bem-sucedido, agora − 15 dias)
    -- Null no primeiro sync (sem histórico).
    since_date      VARCHAR(50),

    -- Quantidade de vagas novas que passaram no match e foram salvas.
    new_jobs_count  INT          NOT NULL DEFAULT 0,

    -- Total de vagas buscadas nas APIs antes de qualquer filtro.
    total_checked   INT          NOT NULL DEFAULT 0,

    -- Quantidade de vagas que passaram no filtro de título E no LLM.
    -- Igual a new_jobs_count quando não há re-score de vagas existentes.
    matched_count   INT          NOT NULL DEFAULT 0,

    -- ATSs consultados nesta execução, separados por vírgula.
    -- Ex: "greenhouse,lever,ashby,remotive"
    sources         VARCHAR(255),

    -- Resultado geral: success | error
    status          VARCHAR(50)  NOT NULL DEFAULT 'success',

    -- Mensagem de erro quando status = error. Null em caso de sucesso.
    error_message   TEXT,

    PRIMARY KEY (id),
    INDEX idx_sync_records_synced_at (synced_at),
    INDEX idx_sync_records_status    (status)
);


-- ------------------------------------------------------------
-- user_profile
--
-- Perfil do candidato extraído do currículo via LLM.
-- Sempre 1 linha (id = 1). Orienta o system prompt do JobMatcherService:
-- em vez de um perfil hardcoded, o match usa os dados desta tabela.
--
-- Fluxo:
--   POST /resume  →  extrai texto do PDF  →  Claude analisa
--               →  grava aqui  →  próximo sync usa profile_summary + skills
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_profile (

    -- Sempre 1. Singleton por design; INSERT usa id=1, UPDATE sobrescreve.
    id                  INT          NOT NULL DEFAULT 1,

    -- Cargos-alvo do candidato, extraídos pelo LLM do currículo.
    -- Ex: ["Tech Lead", "Staff Engineer", "Principal Engineer"]
    target_roles        JSON,

    -- Habilidades técnicas principais identificadas no currículo.
    -- Ex: ["Java", "Spring Boot", "Quarkus", "Kafka", "AWS", "PostgreSQL"]
    skills              JSON,

    -- Anos de experiência estimados pelo LLM com base no histórico do currículo.
    experience_years    INT,

    -- Preferência de regime de trabalho: remote | hybrid | onsite
    work_preference     VARCHAR(50),

    -- Preferência geográfica. Ex: "São Paulo", "Americas (remote)", "Anywhere"
    location_preference VARCHAR(255),

    -- Pontos de destaque do perfil usados no system prompt.
    -- Ex: ["Liderou migração de monolito para microsserviços",
    --       "Gestão de time de 8 engenheiros"]
    highlights          JSON,

    -- Resumo curto do perfil injetado no system prompt do LLM para o match.
    -- Gerado pelo Claude na análise do currículo.
    -- Ex: "Engenheiro Java Sênior com 12 anos de experiência, especializado em..."
    profile_summary     TEXT,

    -- Última atualização do perfil em ISO 8601.
    updated_at          VARCHAR(50),

    PRIMARY KEY (id)
);
