package com.nabto.cassandra.prometheus;

import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.MetricsServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import org.apache.cassandra.metrics.CassandraMetricsRegistry;

public class PrometheusExporter
{
    private Server server;
    
    public PrometheusExporter(int port) {
        server = new Server(port);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        CollectorRegistry collectorRegistry = new CollectorRegistry();
        
        collectorRegistry.register(new DropwizardExports(CassandraMetricsRegistry.Metrics));

        MetricsServlet metricsServlet = new MetricsServlet(collectorRegistry);

        context.addServlet(new ServletHolder(metricsServlet), "/metrics");
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("cannot start metrics http server " + e.getMessage());
        }
    }    
}
