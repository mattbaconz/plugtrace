package dev.pluglabs.plugtrace.report;

import dev.pluglabs.plugtrace.domain.Annotation;
import dev.pluglabs.plugtrace.domain.Change;
import dev.pluglabs.plugtrace.domain.Issue;
import dev.pluglabs.plugtrace.domain.Suspect;

import java.util.List;
import java.util.Map;

public final class HtmlReportRenderer {
    public String render(ReportRequest request, Map<String, Object> executive, RedactionService redaction) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\">");
        html.append("<title>PlugTrace Report #").append(request.current().localSequence()).append("</title>");
        html.append("<style>");
        html.append("body{font-family:Segoe UI,system-ui,sans-serif;margin:2rem;background:#f7f4ef;color:#1c1917;line-height:1.45}");
        html.append("h1,h2{font-family:Georgia,serif} .card{background:#fff;border:1px solid #e7e0d6;padding:1rem 1.25rem;margin:1rem 0}");
        html.append(".meta{color:#57534e;font-size:.95rem} .band{font-weight:700} .plus{color:#166534} .minus{color:#9f1239}");
        html.append("code{background:#efe8dc;padding:.1rem .3rem} ul{padding-left:1.2rem}");
        html.append("</style></head><body>");
        html.append("<h1>PlugTrace Report</h1>");
        html.append("<p class=\"meta\">Schema ").append(ReportService.SCHEMA_VERSION)
                .append(" · Deployment #").append(request.current().localSequence())
                .append(" · Health ").append(escape(request.current().health().name()))
                .append(" · Local only — nothing uploaded</p>");

        html.append("<div class=\"card\"><h2>Executive summary</h2>");
        html.append("<p>Strongest suspect: <span class=\"band\">").append(escape(String.valueOf(executive.get("strongestSuspect"))))
                .append("</span> [").append(escape(String.valueOf(executive.get("band")))).append("]</p><ul>");
        if (executive.get("evidence") instanceof List<?> bullets) {
            for (Object bullet : bullets) {
                html.append("<li>").append(escape(String.valueOf(bullet))).append("</li>");
            }
        }
        html.append("</ul></div>");

        html.append("<div class=\"card\"><h2>Baseline</h2><p>")
                .append(escape(request.baselineDescription())).append("</p></div>");

        html.append("<div class=\"card\"><h2>Changes</h2><ul>");
        if (request.changes().isEmpty()) {
            html.append("<li><em>No typed changes.</em></li>");
        } else {
            for (Change change : request.changes()) {
                html.append("<li><code>").append(escape(change.type().name())).append("</code> ")
                        .append(escape(change.componentKey())).append(" — ")
                        .append(escape(change.explanation())).append("</li>");
            }
        }
        html.append("</ul></div>");

        html.append("<div class=\"card\"><h2>Issues</h2><ul>");
        if (request.issues().isEmpty()) {
            html.append("<li><em>No issues.</em></li>");
        } else {
            for (Issue issue : request.issues()) {
                html.append("<li><code>").append(escape(issue.status().name())).append("</code> ")
                        .append(escape(redaction.redact(issue.normalizedMessage())))
                        .append(" (×").append(issue.occurrenceCount()).append(")</li>");
            }
        }
        html.append("</ul></div>");

        html.append("<div class=\"card\"><h2>Suspects / evidence</h2>");
        for (Suspect suspect : request.suspects()) {
            html.append("<h3>#").append(suspect.rank()).append(" ").append(escape(suspect.componentKey()))
                    .append(" <span class=\"band\">[").append(escape(suspect.band().name())).append("]</span></h3>");
            html.append("<p>").append(escape(suspect.changeSummary())).append("</p><ul>");
            for (var e : suspect.supporting()) {
                html.append("<li class=\"plus\">+ ").append(escape(e.explanation())).append("</li>");
            }
            for (var e : suspect.contradictions()) {
                html.append("<li class=\"minus\">− ").append(escape(e.explanation())).append("</li>");
            }
            html.append("</ul>");
        }
        html.append("</div>");

        html.append("<div class=\"card\"><h2>Annotations</h2><ul>");
        if (request.annotations().isEmpty()) {
            html.append("<li><em>No annotations.</em></li>");
        } else {
            for (Annotation annotation : request.annotations()) {
                html.append("<li><code>").append(escape(annotation.category())).append("</code> ")
                        .append(escape(annotation.actor())).append(": ")
                        .append(escape(redaction.redact(annotation.text()))).append("</li>");
            }
        }
        html.append("</ul></div>");

        html.append("<div class=\"card\"><h2>Privacy</h2><ul>");
        html.append("<li>Hash-only configs</li><li>Secrets redacted in samples</li><li>Nothing uploaded automatically</li>");
        html.append("</ul></div>");
        html.append("<p class=\"meta\">Unknown is a valid outcome when evidence is weak.</p>");
        html.append("</body></html>");
        return html.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
