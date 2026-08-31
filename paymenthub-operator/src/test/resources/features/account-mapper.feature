@unit
Feature: Identity Account Mapper reconciliation

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: Enabled account-mapper creates Deployment and Service
    Given a PaymentHubDeployment CR named "ph-ee-identity-account-mapper" with:
      | image    | docker.io/openmf/ph-ee-identity-account-mapper:mifos-v2.0.0 |
      | replicas | 1                                                            |
      | enabled  | true                                                         |
    When the operator reconciles the CR "ph-ee-identity-account-mapper"
    Then a Deployment "ph-ee-identity-account-mapper" should exist in namespace "paymenthub"
    And the Deployment container image is "docker.io/openmf/ph-ee-identity-account-mapper:mifos-v2.0.0"
    And a Service "ph-ee-identity-account-mapper" should exist in namespace "paymenthub"

  Scenario: Account-mapper with ingress creates Ingress at identity-mapper host
    Given a PaymentHubDeployment CR named "ph-ee-identity-account-mapper" with ingressEnabled true
    And the CR has ingress host "identity-mapper.mifos.gazelle.test"
    When the operator reconciles the CR "ph-ee-identity-account-mapper"
    Then an Ingress "ph-ee-identity-account-mapper" should exist in namespace "paymenthub"
    And the Ingress has host "identity-mapper.mifos.gazelle.test"

  Scenario: Account-mapper Deployment connects to operationsmysql identity_account_mapper DB
    Given a PaymentHubDeployment CR named "ph-ee-identity-account-mapper" with enabled true
    When the operator reconciles the CR "ph-ee-identity-account-mapper"
    Then an env var "SPRING_DATASOURCE_URL" equals "jdbc:mysql://operationsmysql:3306/identity_account_mapper" on Deployment "ph-ee-identity-account-mapper"

  Scenario: Disabled account-mapper removes Deployment and Service
    Given a Deployment "ph-ee-identity-account-mapper" already exists in namespace "paymenthub"
    And a PaymentHubDeployment CR named "ph-ee-identity-account-mapper" with enabled false
    When the operator reconciles the CR "ph-ee-identity-account-mapper"
    Then the Deployment "ph-ee-identity-account-mapper" should not exist in namespace "paymenthub"

  Scenario: Account-mapper CR status is updated to ready after reconciliation
    Given a PaymentHubDeployment CR named "ph-ee-identity-account-mapper" with enabled true
    When the operator reconciles the CR "ph-ee-identity-account-mapper"
    Then the CR "ph-ee-identity-account-mapper" status.ready should be true

  @integration
  Scenario: Account-mapper deployment is schedulable on the cluster
    Given the phee-infra helm chart is deployed
    And a PaymentHubDeployment CR named "ph-ee-identity-account-mapper" with enabled true
    When the operator reconciles the CR "ph-ee-identity-account-mapper"
    Then the Deployment "ph-ee-identity-account-mapper" should reach 1/1 ready within 300 seconds
