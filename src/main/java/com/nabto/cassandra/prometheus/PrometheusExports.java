package com.nabto.cassandra.prometheus;

import io.prometheus.client.CollectorRegistry;

import org.apache.cassandra.metrics.CassandraMetricsRegistry;

import com.codahale.metrics.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Collect Dropwizard metrics from cassandra.
 */
public class PrometheusExports extends io.prometheus.client.Collector implements io.prometheus.client.Collector.Describable {
    private MetricRegistry registry;
    private static final Logger LOGGER = Logger.getLogger(PrometheusExports.class.getName());
    
    /**
     * @param registry a metric registry to export in prometheus.
     */
    public PrometheusExports(MetricRegistry registry) {
        this.registry = registry;
    }
    
    /**
     * Export counter as Prometheus <a href="https://prometheus.io/docs/concepts/metric_types/#gauge">Gauge</a>.
     */
    MetricFamilySamples fromCounter(String dropwizardName, Counter counter) {
        SampleName name = new SampleName(dropwizardName);
        MetricFamilySamples.Sample sample = new MetricFamilySamples.Sample(
            name.getName(), name.getLabelNames(), name.getLabelValues(),
            new Long(counter.getCount()).doubleValue());
        return new MetricFamilySamples(name.getName(), Type.GAUGE, getHelpMessage(dropwizardName, counter), new ArrayList<MetricFamilySamples.Sample>(Arrays.asList(sample)));
    }
    
    private static String getHelpMessage(String metricName, Metric metric){
        return String.format("Generated from Dropwizard metric import (metric=%s, type=%s)",
                             metricName, metric.getClass().getName());
    }
    
    /**
     * Export gauge as a prometheus gauge.
     */
    MetricFamilySamples fromGauge(String dropwizardName, Gauge gauge) {
        SampleName name = new SampleName(dropwizardName);
        Object obj = gauge.getValue();
        double value;
        if (obj instanceof Number) {
            value = ((Number) obj).doubleValue();
        } else if (obj instanceof Boolean) {
            value = ((Boolean) obj) ? 1 : 0;
        } else {
            LOGGER.log(Level.FINE, String.format("Invalid type for Gauge %s: %s", name,
                                                 obj.getClass().getName()));
            return new MetricFamilySamples(name.getName(), Type.GAUGE, getHelpMessage(dropwizardName, gauge), new ArrayList<MetricFamilySamples.Sample>());
        }
        MetricFamilySamples.Sample sample = new MetricFamilySamples.Sample(
            name.getName(), name.getLabelNames(), name.getLabelValues(), value);
        return new MetricFamilySamples(name.getName(), Type.GAUGE, getHelpMessage(dropwizardName, gauge), new ArrayList<MetricFamilySamples.Sample>(Arrays.asList(sample)));
    }
    
    /**
     * Export a histogram snapshot as a prometheus SUMMARY.
     *
     * @param dropwizardName metric name.
     * @param snapshot the histogram snapshot.
     * @param count the total sample count for this snapshot.
     * @param factor a factor to apply to histogram values.
     *
     */
    MetricFamilySamples fromSnapshotAndCount(String dropwizardName, Snapshot snapshot, long count, double factor, String helpMessage) {
        SampleName name = new SampleName(dropwizardName);
        List<MetricFamilySamples.Sample> samples = Arrays.asList(
            new MetricFamilySamples.Sample(name.getName(), name.getLabelNames("quantile"), name.getLabelValues("0.5"), snapshot.getMedian() * factor),
            new MetricFamilySamples.Sample(name.getName(), name.getLabelNames("quantile"), name.getLabelValues("0.75"), snapshot.get75thPercentile() * factor),
            new MetricFamilySamples.Sample(name.getName(), name.getLabelNames("quantile"), name.getLabelValues("0.95"), snapshot.get95thPercentile() * factor),
            new MetricFamilySamples.Sample(name.getName(), name.getLabelNames("quantile"), name.getLabelValues("0.98"), snapshot.get98thPercentile() * factor),
            new MetricFamilySamples.Sample(name.getName(), name.getLabelNames("quantile"), name.getLabelValues("0.99"), snapshot.get99thPercentile() * factor),
            new MetricFamilySamples.Sample(name.getName(), name.getLabelNames("quantile"), name.getLabelValues("0.999"), snapshot.get999thPercentile() * factor),
            new MetricFamilySamples.Sample(name.getName() + "_count", name.getLabelNames(), name.getLabelValues(), count)
            );
        return new MetricFamilySamples(name.getName(), Type.SUMMARY, helpMessage, new ArrayList<MetricFamilySamples.Sample>(samples));
    }

    /**
     * Convert histogram snapshot.
     */
    MetricFamilySamples fromHistogram(String dropwizardName, Histogram histogram) {
        return fromSnapshotAndCount(dropwizardName, histogram.getSnapshot(), histogram.getCount(), 1.0,
                                    getHelpMessage(dropwizardName, histogram));
    }

    /**
     * Export Dropwizard Timer as a histogram. Use TIME_UNIT as time unit.
     */
    MetricFamilySamples fromTimer(String dropwizardName, Timer timer) {
        return fromSnapshotAndCount(dropwizardName, timer.getSnapshot(), timer.getCount(),
                                    1.0D / TimeUnit.SECONDS.toNanos(1L), getHelpMessage(dropwizardName, timer));
    }

