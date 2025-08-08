package com.example.everything.sdk.client;

import com.example.sdk.client.EverythingTestClient;
import com.example.sdk.client.EverythingTools;
import com.example.sdk.client.EverythingResources;
import com.example.sdk.client.EverythingPrompts;
import io.modelcontextprotocol.spec.McpSchema.Resource;
import io.modelcontextprotocol.spec.McpSchema.GetPromptResult;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for the EverythingTestClient library to verify basic functionality
 * with the MCP Everything Server. This test suite covers the essential operations
 * to ensure the client library is working correctly.
 */
@SpringBootTest
@Testcontainers
class EverythingSmokeTest {

    private static final int MCP_PORT = 3001;

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

    private EverythingTestClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = String.format("http://%s:%d",
                mcpContainer.getHost(),
                mcpContainer.getFirstMappedPort());
        
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build();
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void smokeTest_initialization_shouldWork() {
        // When
        boolean initialized = client.initialize();

        // Then
        assertTrue(initialized, "Client should initialize successfully");
        assertTrue(client.isInitialized(), "Client should report as initialized");
        
        assertNotNull(client.getServerInfo(), "Server info should be available");
        assertNotNull(client.getServerInfo().name(), "Server name should be present");
        assertNotNull(client.getServerInfo().version(), "Server version should be present");
        
        System.out.println("✅ Initialization smoke test passed");
        System.out.println("📋 Server: " + client.getServerInfo().name() + " v" + client.getServerInfo().version());
    }

    @Test
    void smokeTest_addTool_shouldWork() {
        // Given
        assertTrue(client.initialize(), "Client should initialize");
        EverythingTools tools = client.getTools();

        // When
        int result = tools.add(10, 20);

        // Then
        assertEquals(30, result, "Add tool should return correct sum");
        
        System.out.println("✅ Add tool smoke test passed: 10 + 20 = " + result);
    }

    @Test
    void smokeTest_longRunningOperation_shouldWork() {
        // Given
        assertTrue(client.initialize(), "Client should initialize");
        EverythingTools tools = client.getTools();

        // When
        String result = tools.longRunningOperation(1, 2);

        // Then
        assertNotNull(result, "Long running operation should return a result");
        assertFalse(result.trim().isEmpty(), "Result should not be empty");
        
        System.out.println("✅ Long running operation smoke test passed");
        System.out.println("📋 Result: " + result);
    }

    @Test
    void smokeTest_ping_shouldWork() {
        // Given
        assertTrue(client.initialize(), "Client should initialize");

        // When
        boolean pingResult = client.ping();

        // Then
        assertTrue(pingResult, "Ping should succeed");
        
        System.out.println("✅ Ping smoke test passed");
    }

    @Test
    void smokeTest_getResources_shouldWork() {
        // Given
        assertTrue(client.initialize(), "Client should initialize");
        EverythingResources resources = client.getResources();

        // When
        List<Resource> availableResources = resources.getAvailableResources();

        // Then
        assertNotNull(availableResources, "Resources list should not be null");
        assertFalse(availableResources.isEmpty(), "Should have at least one resource");
        
        Resource firstResource = availableResources.get(0);
        assertNotNull(firstResource.uri(), "Resource URI should not be null");
        assertNotNull(firstResource.name(), "Resource name should not be null");
        
        System.out.println("✅ Get resources smoke test passed");
        System.out.println("📋 Found " + availableResources.size() + " resources");
        System.out.println("📋 First resource: " + firstResource.name() + " (" + firstResource.uri() + ")");
    }

    @Test
    void smokeTest_retrieveSpecificResource_shouldWork() {
        // Given
        assertTrue(client.initialize(), "Client should initialize");
        EverythingResources resources = client.getResources();
        
        List<Resource> availableResources = resources.getAvailableResources();
        assertFalse(availableResources.isEmpty(), "Should have at least one resource");
        
        Resource targetResource = availableResources.get(0);

        // When
        var resourceResult = resources.readResource(targetResource);

        // Then
        assertNotNull(resourceResult, "Resource result should not be null");
        assertNotNull(resourceResult.contents(), "Resource contents should not be null");
        assertFalse(resourceResult.contents().isEmpty(), "Resource should have content");
        
        var firstContent = resourceResult.contents().get(0);
        assertNotNull(firstContent.uri(), "Content URI should not be null");
        assertNotNull(firstContent.mimeType(), "Content MIME type should not be null");
        
        System.out.println("✅ Retrieve specific resource smoke test passed");
        System.out.println("📋 Retrieved resource: " + targetResource.name());
        System.out.println("📋 Content items: " + resourceResult.contents().size());
        System.out.println("📋 First content MIME type: " + firstContent.mimeType());
    }

    @Test
    void smokeTest_simplePrompt_shouldWork() {
        // Given
        assertTrue(client.initialize(), "Client should initialize");
        EverythingPrompts prompts = client.getPrompts();

        // When
        GetPromptResult result = prompts.getSimplePrompt();

        // Then
        assertNotNull(result, "Simple prompt result should not be null");
        assertNotNull(result.messages(), "Messages should not be null");
        assertFalse(result.messages().isEmpty(), "Should have at least one message");
        
        List<String> textContent = prompts.extractTextContent(result);
        assertFalse(textContent.isEmpty(), "Should have text content");
        assertTrue(textContent.get(0).contains("simple prompt"), "Should contain simple prompt text");
        
        System.out.println("✅ Simple prompt smoke test passed");
        System.out.println("📋 Messages: " + result.messages().size());
        System.out.println("📋 First message: " + textContent.get(0));
    }

