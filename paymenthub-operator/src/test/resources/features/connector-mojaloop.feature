@unit
Feature: Mojaloop Connector reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled connector-mojaloop creates Deployment and Service
    Given a PaymentHubDeployment CR named "ph-ee-connector-mojaloop-java" with:
      | image    | docker.io/openmf/ph-ee-connector-mojaloop-java:mifos-v2.0.0 |
      | replicas | 1                                                            |
      | enabled  | true                                                         |
    When the operator reconciles the CR "ph-ee-connector-mojaloop-java"
    Then a Deployment "ph-ee-connector-mojaloop-java" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/ph-ee-connector-mojaloop-java:mifos-v2.0.0"
    And a Service "ph-ee-connector-mojaloop-java" should exist in namespace "paymenthub"

  Scenario: Disabled connector-mojaloop removes Deployment and Service
    Given a Deployment "ph-ee-connector-mojaloop-java" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "ph-ee-connector-mojaloop-java" with enabled false
    When the operator reconciles the CR "ph-ee-connector-mojaloop-java"
    Then the Deployment "ph-ee-connector-mojaloop-java" should not exist in namespace "paymenthub"

  Scenario: Connector-mojaloop CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "ph-ee-connector-mojaloop-java" with enabled true
    When the operator reconciles the CR "ph-ee-connector-mojaloop-java"
    Then the CR "ph-ee-connector-mojaloop-java" status.ready should be true

  @integration
  Scenario: Connector-mojaloop deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "ph-ee-connector-mojaloop-java" with enabled true
    When the operator reconciles the CR "ph-ee-connector-mojaloop-java"
    Then the Deployment "ph-ee-connector-mojaloop-java" should reach 1/1 ready within 300 seconds
