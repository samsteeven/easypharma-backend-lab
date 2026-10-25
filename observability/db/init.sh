#!/bin/sh
# Crée la base vide ; Flyway (profil sqlserver) crée ensuite le schéma au démarrage du backend.
set -e
SQLCMD="/opt/mssql-tools18/bin/sqlcmd -C -I -b -S mssql -U sa -P $MSSQL_SA_PASSWORD"
$SQLCMD -Q "IF DB_ID('easypharma_db') IS NULL CREATE DATABASE easypharma_db"
echo "Base easypharma_db prête"
