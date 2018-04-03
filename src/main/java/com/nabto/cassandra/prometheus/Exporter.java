package com.nabto.cassandra.prometheus;

import java.lang.instrument.Instrumentation;

public class Exporter {
    private static final String USAGE = "Usage: <port>[:<path>]";

    public static void premain(String agentArg, Instrumentation inst) {
        String[] args = agentArg.split(":");
        if (args.length > 2) {
            System.err.println(USAGE);
        }

        int port = 7400;
        try {
            port = Integer.parseInt(args[0]);
        } catch (Exception e) {
            System.err.println(USAGE + ". No port specified so using default port for cassandra prometheus exporter " + port);
        }

        String path = "metrics";
        if (args.length == 2) {
            if (args[1].length() > 0) {
                path = args[1];
            } else {
                System.err.println(USAGE + ". Invalid path defined so using default path for cassandra prometheus exporter: " + path);
            }
        }

        PrometheusExporter exp = new PrometheusExporter(port, path);
    }
}
