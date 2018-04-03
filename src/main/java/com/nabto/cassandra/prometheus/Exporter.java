package com.nabto.cassandra.prometheus;

import com.yammer.metrics.Metrics;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.exporter.HTTPServer;
import io.prometheus.client.hotspot.DefaultExports;

import java.lang.instrument.Instrumentation;
import java.net.InetSocketAddress;

public class Exporter {

    public static void premain(String agentArgument, Instrumentation instrumentation) throws Exception {
        // Bind to all interfaces by default (this includes IPv6).
        String host = "0.0.0.0";

        // If we have IPv6 address in square brackets, extract it first and then
        // remove it from arguments to prevent confusion from too many colons.
        Integer indexOfClosingSquareBracket = agentArgument.indexOf("]:");
        if (indexOfClosingSquareBracket >= 0) {
            host = agentArgument.substring(0, indexOfClosingSquareBracket + 1);
            agentArgument = agentArgument.substring(indexOfClosingSquareBracket + 2);
        }

        String[] args = agentArgument.split(":");
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: -javaagent:/path/to/JavaAgent.jar=[host:]<port>");
            System.exit(1);
        }

        int port;
        InetSocketAddress socket;

        if (args.length == 2) {
            port = Integer.parseInt(args[1]);
            socket = new InetSocketAddress(args[0], port);
        } else {
            port = Integer.parseInt(args[0]);
            socket = new InetSocketAddress(host, port);
        }

        new PrometheusExports(Metrics.defaultRegistry()).register();
        DefaultExports.initialize();
        new HTTPServer(socket, CollectorRegistry.defaultRegistry, true);
    }
}
