/*
 * Copyright 2024 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.keycloak.quarkus.runtime.configuration.mappers;

import org.keycloak.common.Profile;
import org.keycloak.config.TracingOptions;
import org.keycloak.quarkus.runtime.cli.PropertyException;
import org.keycloak.quarkus.runtime.configuration.Configuration;
import org.keycloak.utils.StringUtil;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import static org.keycloak.config.TracingOptions.TRACING_COMPRESSION;
import static org.keycloak.config.TracingOptions.TRACING_ENABLED;
import static org.keycloak.config.TracingOptions.TRACING_ENDPOINT;
import static org.keycloak.config.TracingOptions.TRACING_INFINISPAN_ENABLED;
import static org.keycloak.config.TracingOptions.TRACING_JDBC_ENABLED;
import static org.keycloak.config.TracingOptions.TRACING_PROTOCOL;
import static org.keycloak.config.TracingOptions.TRACING_RESOURCE_ATTRIBUTES;
import static org.keycloak.config.TracingOptions.TRACING_SAMPLER_RATIO;
import static org.keycloak.config.TracingOptions.TRACING_SAMPLER_TYPE;
import static org.keycloak.config.TracingOptions.TRACING_SERVICE_NAME;
import static org.keycloak.quarkus.runtime.configuration.mappers.PropertyMapper.fromOption;

public class TracingPropertyMappers {
    private static final String OTEL_FEATURE_ENABLED_MSG = "'opentelemetry' feature is enabled";
    private static final String TRACING_ENABLED_MSG = "Tracing is enabled";

    private TracingPropertyMappers() {
    }

    public static PropertyMapper<?>[] getMappers() {
        return new PropertyMapper[]{
                fromOption(TRACING_ENABLED)
                        .isEnabled(TracingPropertyMappers::isFeatureEnabled, OTEL_FEATURE_ENABLED_MSG)
                        .to("quarkus.otel.enabled") // enable/disable whole OTel, tracing is enabled by default
                        .build(),
                fromOption(TRACING_ENDPOINT)
                        .isEnabled(TracingPropertyMappers::isTracingEnabled, TRACING_ENABLED_MSG)
                        .to("quarkus.otel.exporter.otlp.traces.endpoint")
                        .paramLabel("url")
                        .validator(TracingPropertyMappers::validateEndpoint)
                        .build(),
                fromOption(TRACING_SERVICE_NAME)
                        .isEnabled(TracingPropertyMappers::isTracingEnabled, TRACING_ENABLED_MSG)
                        .to("quarkus.otel.service.name")
                        .paramLabel("name")
                        .build(),
                fromOption(TRACING_RESOURCE_ATTRIBUTES)
                        .isEnabled(TracingPropertyMappers::isTracingEnabled, TRACING_ENABLED_MSG)
                        .to("quarkus.otel.resource.attributes")
                        .paramLabel("attributes")
                        .build(),
                fromOption(TRACING_PROTOCOL)
                        .isEnabled(TracingPropertyMappers::isTracingEnabled, TRACING_ENABLED_MSG)
                        .to("quarkus.otel.exporter.otlp.traces.protocol")
                        .paramLabel("protocol")
                        .build(),
                fromOption(TRACING_SAMPLER_TYPE)
                        .isEnabled(TracingPropertyMappers::isTracingEnabled, TRACING_ENABLED_MSG)
                        .to("quarkus.otel.traces.sampler")
                        .paramLabel("type")
                        .build(),
                fromOption(TRACING_SAMPLER_RATIO)
                        .isEnabled(TracingPropertyMappers::isTracingEnabled, TRACING_ENABLED_MSG)
                        .to("quarkus.otel.traces.sampler.arg")
                        .validator(TracingPropertyMappers::validateRatio)
                        .paramLabel("ratio")
                        .build(),
                fromOption(TRACING_COMPRESSION)
                        .isEnabled(TracingPropertyMappers::isTracingEnabled, TRACING_ENABLED_MSG)
                        .to("quarkus.otel.exporter.otlp.traces.compression")
                        .paramLabel("method")
                        .build(),
                fromOption(TRACING_JDBC_ENABLED)
                        .mapFrom(TRACING_ENABLED)
                        .isEnabled(TracingPropertyMappers::isTracingEnabled, TRACING_ENABLED_MSG)
                        .to("quarkus.datasource.jdbc.telemetry")
                        .build(),
                fromOption(TRACING_INFINISPAN_ENABLED)
                        .mapFrom(TracingOptions.TRACING_ENABLED)
                        .to("kc.spi-cache-embedded--default--tracing-enabled")
                        .isEnabled(TracingPropertyMappers::isTracingAndEmbeddedInfinispanEnabled, "tracing and embedded Infinispan is enabled")
                        .build()
        };
    }

    private static void validateEndpoint(String value) {
        if (StringUtil.isBlank(value)) {
            throw new PropertyException("URL specified in 'tracing-endpoint' option must not be empty.");
        }

        if (!isValidUrl(value)) {
            throw new PropertyException("URL specified in 'tracing-endpoint' option is invalid.");
        }
    }

    private static void validateRatio(String value) {
        if (StringUtil.isBlank(value)) {
            throw new PropertyException("Ratio in 'tracing-sampler-ratio' option must not be empty.");
        }

        try {
            var ratio = Double.parseDouble(value);
            // note: 0.0 is a legal value, see https://quarkus.io/guides/opentelemetry-tracing#sampler
            if (ratio < 0.0 || ratio > 1.0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            throw new PropertyException("Ratio in 'tracing-sampler-ratio' option must be a double value in interval [0,1].");
        }
    }

    private static boolean isFeatureEnabled() {
        return Profile.isFeatureEnabled(Profile.Feature.OPENTELEMETRY);
    }

    public static boolean isTracingEnabled() {
        return Configuration.isTrue(TRACING_ENABLED);
    }

    public static boolean isTracingAndEmbeddedInfinispanEnabled() {
        return Configuration.isTrue(TRACING_ENABLED) && CachingPropertyMappers.cacheSetToInfinispan();
    }

    private static boolean isValidUrl(String url) {
        try {
            new URL(url).toURI();
            return true;
        } catch (MalformedURLException | URISyntaxException e) {
            return false;
        }
    }
}
