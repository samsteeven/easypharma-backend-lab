# Observabilité d'EasyPharma : métriques, traces, journaux et alertes

Pile d'observabilité du backend Spring Boot, démarrable avec une seule commande : **traces et journaux** via l'agent OpenTelemetry, **métriques** via Micrometer / Actuator, stockage dans Prometheus (+ VictoriaMetrics pour le long terme), Tempo et Loki, visualisation dans Grafana, **4 indicateurs, 2 tableaux de bord, 3 règles d'alerte**.

![Indicateurs de service](docs/dashboard-service.png)

## Architecture

```
                         scrape /actuator/prometheus (5 s)
 backend (Spring Boot) <------------------------------------- Prometheus --remote_write--> VictoriaMetrics
   + agent OpenTelemetry                                           |  règles d'alerte          (rétention 12 mois)
        |  OTLP : traces + journaux                                |
        v                                                          v
 OTel Collector --traces--> Tempo        Grafana  <--- Prometheus, VictoriaMetrics, Tempo, Loki
                --journaux-> Loki
```

| Composant | Version | Rôle |
|---|---|---|
| Agent OpenTelemetry Java | 2.31.1 | traces (HTTP, JDBC) et journaux du backend, sans modifier le code |
| OpenTelemetry Collector (contrib) | 0.120.0 | reçoit l'OTLP, route les traces vers Tempo et les journaux vers Loki |
| Prometheus | 3.2.1 | collecte des métriques Micrometer, règles d'alerte, rétention 2 jours |
| VictoriaMetrics | 1.113.0 | copie longue durée (remote_write), rétention 12 mois |
| Tempo | 2.7.1 | stockage des traces |
| Loki | 3.4.2 | stockage des journaux (OTLP natif) |
| Grafana | 11.5.2 | 4 sources de données et 2 tableaux de bord provisionnés |

Les métriques passent par Micrometer plutôt que par l'agent (`OTEL_METRICS_EXPORTER=none`) : le backend les exposait déjà, ce qui évite deux séries pour la même mesure.

## Démarrer

Prérequis : Docker. Testé avec 8 Go de mémoire alloués ; la pile occupait environ 2,6 Go au repos (SQL Server 1,3 Go, backend 0,7 Go, le reste moins de 0,6 Go) et davantage sous charge, sans que je l'aie mesuré.

```bash
cd observability
cp .env.example .env      # MSSQL_SA_PASSWORD, JWT_SECRET, GRAFANA_ADMIN_PASSWORD
docker compose up -d --build
docker compose --profile load run --rm k6          # 3 minutes de trafic à 20 requêtes/s
```
- Grafana : <http://localhost:3000> (`admin` / mot de passe du `.env`), dossier « EasyPharma ».
- Prometheus : <http://localhost:9090> (cibles, règles, alertes) · Backend : <http://localhost:8080>.
- Au premier démarrage, Docker télécharge les images et compile le backend (plusieurs minutes), puis charge 2 000 pharmacies et 80 000 offres.
- Réduire le volume de traces sous forte charge : `TRACE_SAMPLE_RATIO=0.1 docker compose up -d backend`.

Ce qui a été ajouté au backend : le profil `observability` (`application-observability.yml` : histogrammes de latence, étiquette `application`, journaux moins bavards) et une métrique de volume dans `PatientSearchController`. Rien d'autre.

## Les 4 indicateurs

| Indicateur | Définition (PromQL, fenêtre 1 min) | Seuil d'alerte |
|---|---|---|
| **Temps de traitement** | `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{...}[1m])))` | p95 > 500 ms pendant 1 min |
| **Volumes** | `easypharma_search_results_offres` : offres renvoyées par recherche (compteur personnalisé, par type de tri) | aucun (indicateur de capacité) |
| **Erreurs** | part des réponses 5xx : `rate(...{status=~"5.."}) / rate(...)` | > 5 % pendant 30 s |
| **Fréquence** | `sum(rate(http_server_requests_seconds_count{...}[1m]))` | aucun |

`/actuator/*` est exclu de tous les indicateurs.

## Les 2 tableaux de bord (JSON provisionnés dans `grafana/dashboards/`)

1. **Indicateurs de service** (`easypharma-sli.json`) : les 4 indicateurs en tuiles et en courbes (global et par endpoint), alertes en cours, journaux d'erreurs (Loki) et traces lentes (Tempo, avec lien vers les journaux de la trace).
2. **JVM, pool de connexions et base** (`easypharma-jvm.json`) : CPU, heap, ramasse-miettes, threads, pool HikariCP (actives, en attente, temps d'attente, durée d'utilisation) et un panneau d'historique 24 h lu dans VictoriaMetrics.

![JVM, pool et base](docs/dashboard-jvm.png)

## Les 3 règles d'alerte (`prometheus/alerts.yml`)

| Alerte | Condition | `for` | Gravité |
|---|---|---|---|
| `BackendDown` | `up{job="backend"} == 0` | 30 s | critical |
| `HighErrorRate` | part des 5xx > 5 % | 30 s | critical |
| `HighLatencyP95` | p95 > 500 ms | 1 min | warning |

Aucune destination de notification n'est configurée (pas d'Alertmanager) : les alertes se consultent dans Prometheus et Grafana.

## Mesures

