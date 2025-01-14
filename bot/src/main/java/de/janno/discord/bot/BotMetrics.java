package de.janno.discord.bot;

import com.google.common.base.Strings;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.*;
import io.micrometer.core.instrument.binder.logging.LogbackMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.util.Headers;
import lombok.NonNull;

import java.time.Duration;

import static io.micrometer.core.instrument.Metrics.globalRegistry;

public class BotMetrics {

    public final static String METRIC_PREFIX = "dice.";
    public final static String METRIC_BUTTON_PREFIX = "buttonEvent";
    public final static String METRIC_DATABASE_PREFIX = "database";
    public final static String METRIC_LEGACY_BUTTON_PREFIX = "legacyButtonEvent";
    public final static String METRIC_SLASH_PREFIX = "slashEvent";
    public final static String METRIC_SLASH_HELP_PREFIX = "slashHelpEvent";
    public final static String CONFIG_TAG = "config";
    public final static String COMMAND_TAG = "command";
    public final static String ACTION_TAG = "action";

    public static void init(String publishMetricsToUrl) {
        if (!Strings.isNullOrEmpty(publishMetricsToUrl)) {
            PrometheusMeterRegistry prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
            Metrics.addRegistry(prometheusRegistry);
            new UptimeMetrics().bindTo(globalRegistry);
            PathHandler handler = Handlers.path().addExactPath("prometheus", exchange ->
            {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                exchange.getResponseSender().send(prometheusRegistry.scrape());
            });
            Undertow server = Undertow.builder()
                    .addHttpListener(8080, publishMetricsToUrl)
                    .setHandler(handler).build();
            server.start();

            prometheusRegistry.config().commonTags("application", "DiscordDiceBot");
            new JvmMemoryMetrics().bindTo(globalRegistry);
            new JvmGcMetrics().bindTo(globalRegistry);
            new ProcessorMetrics().bindTo(globalRegistry);
            new JvmThreadMetrics().bindTo(globalRegistry);
            new LogbackMetrics().bindTo(globalRegistry);
            new ClassLoaderMetrics().bindTo(globalRegistry);
            new JvmHeapPressureMetrics().bindTo(globalRegistry);
            new JvmInfoMetrics().bindTo(globalRegistry);
        }
    }


    public static void incrementButtonMetricCounter(@NonNull String commandName, @NonNull String configString) {
        globalRegistry.counter(METRIC_PREFIX + METRIC_BUTTON_PREFIX, Tags.of(COMMAND_TAG, commandName, CONFIG_TAG, configString)).increment();
    }

    public static void incrementLegacyButtonMetricCounter(@NonNull String commandName) {
        globalRegistry.counter(METRIC_PREFIX + METRIC_LEGACY_BUTTON_PREFIX, Tags.of(COMMAND_TAG, commandName)).increment();
    }

    public static void incrementSlashStartMetricCounter(@NonNull String commandName, @NonNull String configString) {
        globalRegistry.counter(METRIC_PREFIX + METRIC_SLASH_PREFIX, Tags.of(COMMAND_TAG, commandName, CONFIG_TAG, configString)).increment();
    }

    public static void incrementSlashHelpMetricCounter(@NonNull String commandName) {
        globalRegistry.counter(METRIC_PREFIX + METRIC_SLASH_HELP_PREFIX, Tags.of(COMMAND_TAG, commandName)).increment();
    }

    public static void databaseTimer(@NonNull String action, @NonNull Duration duration) {
        Timer.builder(METRIC_PREFIX + METRIC_DATABASE_PREFIX)
                .tags(Tags.of(ACTION_TAG, action))
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram(true)
                .register(globalRegistry)
                .record(duration);
    }

}
