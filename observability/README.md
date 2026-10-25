# Observabilité d'EasyPharma : métriques, traces, journaux et alertes

Pile d'observabilité du backend Spring Boot, démarrable avec une seule commande : **traces et journaux** via l'agent OpenTelemetry, **métriques** via Micrometer / Actuator, stockage dans Prometheus (+ VictoriaMetrics pour le long terme), Tempo et Loki, visualisation dans Grafana, **4 indicateurs, 2 tableaux de bord, 3 règles d'alerte notifiées par Alertmanager** (vers un récepteur local de test).

![Indicateurs de service](docs/dashboard-service.png)

## Architecture

```
                         scrape /actuator/prometheus (5 s)
 backend (Spring Boot) <------------------------------------- Prometheus --remote_write--> VictoriaMetrics
   + agent OpenTelemetry                                           |  règles d'alerte          (rétention 12 mois)
        |  OTLP : traces + journaux                                v
        v                                                      Alertmanager --webhook--> alert-sink (récepteur de test)
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
| Alertmanager | 0.28.1 | reçoit les alertes de Prometheus, regroupe (`group_wait` 5 s) et notifie, résolutions comprises |
| `alert-sink` (Python, bibliothèque standard) | 3.12 | récepteur de test : enregistre chaque notification avec son heure de réception |
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
- Prometheus : <http://localhost:9090> (cibles, règles, alertes) · Alertmanager : <http://localhost:9093> · Notifications reçues : <http://localhost:8099/received> · Backend : <http://localhost:8080>.
- **Tous les ports sont publiés sur `127.0.0.1` uniquement** : rien n'est joignable depuis le réseau.
- Réglage « proche de la production » (collecte et évaluation à 15 s, 10 % des traces) : `docker compose -f docker-compose.yml -f docker-compose.prodlike.yml up -d`.
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

Prometheus envoie ces alertes à **Alertmanager** (`alertmanager/alertmanager.yml` : regroupement par alerte, `group_wait` 5 s, notifications de résolution), qui les poste sur un récepteur local de test (`alert-sink`) ; chaque notification y est enregistrée avec son heure (`GET http://localhost:8099/received`). Pour de vraies notifications, remplacer ce récepteur par un récepteur e-mail, Slack ou PagerDuty dans `alertmanager.yml`.

## Mesures

### p95 d'un endpoint sous charge (k6)
`GET /patient/search?sortBy=POSTGIS_NEAREST` (≈ 230 offres par réponse), 16 utilisateurs virtuels pendant 60 s, backend et SQL Server dans Docker :

| Source | p95 | Détail |
|---|---|---|
| **k6 (client)** | **207 ms** | 6 083 requêtes, 101 req/s, 0 échec, médiane 154 ms, p99 240 ms |
| Prometheus (serveur) | 207 ms | même minute, `histogram_quantile` sur les buckets Micrometer |

Les deux points de vue coïncident, ce qui valide la chaîne de mesure (buckets, requête PromQL). Ce chiffre vient de Docker sur un portable : ne pas le comparer à celui du dépôt `easypharma-dotnet` (processus natifs).

### Délai de détection et de notification des alertes
Chaque panne est injectée à t = 0 ; le script (`measure-alerts.mjs`) relève chaque seconde l'état de l'alerte dans Prometheus et l'heure de réception de la notification dans le récepteur local. Collecte et évaluation toutes les 5 s, `group_wait` d'Alertmanager 5 s. **3 passes par alerte**, chacune repartant d'un état sain (alerte `inactive`, trafic et panne remis à zéro). Valeurs en secondes : médiane, puis [min - max].

| Alerte | Panne injectée | `pending` | `firing` | **Notification reçue** | Retour à `inactive` après réparation |
|---|---|---|---|---|---|
| `BackendDown` | arrêt du backend | 8 [7 - 9] | 38 [37 - 39] | **43** [42 - 43] | 32 [28 - 33] |
| `HighErrorRate` | arrêt de SQL Server, sous 20 req/s | 45 [43 - 45] | 75 [73 - 75] | **79** [77 - 79] | 50 [50 - 55] |
| `HighLatencyP95` | SQL Server limité à 0,3 CPU, sous 20 req/s | 14 [13 - 14] | 74 [74 - 74] | **78** [78 - 79] | 65 [64 - 65] |

Les notifications de résolution arrivent entre 28 et 89 s après la réparation. Résultats bruts : `results/alert-delays.json`.

