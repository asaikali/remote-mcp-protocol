package com.example.everything.sdk.client.basic;

import com.example.sdk.client.EverythingTestClient;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

/**
 * MCP functionality validation tests specifically for HTTP_SSE transport.
 * 
 * This test class validates all basic MCP operations using the HTTP_SSE (Server-Sent Events) transport protocol.
 * It inherits from ValidateBasicMcpFunctionalityTestBase which inherits from EverythingServerTest,
 * providing access to both streamable and SSE servers.
 * 
 * Usage:
 * ```bash
 * # Run only HTTP_SSE tests (fast - uses dedicated SSE server)
 * ./mvnw test -Dtest="ValidateBasicMcpFunctionalitySseTest"
 * 
 * # Run specific test method for HTTP_SSE
 * ./mvnw test -Dtest="ValidateBasicMcpFunctionalitySseTest#testListPrompts"
 * ```
 * 
 * Benefits:
 * - Uses dedicated SSE server from EverythingServerTest
 * - Fast execution with proper server configuration
 * - Tests SSE transport protocol specifically
 * - Useful for testing MCP proxies that support SSE
 * - Independent from HTTP_STREAMABLE test execution
 * 
 * Server Compatibility:
 * - Uses dedicated SSE server instance started in SSE mode
 * - Full SSE transport protocol support and validation
 * - Works alongside streamable server for comprehensive testing
 */
@DisplayName("Validate Basic MCP Functionality - HTTP SSE Transport")
class ValidateBasicMcpFunctionalitySseTest extends ValidateBasicMcpFunctionalityTestBase {

    @Override
    protected EverythingTestClient.TransportType getTransportType() {
        return EverythingTestClient.TransportType.HTTP_SSE;
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
