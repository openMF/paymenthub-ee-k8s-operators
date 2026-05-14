package com.paymenthub.utils;

import com.paymenthub.customresource.PaymentHubDeployment;
import com.paymenthub.customresource.PaymentHubDeploymentStatus;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatusUpdateUtil {

    private static final Logger log = LoggerFactory.getLogger(StatusUpdateUtil.class);

    public static UpdateControl<PaymentHubDeployment> updateStatus(
            PaymentHubDeployment resource, Integer replicas, String image,
            boolean isReady, String errorMessage) {

        PaymentHubDeploymentStatus status = new PaymentHubDeploymentStatus();
        status.setAvailableReplicas(replicas);
        status.setLastAppliedImage(image);
        status.setReady(isReady);
        status.setErrorMessage(errorMessage);
        resource.setStatus(status);

        log.info("Updating status for {} — replicas: {}, image: {}, ready: {}, error: {}",
                resource.getMetadata().getName(), replicas, image, isReady, errorMessage);

        return UpdateControl.patchStatus(resource);
    }

    public static UpdateControl<PaymentHubDeployment> updateErrorStatus(
            PaymentHubDeployment resource, String image, Exception e) {
        return updateStatus(resource, 0, image, false, "Error during reconciliation: " + e.getMessage());
    }

    public static UpdateControl<PaymentHubDeployment> updateDisabledStatus(PaymentHubDeployment resource) {
        PaymentHubDeploymentStatus status = new PaymentHubDeploymentStatus();
        status.setAvailableReplicas(0);
        status.setLastAppliedImage(resource.getSpec().getImage());
        status.setReady(false);
        status.setErrorMessage("Resource is disabled and not created.");
        resource.setStatus(status);

        log.info("Resource {} is disabled — setting status to not-ready.", resource.getMetadata().getName());

        return UpdateControl.patchStatus(resource);
    }
}
