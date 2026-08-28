# mifos-operators

Kubernetes operator source code for [Mifos Gazelle](https://github.com/openMF/mifos-gazelle). These operators manage the lifecycle of complex Digital Public Good (DPG) components within a Kubernetes cluster via Custom Resources (CRDs + CRs).

> **Note:** Deployment manifests — CRDs, per-component Custom Resources, RBAC, and the operator Deployment — live in the [mifos-gazelle](https://github.com/openMF/mifos-gazelle) repo under `src/deployer/operators/`. This repo contains only the operator source code and its container image build.

## Operators

- **[PHEE Operator](paymenthub-operator/README.md)** — manages Payment Hub EE deployments. Watches `PaymentHubDeployment` custom resources (`gazelle.mifos.io/v1`) and reconciles Deployments, Services, Ingresses, ConfigMaps, Secrets, and RBAC for 19 PHEE components (connectors, channels, importer, operations app, Zeebe ops, etc.).

## How operators fit into Mifos Gazelle

Mifos Gazelle orchestrates the deployment of Digital Public Goods (currently MifosX, Payment Hub EE, Mojaloop vNext).  For components that are complex enough to warrant Kubernetes-native lifecycle management, Gazelle deploys an operator rather than raw Helm/manifests.

Operator deployment is fully automated — running `./run.sh -m deploy -a paymenthub` from the mifos-gazelle root (no sudo required post-setup) handles CRD registration, RBAC, and the operator Deployment itself. See the README.md file in the Mifos Gazelle repository
