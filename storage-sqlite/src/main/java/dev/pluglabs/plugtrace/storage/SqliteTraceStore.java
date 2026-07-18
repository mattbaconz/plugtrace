package dev.pluglabs.plugtrace.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.pluglabs.plugtrace.domain.Annotation;
import dev.pluglabs.plugtrace.domain.ComponentIdentity;
import dev.pluglabs.plugtrace.domain.ComponentSnapshot;
import dev.pluglabs.plugtrace.domain.ComponentType;
import dev.pluglabs.plugtrace.domain.ConfigSnapshot;
import dev.pluglabs.plugtrace.domain.Deployment;
import dev.pluglabs.plugtrace.domain.DeploymentHealth;
import dev.pluglabs.plugtrace.domain.DeploymentLifecycle;
import dev.pluglabs.plugtrace.domain.Issue;
import dev.pluglabs.plugtrace.domain.IssueStatus;
import dev.pluglabs.plugtrace.domain.RegressionClass;
import dev.pluglabs.plugtrace.domain.ServerNode;
import dev.pluglabs.plugtrace.domain.TraceStore;
import dev.pluglabs.plugtrace.domain.Checkpoint;
import dev.pluglabs.plugtrace.domain.CheckCriticality;
import dev.pluglabs.plugtrace.domain.CheckResult;
import dev.pluglabs.plugtrace.domain.CheckStatus;
import dev.pluglabs.plugtrace.domain.DeploymentVerification;
import dev.pluglabs.plugtrace.domain.Incident;
import dev.pluglabs.plugtrace.domain.IncidentStatus;
import dev.pluglabs.plugtrace.domain.ExpectedState;
import dev.pluglabs.plugtrace.domain.RecoveryOutcome;
import dev.pluglabs.plugtrace.domain.RecoveryVerification;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class SqliteTraceStore implements TraceStore {
    private final Connection connection;
    private final ObjectMapper mapper;
    private final Path databaseFile;

    public SqliteTraceStore(Path databaseFile) {
        try {
            Class.forName("org.sqlite.JDBC");
            this.databaseFile = databaseFile;
            if (Files.exists(databaseFile)) {
                Path backup = databaseFile.resolveSibling(databaseFile.getFileName() + ".bak");
                Files.copy(databaseFile, backup, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Path parent = databaseFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
            }
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile.toAbsolutePath());
            this.mapper = new ObjectMapper().registerModule(new JavaTimeModule());
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA journal_mode=WAL");
                statement.execute("PRAGMA foreign_keys=ON");
            }
            migrate();
            String integrity = integrityCheck();
            if (!"ok".equalsIgnoreCase(integrity)) {
                throw new IllegalStateException("SQLite integrity_check failed: " + integrity);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to open PlugTrace SQLite store", e);
        }
    }

    private void migrate() throws SQLException {
        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS server_nodes (
                      id TEXT PRIMARY KEY,
                      name TEXT NOT NULL,
                      platform_family TEXT NOT NULL,
                      environment TEXT NOT NULL,
                      created_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS deployments (
                      id TEXT PRIMARY KEY,
                      local_sequence INTEGER NOT NULL,
                      node_id TEXT NOT NULL,
                      parent_id TEXT,
                      state_fingerprint TEXT NOT NULL,
                      started_at TEXT NOT NULL,
                      ended_at TEXT,
                      startup_ready_at TEXT,
                      startup_ready_ms INTEGER NOT NULL DEFAULT -1,
                      crash_reports_json TEXT NOT NULL DEFAULT '[]',
                      lifecycle TEXT NOT NULL,
                      health TEXT NOT NULL,
                      health_reasons_json TEXT NOT NULL,
                      complete INTEGER NOT NULL,
                      tags_json TEXT NOT NULL,
                      server_implementation TEXT NOT NULL,
                      minecraft_version TEXT NOT NULL,
                      java_version TEXT NOT NULL,
                      java_vendor TEXT NOT NULL,
                      components_json TEXT NOT NULL,
                      configs_json TEXT NOT NULL,
                      schema_version INTEGER NOT NULL,
                      UNIQUE(node_id, local_sequence)
                    )
                    """);
            ensureColumn(statement, "deployments", "startup_ready_at", "TEXT");
            ensureColumn(statement, "deployments", "startup_ready_ms", "INTEGER NOT NULL DEFAULT -1");
            ensureColumn(statement, "deployments", "crash_reports_json", "TEXT NOT NULL DEFAULT '[]'");
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS issues (
                      id TEXT NOT NULL,
                      deployment_id TEXT NOT NULL,
                      fingerprint TEXT NOT NULL,
                      normalized_type TEXT NOT NULL,
                      normalized_message TEXT NOT NULL,
                      ownership_json TEXT NOT NULL,
                      first_seen_at TEXT NOT NULL,
                      last_seen_at TEXT NOT NULL,
                      status TEXT NOT NULL,
                      severity TEXT NOT NULL,
                      occurrence_count INTEGER NOT NULL,
                      sample_stack TEXT,
                      regression_class TEXT NOT NULL,
                      PRIMARY KEY (deployment_id, fingerprint)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS annotations (
                      id TEXT PRIMARY KEY,
                      deployment_id TEXT NOT NULL,
                      created_at TEXT NOT NULL,
                      actor TEXT NOT NULL,
                      category TEXT NOT NULL,
                      text TEXT NOT NULL,
                      link TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS checkpoints (
                      id TEXT PRIMARY KEY,
                      deployment_id TEXT NOT NULL,
                      name TEXT NOT NULL,
                      created_at TEXT NOT NULL,
                      actor TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS verifications (
                      id TEXT PRIMARY KEY,
                      deployment_id TEXT NOT NULL,
                      verified_at TEXT NOT NULL,
                      health TEXT NOT NULL,
                      checks_json TEXT NOT NULL,
                      observation_complete INTEGER NOT NULL,
                      new_severe_issue INTEGER NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS incidents (
                      id TEXT PRIMARY KEY,
                      deployment_id TEXT NOT NULL,
                      verification_id TEXT,
                      opened_at TEXT NOT NULL,
                      resolved_at TEXT,
                      status TEXT NOT NULL,
                      summary TEXT NOT NULL,
                      issue_fingerprints_json TEXT NOT NULL,
                      failed_checks_json TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS expected_states (
                      id TEXT PRIMARY KEY,
                      node_id TEXT NOT NULL UNIQUE,
                      source_deployment_id TEXT NOT NULL,
                      captured_at TEXT NOT NULL,
                      plugins_json TEXT NOT NULL,
                      commands_json TEXT NOT NULL,
                      worlds_json TEXT NOT NULL,
                      services_json TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS recovery_verifications (
                      id TEXT PRIMARY KEY,
                      deployment_id TEXT NOT NULL,
                      restore_plan_id TEXT NOT NULL,
                      verified_at TEXT NOT NULL,
                      outcome TEXT NOT NULL,
                      before_issue_rate REAL NOT NULL,
                      after_issue_rate REAL NOT NULL,
                      changed_checks_json TEXT NOT NULL,
                      summary TEXT NOT NULL
                    )
                    """);
            statement.execute("PRAGMA user_version=3");
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(oldAutoCommit);
        }
    }

    private static void ensureColumn(Statement statement, String table, String column, String definition)
            throws SQLException {
        boolean present = false;
        try (ResultSet columns = statement.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (columns.next()) {
                if (column.equalsIgnoreCase(columns.getString("name"))) {
                    present = true;
                    break;
                }
            }
        }
        if (!present) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    @Override
    public synchronized void upsertNode(ServerNode node) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO server_nodes(id, name, platform_family, environment, created_at)
                VALUES(?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET name=excluded.name
                """)) {
            ps.setString(1, node.id());
            ps.setString(2, node.name());
            ps.setString(3, node.platformFamily());
            ps.setString(4, node.environment());
            ps.setString(5, node.createdAt().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized Optional<ServerNode> findNode(String nodeId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT id, name, platform_family, environment, created_at FROM server_nodes WHERE id=?")) {
            ps.setString(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new ServerNode(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4),
                        Instant.parse(rs.getString(5))
                ));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized long nextSequence(String nodeId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(MAX(local_sequence), 0) + 1 FROM deployments WHERE node_id=?")) {
            ps.setString(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void saveDeployment(Deployment deployment) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO deployments(
                  id, local_sequence, node_id, parent_id, state_fingerprint, started_at, ended_at,
                  startup_ready_at, startup_ready_ms, crash_reports_json,
                  lifecycle, health, health_reasons_json, complete, tags_json, server_implementation,
                  minecraft_version, java_version, java_vendor, components_json, configs_json, schema_version
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, deployment.id());
            ps.setLong(2, deployment.localSequence());
            ps.setString(3, deployment.nodeId());
            ps.setString(4, deployment.parentId());
            ps.setString(5, deployment.stateFingerprint());
            ps.setString(6, deployment.startedAt().toString());
            ps.setString(7, deployment.endedAt() == null ? null : deployment.endedAt().toString());
            ps.setString(8, deployment.startupReadyAt() == null ? null : deployment.startupReadyAt().toString());
            ps.setLong(9, deployment.startupReadyMillis());
            ps.setString(10, mapper.writeValueAsString(deployment.crashReportReferences()));
            ps.setString(11, deployment.lifecycle().name());
            ps.setString(12, deployment.health().name());
            ps.setString(13, mapper.writeValueAsString(deployment.healthReasons()));
            ps.setInt(14, deployment.complete() ? 1 : 0);
            ps.setString(15, mapper.writeValueAsString(deployment.tags()));
            ps.setString(16, deployment.serverImplementation());
            ps.setString(17, deployment.minecraftVersion());
            ps.setString(18, deployment.javaVersion());
            ps.setString(19, deployment.javaVendor());
            ps.setString(20, mapper.writeValueAsString(deployment.components().stream().map(this::toComponentDto).toList()));
            ps.setString(21, mapper.writeValueAsString(deployment.configs().stream().map(this::toConfigDto).toList()));
            ps.setInt(22, deployment.schemaVersion());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized Optional<Deployment> findDeployment(String id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM deployments WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapDeployment(rs));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized Optional<Deployment> findBySequence(String nodeId, long sequence) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM deployments WHERE node_id=? AND local_sequence=?")) {
            ps.setString(1, nodeId);
            ps.setLong(2, sequence);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapDeployment(rs));
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized List<Deployment> listDeployments(String nodeId, int limit) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM deployments WHERE node_id=? ORDER BY local_sequence DESC LIMIT ?")) {
            ps.setString(1, nodeId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<Deployment> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(mapDeployment(rs));
                }
                return list;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void saveIssue(String deploymentId, Issue issue) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO issues(
                  id, deployment_id, fingerprint, normalized_type, normalized_message, ownership_json,
                  first_seen_at, last_seen_at, status, severity, occurrence_count, sample_stack, regression_class
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(deployment_id, fingerprint) DO UPDATE SET
                  occurrence_count=excluded.occurrence_count,
                  last_seen_at=excluded.last_seen_at,
                  status=excluded.status,
                  regression_class=excluded.regression_class,
                  sample_stack=COALESCE(excluded.sample_stack, issues.sample_stack)
                """)) {
            ps.setString(1, issue.id());
            ps.setString(2, deploymentId);
            ps.setString(3, issue.fingerprint());
            ps.setString(4, issue.normalizedType());
            ps.setString(5, issue.normalizedMessage());
            ps.setString(6, mapper.writeValueAsString(issue.ownershipCandidates()));
            ps.setString(7, issue.firstSeenAt().toString());
            ps.setString(8, issue.lastSeenAt().toString());
            ps.setString(9, issue.status().name());
            ps.setString(10, issue.severity());
            ps.setLong(11, issue.occurrenceCount());
            ps.setString(12, issue.sampleStack());
            ps.setString(13, issue.regressionClass().name());
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized List<Issue> listIssues(String deploymentId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM issues WHERE deployment_id=?")) {
            ps.setString(1, deploymentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Issue> issues = new ArrayList<>();
                while (rs.next()) {
                    issues.add(new Issue(
                            rs.getString("id"),
                            rs.getString("fingerprint"),
                            rs.getString("normalized_type"),
                            rs.getString("normalized_message"),
                            mapper.readValue(rs.getString("ownership_json"), new TypeReference<>() {}),
                            Instant.parse(rs.getString("first_seen_at")),
                            Instant.parse(rs.getString("last_seen_at")),
                            IssueStatus.valueOf(rs.getString("status")),
                            rs.getString("severity"),
                            rs.getLong("occurrence_count"),
                            rs.getString("sample_stack"),
                            RegressionClass.valueOf(rs.getString("regression_class"))
                    ));
                }
                return issues;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void markDeploymentHealth(
            String deploymentId,
            DeploymentHealth health,
            List<String> reasons,
            List<String> tags
    ) {
        Optional<Deployment> existing = findDeployment(deploymentId);
        if (existing.isEmpty()) {
            return;
        }
        Deployment updated = existing.get().withHealth(health, reasons).withTags(tags);
        saveDeployment(updated);
    }

    @Override
    public synchronized int countDeployments(String nodeId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM deployments WHERE node_id=?")) {
            ps.setString(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized int pruneDeployments(String nodeId, int keepLimit, String protectDeploymentId) {
        if (keepLimit <= 0) {
            return 0;
        }
        List<Deployment> all = listDeployments(nodeId, Integer.MAX_VALUE);
        if (all.size() <= keepLimit) {
            return 0;
        }
        List<Deployment> candidates = new ArrayList<>();
        for (Deployment deployment : all) {
            if (protectDeploymentId != null && protectDeploymentId.equals(deployment.id())) {
                continue;
            }
            List<String> tags = deployment.tags().stream().map(t -> t.toLowerCase(Locale.ROOT)).toList();
            if (tags.contains("healthy") || tags.contains("approved") || tags.contains("before-upgrade")) {
                continue;
            }
            if (hasRetentionReference(deployment.id())) {
                continue;
            }
            candidates.add(deployment);
        }
        // Oldest first among candidates (listDeployments is DESC).
        candidates.sort((a, b) -> Long.compare(a.localSequence(), b.localSequence()));
        int toRemove = all.size() - keepLimit;
        int removed = 0;
        for (Deployment deployment : candidates) {
            if (removed >= toRemove) {
                break;
            }
            deleteDeploymentCascade(deployment.id());
            removed++;
        }
        return removed;
    }

    private void deleteDeploymentCascade(String deploymentId) {
        try {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM incidents WHERE deployment_id=?")) {
                ps.setString(1, deploymentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM recovery_verifications WHERE deployment_id=?")) {
                ps.setString(1, deploymentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM verifications WHERE deployment_id=?")) {
                ps.setString(1, deploymentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM checkpoints WHERE deployment_id=?")) {
                ps.setString(1, deploymentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM issues WHERE deployment_id=?")) {
                ps.setString(1, deploymentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM annotations WHERE deployment_id=?")) {
                ps.setString(1, deploymentId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM deployments WHERE id=?")) {
                ps.setString(1, deploymentId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean hasRetentionReference(String deploymentId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM checkpoints WHERE deployment_id=? UNION SELECT 1 FROM expected_states WHERE source_deployment_id=? LIMIT 1")) {
            ps.setString(1, deploymentId);
            ps.setString(2, deploymentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void saveAnnotation(Annotation annotation) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO annotations(id, deployment_id, created_at, actor, category, text, link)
                VALUES(?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, annotation.id());
            ps.setString(2, annotation.deploymentId());
            ps.setString(3, annotation.createdAt().toString());
            ps.setString(4, annotation.actor());
            ps.setString(5, annotation.category());
            ps.setString(6, annotation.text());
            ps.setString(7, annotation.link());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized List<Annotation> listAnnotations(String deploymentId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM annotations WHERE deployment_id=? ORDER BY created_at ASC")) {
            ps.setString(1, deploymentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Annotation> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(new Annotation(
                            rs.getString("id"),
                            rs.getString("deployment_id"),
                            Instant.parse(rs.getString("created_at")),
                            rs.getString("actor"),
                            rs.getString("category"),
                            rs.getString("text"),
                            rs.getString("link")
                    ));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void saveCheckpoint(Checkpoint checkpoint) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO checkpoints(id, deployment_id, name, created_at, actor)
                VALUES(?,?,?,?,?)
                """)) {
            ps.setString(1, checkpoint.id());
            ps.setString(2, checkpoint.deploymentId());
            ps.setString(3, checkpoint.name());
            ps.setString(4, checkpoint.createdAt().toString());
            ps.setString(5, checkpoint.actor());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized Optional<Checkpoint> findCheckpoint(String id) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM checkpoints WHERE id=?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapCheckpoint(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized List<Checkpoint> listCheckpoints(String nodeId, int limit) {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT c.* FROM checkpoints c JOIN deployments d ON d.id=c.deployment_id
                WHERE d.node_id=? ORDER BY c.created_at DESC LIMIT ?
                """)) {
            ps.setString(1, nodeId);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                List<Checkpoint> out = new ArrayList<>();
                while (rs.next()) out.add(mapCheckpoint(rs));
                return out;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void saveVerification(DeploymentVerification verification) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO verifications(id, deployment_id, verified_at, health, checks_json,
                  observation_complete, new_severe_issue) VALUES(?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, verification.id());
            ps.setString(2, verification.deploymentId());
            ps.setString(3, verification.verifiedAt().toString());
            ps.setString(4, verification.health().name());
            ps.setString(5, mapper.writeValueAsString(verification.checks().stream().map(CheckResultDto::from).toList()));
            ps.setInt(6, verification.observationWindowComplete() ? 1 : 0);
            ps.setInt(7, verification.newSevereIssue() ? 1 : 0);
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized Optional<DeploymentVerification> findLatestVerification(String deploymentId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM verifications WHERE deployment_id=? ORDER BY verified_at DESC LIMIT 1")) {
            ps.setString(1, deploymentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapVerification(rs)) : Optional.empty();
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void saveIncident(Incident incident) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO incidents(id, deployment_id, verification_id, opened_at, resolved_at,
                  status, summary, issue_fingerprints_json, failed_checks_json) VALUES(?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, incident.id());
            ps.setString(2, incident.deploymentId());
            ps.setString(3, incident.verificationId());
            ps.setString(4, incident.openedAt().toString());
            ps.setString(5, incident.resolvedAt() == null ? null : incident.resolvedAt().toString());
            ps.setString(6, incident.status().name());
            ps.setString(7, incident.summary());
            ps.setString(8, mapper.writeValueAsString(incident.issueFingerprints()));
            ps.setString(9, mapper.writeValueAsString(incident.failedCheckIds()));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized List<Incident> listIncidents(String deploymentId, int limit) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM incidents WHERE deployment_id=? ORDER BY opened_at DESC LIMIT ?")) {
            ps.setString(1, deploymentId);
            ps.setInt(2, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                List<Incident> out = new ArrayList<>();
                while (rs.next()) {
                    String resolved = rs.getString("resolved_at");
                    out.add(new Incident(
                            rs.getString("id"), rs.getString("deployment_id"), rs.getString("verification_id"),
                            Instant.parse(rs.getString("opened_at")), resolved == null ? null : Instant.parse(resolved),
                            IncidentStatus.valueOf(rs.getString("status")), rs.getString("summary"),
                            mapper.readValue(rs.getString("issue_fingerprints_json"), new TypeReference<>() {}),
                            mapper.readValue(rs.getString("failed_checks_json"), new TypeReference<>() {})
                    ));
                }
                return out;
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public synchronized void saveExpectedState(ExpectedState state) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO expected_states(id,node_id,source_deployment_id,captured_at,plugins_json,commands_json,worlds_json,services_json)
                VALUES(?,?,?,?,?,?,?,?) ON CONFLICT(node_id) DO UPDATE SET id=excluded.id,
                source_deployment_id=excluded.source_deployment_id,captured_at=excluded.captured_at,
                plugins_json=excluded.plugins_json,commands_json=excluded.commands_json,
                worlds_json=excluded.worlds_json,services_json=excluded.services_json
                """)) {
            ps.setString(1, state.id()); ps.setString(2, state.nodeId()); ps.setString(3, state.sourceDeploymentId());
            ps.setString(4, state.capturedAt().toString()); ps.setString(5, mapper.writeValueAsString(state.plugins()));
            ps.setString(6, mapper.writeValueAsString(state.commands())); ps.setString(7, mapper.writeValueAsString(state.worlds()));
            ps.setString(8, mapper.writeValueAsString(state.services())); ps.executeUpdate();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Override
    public synchronized Optional<ExpectedState> findExpectedState(String nodeId) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM expected_states WHERE node_id=?")) {
            ps.setString(1, nodeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new ExpectedState(rs.getString("id"), rs.getString("node_id"),
                        rs.getString("source_deployment_id"), Instant.parse(rs.getString("captured_at")),
                        mapper.readValue(rs.getString("plugins_json"), new TypeReference<>() {}),
                        mapper.readValue(rs.getString("commands_json"), new TypeReference<>() {}),
                        mapper.readValue(rs.getString("worlds_json"), new TypeReference<>() {}),
                        mapper.readValue(rs.getString("services_json"), new TypeReference<>() {})));
            }
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Override
    public synchronized void saveRecoveryVerification(RecoveryVerification value) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT OR REPLACE INTO recovery_verifications(id,deployment_id,restore_plan_id,verified_at,outcome,
                before_issue_rate,after_issue_rate,changed_checks_json,summary) VALUES(?,?,?,?,?,?,?,?,?)
                """)) {
            ps.setString(1, value.id()); ps.setString(2, value.deploymentId()); ps.setString(3, value.restorePlanId());
            ps.setString(4, value.verifiedAt().toString()); ps.setString(5, value.outcome().name());
            ps.setDouble(6, value.beforeIssueRate()); ps.setDouble(7, value.afterIssueRate());
            ps.setString(8, mapper.writeValueAsString(value.changedChecks())); ps.setString(9, value.summary()); ps.executeUpdate();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Override
    public synchronized List<RecoveryVerification> listRecoveryVerifications(String deploymentId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT * FROM recovery_verifications WHERE deployment_id=? ORDER BY verified_at DESC")) {
            ps.setString(1, deploymentId);
            try (ResultSet rs = ps.executeQuery()) {
                List<RecoveryVerification> out = new ArrayList<>();
                while (rs.next()) out.add(new RecoveryVerification(rs.getString("id"), rs.getString("deployment_id"),
                        rs.getString("restore_plan_id"), Instant.parse(rs.getString("verified_at")),
                        RecoveryOutcome.valueOf(rs.getString("outcome")), rs.getDouble("before_issue_rate"),
                        rs.getDouble("after_issue_rate"), mapper.readValue(rs.getString("changed_checks_json"),
                        new TypeReference<>() {}), rs.getString("summary")));
                return out;
            }
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private Checkpoint mapCheckpoint(ResultSet rs) throws SQLException {
        return new Checkpoint(rs.getString("id"), rs.getString("deployment_id"), rs.getString("name"),
                Instant.parse(rs.getString("created_at")), rs.getString("actor"));
    }

    private DeploymentVerification mapVerification(ResultSet rs) throws Exception {
        List<CheckResultDto> checks = mapper.readValue(rs.getString("checks_json"), new TypeReference<>() {});
        return new DeploymentVerification(
                rs.getString("id"), rs.getString("deployment_id"), Instant.parse(rs.getString("verified_at")),
                DeploymentHealth.valueOf(rs.getString("health")), checks.stream().map(CheckResultDto::toDomain).toList(),
                rs.getInt("observation_complete") == 1, rs.getInt("new_severe_issue") == 1);
    }

    @Override
    public synchronized String integrityCheck() {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("PRAGMA integrity_check")) {
            if (!rs.next()) {
                return "no-result";
            }
            return rs.getString(1);
        } catch (SQLException e) {
            return e.getMessage();
        }
    }

    @Override
    public synchronized void close() {
        try {
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            } catch (SQLException ignored) {
                // Best effort.
            }
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private Deployment mapDeployment(ResultSet rs) throws Exception {
        List<ComponentDto> componentDtos = mapper.readValue(rs.getString("components_json"), new TypeReference<>() {});
        List<ConfigDto> configDtos = mapper.readValue(rs.getString("configs_json"), new TypeReference<>() {});
        String ended = rs.getString("ended_at");
        String ready = rs.getString("startup_ready_at");
        return Deployment.builder()
                .id(rs.getString("id"))
                .localSequence(rs.getLong("local_sequence"))
                .nodeId(rs.getString("node_id"))
                .parentId(rs.getString("parent_id"))
                .stateFingerprint(rs.getString("state_fingerprint"))
                .startedAt(Instant.parse(rs.getString("started_at")))
                .endedAt(ended == null ? null : Instant.parse(ended))
                .startupReadyAt(ready == null ? null : Instant.parse(ready))
                .startupReadyMillis(rs.getLong("startup_ready_ms"))
                .crashReportReferences(mapper.readValue(
                        rs.getString("crash_reports_json"), new TypeReference<List<String>>() {}))
                .lifecycle(DeploymentLifecycle.valueOf(rs.getString("lifecycle")))
                .health(DeploymentHealth.valueOf(rs.getString("health")))
                .healthReasons(mapper.readValue(rs.getString("health_reasons_json"), new TypeReference<>() {}))
                .complete(rs.getInt("complete") == 1)
                .tags(mapper.readValue(rs.getString("tags_json"), new TypeReference<>() {}))
                .serverImplementation(rs.getString("server_implementation"))
                .minecraftVersion(rs.getString("minecraft_version"))
                .javaVersion(rs.getString("java_version"))
                .javaVendor(rs.getString("java_vendor"))
                .components(componentDtos.stream().map(this::fromComponentDto).toList())
                .configs(configDtos.stream().map(this::fromConfigDto).toList())
                .schemaVersion(rs.getInt("schema_version"))
                .build();
    }

    private ComponentDto toComponentDto(ComponentSnapshot snapshot) {
        ComponentIdentity id = snapshot.identity();
        return new ComponentDto(
                id.type().name(),
                id.normalizedName(),
                id.declaredVersion(),
                id.binaryHash(),
                id.authors(),
                id.dependencies(),
                id.softDependencies(),
                id.mainClass(),
                id.apiVersion(),
                snapshot.relativePath(),
                snapshot.sizeBytes(),
                snapshot.loaded(),
                snapshot.enabled(),
                snapshot.loadFailure()
        );
    }

    private ComponentSnapshot fromComponentDto(ComponentDto dto) {
        return new ComponentSnapshot(
                new ComponentIdentity(
                        ComponentType.valueOf(dto.type),
                        dto.normalizedName,
                        dto.declaredVersion,
                        dto.binaryHash,
                        dto.authors,
                        dto.dependencies,
                        dto.softDependencies,
                        dto.mainClass,
                        dto.apiVersion
                ),
                dto.relativePath,
                dto.sizeBytes,
                dto.loaded,
                dto.enabled,
                dto.loadFailure
        );
    }

    private ConfigDto toConfigDto(ConfigSnapshot snapshot) {
        return new ConfigDto(snapshot.ownerComponent(), snapshot.relativePath(), snapshot.sha256(),
                snapshot.captureLevel(), snapshot.sizeBytes(), snapshot.structuralKeyCount());
    }

    private ConfigSnapshot fromConfigDto(ConfigDto dto) {
        return new ConfigSnapshot(dto.ownerComponent, dto.relativePath, dto.sha256, dto.captureLevel,
                dto.sizeBytes, dto.structuralKeyCount);
    }

    private record ComponentDto(
            String type,
            String normalizedName,
            String declaredVersion,
            String binaryHash,
            List<String> authors,
            List<String> dependencies,
            List<String> softDependencies,
            String mainClass,
            String apiVersion,
            String relativePath,
            long sizeBytes,
            boolean loaded,
            boolean enabled,
            String loadFailure
    ) {
    }

    private record ConfigDto(String ownerComponent, String relativePath, String sha256, String captureLevel,
                             long sizeBytes, int structuralKeyCount) {
    }

    private record CheckResultDto(String checkId, String displayName, String status, String criticality,
                                  String summary, java.util.Map<String, Object> safeDetails) {
        static CheckResultDto from(CheckResult result) {
            return new CheckResultDto(result.checkId(), result.displayName(), result.status().name(),
                    result.criticality().name(), result.summary(), result.safeDetails());
        }

        CheckResult toDomain() {
            return new CheckResult(checkId, displayName, CheckStatus.valueOf(status),
                    CheckCriticality.valueOf(criticality), summary, safeDetails);
        }
    }
}
