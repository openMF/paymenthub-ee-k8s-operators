# PHEE Operator

The PHEE Operator is a Kubernetes operator that manages Payment Hub EE component deployments. It watches `PaymentHubDeployment` custom resources and reconciles the cluster state — creating and updating Deployments, Services, Ingresses, ConfigMaps, Secrets, and RBAC objects.

## Deployment

**This operator is deployed and managed by [mifos-gazelle](https://github.com/openMF/mifos-gazelle).**

All deployment artefacts (CRD, per-component Custom Resources, RBAC, operator Deployment) live in:

```
mifos-gazelle/src/deployer/operators/phee/
├── config/
│   ├── crd/ph-ee-CustomResourceDefinition.yaml   ← authoritative CRD
│   └── cr/                                        ← one CR file per component (19 total)
└── operator_rbac.yaml                             ← operator ServiceAccount + RBAC
```

The `phee.sh` deployer script builds the operator Deployment manifest dynamically (local JAR or published image mode) and applies all manifests. Do not duplicate these files here — keeping a second copy creates sync drift.

To deploy PHEE, run from the mifos-gazelle root:

```bash
sudo ./run.sh -u $USER -m deploy -a phee
```

## Building the Operator Image

```bash
# Build and push multi-platform image via JIB
./gradlew jib

# Build to local Docker daemon only (for k3s import)
./gradlew jibDockerBuild
docker save ph-ee-operator:latest -o ph-ee-operator.tar
sudo k3s ctr images import ph-ee-operator.tar
```

## Architecture

- [Architecture Overview](ARCHITECTURE.md) — component overview and design decisions
- [Developer Guide](DEVELOPER_GUIDE.md) — in-depth file-by-file breakdown

## Extending the Operator

To add a new spec field:

1. Add the field to the CRD in `mifos-gazelle/src/deployer/operators/phee/config/crd/ph-ee-CustomResourceDefinition.yaml`
2. Add the field + getter/setter to `PaymentHubDeploymentSpec.java`
3. Use the getter in the relevant util class (`DeploymentUtils`, `NetworkingUtils`, etc.)
4. Add a CR entry for any component that needs the new field

## Domain Names

The operator supports any FQDN — `mifos.gazelle.test` is only a default. Domain is configured in two places per CR:

- `spec.domain` — used for oauth/server URLs in the ConfigMap
- `spec.ingress.rules[].host` — full FQDNs for Ingress rules (set by mifos-gazelle via `GAZELLE_DOMAIN` token substitution)