### p95 d'un endpoint sous charge (k6)
`GET /patient/search?sortBy=POSTGIS_NEAREST` (≈ 230 offres par réponse), 16 utilisateurs virtuels pendant 60 s, backend et SQL Server dans Docker :

| Source | p95 | Détail |
|---|---|---|
| **k6 (client)** | **207 ms** | 6 083 requêtes, 101 req/s, 0 échec, médiane 154 ms, p99 240 ms |
| Prometheus (serveur) | 207 ms | même minute, `histogram_quantile` sur les buckets Micrometer |

Les deux points de vue coïncident, ce qui valide la chaîne de mesure (buckets, requête PromQL). Ce chiffre vient de Docker sur un portable : ne pas le comparer à celui du dépôt `easypharma-dotnet` (processus natifs).

### Délai de détection des alertes
Chaque panne est injectée à t = 0, l'état est relevé toutes les secondes dans l'API Prometheus (`measure-alerts.mjs`). Collecte et évaluation toutes les 5 s.

| Alerte | Panne injectée | `pending` | **`firing`** | Retour à `inactive` après réparation |
|---|---|---|---|---|
| `BackendDown` | arrêt du backend | 10 s | **40 s** | 62 s |
| `HighErrorRate` | arrêt de SQL Server, sous 20 req/s | 41 s | **71 s** | 98 s |
| `HighLatencyP95` | 32 utilisateurs en recherche triée par distance | 9 s | **250 s** | 3 s |

À lire avec précaution :
- Pour `BackendDown`, le délai est essentiellement la durée `for` (30 s) plus l'intervalle de collecte : c'est un paramètre, pas une performance.
- Pour `HighErrorRate`, les 41 s avant `pending` sont cohérents avec le délai de 30 s d'une requête qui échoue quand la base est coupée (voir plus bas) : une 5xx n'est comptée qu'une fois la réponse renvoyée.
- Pour `HighLatencyP95`, le p95 oscille **autour du seuil** (0,40 à 0,92 s) sous cette charge : l'alerte retombe trois fois de `pending` à `inactive` et ne se déclenche que lorsque le p95 reste enfin au-dessus de 500 ms pendant une minute entière. Un seuil placé près de la valeur de fonctionnement normal d'une charge élevée fait « clignoter » l'alerte ; le délai de 250 s mesure surtout cela.
- Une seule mesure par alerte, sur une machine qui sert aussi à autre chose.

## Ce que l'observabilité a montré

- **Un N+1 devient visible dans une trace.** Après la correction, la trace d'une recherche contient 12 spans dont 3 requêtes SQL (20 ms sur 147 ms au total) ; avant la correction, l'API Java émettait 490 requêtes SQL pour la même réponse (comptées côté SQL Server). Les 127 ms restants ne sont pas du SQL (mapping, sérialisation de 238 offres, filtres de sécurité) ; je ne les ai pas décomposés davantage.
- **Quand la base tombe, les requêtes attendent 30 s avant d'échouer.** Avec SQL Server arrêté, le p95 observé atteint 30,00 s, ce qui correspond exactement au `connection-timeout` de HikariCP (30 000 ms) configuré dans `application.yml` : les requêtes attendent vraisemblablement une connexion pendant ce délai avant de renvoyer une 500 (je n'ai pas isolé la trace d'une de ces requêtes). Conséquences : l'alerte de latence se déclenche en même temps que celle des erreurs, et le taux d'erreurs n'augmente qu'après ces 30 s. Un délai de quelques secondes ferait échouer vite et détecter plus tôt ; je ne l'ai pas modifié.
- **Journaux et traces sont liés** : les journaux d'une requête portent son `trace_id` (3 lignes retrouvées pour la trace d'une recherche), et Tempo propose un lien vers eux depuis un span.

## Limites

- **Le volet facultatif Elasticsearch (journaux d'audit et deux règles de détection) n'est pas réalisé** : Docker dispose de 8 Go ici, et Elasticsearch, gourmand en mémoire, s'ajouterait aux 2,6 Go de la pile au repos : je ne l'ai pas tenté.
- **Pas d'Alertmanager** : aucune notification n'est envoyée.
- **Réglages de laboratoire** : intervalle de collecte de 5 s (15 à 30 s en production), échantillonnage des traces à 100 %, mots de passe via `.env`, ports publiés sur la machine hôte. Ne pas exposer tel quel.
- **VictoriaMetrics** : la réception des données (remote_write) et la lecture par Grafana sont vérifiées ; la rétention de 12 mois n'a évidemment pas été éprouvée dans le temps.
- Une seule instance de chaque composant, sans haute disponibilité ni authentification entre eux.
- Seul le backend Java est instrumenté (pas l'API .NET du dépôt `easypharma-dotnet`).

## Reproduire les mesures d'alerte

```bash
docker compose --profile load run -d --rm k6                 # trafic en fond (pour error et down)
node measure-alerts.mjs error      # coupe SQL Server        -> HighErrorRate
node measure-alerts.mjs down       # arrête le backend       -> BackendDown
VUS=32 node measure-alerts.mjs latency                       # -> HighLatencyP95 (lance sa propre charge)
```
Le script attend que l'alerte soit retombée à `inactive` avant d'injecter la panne, relève l'état chaque seconde (avec un délai d'attente sur chaque appel) et répare à la fin.