À lire avec précaution :
- **Ces délais viennent surtout de la configuration**, pas d'une performance de la pile : `for` (30 s ou 1 min), fenêtre de calcul de 1 min, `group_wait` de 5 s. Pour `HighLatencyP95` : 14 s pour que le p95 dépasse 500 ms, plus 60 s de `for` = 74 s, plus 4 s jusqu'à la notification.
- Pour `HighErrorRate`, les 43 à 45 s avant `pending` sont cohérents avec le délai de 30 s d'une requête qui échoue quand la base est coupée (voir plus bas) : une 5xx n'est comptée qu'une fois la réponse renvoyée.
- La régularité (2 s d'écart entre les passes) tient à des conditions identiques d'une passe à l'autre : un seul réglage, une seule machine.
- **Le réglage « proche de la production » (collecte à 15 s) n'a pas été mesuré.**

**Méthode de la panne de latence.** Ma première méthode, 32 utilisateurs en recherche lourde, était mauvaise : le p95 y vaut 432 ms en médiane et ne dépasse 500 ms que 3 % du temps ; trois passes n'ont déclenché **aucune alerte** (scénario `latency-load32` dans les résultats), alors qu'une mesure précédente, faite quand la machine était plus chargée, avait fini par déclencher l'alerte à 250 s. Le résultat dépendait de l'état de la machine, pas du service. Elle est remplacée par une panne déterministe : SQL Server limité à 0,3 CPU sous un trafic faible et constant (le p95 passe de 67 ms à 747 ms en 12 s).
Les 3 passes de `HighLatencyP95` ont été faites avec la **pile allégée** (Grafana, Loki, Tempo, VictoriaMetrics et collecteur arrêtés), car la machine de test (15 Go) manquait de mémoire ; l'évaluation et la notification des alertes ne dépendent pas de ces services. Les deux autres alertes ont été mesurées avec la pile complète.

## Ce que l'observabilité a montré

- **Un N+1 devient visible dans une trace.** Après la correction, la trace d'une recherche contient 12 spans dont 3 requêtes SQL (20 ms sur 147 ms au total) ; avant la correction, l'API Java émettait 490 requêtes SQL pour la même réponse (comptées côté SQL Server). Les 127 ms restants ne sont pas du SQL (mapping, sérialisation de 238 offres, filtres de sécurité) ; je ne les ai pas décomposés davantage.
- **Quand la base tombe, les requêtes attendent 30 s avant d'échouer.** Avec SQL Server arrêté, le p95 observé atteint 30,00 s, ce qui correspond exactement au `connection-timeout` de HikariCP (30 000 ms) configuré dans `application.yml` : les requêtes attendent vraisemblablement une connexion pendant ce délai avant de renvoyer une 500 (je n'ai pas isolé la trace d'une de ces requêtes). Conséquences : l'alerte de latence se déclenche en même temps que celle des erreurs, et le taux d'erreurs n'augmente qu'après ces 30 s. Un délai de quelques secondes ferait échouer vite et détecter plus tôt ; je ne l'ai pas modifié.
- **Journaux et traces sont liés** : les journaux d'une requête portent son `trace_id` (3 lignes retrouvées pour la trace d'une recherche), et Tempo propose un lien vers eux depuis un span.

## Limites

- **Le volet facultatif Elasticsearch (journaux d'audit et deux règles de détection) n'est pas réalisé** : Docker dispose de 8 Go ici, et Elasticsearch, gourmand en mémoire, s'ajouterait aux 2,6 Go de la pile au repos : je ne l'ai pas tenté.
- **Alertmanager ne notifie qu'un récepteur local de test** : pas d'e-mail, de Slack ni d'astreinte ; regroupement par nom d'alerte, sans silences ni inhibitions.
- **Réglages de laboratoire par défaut** : collecte à 5 s, échantillonnage des traces à 100 %, mots de passe via `.env`. Le fichier `docker-compose.prodlike.yml` (15 s, 10 % des traces) est fourni mais ses effets n'ont pas été mesurés ; les ports sont limités à `127.0.0.1`. Ne pas exposer tel quel.
- **Mémoire de Tempo** : avec 100 % des traces, il est monté à 1,6 Go sous charge (contre 0,2 Go au repos). Le prévoir, ou échantillonner, avant toute utilisation durable.
- **Trois passes par alerte**, sur une seule machine (portable) qui a manqué de mémoire pendant la campagne.
- **VictoriaMetrics** : la réception des données (remote_write) et la lecture par Grafana sont vérifiées ; la rétention de 12 mois n'a évidemment pas été éprouvée dans le temps.
- Une seule instance de chaque composant, sans haute disponibilité ni authentification entre eux.
- Seul le backend Java est instrumenté (pas l'API .NET du dépôt `easypharma-dotnet`).

## Reproduire les mesures d'alerte

```bash
node measure-alerts.mjs error 3     # coupe SQL Server sous trafic            -> HighErrorRate   (3 passes)
node measure-alerts.mjs down 3      # arrête le backend                       -> BackendDown
node measure-alerts.mjs slowdb 3    # limite SQL Server à 0,3 CPU sous trafic -> HighLatencyP95
```
Le script lance lui-même le trafic k6 quand il en faut, attend que l'alerte soit retombée à `inactive` avant chaque injection, relève l'état chaque seconde (avec un délai d'attente sur chaque appel), répare, et ajoute chaque passe à `results/alert-delays.json` (`LABEL=...` décrit le réglage mesuré). `slowdb` recrée le conteneur SQL Server à la fin, car `docker update --cpus 0` ne lève pas une limite posée.
