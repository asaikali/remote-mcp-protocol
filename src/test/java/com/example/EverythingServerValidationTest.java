package com.example;

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
        
        return new EverythingServerTestClient(baseUrl);
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
        assertEquals("2025-06-18", result.protocolVersion(), "Should support the expected protocol version");
        
        assertTrue(result.serverInfo().has("name"), "Server info should have name");
        assertTrue(result.serverInfo().has("version"), "Server info should have version");
        
        System.out.println("✅ Initialization successful");
        System.out.println("📋 Session ID: " + result.sessionId());
        System.out.println("📋 Protocol Version: " + result.protocolVersion());
        System.out.println("📋 Server Name: " + result.serverInfo().get("name").asText());
    }

    @Test
    void shouldListExpectedTools() {
        // Given
        EverythingServerTestClient client = createTestClient();
        client.initialize();

        // When
        EverythingServerTestClient.ToolsListResult result = client.listTools();

        // Then
        assertTrue(result.toolNames().size() > 0, "Should have at least one tool");
        
        // Verify expected core tools are present
        Set<String> expectedTools = Set.of("echo", "printEnv", "add", "longRunningOperation");
        for (String expectedTool : expectedTools) {
            assertTrue(result.toolNames().contains(expectedTool), 
                "Expected tool '" + expectedTool + "' should be available");
        }
        
        System.out.println("✅ Tools listing successful");
        System.out.println("📋 Total tools: " + result.toolNames().size());
        System.out.println("📋 Core tools verified: " + expectedTools);
        System.out.println("📋 All tools: " + result.toolNames());
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
        EverythingServerTestClient.EchoResult result = client.invokeEcho(testMessage);

        // Then
        assertNotNull(result.message(), "Echo result should not be null");
        assertTrue(result.message().contains(testMessage), 
            "Echo result should contain the input message");
        
        System.out.println("✅ Echo tool successful");
        System.out.println("📋 Input: " + testMessage);
        System.out.println("📋 Output: " + result.message());
    }

    @Test
    void shouldAddTwoNumbers() {
        // Given
        EverythingServerTestClient client = createTestClient();
        client.initialize();
        int a = 2, b = 3, expected = 5;

        // When
        EverythingServerTestClient.AddResult result = client.invokeAdd(a, b);

        // Then
        assertEquals(expected, result.result(), "2 + 3 should equal 5");
        assertTrue(result.fullText().contains(String.valueOf(expected)), 
            "Full text should contain the result");
        
        System.out.println("✅ Add tool successful");
        System.out.println("📋 Calculation: " + a + " + " + b + " = " + result.result());
        System.out.println("📋 Full response: " + result.fullText());
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
        
        EverythingServerTestClient.EchoResult echo1 = client.invokeEcho("First message");
        EverythingServerTestClient.EchoResult echo2 = client.invokeEcho("Second message");
        
        EverythingServerTestClient.AddResult add1 = client.invokeAdd(1, 1);
        EverythingServerTestClient.AddResult add2 = client.invokeAdd(5, 7);
        
        // Validate results
        assertTrue(echo1.message().contains("First message"));
        assertTrue(echo2.message().contains("Second message"));
        assertEquals(2, add1.result());
        assertEquals(12, add2.result());
        
        System.out.println("✅ Multiple operations successful");
        System.out.println("📋 Echo 1: " + echo1.message());
        System.out.println("📋 Echo 2: " + echo2.message());
        System.out.println("📋 Add 1: " + add1.result());
        System.out.println("📋 Add 2: " + add2.result());
    }
}