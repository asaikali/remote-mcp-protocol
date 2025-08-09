package com.example.everything.sdk.client.foundation;

import com.example.sdk.client.EverythingTestClient;
import com.example.sdk.client.EverythingTools;
import com.example.sdk.client.EverythingResources;
import com.example.sdk.client.EverythingPrompts;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Foundation Layer Test: Error Handling
 * 
 * Tests proper handling of invalid requests, malformed data, and error conditions.
 * Validates that the client library gracefully handles various failure scenarios.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Error Handling Tests")
class ErrorHandlingTest {

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
    private EverythingTestClient client;

    @BeforeEach
    void setUp() {
        baseUrl = String.format("http://%s:%d",
                mcpContainer.getHost(),
                mcpContainer.getFirstMappedPort());
        
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build();
        
        assertTrue(client.initialize(), "Client should initialize for error tests");
    }

    @Test
    @DisplayName("Should handle invalid tool names gracefully")
    void testInvalidToolNames() {
        EverythingTools tools = client.getTools();
        
        // Test non-existent tool
        assertThrows(RuntimeException.class, () -> {
            tools.callTool("nonExistentTool", null);
        }, "Should throw exception for non-existent tool");
        
        // Test empty tool name
        assertThrows(RuntimeException.class, () -> {
            tools.callTool("", null);
        }, "Should throw exception for empty tool name");
        
        // Test null tool name
        assertThrows(RuntimeException.class, () -> {
            tools.callTool(null, null);
        }, "Should throw exception for null tool name");
        
        System.out.println("✅ Invalid tool names test passed");
    }

    @Test
    @DisplayName("Should handle invalid tool arguments")
    void testInvalidToolArguments() {
        EverythingTools tools = client.getTools();
        
        // Test add tool with invalid types (strings instead of numbers)
        Exception exception = assertThrows(RuntimeException.class, () -> {
            // This should fail - add expects numbers but we're not providing valid numeric arguments
            tools.callTool("add", java.util.Map.of("a", "not_a_number", "b", "also_not_a_number"));
        });
        assertNotNull(exception.getMessage(), "Exception should have a message");
        
        // Test missing required arguments
        assertThrows(RuntimeException.class, () -> {
            tools.callTool("add", java.util.Map.of("a", 5)); // Missing 'b' argument
        }, "Should throw exception for missing required arguments");
        
        // Test null arguments for tool that requires them
        assertThrows(RuntimeException.class, () -> {
            tools.callTool("add", null);
        }, "Should throw exception for null arguments when required");
        
        System.out.println("✅ Invalid tool arguments test passed");
    }

    @Test
    @DisplayName("Should handle invalid resource operations")
    void testInvalidResourceOperations() {
        EverythingResources resources = client.getResources();
        
        // Test non-existent resource URI
        assertThrows(RuntimeException.class, () -> {
            resources.readResource("test://invalid/resource/999");
        }, "Should throw exception for non-existent resource");
        
        // Test malformed resource URI
        assertThrows(RuntimeException.class, () -> {
            resources.readResource("invalid-uri-format");
        }, "Should throw exception for malformed resource URI");
        
        // Test null resource URI
        assertThrows(RuntimeException.class, () -> {
            resources.readResource((String) null);
        }, "Should throw exception for null resource URI");
        
        // Test empty resource URI
        assertThrows(RuntimeException.class, () -> {
            resources.readResource("");
        }, "Should throw exception for empty resource URI");
        
        System.out.println("✅ Invalid resource operations test passed");
    }

    @Test
    @DisplayName("Should handle invalid prompt operations")
    void testInvalidPromptOperations() {
        EverythingPrompts prompts = client.getPrompts();
        
        // Test non-existent prompt
        assertThrows(RuntimeException.class, () -> {
            prompts.getPrompt("nonExistentPrompt", null);
        }, "Should throw exception for non-existent prompt");
        
        // Test null prompt name
        assertThrows(RuntimeException.class, () -> {
            prompts.getPrompt(null, null);
        }, "Should throw exception for null prompt name");
        
        // Test empty prompt name
        assertThrows(RuntimeException.class, () -> {
            prompts.getPrompt("", null);
        }, "Should throw exception for empty prompt name");
        
        // Test invalid arguments for resource prompt
        assertThrows(RuntimeException.class, () -> {
            prompts.getResourcePrompt(0); // Resource ID should be 1-100
        }, "Should throw exception for invalid resource ID (too low)");
        
        assertThrows(RuntimeException.class, () -> {
            prompts.getResourcePrompt(101); // Resource ID should be 1-100
        }, "Should throw exception for invalid resource ID (too high)");
        
        System.out.println("✅ Invalid prompt operations test passed");
    }

    @Test
    @DisplayName("Should handle invalid connection parameters")
    void testInvalidConnectionParameters() {
        // Test invalid URL
        assertThrows(RuntimeException.class, () -> {
            try (EverythingTestClient invalidClient = EverythingTestClient.builder()
                    .baseUrl("invalid-url-format")
                    .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                    .build()) {
                invalidClient.initialize();
            }
        }, "Should throw exception for invalid URL format");
        
        // Test unreachable URL
        assertThrows(RuntimeException.class, () -> {
            try (EverythingTestClient unreachableClient = EverythingTestClient.builder()
                    .baseUrl("http://localhost:99999") // Unlikely to be in use
                    .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                    .requestTimeout(Duration.ofSeconds(2)) // Short timeout
                    .build()) {
                unreachableClient.initialize();
            }
        }, "Should throw exception for unreachable server");
        
        System.out.println("✅ Invalid connection parameters test passed");
    }

