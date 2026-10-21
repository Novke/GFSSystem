-- V9: link za poziv u WhatsApp grupu generacije (https://chat.whatsapp.com/<kod>), NULL = nije podešen. Nastavnik ga
-- šalje studentima mejlom, a javna forma za upis ga prikazuje posle uspešne prijave. Kolona je nullable, pa prethodna
-- slika (ddl-auto=validate ne zna za nju, ali je ne smeta) i rollback rade i posle ove migracije.
-- Idempotentno kao V2-V8 (MySQL 8.0 nema ADD COLUMN IF NOT EXISTS, pa preko information_schema.COLUMNS + PREPARE).
SET @ima := (SELECT COUNT(*) FROM information_schema.COLUMNS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'grupe' AND COLUMN_NAME = 'wa_link');
SET @sql := IF(@ima = 0, 'ALTER TABLE grupe ADD COLUMN wa_link varchar(100) DEFAULT NULL', 'DO 0');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
