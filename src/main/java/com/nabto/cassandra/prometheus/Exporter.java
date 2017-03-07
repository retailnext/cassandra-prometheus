package com.nabto.cassandra.prometheus;

import java.lang.instrument.Instrumentation;

public class Exporter 
{
    public static void premain(String args, Instrumentation inst)
    {
        int port = 7400;
        try {
            port = Integer.parseInt(args);
        } catch(Exception e) {
            System.err.println("Usage: <port>. No port specified so using default port for cassandra prometheus exporter " + port);    
        }

        PrometheusExporter exp = new PrometheusExporter(port);
    }
}
