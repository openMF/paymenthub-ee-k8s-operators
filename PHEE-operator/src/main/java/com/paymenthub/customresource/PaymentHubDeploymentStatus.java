package com.paymenthub.customresource;

import java.util.Objects;

public class PaymentHubDeploymentStatus {
    private Integer availableReplicas;
    private String errorMessage;
    private String lastAppliedImage;
    private Boolean ready;

    public PaymentHubDeploymentStatus() {
    }

    public PaymentHubDeploymentStatus(Integer availableReplicas, String errorMessage, String lastAppliedImage, Boolean ready) {
        this.availableReplicas = availableReplicas;
        this.errorMessage = errorMessage;
        this.lastAppliedImage = lastAppliedImage;
        this.ready = ready;
    }

    public Integer getAvailableReplicas() {
        return availableReplicas;
    }

    public void setAvailableReplicas(Integer availableReplicas) {
        this.availableReplicas = availableReplicas;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getLastAppliedImage() {
        return lastAppliedImage;
    }

    public void setLastAppliedImage(String lastAppliedImage) {
        this.lastAppliedImage = lastAppliedImage;
    }

    public Boolean isReady() {
        return ready;
    }

    public void setReady(Boolean ready) {
        this.ready = ready;
    }

    @Override
    public String toString() {
        return "PaymentHubDeploymentStatus{" +
                "availableReplicas=" + availableReplicas +
                ", errorMessage='" + errorMessage + '\'' +
                ", lastAppliedImage='" + lastAppliedImage + '\'' +
                ", ready=" + ready +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentHubDeploymentStatus)) return false;
        PaymentHubDeploymentStatus that = (PaymentHubDeploymentStatus) o;
        return Objects.equals(ready, that.ready) &&
               Objects.equals(availableReplicas, that.availableReplicas) &&
               Objects.equals(errorMessage, that.errorMessage) &&
               Objects.equals(lastAppliedImage, that.lastAppliedImage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(availableReplicas, errorMessage, lastAppliedImage, ready);
    }
}
