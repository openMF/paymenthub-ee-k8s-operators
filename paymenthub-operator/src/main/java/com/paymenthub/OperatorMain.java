package com.paymenthub;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.javaoperatorsdk.operator.Operator;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import com.paymenthub.customresource.PaymentHubDeployment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OperatorMain {
    private static final Logger log = LoggerFactory.getLogger(OperatorMain.class);

    public static void main(String[] args) throws InterruptedException {
        log.info("Starting the Payment Hub EE Operator!");

        KubernetesClient client = new KubernetesClientBuilder().build();
        // Fail fast on startup: if the CRD is missing the informer will error and the
        // operator exits so Kubernetes can restart the pod rather than running blind.
        Operator operator = new Operator(o -> o
                .withKubernetesClient(client)
                .withStopOnInformerErrorDuringStartup(true));
        log.info("Operator instance created.");

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutdown signal received — stopping operator.");
            operator.stop();
            client.close();
        }));

        Reconciler<PaymentHubDeployment> reconciler = new PaymentHubDeploymentController(client);
        operator.register(reconciler);
        log.info("Reconciler {} registered.", reconciler.getClass().getSimpleName());

        operator.start();
        log.info("Operator started successfully.");

        Thread.currentThread().join();
    }
}
