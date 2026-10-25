-- Équivalent SQL Server de la recherche PostGIS : colonne geography calculée (persistée) + index spatial.
-- geography::Point(latitude, longitude, srid) : latitude en premier (inverse de ST_MakePoint de PostGIS).
ALTER TABLE pharmacies ADD location AS geography::Point(CAST(latitude AS float), CAST(longitude AS float), 4326) PERSISTED;
GO
CREATE SPATIAL INDEX six_pharmacies_location ON pharmacies (location);
