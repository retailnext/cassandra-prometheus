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

Copy the cassandra-prometheus-1.0-SNAPSHOT.jar together with the other
dependencies into the folder /usr/share/cassandra/lib/ on the
cassandra installation. Run the cassandra jvm with an extra -javaagent
parameter 

```
echo 'JVM_OPTS="$JVM_OPTS -javaagent:/usr/share/cassandra/lib/cassandra-prometheus-1.0-SNAPSHOT.jar=7400"' >> /etc/cassandra/cassandra-env.sh
```

## Jar dependencies

  * (http://search.maven.org/remotecontent?filepath=org/eclipse/jetty/aggregate/jetty-all/9.4.2.v20170220/jetty-all-9.4.2.v20170220-uber.jar)
  * (http://search.maven.org/remotecontent?filepath=io/prometheus/simpleclient_dropwizard/0.0.21/simpleclient_dropwizard-0.0.21.jar)
  * (http://search.maven.org/remotecontent?filepath=io/prometheus/simpleclient_servlet/0.0.21/simpleclient_servlet-0.0.21.jar)
  * (http://search.maven.org/remotecontent?filepath=io/prometheus/simpleclient/0.0.21/simpleclient-0.0.21.jar)
  * (http://search.maven.org/remotecontent?filepath=io/prometheus/simpleclient_common/0.0.21/simpleclient_common-0.0.21.jar)


## TODO

build a jar file which has all dependencies which is needed for cassandra excluding cassandra.
