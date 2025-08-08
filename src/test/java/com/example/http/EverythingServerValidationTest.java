package com.example.http;

import io.modelcontextprotocol.spec.McpSchema;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for validating MCP Everything Server functionality
 * using the reusable EverythingServerTestClient.
 * 
 * This test class can serve as a template for testing MCP proxies and other
 * MCP server implementations in different projects.
 */
@SpringBootTest
@Testcontainers
class EverythingServerValidationTest {

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

    private EverythingServerTestClient createTestClient() {
        String baseUrl = String.format("http://%s:%d%s",
                mcpContainer.getHost(),
                mcpContainer.getFirstMappedPort(),
                MCP_ENDPOINT);
        
        return EverythingServerTestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Test
    void shouldInitializeSuccessfully() {
        // Given
        EverythingServerTestClient client = createTestClient();

        // When
        EverythingServerTestClient.InitializationResult result = client.initialize();

        // Then
        assertNotNull(result.sessionId(), "Session ID should be present");
        assertFalse(result.sessionId().trim().isEmpty(), "Session ID should not be empty");
        assertNotNull(result.protocolVersion(), "Protocol version should be present");
        
        assertNotNull(result.serverInfo(), "Server info should be present");
        assertNotNull(result.serverInfo().name(), "Server info should have name");
        assertNotNull(result.serverInfo().version(), "Server info should have version");
    }

    @Test
    void shouldListExpectedTools() {
        // Given
        EverythingServerTestClient client = createTestClient();
        client.initialize();

        // When
        EverythingServerTestClient.ToolsListResult result = client.listTools();

        // Then
        assertTrue(result.tools().size() > 0, "Should have at least one tool");
        
        // Extract tool names from Tool objects
        Set<String> toolNames = result.tools().stream()
                .map(McpSchema.Tool::name)
                .collect(java.util.stream.Collectors.toSet());
        
        // Verify expected core tools are present
        Set<String> expectedTools = Set.of("echo", "printEnv", "add", "longRunningOperation");
        for (String expectedTool : expectedTools) {
            assertTrue(toolNames.contains(expectedTool), 
                "Expected tool '" + expectedTool + "' should be available");
        }
        
        System.out.println("✅ Tools listing successful");
        System.out.println("📋 Total tools: " + result.tools().size());
        System.out.println("📋 Core tools verified: " + expectedTools);
        System.out.println("📋 All tools: " + toolNames);
    }

    @Test
    void shouldValidateCoreTools() {
        // Given
        EverythingServerTestClient client = createTestClient();
        client.initialize();

        // When & Then - should not throw any exceptions
        assertDoesNotThrow(() -> client.validateCoreTools(), 
            "Core tool validation should pass");
        
        System.out.println("✅ Core tools validation passed");
    }

    @Test
    void shouldEchoMessage() {
        // Given
        EverythingServerTestClient client = createTestClient();
        client.initialize();
        String testMessage = "Hello from MCP Test Suite!";

        // When
        EverythingServerTestClient.CallToolResult result = client.invokeEcho(testMessage);

        // Then
        assertNotNull(result.callToolResult(), "Echo result should not be null");
        assertNotNull(result.callToolResult().content(), "Echo result content should not be null");
        assertFalse(result.callToolResult().content().isEmpty(), "Echo result content should not be empty");
        
        // Extract text content
        var content = result.callToolResult().content().get(0);
        assertTrue(content instanceof McpSchema.TextContent, "Content should be text");
        String echoMessage = ((McpSchema.TextContent) content).text();
        
        assertTrue(echoMessage.contains(testMessage), 
            "Echo result should contain the input message");
        
        System.out.println("✅ Echo tool successful");
        System.out.println("📋 Input: " + testMessage);
        System.out.println("📋 Output: " + echoMessage);
    }

    @Test
    void shouldAddTwoNumbers() {
        // Given
        EverythingServerTestClient client = createTestClient();
        client.initialize();
        int a = 2, b = 3, expected = 5;

        // When
        EverythingServerTestClient.CallToolResult result = client.invokeAdd(a, b);

        // Then
        assertNotNull(result.callToolResult(), "Add result should not be null");
        assertNotNull(result.callToolResult().content(), "Add result content should not be null");
        assertFalse(result.callToolResult().content().isEmpty(), "Add result content should not be empty");
        
        // Extract text content
        var content = result.callToolResult().content().get(0);
        assertTrue(content instanceof McpSchema.TextContent, "Content should be text");
        String addMessage = ((McpSchema.TextContent) content).text();
        
        assertTrue(addMessage.contains(String.valueOf(expected)), 
            "Full text should contain the result");
        
        System.out.println("✅ Add tool successful");
        System.out.println("📋 Calculation: " + a + " + " + b + " = " + expected);
        System.out.println("📋 Full response: " + addMessage);
    }

    @Test
    void shouldRunCompleteTestSuite() {
        // Given
        EverythingServerTestClient client = createTestClient();

        // When
        EverythingServerTestClient.TestSuiteResult result = client.runTestSuite();

        // Then
        assertTrue(result.success(), "Complete test suite should pass");
        assertNotNull(result.sessionId(), "Should have a session ID");
        assertTrue(result.toolCount() >= 4, "Should have at least 4 tools");
        assertNotNull(result.echoMessage(), "Should have echo result");
        assertEquals(5, result.addResult(), "Add result should be 5");
        assertNull(result.errorMessage(), "Should have no error message");
        
        System.out.println("✅ Complete test suite successful");
        System.out.println("📋 Session ID: " + result.sessionId());
        System.out.println("📋 Tool count: " + result.toolCount());
        System.out.println("📋 Echo message: " + result.echoMessage());
        System.out.println("📋 Add result: " + result.addResult());
    }

    @Test
    void shouldHandleMultipleOperations() {
        // Given
        EverythingServerTestClient client = createTestClient();
        client.initialize();

        // When & Then - Test multiple operations in sequence
        client.validateCoreTools();
        
        EverythingServerTestClient.CallToolResult echo1 = client.invokeEcho("First message");
        EverythingServerTestClient.CallToolResult echo2 = client.invokeEcho("Second message");
        
        EverythingServerTestClient.CallToolResult add1 = client.invokeAdd(1, 1);
        EverythingServerTestClient.CallToolResult add2 = client.invokeAdd(5, 7);
        
        // Extract text content for validation
        String echo1Message = ((McpSchema.TextContent) echo1.callToolResult().content().get(0)).text();
        String echo2Message = ((McpSchema.TextContent) echo2.callToolResult().content().get(0)).text();
        String add1Message = ((McpSchema.TextContent) add1.callToolResult().content().get(0)).text();
        String add2Message = ((McpSchema.TextContent) add2.callToolResult().content().get(0)).text();
        
        // Validate results
        assertTrue(echo1Message.contains("First message"));
        assertTrue(echo2Message.contains("Second message"));
        assertTrue(add1Message.contains("2"));  // 1 + 1 = 2
        assertTrue(add2Message.contains("12")); // 5 + 7 = 12
        
        System.out.println("✅ Multiple operations successful");
        System.out.println("📋 Echo 1: " + echo1Message);
        System.out.println("📋 Echo 2: " + echo2Message);
        System.out.println("📋 Add 1: " + add1Message);
        System.out.println("📋 Add 2: " + add2Message);
    }
}
