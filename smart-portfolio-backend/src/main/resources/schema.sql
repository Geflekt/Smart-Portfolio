CREATE EXTENSION IF NOT EXISTS  vector;

CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    description TEXT NOT NULL,
    tech_stack VARCHAR(255),
    github_url VARCHAR(255),
    live_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS contact_messages(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_name VARCHAR(100) NOT NULL,
    sender_email VARCHAR(255) NOT NULL,
    message_body TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    submitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS resume_embeddings(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    embedding VECTOR(768) NOT NULL,
    metadata JSONB
);

CREATE INDEX IF NOT EXISTS resume_embeddings_hnsw_idx ON resume_embeddings USING hnsw (embedding vector_cosine_ops);

CREATE TABLE IF NOT EXISTS ai_semantic_cache(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prompt_text TEXT NOT NULL,
    prompt_embedding VECTOR(768) NOT NULL,
    cached_response TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sponsors(
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sponsor_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    amount DECIMAL(10 , 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'INR',
    status VARCHAR(50) DEFAULT 'PENDING',
    razorpay_payment_id VARCHAR(255) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    is_processed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    event_id VARCHAR(255) UNIQUE
);

CREATE TABLE IF NOT EXISTS profile (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    role VARCHAR(255),
    education VARCHAR(255),
    bio TEXT,
    email VARCHAR(255),
    phone VARCHAR(50),
    github_url VARCHAR(255),
    linkedin_url VARCHAR(255),
    leetcode_url VARCHAR(255),
    codeforces_url VARCHAR(255),
    resume_url VARCHAR(500),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS experiences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role VARCHAR(255) NOT NULL,
    company VARCHAR(255) NOT NULL,
    period VARCHAR(100),
    description TEXT,
    tech_stack VARCHAR(255),
    sort_order INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO profile (name, role, education, bio, email, phone, github_url, linkedin_url, leetcode_url, codeforces_url, resume_url)
SELECT 'Ayush Tripathi',
       'Backend Developer',
       'B.Tech in CSE @ Gurukul Kangri Deemed to be University',
       NULL,
       'ayush.tripathi0509@gmail.com',
       '+91 8178410657',
       'https://github.com/fierce-adventurer',
       'https://www.linkedin.com/in/ayush-tripathi-89265923a/',
       'https://leetcode.com/u/ayushtripathi2005/',
       'https://codeforces.com/profile/fierce-adventurer',
       'https://drive.google.com/file/d/1mkDrr9xYSplGzt3btQkSZnR-FzJvXJgq/view?usp=drive_link'
WHERE NOT EXISTS (SELECT 1 FROM profile LIMIT 1);

-- Purge any previous hardcoded test records
DELETE FROM experiences WHERE company = 'IIT Ropar' OR role = 'Virtual Software Engineering Intern';
UPDATE profile SET bio = NULL WHERE bio LIKE 'Experienced in building scalable backend systems%';

-- LRU Bio Cache table (stores up to 20 dynamically generated bios)
CREATE TABLE IF NOT EXISTS bio_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bio TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_accessed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS bio_cache_lru_idx ON bio_cache (last_accessed_at ASC);