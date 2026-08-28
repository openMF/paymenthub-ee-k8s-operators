package com.paymenthub;

import io.fabric8.kubernetes.api.model.ConfigMapVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EmptyDirVolumeSource;
import io.fabric8.kubernetes.api.model.LabelSelectorBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.PodSpec;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpec;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.Volume;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpec;
import io.fabric8.kubernetes.api.model.apps.DeploymentSpecBuilder;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;

import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.api.reconciler.Cleaner;
import io.javaoperatorsdk.operator.api.reconciler.DeleteControl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paymenthub.customresource.PaymentHubDeployment;
import com.paymenthub.customresource.PaymentHubDeploymentSpec;
import com.paymenthub.utils.LoggingUtil;
import com.paymenthub.utils.StatusUpdateUtil;
import com.paymenthub.utils.DeletionUtil;
import com.paymenthub.utils.DeploymentUtils;
import com.paymenthub.utils.RbacUtils;
import com.paymenthub.utils.ResourceUtils;
import com.paymenthub.utils.NetworkingUtils;
import com.paymenthub.utils.OwnerReferenceUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ControllerConfiguration
public class PaymentHubDeploymentController implements Reconciler<PaymentHubDeployment>, Cleaner<PaymentHubDeployment> {

    private static final String TLS_INIT_IMAGE  = "eclipse-temurin:17.0.11_9-jdk-jammy";
    private static final String CURL_INIT_IMAGE = "curlimages/curl:8.7.1";
    private static final String WAIT_DB_IMAGE   = "busybox:1.36";

    private static final Logger log = LoggerFactory.getLogger(PaymentHubDeploymentController.class);
    private final KubernetesClient kubernetesClient;

    /**
     * Constructor for initializing the PaymentHubDeploymentController with the necessary Kubernetes client.
     *
     * @param kubernetesClient The Kubernetes client used for interacting with the Kubernetes API server.
     */
    public PaymentHubDeploymentController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    /**
     * Reconciles the custom resource by managing associated Kubernetes resources such as RBAC, Secrets, ConfigMaps,
     * Ingress, Services, and the Deployment itself. Handles the enablement and disablement of these resources
     * based on the specifications defined in the custom resource.
     *
     * @param resource The custom resource containing the specifications for the various Kubernetes resources.
     * @param context  The context in which the reconciliation is taking place, providing access to cached resources.
     * @return UpdateControl<PaymentHubDeployment> The control object that dictates the next steps for the reconciliation loop.
     */
    @Override
    public UpdateControl<PaymentHubDeployment> reconcile(PaymentHubDeployment resource, Context<PaymentHubDeployment> context) {
        String resourceName = resource.getMetadata().getName();

        // Check if the deployment is disabled
        if (resource.getSpec().getEnabled() == null || !resource.getSpec().getEnabled()) {
            log.info("Deployment {} is disabled, deleting all associated resources.", resourceName);
            DeletionUtil.deleteResources(kubernetesClient, resource);
            return StatusUpdateUtil.updateDisabledStatus(resource);
        }

        // Log detailed resource information for debugging
        LoggingUtil.logResourceDetails(resource);

        try {
            // Check and reconcile RBACs
            if (resource.getSpec().getRbacEnabled() == null || !resource.getSpec().getRbacEnabled()) {
                log.info("RBACs for resource {} are disabled, deleting associated RBAC resources.", resourceName);
                DeletionUtil.deleteRbacResources(kubernetesClient, resource);
            } else {
                // INFO level log to indicate RBAC reconciliation start
                log.info("Reconciling RBAC resources for {}.", resourceName);
                RbacUtils.reconcileServiceAccount(kubernetesClient, resource);
                RbacUtils.reconcileRole(kubernetesClient, resource);
                RbacUtils.reconcileRoleBinding(kubernetesClient, resource);
                RbacUtils.reconcileClusterRole(kubernetesClient, resource);
                RbacUtils.reconcileClusterRoleBinding(kubernetesClient, resource);
            }

            // Check and reconcile Secrets
            if (resource.getSpec().getSecretEnabled() == null || !resource.getSpec().getSecretEnabled()) {
                log.info("Secrets for resource {} are disabled, deleting associated Secret resources.", resourceName);
                DeletionUtil.deleteSecretResources(kubernetesClient, resource);
            } else {
                // DEBUG level log to indicate Secret reconciliation
                log.debug("Reconciling Secret for {}.", resourceName);
                ResourceUtils.reconcileSecret(kubernetesClient, resource);
            }

            // Check and reconcile ConfigMaps
            if (resource.getSpec().getConfigMapEnabled() == null || !resource.getSpec().getConfigMapEnabled()) {
                log.info("ConfigMap for resource {} is disabled, deleting associated ConfigMap resources.", resourceName);
                DeletionUtil.deleteConfigMapResources(kubernetesClient, resource);
            } else {
                // DEBUG level log to indicate ConfigMap reconciliation
                log.debug("Reconciling ConfigMap for {}.", resourceName);
                ResourceUtils.reconcileConfigmap(kubernetesClient, resource);
            }

            // Always reconcile Services when enabled
            NetworkingUtils.reconcileServices(kubernetesClient, resource);

            // Reconcile Ingress conditionally
            if (resource.getSpec().getIngressEnabled() == null || !resource.getSpec().getIngressEnabled()) {
                log.info("Ingress for resource {} is disabled, deleting Ingress if present.", resourceName);
                DeletionUtil.deleteIngressResources(kubernetesClient, resource);
            } else {
                log.info("Reconciling Ingress for {}.", resourceName);
                NetworkingUtils.reconcileIngress(kubernetesClient, resource);
            }

            // Always reconcile the Deployment itself
            log.info("Reconciling Deployment for {}.", resourceName);
            reconcileDeployment(resource);

            // Return success status update
            log.info("Reconciliation successful for {}.", resourceName);
            return StatusUpdateUtil.updateStatus(resource, resource.getSpec().getReplicas(), resource.getSpec().getImage(), true, "");

        } catch (Exception e) {
            // Log the error and return an error status update
            log.error("Error during reconciliation for resource " + resourceName, e);
            return StatusUpdateUtil.updateErrorStatus(resource, resource.getSpec().getImage(), e);
        }
    }

