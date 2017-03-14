package com.nabto.cassandra.prometheus;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.MetricsServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import org.apache.cassandra.metrics.CassandraMetricsRegistry;

public class PrometheusExporter
{
    private Server server;
    
    public PrometheusExporter(int port) {
        
        QueuedThreadPool threadPool = new QueuedThreadPool(25);
        server = new Server(threadPool);

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        CollectorRegistry collectorRegistry = new CollectorRegistry();
        
        collectorRegistry.register(new PrometheusExports(CassandraMetricsRegistry.Metrics));

        MetricsServlet metricsServlet = new MetricsServlet(collectorRegistry);

        context.addServlet(new ServletHolder(metricsServlet), "/metrics");
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("cannot start metrics http server " + e.getMessage());
        }
    }
}
