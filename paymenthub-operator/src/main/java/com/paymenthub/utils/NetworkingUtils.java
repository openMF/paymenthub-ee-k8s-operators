package com.paymenthub.utils;

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRuleBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressTLS;
import io.fabric8.kubernetes.api.model.networking.v1.HTTPIngressPathBuilder;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;

import com.paymenthub.customresource.PaymentHubDeployment;
import com.paymenthub.customresource.PaymentHubDeploymentSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class NetworkingUtils {

    private static final Logger log = LoggerFactory.getLogger(NetworkingUtils.class);

    private NetworkingUtils() {
    }

    /**
     * Reconciles the Services for the given custom resource.
     * This includes creating, updating, or deleting services as necessary.
     *
     * @param resource The custom resource specifying the service configuration.
     */
    public static void reconcileServices(KubernetesClient kubernetesClient, PaymentHubDeployment resource) {
        log.info("Reconciling Services for resource: {}", resource.getMetadata().getName());

        List<Service> desiredServices = createServices(resource);
        log.debug("Desired Service specs: {}", desiredServices.stream().map(Service::toString).collect(Collectors.joining(", ")));

        // Get the list of existing services in the namespace
        List<Service> existingServices = kubernetesClient.services()
                .inNamespace(resource.getMetadata().getNamespace())
                .list()
                .getItems()
                .stream()
                .filter(service -> desiredServices.stream().anyMatch(desiredService -> desiredService.getMetadata().getName().equals(service.getMetadata().getName())))
                .collect(Collectors.toList());

        for (Service desiredService : desiredServices) {
            Optional<Service> existingServiceOpt = existingServices.stream()
                    .filter(existingService -> existingService.getMetadata().getName().equals(desiredService.getMetadata().getName()))
                    .findFirst();

            if (existingServiceOpt.isPresent()) {
                Service existingService = existingServiceOpt.get();

                // Compare the specs and only update if necessary
                if (!areServicesEqual(existingService, desiredService)) {
                    kubernetesClient.services()
                            .inNamespace(resource.getMetadata().getNamespace())
                            .withName(existingService.getMetadata().getName())
                            .patch(desiredService); // Apply a patch instead of replacing the service
                    log.info("Updated existing Service: {}", desiredService.getMetadata().getName());
                } else {
                    log.info("Service is up-to-date: {}", desiredService.getMetadata().getName());
                }
            } else {
                // Create the service if it doesn't exist
                kubernetesClient.services()
                        .inNamespace(resource.getMetadata().getNamespace())
                        .create(desiredService);
                log.info("Created new Service: {}", desiredService.getMetadata().getName());
            }
        }
    }

    // Helper method to compare services based on significant fields
    private static boolean areServicesEqual(Service existingService, Service desiredService) {
        // Compare important fields such as spec, ports, selectors, etc.
        return Objects.equals(existingService.getSpec().getPorts(), desiredService.getSpec().getPorts())
                && Objects.equals(existingService.getSpec().getSelector(), desiredService.getSpec().getSelector())
                && Objects.equals(existingService.getSpec().getType(), desiredService.getSpec().getType());
    }

    /**
     * Creates a list of Kubernetes Service objects based on the custom resource specifications.
     *
     * @param resource The custom resource specifying the service configuration.
     * @return A list of created Service objects.
     */
    private static List<Service> createServices(PaymentHubDeployment resource) {
        log.info("Creating Services spec for resource: {}", resource.getMetadata().getName());

        PaymentHubDeploymentSpec spec = resource.getSpec();
        List<PaymentHubDeploymentSpec.Service> serviceSpecs = spec.getServices();
        if (serviceSpecs == null) return Collections.emptyList();

        return serviceSpecs.stream()
                .map(serviceSpec -> {
                    List<PaymentHubDeploymentSpec.Service.Port> rawPorts =
                            serviceSpec.getPorts() != null ? serviceSpec.getPorts() : Collections.emptyList();
                    List<ServicePort> ports = rawPorts.stream()
                            .map(portSpec -> new ServicePortBuilder()
                                    .withName(portSpec.getName())
                                    .withPort(portSpec.getPort())
                                    .withTargetPort(new IntOrString(portSpec.getTargetPort()))
                                    .withProtocol(portSpec.getProtocol())
                                    .build())
                            .collect(Collectors.toList());

                    // Copy defensively — serviceSpec.getLabels() is the live map on the
                    // CR spec and must never be mutated in place.
                    Map<String, String> labels = serviceSpec.getLabels();
                    labels = labels == null ? new HashMap<>() : new HashMap<>(labels);

                    labels.putIfAbsent("app", resource.getMetadata().getName());
                    labels.putIfAbsent("app.kubernetes.io/managed-by", "paymenthub-operator");

                    return new ServiceBuilder()
                            .withNewMetadata()
                                .withName(serviceSpec.getName())
                                .withNamespace(resource.getMetadata().getNamespace())
                                .withLabels(labels)
                                .withAnnotations(serviceSpec.getAnnotations())
                                .withOwnerReferences(OwnerReferenceUtils.createOwnerReferences(resource))
                            .endMetadata()
                            .withNewSpec()
                                .withSelector(serviceSpec.getSelector() != null ? serviceSpec.getSelector() :
                                        Map.of("app", resource.getMetadata().getName()))
                                .withPorts(ports)
                                .withType(serviceSpec.getType() != null ? serviceSpec.getType() : "ClusterIP")
                                .withSessionAffinity(serviceSpec.getSessionAffinity())
                            .endSpec()
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Reconciles the Ingress for the given custom resource.
     * This includes creating or updating the Ingress as necessary.
     *
     * @param resource The custom resource specifying the Ingress configuration.
     */
    public static void reconcileIngress(KubernetesClient kubernetesClient, PaymentHubDeployment resource) {
        String ingressName = resource.getMetadata().getName();
        log.info("Reconciling Ingress for resource: {}", resource.getMetadata().getName());

        Ingress ingress = createIngress(resource, ingressName);
        log.debug("Created Ingress spec: {}", ingress);

        Resource<Ingress> ingressResource = kubernetesClient.network().v1().ingresses()
                .inNamespace(resource.getMetadata().getNamespace())
                .withName(ingressName);

        if (ingressResource.get() == null) {
            ingressResource.create(ingress);
            log.info("Created new Ingress: {}", ingressName);
        } else {
            ingressResource.patch(ingress);
            log.info("Updated existing Ingress: {}", ingressName);
        }
    }

    /**
     * Creates a Kubernetes Ingress object based on the custom resource specifications.
     *
     * @param resource The custom resource specifying the Ingress configuration.
     * @param ingressName The name of the Ingress to be created or updated.
     * @return The created Ingress object.
     */
    private static Ingress createIngress(PaymentHubDeployment resource, String ingressName) {
        log.info("Creating Ingress spec for resource: {}", resource.getMetadata().getName());

        PaymentHubDeploymentSpec.Ingress ingressSpec = resource.getSpec().getIngress();
        if (ingressSpec == null) {
            log.warn("ingressEnabled=true but no ingress spec defined for {}, creating empty Ingress", ingressName);
            ingressSpec = new PaymentHubDeploymentSpec.Ingress();
        }

        List<PaymentHubDeploymentSpec.Ingress.TLS> tlsSpecs = ingressSpec.getTls();
        List<IngressTLS> ingressTlsList = tlsSpecs != null
                ? tlsSpecs.stream()
                    .map(tls -> new IngressTLS(tls.getHosts(), tls.getSecretName()))
                    .collect(Collectors.toList())
                : Collections.emptyList();

        List<PaymentHubDeploymentSpec.Ingress.Rule> rawRules =
                ingressSpec.getRules() != null ? ingressSpec.getRules() : Collections.emptyList();
        List<IngressRule> rules = rawRules.stream()
                .map(rule -> new IngressRuleBuilder()
                        .withHost(rule.getHost())
                        .withNewHttp()
                            .addAllToPaths(rule.getPaths().stream().map(customPath ->
                                new HTTPIngressPathBuilder()
                                    .withPath(customPath.getPath())
                                    .withPathType(customPath.getPathType())
                                    .withNewBackend()
                                        .withNewService()
                                            .withName(customPath.getBackend().getService().getName())
                                            .withNewPort()
                                                .withNumber(customPath.getBackend().getService().getPort().getNumber())
                                            .endPort()
                                        .endService()
                                    .endBackend()
                                .build()
                            ).collect(Collectors.toList()))
                        .endHttp()
                        .build()
                ).collect(Collectors.toList());

        // Copy defensively — ingressSpec.getLabels() is the live map on the CR spec
        // and must never be mutated in place.
        Map<String, String> labels = ingressSpec.getLabels();
        labels = labels == null ? new HashMap<>() : new HashMap<>(labels);

        // Add default labels if they are not provided in the CR
        labels.putIfAbsent("app", resource.getMetadata().getName());
        labels.putIfAbsent("app.kubernetes.io/managed-by", "paymenthub-operator");

        return new IngressBuilder()
                .withNewMetadata()
                    .withName(ingressName)
                    .withNamespace(resource.getMetadata().getNamespace())
                    .withLabels(labels)
                    .withAnnotations(ingressSpec.getAnnotations())
                    .withOwnerReferences(OwnerReferenceUtils.createOwnerReferences(resource))
                .endMetadata()
                .withNewSpec()
                    .withIngressClassName(ingressSpec.getClassName())
                    .withTls(ingressTlsList)
                    .withRules(rules)
                .endSpec()
                .build();
    }

}
