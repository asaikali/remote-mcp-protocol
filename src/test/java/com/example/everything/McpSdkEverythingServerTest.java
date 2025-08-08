package com.example.everything;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for validating MCP Everything Server functionality
 * using the official MCP Java SDK synchronous client.
 * 
 * This test demonstrates the correct usage of the MCP Java SDK for:
 * - HTTP Streamable transport connection to MCP servers
 * - Synchronous client operations and lifecycle management
 * - Tool discovery, validation, and invocation
 * - Proper error handling and resource cleanup
 * - Advanced MCP features like progress notifications and logging
 */
@SpringBootTest
@Testcontainers
class McpSdkEverythingServerTest {

    private static final int MCP_PORT = 3001;
    private static final String MCP_ENDPOINT = "/mcp";

    @Container
    static GenericContainer<?> mcpContainer = new GenericContainer<>(DockerImageName.parse("mcp-everything:latest"))
            .withCommand("/app/entry-point.sh", "streamableHttp")
            .withExposedPorts(MCP_PORT)
            .waitingFor(Wait.forLogMessage(".*MCP Streamable HTTP Server listening on port 3001.*", 1)
                    .withStartupTimeout(Duration.ofSeconds(60)));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("mcp.server.host", mcpContainer::getHost);
        registry.add("mcp.server.port", mcpContainer::getFirstMappedPort);
    }

    private McpSyncClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = String.format("http://%s:%d%s",
                mcpContainer.getHost(),
                mcpContainer.getFirstMappedPort(),
                MCP_ENDPOINT);
        
        // Create HTTP Streamable transport
        var transport = HttpClientStreamableHttpTransport.builder(baseUrl)
                .endpoint(MCP_ENDPOINT)
                .build();

        // Create synchronous MCP client with comprehensive configuration
        client = McpClient.sync(transport)
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(10))
                .capabilities(ClientCapabilities.builder()
                        .roots(true)
                        .sampling()
                        .elicitation()
                        .build())
                .clientInfo(new Implementation("MCP SDK Integration Test", "1.0.0"))
                .toolsChangeConsumer(tools -> System.out.println("🔧 Tools changed: " + tools.size() + " tools available"))
                .loggingConsumer(log -> System.out.println("📝 Server log [" + log.level() + "]: " + log.data()))
                .progressConsumer(progress -> System.out.println("⏳ Progress [" + progress.progressToken() + "]: " + progress.progress() + "/" + progress.total()))
                .build();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            assertTrue(client.closeGracefully(), "Client should close gracefully");
        }
    }

    @Test
    void shouldInitializeWithMcpSdk() {
        // When
        InitializeResult result = client.initialize();

        // Then
        assertNotNull(result, "Initialization result should not be null");
        assertNotNull(result.protocolVersion(), "Protocol version should be present");
        assertNotNull(result.serverInfo(), "Server info should be present");
        assertNotNull(result.serverInfo().name(), "Server name should be present");
        assertNotNull(result.serverInfo().version(), "Server version should be present");
        assertNotNull(result.capabilities(), "Server capabilities should be present");

        System.out.println("✅ MCP SDK Initialization successful");
        System.out.println("📋 Protocol Version: " + result.protocolVersion());
        System.out.println("📋 Server: " + result.serverInfo().name() + " v" + result.serverInfo().version());
        System.out.println("📋 Capabilities: " + (result.capabilities().tools() != null ? "✓ Tools" : "✗ Tools") + 
                          ", " + (result.capabilities().resources() != null ? "✓ Resources" : "✗ Resources"));
    }

    @Test
    void shouldListAndValidateToolsWithMcpSdk() {
        // Given
        client.initialize();

        // When
        ListToolsResult result = client.listTools();

        // Then
        assertNotNull(result, "Tools result should not be null");
        assertNotNull(result.tools(), "Tools list should not be null");
        assertTrue(result.tools().size() > 0, "Should have at least one tool");

        // Extract tool names and validate core tools
        Set<String> toolNames = result.tools().stream()
                .map(Tool::name)
                .collect(Collectors.toSet());

        Set<String> expectedCoreTools = Set.of("echo", "printEnv", "add", "longRunningOperation");
        for (String expectedTool : expectedCoreTools) {
            assertTrue(toolNames.contains(expectedTool),
                    "Expected core tool '" + expectedTool + "' should be available");
        }

        // Validate tool structure
        for (Tool tool : result.tools()) {
            assertNotNull(tool.name(), "Tool name should not be null");
            assertNotNull(tool.description(), "Tool description should not be null");
            // Input schema is optional but if present should be valid
            if (tool.inputSchema() != null) {
                assertNotNull(tool.inputSchema().type(), "Input schema type should not be null when schema is present");
            }
        }

        System.out.println("✅ Tools listing and validation successful");
        System.out.println("📋 Total tools: " + result.tools().size());
        System.out.println("📋 Core tools verified: " + expectedCoreTools);
        System.out.println("📋 All available tools: " + toolNames);
    }

    @Test
    void shouldInvokeEchoToolWithMcpSdk() {
        // Given
        client.initialize();
        String testMessage = "Hello from MCP Java SDK!";
        CallToolRequest request = new CallToolRequest("echo", Map.of("message", testMessage));

        // When
        CallToolResult result = client.callTool(request);

        // Then
        assertNotNull(result, "Call tool result should not be null");
        assertNotNull(result.content(), "Result content should not be null");
        assertFalse(result.content().isEmpty(), "Result content should not be empty");
        assertNull(result.isError(), "Result should not indicate error");

        // Validate content structure
        Content content = result.content().get(0);
        assertInstanceOf(TextContent.class, content, "Content should be TextContent");
        
        TextContent textContent = (TextContent) content;
        assertNotNull(textContent.text(), "Text content should not be null");
        assertTrue(textContent.text().contains(testMessage),
                "Echo result should contain the input message");

        System.out.println("✅ Echo tool invocation successful");
        System.out.println("📋 Input: " + testMessage);
        System.out.println("📋 Output: " + textContent.text());
    }

    @Test
    void shouldInvokeAddToolWithMcpSdk() {
        // Given
        client.initialize();
        int a = 15, b = 27, expected = 42;
        CallToolRequest request = new CallToolRequest("add", Map.of("a", a, "b", b));

        // When
        CallToolResult result = client.callTool(request);

        // Then
        assertNotNull(result, "Call tool result should not be null");
        assertNotNull(result.content(), "Result content should not be null");
        assertFalse(result.content().isEmpty(), "Result content should not be empty");
        assertNull(result.isError(), "Result should not indicate error");

        // Validate content structure and extract result
        Content content = result.content().get(0);
        assertInstanceOf(TextContent.class, content, "Content should be TextContent");
        
        TextContent textContent = (TextContent) content;
        String resultText = textContent.text();
        assertNotNull(resultText, "Text content should not be null");
        assertTrue(resultText.contains(String.valueOf(expected)),
                "Add result should contain the expected sum: " + expected);

        System.out.println("✅ Add tool invocation successful");
        System.out.println("📋 Calculation: " + a + " + " + b + " = " + expected);
        System.out.println("📋 Server response: " + resultText);
    }

    @Test
    void shouldHandleLongRunningOperationWithProgress() {
        // Given
        client.initialize();
        String progressToken = "test-progress-" + System.currentTimeMillis();
        CallToolRequest request = CallToolRequest.builder()
                .name("longRunningOperation")
                .arguments(Map.of("duration", 2, "steps", 3))
                .progressToken(progressToken)
                .build();

        // When
        CallToolResult result = client.callTool(request);

        // Then
        assertNotNull(result, "Long running operation result should not be null");
        assertNotNull(result.content(), "Result content should not be null");
        assertFalse(result.content().isEmpty(), "Result content should not be empty");
        assertNull(result.isError(), "Result should not indicate error");

        // Validate the operation completed successfully
        Content content = result.content().get(0);
        assertInstanceOf(TextContent.class, content, "Content should be TextContent");
        
        TextContent textContent = (TextContent) content;
        String resultText = textContent.text();
        assertNotNull(resultText, "Text content should not be null");
        assertTrue(resultText.toLowerCase().contains("completed") || resultText.toLowerCase().contains("finished"),
                "Long running operation should indicate completion");

        System.out.println("✅ Long running operation completed successfully");
        System.out.println("📋 Progress Token: " + progressToken);
        System.out.println("📋 Result: " + resultText);
    }

    @Test
    void shouldHandleInvalidToolGracefully() {
        // Given
        client.initialize();
        CallToolRequest request = new CallToolRequest("nonExistentTool", Map.of("param", "value"));

        // When & Then
        Exception exception = assertThrows(Exception.class, () -> client.callTool(request));
        assertNotNull(exception, "Should throw exception for invalid tool");
        
        System.out.println("✅ Invalid tool handling successful");
        System.out.println("📋 Expected exception: " + exception.getClass().getSimpleName());
    }

    @Test
    void shouldTestPingOperation() {
        // Given
        client.initialize();

        // When & Then
        assertDoesNotThrow(() -> {
            Object pingResult = client.ping();
            System.out.println("✅ Ping operation successful");
            System.out.println("📋 Ping result: " + (pingResult != null ? pingResult.toString() : "null"));
        });
    }

    @Test
    void shouldListResourcesWhenAvailable() {
        // Given
        client.initialize();

        // When
        ListResourcesResult result = client.listResources();

        // Then
        assertNotNull(result, "Resources result should not be null");
        assertNotNull(result.resources(), "Resources list should not be null");
        
        System.out.println("✅ Resources listing successful");
        System.out.println("📋 Available resources: " + result.resources().size());
        
        // If resources are available, validate their structure
        if (!result.resources().isEmpty()) {
            Resource firstResource = result.resources().get(0);
            assertNotNull(firstResource.uri(), "Resource URI should not be null");
            assertNotNull(firstResource.name(), "Resource name should not be null");
            System.out.println("📋 Sample resource: " + firstResource.name() + " (" + firstResource.uri() + ")");
        }
    }

    @Test
    void shouldTestClientStateAndCapabilities() {
        // Given - Test client state before initialization
        assertFalse(client.isInitialized(), "Client should not be initialized initially");

        // When
        client.initialize();

        // Then
        assertTrue(client.isInitialized(), "Client should be initialized after initialize() call");
        
        assertNotNull(client.getClientCapabilities(), "Client capabilities should be available");
        assertNotNull(client.getClientInfo(), "Client info should be available");
        assertNotNull(client.getServerCapabilities(), "Server capabilities should be available after initialization");
        assertNotNull(client.getServerInfo(), "Server info should be available after initialization");

        System.out.println("✅ Client state and capabilities validation successful");
        System.out.println("📋 Client: " + client.getClientInfo().name() + " v" + client.getClientInfo().version());
        System.out.println("📋 Client supports roots: " + (client.getClientCapabilities().roots() != null));
        System.out.println("📋 Client supports sampling: " + (client.getClientCapabilities().sampling() != null));
    }

    @Test
    void shouldPerformComprehensiveIntegrationTest() {
        System.out.println("🚀 Starting comprehensive MCP SDK integration test");

        // Step 1: Initialize
        InitializeResult initResult = client.initialize();
        assertNotNull(initResult);
        System.out.println("✓ Step 1: Initialization completed");

        // Step 2: List and validate tools
        ListToolsResult toolsResult = client.listTools();
        assertTrue(toolsResult.tools().size() >= 4);
        System.out.println("✓ Step 2: Found " + toolsResult.tools().size() + " tools");

        // Step 3: Test echo functionality
        CallToolResult echoResult = client.callTool(new CallToolRequest("echo", Map.of("message", "Integration Test")));
        TextContent echoContent = (TextContent) echoResult.content().get(0);
        assertTrue(echoContent.text().contains("Integration Test"));
        System.out.println("✓ Step 3: Echo test passed");

        // Step 4: Test mathematical operation
        CallToolResult addResult = client.callTool(new CallToolRequest("add", Map.of("a", 10, "b", 32)));
        TextContent addContent = (TextContent) addResult.content().get(0);
        assertTrue(addContent.text().contains("42"));
        System.out.println("✓ Step 4: Add operation test passed");

        // Step 5: Test server ping
        assertDoesNotThrow(() -> client.ping());
        System.out.println("✓ Step 5: Ping test passed");

        // Step 6: List available resources
        ListResourcesResult resourcesResult = client.listResources();
        assertNotNull(resourcesResult.resources());
        System.out.println("✓ Step 6: Resources listing completed (" + resourcesResult.resources().size() + " resources)");

        System.out.println("🎉 Comprehensive MCP SDK integration test completed successfully!");
        System.out.println("📊 Test Summary:");
        System.out.println("   - Server: " + initResult.serverInfo().name() + " v" + initResult.serverInfo().version());
        System.out.println("   - Protocol: " + initResult.protocolVersion());
        System.out.println("   - Tools: " + toolsResult.tools().size() + " available");
        System.out.println("   - Resources: " + resourcesResult.resources().size() + " available");
        System.out.println("   - All core operations: ✅ PASSED");
    }
}