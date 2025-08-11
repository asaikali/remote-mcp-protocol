-- PostgreSQL initialization script
-- This script runs when the PostgreSQL container starts for the first time
--
-- NOTE: The main 'dev' database is created automatically by the POSTGRES_DB environment variable.
-- This script demonstrates how to create ADDITIONAL databases in the same PostgreSQL instance.
-- 
-- Use this pattern when you need multiple databases (e.g., separate test database, 
-- multiple microservices sharing one PostgreSQL instance, etc.)

-- Create an additional 'test' database for testing purposes
CREATE DATABASE test
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;

-- Example: Create another database for a different service
-- CREATE DATABASE analytics
--     WITH
--     OWNER = postgres
--     ENCODING = 'UTF8'
--     LC_COLLATE = 'en_US.utf8'
--     LC_CTYPE = 'en_US.utf8'
--     TABLESPACE = pg_default
--     CONNECTION LIMIT = -1;
