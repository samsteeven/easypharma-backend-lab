# EasyPharma backend : un même code sur PostgreSQL/PostGIS et SQL Server

API REST Spring Boot de recherche et de commande de médicaments (Cameroun), **portée sur SQL Server 2022** en gardant PostgreSQL + PostGIS.
Le dépôt montre comment une application réelle, écrite pour PostgreSQL, devient portable : migrations, requêtes spatiales, tests sur trois bases.

| | |
|---|---|
| Stack | Java 21, Spring Boot 3.5, Spring Data JPA / Hibernate 6, Spring Security + JWT, Flyway, Testcontainers |
| Bases | PostgreSQL 16 + PostGIS (profil par défaut), **SQL Server 2022** (profil `sqlserver`), H2 (tests) |
| Origine | Projet d'équipe (4 personnes) du cours « Développement Avancé & Usine Logicielle », M1 EADL, 2025-2026 ; dépôt d'origine : [`EasyPharma-backend`](https://github.com/samsteeven/EasyPharma-backend) |
| Ici | L'historique repart de zéro : le dépôt d'origine suivait des fichiers qui n'avaient pas à l'être (fichiers d'environnement, journaux, archives) |

## Ce qui a été fait dans ce dépôt

1. **Profil Spring `sqlserver`** : driver `mssql-jdbc`, dialecte `SQLServerDialect`, Flyway sur `db/sqlserver`, validation Hibernate (`ddl-auto: validate`).
2. **Migrations T-SQL** : `V1` (schéma de base généré depuis les entités JPA, 17 tables) et `V2` (colonne `geography` calculée + index spatial).
3. **Recherche par proximité réécrite** pour SQL Server (`STDistance`) derrière une interface, avec l'implémentation PostGIS conservée.
4. **Requêtes rendues portables** : `ILIKE` et `CAST(... AS text)` remplacés par du JPQL (`LOWER(...) LIKE`), colonnes `TEXT` remplacées par `@JdbcTypeCode(LONGVARCHAR)`.
5. **Tests** : la suite existante (75 tests) tourne sans modification sur H2, PostgreSQL et SQL Server ; un contrat de tests géographiques vérifie les deux implémentations sur de vrais conteneurs.
6. **Correction d'un N+1** dans les recherches géographiques : les requêtes spatiales ne renvoient plus que des identifiants triés, que `GeoEntityLoader` charge ensuite avec leurs relations (`JOIN FETCH`). Avant : 119 requêtes SQL pour 113 pharmacies et 490 pour 238 offres ; après : 7 et 13. Deux tests comptent les requêtes via les statistiques Hibernate et échouent si le N+1 revient. Effet mesuré avec k6 (p95 de la recherche triée par distance : 21,6 s avant, 0,35 s après) dans [`easypharma-dotnet`](https://github.com/samsteeven/easypharma-dotnet/tree/main/bench).
7. **Pile d'observabilité** (dossier [`observability/`](observability/README.md)) : agent OpenTelemetry, Prometheus + VictoriaMetrics, Loki, Tempo et Grafana en une commande `docker compose`, avec 4 indicateurs, 2 tableaux de bord, 3 règles d'alerte et les délais de détection mesurés.

## Ce qui change entre les deux bases

| Sujet | PostgreSQL / PostGIS | SQL Server |
|---|---|---|
| Distance | `ST_DistanceSphere(ST_MakePoint(lon, lat), ST_MakePoint(:lon, :lat))` | `location.STDistance(geography::Point(:lat, :lon, 4326))` |
| Index spatial | aucun (distance calculée à la volée) | colonne `location` calculée et persistée, avec index spatial |
| Recherche texte | JPQL `LOWER(...) LIKE` | identique (JPQL portable) |
| Colonnes longues | `text` | `varchar(max)` |
| Booléen dans du SQL natif | `is_available = true` | `is_available = 1` |
| Identifiants | `uuid` | `uniqueidentifier` |

Le SQL spatial est isolé derrière `GeoSearchRepository` : `PostgisGeoSearchRepository` (`@Profile("!sqlserver")`) et `SqlServerGeoSearchRepository` (`@Profile("sqlserver")`). Les contrôleurs et services ne savent pas quelle base ils utilisent. Le reste des requêtes est en JPQL portable.

### Pièges rencontrés
- **Ordre des coordonnées** : `geography::Point(latitude, longitude, srid)` prend la latitude en premier, à l'inverse de `ST_MakePoint(longitude, latitude)`.
- **`QUOTED_IDENTIFIER`** : l'index spatial sur une colonne calculée exige `SET QUOTED_IDENTIFIER ON`. Le driver JDBC l'active, mais `sqlcmd` non : il faut le lancer avec `-I`, sinon les `INSERT` échouent (erreur 1934).
- **Flyway est récursif** : les scripts SQL Server sont dans `db/sqlserver` et non dans `db/migration/sqlserver`, sinon la suite H2 les exécute et échoue (`UNIQUEIDENTIFIER` inconnu).
- **`TEXT` sous SQL Server** est déprécié ; mapper vers `varchar(max)` via `LONGVARCHAR` règle les deux bases sans `columnDefinition`.

## Démarrage

Prérequis : JDK 21, Maven 3.9+, Docker.

### SQL Server (profil `sqlserver`)
```bash
cp .env.example .env     # renseigner MSSQL_SA_PASSWORD (mot de passe fort) et JWT_SECRET (>= 32 octets, en base64)
docker compose --env-file .env -f docker-compose.sqlserver.yml up -d
docker exec easypharma-mssql /opt/mssql-tools18/bin/sqlcmd -C -I -S localhost -U sa -P "$MSSQL_SA_PASSWORD" \
  -Q "CREATE DATABASE easypharma_db"
# exporter les variables du .env dans le shell (MSSQL_SA_PASSWORD, JWT_SECRET), puis :
mvn spring-boot:run -Dspring-boot.run.profiles=dev,sqlserver
```
Au démarrage, Flyway applique `V1` et `V2`, puis Hibernate valide le schéma.

### PostgreSQL + PostGIS (profil par défaut)
```bash
docker run -d --name easypharma-pg -e POSTGRES_PASSWORD=admin -e POSTGRES_DB=easypharma_db \
  -p 5432:5432 postgis/postgis:16-3.4-alpine
export JWT_SECRET=...    # obligatoire
mvn spring-boot:run
```
La configuration par défaut vise `localhost:5432`, base `easypharma_db`, utilisateur `postgres` (voir `application.yml`). Le schéma y est créé par Hibernate (`ddl-auto: update`) ; les anciennes migrations PostgreSQL de `db/migrations` ne sont pas chargées (voir les limites).

Redis (cache) et RabbitMQ sont prévus par le projet, mais n'ont pas été nécessaires pour les requêtes ci-dessous.

### Essayer la recherche par proximité
Sur SQL Server, charger un jeu de données (4 pharmacies, dont une `PENDING`) :
```bash
docker cp scripts/seed-sqlserver.sql easypharma-mssql:/tmp/seed.sql
docker exec easypharma-mssql /opt/mssql-tools18/bin/sqlcmd -C -I -S localhost -U sa -P "$MSSQL_SA_PASSWORD" \
  -d easypharma_db -f 65001 -i /tmp/seed.sql
```
Puis, depuis Yaoundé (3.855, 11.51), l'endpoint est public :
```bash
curl "http://localhost:8080/api/v1/pharmacies/nearby?latitude=3.855&longitude=11.51&radiusKm=10"    # Bastos, Centre
curl "http://localhost:8080/api/v1/pharmacies/nearby?latitude=3.855&longitude=11.51&radiusKm=400"   # + Akwa (Douala)
curl "http://localhost:8080/api/v1/pharmacies/nearby?latitude=3.855&longitude=11.51&radiusKm=0.5"   # []
```
Les pharmacies sont triées de la plus proche à la plus lointaine ; une pharmacie non approuvée n'apparaît jamais.
La recherche de médicaments triée par distance (`GET /api/v1/patient/search?sortBy=POSTGIS_NEAREST&userLat=..&userLon=..`) demande un jeton JWT (`/api/v1/auth/register`, puis `/login`). La valeur `POSTGIS_NEAREST` garde le nom historique du paramètre, quelle que soit la base.
Documentation interactive : `http://localhost:8080/swagger-ui.html`.

## Tests

```bash
mvn test                                   # suite complète sur H2 (75 tests)
mvn test -Dtest=*GeoSearchIT               # contrat géographique sur PostGIS et SQL Server (conteneurs)
./scripts/run-tests.ps1 -Db postgres       # suite complète sur PostgreSQL (driver JDBC Testcontainers)
./scripts/run-tests.ps1 -Db sqlserver      # suite complète sur SQL Server
```
`run-tests.ps1` ne modifie pas les tests : il surcharge la datasource par des variables d'environnement Spring et laisse le driver `jdbc:tc:` démarrer le conteneur. Lui passer `-Mvn <chemin vers mvn>` si `mvn` n'est pas dans le `PATH`.

`GeoSearchContract` est une classe abstraite de 7 cas (tri par distance, rayon, exclusion des pharmacies non approuvées, recherche insensible à la casse et aux accents, filtre de classe thérapeutique, et 2 garde-fous contre le N+1) ; `PostgisGeoSearchIT` et `SqlServerGeoSearchIT` l'exécutent chacun contre son conteneur. Côté SQL Server, ce test valide aussi les migrations Flyway et le schéma Hibernate.

| Base | Tests | Durée de la suite* |
|---|---|---|
| H2 (en mémoire) | 75 / 75 | 46 s |
| PostgreSQL 16 (conteneur) | 75 / 75 | 67 s |
| SQL Server 2022 (conteneur) | 75 / 75 | 88 s |

\* Une mesure par base, poste Windows, images Docker déjà téléchargées, projet déjà compilé. C'est un ordre de grandeur, pas un benchmark. Sur PostgreSQL et SQL Server, la suite complète crée son schéma via Hibernate ; seuls les tests `*GeoSearchIT` exercent les migrations Flyway SQL Server.

## Structure utile

```
src/main/java/.../domain/search/geo/   GeoSearchRepository + implémentations PostGIS / SQL Server
src/main/resources/db/sqlserver/       V1 (schéma), V2 (geography + index spatial)
src/main/resources/application-sqlserver.yml
docker-compose.sqlserver.yml           SQL Server 2022
scripts/run-tests.ps1                  suite complète sur h2 | postgres | sqlserver
scripts/seed-sqlserver.sql             jeu de données de démonstration
src/test/.../domain/search/geo/        GeoSearchContract et ses deux implémentations
```

Architecture applicative : couches `presentation` (REST, DTO), `application` (cas d'usage), `domain` (entités, repositories, services) et `infrastructure` (sécurité, configuration).

## Limites connues

- **Anciennes migrations PostgreSQL** : `db/migrations` n'est pas chargé par Flyway, dont le dossier configuré est `db/migration`. Héritage du projet d'origine, non traité ici ; c'est pourquoi PostgreSQL repose sur `ddl-auto: update`.
- **Schéma SQL Server généré, pas traduit** : `V1` vient des entités JPA, pas d'une traduction des 13 scripts PostgreSQL (qui utilisent `CREATE EXTENSION`, `::`, `NOW()`).
- **`JWT_SECRET` est obligatoire** : il n'y a plus de clé par défaut dans `application.yml`.
- **Pas de WebSocket** : l'ancien README en annonçait ; le code n'en contient pas.
- **Paiement et SMS simulés** dans le projet d'origine (aucune passerelle réelle).
- **Mesures de durée** : une seule par base.

## Licence

Projet pédagogique, sans licence explicite.