    @Test
    @DisplayName("Should handle boundary value errors")
    void testBoundaryValueErrors() {
        EverythingTools tools = client.getTools();
        EverythingPrompts prompts = client.getPrompts();
        
        // Test extremely large numbers for add tool
        assertThrows(RuntimeException.class, () -> {
            tools.add(Integer.MAX_VALUE, 1); // Should overflow
        }, "Should handle integer overflow gracefully");
        
        // Test negative duration for long running operation
        assertThrows(RuntimeException.class, () -> {
            tools.longRunningOperation(-1, 1); // Negative duration should be invalid
        }, "Should reject negative duration");
        
        // Test zero steps for long running operation
        assertThrows(RuntimeException.class, () -> {
            tools.longRunningOperation(1, 0); // Zero steps should be invalid
        }, "Should reject zero steps");
        
        System.out.println("✅ Boundary value errors test passed");
    }

    @Test
    @DisplayName("Should provide meaningful error messages")
    void testErrorMessageQuality() {
        EverythingTools tools = client.getTools();
        EverythingResources resources = client.getResources();
        EverythingPrompts prompts = client.getPrompts();
        
        // Test that error messages are informative
        Exception toolException = assertThrows(RuntimeException.class, () -> {
            tools.callTool("invalidTool", null);
        });
        assertNotNull(toolException.getMessage(), "Tool error should have a message");
        assertFalse(toolException.getMessage().trim().isEmpty(), "Tool error message should not be empty");
        
        Exception resourceException = assertThrows(RuntimeException.class, () -> {
            resources.readResource("invalid://resource");
        });
        assertNotNull(resourceException.getMessage(), "Resource error should have a message");
        assertFalse(resourceException.getMessage().trim().isEmpty(), "Resource error message should not be empty");
        
        Exception promptException = assertThrows(RuntimeException.class, () -> {
            prompts.getPrompt("invalidPrompt", null);
        });
        assertNotNull(promptException.getMessage(), "Prompt error should have a message");
        assertFalse(promptException.getMessage().trim().isEmpty(), "Prompt error message should not be empty");
        
        System.out.println("✅ Error message quality test passed");
        System.out.println("📋 Tool error sample: " + toolException.getMessage());
        System.out.println("📋 Resource error sample: " + resourceException.getMessage());
        System.out.println("📋 Prompt error sample: " + promptException.getMessage());
    }

    @Test
    @DisplayName("Should handle concurrent error scenarios")
    void testConcurrentErrors() {
        EverythingTools tools = client.getTools();
        
        // Test multiple invalid operations concurrently
        assertAll("Multiple concurrent errors should be handled independently",
                () -> assertThrows(RuntimeException.class, () -> tools.callTool("invalid1", null)),
                () -> assertThrows(RuntimeException.class, () -> tools.callTool("invalid2", null)),
                () -> assertThrows(RuntimeException.class, () -> tools.callTool("invalid3", null))
        );
        
        // Verify that client is still functional after errors
        assertTrue(client.ping(), "Client should still be functional after concurrent errors");
        assertEquals(10, tools.add(4, 6), "Valid operations should still work after errors");
        
        System.out.println("✅ Concurrent error scenarios test passed");
    }

    @Test
    @DisplayName("Should maintain stability after repeated errors")
    void testRepeatedErrorStability() {
        EverythingTools tools = client.getTools();
        
        // Generate multiple errors in sequence
        for (int i = 0; i < 5; i++) {
            final int index = i;
            assertThrows(RuntimeException.class, () -> {
                tools.callTool("invalid" + index, null);
            }, "Each error should be handled consistently");
        }
        
        // Verify client stability
        assertTrue(client.ping(), "Client should remain stable after repeated errors");
        assertEquals(15, tools.add(7, 8), "Client should still perform valid operations");
        
        // Test that we can still access other services
        assertFalse(client.getResources().getAvailableResources().isEmpty(), 
                "Resources should still be accessible");
        assertFalse(client.getPrompts().getAvailablePrompts().isEmpty(), 
                "Prompts should still be accessible");
        
        System.out.println("✅ Repeated error stability test passed");
    }
    
    @Test
    @DisplayName("Should handle partial response scenarios")
    void testPartialResponseHandling() {
        // This test would be enhanced with network simulation in Phase 2
        // For now, we test what we can with current infrastructure
        
        EverythingTools tools = client.getTools();
        
        // Test operations that might have varying response sizes
        assertDoesNotThrow(() -> {
            String result = tools.echo("Short message");
            assertNotNull(result, "Short echo should work");
        }, "Short responses should be handled correctly");
        
        assertDoesNotThrow(() -> {
            String longMessage = "This is a much longer message that tests the handling of " +
                    "larger responses from the server. ".repeat(10);
            String result = tools.echo(longMessage);
            assertNotNull(result, "Long echo should work");
            assertTrue(result.contains("This is a much longer message"), "Long response should be complete");
        }, "Longer responses should be handled correctly");
        
        System.out.println("✅ Partial response handling test passed");
    }
}