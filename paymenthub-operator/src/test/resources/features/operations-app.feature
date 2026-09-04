@unit
Feature: Operations App reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled operations-app creates Deployment and Service
    Given a PaymentHubDeployment CR named "paymenthub-ee-bff" with:
      | image    | docker.io/openmf/paymenthub-ee-bff:mifos-v2.0.0 |
      | replicas | 1                                               |
      | enabled  | true                                            |
    When the operator reconciles the CR "paymenthub-ee-bff"
    Then a Deployment "paymenthub-ee-bff" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/paymenthub-ee-bff:mifos-v2.0.0"
    And a Service "paymenthub-ee-bff" should exist in namespace "paymenthub"

  Scenario: Operations-app with ingress creates Ingress at ops-bk host
    Given a PaymentHubDeployment CR named "paymenthub-ee-bff" with ingressEnabled true
    And the CR has ingress host "ops-bk.mifos.gazelle.test"
    When the operator reconciles the CR "paymenthub-ee-bff"
    Then an Ingress "paymenthub-ee-bff" should exist in namespace "paymenthub"
    And the Ingress has host "ops-bk.mifos.gazelle.test"

  Scenario: Operations-app init container waits for operationsmysql
    Given a PaymentHubDeployment CR named "paymenthub-ee-bff" with:
      | enabled | true |
    When the operator reconciles the CR "paymenthub-ee-bff"
    Then an init container exists on Deployment "paymenthub-ee-bff"

  Scenario: Disabled operations-app removes Deployment and Service
    Given a Deployment "paymenthub-ee-bff" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "paymenthub-ee-bff" with enabled false
    When the operator reconciles the CR "paymenthub-ee-bff"
    Then the Deployment "paymenthub-ee-bff" should not exist in namespace "paymenthub"
    And the Service "paymenthub-ee-bff" should not exist in namespace "paymenthub"

  Scenario: Operations-app CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "paymenthub-ee-bff" with enabled true
    When the operator reconciles the CR "paymenthub-ee-bff"
    Then the CR "paymenthub-ee-bff" status.ready should be true

  @integration
  Scenario: Operations-app deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "paymenthub-ee-bff" with enabled true
    When the operator reconciles the CR "paymenthub-ee-bff"
    Then the Deployment "paymenthub-ee-bff" should reach 1/1 ready within 300 seconds
