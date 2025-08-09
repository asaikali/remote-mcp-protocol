package com.example.everything.sdk.client.foundation;

import com.example.sdk.client.EverythingTestClient;
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
 * Foundation Layer Test: Connection Lifecycle
 * 
 * Tests the basic MCP connection lifecycle: Initialize → Use → Close
 * This validates proper protocol handshake, session management, and cleanup.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Connection Lifecycle Tests")
class ConnectionLifecycleTest {

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
    @DisplayName("Should successfully complete full connection lifecycle")
    void testFullConnectionLifecycle() {
        EverythingTestClient client = null;
        
        try {
            // Step 1: Create client (no connection yet)
            client = EverythingTestClient.builder()
                    .baseUrl(baseUrl)
                    .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                    .build();
            
            assertNotNull(client, "Client should be created");
            assertFalse(client.isInitialized(), "Client should not be initialized yet");
            
            // Step 2: Initialize connection
            boolean initialized = client.initialize();
            assertTrue(initialized, "Client should initialize successfully");
            assertTrue(client.isInitialized(), "Client should report as initialized");
            
            // Step 3: Use connection (basic operation)
            assertNotNull(client.getServerInfo(), "Should be able to get server info");
            assertTrue(client.ping(), "Should be able to ping server");
            
            // Step 4: Verify connection is still active
            int addResult = client.getTools().add(10, 5);
            assertEquals(15, addResult, "Should be able to perform operations");
            
            // Step 5: Close connection
            client.close();
            
            System.out.println("✅ Full connection lifecycle test passed");
            
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    @Test
    @DisplayName("Should handle multiple initialize calls gracefully")
    void testMultipleInitializeCalls() {
        try (EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build()) {
            
            // First initialize
            assertTrue(client.initialize(), "First initialize should succeed");
            assertTrue(client.isInitialized(), "Client should be initialized");
            
            // Second initialize - should be handled gracefully
            boolean secondInit = client.initialize();
            assertTrue(secondInit, "Second initialize should not fail");
            assertTrue(client.isInitialized(), "Client should still be initialized");
            
            // Should still be functional
            assertTrue(client.ping(), "Client should still work after multiple initializations");
            
            System.out.println("✅ Multiple initialize calls test passed");
        }
    }

    @Test
    @DisplayName("Should handle operations after close gracefully")
    void testOperationsAfterClose() {
        EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build();
        
        // Initialize and verify it works
        assertTrue(client.initialize(), "Client should initialize");
        assertTrue(client.ping(), "Ping should work before close");
        
        // Close the client
        client.close();
        
        // Operations after close should fail gracefully
        assertThrows(RuntimeException.class, client::ping, 
                "Operations after close should throw exception");
        
        System.out.println("✅ Operations after close test passed");
    }

    @Test
    @DisplayName("Should handle connection without initialization")
    void testOperationsWithoutInitialization() {
        try (EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build()) {
            
            assertFalse(client.isInitialized(), "Client should not be initialized");
            
            // Operations without initialization should fail
            assertThrows(RuntimeException.class, client::ping, 
                    "Operations without initialization should fail");
            
            assertThrows(RuntimeException.class, client::getServerInfo, 
                    "Server info should not be available without initialization");
            
            System.out.println("✅ Operations without initialization test passed");
        }
    }

    @Test
    @DisplayName("Should handle both transport types in lifecycle")
    void testLifecycleWithDifferentTransports() {
        // Test HTTP Streamable
        try (EverythingTestClient streamableClient = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build()) {
            
            assertTrue(streamableClient.initialize(), "Streamable client should initialize");
            assertTrue(streamableClient.ping(), "Streamable client should work");
            assertEquals(10, streamableClient.getTools().add(4, 6), "Streamable client should perform operations");
        }
        
        // Test HTTP SSE (if supported by the server - our container only supports Streamable)
        try (EverythingTestClient sseClient = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_SSE)
                .requestTimeout(Duration.ofSeconds(5)) // Short timeout for SSE test
                .build()) {
            
            // SSE might not be supported by the current server setup
            try {
                boolean sseInitialized = sseClient.initialize();
                if (sseInitialized) {
                    assertTrue(sseClient.ping(), "SSE client should work if initialized");
                    assertEquals(15, sseClient.getTools().add(7, 8), "SSE client should perform operations");
                    System.out.println("✅ SSE transport supported and working");
                } else {
                    System.out.println("⚠️ SSE transport not supported by current server setup");
                }
            } catch (RuntimeException e) {
                // SSE might not be supported - this is acceptable for this test
                System.out.println("⚠️ SSE transport failed (expected if not supported): " + e.getMessage());
            }
        }
        
        System.out.println("✅ Transport types lifecycle test completed");
    }

    @Test
    @DisplayName("Should maintain session state throughout lifecycle")
    void testSessionStatePersistence() {
        try (EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build()) {
            
            // Initialize
            assertTrue(client.initialize(), "Client should initialize");
            
            // Get server info (should be cached)
            var serverInfo1 = client.getServerInfo();
            assertNotNull(serverInfo1, "Server info should be available");
            
            // Perform operations
            client.getTools().echo("test message 1");
            client.getTools().add(1, 2);
            
            // Get server info again (should still be available)
            var serverInfo2 = client.getServerInfo();
            assertNotNull(serverInfo2, "Server info should still be available");
            assertEquals(serverInfo1.name(), serverInfo2.name(), "Server info should be consistent");
            
            // More operations
            client.getResources().getAvailableResources();
            client.getPrompts().getSimplePrompt();
            
            // Final verification
            assertTrue(client.ping(), "Client should still be functional");
            
            System.out.println("✅ Session state persistence test passed");
        }
    }
}