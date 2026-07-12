-- ============================================================
-- Separate database owned by the intelipath-service scraper.
-- Kept apart from the main "intelipath" DB so the service has no
-- schema coupling; it shares the same Postgres server only.
-- The service (SQLAlchemy) creates its own tables on startup.
-- Runs before 01_init_intelipath.sql on a fresh volume.
-- ============================================================
CREATE DATABASE scraper;
