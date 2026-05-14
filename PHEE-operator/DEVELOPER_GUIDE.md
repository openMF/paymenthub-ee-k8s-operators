# Developer Guide

## Table of Contents

### How the Operator Works
1. [Key Components](#key-components)
   - [Custom Resource Definition (CRD) and Custom Resource (CR)](#custom-resource-definition-crd-and-custom-resource-cr)
   - [Controller File](#controller-file)
   - [Kind and Group](#kind-and-group)

### Explanation of Files
1. [Deployment files](#deployment-files)
   - [ph-ee-CustomResourceDefinition.yaml](#ph-ee-customresourcedefinitionyaml)
   - [operator_deployment_manifests.yaml](#operator_deployment_manifestsyaml)
2. [Custom Resource Files](#custom-resource-files)
   - [PaymentHubDeployment.java](#paymenthubdeploymentjava)
   - [PaymentHubDeploymentSpec.java](#paymenthubdeploymentspecjava)
   - [PaymentHubDeploymentStatus.java](#paymenthubdeploymentstatusjava)
3. [SRC Files](#src-files)
   - [OperatorMain.java](#operatormainjava)
   - [PaymentHubDeploymentController.java](#paymenthubdeploymentcontrollerjava)
   - [Utility Classes](#utility-classes)
     - [DeletionUtil.java](#deletionutiljava)
     - [DeploymentUtils.java](#deploymentutilsjava)
     - [LoggingUtil.java](#loggingutiljava)
     - [NetworkingUtils.java](#networkingutilsjava)
     - [OwnerReferenceUtils.java](#ownerreferenceutilsjava)
     - [RbacUtils.java](#rbacutilsjava)
     - [ResourceUtils.java](#resourceutilsjava)
     - [StatusUpdateUtil.java](#statusupdateutiljava)
4. [Testing](#testing)

---

# How the Operator Works

To start making changes to the PHEE Operator, it's crucial to understand several key components that define the architecture of the operator and how they interact. See also [ARCHITECTURE.md](ARCHITECTURE.md) for a higher-level overview and design decisions.

## Key Components

### Custom Resource Definition (CRD) and Custom Resource (CR)

- **Custom Resource Definition (CRD):**
  The CRD is a schema that defines the structure and validation rules for custom resources. It acts as a blueprint specifying how custom resources should be formatted and what fields they should include.

- **Custom Resource (CR):**
  The CR is an instance of the CRD, representing a specific configuration of resources. Think of the CRD as a template or switchboard and the CR as the plug that fits into this switchboard. The CR must adhere to the schema defined by the CRD to ensure proper functionality. Essentially, the CR defines the desired state of resources that the operator should manage.

### Controller File

- **Purpose:**
  The controller watches for changes to custom resources and ensures that the cluster's state matches the desired state specified by the CR. It uses the values defined in the CR to create, update, or delete resources as needed.

- **Function:**
  The controller continuously monitors custom resources and triggers reconciliation processes to align the cluster's actual state with the desired state defined in the CR. If there are any changes in the CR, the controller invokes reconciliation methods to update the cluster accordingly.

### Kind and Group

- **Kind:**
  The `kind` defines the type of resource, and it plays a crucial role in linking the CRD, CR, and controller. The CRD defines a kind, which must be used in the CR to establish a connection between the CR and the CRD. The controller uses this kind to identify and manage the custom resource.

- **Group:**
  The `group` categorizes resource types within different API versions. When the controller interacts with a CR, it checks the group and version specified in the CRD to ensure compatibility and perform appropriate API operations. For this operator the group is `gazelle.mifos.io` and the version is `v1`.


# Explanation of Files

## Deployment files

> **Note:** These files live in [mifos-gazelle](https://github.com/openMF/mifos-gazelle) under `src/deployer/operators/phee/`, not in this repo. The descriptions below explain their structure and purpose for developers who need to understand or modify them. Do not duplicate them here — keeping a second copy creates sync drift.

### ph-ee-CustomResourceDefinition.yaml

Our CRD for the operator contains all the fields that our controller file might need to maintain the desired state of the cluster. It defines the structure and validation rules for the custom resources (CR), ensuring that the custom resources adhere to the specified format and contain all necessary information for the operator to function correctly.

#### Metadata

**Metadata** contains essential information about the CRD, such as its `name`, which identifies the CRD within the Kubernetes API. This `name` is formatted as `<plural>.<group>`, where `plural` is the plural form of the resource name and `group` specifies the API group. The `metadata` section helps Kubernetes identify and manage the CRD.

#### Spec

**Spec** defines the specifications and behavior of the custom resource. This includes:
- **Group**: The API group under which the CRD is categorized (`gazelle.mifos.io`).
- **Names**: Specifies the `kind`, `listKind`, `plural`, `singular`, and optional `shortNames` for the custom resource.
- **Scope**: Indicates whether the CRD is `Namespaced` or `Cluster-wide`.
- **Versions**: Defines the versions of the CRD, including whether they are `served` and used for `storage`. It also specifies `subresources` like `status` and the `schema` for validation.

#### Schema (openAPIV3Schema)

**Schema (openAPIV3Schema)** outlines the structure and validation rules for the custom resource's specification. It includes the `spec` and its fields, providing a detailed structure for how the custom resources are defined and validated. This block is crucial for ensuring that the custom resources conform to the specified format.

- **Spec** fields:
  - `enabled`
  - `image`
  - `replicas`
  - `containerPort`
  - `domain`
  - `labels`
  - `volMount`
  - `environment`
  - `resources`
  - `livenessProbe`
  - `readinessProbe`
  - `ingress`
  - `services`
  - `initContainerEnabled`
  - `waitForGatewayEnabled`
  - `tlsKeystoreEnabled`
  - `rbacEnabled`
  - `secretEnabled`
  - `configMapEnabled`
  - `ingressEnabled`

#### Status

**Status** provides information about the state of the custom resource. It includes fields such as `availableReplicas`, `errorMessage`, `lastAppliedImage`, and `ready`. This section is used to track the current state and health of the resource, making it easier to monitor and manage its lifecycle.

### operator_deployment_manifests.yaml

This YAML file defines several Kubernetes resources essential for deploying and managing the PHEE Operator. It starts with a `ServiceAccount`, which is used by the operator to interact with the Kubernetes API. The `Deployment` specifies how the operator should be deployed, including the Docker image to use, resource requests and limits, environment variables, and the service account to associate with it. The `ClusterRole` and `ClusterRoleBinding` provide the operator with the necessary permissions to access and manage various Kubernetes resources across the cluster. The `Role` and `RoleBinding` are used to grant specific permissions within the `default` namespace, ensuring the operator can manage resources like custom resources, their statuses, and associated roles. Overall, this file configures the operator's runtime environment, access controls, and permissions, ensuring it operates correctly and securely within the Kubernetes cluster. Two very important configurations to notice in this file are the image name and the `apigroups` in `ClusterRole`.

## Custom Resource Files

### PaymentHubDeployment.java

This Java file defines the custom resource for `PaymentHubDeployment` in the Kubernetes ecosystem using the Fabric8 Kubernetes client. It extends the `CustomResource` class, which is part of the Fabric8 library, and implements the `Namespaced` interface to indicate that this custom resource is scoped to a namespace. The class is annotated with `@Version`, `@Group`, and `@Plural` to specify the API version, API group, and plural name of the custom resource, respectively. This setup allows the Kubernetes API to recognize and manage the `PaymentHubDeployment` resource, including its specification and status, as defined by the `PaymentHubDeploymentSpec` and `PaymentHubDeploymentStatus` classes.

### PaymentHubDeploymentSpec.java

The `PaymentHubDeploymentSpec.java` file defines the specification for the `PaymentHubDeployment` custom resource in Kubernetes. It includes fields that detail the configuration and operational parameters of the custom resource, such as `enabled`, `volMount`, `replicas`, `image`, and `containerPort`. This class serves as the blueprint for how the custom resource should be structured and what information it should contain. It provides getters and setters for each field, ensuring that the specification can be easily managed and accessed. The significance of this file lies in its role in specifying the desired state and configuration for the custom resource, which the Kubernetes controller will use to manage and reconcile the resource's state within the cluster.

### PaymentHubDeploymentStatus.java

The `PaymentHubDeploymentStatus.java` file represents the status of a `PaymentHubDeployment` custom resource in Kubernetes. It encapsulates information about the current state of the deployment, including the number of `availableReplicas`, any `errorMessage`, the `lastAppliedImage`, and whether the deployment is `ready`. This class provides getter and setter methods to access and update these fields, allowing the status of the deployment to be tracked and modified. The file also includes `toString()`, `equals()`, and `hashCode()` functions that facilitate object comparison and provide a string representation of the status, useful for logging and debugging.

## SRC Files

### OperatorMain.java

The `OperatorMain.java` file serves as the entry point for the PHEE Operator, initializing the Kubernetes client and registering the custom resource controller with the operator framework. It starts by setting up the Fabric8 Kubernetes client, which is used to interact with the Kubernetes API. The main method then registers the `PaymentHubDeploymentController` with the operator framework, associating it with the `PaymentHubDeployment` custom resource. This setup ensures that the controller is notified of any changes to the custom resource and can perform the necessary reconciliation actions.

### PaymentHubDeploymentController.java

The `PaymentHubDeploymentController.java` file is the core of the PHEE Operator, responsible for watching the `PaymentHubDeployment` custom resource and reconciling its state within the Kubernetes cluster. The controller is registered with the operator framework in `OperatorMain.java`, which ensures that it is notified of any changes to the custom resource. The controller's main task is to reconcile the desired state specified in the custom resource with the actual state of the Kubernetes resources. It does this by creating, updating, or deleting resources such as Deployments, Services, Ingresses, and RBAC configurations based on the custom resource's specifications. The controller uses various utility classes to perform these actions, ensuring that all aspects of the custom resource are managed effectively.

## Utility Classes

### DeletionUtil.java

The `DeletionUtil.java` file is a utility class designed for managing the deletion of Kubernetes resources associated with a `PaymentHubDeployment` custom resource. It provides methods to delete Deployments, RBAC-related resources (ServiceAccounts, Roles, RoleBindings, ClusterRoles, and ClusterRoleBindings), Secrets, ConfigMaps, Ingress, and Services. Each method is tailored to delete a specific type of resource based on the owner reference set by the custom resource, ensuring that resources created by the custom resource are properly cleaned up when the custom resource is deleted. Note that cluster-scoped RBAC resources (ClusterRole, ClusterRoleBinding) do not inherit namespace-scoped owner references and are therefore explicitly deleted by this class.

### DeploymentUtils.java

The `DeploymentUtils.java` file is a utility class that provides helper methods for constructing Kubernetes `Deployment` resources for a `PaymentHubDeployment` custom resource. It includes helper methods for setting up container specifications, resource requests and limits, liveness and readiness probes, and volume mounts. This class is essential for ensuring that the custom resource is properly deployed and managed within the Kubernetes cluster.

### LoggingUtil.java

The `LoggingUtil.java` file is a utility class designed to facilitate consistent and structured logging within the PHEE Operator. It provides methods for generating standard logging messages that include key details such as the custom resource name, namespace, and operation being performed. By centralizing logging logic, this utility class reduces code duplication and improves observability and debuggability across the operator.

### NetworkingUtils.java

The `NetworkingUtils.java` file is a utility class that provides methods for managing Kubernetes networking resources — specifically `Service` and `Ingress` resources — associated with the `PaymentHubDeployment` custom resource. It includes methods to create, update, or delete these resources based on the custom resource's specifications. The `createService` method sets up a `Service` that exposes the custom resource's pods on a specified port, while the `createIngress` method configures an `Ingress` resource to manage external access to the service.

### OwnerReferenceUtils.java

The `OwnerReferenceUtils.java` file is a utility class that provides methods for setting up and managing owner references in Kubernetes resources. Owner references establish a parent-child relationship between resources, ensuring that when a parent resource is deleted, the associated namespace-scoped child resources are also deleted automatically. This prevents orphaned resources within the Kubernetes cluster.

### RbacUtils.java

The `RbacUtils.java` file is a utility class that provides methods for managing Kubernetes RBAC resources associated with the `PaymentHubDeployment` custom resource. It includes methods to create, update, or delete `ServiceAccount`, `Role`, `RoleBinding`, `ClusterRole`, and `ClusterRoleBinding` resources. The `createServiceAccount` method sets up a `ServiceAccount` that can be used by the custom resource's pods to interact with the Kubernetes API, while `createRole` and `createRoleBinding` establish the necessary permissions for the custom resource to manage its associated resources.

### ResourceUtils.java

The `ResourceUtils.java` file is a utility class that provides methods for managing `ConfigMaps`, `Secrets`, and `PersistentVolumeClaims` (PVCs) associated with the `PaymentHubDeployment` custom resource. The `createConfigMap` method sets up a `ConfigMap` that can store configuration data for the custom resource, the `createSecret` method handles sensitive data such as passwords and API keys (base64-encoded), and the `createPvc` method sets up a `PersistentVolumeClaim` to manage storage requirements.

### StatusUpdateUtil.java

The `StatusUpdateUtil.java` file is a utility class that provides methods for updating the status subresource of the `PaymentHubDeployment` custom resource in Kubernetes. The status subresource is used to track the current state of the custom resource, including fields like `availableReplicas`, `errorMessage`, `lastAppliedImage`, and `ready`. This class keeps the custom resource's status in sync with the actual state of the resources in the cluster.

---

## Testing

The operator includes BDD integration tests using Cucumber 7 + JUnit 5. Feature files live in `src/test/resources/features/` and cover reconciliation of each PHEE component:

| Feature file | Component tested |
|---|---|
| `account-mapper.feature` | Account Mapper |
| `bulk-processor.feature` | Bulk Processor |
| `channel.feature` | Channel |
| `connector-bulk.feature` | Connector Bulk |
| `connector-gsma.feature` | Connector GSMA |
| `connector-mock-payment.feature` | Connector Mock Payment |
| `connector-mojaloop.feature` | Connector Mojaloop |
| `importer-rdbms.feature` | RDBMS Importer |
| `notifications.feature` | Notifications |
| `operations-app.feature` | Operations App |
| `zeebe-ops.feature` | Zeebe Operations |

Run all tests:

```bash
./gradlew test
```

Unit tests use Mockito and the Fabric8 mock Kubernetes server — no live cluster required. Each scenario creates a `PaymentHubDeployment` CR, calls the reconciler, and asserts that the expected Kubernetes resources (Deployment, Service, Ingress, etc.) exist with the correct configuration.
