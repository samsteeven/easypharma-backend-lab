#!/bin/sh
# Charge le jeu de données (2 000 pharmacies, 80 000 offres) une seule fois, quand le schéma existe.
set -e
SQLCMD="/opt/mssql-tools18/bin/sqlcmd -C -I -b -S mssql -U sa -P $MSSQL_SA_PASSWORD -d easypharma_db"
n=$($SQLCMD -h -1 -W -Q "SET NOCOUNT ON; SELECT COUNT(*) FROM medications" | tr -d '[:space:]')
if [ "$n" = "0" ]; then
  echo "Chargement du jeu de données"
  $SQLCMD -f 65001 -i /db/seed-bench.sql
else
  echo "Données déjà présentes ($n médicaments)"
fi
