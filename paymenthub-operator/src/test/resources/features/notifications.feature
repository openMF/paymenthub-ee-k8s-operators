@unit
Feature: Notifications Connector reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled notifications creates Deployment and Service
    Given a PaymentHubDeployment CR named "paymenthub-ee-notifications" with:
      | image    | docker.io/openmf/paymenthub-ee-notifications:v1.4.0-gazelle-1.1.0 |
      | replicas | 1                                                                 |
      | enabled  | true                                                              |
    When the operator reconciles the CR "paymenthub-ee-notifications"
    Then a Deployment "paymenthub-ee-notifications" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/paymenthub-ee-notifications:v1.4.0-gazelle-1.1.0"
    And a Service "paymenthub-ee-notifications" should exist in namespace "paymenthub"

  Scenario: Disabled notifications removes Deployment and Service
    Given a Deployment "paymenthub-ee-notifications" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "paymenthub-ee-notifications" with enabled false
    When the operator reconciles the CR "paymenthub-ee-notifications"
    Then the Deployment "paymenthub-ee-notifications" should not exist in namespace "paymenthub"
    And the Service "paymenthub-ee-notifications" should not exist in namespace "paymenthub"

  Scenario: Notifications CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "paymenthub-ee-notifications" with enabled true
    When the operator reconciles the CR "paymenthub-ee-notifications"
    Then the CR "paymenthub-ee-notifications" status.ready should be true

  @integration
  Scenario: Notifications deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "paymenthub-ee-notifications" with enabled true
    When the operator reconciles the CR "paymenthub-ee-notifications"
    Then the Deployment "paymenthub-ee-notifications" should reach 1/1 ready within 300 seconds
