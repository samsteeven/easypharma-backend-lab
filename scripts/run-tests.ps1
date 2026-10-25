# Lance la suite de tests complète sur H2 (défaut), PostgreSQL ou SQL Server via le driver JDBC Testcontainers.
# Usage : .\scripts\run-tests.ps1 -Db h2|postgres|sqlserver [-Mvn <chemin mvn.cmd>]
# Les tests ne changent pas : seules des variables d'environnement Spring surchargent application-test.yml.
param(
    [ValidateSet('h2', 'postgres', 'sqlserver')][string]$Db = 'h2',
    [string]$Mvn = 'mvn'
)

$env:JWT_SECRET = $env:JWT_SECRET ? $env:JWT_SECRET : 'dGVzdC1vbmx5LXNlY3JldC1rZXktZm9yLWVhc3lwaGFybWEtdGVzdHM='
switch ($Db) {
    'postgres' {
        $env:SPRING_DATASOURCE_URL = 'jdbc:tc:postgresql:16-alpine:///easypharma_test'
        $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.testcontainers.jdbc.ContainerDatabaseDriver'
        $env:SPRING_DATASOURCE_USERNAME = 'test'
        $env:SPRING_DATASOURCE_PASSWORD = 'test'
        $env:SPRING_JPA_DATABASE_PLATFORM = 'org.hibernate.dialect.PostgreSQLDialect'
    }
    'sqlserver' {
        $env:SPRING_DATASOURCE_URL = 'jdbc:tc:sqlserver:2022-latest:///'
        $env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'org.testcontainers.jdbc.ContainerDatabaseDriver'
        $env:SPRING_JPA_DATABASE_PLATFORM = 'org.hibernate.dialect.SQLServerDialect'
    }
}

$sw = [Diagnostics.Stopwatch]::StartNew()
& $Mvn test -B
"Suite $Db : $([int]$sw.Elapsed.TotalSeconds) s (code retour $LASTEXITCODE)"
