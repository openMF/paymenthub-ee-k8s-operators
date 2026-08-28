package com.paymenthub.customresource;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHubDeploymentStatus {
    private Integer availableReplicas;
    private String errorMessage;
    private String lastAppliedImage;
    private Boolean ready;
}
