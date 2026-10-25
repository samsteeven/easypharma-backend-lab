-- Jeu de données de benchmark, déterministe : 300 médicaments, 2 000 pharmacies (95 % approuvées),
-- 40 offres par pharmacie (80 000 lignes), 1 compte patient (bench@test.local / Test1234!).
-- À lancer sur une base fraîche avec : sqlcmd -I -d easypharma_db -f 65001 -i seed-bench.sql
SET NOCOUNT ON;

DECLARE @hash varchar(100) = '$2a$10$DzDReIMALgTxWnLcCy0emeuLcmXgbbxxah3krC48m5cMIhxOpOjL2'; -- Test1234!

-- Compte patient utilisé par k6 pour obtenir un jeton
INSERT INTO users (id,is_active,is_verified,created_at,updated_at,phone,role,first_name,last_name,email,password)
VALUES (NEWID(),1,1,SYSDATETIME(),SYSDATETIME(),'680000000','PATIENT','Bench','Patient','bench@test.local',@hash);

-- Médicaments : 20 noms x 15 dosages
DECLARE @names TABLE (n int, nm varchar(40), gen varchar(40));
INSERT INTO @names VALUES
 (0,'Paracétamol','Paracétamol'),(1,'Amoxicilline','Amoxicilline'),(2,'Ibuprofène','Ibuprofène'),(3,'Artéméther','Artéméther'),
 (4,'Metformine','Metformine'),(5,'Amlodipine','Amlodipine'),(6,'Cétirizine','Cétirizine'),(7,'Ciprofloxacine','Ciprofloxacine'),
 (8,'Diclofénac','Diclofénac'),(9,'Oméprazole','Oméprazole'),(10,'Azithromycine','Azithromycine'),(11,'Loratadine','Loratadine'),
 (12,'Aspirine','Acide acétylsalicylique'),(13,'Vitamine C','Acide ascorbique'),(14,'Fer','Sulfate ferreux'),(15,'Zinc','Sulfate de zinc'),
 (16,'Salbutamol','Salbutamol'),(17,'Doxycycline','Doxycycline'),(18,'Tramadol','Tramadol'),(19,'Quinine','Quinine');

;WITH k AS (SELECT TOP (300) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) - 1 AS i FROM sys.all_objects a CROSS JOIN sys.all_objects b)
INSERT INTO medications (id,requires_prescription,created_at,updated_at,therapeutic_class,name,generic_name,symptoms,dosage)
SELECT NEWID(), CASE WHEN k.i % 7 = 0 THEN 1 ELSE 0 END, SYSDATETIME(), SYSDATETIME(),
       CHOOSE(k.i % 10 + 1,'ANTALGIQUE','ANTIBIOTIQUE','ANTIPALUDEEN','ANTIHYPERTENSEUR','ANTIINFLAMMATOIRE',
                           'ANTIDIABETIQUE','ANTIHISTAMINIQUE','ANTIPYRETIQUE','VITAMINE','AUTRE'),
       CONCAT(n.nm, ' ', 100 * (k.i / 20 + 1), 'mg'), n.gen,
       CHOOSE(k.i % 4 + 1,'fièvre, douleur, maux de tête','infection, toux','paludisme, fièvre','allergie, démangeaisons'),
       CONCAT(100 * (k.i / 20 + 1), 'mg')
FROM k JOIN @names n ON n.n = k.i % 20;

-- Pharmacies (un propriétaire par pharmacie) réparties entre 3,5 N / 9 E et 6 N / 12 E (Cameroun)
;WITH k AS (SELECT TOP (2000) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) AS i FROM sys.all_objects a CROSS JOIN sys.all_objects b)
SELECT i, NEWID() AS uid, NEWID() AS pid,
       CAST(3.5 + (ABS(CHECKSUM(i, 11)) % 25000) / 10000.0 AS numeric(10,8)) AS lat,
       CAST(9.0 + (ABS(CHECKSUM(i, 29)) % 30000) / 10000.0 AS numeric(11,8)) AS lon
INTO #p FROM k;

INSERT INTO users (id,is_active,is_verified,created_at,updated_at,phone,role,first_name,last_name,email,password)
SELECT uid,1,1,SYSDATETIME(),SYSDATETIME(),CONCAT('7', RIGHT(CONCAT('00000000', i), 8)),'PHARMACY_ADMIN','Pharma','Bench',
       CONCAT('bench', i, '@bench.local'), @hash FROM #p;

INSERT INTO pharmacies (id,user_id,latitude,longitude,created_at,updated_at,phone,status,license_number,city,name,license_document_url,address)
SELECT pid,uid,lat,lon,SYSDATETIME(),SYSDATETIME(),CONCAT('6', RIGHT(CONCAT('00000000', i), 8)),
       CASE WHEN i % 20 = 0 THEN 'PENDING' ELSE 'APPROVED' END, CONCAT('LIC-', i),
       CHOOSE(i % 5 + 1,'Yaoundé','Douala','Bafoussam','Garoua','Bamenda'), CONCAT('Pharmacie Bench ', i), 'u', 'adresse'
FROM #p;

UPDATE u SET pharmacy_id = p.id FROM users u JOIN pharmacies p ON p.user_id = u.id;

-- 40 offres par pharmacie : médicaments (7i + 13k) mod 300, tous distincts car 13 est premier avec 300
;WITH k AS (SELECT TOP (40) ROW_NUMBER() OVER (ORDER BY (SELECT NULL)) - 1 AS k FROM sys.all_objects),
      m AS (SELECT id, ROW_NUMBER() OVER (ORDER BY name, dosage) - 1 AS rn FROM medications)
INSERT INTO pharmacy_medications (id,is_available,price,stock_quantity,created_at,updated_at,medication_id,pharmacy_id)
SELECT NEWID(), CASE WHEN ABS(CHECKSUM(p.i, k.k)) % 10 = 0 THEN 0 ELSE 1 END,
       500 + ABS(CHECKSUM(p.i, k.k, 7)) % 5000, 5 + ABS(CHECKSUM(p.i, k.k, 3)) % 100,
       SYSDATETIME(), SYSDATETIME(), m.id, p.pid
FROM #p p CROSS JOIN k JOIN m ON m.rn = (7 * p.i + 13 * k.k) % 300;

DROP TABLE #p;

SELECT (SELECT COUNT(*) FROM medications) AS medications, (SELECT COUNT(*) FROM pharmacies) AS pharmacies,
       (SELECT COUNT(*) FROM pharmacy_medications) AS offres;
