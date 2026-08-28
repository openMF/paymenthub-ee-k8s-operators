package com.paymenthub.utils;

import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Probe;
import io.fabric8.kubernetes.api.model.ProbeBuilder;
import io.fabric8.kubernetes.api.model.HTTPGetActionBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirements;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;

import com.paymenthub.customresource.PaymentHubDeployment;
import com.paymenthub.customresource.PaymentHubDeploymentSpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DeploymentUtils {

    private static final Logger log = LoggerFactory.getLogger(DeploymentUtils.class);

    private DeploymentUtils() {
    }

    /**
     * Creates a list of environment variables for the deployment based on the custom resource specifications.
     *
     * @param resource The custom resource containing environment variable definitions.
     * @return List of EnvVar objects to be added to the deployment container.
     */
    public static List<EnvVar> createEnvironmentVariables(PaymentHubDeployment resource) {
        List<PaymentHubDeploymentSpec.EnvironmentVariable> envVars = resource.getSpec().getEnvironment();
        if (envVars == null) return Collections.emptyList();
        return envVars.stream()
            .map(env -> {
                EnvVarBuilder envVarBuilder = new EnvVarBuilder().withName(env.getName());

                // Handle direct value
                if (env.getValue() != null) {
                    envVarBuilder.withValue(env.getValue());
                }
                // Handle value from secret
                else if (env.getValueFrom() != null && env.getValueFrom().getSecretKeyRef() != null) {
                    envVarBuilder.withValueFrom(new EnvVarSourceBuilder()
                        .withSecretKeyRef(new SecretKeySelectorBuilder()
                            .withName(env.getValueFrom().getSecretKeyRef().getName())
                            .withKey(env.getValueFrom().getSecretKeyRef().getKey())
                            .build())
                        .build());
                }
                // Log a warning if no value or valueFrom is defined
                else {
                    log.warn("Environment variable {} has no value or valueFrom defined.", env.getName());
                }

                return envVarBuilder.build();
            })
            .collect(Collectors.toList());
    }

    /**
     * Creates the resource requirements for the deployment based on the custom resource specifications.
     *
     * @param resource The custom resource containing CPU and memory specifications.
     * @return ResourceRequirements object to be added to the deployment container.
     */
    public static ResourceRequirements createResourceRequirements(PaymentHubDeployment resource) {
        PaymentHubDeploymentSpec.Resources res = resource.getSpec().getResources();
        if (res == null) return new ResourceRequirementsBuilder().build();
        ResourceRequirementsBuilder builder = new ResourceRequirementsBuilder();
        if (res.getLimits() != null) {
            builder.addToLimits("cpu",    new Quantity(res.getLimits().getCpu()));
            builder.addToLimits("memory", new Quantity(res.getLimits().getMemory()));
        }
        if (res.getRequests() != null) {
            builder.addToRequests("cpu",    new Quantity(res.getRequests().getCpu()));
            builder.addToRequests("memory", new Quantity(res.getRequests().getMemory()));
        }
        return builder.build();
    }

    /**
     * Creates a Probe for the deployment based on the custom resource specifications.
     *
     * @param resource The custom resource containing probe specifications.
     * @param probeType The type of probe to create ("liveness" or "readiness").
     * @return The created Probe object, or null if the probe type is not specified in the custom resource.
     */
    public static Probe createProbe(PaymentHubDeployment resource, String probeType) {
        PaymentHubDeploymentSpec.Probe probeSpec = null;

        if ("liveness".equals(probeType)) {
            probeSpec = resource.getSpec().getLivenessProbe();
        } else if ("readiness".equals(probeType)) {
            probeSpec = resource.getSpec().getReadinessProbe();
        }

        if (probeSpec == null) {
            return null;
        }

        return new ProbeBuilder()
            .withHttpGet(new HTTPGetActionBuilder()
                .withPath(probeSpec.getPath())
                .withPort(new IntOrString(probeSpec.getPort()))
                .build())
            .withInitialDelaySeconds(probeSpec.getInitialDelaySeconds())
            .withPeriodSeconds(probeSpec.getPeriodSeconds())
            .withFailureThreshold(probeSpec.getFailureThreshold())
            .withTimeoutSeconds(probeSpec.getTimeoutSeconds())
            .build();
    }

}
