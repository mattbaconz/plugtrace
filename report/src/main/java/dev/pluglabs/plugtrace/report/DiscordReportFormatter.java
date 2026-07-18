package dev.pluglabs.plugtrace.report;

import java.util.Map;

public final class DiscordReportFormatter {
    private static final int LIMIT = 2000;

    public String format(ReportRequest request, Map<String, Object> executive) {
        StringBuilder sb = new StringBuilder();
        sb.append("**PlugTrace** deployment #").append(request.current().localSequence())
                .append(" · health `").append(request.current().health()).append("`\n");
        sb.append("Suspect: **").append(executive.get("strongestSuspect")).append("** [`")
                .append(executive.get("band")).append("`]\n");
        sb.append("Baseline: ").append(request.baselineDescription()).append('\n');
        int changeCount = Math.min(3, request.changes().size());
        for (int i = 0; i < changeCount; i++) {
            var change = request.changes().get(i);
            sb.append("• ").append(change.type()).append(' ').append(change.componentKey()).append('\n');
        }
        sb.append("Issues: ").append(request.issues().size())
                .append(" · Full report: `plugins/PlugTrace/reports/deployment-")
                .append(request.current().localSequence()).append(".html`");
        String out = sb.toString();
        if (out.length() <= LIMIT) {
            return out;
        }
        return out.substring(0, LIMIT - 1) + "…";
    }
}
