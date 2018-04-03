package com.nabto.cassandra.prometheus;

import com.yammer.metrics.core.Counter;
import com.yammer.metrics.core.Gauge;
import com.yammer.metrics.core.Histogram;
import com.yammer.metrics.core.Metered;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricProcessor;
import com.yammer.metrics.core.MetricsRegistry;
import com.yammer.metrics.core.Timer;
import com.yammer.metrics.stats.Snapshot;

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
    private MetricsRegistry registry;
    private static final Logger LOGGER = Logger.getLogger(PrometheusExports.class.getName());

    /**
     * @param registry a metric registry to export in prometheus.
     */
    public PrometheusExports(MetricsRegistry registry) {
        this.registry = registry;
    }

    /**
     * Export counter as Prometheus <a href="https://prometheus.io/docs/concepts/metric_types/#gauge">Gauge</a>.
     */
    MetricFamilySamples fromCounter(MetricMetadata metadata, Counter counter) {
        MetricFamilySamples.Sample sample = new MetricFamilySamples.Sample(
                metadata.getName(), metadata.getLabelNames(), metadata.getLabelValues(),
                new Long(counter.count()).doubleValue());
        return new MetricFamilySamples(metadata.getName(), Type.GAUGE, metadata.getHelpMessage(counter), new ArrayList<MetricFamilySamples.Sample>(Arrays.asList(sample)));
    }


    /**
     * Export gauge as a prometheus gauge.
     */
    MetricFamilySamples fromGauge(MetricMetadata metadata, Gauge gauge) {
        Object obj = gauge.value();
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
        return fromSnapshotAndCount(metadata, histogram.getSnapshot(), histogram.count(), 1.0,
                metadata.getHelpMessage(histogram));
    }

    /**
     * Export Dropwizard Timer as a histogram. Use TIME_UNIT as time unit.
     */
    MetricFamilySamples fromTimer(MetricMetadata metadata, Timer timer) {
        return fromSnapshotAndCount(metadata, timer.getSnapshot(), timer.count(),
                1.0D / TimeUnit.SECONDS.toNanos(1L), metadata.getHelpMessage(timer));
    }

    /**
     * Export a Meter as as prometheus COUNTER.
     */
    MetricFamilySamples fromMeter(MetricMetadata metadata, Metered meter) {
        MetricFamilySamples.Sample sample = new MetricFamilySamples.Sample(
                metadata.getName() + "_total", metadata.getLabelNames(), metadata.getLabelValues(), meter.count());

        return new MetricFamilySamples(metadata.getName() + "_total", Type.COUNTER, metadata.getHelpMessage(meter),
                new ArrayList<MetricFamilySamples.Sample>(Arrays.asList(sample)));
    }

    @Override
    public List<MetricFamilySamples> collect() {

        PrometheusMetricProcessor processor = new PrometheusMetricProcessor();
        SamplesMap samplesMap = new SamplesMap();

        for (SortedMap.Entry<MetricName, Metric> entry : registry.allMetrics().entrySet()) {
            try {
                entry.getValue().processWith(processor, entry.getKey(), samplesMap);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return samplesMap.getSamples();
    }

    private class PrometheusMetricProcessor implements MetricProcessor<SamplesMap> {
        @Override
        public void processMeter(MetricName name, Metered meter, SamplesMap context) {
            MetricMetadata metadata = new MetricMetadata(name);
            if (metadata.shouldReport()) {
                context.addSamples(fromMeter(metadata, meter));
            }
        }

        @Override
        public void processCounter(MetricName name, Counter counter, SamplesMap context) {
            MetricMetadata metadata = new MetricMetadata(name);
            if (metadata.shouldReport()) {
                context.addSamples(fromCounter(metadata, counter));
            }
        }

        @Override
        public void processHistogram(MetricName name, Histogram histogram, SamplesMap context) {
            MetricMetadata metadata = new MetricMetadata(name);
            if (metadata.shouldReport()) {
                context.addSamples(fromHistogram(metadata, histogram));
            }
        }

        @Override
        public void processTimer(MetricName name, Timer timer, SamplesMap context) {
            MetricMetadata metadata = new MetricMetadata(name);
            if (metadata.shouldReport()) {
                context.addSamples(fromTimer(metadata, timer));
            }
        }

        @Override
        public void processGauge(MetricName name, Gauge<?> gauge, SamplesMap context) {
            MetricMetadata metadata = new MetricMetadata(name);
            if (metadata.shouldReport()) {
                context.addSamples(fromGauge(metadata, gauge));
            }
        }
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
