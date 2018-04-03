package com.nabto.cassandra.prometheus;

import com.yammer.metrics.Metrics;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.MetricsServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

public class PrometheusExporter {
    private Server server;

    public PrometheusExporter(int port, String path) {

        QueuedThreadPool threadPool = new QueuedThreadPool(25);
        server = new Server(threadPool);

        ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.addConnector(connector);

        ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/");
        server.setHandler(context);

        CollectorRegistry collectorRegistry = new CollectorRegistry();

        collectorRegistry.register(new PrometheusExports(Metrics.defaultRegistry()));

        MetricsServlet metricsServlet = new MetricsServlet(collectorRegistry);

        context.addServlet(new ServletHolder(metricsServlet), "/" + path);
        try {
            server.start();
        } catch (Exception e) {
            System.err.println("cannot start metrics http server " + e.getMessage());
        }
    }
}
