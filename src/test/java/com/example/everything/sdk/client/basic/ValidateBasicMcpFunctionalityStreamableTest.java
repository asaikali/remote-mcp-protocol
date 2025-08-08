package com.example.everything.sdk.client.basic;

import com.example.sdk.client.EverythingTestClient;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

/**
 * MCP functionality validation tests specifically for HTTP_STREAMABLE transport.
 * 
 * This test class validates all basic MCP operations using the HTTP_STREAMABLE transport protocol.
 * It inherits from ValidateBasicMcpFunctionalityTestBase which inherits from EverythingServerTest,
 * providing access to both streamable and SSE servers.
 * 
 * Usage:
 * ```bash
 * # Run only HTTP_STREAMABLE tests (fast - uses dedicated streamable server)
 * ./mvnw test -Dtest="ValidateBasicMcpFunctionalityStreamableTest"
 * 
 * # Run specific test method for HTTP_STREAMABLE
 * ./mvnw test -Dtest="ValidateBasicMcpFunctionalityStreamableTest#testListPrompts"
 * ```
 * 
 * Benefits:
 * - Uses dedicated streamable server from EverythingServerTest
 * - Fast execution with proper server configuration
 * - Clear focus on HTTP_STREAMABLE protocol
 * - Ideal for development when you know your proxy supports HTTP_STREAMABLE
 * - Perfect for CI/CD pipelines that only need to validate specific transport types
 */
@DisplayName("Validate Basic MCP Functionality - HTTP Streamable Transport")
class ValidateBasicMcpFunctionalityStreamableTest extends ValidateBasicMcpFunctionalityTestBase {

    @Override
    protected EverythingTestClient.TransportType getTransportType() {
        return EverythingTestClient.TransportType.HTTP_STREAMABLE;
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
