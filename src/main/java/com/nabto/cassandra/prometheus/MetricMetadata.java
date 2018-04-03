package com.nabto.cassandra.prometheus;

import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class MetricMetadata {
    private static final Logger LOGGER = Logger.getLogger(MetricMetadata.class.getName());

    private static Pattern typeAndNamePattern = Pattern.compile("^org\\.apache\\.cassandra\\.metrics:type=([^,]+),name=([^,]+)$");
    private static Pattern typeAndScopeAndNamePattern = Pattern.compile("^org\\.apache\\.cassandra\\.metrics:type=([^,]+),scope=([^,]+),name=([^,]+)$");
    private static Pattern typeAndKeyspaceAndScopeAndNamePattern = Pattern.compile("^org\\.apache\\.cassandra\\.metrics:type=([^,]+),keyspace=([^,]+),scope=([^,]+),name=([^,]+)$");
    private static Pattern typeAndPathAndScopeAndNamePattern = Pattern.compile("^org\\.apache\\.cassandra\\.metrics:type=([^,]+),path=([^,]+),scope=([^,]+),name=([^,]+)$");

    private List<String> labelNames = new ArrayList<String>();
    private List<String> labelValues = new ArrayList<String>();
    private String name;
    private String mbean;

    private boolean doNotReport;

    // convert cassandra metric names to prometheus names and label pairs
    MetricMetadata(MetricName metricName) {
        mbean = metricName.getMBeanName();

        // Do not report aggregated/rolled-up metrics.
        if (metricName.getType().equals("keyspace")) {
            doNotReport = true;
            return;
        }

        Matcher typeAndNameMatcher = typeAndNamePattern.matcher(mbean);
        Matcher typeAndScopeAndNameMatcher = typeAndScopeAndNamePattern.matcher(mbean);
        Matcher typeAndKeyspaceAndScopeAndNameMatcher = typeAndKeyspaceAndScopeAndNamePattern.matcher(mbean);
        Matcher typeAndPathAndScopeAndNameMatcher = typeAndPathAndScopeAndNamePattern.matcher(mbean);

        if (typeAndNameMatcher.find()) {
            switch (typeAndNameMatcher.group(1)) {
                case "ClientRequestMetrics":
                    name = "cassandra_client_request_" + CamelCase.toSnakeCase(typeAndNameMatcher.group(2));
                    break;
                case "Connection":
                case "ColumnFamily":
                    // Do not report aggregated/rolled-up metrics.
                    doNotReport = true;
                    break;
                default:
                    name = "cassandra_" + CamelCase.toSnakeCase(typeAndNameMatcher.group(1)) + "_" + CamelCase.toSnakeCase(typeAndNameMatcher.group(2));
            }
        } else if (typeAndScopeAndNameMatcher.find()) {
            switch (typeAndScopeAndNameMatcher.group(1)) {
                case "Cache":
                    name = "cassandra_cache_" + CamelCase.toSnakeCase(typeAndScopeAndNameMatcher.group(3));
                    labelNames.add("cache");
                    labelValues.add(typeAndScopeAndNameMatcher.group(2));
                    break;
                case "ClientRequest":
                    name = "cassandra_client_request_" + CamelCase.toSnakeCase(typeAndScopeAndNameMatcher.group(3));
                    labelNames.add("operation");
                    labelValues.add(typeAndScopeAndNameMatcher.group(2).toLowerCase());
                    break;
                case "DroppedMessage":
                    name = "cassandra_dropped_message";
                    labelNames.add("type");
                    labelValues.add(typeAndScopeAndNameMatcher.group(2).toLowerCase());
                    break;
                case "Connection":
                    name = "cassandra_outbound_" + CamelCase.toSnakeCase(typeAndScopeAndNameMatcher.group(3));
                    labelNames.add("address");
                    labelValues.add(typeAndScopeAndNameMatcher.group(2).toLowerCase());
                    break;
                default:
                    LOGGER.warning("unhandled metric: " + mbean);
                    doNotReport = true;
            }
        } else if (typeAndKeyspaceAndScopeAndNameMatcher.find()) {
            switch (typeAndKeyspaceAndScopeAndNameMatcher.group(1)) {
                case "IndexColumnFamily":
                case "ColumnFamily":
                    name = "cassandra_" + CamelCase.toSnakeCase(typeAndKeyspaceAndScopeAndNameMatcher.group(4));
                    labelNames.add("keyspace");
                    labelValues.add(typeAndKeyspaceAndScopeAndNameMatcher.group(2));
                    labelNames.add("table");
                    labelValues.add(typeAndKeyspaceAndScopeAndNameMatcher.group(3));
                    break;
                default:
                    LOGGER.warning("unhandled metric: " + mbean);
                    doNotReport = true;
            }
        } else if (typeAndPathAndScopeAndNameMatcher.find()) {
            switch (typeAndPathAndScopeAndNameMatcher.group(1)) {
                case "ThreadPools":
                    name = "cassandra_thread_pool_" + CamelCase.toSnakeCase(typeAndPathAndScopeAndNameMatcher.group(4));
                    labelNames.add("pool_type");
                    labelValues.add(typeAndPathAndScopeAndNameMatcher.group(2));
                    labelNames.add("pool");
                    labelValues.add(typeAndPathAndScopeAndNameMatcher.group(3));
                    break;
                default:
                    LOGGER.warning("unhandled metric: " + mbean);
                    doNotReport = true;
            }
        } else {
            LOGGER.warning("unhandled metric: " + mbean);
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
        return String.format("Generated from Dropwizard metric import (mbean=%s, type=%s)",
                mbean, metric.getClass().getName());
    }
}
