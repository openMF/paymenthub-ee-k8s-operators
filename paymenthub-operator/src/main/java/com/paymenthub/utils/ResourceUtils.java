package com.paymenthub.utils;

import io.fabric8.kubernetes.api.model.ConfigMap;
import io.fabric8.kubernetes.api.model.ConfigMapBuilder;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import com.paymenthub.customresource.PaymentHubDeployment;

/**
 * Utility class for managing Kubernetes resources like ConfigMaps and Secrets.
 */
public class ResourceUtils {
    private static final Logger log = LoggerFactory.getLogger(ResourceUtils.class);

    private static final Map<String, String> DEFAULT_SECRET_DATA = Map.of("database-password", "password");

    private ResourceUtils() {
    }

    /**
     * Reconciles the ConfigMap for the given custom resource. Creates or updates the ConfigMap as necessary.
     */
    public static void reconcileConfigmap(KubernetesClient kubernetesClient, PaymentHubDeployment resource) {
        String name = resource.getMetadata().getName() + "-configmap";
        log.info("Reconciling ConfigMap for resource: {}", resource.getMetadata().getName());
        ConfigMap configMap = createConfigMap(resource, name);

        Resource<ConfigMap> configMapResource = kubernetesClient.configMaps()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(name);

        if (configMapResource.get() == null) {
            configMapResource.create(configMap);
            log.info("Created new ConfigMap: {}", name);
        } else {
            configMapResource.patch(configMap);
            log.info("Updated existing ConfigMap: {}", name);
        }
    }

    private static ConfigMap createConfigMap(PaymentHubDeployment resource, String name) {
        String domain = resource.getSpec().getDomain();

        Map<String, String> data = new HashMap<>();
        data.put("configuration.properties",
            "oauth.enabled false\n" +
            "oauth.basicAuth true\n" +
            "oauth.basicAuthToken Y2xpZW50Og==\n" +
            "oauth.serverUrl https://ops-bk." + domain + "\n" +
            "serverUrl https://ops-bk." + domain + "\n" +
            "auth.enabled false\n" +
            "auth.tenant phdefault");

        // Any additional literal key/value pairs the CR declares (e.g. an nginx
        // default.conf override) are merged in as-is — the operator has no
        // per-component knowledge of what a given CR needs here.
        if (resource.getSpec().getConfigMapData() != null) {
            data.putAll(resource.getSpec().getConfigMapData());
        }

        return new ConfigMapBuilder()
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(resource.getMetadata().getNamespace())
                    .withOwnerReferences(OwnerReferenceUtils.createOwnerReferences(resource))
                .endMetadata()
                .addToData(data)
                .build();
    }

    /**
     * Reconciles the Secret for the given custom resource. Creates or updates the Secret as necessary.
     */
    public static void reconcileSecret(KubernetesClient kubernetesClient, PaymentHubDeployment resource) {
        String secretName = resource.getMetadata().getName() + "-secret";
        log.info("Reconciling Secret for resource: {}", resource.getMetadata().getName());
        Secret secret = createSecret(resource, secretName);

        Resource<Secret> secretResource = kubernetesClient.secrets()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(secretName);

        if (secretResource.get() == null) {
            secretResource.create(secret);
            log.info("Created new Secret: {}", secretName);
        } else {
            secretResource.patch(secret);
            log.info("Updated existing Secret: {}", secretName);
        }
    }

    private static Secret createSecret(PaymentHubDeployment resource, String secretName) {
        Map<String, String> secretData = resource.getSpec().getSecretData();
        if (secretData == null || secretData.isEmpty()) {
            secretData = DEFAULT_SECRET_DATA;
        }

        return new SecretBuilder()
                .withNewMetadata()
                    .withName(secretName)
                    .withNamespace(resource.getMetadata().getNamespace())
                    .withOwnerReferences(OwnerReferenceUtils.createOwnerReferences(resource))
                .endMetadata()
                .withStringData(secretData)
                .build();
    }
}
