package com.hoanghnt.swiftpay.config;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Render Postgres provides {@code DATABASE_URL} as
 * {@code postgresql://user:password@host:port/database}. Spring JDBC expects a {@code jdbc:postgresql}
 * URL; this post-processor runs before context refresh and sets {@code spring.datasource.*} when
 * {@code DATABASE_URL} is present.
 */
public class RenderPostgresEnvironmentPostProcessor implements EnvironmentPostProcessor {

    private static final String DB_URL = "DATABASE_URL";
    private static final String PROP_DATASOURCE_URL = "spring.datasource.url";
    private static final String PROP_DATASOURCE_USERNAME = "spring.datasource.username";
    private static final String PROP_DATASOURCE_PASSWORD = "spring.datasource.password";
    private static final String RENDER_OVERRIDES = "renderPostgresOverrides";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty(DB_URL);
        if (databaseUrl == null || databaseUrl.isBlank()
                || !databaseUrl.startsWith("postgresql://")) {
            return;
        }
        JdbcFromPostgresUrl parsed;
        try {
            parsed = parsePostgresqlUrl(databaseUrl);
        } catch (Exception e) {
            throw new IllegalStateException("Invalid DATABASE_URL; cannot start application", e);
        }
        Map<String, Object> map = new HashMap<>();
        map.put(PROP_DATASOURCE_URL, parsed.jdbcUrl());
        map.put(PROP_DATASOURCE_USERNAME, parsed.username());
        map.put(PROP_DATASOURCE_PASSWORD, parsed.password());
        environment.getPropertySources().addFirst(new MapPropertySource(RENDER_OVERRIDES, map));
    }

    private static JdbcFromPostgresUrl parsePostgresqlUrl(String databaseUrl) {
        URI u = URI.create(databaseUrl);
        String userInfo = u.getRawUserInfo();
        if (userInfo == null || userInfo.isEmpty()) {
            throw new IllegalArgumentException("DATABASE_URL has no user info");
        }
        int split = userInfo.indexOf(':');
        String user = split >= 0 ? userInfo.substring(0, split) : userInfo;
        String pass = split >= 0 ? userInfo.substring(split + 1) : "";
        String userDecoded = user.isEmpty() ? user : java.net.URLDecoder.decode(user, java.nio.charset.StandardCharsets.UTF_8);
        String passDecoded = pass.isEmpty() ? pass : java.net.URLDecoder.decode(pass, java.nio.charset.StandardCharsets.UTF_8);
        String host = u.getHost();
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("DATABASE_URL has no host");
        }
        int port = u.getPort() > 0 ? u.getPort() : 5432;
        String path = u.getPath();
        if (path == null || path.length() < 2) {
            throw new IllegalArgumentException("DATABASE_URL has no database name in path");
        }
        String database = path.substring(1);
        String baseQuery = u.getQuery();
        StringBuilder jdbc = new StringBuilder();
        jdbc.append("jdbc:postgresql://").append(host).append(':').append(port).append('/').append(database);
        jdbc.append("?sslmode=require");
        if (baseQuery != null && !baseQuery.isEmpty()) {
            jdbc.append('&').append(baseQuery);
        }
        return new JdbcFromPostgresUrl(jdbc.toString(), userDecoded, passDecoded);
    }

    private record JdbcFromPostgresUrl(String jdbcUrl, String username, String password) {
    }
}
