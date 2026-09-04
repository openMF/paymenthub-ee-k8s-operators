@unit
Feature: Mock Payment Schema Connector reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled connector-mock-payment creates Deployment and Service
    Given a PaymentHubDeployment CR named "paymenthub-ee-connector-mock-payment-schema" with:
      | image    | docker.io/openmf/paymenthub-ee-connector-mock-payment-schema:mifos-v2.0.0 |
      | replicas | 1                                                                         |
      | enabled  | true                                                                      |
    When the operator reconciles the CR "paymenthub-ee-connector-mock-payment-schema"
    Then a Deployment "paymenthub-ee-connector-mock-payment-schema" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/paymenthub-ee-connector-mock-payment-schema:mifos-v2.0.0"
    And a Service "paymenthub-ee-connector-mock-payment-schema" should exist in namespace "paymenthub"

  Scenario: Disabled connector-mock-payment removes Deployment and Service
    Given a Deployment "paymenthub-ee-connector-mock-payment-schema" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "paymenthub-ee-connector-mock-payment-schema" with enabled false
    When the operator reconciles the CR "paymenthub-ee-connector-mock-payment-schema"
    Then the Deployment "paymenthub-ee-connector-mock-payment-schema" should not exist in namespace "paymenthub"

  Scenario: Connector-mock-payment CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "paymenthub-ee-connector-mock-payment-schema" with enabled true
    When the operator reconciles the CR "paymenthub-ee-connector-mock-payment-schema"
    Then the CR "paymenthub-ee-connector-mock-payment-schema" status.ready should be true

  @integration
  Scenario: Connector-mock-payment deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "paymenthub-ee-connector-mock-payment-schema" with enabled true
    When the operator reconciles the CR "paymenthub-ee-connector-mock-payment-schema"
    Then the Deployment "paymenthub-ee-connector-mock-payment-schema" should reach 1/1 ready within 300 seconds
