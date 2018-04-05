package com.nabto.cassandra.prometheus;

import com.codahale.metrics.Metric;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class MetricMetadata {
    private static final Logger LOGGER = Logger.getLogger(MetricMetadata.class.getName());

    private static Pattern datacenterLatencyPattern = Pattern.compile("^(.+)-Latency$");
    private static Pattern dotAllPattern = Pattern.compile("\\.all$");
    private static Pattern legalNamePattern = Pattern.compile("^[a-zA-Z_:][a-zA-Z0-9_:]*$");
    private static Pattern minuteHitRatePattern = Pattern.compile("MinuteHitRate\\.");
    private static Pattern nameHyphenAddressPattern = Pattern.compile("^([^.\\-]+)-(.+)$");
    private static Pattern partAndRestPattern = Pattern.compile("^([^.]+)\\.(.+)$");
    private static Pattern parts2Pattern = Pattern.compile("^([^.]+)\\.([^.]+)$");
    private static Pattern parts3Pattern = Pattern.compile("^([^.]+)\\.([^.]+)\\.([^.]+)$");
    private static Pattern parts4Pattern = Pattern.compile("^([^.]+)\\.([^.]+)\\.([^.]+)\\.([^.]+)$");
    private static Pattern typeAndRestPattern = Pattern.compile("^org\\.apache\\.cassandra\\.metrics\\.([^.]+)\\.(.+)$");
    private static Pattern unqualifiedReadOrWritePattern = Pattern.compile("Read$|Write$");

    private List<String> labelNames = new ArrayList<String>();
    private List<String> labelValues = new ArrayList<String>();
    private String name;
    private String dropwizardName;

    private boolean doNotReport;

    // convert cassandra metric names to prometheus names and label pairs
    MetricMetadata(String dropwizardName) {
        this.dropwizardName = dropwizardName;
        Matcher typeAndRestMatcher = typeAndRestPattern.matcher(dropwizardName);
        if (!typeAndRestMatcher.find()) {
            LOGGER.warning("unhandled metric: " + dropwizardName);
            doNotReport = true;
            return;
        }
        String type = typeAndRestMatcher.group(1);
        String rest = typeAndRestMatcher.group(2);

        Matcher m;
        switch (type) {
            case "BufferPool":
            case "Client":
            case "CommitLog":
            case "Compaction":
                // Note: Compaction.PendingTasksByTableName exists, but that gets dropped because of the gauge type.
            case "CQL":
            case "HintsService":
            case "Index":
            case "MemtablePool":
            case "ReadRepair":
            case "Storage":
            case "Streaming":
                // This and preceding cases are OK to handle generically.
                name = String.format("cassandra_%s_%s", CamelCase.toSnakeCase(type), CamelCase.toSnakeCase(rest));
                break;
            case "keyspace":
                doNotReport = true;
                break;
            case "IndexTable":
                m = parts4Pattern.matcher(rest);
                if (!m.find()) {
                    LOGGER.warning("unhandled metric: " + dropwizardName);
                    doNotReport = true;
                    break;
                }
                name = "cassandra_" + CamelCase.toSnakeCase(m.group(1));
                labelNames.add("keyspace");
                labelValues.add(m.group(2));
                labelNames.add("table");
                labelValues.add(m.group(3) + "." + m.group(4));
                break;
            case "Table":
                m = parts3Pattern.matcher(rest);
                if (!m.find()) {
                    // Silently drop rolled-up metrics.
                    m = dotAllPattern.matcher(rest);
                    if (!m.find()) {
                        LOGGER.warning("unhandled metric: " + dropwizardName);
                    }
                    doNotReport = true;
                    break;
                }
                name = "cassandra_" + CamelCase.toSnakeCase(m.group(1));
                labelNames.add("keyspace");
                labelValues.add(m.group(2));
                labelNames.add("table");
                labelValues.add(m.group(3));
                break;
            case "DroppedMessage":
                // org.apache.cassandra.metrics.DroppedMessage.CrossNodeDroppedLatency.BATCH_REMOVE
                m = parts2Pattern.matcher(rest);
                if (!m.find()) {
                    LOGGER.warning("unhandled metric: " + dropwizardName);
                    doNotReport = true;
                    break;
                }
                name = "cassandra_dropped_message";
                labelNames.add("type");
                labelValues.add(m.group(2).toLowerCase());
                break;
            case "ThreadPools":
                // org.apache.cassandra.metrics.ThreadPools.TotalBlockedTasks.transport.Native-Transport-Requests
                m = parts3Pattern.matcher(rest);
                if (!m.find()) {
                    LOGGER.warning("unhandled metric: " + dropwizardName);
                    doNotReport = true;
                    break;
                }
                name = "cassandra_thread_pool_" + CamelCase.toSnakeCase(m.group(1));
                labelNames.add("pool_type");
                labelValues.add(m.group(2));
                labelNames.add("pool");
                labelValues.add(m.group(3));
                break;
            case "Cache":
                // org.apache.cassandra.metrics.Cache.HitRate.ChunkCache
                // org.apache.cassandra.metrics.Cache.FifteenMinuteHitRate.CounterCache
                m = parts2Pattern.matcher(rest);
                if (!m.find()) {
                    LOGGER.warning("unhandled metric: " + dropwizardName);
                    doNotReport = true;
                    break;
                }
                name = "cassandra_cache_" + CamelCase.toSnakeCase(m.group(1));
                labelNames.add("cache");
                labelValues.add(m.group(2));
                m = minuteHitRatePattern.matcher(rest);
                if (m.find()) {
                    // Drop "XMinuteHitRate"
                    doNotReport = true;
                }
                break;
            case "ClientRequest":
                m = parts2Pattern.matcher(rest);
                if (!m.find()) {
                    LOGGER.warning("unhandled metric: " + dropwizardName);
                    doNotReport = true;
                    break;
                }
                name = "cassandra_client_request" + CamelCase.toSnakeCase(m.group(1));
                labelNames.add("operation");
                labelValues.add(m.group(2).toLowerCase());
                m = unqualifiedReadOrWritePattern.matcher(rest);
                if (m.find()) {
                    // Drop rolled-up operations.
                    doNotReport = true;
                }
                break;
            case "Connection":
                // org.apache.cassandra.metrics.Connection.Timeouts.10.7.162.250
                // org.apache.cassandra.metrics.Connection.TotalTimeouts
                m = partAndRestPattern.matcher(rest);
                if (!m.find()) {
                    // Drop: org.apache.cassandra.metrics.Connection.TotalTimeouts
                    doNotReport = true;
                    break;
                }
                name = "cassandra_outbound_" + CamelCase.toSnakeCase(m.group(1));
                labelNames.add("address");
                labelValues.add(m.group(2));
                break;
            case "HintedHandOffManager":
                m = nameHyphenAddressPattern.matcher(rest);
                if (!m.find()) {
                    LOGGER.warning("unhandled metric: " + dropwizardName);
                    doNotReport = true;
                    break;
                }
                name = "cassandra_" + CamelCase.toSnakeCase(m.group(1));
                labelNames.add("address");
                labelValues.add(m.group(2));
                break;
            case "Messaging":
                if (rest.equals("CrossNodeLatency")) {
                    // Drop the rolled-up metric, report per-datacenter.
                    doNotReport = true;
                    break;
                }
                m = datacenterLatencyPattern.matcher(rest);
                if (!m.find()) {
                    LOGGER.warning("unhandled metric: " + dropwizardName);
                    doNotReport = true;
                    break;
                }
                name = "cassandra_cross_node_latency";
                labelNames.add("datacenter");
                labelValues.add(m.group(1));
                break;
            default:
                LOGGER.warning("unhandled metric: " + dropwizardName);
                doNotReport = true;
        }

        if (!doNotReport && !legalNamePattern.matcher(name).find()) {
            LOGGER.warning("generated illegal metric name for metric: " + dropwizardName);
            doNotReport = true;
        }
    }

    List<String> getLabelNames() {
        return labelNames;
    }

    List<String> getLabelValues() {
        return labelValues;
    }

    List<String> getLabelNames(String extra) {
        ArrayList<String> names = new ArrayList<String>();
        names.addAll(labelNames);
        names.add(extra);

        return names;
    }

    List<String> getLabelValues(String extra) {
        ArrayList<String> values = new ArrayList<String>();
        values.addAll(labelValues);
        values.add(extra);
        return values;
    }

    String getName() {
        return name;
    }

    Boolean shouldReport() {
        return !doNotReport;
    }

    String getHelpMessage(Metric metric) {
        return String.format("Generated from Dropwizard metric import (metric=%s, type=%s)",
                dropwizardName, metric.getClass().getName());
    }
}
