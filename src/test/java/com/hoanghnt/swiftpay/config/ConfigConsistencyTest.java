package com.hoanghnt.swiftpay.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ConfigConsistencyTest {

    private static final List<String> MODULES =
            List.of(".", "auth-service", "wallet-service", "transaction-service", "gateway");

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^}]*}");
    private static final Pattern FORBIDDEN = Pattern.compile("localhost|127\\.0\\.0\\.1");

    @Test
    @DisplayName("không file application*.yml nào được hard-code localhost/127.0.0.1 ngoài placeholder ${...}")
    void noHardcodedInfrastructureHosts() throws IOException {
        List<String> violations = new ArrayList<>();
        int scanned = 0;

        for (String module : MODULES) {
            Path resources = Path.of(module, "src", "main", "resources");
            if (!Files.isDirectory(resources)) {
                continue;
            }
            try (Stream<Path> files = Files.list(resources)) {
                List<Path> ymls = files
                        .filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml"))
                        .toList();

                for (Path yml : ymls) {
                    scanned++;
                    List<String> lines = Files.readAllLines(yml);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = stripComment(lines.get(i));
                        String withoutPlaceholders = PLACEHOLDER.matcher(line).replaceAll("");
                        if (FORBIDDEN.matcher(withoutPlaceholders).find()) {
                            violations.add("%s:%d → %s".formatted(yml, i + 1, lines.get(i).trim()));
                        }
                    }
                }
            }
        }

        assertThat(scanned)
                .as("phải quét được file application*.yml — nếu = 0 thì đường dẫn module sai, test vô nghĩa")
                .isGreaterThanOrEqualTo(MODULES.size());

        assertThat(violations)
                .as("""
                        Endpoint hạ tầng bị hard-code. Phải dùng dạng ${ENV_VAR:localhost:port} để môi
                        trường container override được — xem KafkaErrorHandlingConfig / docs/plans/04.""")
                .isEmpty();
    }

    private static String stripComment(String line) {
        int hash = line.indexOf('#');
        return hash >= 0 ? line.substring(0, hash) : line;
    }

    @Test
    @DisplayName("mọi module đều khai báo bootstrap-servers qua biến môi trường (không module nào lệch)")
    void allModulesResolveKafkaFromEnv() throws IOException {
        List<String> found = new ArrayList<>();
        List<String> violations = new ArrayList<>();

        for (String module : MODULES) {
            Path resources = Path.of(module, "src", "main", "resources");
            if (!Files.isDirectory(resources)) {
                continue;
            }
            try (Stream<Path> files = Files.list(resources)) {
                for (Path yml : files.filter(p -> p.getFileName().toString().matches("application.*\\.ya?ml")).toList()) {
                    for (String raw : Files.readAllLines(yml)) {
                        String line = stripComment(raw).trim();
                        if (!line.startsWith("bootstrap-servers:")) {
                            continue;
                        }
                        String value = line.substring("bootstrap-servers:".length()).trim();
                        found.add(module);
                        Matcher m = PLACEHOLDER.matcher(value);
                        if (!m.find() || m.start() != 0) {
                            violations.add("%s → bootstrap-servers phải bắt đầu bằng ${...}, đang là: %s"
                                    .formatted(yml, value));
                        }
                    }
                }
            }
        }

        assertThat(found).as("phải tìm thấy khai báo bootstrap-servers ở ít nhất 1 module").isNotEmpty();
        assertThat(violations).isEmpty();
    }
}
