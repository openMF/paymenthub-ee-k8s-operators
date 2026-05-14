package com.paymenthub.steps;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.KubernetesClient;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesAssertions {

    public static void assertDeploymentExists(KubernetesClient client, String name, String namespace) {
        Deployment d = client.apps().deployments().inNamespace(namespace).withName(name).get();
        assertThat(d).as("Deployment %s/%s", namespace, name).isNotNull();
    }

    public static void assertDeploymentAbsent(KubernetesClient client, String name, String namespace) {
        Deployment d = client.apps().deployments().inNamespace(namespace).withName(name).get();
        assertThat(d).as("Deployment %s/%s should be absent", namespace, name).isNull();
    }

    public static void assertDeploymentImage(KubernetesClient client, String name, String namespace, String expectedImage) {
        Deployment d = client.apps().deployments().inNamespace(namespace).withName(name).get();
        assertThat(d).isNotNull();
        String actualImage = d.getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
        assertThat(actualImage).as("container image for %s", name).isEqualTo(expectedImage);
    }

    public static void assertDeploymentReplicas(KubernetesClient client, String name, String namespace, int expectedReplicas) {
        Deployment d = client.apps().deployments().inNamespace(namespace).withName(name).get();
        assertThat(d).isNotNull();
        assertThat(d.getSpec().getReplicas()).as("replicas for %s", name).isEqualTo(expectedReplicas);
    }

    public static void assertServiceExists(KubernetesClient client, String name, String namespace) {
        Service svc = client.services().inNamespace(namespace).withName(name).get();
        assertThat(svc).as("Service %s/%s", namespace, name).isNotNull();
    }

    public static void assertServiceAbsent(KubernetesClient client, String name, String namespace) {
        Service svc = client.services().inNamespace(namespace).withName(name).get();
        assertThat(svc).as("Service %s/%s should be absent", namespace, name).isNull();
    }

    public static void assertIngressExists(KubernetesClient client, String name, String namespace) {
        Ingress ing = client.network().v1().ingresses().inNamespace(namespace).withName(name).get();
        assertThat(ing).as("Ingress %s/%s", namespace, name).isNotNull();
    }

    public static void assertIngressAbsent(KubernetesClient client, String name, String namespace) {
        Ingress ing = client.network().v1().ingresses().inNamespace(namespace).withName(name).get();
        assertThat(ing).as("Ingress %s/%s should be absent", namespace, name).isNull();
    }

    public static void assertIngressHost(KubernetesClient client, String name, String namespace, String expectedHost) {
        Ingress ing = client.network().v1().ingresses().inNamespace(namespace).withName(name).get();
        assertThat(ing).isNotNull();
        String actualHost = ing.getSpec().getRules().get(0).getHost();
        assertThat(actualHost).as("ingress host for %s", name).isEqualTo(expectedHost);
    }
}
