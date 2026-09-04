@unit
Feature: GSMA Connector reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled connector-gsma creates Deployment and Service
    Given a PaymentHubDeployment CR named "paymenthub-ee-connector-mm-gsma" with:
      | image    | docker.io/openmf/paymenthub-ee-connector-mm-gsma:v1.3.0-gazelle-1.1.0 |
      | replicas | 1                                                                     |
      | enabled  | true                                                                  |
    When the operator reconciles the CR "paymenthub-ee-connector-mm-gsma"
    Then a Deployment "paymenthub-ee-connector-mm-gsma" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/paymenthub-ee-connector-mm-gsma:v1.3.0-gazelle-1.1.0"
    And a Service "paymenthub-ee-connector-mm-gsma" should exist in namespace "paymenthub"

  Scenario: Connector-gsma with ingressEnabled true creates an Ingress
    Given a PaymentHubDeployment CR named "paymenthub-ee-connector-mm-gsma" with ingressEnabled true
    And the CR has ingress host "gsma.mifos.gazelle.test"
    When the operator reconciles the CR "paymenthub-ee-connector-mm-gsma"
    Then an Ingress "paymenthub-ee-connector-mm-gsma" should exist in namespace "paymenthub"
    And the Ingress has host "gsma.mifos.gazelle.test"

  Scenario: Disabled connector-gsma removes Deployment and Service
    Given a Deployment "paymenthub-ee-connector-mm-gsma" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "paymenthub-ee-connector-mm-gsma" with enabled false
    When the operator reconciles the CR "paymenthub-ee-connector-mm-gsma"
    Then the Deployment "paymenthub-ee-connector-mm-gsma" should not exist in namespace "paymenthub"

  Scenario: Connector-gsma CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "paymenthub-ee-connector-mm-gsma" with enabled true
    When the operator reconciles the CR "paymenthub-ee-connector-mm-gsma"
    Then the CR "paymenthub-ee-connector-mm-gsma" status.ready should be true

  @integration
  Scenario: Connector-gsma deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "paymenthub-ee-connector-mm-gsma" with enabled true
    When the operator reconciles the CR "paymenthub-ee-connector-mm-gsma"
    Then the Deployment "paymenthub-ee-connector-mm-gsma" should reach 1/1 ready within 300 seconds
