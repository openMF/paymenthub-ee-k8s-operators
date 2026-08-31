# PHEE Operator

The PHEE Operator is a Kubernetes operator that manages Payment Hub EE component deployments. It watches `PaymentHubDeployment` custom resources and reconciles the cluster state — creating and updating Deployments, Services, Ingresses, ConfigMaps, Secrets, and RBAC objects.

## Deployment

**This operator is deployed and managed by [mifos-gazelle](https://github.com/openMF/mifos-gazelle).**

All deployment artefacts (CRD, per-component Custom Resources, RBAC, operator Deployment) live in:

```
mifos-gazelle/src/deployer/operators/paymenthub/
├── config/
│   ├── crd/ph-ee-CustomResourceDefinition.yaml   ← authoritative CRD
│   └── cr/                                        ← one CR file per component (19 total)
└── operator_rbac.yaml                             ← operator ServiceAccount + RBAC
```

The `paymenthub.sh` deployer script generates the operator Deployment manifest (published image only) and applies all manifests. Do not duplicate these files here — keeping a second copy creates sync drift.

To deploy PHEE, run from the mifos-gazelle root:

```bash
./run.sh -m deploy -a paymenthub
```

## Local Development

Once PaymentHub is deployed at least once (above), iterate on operator code without building/pushing an image at all, using mifos-gazelle's `localdev.py`:

```bash
cd ~/mifos-gazelle/src/utils/localdev
./localdev.py --setup --component paymenthub-operator   # checkout + patch the live Deployment

cd <this repo>/paymenthub-operator
./gradlew bootJar
kubectl delete pod -n paymenthub -l app=ph-ee-operator   # pick up the new JAR
kubectl logs -f -n paymenthub -l app=ph-ee-operator

# When done
cd ~/mifos-gazelle/src/utils/localdev
./localdev.py --restore --component paymenthub-operator
```

This patches the operator's Deployment to run `java -jar` off a hostPath-mounted local build — the same k8s-direct mechanism `localdev.py` uses for every other PHEE component. Unlike those, nothing reconciles the operator's own Deployment, so there's no need to scale anything down first. See mifos-gazelle's `docs/LOCALDEV.md` for the full workflow.

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

The image name/tag above must match `jib.to.image` and `version` in `build.gradle.kts`. Note CI does not use Jib to publish images — it builds via `docker buildx` against this repo's `Dockerfile`; Jib here is a local/manual convenience only.

## Architecture

- [Architecture Overview](ARCHITECTURE.md) — component overview and design decisions
- [Developer Guide](DEVELOPER_GUIDE.md) — in-depth file-by-file breakdown

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
