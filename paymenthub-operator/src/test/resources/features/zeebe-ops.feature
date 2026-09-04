@unit
Feature: Zeebe-Ops reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled zeebe-ops creates Deployment and Service
    Given a PaymentHubDeployment CR named "paymenthub-ee-zeebe-ops" with:
      | image    | docker.io/openmf/paymenthub-ee-zeebe-ops:mifos-v2.0.0 |
      | replicas | 1                                                     |
      | enabled  | true                                                  |
    When the operator reconciles the CR "paymenthub-ee-zeebe-ops"
    Then a Deployment "paymenthub-ee-zeebe-ops" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/paymenthub-ee-zeebe-ops:mifos-v2.0.0"
    And a Service "paymenthub-ee-zeebe-ops" should exist in namespace "paymenthub"

  Scenario: Zeebe-ops with ingress creates Ingress at zeebeops host
    Given a PaymentHubDeployment CR named "paymenthub-ee-zeebe-ops" with ingressEnabled true
    And the CR has ingress host "zeebeops.mifos.gazelle.test"
    When the operator reconciles the CR "paymenthub-ee-zeebe-ops"
    Then an Ingress "paymenthub-ee-zeebe-ops" should exist in namespace "paymenthub"
    And the Ingress has host "zeebeops.mifos.gazelle.test"

  Scenario: Zeebe-ops Deployment uses shared infra Elasticsearch endpoint
    Given a PaymentHubDeployment CR named "paymenthub-ee-zeebe-ops" with enabled true
    When the operator reconciles the CR "paymenthub-ee-zeebe-ops"
    Then an env var "ELASTICSEARCH_URL" equals "http://infra-elasticsearch.infra.svc.cluster.local:9200/" on Deployment "paymenthub-ee-zeebe-ops"

  Scenario: Disabled zeebe-ops removes Deployment and Service
    Given a Deployment "paymenthub-ee-zeebe-ops" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "paymenthub-ee-zeebe-ops" with enabled false
    When the operator reconciles the CR "paymenthub-ee-zeebe-ops"
    Then the Deployment "paymenthub-ee-zeebe-ops" should not exist in namespace "paymenthub"

  Scenario: Zeebe-ops CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "paymenthub-ee-zeebe-ops" with enabled true
    When the operator reconciles the CR "paymenthub-ee-zeebe-ops"
    Then the CR "paymenthub-ee-zeebe-ops" status.ready should be true

  @integration
  Scenario: Zeebe-ops deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "paymenthub-ee-zeebe-ops" with enabled true
    When the operator reconciles the CR "paymenthub-ee-zeebe-ops"
    Then the Deployment "paymenthub-ee-zeebe-ops" should reach 1/1 ready within 300 seconds
