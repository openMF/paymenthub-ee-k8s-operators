@unit
Feature: Mojaloop Connector reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled connector-mojaloop creates Deployment and Service
    Given a PaymentHubDeployment CR named "paymenthub-ee-connector-mojaloop" with:
      | image    | docker.io/openmf/paymenthub-ee-connector-mojaloop:mifos-v2.0.0 |
      | replicas | 1                                                              |
      | enabled  | true                                                           |
    When the operator reconciles the CR "paymenthub-ee-connector-mojaloop"
    Then a Deployment "paymenthub-ee-connector-mojaloop" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/paymenthub-ee-connector-mojaloop:mifos-v2.0.0"
    And a Service "paymenthub-ee-connector-mojaloop" should exist in namespace "paymenthub"

  Scenario: Disabled connector-mojaloop removes Deployment and Service
    Given a Deployment "paymenthub-ee-connector-mojaloop" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "paymenthub-ee-connector-mojaloop" with enabled false
    When the operator reconciles the CR "paymenthub-ee-connector-mojaloop"
    Then the Deployment "paymenthub-ee-connector-mojaloop" should not exist in namespace "paymenthub"

  Scenario: Connector-mojaloop CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "paymenthub-ee-connector-mojaloop" with enabled true
    When the operator reconciles the CR "paymenthub-ee-connector-mojaloop"
    Then the CR "paymenthub-ee-connector-mojaloop" status.ready should be true

  @integration
  Scenario: Connector-mojaloop deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "paymenthub-ee-connector-mojaloop" with enabled true
    When the operator reconciles the CR "paymenthub-ee-connector-mojaloop"
    Then the Deployment "paymenthub-ee-connector-mojaloop" should reach 1/1 ready within 300 seconds
