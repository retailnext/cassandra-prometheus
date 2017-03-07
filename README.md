## Cassandra prometheus exporter

The cassandra prometheus exporter is a prometheus exporter for
cassandra. It works by exporting dropwizard metrics from the cassandra
core in the prometheus format. It's much more performant than the
prometheus jmx exporter.

build the jar file
```
mvn package
```

## How To

Copy the cassandra-prometheus-1.0-SNAPSHOT-jar-with-dependencies.jar together with the other
dependencies into the folder /usr/share/cassandra/lib/ on the
cassandra installation. Run the cassandra jvm with an extra -javaagent
parameter 

```
echo 'JVM_OPTS="$JVM_OPTS -javaagent:/usr/share/cassandra/lib/cassandra-prometheus-1.0-SNAPSHOT-jar-with-dependencies.jar=7400"' >> /etc/cassandra/cassandra-env.sh
```
