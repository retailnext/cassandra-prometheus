package com.nabto.cassandra.prometheus;

import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.MetricsServlet;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import org.apache.cassandra.metrics.CassandraMetricsRegistry;

public class App 
{
    public static void main( String[] args ) throws Exception
    {
        int port = 7400;
        if (args.length < 1) {
            System.err.println("Usage: WebServer <port>. No port specified so using default port for cassandra prometheus exporter " + port);
        } else {
            port = Integer.parseInt(args[0]);
        }

        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        CollectorRegistry collectorRegistry = new CollectorRegistry();
        
        collectorRegistry.register(new DropwizardExports(CassandraMetricsRegistry.Metrics));

        MetricsServlet metricsServlet = new MetricsServlet(collectorRegistry);

        context.addServlet(new ServletHolder(metricsServlet), "/metrics");
        
        server.start();
        server.join();
    }
}
