# PHEE Operator Architecture

This document provides a conceptual overview of the PHEE Operator — what it is, how its components relate, and why key design decisions were made.

## Repo Structure

This repo contains operator **source code only**. Deployment manifests (CRD, CRs, RBAC) live in [mifos-gazelle](https://github.com/openMF/mifos-gazelle).

```
paymenthub-operator/
├── src/
│   ├── main/java/com/paymenthub/
│   │   ├── customresource/
│   │   │   ├── PaymentHubDeployment.java
│   │   │   ├── PaymentHubDeploymentSpec.java
│   │   │   └── PaymentHubDeploymentStatus.java
│   │   ├── utils/
│   │   │   ├── DeletionUtil.java
│   │   │   ├── DeploymentUtils.java
│   │   │   ├── LoggingUtil.java
│   │   │   ├── NetworkingUtils.java
│   │   │   ├── OwnerReferenceUtils.java
│   │   │   ├── RbacUtils.java
│   │   │   ├── ResourceUtils.java
│   │   │   └── StatusUpdateUtil.java
│   │   ├── OperatorMain.java
│   │   └── PaymentHubDeploymentController.java
│   └── test/
│       ├── java/                          ← unit tests (JUnit 5 + Mockito)
│       └── resources/features/            ← BDD scenarios (Cucumber, 11 feature files)
├── build.gradle.kts                       ← Gradle build; Java 21; Jib for image publishing
├── ARCHITECTURE.md
├── DEVELOPER_GUIDE.md
└── README.md
```

## Table of Contents

1. [Introduction](#introduction)
2. [Overview](#overview)
3. [Components](#components)
   - [Custom Resource Definition (CRD)](#custom-resource-definition-crd)
   - [Custom Resource (CR)](#custom-resource-cr)
   - [OperatorMain](#operatormain)
   - [Controller](#controller)
   - [Utility Classes](#utility-classes)
   - [Custom Resource Model Classes](#custom-resource-model-classes)
4. [Deployment](#deployment)
5. [Design Decisions](#design-decisions)


## Introduction

The PHEE Operator is a Kubernetes Operator that manages and automates the lifecycle of Payment Hub EE components within a Kubernetes cluster. It watches `PaymentHubDeployment` custom resources and reconciles the cluster state — creating, updating, and cleaning up Deployments, Services, Ingresses, ConfigMaps, Secrets, and RBAC objects.

The operator is deployed and driven by [mifos-gazelle](https://github.com/openMF/mifos-gazelle). Each Payment Hub EE component (connectors, channels, importer, operations app, Zeebe ops, etc.) has its own `PaymentHubDeployment` CR; the operator reconciles all 19 of them.

## Overview

The operator comprises several key components:

- **Custom Resource Definitions (CRDs):** Define the schema and validation rules for `PaymentHubDeployment` resources in Kubernetes.
- **Custom Resources (CRs):** Represent the desired state of each PHEE component as an instance of the CRD.
- **OperatorMain:** Sets up the Kubernetes client, initializes the operator, registers the controller, and starts the reconciliation loop.
- **Controller:** Handles reconciliation to ensure the cluster's actual state matches the desired configuration.
- **Utility Classes:** Provide focused functions for creating resources, managing configurations, and logging.


## Components

### Custom Resource Definition (CRD)

- **Authoritative location**: `mifos-gazelle/src/deployer/operators/phee/config/crd/ph-ee-CustomResourceDefinition.yaml`
- **API group / version**: `gazelle.mifos.io / v1`

The CRD defines the schema and structure for all `PaymentHubDeployment` custom resources managed by the operator. It specifies the fields in `spec` (desired state) and `status` (observed state), including validation rules.

### Custom Resource (CR)

- **Authoritative location**: `mifos-gazelle/src/deployer/operators/phee/config/cr/` (one file per component, 19 total)

Each CR is an instance of the CRD representing the desired state of one PHEE component. CRs are applied to the cluster by mifos-gazelle during `phee.sh` deployment. Do not duplicate CR files in this repo — keeping a second copy creates sync drift.

### OperatorMain

- **File**: `src/main/java/com/paymenthub/OperatorMain.java`

Entry point for the operator. Initializes the Fabric8 Kubernetes client, creates an `Operator` instance, registers `PaymentHubDeploymentController` as the reconciler, and starts the operator loop.

### Controller

- **File**: `src/main/java/com/paymenthub/PaymentHubDeploymentController.java`

Core of the operator. Implements `Reconciler<PaymentHubDeployment>` and `Cleaner<PaymentHubDeployment>`. Continuously watches for `PaymentHubDeployment` CR changes and reconciles:

- RBAC (ServiceAccount, Role, RoleBinding, ClusterRole, ClusterRoleBinding)
- Secrets and ConfigMaps
- Services and Ingresses
- Deployments (with optional init containers: TLS keystore generation, wait-for-database, wait-for-gateway)

Handles graceful deletion, including cluster-scoped RBAC cleanup that owner references cannot handle automatically.

### Utility Classes

| Class | File | Purpose |
|-------|------|---------|
| `DeletionUtil` | `utils/DeletionUtil.java` | Deletes Deployments, RBAC, Secrets, ConfigMaps, Ingress, Services by owner reference |
| `DeploymentUtils` | `utils/DeploymentUtils.java` | Helper methods: container specs, resource limits, probes, volume mounts |
| `LoggingUtil` | `utils/LoggingUtil.java` | Structured logging with CR name, namespace, and operation for consistent observability |
| `NetworkingUtils` | `utils/NetworkingUtils.java` | Creates, updates, deletes Services and Ingresses |
| `OwnerReferenceUtils` | `utils/OwnerReferenceUtils.java` | Sets owner references for automatic child-resource garbage collection |
| `RbacUtils` | `utils/RbacUtils.java` | Reconciles ServiceAccount, Role, RoleBinding, ClusterRole, ClusterRoleBinding |
| `ResourceUtils` | `utils/ResourceUtils.java` | Reconciles ConfigMaps and Secrets. Both are CR-driven: `spec.configMapData` merges extra literal key/value pairs into the generated ConfigMap, and `spec.secretData` supplies the generated Secret's `stringData` (falling back to a single `database-password` key when a CR doesn't set it) |
| `StatusUpdateUtil` | `utils/StatusUpdateUtil.java` | Updates the CR status subresource (disabled / error / ready with replica count) |

### Custom Resource Model Classes

| Class | File | Purpose |
|-------|------|---------|
| `PaymentHubDeployment` | `customresource/PaymentHubDeployment.java` | Extends `CustomResource`, implements `Namespaced`; annotated with API group, version, and plural name so Fabric8 recognises it |
| `PaymentHubDeploymentSpec` | `customresource/PaymentHubDeploymentSpec.java` | Spec fields (Lombok-generated getters/setters): `enabled`, `image`, `replicas`, `containerPort`, `domain`, `labels`, `resources`, `livenessProbe`, `readinessProbe`, `rbacEnabled`, `secretEnabled`, `configMapEnabled`, `ingressEnabled`, `initContainerEnabled`, `waitForGatewayEnabled`, `tlsKeystoreEnabled`, `volMount` (including optional `volMount.mounts`, a list of `{subPath, mountPath}` pairs), `ingress`, `services`, `environment`, `configMapData`, `secretData` |
| `PaymentHubDeploymentStatus` | `customresource/PaymentHubDeploymentStatus.java` | Status fields: `availableReplicas`, `ready`, `lastAppliedImage`, `errorMessage` |

## Deployment

Deployment is fully managed by mifos-gazelle. See [README.md](README.md) for build and deploy instructions.

The mifos-gazelle `phee.sh` script applies the CRD, RBAC, and operator Deployment, then applies all 19 per-component CRs. The operator starts reconciling immediately once the CRs are applied.

## Design Decisions

- **Language**: Java — strong typing, extensive ecosystem, and natural fit for the Java Operator SDK.
- **Framework**: [Java Operator SDK (JOSDK)](https://javaoperatorsdk.io/) — handles watch loops, event queuing, and retry back-off, letting the controller focus on reconciliation logic.
- **Build**: Gradle. CI builds the image via `./gradlew bootJar` followed by `docker buildx build` against this repo's `Dockerfile`, producing multi-platform (linux/amd64, linux/arm64) images. [Jib](https://github.com/GoogleContainerTools/jib) is also wired into the Gradle build for local/manual image builds (see the operator README's "Building the Operator Image" section) but isn't what CI uses to publish images.
- **CRD structure**: Designed to be extensible — adding a new spec field requires a one-line CRD change, a getter/setter in `PaymentHubDeploymentSpec`, and usage in the relevant util class.
- **Controller logic**: Focused on idempotency and modularity — each utility class owns one resource type, making the reconciliation loop easy to read and test independently.
- **Deployment manifests in mifos-gazelle**: Keeping CRDs and CRs in the deployment tool (rather than this repo) avoids drift between what the operator expects and what gets applied to the cluster.
