package com.paymenthub.utils;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Base64;
import java.util.*;

import com.paymenthub.customresource.PaymentHubDeployment;

/**
 * Utility class for managing Kubernetes resources like ConfigMaps and Secrets.
 */
public class ResourceUtils {
    private static final Logger log = LoggerFactory.getLogger(ResourceUtils.class);
    private final KubernetesClient kubernetesClient;

    public ResourceUtils(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    /**
     * Reconciles the ConfigMap for the given custom resource. Creates or updates the ConfigMap as necessary.
     */
    public void reconcileConfigmap(PaymentHubDeployment resource) {
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

    private ConfigMap createConfigMap(PaymentHubDeployment resource, String name) {
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

        // The operations-web image's default nginx config uses `aio on`, which calls
        // io_setup() — a Linux AIO syscall not available on Colima/macOS kernels.
        // Override default.conf with a config that omits aio, matching the Helm chart.
        if ("ph-ee-operations-web".equals(resource.getMetadata().getName())) {
            data.put("default.conf",
                "server {\n" +
                "    listen       80;\n" +
                "    server_name  localhost;\n" +
                "    root   /usr/share/nginx/html;\n" +
                "    index  index.html index.htm;\n" +
                "\n" +
                "    sendfile off;\n" +
                "\n" +
                "    location / {\n" +
                "        try_files $uri $uri/ /index.html;\n" +
                "    }\n" +
                "\n" +
                "    location ~* \\.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {\n" +
                "        expires 1y;\n" +
                "        add_header Cache-Control \"public, immutable\";\n" +
                "    }\n" +
                "\n" +
                "    location ~* (index\\.html|env\\.js)$ {\n" +
                "        add_header Cache-Control \"no-store, no-cache, must-revalidate\";\n" +
                "    }\n" +
                "\n" +
                "    error_page   500 502 503 504  /50x.html;\n" +
                "    location = /50x.html {\n" +
                "        root   /usr/share/nginx/html;\n" +
                "    }\n" +
                "}\n");
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
    public void reconcileSecret(PaymentHubDeployment resource) {
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

    private Secret createSecret(PaymentHubDeployment resource, String secretName) {
        SecretBuilder secretBuilder = new SecretBuilder()
                .withNewMetadata()
                    .withName(secretName)
                    .withNamespace(resource.getMetadata().getNamespace())
                    .withOwnerReferences(OwnerReferenceUtils.createOwnerReferences(resource))
                .endMetadata();

        if ("ph-ee-connector-bulk".equals(resource.getMetadata().getName())) {
            secretBuilder
                .addToData("aws-access-key", Base64.getEncoder().encodeToString("root".getBytes()))
                .addToData("aws-secret-key", Base64.getEncoder().encodeToString("password".getBytes()))
                .addToData("aws-region", Base64.getEncoder().encodeToString("ap-south-1".getBytes()));
            return secretBuilder.build();
        }

        if ("message-gateway".equals(resource.getMetadata().getName())) {
            secretBuilder
                .addToData("api-key", Base64.getEncoder().encodeToString("<api-key>".getBytes()))
                .addToData("project-id", Base64.getEncoder().encodeToString("<project-id>".getBytes()))
                .addToData("database-password", Base64.getEncoder().encodeToString("password".getBytes()));
            return secretBuilder.build();
        }

        secretBuilder.addToData("database-password", Base64.getEncoder().encodeToString("password".getBytes()));
        return secretBuilder.build();
    }
}
