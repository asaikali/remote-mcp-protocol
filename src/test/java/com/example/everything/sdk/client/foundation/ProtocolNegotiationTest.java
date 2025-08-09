package com.example.everything.sdk.client.foundation;

import com.example.sdk.client.EverythingTestClient;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Foundation Layer Test: Protocol Negotiation
 * 
 * Tests protocol version negotiation and capability matching between client and server.
 * Validates that the MCP handshake process works correctly under various scenarios.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Protocol Negotiation Tests")
class ProtocolNegotiationTest {

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

    private String baseUrl;

    @BeforeEach
    void setUp() {
        baseUrl = String.format("http://%s:%d",
                mcpContainer.getHost(),
                mcpContainer.getFirstMappedPort());
    }

    @Test
    @DisplayName("Should negotiate protocol version successfully")
    void testProtocolVersionNegotiation() {
        try (EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build()) {
            
            // Initialize should succeed with version negotiation
            assertTrue(client.initialize(), "Protocol negotiation should succeed");
            
            // Verify we can get server info (indicating successful negotiation)
            var serverInfo = client.getServerInfo();
            assertNotNull(serverInfo, "Server info should be available after negotiation");
            assertNotNull(serverInfo.version(), "Server should have version info");
            
            // Verify basic functionality works (indicating compatible protocol)
            assertTrue(client.ping(), "Basic operations should work after negotiation");
            
            System.out.println("✅ Protocol version negotiation test passed");
            System.out.println("📋 Server version: " + serverInfo.version());
        }
    }

    @Test
    @DisplayName("Should handle capability negotiation correctly")
    void testCapabilityNegotiation() {
        try (EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build()) {
            
            assertTrue(client.initialize(), "Initialization should succeed");
            
            // Test that negotiated capabilities work
            
            // Test tools capability
            assertDoesNotThrow(() -> client.getTools().getAvailableTools(), 
                    "Tools capability should be available");
            
            // Test resources capability  
            assertDoesNotThrow(() -> client.getResources().getAvailableResources(), 
                    "Resources capability should be available");
            
            // Test prompts capability
            assertDoesNotThrow(() -> client.getPrompts().getAvailablePrompts(), 
                    "Prompts capability should be available");
            
            // Verify we can actually use the capabilities
            assertTrue(client.getTools().getAvailableTools().size() > 0, 
                    "Tools should be available");
            assertTrue(client.getResources().getAvailableResources().size() > 0, 
                    "Resources should be available");
            assertTrue(client.getPrompts().getAvailablePrompts().size() > 0, 
                    "Prompts should be available");
            
            System.out.println("✅ Capability negotiation test passed");
            System.out.println("📋 Tools available: " + client.getTools().getAvailableTools().size());
            System.out.println("📋 Resources available: " + client.getResources().getAvailableResources().size());
            System.out.println("📋 Prompts available: " + client.getPrompts().getAvailablePrompts().size());
        }
    }

    @Test
    @DisplayName("Should validate server capabilities match expected features")
    void testServerCapabilityValidation() {
        try (EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build()) {
            
            assertTrue(client.initialize(), "Initialization should succeed");
            
            // Validate expected tools from Everything server
            var tools = client.getTools().getAvailableTools();
            Set<String> toolNames = Set.of(
                    "echo", "add", "longRunningOperation", "printEnv", "sampleLLM", 
                    "getTinyImage", "annotatedMessage", "getResourceReference", 
                    "startElicitation", "getResourceLinks", "structuredContent"
            );
            
            long matchingTools = tools.stream()
                    .map(Tool::name)
                    .filter(toolNames::contains)
                    .count();
            
            assertTrue(matchingTools >= 10, 
                    "Should have most expected tools available (found " + matchingTools + ")");
            
            // Validate expected prompts
            var prompts = client.getPrompts().getAvailablePrompts();
            Set<String> promptNames = Set.of("simple_prompt", "complex_prompt", "resource_prompt");
            
            long matchingPrompts = prompts.stream()
                    .map(Prompt::name)
                    .filter(promptNames::contains)
                    .count();
            
            assertEquals(3, matchingPrompts, "Should have all 3 expected prompts");
            
            // Validate resources (Everything server has 100 resources)
            var resources = client.getResources().getAvailableResources();
            assertTrue(resources.size() >= 10, 
                    "Should have substantial number of resources (found " + resources.size() + ")");
            
            System.out.println("✅ Server capability validation test passed");
            System.out.println("📋 Matching tools: " + matchingTools + "/" + toolNames.size());
            System.out.println("📋 Matching prompts: " + matchingPrompts + "/" + promptNames.size());
            System.out.println("📋 Total resources: " + resources.size());
        }
    }

    @Test
    @DisplayName("Should handle connection with minimal capabilities")
    void testMinimalCapabilityConnection() {
        try (EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build()) {
            
            // Even with minimal capabilities, basic connection should work
            assertTrue(client.initialize(), "Connection with minimal capabilities should succeed");
            
            // Basic server info should always be available
            assertNotNull(client.getServerInfo(), "Server info should be available");
            
            // Ping should work as a basic connectivity test
            assertTrue(client.ping(), "Ping should work with minimal capabilities");
            
            System.out.println("✅ Minimal capability connection test passed");
        }
    }

    @Test
    @DisplayName("Should handle protocol negotiation timing correctly")
    void testNegotiationTiming() {
        try (EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .requestTimeout(Duration.ofSeconds(10)) // Set reasonable timeout
                .build()) {
            
            long startTime = System.currentTimeMillis();
            
            assertTrue(client.initialize(), "Negotiation should complete within timeout");
            
            long negotiationTime = System.currentTimeMillis() - startTime;
            
            // Negotiation should be reasonably fast (less than 5 seconds)
            assertTrue(negotiationTime < 5000, 
                    "Protocol negotiation should complete quickly (took " + negotiationTime + "ms)");
            
            // Verify immediate functionality after negotiation
            assertNotNull(client.getServerInfo(), "Server info should be immediately available");
            
            System.out.println("✅ Negotiation timing test passed");
            System.out.println("📋 Negotiation completed in: " + negotiationTime + "ms");
        }
    }

    @Test
    @DisplayName("Should maintain protocol compliance after negotiation")
    void testPostNegotiationCompliance() {
        try (EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build()) {
            
            assertTrue(client.initialize(), "Initialization should succeed");
            
            // Test various operations to ensure protocol compliance
            
            // 1. Tool operations should follow MCP protocol
            assertDoesNotThrow(() -> {
                int result = client.getTools().add(5, 3);
                assertEquals(8, result, "Tool operations should work correctly");
            }, "Tool operations should be protocol compliant");
            
            // 2. Resource operations should follow MCP protocol
            assertDoesNotThrow(() -> {
                var resources = client.getResources().getAvailableResources();
                if (!resources.isEmpty()) {
                    var resourceContent = client.getResources().readResource(resources.get(0));
                    assertNotNull(resourceContent, "Resource reading should work");
                }
            }, "Resource operations should be protocol compliant");
            
            // 3. Prompt operations should follow MCP protocol
            assertDoesNotThrow(() -> {
                var simplePrompt = client.getPrompts().getSimplePrompt();
                assertNotNull(simplePrompt, "Prompt operations should work");
                assertFalse(simplePrompt.messages().isEmpty(), "Prompts should return messages");
            }, "Prompt operations should be protocol compliant");
            
            System.out.println("✅ Post-negotiation compliance test passed");
        }
    }
}