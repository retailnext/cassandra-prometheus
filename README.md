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

Copy the cassandra-prometheus-1.0-SNAPSHOT-jar-with-dependencies.jar
to the deployment and run the cassandra instance with an extra
-javaagent argument. e.g:

```
echo 'JVM_OPTS="$JVM_OPTS -javaagent:/usr/share/cassandra/lib/cassandra-prometheus-1.0-SNAPSHOT-jar-with-dependencies.jar=7400"' >> /etc/cassandra/cassandra-env.sh
```
