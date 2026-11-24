package io.github.slavikjunior.syncclip.server.contracts.metrics

/**
 * Порт метрик (OPS-07): фичи не зависят от Prometheus/Micrometer.
 * Реализация — `MetricsRegistry` в `:server:app`; в тестах и при выключенных
 * метриках используется [NoopMetricsSink].
 */
interface MetricsSink {

    fun addCounter(name: String, delta: Long, labels: Map<String, String> = emptyMap()) {
        // no-op по умолчанию
    }

    fun increment(name: String, labels: Map<String, String> = emptyMap()) {
        addCounter(name, 1, labels)
    }

    fun observe(name: String, value: Double, labels: Map<String, String> = emptyMap()) {
        // no-op по умолчанию
    }
}

object NoopMetricsSink : MetricsSink
