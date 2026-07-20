-- ============================================================================
-- ECOS PostgreSQL Initialization Script
-- Database: sys_man (development)
-- Creates all 8 schemas in correct dependency order
-- ============================================================================

-- Create database (run as superuser)
-- CREATE DATABASE sys_man WITH ENCODING 'UTF8' LC_COLLATE 'en_US.UTF-8' LC_CTYPE 'en_US.UTF-8';

-- Schema creation order (respecting FK dependencies)
-- 1. ecos_sysman   (no cross-domain deps)
-- 2. ecos_security  (→ ecos_sysman)
-- 3. ecos_data      (→ ecos_sysman)
-- 4. ecos_ontology  (no cross-domain deps at schema level)
-- 5. ecos_knowledge (no cross-domain deps)
-- 6. ecos_ai        (no cross-domain deps)
-- 7. ecos_cognitive (→ ecos_ontology, self-ref)
-- 8. ecos_infra     (no cross-domain deps)

-- Set search_path for development
ALTER DATABASE sys_man SET search_path TO
    ecos_sysman, ecos_security, ecos_data, ecos_ontology,
    ecos_knowledge, ecos_ai, ecos_cognitive, ecos_infra, public;

-- Execute DDL files in order
\i 01_ecos_sysman.sql
\i 02_ecos_security.sql
\i 03_ecos_data.sql
\i 04_ecos_ontology.sql
\i 05_ecos_knowledge.sql
\i 06_ecos_ai.sql
\i 07_ecos_cognitive.sql
\i 08_ecos_infra.sql
