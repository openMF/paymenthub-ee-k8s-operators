@unit
Feature: RDBMS Importer reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled importer-rdbms creates Deployment and Service
    Given a PaymentHubDeployment CR named "ph-ee-importer-rdbms" with:
      | image    | docker.io/openmf/ph-ee-importer-rdbms:mifos-v2.0.0 |
      | replicas | 1                                                   |
      | enabled  | true                                                |
    When the operator reconciles the CR "ph-ee-importer-rdbms"
    Then a Deployment "ph-ee-importer-rdbms" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/ph-ee-importer-rdbms:mifos-v2.0.0"

  Scenario: Importer-rdbms Deployment has tenantsConnection Spring profile set
    Given a PaymentHubDeployment CR named "ph-ee-importer-rdbms" with enabled true
    When the operator reconciles the CR "ph-ee-importer-rdbms"
    Then an env var "SPRING_PROFILES_ACTIVE" equals "local,tenantsConnection" on Deployment "ph-ee-importer-rdbms"

  Scenario: Disabled importer-rdbms removes Deployment
    Given a Deployment "ph-ee-importer-rdbms" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "ph-ee-importer-rdbms" with enabled false
    When the operator reconciles the CR "ph-ee-importer-rdbms"
    Then the Deployment "ph-ee-importer-rdbms" should not exist in namespace "paymenthub"

  Scenario: Importer-rdbms CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "ph-ee-importer-rdbms" with enabled true
    When the operator reconciles the CR "ph-ee-importer-rdbms"
    Then the CR "ph-ee-importer-rdbms" status.ready should be true

  @integration
  Scenario: Importer-rdbms deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "ph-ee-importer-rdbms" with enabled true
    When the operator reconciles the CR "ph-ee-importer-rdbms"
    Then the Deployment "ph-ee-importer-rdbms" should reach 1/1 ready within 300 seconds
