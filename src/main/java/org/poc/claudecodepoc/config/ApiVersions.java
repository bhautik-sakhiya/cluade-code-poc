package org.poc.claudecodepoc.config;

public final class ApiVersions {

    private ApiVersions() {}

    public static final String PREFIX = "/api/v{apiVersion:[0-9]+}";
}