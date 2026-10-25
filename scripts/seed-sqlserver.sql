-- Jeu de données de démonstration pour SQL Server (4 pharmacies dont 1 PENDING, 1 médicament).
-- À lancer avec sqlcmd -I (QUOTED_IDENTIFIER ON, requis par l index spatial).
SET NOCOUNT ON;
DECLARE @m uniqueidentifier = NEWID();
INSERT INTO medications (id,requires_prescription,created_at,updated_at,therapeutic_class,name,generic_name,symptoms)
VALUES (@m,0,SYSDATETIME(),SYSDATETIME(),'ANTALGIQUE','Doliprane','Paracétamol','fièvre, douleur, maux de tête');
DECLARE @p TABLE (n int, nm varchar(50), lat numeric(10,8), lon numeric(11,8), st varchar(20), city varchar(50));
INSERT INTO @p VALUES (1,'Pharmacie Centre',3.8667,11.5167,'APPROVED','Yaoundé'),(2,'Pharmacie Bastos',3.848,11.5021,'APPROVED','Yaoundé'),(3,'Pharmacie Akwa',4.0511,9.7679,'APPROVED','Douala'),(4,'Pharmacie En attente',3.86,11.52,'PENDING','Yaoundé');
DECLARE @n int=1, @u uniqueidentifier;
WHILE @n<=4 BEGIN
  SET @u=NEWID();
  INSERT INTO users (id,is_active,is_verified,created_at,updated_at,phone,role,first_name,last_name,email,password)
   VALUES (@u,1,1,SYSDATETIME(),SYSDATETIME(),CONCAT('69000000',@n),'PHARMACY_ADMIN','T','A',CONCAT('a',@n,'@test.local'),'x');
  INSERT INTO pharmacies (id,user_id,latitude,longitude,created_at,updated_at,phone,status,license_number,city,name,license_document_url,address)
   SELECT NEWID(),@u,lat,lon,SYSDATETIME(),SYSDATETIME(),CONCAT('2',n),st,CONCAT('L',n),city,nm,'u','adr' FROM @p WHERE n=@n;
  SET @n+=1;
END
INSERT INTO pharmacy_medications (id,is_available,price,stock_quantity,created_at,updated_at,medication_id,pharmacy_id)
SELECT NEWID(),1,1000+ROW_NUMBER() OVER (ORDER BY name)*100,10,SYSDATETIME(),SYSDATETIME(),@m,id FROM pharmacies;
SELECT COUNT(*) AS pharmacies FROM pharmacies;
