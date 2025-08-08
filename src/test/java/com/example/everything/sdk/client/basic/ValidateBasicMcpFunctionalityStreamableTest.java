package com.example.everything.sdk.client.basic;

import com.example.sdk.client.EverythingTestClient;
import org.junit.jupiter.api.DisplayName;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * MCP functionality validation tests specifically for HTTP_STREAMABLE transport.
 * 
 * This test class validates all basic MCP operations using the HTTP_STREAMABLE transport protocol.
 * It extends the base test class and specifies the transport type to use.
 * 
 * Usage:
 * ```bash
 * # Run only HTTP_STREAMABLE tests (fast - no timeout waiting for unsupported SSE)
 * ./mvnw test -Dtest="ValidateBasicMcpFunctionalityStreamableTest"
 * 
 * # Run specific test method for HTTP_STREAMABLE
 * ./mvnw test -Dtest="ValidateBasicMcpFunctionalityStreamableTest#testListPrompts"
 * ```
 * 
 * Benefits:
 * - Fast execution (no SSE timeout delays)
 * - Clear focus on HTTP_STREAMABLE protocol
 * - Ideal for development when you know your proxy supports HTTP_STREAMABLE
 * - Perfect for CI/CD pipelines that only need to validate specific transport types
 */
@DisplayName("Validate Basic MCP Functionality - HTTP Streamable Transport")
class ValidateBasicMcpFunctionalityStreamableTest extends ValidateBasicMcpFunctionalityTestBase {

    @Container
    static GenericContainer<?> streamableContainer = new GenericContainer<>(DockerImageName.parse("mcp-everything:latest"))
            .withCommand("/app/entry-point.sh", "streamableHttp")
            .withExposedPorts(MCP_PORT)
            .waitingFor(Wait.forLogMessage(".*MCP Streamable HTTP Server listening on port 3001.*", 1)
                    .withStartupTimeout(Duration.ofSeconds(30)));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("mcp.server.host", streamableContainer::getHost);
        registry.add("mcp.server.port", streamableContainer::getFirstMappedPort);
    }

    @Override
    protected EverythingTestClient.TransportType getTransportType() {
        return EverythingTestClient.TransportType.HTTP_STREAMABLE;
    }

    @Override
    protected GenericContainer<?> getContainer() {
        return streamableContainer;
    }

    @Override
    protected Duration getRequestTimeout() {
        // HTTP_STREAMABLE typically has fast response times
        return Duration.ofSeconds(10);
    }

    @Override
    protected boolean isTransportSupported() {
        // HTTP_STREAMABLE is fully supported by the Everything Server
        return true;
    }
}
