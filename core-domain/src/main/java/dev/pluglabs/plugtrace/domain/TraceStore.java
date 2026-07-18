package dev.pluglabs.plugtrace.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Persistence boundary — implementations must not block server/region threads. */
public interface TraceStore {
    void upsertNode(ServerNode node);

    Optional<ServerNode> findNode(String nodeId);

    long nextSequence(String nodeId);

    void saveDeployment(Deployment deployment);

    Optional<Deployment> findDeployment(String id);

    Optional<Deployment> findBySequence(String nodeId, long sequence);

    List<Deployment> listDeployments(String nodeId, int limit);

    int countDeployments(String nodeId);

    /**
     * Deletes oldest deployments beyond {@code keepLimit}, never deleting {@code protectDeploymentId}.
     * Skips deployments tagged healthy/approved/before-upgrade when possible.
     * @return number of deployments removed
     */
    int pruneDeployments(String nodeId, int keepLimit, String protectDeploymentId);

    void saveIssue(String deploymentId, Issue issue);

    List<Issue> listIssues(String deploymentId);

    void markDeploymentHealth(String deploymentId, DeploymentHealth health, List<String> reasons, List<String> tags);

    void saveAnnotation(Annotation annotation);

    List<Annotation> listAnnotations(String deploymentId);

    void saveCheckpoint(Checkpoint checkpoint);

    Optional<Checkpoint> findCheckpoint(String id);

    List<Checkpoint> listCheckpoints(String nodeId, int limit);

    void saveVerification(DeploymentVerification verification);

    Optional<DeploymentVerification> findLatestVerification(String deploymentId);

    void saveIncident(Incident incident);

    List<Incident> listIncidents(String deploymentId, int limit);

    void saveExpectedState(ExpectedState expectedState);

    Optional<ExpectedState> findExpectedState(String nodeId);

    void saveRecoveryVerification(RecoveryVerification verification);

    List<RecoveryVerification> listRecoveryVerifications(String deploymentId);

    /** @return "ok" or integrity_check failure detail */
    String integrityCheck();

    void close();
}
