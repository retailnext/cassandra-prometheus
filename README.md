## Cassandra prometheus exporter

This is a prometheus exporter for cassandra. It works by exporting
dropwizard metrics from the cassandra core in the prometheus
format. It's much more performant both in terms of memory and cpu
resources than the prometheus jmx exporter.

## Building

build the jar file
```
mvn package
```

## How To

Copy the cassandra-prometheus-2.0-SNAPSHOT-jar-with-dependencies.jar
to the deployment and run the cassandra instance with an extra
-javaagent argument. e.g to have metrics at `http://127.0.0.1:7400/metrics`:

```
echo 'JVM_OPTS="$JVM_OPTS -javaagent:/usr/share/cassandra/lib/cassandra-prometheus-2.0-SNAPSHOT-jar-with-dependencies.jar=7400"' >> /etc/cassandra/cassandra-env.sh
```

## Running the integration test

```
cd integrationtest
cp ../target/cassandra-prometheus-2.0-SNAPSHOT-jar-with-dependencies.jar cassandra/
docker-compose pull
docker-compose build
docker-compose up -d
```

Open a browser to the address 127.0.0.1:9090 and see that cassandra gets scraped.
