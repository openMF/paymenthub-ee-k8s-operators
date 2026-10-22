@unit
Feature: Running a component from a local build (spec.localDev)

  Background:
    Given the Kubernetes mock server is running
    And the paymenthub namespace exists

  Scenario: With the opt-in on, a local jar replaces the image
    Given the operator runs with LOCALDEV_ENABLED true
    And a PaymentHubDeployment CR named "paymenthub-ee-connector-mojaloop" with enabled true
    And the CR "paymenthub-ee-connector-mojaloop" runs from local jar "/app/build/libs/app.jar" in "/home/dev/paymenthub-ee-connector-mojaloop"
    When the operator reconciles the CR "paymenthub-ee-connector-mojaloop"
    Then the Deployment "paymenthub-ee-connector-mojaloop" runs "java -jar /app/build/libs/app.jar" on image "eclipse-temurin:21"
    And the Deployment "paymenthub-ee-connector-mojaloop" mounts host path "/home/dev/paymenthub-ee-connector-mojaloop" at "/app"
    And an env var "SPRING_PROFILES_ACTIVE" equals "bb" on Deployment "paymenthub-ee-connector-mojaloop"

  Scenario: The local jar survives a later reconcile
    Given the operator runs with LOCALDEV_ENABLED true
    And a PaymentHubDeployment CR named "paymenthub-ee-connector-mojaloop" with enabled true
    And the CR "paymenthub-ee-connector-mojaloop" runs from local jar "/app/build/libs/app.jar" in "/home/dev/paymenthub-ee-connector-mojaloop"
    When the operator reconciles the CR "paymenthub-ee-connector-mojaloop"
    And the operator reconciles the CR "paymenthub-ee-connector-mojaloop"
    Then the Deployment "paymenthub-ee-connector-mojaloop" runs "java -jar /app/build/libs/app.jar" on image "eclipse-temurin:21"

  Scenario: Turning local development off puts the image back
    Given the operator runs with LOCALDEV_ENABLED true
    And a PaymentHubDeployment CR named "paymenthub-ee-connector-mojaloop" with enabled true
    And the CR "paymenthub-ee-connector-mojaloop" runs from local jar "/app/build/libs/app.jar" in "/home/dev/paymenthub-ee-connector-mojaloop"
    When the operator reconciles the CR "paymenthub-ee-connector-mojaloop"
    And the CR "paymenthub-ee-connector-mojaloop" has local development turned off
    And the operator reconciles the CR "paymenthub-ee-connector-mojaloop"
    Then the Deployment "paymenthub-ee-connector-mojaloop" runs its image with no local mount

  Scenario: Without the opt-in, spec.localDev is ignored
    Given the operator runs with LOCALDEV_ENABLED false
    And a PaymentHubDeployment CR named "paymenthub-ee-connector-mojaloop" with enabled true
    And the CR "paymenthub-ee-connector-mojaloop" runs from local jar "/app/build/libs/app.jar" in "/home/dev/paymenthub-ee-connector-mojaloop"
    When the operator reconciles the CR "paymenthub-ee-connector-mojaloop"
    Then the Deployment "paymenthub-ee-connector-mojaloop" runs its image with no local mount

  Scenario: Without a jar, the directory is mounted over the image (static web apps)
    Given the operator runs with LOCALDEV_ENABLED true
    And a PaymentHubDeployment CR named "paymenthub-ee-operationsui-react" with enabled true
    And the CR "paymenthub-ee-operationsui-react" mounts local directory "/home/dev/paymenthub-ee-operationsui-react/dist" at "/usr/share/nginx/html"
    When the operator reconciles the CR "paymenthub-ee-operationsui-react"
    Then the Deployment container image is "docker.io/openmf/paymenthub-ee-operationsui-react:mifos-v2.0.0"
    And the Deployment "paymenthub-ee-operationsui-react" mounts host path "/home/dev/paymenthub-ee-operationsui-react/dist" at "/usr/share/nginx/html"