    /**
     * Called when a PaymentHubDeployment CR is deleted directly. Cleans up cluster-scoped
     * resources (ClusterRole, ClusterRoleBinding) that owner-references cannot GC automatically.
     */
    @Override
    public DeleteControl cleanup(PaymentHubDeployment resource, Context<PaymentHubDeployment> context) {
        log.info("CR {} deleted — cleaning up cluster-scoped RBAC resources.", resource.getMetadata().getName());
        DeletionUtil.deleteRbacResources(kubernetesClient, resource);
        return DeleteControl.defaultDelete();
    }

    /**
     * Reconciles the Deployment based on the given custom resource.
     *
     * @param resource The custom resource containing the specifications for the deployment.
     */
    private void reconcileDeployment(PaymentHubDeployment resource) {
        log.info("Reconciling Deployment for resource: {}", resource.getMetadata().getName());
        Deployment deployment = createDeployment(resource);
        log.debug("Created Deployment spec: {}", deployment);

        Resource<Deployment> deploymentResource = kubernetesClient.apps().deployments()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName());

        if (deploymentResource.get() == null) {
            deploymentResource.create(deployment);
            log.info("Created new Deployment: {}", resource.getMetadata().getName());
        } else {
            deploymentResource.patch(deployment);
            log.info("Updated existing Deployment: {}", resource.getMetadata().getName());
        }
    }

    /**
     * Creates a Kubernetes Deployment object based on the custom resource specifications.
     *
     * @param resource The custom resource specifying the deployment configuration.
     * @return The created Deployment object, or null if critical fields are missing.
     */
    private Deployment createDeployment(PaymentHubDeployment resource) {
        log.info("Creating Deployment spec for resource: {}", resource.getMetadata().getName());

        // Full label set for Deployment metadata and pod template. Copy defensively —
        // resource.getSpec().getLabels() is the live map on the (possibly cached) CR,
        // and must never be mutated in place.
        Map<String, String> labels = resource.getSpec().getLabels();
        labels = labels == null ? new HashMap<>() : new HashMap<>(labels);
        labels.putIfAbsent("app", resource.getMetadata().getName());
        labels.putIfAbsent("app.kubernetes.io/managed-by", "paymenthub-operator");

        // Selector uses only the stable "app" label — never include managed-by here,
        // as spec.selector is immutable and adding it breaks Helm-pre-created Deployments.
        Map<String, String> selectorLabels = new HashMap<>();
        selectorLabels.put("app", resource.getMetadata().getName());

        // Build the main container with environment variables, resources, and volume mounts
        ContainerBuilder containerBuilder = new ContainerBuilder()
            .withName(resource.getMetadata().getName())
            .withImage(resource.getSpec().getImage())
            .withEnv(DeploymentUtils.createEnvironmentVariables(resource))
            .withResources(DeploymentUtils.createResourceRequirements(resource))
            .withLivenessProbe(DeploymentUtils.createProbe(resource, "liveness"))
            .withReadinessProbe(DeploymentUtils.createProbe(resource, "readiness"));

        // Conditionally add the container port if it's provided in the CR
        Integer containerPort = resource.getSpec().getContainerPort();
        if (containerPort != null) {
            containerBuilder.withPorts(new ContainerPortBuilder()
                .withContainerPort(containerPort)
                .build());
        } else {
            log.info("Container port not provided, skipping port configuration.");
        }

        // Logging for volume mount configuration
        log.debug("Volume mount configuration: {}", resource.getSpec().getVolMount());

        // Add volume mount conditionally
        if (resource.getSpec().getVolMount() != null && Boolean.TRUE.equals(resource.getSpec().getVolMount().getEnabled())) {
            String volMountName = resource.getSpec().getVolMount().getName();

            if (volMountName != null) {
                List<PaymentHubDeploymentSpec.VolMount.SubPathMount> subPathMounts = resource.getSpec().getVolMount().getMounts();

                if (subPathMounts != null && !subPathMounts.isEmpty()) {
                    // CR declares one or more explicit subPath mounts from the same
                    // ConfigMap-backed volume (e.g. multiple files at distinct paths).
                    for (PaymentHubDeploymentSpec.VolMount.SubPathMount mount : subPathMounts) {
                        containerBuilder.addToVolumeMounts(new VolumeMountBuilder()
                            .withName(volMountName)
                            .withMountPath(mount.getMountPath())
                            .withSubPath(mount.getSubPath())
                            .build());
                    }
                } else {
                    // Default: a single generic mount at /config.
                    containerBuilder.addToVolumeMounts(new VolumeMountBuilder()
                        .withName(volMountName)
                        .withMountPath("/config")
                        .build());
                }
            } else {
                log.warn("Volume mount name is null, skipping volume mount.");
            }
        }

        // Add /tls volume mount to main container when TLS keystore is required (must be before build())
        if (Boolean.TRUE.equals(resource.getSpec().getTlsKeystoreEnabled())) {
            containerBuilder.addToVolumeMounts(new VolumeMountBuilder()
                .withName("tls-volume")
                .withMountPath("/tls")
                .build());
        }

        Container container = containerBuilder.build();

        // Create PodSpec with the defined container and volumes
        PodSpecBuilder podSpecBuilder = new PodSpecBuilder()
            .withContainers(container);

        // Build ordered list of init containers
        List<Container> initContainers = new ArrayList<>();

        if (Boolean.TRUE.equals(resource.getSpec().getTlsKeystoreEnabled())) {
            log.info("create-tls-keystore init container enabled for {}.", resource.getMetadata().getName());
            initContainers.add(new ContainerBuilder()
                .withName("create-tls-keystore")
                .withImage(TLS_INIT_IMAGE)
                .withCommand("sh", "-c")
                .withArgs("keytool -genkeypair -alias ams-mifos -keyalg RSA -keysize 2048 -storetype PKCS12 -keystore /tls/keystore.p12 -storepass changeit -keypass changeit -validity 3650 -dname \"CN=ams-mifos, OU=PaymentHub, O=Mifos, L=City, ST=State, C=US\" && echo 'Keystore created.' && ls -la /tls/")
                .withVolumeMounts(new VolumeMountBuilder()
                    .withName("tls-volume")
                    .withMountPath("/tls")
                    .build())
                .build());
        }

        if (Boolean.TRUE.equals(resource.getSpec().getWaitForGatewayEnabled())) {
            log.info("wait-for-gateway init container enabled for {}.", resource.getMetadata().getName());
            initContainers.add(new ContainerBuilder()
                .withName("wait-for-gateway")
                .withImage(CURL_INIT_IMAGE)
                .withCommand("sh", "-c")
                .withArgs("until curl -s http://paymenthub-infra-zeebe-gateway:9600/actuator/health/liveness | grep -q '\"status\":\"UP\"'; do echo 'Waiting for Zeebe gateway...'; sleep 2; done; echo 'Zeebe gateway is up.'")
                .build());
        }

        if (Boolean.TRUE.equals(resource.getSpec().getInitContainerEnabled())) {
            log.info("wait-db init container enabled for {}.", resource.getMetadata().getName());
            initContainers.add(new ContainerBuilder()
                .withName("wait-db")
                .withImage(WAIT_DB_IMAGE)
                .withCommand("sh", "-c")
                .withArgs("until nc -z operationsmysql 3306; do echo 'Waiting for MySQL...'; sleep 2; done; echo 'MySQL ready.'")
                .build());
        }

        if (!initContainers.isEmpty()) {
            podSpecBuilder.withInitContainers(initContainers);
        }

        // Add volumes conditionally
        List<Volume> volumes = new ArrayList<>();

        if (resource.getSpec().getVolMount() != null && Boolean.TRUE.equals(resource.getSpec().getVolMount().getEnabled())) {
            String volMountName = resource.getSpec().getVolMount().getName();
            if (volMountName != null) {
                volumes.add(new VolumeBuilder()
                    .withName(volMountName)
                    .withConfigMap(new ConfigMapVolumeSourceBuilder()
                        .withName(volMountName)
                        .build())
                    .build());
            } else {
                log.warn("Volume mount name is null, skipping volume creation.");
            }
        }

        if (Boolean.TRUE.equals(resource.getSpec().getTlsKeystoreEnabled())) {
            volumes.add(new VolumeBuilder()
                .withName("tls-volume")
                .withEmptyDir(new EmptyDirVolumeSource())
                .build());
        }

        if (!volumes.isEmpty()) {
            podSpecBuilder.withVolumes(volumes);
        }

        PodSpec podSpec = podSpecBuilder.build();

        // Build the PodTemplateSpec with metadata and spec
        PodTemplateSpec podTemplateSpec = new PodTemplateSpecBuilder()
            .withNewMetadata()
                .withLabels(labels)
            .endMetadata()
            .withSpec(podSpec)
            .build();

        // Define the DeploymentSpec with replicas, selector, and template
        DeploymentSpec deploymentSpec = new DeploymentSpecBuilder()
            .withReplicas(resource.getSpec().getReplicas())
            .withSelector(new LabelSelectorBuilder()
                .withMatchLabels(selectorLabels)
                .build())
            .withTemplate(podTemplateSpec)
            .build();

        // Handle the case where metadata fields might be null
        String name = resource.getMetadata().getName();
        String namespace = resource.getMetadata().getNamespace();

        if (name == null || namespace == null) {
            throw new IllegalStateException("CR " + resource.getMetadata().getName() + " has null name or namespace");
        }

        // Create Deployment metadata with owner references
        ObjectMeta metadata = new ObjectMetaBuilder()
            .withName(name)
            .withNamespace(namespace)
            .withLabels(labels)
            .withOwnerReferences(OwnerReferenceUtils.createOwnerReferences(resource))
            .build();

        // Log the final deployment object for debugging purposes
        log.debug("Final Deployment object: {}", metadata);

        // Build the final Deployment object
        return new DeploymentBuilder()
            .withMetadata(metadata)
            .withSpec(deploymentSpec)
            .build();
    }

}
