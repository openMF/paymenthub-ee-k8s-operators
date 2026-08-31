@unit
Feature: Channel Connector reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled channel creates Deployment and Service with correct image
    Given a PaymentHubDeployment CR named "ph-ee-connector-channel" with:
      | image    | docker.io/openmf/ph-ee-connector-channel:mifos-v2.0.0 |
      | replicas | 1                                                     |
      | enabled  | true                                                  |
    When the operator reconciles the CR "ph-ee-connector-channel"
    Then a Deployment "ph-ee-connector-channel" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/ph-ee-connector-channel:mifos-v2.0.0"
    And the Deployment has 1 replica
    And a Service "ph-ee-connector-channel" should exist in namespace "paymenthub"

  Scenario: Channel with ingress enabled creates Ingress with correct host
    Given a PaymentHubDeployment CR named "ph-ee-connector-channel" with ingressEnabled true
    And the CR has ingress host "channel.mifos.gazelle.test"
    When the operator reconciles the CR "ph-ee-connector-channel"
    Then an Ingress "ph-ee-connector-channel" should exist in namespace "paymenthub"
    And the Ingress has host "channel.mifos.gazelle.test"

  Scenario: Disabled channel deletes existing Deployment and Service
    Given a Deployment "ph-ee-connector-channel" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "ph-ee-connector-channel" with enabled false
    When the operator reconciles the CR "ph-ee-connector-channel"
    Then the Deployment "ph-ee-connector-channel" should not exist in namespace "paymenthub"
    And the Service "ph-ee-connector-channel" should not exist in namespace "paymenthub"

  Scenario: Channel CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "ph-ee-connector-channel" with enabled true
    When the operator reconciles the CR "ph-ee-connector-channel"
    Then the CR "ph-ee-connector-channel" status.ready should be true

  @integration
  Scenario: Channel deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "ph-ee-connector-channel" with enabled true
    When the operator reconciles the CR "ph-ee-connector-channel"
    Then the Deployment "ph-ee-connector-channel" should reach 1/1 ready within 300 seconds
