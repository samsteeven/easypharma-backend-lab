# EasyPharma backend : labo PostgreSQL / SQL Server

Backend Spring Boot d'EasyPharma (projet de cours M1 EADL, voir plus bas), repris ici pour démontrer **le même code sur deux bases**
: PostgreSQL + PostGIS (profil par défaut) et **SQL Server 2022** (profil `sqlserver`, branche `sqlserver`).

## Lancer sur SQL Server
```bash
cp .env.example .env          # renseigner MSSQL_SA_PASSWORD et JWT_SECRET
docker compose --env-file .env -f docker-compose.sqlserver.yml up -d
# créer la base : CREATE DATABASE easypharma_db;
mvn spring-boot:run -Dspring-boot.run.profiles=dev,sqlserver
```
Flyway applique `db/sqlserver/V1` (schéma généré depuis les entités) et `V2` (colonne `geography` + index spatial), puis Hibernate valide le schéma.

## Ce qui change entre les deux bases
| Sujet | PostgreSQL / PostGIS | SQL Server |
|---|---|---|
| Distance | `ST_DistanceSphere(ST_MakePoint(lon, lat), ...)` | `location.STDistance(geography::Point(lat, lon, 4326))` |
| Index | aucun (calcul à la volée) | colonne `location` calculée persistée + index spatial |
| Recherche texte | JPQL `LOWER(...) LIKE` (remplace `ILIKE`) | identique |
| Colonnes longues | `text` | `varchar(max)` (`@JdbcTypeCode(LONGVARCHAR)`) |
| Booléens natifs | `is_available = true` | `is_available = 1` |

Le SQL spatial vit derrière `GeoSearchRepository`, avec une implémentation par profil (`PostgisGeoSearchRepository`, `SqlServerGeoSearchRepository`) ; le reste des requêtes est en JPQL portable.

Pièges rencontrés :
- `geography::Point` prend la **latitude en premier**, à l'inverse de `ST_MakePoint(longitude, latitude)`.
- L'index spatial sur colonne calculée exige `QUOTED_IDENTIFIER ON` : `sqlcmd` doit être lancé avec `-I` (le driver JDBC est bon par défaut).
- Flyway scanne ses dossiers récursivement : les scripts SQL Server sont dans `db/sqlserver`, hors de `db/migration`, sinon les tests H2 les exécutent.

## Tests
- Suite complète (75 tests) sur chaque base, sans modifier les tests : `scripts/run-tests.ps1 -Db h2|postgres|sqlserver` (driver JDBC Testcontainers).
- Contrat géographique identique sur les deux implémentations (`GeoSearchContract`, 5 cas chacun, conteneurs PostGIS 16 et SQL Server 2022) :
  `mvn test -Dtest=*GeoSearchIT`

| Base | Tests | Durée de la suite* |
|---|---|---|
| H2 (mémoire) | 75 / 75 | 46 s |
| PostgreSQL 16 (conteneur) | 75 / 75 | 67 s |
| SQL Server 2022 (conteneur) | 75 / 75 | 88 s |

\* Une mesure par base sur un poste Windows, images Docker déjà téléchargées, compilation déjà faite ; sans `clean`. Ordre de grandeur, pas un benchmark.
Sur PostgreSQL et SQL Server, le schéma de la suite complète est créé par Hibernate ; seuls les tests `*GeoSearchIT` valident les migrations Flyway SQL Server.

## Hors périmètre / limites
- Les anciennes migrations PostgreSQL (`db/migrations`) ne sont toujours pas chargées par Flyway (le dossier configuré est `db/migration`) : héritage du projet d'origine, non traité.
- `JWT_SECRET` est obligatoire (plus de valeur par défaut en clair).

---

# EasyPharma Backend - Système de Gestion de Pharmacie

EasyPharma est une application robuste de gestion de pharmacie et de commande de médicaments en ligne, conçue avec Spring Boot.

## 🚀 Fonctionnalités Principales

- **Authentification & Autorisation** : Sécurisation via JWT (Json Web Token).
- **Gestion de Pharmacie** : Inventaire, employés, et statistiques.
- **Recherche de Médicaments** : Recherche intelligente par nom, symptôme ou proximité géographique.
- **Commandes & Livraison** : Gestion du cycle de vie des commandes et assignation des livreurs.
- **Audit & Sécurité** : Logs d'audit détaillés et gestion granulaire des permissions.
- **WebSocket** : Notifications en temps réel.

## 🛠️ Stack Technique

- **Framework** : Spring Boot 3.x
- **Persistance** : Spring Data JPA, Hibernate
- **Base de Données** : PostgreSQL (Prod/Dev), H2 (Test)
- **Migration** : Flyway
- **Sécurité** : Spring Security, JWT
- **Documentation** : Swagger / OpenAPI
- **Tests** : JUnit 5, Mockito, AssertJ

## ⚙️ Configuration & Installation

### Prérequis
- Java 17+
- Maven 3.6+
- PostgreSQL (Optionnel, H2 par défaut en mode dev)

### Exécution
Pour lancer l'application en mode développement :
```bash
./mvnw spring-boot:run
```

### Tests
Pour exécuter la suite de tests (75 tests unitaires et d'intégration) :
```bash
./mvnw test
```

### Documentation API
Une fois lancée, la doc Swagger est accessible à :
`http://localhost:8080/swagger-ui.html`

## 🏗️ Architecture

Le projet suit une architecture propre en couches :
1. **Presentation** : Contrôleurs REST et DTOs.
2. **Application** : Cas d'utilisation (UseCases) et services applicatifs.
3. **Domain** : Entités JPA, agrégats et interfaces de repository (Cœur métier).
4. **Infrastructure** : Sécurité, configuration, implémentations techniques et adaptateurs.

## 📂 Organisation des Dossiers
- `src/main/java` : Code source organisé par couche et par domaine.
- `src/main/resources` : Configuration YAML et migrations SQL (Flyway).
- `src/test` : Tests unitaires et d'intégration robustes.

---
*Projet réalisé dans le cadre du Semestre 1 - EADL.*
"# test" 