    @Test
    void smokeTest_complexPrompt_shouldWork() {
        // Given
        assertTrue(client.initialize(), "Client should initialize");
        EverythingPrompts prompts = client.getPrompts();

        // When
        GetPromptResult result = prompts.getComplexPrompt("0.7", "casual");

        // Then
        assertNotNull(result, "Complex prompt result should not be null");
        assertNotNull(result.messages(), "Messages should not be null");
        assertTrue(result.messages().size() >= 2, "Should have multiple messages for complex prompt");
        
        List<String> textContent = prompts.extractTextContent(result);
        assertFalse(textContent.isEmpty(), "Should have text content");
        assertTrue(textContent.get(0).contains("complex prompt"), "Should contain complex prompt text");
        assertTrue(textContent.get(0).contains("temperature=0.7"), "Should contain temperature parameter");
        assertTrue(textContent.get(0).contains("style=casual"), "Should contain style parameter");
        
        boolean hasImageContent = prompts.hasImageContent(result);
        assertTrue(hasImageContent, "Complex prompt should include image content");
        
        System.out.println("✅ Complex prompt smoke test passed");
        System.out.println("📋 Messages: " + result.messages().size());
        System.out.println("📋 Has image content: " + hasImageContent);
        System.out.println("📋 First message: " + textContent.get(0));
    }

    @Test
    void smokeTest_resourcePrompt_shouldWork() {
        // Given
        assertTrue(client.initialize(), "Client should initialize");
        EverythingPrompts prompts = client.getPrompts();
        int testResourceId = 5;

        // When
        GetPromptResult result = prompts.getResourcePrompt(testResourceId);

        // Then
        assertNotNull(result, "Resource prompt result should not be null");
        assertNotNull(result.messages(), "Messages should not be null");
        assertTrue(result.messages().size() >= 2, "Should have multiple messages for resource prompt");
        
        List<String> textContent = prompts.extractTextContent(result);
        assertFalse(textContent.isEmpty(), "Should have text content");
        assertTrue(textContent.get(0).contains("Resource " + testResourceId), "Should reference the specific resource");
        
        boolean hasResourceContent = prompts.hasResourceContent(result);
        assertTrue(hasResourceContent, "Resource prompt should include resource content");
        
        System.out.println("✅ Resource prompt smoke test passed");
        System.out.println("📋 Messages: " + result.messages().size());
        System.out.println("📋 Resource ID: " + testResourceId);
        System.out.println("📋 Has resource content: " + hasResourceContent);
        System.out.println("📋 First message: " + textContent.get(0));
    }

    @Test
    void smokeTest_comprehensiveFlow_shouldWork() {
        System.out.println("🚀 Starting comprehensive smoke test flow");

        // Step 1: Initialize
        assertTrue(client.initialize(), "Client should initialize");
        System.out.println("✓ Step 1: Initialization completed");

        // Step 2: Test tools
        EverythingTools tools = client.getTools();
        int addResult = tools.add(5, 7);
        assertEquals(12, addResult, "Add operation should work");
        
        String echoResult = tools.echo("Smoke test message");
        assertTrue(echoResult.contains("Smoke test message"), "Echo should work");
        System.out.println("✓ Step 2: Tools functionality validated");

        // Step 3: Test resources
        EverythingResources resources = client.getResources();
        List<Resource> resourcesList = resources.getAvailableResources();
        assertTrue(resourcesList.size() > 0, "Should have resources");
        
        if (!resourcesList.isEmpty()) {
            var sampleResource = resourcesList.get(0);
            var resourceContent = resources.readResource(sampleResource);
            assertNotNull(resourceContent, "Should be able to read resource");
        }
        System.out.println("✓ Step 3: Resources functionality validated");

        // Step 4: Test prompts
        EverythingPrompts prompts = client.getPrompts();
        GetPromptResult simplePrompt = prompts.getSimplePrompt();
        assertNotNull(simplePrompt, "Simple prompt should work");
        
        GetPromptResult complexPrompt = prompts.getComplexPrompt("0.5", "technical");
        assertNotNull(complexPrompt, "Complex prompt should work");
        assertTrue(prompts.hasImageContent(complexPrompt), "Complex prompt should have image content");
        
        GetPromptResult resourcePrompt = prompts.getResourcePrompt(3);
        assertNotNull(resourcePrompt, "Resource prompt should work");
        assertTrue(prompts.hasResourceContent(resourcePrompt), "Resource prompt should have resource content");
        System.out.println("✓ Step 4: Prompts functionality validated");

        // Step 5: Test connectivity
        assertTrue(client.ping(), "Ping should work");
        System.out.println("✓ Step 5: Connectivity test passed");

        // Step 6: Verify server info
        assertNotNull(client.getServerInfo(), "Server info should be available");
        assertNotNull(client.getServerInfo().name(), "Server name should be available");
        System.out.println("✓ Step 6: Server info validated");

        System.out.println("🎉 Comprehensive smoke test completed successfully!");
        System.out.println("📊 Smoke Test Summary:");
        System.out.println("   - Initialization: ✅ PASSED");
        System.out.println("   - Add Tool: ✅ PASSED (5 + 7 = " + addResult + ")");
        System.out.println("   - Echo Tool: ✅ PASSED");
        System.out.println("   - Resources: ✅ PASSED (" + resourcesList.size() + " available)");
        System.out.println("   - Simple Prompt: ✅ PASSED");
        System.out.println("   - Complex Prompt: ✅ PASSED (with image content)");
        System.out.println("   - Resource Prompt: ✅ PASSED (with resource content)");
        System.out.println("   - Connectivity: ✅ PASSED");
        System.out.println("   - Server Info: ✅ PASSED");
        System.out.println("   🎯 All smoke tests passed - Library is functional!");
    }
}
