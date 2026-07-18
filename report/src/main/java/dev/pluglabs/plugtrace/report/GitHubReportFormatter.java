package dev.pluglabs.plugtrace.report;

import dev.pluglabs.plugtrace.domain.Change;
import dev.pluglabs.plugtrace.domain.Issue;
import dev.pluglabs.plugtrace.domain.Suspect;

import java.util.Map;

public final class GitHubReportFormatter {
    public String format(ReportRequest request, Map<String, Object> executive, RedactionService redaction) {
        StringBuilder md = new StringBuilder();
        md.append("## PlugTrace report\n\n");
        md.append("- [ ] Reviewed deployment #").append(request.current().localSequence()).append('\n');
        md.append("- [ ] Checked strongest suspect\n");
        md.append("- [ ] Confirmed privacy/redaction acceptable\n\n");
        md.append("**Strongest suspect:** `").append(executive.get("strongestSuspect"))
                .append("` (`").append(executive.get("band")).append("`)\n\n");
        md.append("**Baseline:** ").append(request.baselineDescription()).append("\n\n");
        md.append("### Changes\n\n");
        if (request.changes().isEmpty()) {
            md.append("_None_\n\n");
        } else {
            for (Change change : request.changes()) {
                md.append("- `").append(change.type()).append("` ").append(change.componentKey())
                        .append(" — ").append(change.explanation()).append('\n');
            }
            md.append('\n');
        }
        md.append("### Issues\n\n");
        for (Issue issue : request.issues()) {
            md.append("<details><summary>")
                    .append(issue.status()).append(" — ")
                    .append(escapeMd(redaction.redact(issue.normalizedMessage())))
                    .append("</summary>\n\n```\n")
                    .append(escapeMd(redaction.redact(issue.sampleStack())))
                    .append("\n```\n</details>\n\n");
        }
        md.append("### Suspects\n\n");
        for (Suspect suspect : request.suspects()) {
            md.append(suspect.rank()).append(". **").append(suspect.componentKey()).append("** — ")
                    .append(suspect.band()).append('\n');
        }
        return md.toString();
    }

    private static String escapeMd(String value) {
        return value == null ? "" : value.replace("```", "'''");
    }
}
