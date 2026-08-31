@unit
Feature: Bulk Connector reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled connector-bulk creates Deployment and Service
    Given a PaymentHubDeployment CR named "ph-ee-connector-bulk" with:
      | image    | docker.io/openmf/ph-ee-connector-bulk:mifos-v2.0.0 |
      | replicas | 1                                                   |
      | enabled  | true                                                |
    When the operator reconciles the CR "ph-ee-connector-bulk"
    Then a Deployment "ph-ee-connector-bulk" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/ph-ee-connector-bulk:mifos-v2.0.0"
    And a Service "ph-ee-connector-bulk" should exist in namespace "paymenthub"

  Scenario: Connector-bulk with ingress creates Ingress at connector host
    Given a PaymentHubDeployment CR named "ph-ee-connector-bulk" with ingressEnabled true
    And the CR has ingress host "connector.mifos.gazelle.test"
    When the operator reconciles the CR "ph-ee-connector-bulk"
    Then an Ingress "ph-ee-connector-bulk" should exist in namespace "paymenthub"
    And the Ingress has host "connector.mifos.gazelle.test"

  Scenario: Connector-bulk Deployment has MinIO S3 endpoint configured
    Given a PaymentHubDeployment CR named "ph-ee-connector-bulk" with enabled true
    When the operator reconciles the CR "ph-ee-connector-bulk"
    Then an env var "CLOUD_AWS_S3BASEURL" equals "http://minio:9000" on Deployment "ph-ee-connector-bulk"

  Scenario: Disabled connector-bulk removes Deployment and Service
    Given a Deployment "ph-ee-connector-bulk" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "ph-ee-connector-bulk" with enabled false
    When the operator reconciles the CR "ph-ee-connector-bulk"
    Then the Deployment "ph-ee-connector-bulk" should not exist in namespace "paymenthub"

  Scenario: Connector-bulk CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "ph-ee-connector-bulk" with enabled true
    When the operator reconciles the CR "ph-ee-connector-bulk"
    Then the CR "ph-ee-connector-bulk" status.ready should be true

  @integration
  Scenario: Connector-bulk deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "ph-ee-connector-bulk" with enabled true
    When the operator reconciles the CR "ph-ee-connector-bulk"
    Then the Deployment "ph-ee-connector-bulk" should reach 1/1 ready within 300 seconds
