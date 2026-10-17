-- Create the database used by the backend.
-- Tables are created automatically by Hibernate (spring.jpa.hibernate.ddl-auto=update).
CREATE DATABASE IF NOT EXISTS pick_dispatch
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- The application defaults to root/root for local development.
-- Override with DB_URL / DB_USERNAME / DB_PASSWORD environment variables.
