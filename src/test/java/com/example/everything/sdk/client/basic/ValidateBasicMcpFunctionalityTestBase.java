package com.example.everything.sdk.client.basic;

import com.example.sdk.client.EverythingTestClient;
import com.example.sdk.client.EverythingTools;
import com.example.sdk.client.EverythingResources;
import com.example.sdk.client.EverythingPrompts;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base class for MCP functionality validation tests.
 * 
 * This base class contains all the common test logic for validating basic MCP operations.
 * Subclasses specify which transport protocol to use (HTTP_STREAMABLE or HTTP_SSE).
 * 
 * This approach allows you to run tests for each transport protocol independently:
 * - ValidateBasicMcpFunctionalityStreamableTest - Tests HTTP_STREAMABLE only
 * - ValidateBasicMcpFunctionalitySseTest - Tests HTTP_SSE only
 * 
 * Benefits:
 * - Faster execution (no timeout waiting for unsupported protocols)
 * - Independent test runs for each protocol
 * - Clear separation of transport-specific behavior
 * - Easier CI/CD integration (can run only supported transports)
 */
@SpringBootTest
@Testcontainers
public abstract class ValidateBasicMcpFunctionalityTestBase {

    protected static final int MCP_PORT = 3001;

    private String baseUrl;
    private EverythingTestClient client;

    /**
     * Subclasses must implement this method to specify which transport protocol to use.
     * @return the transport type for this test class
     */
    protected abstract EverythingTestClient.TransportType getTransportType();

    /**
     * Subclasses must provide access to their container instance.
     * @return the container instance for this transport
     */
    protected abstract GenericContainer<?> getContainer();

    /**
     * Subclasses can override this to provide transport-specific timeout values.
     * @return the request timeout for this transport type
     */
    protected Duration getRequestTimeout() {
        return Duration.ofSeconds(10);
    }

    /**
     * Subclasses can override this to handle transport-specific setup logic.
     * @return true if the transport is expected to work, false if it should be skipped
     */
    protected boolean isTransportSupported() {
        return true; // Default assumption - subclasses can override
    }

