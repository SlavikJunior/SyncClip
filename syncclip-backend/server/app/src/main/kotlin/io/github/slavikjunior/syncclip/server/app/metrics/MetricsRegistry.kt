package io.github.slavikjunior.syncclip.server.app.metrics

import io.github.slavikjunior.syncclip.server.contracts.metrics.MetricsSink
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.DoubleAdder
import java.util.concurrent.atomic.LongAdder

/**
 * Минимальный Prometheus-совместимый реестр без внешних зависимостей (OPS-07):
 * counters и summaries. Gauges (WS/queue) добавляются при рендеринге из
 * живых источников, чтобы не держать фон (см. `metricsRoutes`).
 */
class MetricsRegistry : MetricsSink {

    private data class Key(val name: String, val labels: Map<String, String>)

    private data class Summary(val count: LongAdder = LongAdder(), val sum: DoubleAdder = DoubleAdder())

    private val counters = ConcurrentHashMap<Key, LongAdder>()
    private val summaries = ConcurrentHashMap<Key, Summary>()

    override fun addCounter(name: String, delta: Long, labels: Map<String, String>) {
        if (delta == 0L) return
        counters.computeIfAbsent(Key(name, labels.toSortedMap())) { LongAdder() }.add(delta)
    }

    override fun observe(name: String, value: Double, labels: Map<String, String>) {
        val summary = summaries.computeIfAbsent(Key(name, labels.toSortedMap())) { Summary() }
        summary.count.increment()
        summary.sum.add(value)
    }

    fun render(): String = buildString {
        counters.entries
            .groupBy({ it.key.name }, { it.key to it.value.sum() })
            .toSortedMap()
            .forEach { (name, samples) ->
                appendLine("# TYPE $name counter")
                samples.forEach { (key, value) -> appendLine(sample(name, key.labels, value.toString())) }
            }
        summaries.entries
            .groupBy({ it.key.name }, { it.key to it.value })
            .toSortedMap()
            .forEach { (name, samples) ->
                appendLine("# TYPE $name summary")
                samples.forEach { (key, summary) ->
                    appendLine(sample("${name}_count", key.labels, summary.count.sum().toString()))
                    appendLine(sample("${name}_sum", key.labels, summary.sum.sum().toString()))
                }
            }
    }

    private fun sample(name: String, labels: Map<String, String>, value: String): String =
        if (labels.isEmpty()) {
            "$name $value"
        } else {
            val rendered = labels.entries.joinToString(",") { (key, labelValue) ->
                "$key=\"${escape(labelValue)}\""
            }
            "$name{$rendered} $value"
        }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
}
