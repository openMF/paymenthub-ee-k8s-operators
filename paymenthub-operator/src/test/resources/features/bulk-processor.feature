@unit
Feature: Bulk Processor reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled bulk-processor creates Deployment and Service
    Given a PaymentHubDeployment CR named "ph-ee-bulk-processor" with:
      | image    | docker.io/openmf/ph-ee-bulk-processor:mifos-v2.0.0 |
      | replicas | 1                                                   |
      | enabled  | true                                                |
    When the operator reconciles the CR "ph-ee-bulk-processor"
    Then a Deployment "ph-ee-bulk-processor" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/ph-ee-bulk-processor:mifos-v2.0.0"
    And a Service "ph-ee-bulk-processor" should exist in namespace "paymenthub"

  Scenario: Bulk-processor with ingress creates Ingress at bulk-processor host
    Given a PaymentHubDeployment CR named "ph-ee-bulk-processor" with ingressEnabled true
    And the CR has ingress host "bulk-processor.mifos.gazelle.test"
    When the operator reconciles the CR "ph-ee-bulk-processor"
    Then an Ingress "ph-ee-bulk-processor" should exist in namespace "paymenthub"
    And the Ingress has host "bulk-processor.mifos.gazelle.test"

  Scenario: Bulk-processor Deployment has TENANTS env var set
    Given a PaymentHubDeployment CR named "ph-ee-bulk-processor" with enabled true
    When the operator reconciles the CR "ph-ee-bulk-processor"
    Then an env var "TENANTS" equals "greenbank,bluebank" on Deployment "ph-ee-bulk-processor"

  Scenario: Disabled bulk-processor removes Deployment and Service
    Given a Deployment "ph-ee-bulk-processor" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "ph-ee-bulk-processor" with enabled false
    When the operator reconciles the CR "ph-ee-bulk-processor"
    Then the Deployment "ph-ee-bulk-processor" should not exist in namespace "paymenthub"

  Scenario: Bulk-processor CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "ph-ee-bulk-processor" with enabled true
    When the operator reconciles the CR "ph-ee-bulk-processor"
    Then the CR "ph-ee-bulk-processor" status.ready should be true

  @integration
  Scenario: Bulk-processor deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "ph-ee-bulk-processor" with enabled true
    When the operator reconciles the CR "ph-ee-bulk-processor"
    Then the Deployment "ph-ee-bulk-processor" should reach 1/1 ready within 300 seconds
