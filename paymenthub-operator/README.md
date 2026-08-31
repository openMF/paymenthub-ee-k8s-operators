# PHEE Operator

The PHEE Operator is a Kubernetes operator that manages Payment Hub EE component deployments. It watches `PaymentHubDeployment` custom resources (kind `PaymentHubDeployment`, group `gazelle.mifos.io`, version `v1`) and reconciles the cluster state — creating and updating Deployments, Services, Ingresses, ConfigMaps, Secrets, and RBAC objects for each of the 19 PHEE components.

This repo contains **operator source code only**. It has no README/ARCHITECTURE/DEVELOPER split — this file is the whole story; keeping one file means there's one place to keep in sync instead of three.

> **Path convention used throughout this file:** examples assume [mifos-gazelle](https://github.com/openMF/mifos-gazelle) is cloned to `~/mifos-gazelle` and this repo to `~/paymenthub-ee-k8s-operators` — both directly in your home directory. Adjust the paths if yours live elsewhere.

## Deployment

**This operator is deployed and managed by [mifos-gazelle](https://github.com/openMF/mifos-gazelle).** All deployment artefacts live there, not in this repo:

- `src/deployer/operators/paymenthub/config/crd/ph-ee-CustomResourceDefinition.yaml` — authoritative CRD
- `src/deployer/operators/paymenthub/config/cr/` — one CR file per component (19 total)
- `src/deployer/operators/paymenthub/operator_rbac.yaml` — operator ServiceAccount + RBAC (`ClusterRole`/`ClusterRoleBinding` grant it permission to manage Deployments/Services/Ingresses/ConfigMaps/Secrets/RBAC/`PaymentHubDeployment` statuses cluster-wide — adding a new resource type the operator needs means adding it here, or reconciliation fails with a 403)

Do not duplicate these files here — a second copy creates sync drift.

`src/deployer/paymenthub.sh`'s `deploy_ph_operator()` applies the CRD and RBAC, then generates and applies the operator's own `Deployment` (via `write_operator_deployment_image()`, image from `config/config.ini`'s `PH_OPERATOR_IMAGE` — there's no static Deployment manifest file), then applies all 19 CRs. The operator starts reconciling as soon as they land.

To deploy, run from the mifos-gazelle root:

```bash
cd ~/mifos-gazelle
./run.sh -m deploy -a paymenthub
```

## Local Development

Once PaymentHub is deployed at least once (above), iterate on operator code without building/pushing an image at all, using mifos-gazelle's `localdev.py`:

```bash
cd ~/mifos-gazelle/src/utils/localdev
./localdev.py --setup --component paymenthub-operator   # checkout + patch the live Deployment

cd ~/paymenthub-ee-k8s-operators/paymenthub-operator
./gradlew bootJar
kubectl delete pod -n paymenthub -l app=ph-ee-operator   # pick up the new JAR
kubectl logs -f -n paymenthub -l app=ph-ee-operator

# When done
cd ~/mifos-gazelle/src/utils/localdev
./localdev.py --restore --component paymenthub-operator
```

This patches the operator's Deployment to run `java -jar` off a hostPath-mounted local build — the same k8s-direct mechanism `localdev.py` uses for every other PHEE component. Unlike those, nothing reconciles the operator's own Deployment, so there's no need to scale anything down first. See mifos-gazelle's [docs/LOCALDEV.md](https://github.com/openMF/mifos-gazelle/blob/dev/docs/LOCALDEV.md) for the full workflow.

## Building the Operator Image

For producing a real image (e.g. to test image mode, or ahead of a release) rather than for day-to-day local iteration:

```bash
# Build and push multi-platform image via JIB
./gradlew jib

# Build to local Docker daemon only (for k3s import)
./gradlew jibDockerBuild
docker save openmf/paymenthub-operator:1.0.0 -o paymenthub-operator.tar
sudo k3s ctr images import paymenthub-operator.tar
```

The image name/tag above must match `jib.to.image` and `version` in `build.gradle.kts`. CI does **not** use Jib to publish images — it builds via `docker buildx` against this repo's `Dockerfile`; Jib here is a local/manual convenience only.

Alternatively, `./build-local-image.sh` builds and imports a `paymenthub-operator:local` image the same way, via mifos-gazelle's shared `build-and-import-image.sh` (assumes mifos-gazelle is checked out as a sibling of this repo; override with `GAZELLE_DIR` otherwise).

## Code Structure

| File | Role |
|------|------|
| `OperatorMain.java` | Entry point — builds the Fabric8 client, registers `PaymentHubDeploymentController`, starts the reconcile loop |
| `PaymentHubDeploymentController.java` | Implements `Reconciler`/`Cleaner`; drives RBAC, Secrets, ConfigMaps, Services, Ingresses, and the Deployment (with optional init containers: TLS keystore generation, wait-for-database, wait-for-gateway) per CR; handles cluster-scoped RBAC cleanup that owner references can't |
| `customresource/PaymentHubDeployment.java` | The CR class — `@Group("gazelle.mifos.io") @Version("v1") @Plural("paymenthubdeployments")` |
| `customresource/PaymentHubDeploymentSpec.java` | Spec fields (Lombok-generated getters/setters): `enabled`, `image`, `replicas`, `containerPort`, `domain`, `labels`, `resources`, `livenessProbe`, `readinessProbe`, `rbacEnabled`, `secretEnabled`, `configMapEnabled`, `ingressEnabled`, `initContainerEnabled`, `waitForGatewayEnabled`, `tlsKeystoreEnabled`, `volMount` (incl. optional `volMount.mounts`: `{subPath, mountPath}` list), `ingress`, `services`, `environment`, `configMapData`, `secretData` |
| `customresource/PaymentHubDeploymentStatus.java` | Status fields: `availableReplicas`, `ready`, `lastAppliedImage`, `errorMessage` |
| `utils/RbacUtils.java` | Reconciles ServiceAccount, Role, RoleBinding, ClusterRole, ClusterRoleBinding |
| `utils/ResourceUtils.java` | Reconciles ConfigMaps (base data + `spec.configMapData` merged in) and Secrets (`spec.secretData` via `Secret.stringData`, falling back to a single `database-password` key). Neither special-cases any component by name — all per-component data comes from the CR |
| `utils/NetworkingUtils.java` | `createServices` builds one `Service` per entry in `spec.services`; `createIngress` builds the `Ingress` |
| `utils/DeploymentUtils.java` | Env vars, resource requests/limits, liveness/readiness probes for the container spec |
| `utils/DeletionUtil.java` | Deletes Deployment/RBAC/Secret/ConfigMap/Ingress/Services by name when a CR is disabled or removed |
| `utils/OwnerReferenceUtils.java` | Builds the owner reference every namespace-scoped child resource gets, for automatic GC |
| `utils/StatusUpdateUtil.java` | Writes the CR's status subresource (disabled / error / ready + replica count) |
| `utils/LoggingUtil.java` | Structured logging of CR name/namespace/operation |

## Design Decisions

- **Language**: Java — strong typing, extensive ecosystem, natural fit for the Java Operator SDK.
- **Framework**: [Java Operator SDK](https://javaoperatorsdk.io/) — handles watch loops, event queuing, and retry back-off, letting the controller focus on reconciliation logic.
- **Build**: Gradle. CI builds via `./gradlew bootJar` + `docker buildx build` against this repo's `Dockerfile`. Jib is wired in for local/manual builds only (see above).
- **CRD structure**: additive — adding a spec field is a CRD change + a Lombok field + usage in the relevant util class, not a breaking change to existing CRs.
- **Deployment manifests live in mifos-gazelle, not here**: keeps a single source of truth for what's actually applied to the cluster.

## Extending the Operator

To add a new spec field:

1. Add the field to the CRD in `mifos-gazelle/src/deployer/operators/paymenthub/config/crd/ph-ee-CustomResourceDefinition.yaml`
2. Add the field to `PaymentHubDeploymentSpec.java` (Lombok generates the getter/setter)
3. Use the getter in the relevant util class (`DeploymentUtils`, `NetworkingUtils`, etc.)
4. Add a CR entry for any component that needs the new field

## Domain Names

The operator supports any FQDN — `mifos.gazelle.test` is only a default. Domain is configured in two places per CR:

- `spec.domain` — used for oauth/server URLs in the ConfigMap
- `spec.ingress.rules[].host` — full FQDNs for Ingress rules (set by mifos-gazelle via `GAZELLE_DOMAIN` token substitution)

## Testing

BDD tests using Cucumber 7 + JUnit 5 (`src/test/resources/features/`, one feature file per component) plus a small JUnit suite. Unit tests (`@unit` tag) run against Fabric8's mock Kubernetes server — no live cluster required; each scenario creates a `PaymentHubDeployment` CR, calls the reconciler directly, and asserts the expected Deployment/Service/Ingress/etc. exist with the right configuration.

```bash
./gradlew test              # unit tests (mock server)
./gradlew integrationTest   # integration tests, against a real cluster via $KUBECONFIG
```
