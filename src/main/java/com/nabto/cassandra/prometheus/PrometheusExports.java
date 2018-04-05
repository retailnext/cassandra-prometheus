package com.nabto.cassandra.prometheus;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    MetricFamilySamples fromCounter(MetricMetadata metadata, Counter counter) {
        MetricFamilySamples.Sample sample = new MetricFamilySamples.Sample(
                metadata.getName(), metadata.getLabelNames(), metadata.getLabelValues(),
                new Long(counter.getCount()).doubleValue());
        return new MetricFamilySamples(metadata.getName(), Type.GAUGE, metadata.getHelpMessage(counter), new ArrayList<MetricFamilySamples.Sample>(Arrays.asList(sample)));
    }

    /**
     * Export gauge as a prometheus gauge.
     */
    MetricFamilySamples fromGauge(MetricMetadata metadata, Gauge gauge) {
        Object obj = gauge.getValue();
        double value;
        if (obj instanceof Number) {
            value = ((Number) obj).doubleValue();
        } else if (obj instanceof Boolean) {
            value = ((Boolean) obj) ? 1 : 0;
        } else {
            LOGGER.log(Level.FINE, String.format("Invalid type for Gauge %s: %s", metadata.getName(),
                    obj.getClass().getName()));
            return new MetricFamilySamples(metadata.getName(), Type.GAUGE, metadata.getHelpMessage(gauge), new ArrayList<MetricFamilySamples.Sample>());
        }
        MetricFamilySamples.Sample sample = new MetricFamilySamples.Sample(
                metadata.getName(), metadata.getLabelNames(), metadata.getLabelValues(), value);
        return new MetricFamilySamples(metadata.getName(), Type.GAUGE, metadata.getHelpMessage(gauge), new ArrayList<MetricFamilySamples.Sample>(Arrays.asList(sample)));
    }

    /**
     * Export a histogram snapshot as a prometheus SUMMARY.
     *
     * @param metadata metric metadata.
     * @param snapshot the histogram snapshot.
     * @param count    the total sample count for this snapshot.
     * @param factor   a factor to apply to histogram values.
     */
    MetricFamilySamples fromSnapshotAndCount(MetricMetadata metadata, Snapshot snapshot, long count, double factor, String helpMessage) {
        List<MetricFamilySamples.Sample> samples = Arrays.asList(
                new MetricFamilySamples.Sample(metadata.getName(), metadata.getLabelNames("quantile"), metadata.getLabelValues("0.5"), snapshot.getMedian() * factor),
                new MetricFamilySamples.Sample(metadata.getName(), metadata.getLabelNames("quantile"), metadata.getLabelValues("0.75"), snapshot.get75thPercentile() * factor),
                new MetricFamilySamples.Sample(metadata.getName(), metadata.getLabelNames("quantile"), metadata.getLabelValues("0.95"), snapshot.get95thPercentile() * factor),
                new MetricFamilySamples.Sample(metadata.getName(), metadata.getLabelNames("quantile"), metadata.getLabelValues("0.98"), snapshot.get98thPercentile() * factor),
                new MetricFamilySamples.Sample(metadata.getName(), metadata.getLabelNames("quantile"), metadata.getLabelValues("0.99"), snapshot.get99thPercentile() * factor),
                new MetricFamilySamples.Sample(metadata.getName(), metadata.getLabelNames("quantile"), metadata.getLabelValues("0.999"), snapshot.get999thPercentile() * factor),
                new MetricFamilySamples.Sample(metadata.getName() + "_count", metadata.getLabelNames(), metadata.getLabelValues(), count)
        );
        return new MetricFamilySamples(metadata.getName(), Type.SUMMARY, helpMessage, new ArrayList<MetricFamilySamples.Sample>(samples));
    }

    /**
     * Convert histogram snapshot.
     */
    MetricFamilySamples fromHistogram(MetricMetadata metadata, Histogram histogram) {
        return fromSnapshotAndCount(metadata, histogram.getSnapshot(), histogram.getCount(), 1.0,
                metadata.getHelpMessage(histogram));
    }

    /**
     * Export Dropwizard Timer as a histogram. Use TIME_UNIT as time unit.
     */
    MetricFamilySamples fromTimer(MetricMetadata metadata, Timer timer) {
        return fromSnapshotAndCount(metadata, timer.getSnapshot(), timer.getCount(),
                1.0D / TimeUnit.SECONDS.toNanos(1L), metadata.getHelpMessage(timer));
    }

    /**
     * Export a Meter as as prometheus COUNTER.
     */
    MetricFamilySamples fromMeter(MetricMetadata metadata, Meter meter) {
        MetricFamilySamples.Sample sample = new MetricFamilySamples.Sample(
                metadata.getName() + "_total", metadata.getLabelNames(), metadata.getLabelValues(), meter.getCount());

        return new MetricFamilySamples(metadata.getName() + "_total", Type.COUNTER, metadata.getHelpMessage(meter),
                new ArrayList<MetricFamilySamples.Sample>(Arrays.asList(sample)));
    }


    @Override
    public List<MetricFamilySamples> collect() {

        SamplesMap mfSamples = new SamplesMap();

        for (SortedMap.Entry<String, Gauge> entry : registry.getGauges().entrySet()) {
            MetricMetadata metadata = new MetricMetadata(entry.getKey());
            if (metadata.shouldReport()) {
                mfSamples.addSamples(fromGauge(metadata, entry.getValue()));
            }
        }
        for (SortedMap.Entry<String, Counter> entry : registry.getCounters().entrySet()) {
            MetricMetadata metadata = new MetricMetadata(entry.getKey());
            if (metadata.shouldReport()) {
                mfSamples.addSamples(fromCounter(metadata, entry.getValue()));
            }
        }
        for (SortedMap.Entry<String, Histogram> entry : registry.getHistograms().entrySet()) {
            MetricMetadata metadata = new MetricMetadata(entry.getKey());
            if (metadata.shouldReport()) {
                mfSamples.addSamples(fromHistogram(metadata, entry.getValue()));
            }
        }
        for (SortedMap.Entry<String, Timer> entry : registry.getTimers().entrySet()) {
            MetricMetadata metadata = new MetricMetadata(entry.getKey());
            if (metadata.shouldReport()) {
                mfSamples.addSamples(fromTimer(metadata, entry.getValue()));
            }
        }
        for (SortedMap.Entry<String, Meter> entry : registry.getMeters().entrySet()) {
            MetricMetadata metadata = new MetricMetadata(entry.getKey());
            if (metadata.shouldReport()) {
                mfSamples.addSamples(fromMeter(metadata, entry.getValue()));
            }
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
    }
}