    /**
     * Export a Meter as as prometheus COUNTER.
     */
    MetricFamilySamples fromMeter(String dropwizardName, Meter meter) {
        SampleName name = new SampleName(dropwizardName);
        MetricFamilySamples.Sample sample = new MetricFamilySamples.Sample(
            name.getName() + "_total", name.getLabelNames(), name.getLabelValues(), meter.getCount());

        return new MetricFamilySamples(name.getName() + "_total", Type.COUNTER, getHelpMessage(dropwizardName, meter),
                                       new ArrayList<MetricFamilySamples.Sample>(Arrays.asList(sample)));
    }
   

    @Override
    public List<MetricFamilySamples> collect() {

        SamplesMap mfSamples = new SamplesMap();

        for (SortedMap.Entry<String, Gauge> entry : registry.getGauges().entrySet()) {
            mfSamples.addSamples(fromGauge(entry.getKey(), entry.getValue()));
        }
        for (SortedMap.Entry<String, Counter> entry : registry.getCounters().entrySet()) {
            mfSamples.addSamples(fromCounter(entry.getKey(), entry.getValue()));
        }
        for (SortedMap.Entry<String, Histogram> entry : registry.getHistograms().entrySet()) {
            mfSamples.addSamples(fromHistogram(entry.getKey(), entry.getValue()));
        }
        for (SortedMap.Entry<String, Timer> entry : registry.getTimers().entrySet()) {
            mfSamples.addSamples(fromTimer(entry.getKey(), entry.getValue()));
        }
        for (SortedMap.Entry<String, Meter> entry : registry.getMeters().entrySet()) {
            mfSamples.addSamples(fromMeter(entry.getKey(), entry.getValue()));
        }
        return mfSamples.getSamples();
    }

    @Override
    public List<MetricFamilySamples> describe() {
        return new ArrayList<MetricFamilySamples>();
    }

    // helper to remove overlapping metricsfamilies
    private class SamplesMap {
        HashMap<String, MetricFamilySamples> families;

        public SamplesMap() {
            families = new HashMap<String, MetricFamilySamples>();
        }
        
        public void addSamples(MetricFamilySamples samples) {
            MetricFamilySamples family = families.get(samples.name);
            if (family != null) {
                family.samples.addAll(samples.samples);
            } else {
                families.put(samples.name, samples);
            }
        }
        
        public List<MetricFamilySamples> getSamples() {
            List<MetricFamilySamples> list = new ArrayList<>();
            list.addAll(families.values());
            return list;
        }
    };

    // convert cassandra metric names to prometheus names and label pairs
    private class SampleName {
        
        private List<String> labelNames = new ArrayList<String>();
        private List<String> labelValues = new ArrayList<String>();
        private String metricName;
        
        public SampleName(String name) {
            name = name.toLowerCase();
            // all metrics starts with org.apache.cassandra.metrics
            // replace org.apache.cassandra.metrics -> cassandra
            // org.apache.cassandra.metrics.Table.BloomFilterDiskSpaceUsed.system_schema.triggers -> cassandra_table_bloomfilterdiskspaceused{keyspace=system_schema, table=triggers}

            // org.apache.cassandra.metrics.Table.CasCommitLatency.system.peers
            Pattern tables = Pattern.compile("org\\.apache\\.cassandra\\.metrics\\.table\\.([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_-]+)");

            // org.apache.cassandra.metrics.Cache.Entries.ChunkCache
            // org.apache.cassandra.metrics cache metrictype cachename 
            Pattern caches = Pattern.compile("org\\.apache\\.cassandra\\.metrics\\.cache\\.([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_-]+)");

            // org_apache_cassandra_metrics_keyspace_MemtableOnHeapDataSize_system_distributed 0.0
            // org.apache.cassandra.metrics.keyspace.WriteLatency.system
            Pattern keyspaces = Pattern.compile("org\\.apache\\.cassandra\\.metrics\\.keyspace\\.([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_-]+)");

            // rest
            Pattern p = Pattern.compile("org\\.apache\\.cassandra\\.metrics\\.(.*)");
            Matcher m = p.matcher(name);

            
            Matcher tableMatcher = tables.matcher(name);
            Matcher cacheMatcher = caches.matcher(name);
            Matcher keyspaceMatcher = keyspaces.matcher(name);
            
            if (tableMatcher.find()) {
                metricName = "cassandra_table_" + tableMatcher.group(1);
                labelNames.add("keyspace");
                labelValues.add(tableMatcher.group(2));
                labelNames.add("table");
                labelValues.add(tableMatcher.group(3));
            } else if (cacheMatcher.find()) {
                metricName = "cassandra_cache_" + cacheMatcher.group(1);
                labelNames.add("cache");
                labelValues.add(cacheMatcher.group(2));
            } else if (keyspaceMatcher.find()) {
                metricName = "cassandra_keyspace_" + keyspaceMatcher.group(1);
                labelNames.add("keyspace");
                labelValues.add(keyspaceMatcher.group(2));
            } else  if (m.find()) {
                metricName = "cassandra_" + m.group(1);
                metricName = metricName.replaceAll("[^a-zA-Z0-9:_]", "_");
            } else {
                metricName = name.replaceAll("[^a-zA-Z0-9:_]", "_");
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
            return metricName;
        }
    }
}
