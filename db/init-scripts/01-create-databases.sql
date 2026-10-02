-- Script executado na inicialização do container PostgreSQL
-- Garante que ambos os bancos existam mesmo se o volume for recriado

SELECT 'CREATE DATABASE evolution'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'evolution')\gexec

SELECT 'CREATE DATABASE cefan'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'cefan')\gexec

GRANT ALL PRIVILEGES ON DATABASE evolution TO postgres;
GRANT ALL PRIVILEGES ON DATABASE cefan TO postgres;
