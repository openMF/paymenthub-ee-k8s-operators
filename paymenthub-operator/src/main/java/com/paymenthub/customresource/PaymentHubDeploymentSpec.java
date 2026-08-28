package com.paymenthub.customresource;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentHubDeploymentSpec {
    private Boolean enabled;
    private String domain = "mifos.gazelle.test";
    private Map<String, String> labels;
    private VolMount volMount;
    private Integer replicas;
    private String image;
    private Integer containerPort;
    private Resources resources;
    private Probe livenessProbe;
    private Probe readinessProbe;
    private Boolean rbacEnabled;
    private Boolean secretEnabled;
    private Boolean configMapEnabled;
    private Boolean ingressEnabled;
    private Ingress ingress;
    private List<Service> services;
    private List<EnvironmentVariable> environment;
    private Boolean initContainerEnabled;
    private Boolean waitForGatewayEnabled;
    private Boolean tlsKeystoreEnabled;

    // Extra literal key/value pairs merged into the generated ConfigMap's data,
    // on top of the base configuration.properties entry every component gets.
    private Map<String, String> configMapData;

    // Literal (plaintext) key/value pairs for the generated Secret. The operator
    // writes these via Secret.stringData (server-side base64 encoding), so values
    // here are plaintext, not pre-encoded. Falls back to a single database-password
    // key when a CR doesn't set this.
    private Map<String, String> secretData;

    // Inner classes for nested objects
    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class VolMount {
        private Boolean enabled;
        private String name;

        // When non-empty, mount one VolumeMount per entry from the named volume,
        // each at its own subPath/mountPath, instead of the generic single /config mount.
        private List<SubPathMount> mounts;

        @Getter
        @Setter
        @ToString
        @EqualsAndHashCode
        @NoArgsConstructor
        public static class SubPathMount {
            private String subPath;
            private String mountPath;
        }
    }

    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Resources {
        private ResourceDetails limits;
        private ResourceDetails requests;
    }

    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class ResourceDetails {
        private String cpu;
        private String memory;
    }

    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Probe {
        private String path;
        private Integer port;
        private Integer initialDelaySeconds;
        private Integer periodSeconds;
        private Integer failureThreshold;
        private Integer timeoutSeconds;
    }

    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Ingress {
        private String host;
        private String path;
        private String className;
        private Map<String, String> annotations;
        private List<TLS> tls;
        private List<Rule> rules;
        private Map<String, String> labels;

        @Getter
        @Setter
        @ToString
        @EqualsAndHashCode
        @NoArgsConstructor
        public static class TLS {
            private List<String> hosts;
            private String secretName;
        }

        @Getter
        @Setter
        @ToString
        @EqualsAndHashCode
        @NoArgsConstructor
        public static class Rule {
            private String host;
            private List<Path> paths;
        }

        @Getter
        @Setter
        @ToString
        @EqualsAndHashCode
        @NoArgsConstructor
        public static class Path {
            private String path;
            private String pathType;
            private Backend backend;
        }

        @Getter
        @Setter
        @ToString
        @EqualsAndHashCode
        @NoArgsConstructor
        public static class Backend {
            private Service service;
        }

        @Getter
        @Setter
        @ToString
        @EqualsAndHashCode
        @NoArgsConstructor
        public static class Service {
            private String name;
            private Port port;
        }

        @Getter
        @Setter
        @ToString
        @EqualsAndHashCode
        @NoArgsConstructor
        public static class Port {
            private Integer number;
        }
    }

    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class Service {
        private String name;
        private List<Port> ports;
        private Map<String, String> selector = new HashMap<>();
        private String type;
        private Map<String, String> annotations = new HashMap<>();
        private String sessionAffinity;
        private Map<String, String> labels = new HashMap<>();

        private Service(Builder builder) {
            this.name = builder.name;
            this.ports = builder.ports;
            this.selector = builder.selector;
            this.type = builder.type;
            this.annotations = builder.annotations;
            this.sessionAffinity = builder.sessionAffinity;
            this.labels = builder.labels;
        }

        // Builder class for Service
        @JsonPOJOBuilder(withPrefix = "")
        public static class Builder {
            private String name;
            private List<Port> ports;
            private Map<String, String> selector = new HashMap<>();
            private String type;
            private Map<String, String> annotations = new HashMap<>();
            private String sessionAffinity;
            private Map<String, String> labels = new HashMap<>();

            public Builder withName(String name) {
                this.name = name;
                return this;
            }

            public Builder withPorts(List<Port> ports) {
                this.ports = ports;
                return this;
            }

            public Builder withSelector(Map<String, String> selector) {
                this.selector = selector;
                return this;
            }

            public Builder withType(String type) {
                this.type = type;
                return this;
            }

            public Builder withAnnotations(Map<String, String> annotations) {
                this.annotations = annotations;
                return this;
            }

            public Builder withSessionAffinity(String sessionAffinity) {
                this.sessionAffinity = sessionAffinity;
                return this;
            }

            public Builder withLabels(Map<String, String> labels) {
                this.labels = labels;
                return this;
            }

            public Service build() {
                return new Service(this);
            }
        }

        // Nested class for Port
        @Getter
        @ToString
        @EqualsAndHashCode
        public static class Port {
            private String name;
            private Integer port;
            private Integer targetPort;
            private String protocol;

            // Default constructor required for Jackson
            public Port() {
            }

            // Private constructor to enforce builder usage
            private Port(PortBuilder builder) {
                this.name = builder.name;
                this.port = builder.port;
                this.targetPort = builder.targetPort;
                this.protocol = builder.protocol;
            }

            // Builder class for Port
            @JsonPOJOBuilder(withPrefix = "")
            public static class PortBuilder {
                private String name;
                private Integer port;
                private Integer targetPort;
                private String protocol;

                public PortBuilder withName(String name) {
                    this.name = name;
                    return this;
                }

                public PortBuilder withPort(Integer port) {
                    this.port = port;
                    return this;
                }

                public PortBuilder withTargetPort(Integer targetPort) {
                    this.targetPort = targetPort;
                    return this;
                }

                public PortBuilder withProtocol(String protocol) {
                    this.protocol = protocol;
                    return this;
                }

                public Port build() {
                    return new Port(this);
                }
            }
        }
    }

    @Getter
    @Setter
    @ToString
    @EqualsAndHashCode
    @NoArgsConstructor
    public static class EnvironmentVariable {
        private String name;
        private String value;
        private ValueFrom valueFrom;

        public EnvironmentVariable(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Getter
        @Setter
        @ToString
        @EqualsAndHashCode
        @NoArgsConstructor
        public static class ValueFrom {
            private SecretKeyRef secretKeyRef;

            @Getter
            @Setter
            @ToString
            @EqualsAndHashCode
            @NoArgsConstructor
            public static class SecretKeyRef {
                private String name;
                private String key;
            }
        }
    }
}
