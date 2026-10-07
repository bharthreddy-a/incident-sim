# Incident simulation: payment -> wallet -> MySQL

## 1. Start MySQL
    docker compose up -d          # loads db/schema.sql (walletdb.wallet_account, rows 1 and 2)

## 2. Build + run wallet (port 8081)
    cd wallet-service && mvn clean package -DskipTests
    java -Xmx512m -jar target/wallet-service-1.0.0.jar

## 3. Build + run payment (port 8080) with a SMALL heap so OOM is reachable
    cd payment-service && mvn clean package -DskipTests
    java -Xmx256m -XX:+HeapDumpOnOutOfMemoryError -jar target/payment-service-1.0.0.jar
    # run from payment-service/ so ./config/simulation.json is found

## 4. Trigger
    curl -X POST localhost:8080/simulation/start                    # uses yml + config/simulation.json
    curl -X POST localhost:8080/simulation/start -H 'Content-Type: application/json' \
         -d '{"requests":300,"concurrency":40,"holdMs":3000,"cpuBurnMs":200,"memoryPerResponseKb":4096}'
    curl localhost:8080/simulation/status
    curl -X POST localhost:8080/simulation/reset                    # frees payment memory

## Watch the row lock waits in MySQL
    SELECT * FROM performance_schema.data_lock_waits;
    SELECT * FROM sys.innodb_lock_waits\G
    SHOW STATUS LIKE 'Innodb_row_lock%';

## Metrics
    curl localhost:8080/actuator/prometheus      # payment
    curl localhost:8081/actuator/prometheus      # wallet
    docker compose up -d                         # also starts Prometheus :9090 and Grafana :3000 (admin/admin)
In Grafana add a Prometheus data source (http://prometheus:9090) and import dashboard ID 4701 (JVM Micrometer).
