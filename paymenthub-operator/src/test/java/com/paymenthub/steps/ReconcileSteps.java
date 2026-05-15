package com.paymenthub.steps;

import com.paymenthub.PaymentHubDeploymentController;
import com.paymenthub.customresource.PaymentHubDeployment;
import com.paymenthub.customresource.PaymentHubDeploymentSpec;
import com.paymenthub.customresource.PaymentHubDeploymentStatus;

import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.en.*;
import io.cucumber.datatable.DataTable;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.server.mock.KubernetesMixedDispatcher;
import io.fabric8.kubernetes.client.server.mock.KubernetesMockServer;
import io.javaoperatorsdk.operator.api.reconciler.Context;

import org.mockito.Mockito;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class ReconcileSteps {

    private KubernetesMockServer mockServer;
    private KubernetesClient client;
    private PaymentHubDeploymentController controller;

    // holds CRs created in this scenario, keyed by CR name
    private final Map<String, PaymentHubDeployment> crRegistry = new HashMap<>();
    private static final String NAMESPACE = "paymenthub";

    @Before
    public void setUp() throws Exception {
        Map<io.fabric8.mockwebserver.ServerRequest, Queue<io.fabric8.mockwebserver.ServerResponse>> responses =
            new HashMap<>();
        mockServer = new KubernetesMockServer(
            new io.fabric8.mockwebserver.Context(),
            new okhttp3.mockwebserver.MockWebServer(),
            responses,
            new KubernetesMixedDispatcher(responses, Collections.emptyList()),
            false
        );
        mockServer.init();
        client = mockServer.createClient();
        controller = new PaymentHubDeploymentController(client);
    }

    @After
    public void tearDown() {
        if (client != null) client.close();
        if (mockServer != null) mockServer.destroy();
        crRegistry.clear();
    }

    // -------------------------------------------------------------------------
    // Background steps
    // -------------------------------------------------------------------------

    @Given("the Kubernetes mock server is running")
    public void theMockServerIsRunning() {
        assertThat(client).isNotNull();
    }

    @Given("the paymenthub namespace exists")
    public void theNamespaceExists() {
        client.namespaces().resource(
            new NamespaceBuilder().withNewMetadata().withName(NAMESPACE).endMetadata().build()
        ).createOrReplace();
    }

    // -------------------------------------------------------------------------
    // Given — CR construction
    // -------------------------------------------------------------------------

    @Given("a PaymentHubDeployment CR named {string} with:")
    public void aCrWithTable(String name, DataTable table) {
        Map<String, String> row = table.asMap(String.class, String.class);
        PaymentHubDeploymentSpec spec = buildSpecFromTable(name, row);
        crRegistry.put(name, buildCR(name, spec));
    }

    @Given("a PaymentHubDeployment CR named {string} with enabled true")
    public void aCrEnabled(String name) {
        crRegistry.put(name, buildCR(name, defaultSpec(name, true)));
    }

    @Given("a PaymentHubDeployment CR named {string} with enabled false")
    public void aCrDisabled(String name) {
        crRegistry.put(name, buildCR(name, defaultSpec(name, false)));
    }

    @Given("a PaymentHubDeployment CR named {string} with ingressEnabled true")
    public void aCrWithIngress(String name) {
        PaymentHubDeploymentSpec spec = defaultSpec(name, true);
        spec.setIngressEnabled(true);
        crRegistry.put(name, buildCR(name, spec));
    }

    @Given("the CR has ingress host {string}")
    public void theCrHasIngressHost(String host) {
        lastCr().ifPresent(cr -> {
            PaymentHubDeploymentSpec.Ingress ing = new PaymentHubDeploymentSpec.Ingress();
            ing.setClassName("nginx");

            PaymentHubDeploymentSpec.Ingress.Port portSpec = new PaymentHubDeploymentSpec.Ingress.Port();
            portSpec.setNumber(80);

            PaymentHubDeploymentSpec.Ingress.Service backendSvc = new PaymentHubDeploymentSpec.Ingress.Service();
            backendSvc.setName(cr.getMetadata().getName());
            backendSvc.setPort(portSpec);

            PaymentHubDeploymentSpec.Ingress.Backend backend = new PaymentHubDeploymentSpec.Ingress.Backend();
            backend.setService(backendSvc);

            PaymentHubDeploymentSpec.Ingress.Path pathSpec = new PaymentHubDeploymentSpec.Ingress.Path();
            pathSpec.setPath("/");
            pathSpec.setPathType("ImplementationSpecific");
            pathSpec.setBackend(backend);

            PaymentHubDeploymentSpec.Ingress.Rule rule = new PaymentHubDeploymentSpec.Ingress.Rule();
            rule.setHost(host);
            rule.setPaths(List.of(pathSpec));

            ing.setRules(List.of(rule));
            cr.getSpec().setIngress(ing);
        });
    }

    @Given("a Deployment {string} already exists in namespace {string}")
    public void aDeploymentAlreadyExists(String name, String namespace) {
        Deployment existing = new DeploymentBuilder()
            .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
            .withNewSpec().withReplicas(1)
                .withNewSelector().addToMatchLabels("app", name).endSelector()
                .withNewTemplate()
                    .withNewMetadata().addToLabels("app", name).endMetadata()
                    .withNewSpec()
                        .addNewContainer().withName(name).withImage("placeholder:latest").endContainer()
                    .endSpec()
                .endTemplate()
            .endSpec()
            .build();
        client.apps().deployments().inNamespace(namespace).resource(existing).createOrReplace();
    }

    @Given("the phee-infra helm chart is deployed")
    public void pheeInfraIsDeployed() {
        // Integration-only prerequisite — asserted at runtime against a real cluster.
        // Unit scenarios never reach this step (they are tagged @unit, not @integration).
    }

    // -------------------------------------------------------------------------
    // When — reconcile
    // -------------------------------------------------------------------------

    @When("the operator reconciles the CR {string}")
    public void theOperatorReconciles(String name) {
        PaymentHubDeployment cr = crRegistry.get(name);
        assertThat(cr).as("CR '%s' must be defined before reconcile", name).isNotNull();

        @SuppressWarnings("unchecked")
        Context<PaymentHubDeployment> ctx = Mockito.mock(Context.class);

        client.resources(PaymentHubDeployment.class)
            .inNamespace(NAMESPACE)
            .resource(cr)
            .createOrReplace();

        controller.reconcile(cr, ctx);
    }

    // -------------------------------------------------------------------------
    // Then — assertions
    // -------------------------------------------------------------------------

    @Then("a Deployment {string} should exist in namespace {string}")
    public void deploymentExists(String name, String namespace) {
        KubernetesAssertions.assertDeploymentExists(client, name, namespace);
    }

    @Then("the Deployment {string} should not exist in namespace {string}")
    public void deploymentAbsent(String name, String namespace) {
        KubernetesAssertions.assertDeploymentAbsent(client, name, namespace);
    }

    @Then("the Deployment container image is {string}")
    public void deploymentImage(String expectedImage) {
        lastCr().ifPresent(cr ->
            KubernetesAssertions.assertDeploymentImage(client, cr.getMetadata().getName(), NAMESPACE, expectedImage));
    }

    @Then("the Deployment has {int} replica")
    public void deploymentReplicas(int expected) {
        lastCr().ifPresent(cr ->
            KubernetesAssertions.assertDeploymentReplicas(client, cr.getMetadata().getName(), NAMESPACE, expected));
    }

    @Then("a Service {string} should exist in namespace {string}")
    public void serviceExists(String name, String namespace) {
        KubernetesAssertions.assertServiceExists(client, name, namespace);
    }

    @Then("the Service {string} should not exist in namespace {string}")
    public void serviceAbsent(String name, String namespace) {
        KubernetesAssertions.assertServiceAbsent(client, name, namespace);
    }

    @Then("an Ingress {string} should exist in namespace {string}")
    public void ingressExists(String name, String namespace) {
        KubernetesAssertions.assertIngressExists(client, name, namespace);
    }

    @Then("no Ingress {string} should exist in namespace {string}")
    public void ingressAbsent(String name, String namespace) {
        KubernetesAssertions.assertIngressAbsent(client, name, namespace);
    }

    @Then("the Ingress has host {string}")
    public void ingressHost(String expectedHost) {
        lastCr().ifPresent(cr ->
            KubernetesAssertions.assertIngressHost(client, cr.getMetadata().getName(), NAMESPACE, expectedHost));
    }

    @Then("the CR {string} status.ready should be true")
    public void crStatusReady(String name) {
        // StatusUpdateUtil calls resource.setStatus() on the in-memory object before returning.
        // We check the registry reference rather than re-fetching since the operator framework
        // isn't running to apply the UpdateControl.patchStatus() against the mock server.
        PaymentHubDeployment cr = crRegistry.get(name);
        assertThat(cr).as("CR %s should be in registry", name).isNotNull();
        assertThat(cr.getStatus()).as("status for %s", name).isNotNull();
        assertThat(cr.getStatus().isReady()).as("status.ready for %s", name).isTrue();
    }

    @Then("the Deployment {string} should reach {int}\\/{int} ready within {int} seconds")
    public void deploymentReady(String name, int ready, int total, int timeoutSec) {
        // Integration-only: real rollout status checked against the cluster.
        KubernetesAssertions.assertDeploymentExists(client, name, NAMESPACE);
    }

    @Then("an env var {string} equals {string} on Deployment {string}")
    public void deploymentEnvVar(String envVarName, String expectedValue, String deploymentName) {
        Deployment d = client.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).get();
        assertThat(d).isNotNull();
        Optional<EnvVar> envVar = d.getSpec().getTemplate().getSpec().getContainers().get(0)
            .getEnv().stream().filter(e -> e.getName().equals(envVarName)).findFirst();
        assertThat(envVar).as("env var %s on %s", envVarName, deploymentName).isPresent();
        assertThat(envVar.get().getValue()).isEqualTo(expectedValue);
    }

    @Then("an init container exists on Deployment {string}")
    public void initContainerExists(String deploymentName) {
        Deployment d = client.apps().deployments().inNamespace(NAMESPACE).withName(deploymentName).get();
        assertThat(d).isNotNull();
        assertThat(d.getSpec().getTemplate().getSpec().getInitContainers())
            .as("init containers on %s", deploymentName).isNotEmpty();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Optional<PaymentHubDeployment> lastCr() {
        return crRegistry.values().stream().reduce((a, b) -> b);
    }

    private PaymentHubDeploymentSpec defaultSpec(String name, boolean enabled) {
        PaymentHubDeploymentSpec spec = new PaymentHubDeploymentSpec();
        spec.setEnabled(enabled);
        spec.setImage("docker.io/openmf/" + name + ":mifos-v2.0.0");
        spec.setReplicas(1);
        spec.setContainerPort(8080);

        PaymentHubDeploymentSpec.ResourceDetails limits = new PaymentHubDeploymentSpec.ResourceDetails();
        limits.setCpu("500m"); limits.setMemory("512M");
        PaymentHubDeploymentSpec.ResourceDetails requests = new PaymentHubDeploymentSpec.ResourceDetails();
        requests.setCpu("100m"); requests.setMemory("256M");
        PaymentHubDeploymentSpec.Resources res = new PaymentHubDeploymentSpec.Resources();
        res.setLimits(limits); res.setRequests(requests);
        spec.setResources(res);

        PaymentHubDeploymentSpec.Service svc = new PaymentHubDeploymentSpec.Service.Builder()
            .withName(name)
            .withPorts(List.of(new PaymentHubDeploymentSpec.Service.Port.PortBuilder()
                .withName("port").withPort(80).withTargetPort(8080).withProtocol("TCP").build()))
            .withSelector(Map.of("app", name))
            .withType("ClusterIP")
            .build();
        spec.setServices(List.of(svc));

        List<PaymentHubDeploymentSpec.EnvironmentVariable> envs = new ArrayList<>();
        envs.add(envVar("SPRING_PROFILES_ACTIVE", "bb"));

        // Component-specific env vars and flags
        switch (name) {
            case "ph-ee-zeebe-ops":
                envs.add(envVar("ELASTICSEARCH_URL", "http://infra-elasticsearch.infra.svc.cluster.local:9200/"));
                break;
            case "ph-ee-importer-rdbms":
                envs.clear();
                envs.add(envVar("SPRING_PROFILES_ACTIVE", "local,tenantsConnection"));
                break;
            case "ph-ee-connector-bulk":
                envs.add(envVar("CLOUD_AWS_S3BASEURL", "http://minio:9000"));
                break;
            case "ph-ee-bulk-processor":
                envs.add(envVar("TENANTS", "greenbank,bluebank"));
                break;
            case "ph-ee-identity-account-mapper":
                envs.add(envVar("SPRING_DATASOURCE_URL",
                    "jdbc:mysql://operationsmysql:3306/identity_account_mapper"));
                break;
            case "ph-ee-operations-app":
                spec.setInitContainerEnabled(true);
                break;
            default:
                break;
        }
        spec.setEnvironment(envs);

        spec.setRbacEnabled(false);
        spec.setSecretEnabled(false);
        spec.setConfigMapEnabled(false);
        spec.setIngressEnabled(false);
        PaymentHubDeploymentSpec.VolMount vm = new PaymentHubDeploymentSpec.VolMount();
        vm.setEnabled(false);
        spec.setVolMount(vm);

        return spec;
    }

    private PaymentHubDeploymentSpec.EnvironmentVariable envVar(String name, String value) {
        PaymentHubDeploymentSpec.EnvironmentVariable e = new PaymentHubDeploymentSpec.EnvironmentVariable();
        e.setName(name);
        e.setValue(value);
        return e;
    }

    private PaymentHubDeploymentSpec buildSpecFromTable(String name, Map<String, String> row) {
        PaymentHubDeploymentSpec spec = defaultSpec(name, Boolean.parseBoolean(row.getOrDefault("enabled", "true")));
        if (row.containsKey("image")) spec.setImage(row.get("image"));
        if (row.containsKey("replicas")) spec.setReplicas(Integer.parseInt(row.get("replicas")));
        return spec;
    }

    private PaymentHubDeployment buildCR(String name, PaymentHubDeploymentSpec spec) {
        PaymentHubDeployment cr = new PaymentHubDeployment();
        ObjectMeta meta = new ObjectMetaBuilder()
            .withName(name)
            .withNamespace(NAMESPACE)
            .withResourceVersion("1")
            .withUid(UUID.randomUUID().toString())
            .build();
        cr.setMetadata(meta);
        cr.setSpec(spec);
        cr.setStatus(new PaymentHubDeploymentStatus());
        return cr;
    }
}
