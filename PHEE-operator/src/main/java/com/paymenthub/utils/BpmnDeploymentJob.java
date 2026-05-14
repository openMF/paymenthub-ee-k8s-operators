package com.paymenthub.utils;

import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import com.paymenthub.customresource.PaymentHubDeployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BpmnDeploymentJob {

    private static final Logger log = LoggerFactory.getLogger(BpmnDeploymentJob.class);
    static final String JOB_NAME = "phee-bpmn-deploy";
    static final String CONFIGMAP_NAME = "phee-bpmn-diagrams";

    public static void createIfNeeded(KubernetesClient client, String namespace) {
        Job existing = client.batch().v1().jobs()
            .inNamespace(namespace).withName(JOB_NAME).get();
        if (existing != null) {
            log.info("BPMN deploy job already exists, skipping creation.");
            return;
        }

        String tenants = resolveTenants(client, namespace);
        log.info("Creating BPMN deploy job for tenants: {}", tenants);

        Job job = new JobBuilder()
            .withNewMetadata()
                .withName(JOB_NAME)
                .withNamespace(namespace)
            .endMetadata()
            .withNewSpec()
                .withBackoffLimit(3)
                .withNewTemplate()
                    .withNewSpec()
                        .withRestartPolicy("OnFailure")
                        .addNewVolume()
                            .withName("bpmn-files")
                            .withNewConfigMap().withName(CONFIGMAP_NAME).endConfigMap()
                        .endVolume()
                        .addNewContainer()
                            .withName("bpmn-uploader")
                            .withImage("curlimages/curl:8.7.1")
                            .addNewEnv().withName("TENANTS").withValue(tenants).endEnv()
                            .withCommand("sh", "-c",
                                "for tenant in $(echo $TENANTS | tr ',' ' '); do " +
                                "  for f in /bpmn/*.bpmn; do " +
                                "    curl -sf -X POST http://ph-ee-zeebe-ops:8080/zeebe/upload " +
                                "      -H \"Platform-TenantId: $tenant\" " +
                                "      --form \"file=@$f\" || exit 1; " +
                                "  done; " +
                                "done")
                            .addNewVolumeMount()
                                .withName("bpmn-files")
                                .withMountPath("/bpmn")
                            .endVolumeMount()
                        .endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();

        client.batch().v1().jobs().inNamespace(namespace).create(job);
        log.info("Created BPMN deploy job: {}", JOB_NAME);
    }

    private static String resolveTenants(KubernetesClient client, String namespace) {
        try {
            PaymentHubDeployment bulkProc = client.resources(PaymentHubDeployment.class)
                .inNamespace(namespace).withName("ph-ee-bulk-processor").get();
            if (bulkProc != null && bulkProc.getSpec().getEnvironment() != null) {
                return bulkProc.getSpec().getEnvironment().stream()
                    .filter(e -> "TENANTS".equals(e.getName()))
                    .map(e -> e.getValue())
                    .findFirst()
                    .orElse("greenbank");
            }
        } catch (Exception e) {
            log.warn("Could not read ph-ee-bulk-processor CR for tenants, using default: {}", e.getMessage());
        }
        return "greenbank";
    }
}
