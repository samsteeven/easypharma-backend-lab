# Lance la suite de tests complète sur H2 (défaut), PostgreSQL ou SQL Server via le driver JDBC Testcontainers.
# Usage : .\scripts\run-tests.ps1 -Db h2|postgres|sqlserver [-Mvn <chemin mvn.cmd>]
# Les tests ne changent pas : seules des variables d'environnement Spring surchargent application-test.yml.
param(
    [ValidateSet('h2', 'postgres', 'sqlserver')][string]$Db = 'h2',
    [string]$Mvn = 'mvn'
)

if (-not $env:JWT_SECRET) {
    # Secret jetable, propre à ce lancement : aucune valeur fixe dans le dépôt (36 caractères distincts, assez pour HS256).
    $env:JWT_SECRET = -join ((48..57) + (97..122) | Get-Random -Count 64 | ForEach-Object { [char]$_ })
}
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
