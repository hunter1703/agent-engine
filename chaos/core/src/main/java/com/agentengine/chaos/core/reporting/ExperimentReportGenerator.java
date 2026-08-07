package com.agentengine.chaos.core.reporting;

import com.agentengine.chaos.api.CriterionFailure;
import com.agentengine.chaos.api.ExperimentResult;
import com.agentengine.chaos.api.FaultEvent;
import com.agentengine.chaos.api.ReportFormat;
import com.agentengine.chaos.api.SteadyStateMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class ExperimentReportGenerator {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .registerModule(new Jdk8Module())
            .enable(SerializationFeature.INDENT_OUTPUT);

    public String generate(final ExperimentResult result, final ReportFormat format) {
        return switch (format) {
            case JSON -> toJson(result);
            case MARKDOWN -> toMarkdown(result);
            case HTML -> toHtml(result);
            case UNKNOWN -> throw new IllegalArgumentException("Unsupported report format: " + format);
        };
    }

    public String toJson(final ExperimentResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize experiment report to JSON", ex);
        }
    }

    public String toMarkdown(final ExperimentResult result) {
        final StringBuilder md = new StringBuilder();
        md.append("# Chaos Experiment Report: ")
                .append(result.experimentName())
                .append('\n')
                .append('\n');
        md.append("- **ID**: ").append(result.experimentId()).append('\n');
        md.append("- **Status**: ").append(result.status()).append('\n');
        md.append("- **Start**: ").append(result.startTime()).append('\n');
        result.endTime().ifPresent(end -> md.append("- **End**: ").append(end).append('\n'));
        result.recoveryTime()
                .ifPresent(duration ->
                        md.append("- **Recovery time**: ").append(duration).append('\n'));
        result.abortReason()
                .ifPresent(reason ->
                        md.append("- **Abort reason**: ").append(reason).append('\n'));
        md.append('\n');

        md.append("## Steady State\n\n");
        md.append("| Metric | Baseline | Post-Recovery |\n");
        md.append("|---|---|---|\n");
        if (result.baseline().isPresent() && result.postRecovery().isPresent()) {
            appendMetricsRows(md, result.baseline().get(), result.postRecovery().get());
        } else {
            md.append("| (baseline or post-recovery snapshot not collected) | | |\n");
        }
        md.append('\n');

        result.evaluation().ifPresent(evaluation -> {
            md.append("## Success Criteria\n\n");
            if (evaluation.failures().isEmpty()) {
                md.append("All criteria passed.\n\n");
            } else {
                md.append("| Criterion | Threshold | Actual | Description |\n");
                md.append("|---|---|---|---|\n");
                for (final CriterionFailure failure : evaluation.failures()) {
                    md.append("| ")
                            .append(failure.type())
                            .append(" | ")
                            .append(failure.threshold())
                            .append(" | ")
                            .append(failure.actual())
                            .append(" | ")
                            .append(failure.description())
                            .append(" |\n");
                }
                md.append('\n');
            }
        });

        md.append("## Fault Events\n\n");
        for (final FaultEvent event : result.faultEvents()) {
            md.append("- `")
                    .append(event.faultId())
                    .append("` ")
                    .append(event.faultType())
                    .append(" — ")
                    .append(event.outcome())
                    .append(" at ")
                    .append(event.startTime())
                    .append('\n');
        }
        return md.toString();
    }

    public String toHtml(final ExperimentResult result) {
        final StringBuilder html = new StringBuilder();
        html.append("<html><head><title>Chaos Experiment Report: ")
                .append(escapeHtml(result.experimentName()))
                .append("</title></head><body>");
        html.append("<h1>").append(escapeHtml(result.experimentName())).append("</h1>");
        html.append("<p><b>ID:</b> ").append(escapeHtml(result.experimentId())).append("</p>");
        html.append("<p><b>Status:</b> ").append(result.status()).append("</p>");
        html.append("<p><b>Start:</b> ").append(result.startTime()).append("</p>");
        result.recoveryTime()
                .ifPresent(duration -> html.append("<p><b>Recovery time:</b> ")
                        .append(duration)
                        .append("</p>"));

        html.append("<h2>Steady State</h2><table border=\"1\"><tr><th>Metric</th><th>Baseline</th>"
                + "<th>Post-Recovery</th></tr>");
        if (result.baseline().isPresent() && result.postRecovery().isPresent()) {
            appendMetricsRowsHtml(
                    html, result.baseline().get(), result.postRecovery().get());
        }
        html.append("</table>");

        result.evaluation().ifPresent(evaluation -> {
            html.append("<h2>Success Criteria</h2>");
            if (evaluation.failures().isEmpty()) {
                html.append("<p>All criteria passed.</p>");
            } else {
                html.append("<table border=\"1\"><tr><th>Criterion</th><th>Threshold</th><th>Actual</th>"
                        + "<th>Description</th></tr>");
                for (final CriterionFailure failure : evaluation.failures()) {
                    html.append("<tr><td>")
                            .append(failure.type())
                            .append("</td><td>")
                            .append(failure.threshold())
                            .append("</td><td>")
                            .append(failure.actual())
                            .append("</td><td>")
                            .append(escapeHtml(failure.description()))
                            .append("</td></tr>");
                }
                html.append("</table>");
            }
        });

        html.append("<h2>Fault Events</h2><ul>");
        for (final FaultEvent event : result.faultEvents()) {
            html.append("<li>")
                    .append(escapeHtml(event.faultId()))
                    .append(' ')
                    .append(event.faultType())
                    .append(" &mdash; ")
                    .append(event.outcome())
                    .append("</li>");
        }
        html.append("</ul></body></html>");
        return html.toString();
    }

    private static void appendMetricsRows(
            final StringBuilder md, final SteadyStateMetrics baseline, final SteadyStateMetrics postRecovery) {
        md.append("| Success rate | ")
                .append(baseline.successRate())
                .append(" | ")
                .append(postRecovery.successRate())
                .append(" |\n");
        md.append("| p99 latency | ")
                .append(baseline.p99Latency())
                .append(" | ")
                .append(postRecovery.p99Latency())
                .append(" |\n");
        md.append("| Error rate | ")
                .append(baseline.errorRate())
                .append(" | ")
                .append(postRecovery.errorRate())
                .append(" |\n");
    }

    private static void appendMetricsRowsHtml(
            final StringBuilder html, final SteadyStateMetrics baseline, final SteadyStateMetrics postRecovery) {
        html.append("<tr><td>Success rate</td><td>")
                .append(baseline.successRate())
                .append("</td><td>")
                .append(postRecovery.successRate())
                .append("</td></tr>");
        html.append("<tr><td>p99 latency</td><td>")
                .append(baseline.p99Latency())
                .append("</td><td>")
                .append(postRecovery.p99Latency())
                .append("</td></tr>");
        html.append("<tr><td>Error rate</td><td>")
                .append(baseline.errorRate())
                .append("</td><td>")
                .append(postRecovery.errorRate())
                .append("</td></tr>");
    }

    private static String escapeHtml(final String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
