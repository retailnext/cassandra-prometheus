FROM cassandra:3.10

COPY cassandra.yaml /etc/cassandra/
COPY cassandra-prometheus-1.0-SNAPSHOT-jar-with-dependencies.jar /usr/share/cassandra/lib/


RUN echo 'JVM_OPTS="$JVM_OPTS -javaagent:/usr/share/cassandra/lib/cassandra-prometheus-1.0-SNAPSHOT-jar-with-dependencies.jar=7400"' >> /etc/cassandra/cassandra-env.sh