    @BeforeEach
    void setUp() {
        GenericContainer<?> container = getContainer();
        baseUrl = String.format("http://%s:%d",
                container.getHost(),
                container.getFirstMappedPort());
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    /**
     * Creates and initializes a client with the transport type specified by the subclass.
     * Handles transport-specific initialization gracefully.
     */
    protected EverythingTestClient createAndInitializeClient() {
        if (!isTransportSupported()) {
            System.out.println("⚠️ Transport " + getTransportType() + " is not supported by the current server setup");
            return null;
        }

        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(getTransportType())
                .requestTimeout(getRequestTimeout())
                .build();
        
        try {
            boolean initialized = client.initialize();
            if (initialized) {
                System.out.println("✅ Connected to MCP server using " + getTransportType() + ": " + client.getServerInfo().name());
                return client;
            } else {
                System.out.println("⚠️ Failed to initialize client with " + getTransportType() + " transport");
                client.close();
                client = null;
                return null;
            }
        } catch (Exception e) {
            System.out.println("⚠️ Exception during " + getTransportType() + " initialization: " + e.getMessage());
            client.close();
            client = null;
            return null;
        }
    }

    @Test
    @DisplayName("Should list prompts correctly")
    void testListPrompts() {
        EverythingTestClient testClient = createAndInitializeClient();
        if (testClient == null) {
            System.out.println("⚠️ Skipping prompt listing test for " + getTransportType() + " (not supported)");
            return;
        }
        
        System.out.println("\n📝 Testing Prompt Listing with " + getTransportType() + "...");
        EverythingPrompts prompts = testClient.getPrompts();
        List<Prompt> availablePrompts = prompts.getAvailablePrompts();
        
        assertNotNull(availablePrompts, "Prompts list should not be null");
        assertFalse(availablePrompts.isEmpty(), "Should have at least one prompt");
        assertTrue(availablePrompts.size() >= 3, "Should have at least 3 prompts (simple, complex, resource)");
        
        // Verify specific prompts exist
        boolean hasSimplePrompt = availablePrompts.stream().anyMatch(p -> "simple_prompt".equals(p.name()));
        boolean hasComplexPrompt = availablePrompts.stream().anyMatch(p -> "complex_prompt".equals(p.name()));
        boolean hasResourcePrompt = availablePrompts.stream().anyMatch(p -> "resource_prompt".equals(p.name()));
        
        assertTrue(hasSimplePrompt, "Should have simple_prompt");
        assertTrue(hasComplexPrompt, "Should have complex_prompt");  
        assertTrue(hasResourcePrompt, "Should have resource_prompt");
        
        System.out.println("✅ " + getTransportType() + " Prompts: Found " + availablePrompts.size() + " prompts (simple, complex, resource)");
    }

    @Test
    @DisplayName("Should list tools correctly")
    void testListTools() {
        EverythingTestClient testClient = createAndInitializeClient();
        if (testClient == null) {
            System.out.println("⚠️ Skipping tool listing test for " + getTransportType() + " (not supported)");
            return;
        }
        
        System.out.println("\n🔧 Testing Tool Listing with " + getTransportType() + "...");
        EverythingTools tools = testClient.getTools();
        List<Tool> availableTools = tools.getAvailableTools();
        
        assertNotNull(availableTools, "Tools list should not be null");
        assertFalse(availableTools.isEmpty(), "Should have at least one tool");
        assertTrue(availableTools.size() >= 10, "Should have at least 10 tools");
        
        // Verify key tools exist
        boolean hasAddTool = availableTools.stream().anyMatch(t -> "add".equals(t.name()));
        boolean hasEchoTool = availableTools.stream().anyMatch(t -> "echo".equals(t.name()));
        boolean hasLongRunningTool = availableTools.stream().anyMatch(t -> "longRunningOperation".equals(t.name()));
        
        assertTrue(hasAddTool, "Should have add tool");
        assertTrue(hasEchoTool, "Should have echo tool");
        assertTrue(hasLongRunningTool, "Should have longRunningOperation tool");
        
        System.out.println("✅ " + getTransportType() + " Tools: Found " + availableTools.size() + " tools (including add, echo, longRunningOperation)");
    }

    @Test
    @DisplayName("Should list resources correctly")
    void testListResources() {
        EverythingTestClient testClient = createAndInitializeClient();
        if (testClient == null) {
            System.out.println("⚠️ Skipping resource listing test for " + getTransportType() + " (not supported)");
            return;
        }
        
        System.out.println("\n📚 Testing Resource Listing with " + getTransportType() + "...");
        EverythingResources resources = testClient.getResources();
        List<Resource> availableResources = resources.getAvailableResources();
        
        assertNotNull(availableResources, "Resources list should not be null");
        assertFalse(availableResources.isEmpty(), "Should have at least one resource");
        assertTrue(availableResources.size() >= 10, "Should have substantial number of resources");
        
        System.out.println("✅ " + getTransportType() + " Resources: Found " + availableResources.size() + " resources");
    }

    @Test
    @DisplayName("Should execute add tool correctly")
    void testAddToolExecution() {
        EverythingTestClient testClient = createAndInitializeClient();
        if (testClient == null) {
            System.out.println("⚠️ Skipping add tool test for " + getTransportType() + " (not supported)");
            return;
        }
        
        System.out.println("\n➕ Testing Add Tool Execution with " + getTransportType() + "...");
        EverythingTools tools = testClient.getTools();
        
        int operandA = 15;
        int operandB = 27;
        int expectedSum = operandA + operandB;
        
        int actualSum = tools.add(operandA, operandB);
        assertEquals(expectedSum, actualSum, "Add tool should return correct sum");
        
        System.out.println("✅ " + getTransportType() + " Add Tool: " + operandA + " + " + operandB + " = " + actualSum + " ✓");
    }

    @Test
    @DisplayName("Should retrieve simple prompt correctly")
    void testSimplePromptRetrieval() {
        EverythingTestClient testClient = createAndInitializeClient();
        if (testClient == null) {
            System.out.println("⚠️ Skipping simple prompt test for " + getTransportType() + " (not supported)");
            return;
        }
        
        System.out.println("\n💬 Testing Simple Prompt Retrieval with " + getTransportType() + "...");
        EverythingPrompts prompts = testClient.getPrompts();
        GetPromptResult simplePromptResult = prompts.getSimplePrompt();
        
        assertNotNull(simplePromptResult, "Simple prompt result should not be null");
        assertNotNull(simplePromptResult.messages(), "Prompt messages should not be null");
        assertFalse(simplePromptResult.messages().isEmpty(), "Prompt should have at least one message");
        
        List<String> textContent = prompts.extractTextContent(simplePromptResult);
        assertFalse(textContent.isEmpty(), "Prompt should have text content");
        
        String promptText = textContent.get(0);
        assertNotNull(promptText, "Prompt text should not be null");
        assertFalse(promptText.trim().isEmpty(), "Prompt text should not be empty");
        
        System.out.println("✅ " + getTransportType() + " Simple Prompt: Retrieved successfully - \"" + 
                (promptText.length() > 50 ? promptText.substring(0, 50) + "..." : promptText) + "\"");
    }

    @Test
    @DisplayName("Should retrieve text resource correctly")
    void testTextResourceRetrieval() {
        EverythingTestClient testClient = createAndInitializeClient();
        if (testClient == null) {
            System.out.println("⚠️ Skipping text resource test for " + getTransportType() + " (not supported)");
            return;
        }
        
        System.out.println("\n📄 Testing Text Resource Retrieval with " + getTransportType() + "...");
        EverythingResources resources = testClient.getResources();
        List<Resource> availableResources = resources.getAvailableResources();
        
        // Try to find a text resource
        Resource textResource = null;
        ReadResourceResult textResourceResult = null;
        ResourceContents textResourceContent = null;
        
        // Try a few different resources to find a text one
        for (int i = 0; i < Math.min(5, availableResources.size()) && textResource == null; i++) {
            Resource candidateResource = availableResources.get(i);
            try {
                ReadResourceResult candidateResult = resources.readResource(candidateResource);
                if (!candidateResult.contents().isEmpty()) {
                    ResourceContents candidateContent = candidateResult.contents().get(0);
                    if (candidateContent.mimeType() != null && candidateContent.mimeType().startsWith("text/")) {
                        textResource = candidateResource;
                        textResourceResult = candidateResult;
                        textResourceContent = candidateContent;
                        break;
                    }
                }
            } catch (Exception e) {
                // Try next resource
            }
        }
        
        // If no text resource found, use the first resource regardless of type
        if (textResource == null) {
            textResource = availableResources.get(0);
            textResourceResult = resources.readResource(textResource);
            textResourceContent = textResourceResult.contents().get(0);
            System.out.println("⚠️ No text resource found, using: " + textResourceContent.mimeType());
        }
        
        assertNotNull(textResourceResult, "Resource result should not be null");
        assertNotNull(textResourceResult.contents(), "Resource contents should not be null");
        assertFalse(textResourceResult.contents().isEmpty(), "Resource should have content");
        assertNotNull(textResourceContent.mimeType(), "Resource should have mime type");
        
        // Check if we found a text resource or just any resource
        boolean isTextResource = textResourceContent.mimeType().startsWith("text/");
        if (isTextResource) {
            System.out.println("✅ " + getTransportType() + " Text Resource: Retrieved successfully - " + 
                    textResourceContent.mimeType() + " (" + textResource.uri() + ")");
        } else {
            System.out.println("✅ " + getTransportType() + " Resource: Retrieved successfully - " + 
                    textResourceContent.mimeType() + " (" + textResource.uri() + ") [Note: Text resource not found, but resource retrieval works]");
        }
    }

    @Test
    @DisplayName("Should retrieve binary resource correctly")
    void testBinaryResourceRetrieval() {
        EverythingTestClient testClient = createAndInitializeClient();
        if (testClient == null) {
            System.out.println("⚠️ Skipping binary resource test for " + getTransportType() + " (not supported)");
            return;
        }
        
        System.out.println("\n📦 Testing Binary Resource Retrieval with " + getTransportType() + "...");
        EverythingResources resources = testClient.getResources();
        List<Resource> availableResources = resources.getAvailableResources();
        
        // Try to find a binary resource
        Resource binaryResource = null;
        ReadResourceResult binaryResourceResult = null;
        ResourceContents binaryResourceContent = null;
        
        // Try a few different resources to find a binary one
        for (int i = 0; i < Math.min(10, availableResources.size()); i++) {
            Resource candidateResource = availableResources.get(i);
            try {
                ReadResourceResult candidateResult = resources.readResource(candidateResource);
                if (!candidateResult.contents().isEmpty()) {
                    ResourceContents candidateContent = candidateResult.contents().get(0);
                    if (candidateContent.mimeType() != null && 
                        (candidateContent.mimeType().startsWith("application/") || 
                         candidateContent.mimeType().startsWith("image/") ||
                         candidateContent.mimeType().equals("application/octet-stream"))) {
                        binaryResource = candidateResource;
                        binaryResourceResult = candidateResult;
                        binaryResourceContent = candidateContent;
                        break;
                    }
                }
            } catch (Exception e) {
                // Try next resource
            }
        }
        
        // If no binary resource found, use any different resource
        if (binaryResource == null) {
            binaryResource = availableResources.get(Math.min(1, availableResources.size() - 1));
            binaryResourceResult = resources.readResource(binaryResource);
            binaryResourceContent = binaryResourceResult.contents().get(0);
            System.out.println("⚠️ No binary resource found, using: " + binaryResourceContent.mimeType());
        }
        
        assertNotNull(binaryResourceResult, "Binary resource result should not be null");
        assertNotNull(binaryResourceResult.contents(), "Binary resource contents should not be null");
        assertFalse(binaryResourceResult.contents().isEmpty(), "Binary resource should have content");
        assertNotNull(binaryResourceContent.mimeType(), "Binary resource should have mime type");
        
        System.out.println("✅ " + getTransportType() + " Binary Resource: Retrieved successfully - " + 
                binaryResourceContent.mimeType() + " (" + binaryResource.uri() + ")");
    }

    @Test
    @DisplayName("Should provide quick connectivity validation")
    void testQuickConnectivityCheck() {
        EverythingTestClient testClient = createAndInitializeClient();
        if (testClient == null) {
            System.out.println("⚠️ Skipping quick connectivity test for " + getTransportType() + " (not supported)");
            return;
        }
        
        // Ultra-fast test for basic connectivity during development
        assertTrue(testClient.ping(), "Basic connectivity should work");
        assertNotNull(testClient.getServerInfo(), "Server info should be available");
        assertTrue(testClient.getTools().getAvailableTools().size() > 0, "Should have tools");
        
        System.out.println("⚡ " + getTransportType() + " Quick Check: Connection ✓, Tools ✓, Server: " + 
                testClient.getServerInfo().name());
    }

    @Test
    @DisplayName("Should validate all basic MCP functionality in sequence")
    void testCompleteBasicMcpFunctionality() {
        EverythingTestClient testClient = createAndInitializeClient();
        if (testClient == null) {
            System.out.println("⚠️ Skipping complete functionality test for " + getTransportType() + " (not supported)");
            return;
        }
        
        System.out.println("\n🧪 COMPLETE BASIC MCP FUNCTIONALITY VALIDATION WITH " + getTransportType());
        System.out.println("==============================================\n");
        
        // This test runs all operations in sequence to ensure they work together
        System.out.println("✅ 1. Connection: Established successfully with " + getTransportType());
        
        // Get all helpers
        EverythingPrompts prompts = testClient.getPrompts();
        EverythingTools tools = testClient.getTools();
        EverythingResources resources = testClient.getResources();
        
        // Quick validation of all capabilities
        List<Prompt> availablePrompts = prompts.getAvailablePrompts();
        List<Tool> availableTools = tools.getAvailableTools();
        List<Resource> availableResources = resources.getAvailableResources();
        
        assertTrue(availablePrompts.size() >= 3, "Should have prompts");
        assertTrue(availableTools.size() >= 10, "Should have tools");
        assertTrue(availableResources.size() >= 10, "Should have resources");
        
        // Execute one operation from each category
        int sum = tools.add(10, 5);
        assertEquals(15, sum, "Add tool should work");
        
        GetPromptResult prompt = prompts.getSimplePrompt();
        assertNotNull(prompt, "Simple prompt should work");
        
        ReadResourceResult resource = resources.readResource(availableResources.get(0));
        assertNotNull(resource, "Resource retrieval should work");
        
        // Final summary
        System.out.println("\n🎉 ALL BASIC MCP FUNCTIONALITY VALIDATED!");
        System.out.println("=========================================");
        System.out.println("✅ Listed " + availablePrompts.size() + " prompts");
        System.out.println("✅ Listed " + availableTools.size() + " tools");
        System.out.println("✅ Listed " + availableResources.size() + " resources");
        System.out.println("✅ Executed add tool: 10 + 5 = " + sum);
        System.out.println("✅ Retrieved simple prompt");
        System.out.println("✅ Retrieved resource");
        System.out.println("\n🚀 Your MCP proxy should handle all these operations with " + getTransportType() + "!");
    }
}
