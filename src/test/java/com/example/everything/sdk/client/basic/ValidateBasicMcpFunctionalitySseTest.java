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
 * MCP functionality validation tests specifically for HTTP_SSE transport.
 * 
 * This test class validates all basic MCP operations using the HTTP_SSE (Server-Sent Events) transport protocol.
 * It extends the base test class and specifies the transport type to use.
 * 
 * Note: The current Everything Server setup only supports HTTP_STREAMABLE transport.
 * These tests will gracefully skip if SSE is not supported by the server.
 * 
 * Usage:
 * ```bash
 * # Run only HTTP_SSE tests
 * ./mvnw test -Dtest="ValidateBasicMcpFunctionalitySseTest"
 * 
 * # Run specific test method for HTTP_SSE
 * ./mvnw test -Dtest="ValidateBasicMcpFunctionalitySseTest#testListPrompts"
 * ```
 * 
 * Benefits:
 * - Tests SSE transport protocol specifically
 * - Graceful handling of unsupported transport scenarios
 * - Useful for testing MCP proxies that support SSE
 * - Independent from HTTP_STREAMABLE test execution
 * 
 * Server Compatibility:
 * - If your MCP server supports SSE, these tests will run and validate functionality
 * - If SSE is not supported, tests will skip gracefully with clear warning messages
 * - This allows the same test suite to work with different server configurations
 */
@DisplayName("Validate Basic MCP Functionality - HTTP SSE Transport")
class ValidateBasicMcpFunctionalitySseTest extends ValidateBasicMcpFunctionalityTestBase {

    @Container
    static GenericContainer<?> sseContainer = new GenericContainer<>(DockerImageName.parse("mcp-everything:latest"))
            .withCommand("/app/entry-point.sh", "sse")
            .withExposedPorts(MCP_PORT)
            .waitingFor(Wait.forLogMessage(".*Server is running on port 3001.*", 1)
                    .withStartupTimeout(Duration.ofSeconds(30)));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("mcp.server.host", sseContainer::getHost);
        registry.add("mcp.server.port", sseContainer::getFirstMappedPort);
    }

    @Override
    protected EverythingTestClient.TransportType getTransportType() {
        return EverythingTestClient.TransportType.HTTP_SSE;
    }

    @Override
    protected GenericContainer<?> getContainer() {
        return sseContainer;
    }

    @Override
    protected Duration getRequestTimeout() {
        // SSE might need more time for initial connection setup
        return Duration.ofSeconds(15);
    }

    @Override
    protected boolean isTransportSupported() {
        // SSE is now fully supported since we start the server in SSE mode
        return true;
    }
}
