-- PostgreSQL initialization script
-- This script runs when the PostgreSQL container starts for the first time
-- Creates a generic 'dev' database for development
-- Customize database names by editing this file

CREATE DATABASE dev
    WITH
    OWNER = postgres
    ENCODING = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE = 'en_US.utf8'
    TABLESPACE = pg_default
    CONNECTION LIMIT = -1;
