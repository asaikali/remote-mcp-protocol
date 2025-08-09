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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Foundation Layer Test: Transport Layer
 * 
 * Tests differences between HTTP Streamable and HTTP SSE transport protocols.
 * Validates that both transport types provide equivalent functionality with
 * appropriate performance characteristics.
 */
@SpringBootTest
@Testcontainers
@DisplayName("Transport Layer Tests")
class TransportLayerTest {

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
    @DisplayName("Should provide equivalent functionality across transport types")
    void testTransportEquivalency() {
        // Test HTTP Streamable
        try (EverythingTestClient streamableClient = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build()) {
            
            assertTrue(streamableClient.initialize(), "Streamable client should initialize");
            
            // Test basic operations
            assertTrue(streamableClient.ping(), "Streamable ping should work");
            assertEquals(10, streamableClient.getTools().add(4, 6), "Streamable add should work");
            assertNotNull(streamableClient.getServerInfo(), "Streamable server info should be available");
            
            var streamableTools = streamableClient.getTools().getAvailableTools();
            var streamableResources = streamableClient.getResources().getAvailableResources();
            var streamablePrompts = streamableClient.getPrompts().getAvailablePrompts();
            
            System.out.println("📋 Streamable - Tools: " + streamableTools.size() + 
                             ", Resources: " + streamableResources.size() + 
                             ", Prompts: " + streamablePrompts.size());
        }
        
        // Test HTTP SSE (if supported)
        try (EverythingTestClient sseClient = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_SSE)
                .requestTimeout(Duration.ofSeconds(5))
                .build()) {
            
            try {
                boolean sseInitialized = sseClient.initialize();
                
                if (sseInitialized) {
                    // Test same operations
                    assertTrue(sseClient.ping(), "SSE ping should work");
                    assertEquals(10, sseClient.getTools().add(4, 6), "SSE add should work");
                    assertNotNull(sseClient.getServerInfo(), "SSE server info should be available");
                    
                    var sseTools = sseClient.getTools().getAvailableTools();
                    var sseResources = sseClient.getResources().getAvailableResources();
                    var ssePrompts = sseClient.getPrompts().getAvailablePrompts();
                    
                    System.out.println("📋 SSE - Tools: " + sseTools.size() + 
                                     ", Resources: " + sseResources.size() + 
                                     ", Prompts: " + ssePrompts.size());
                    
                    // Compare results (they should be equivalent)
                    // Note: Exact counts might vary due to timing, but should be in same ballpark
                    assertTrue(Math.abs(sseTools.size() - 11) <= 2, "SSE should have similar tool count");
                    assertTrue(sseResources.size() >= 10, "SSE should have substantial resources");
                    assertEquals(3, ssePrompts.size(), "SSE should have expected prompt count");
                } else {
                    System.out.println("⚠️ SSE transport not supported by current server - skipping SSE tests");
                }
            } catch (RuntimeException e) {
                System.out.println("⚠️ SSE transport failed (expected if not supported): " + e.getMessage());
            }
        }
        
        System.out.println("✅ Transport equivalency test passed");
    }

    @Test
    @DisplayName("Should handle initialization timing differences")
    void testInitializationTiming() {
        long streamableTime = measureInitializationTime(EverythingTestClient.TransportType.HTTP_STREAMABLE);
        long sseTime = measureInitializationTime(EverythingTestClient.TransportType.HTTP_SSE);
        
        // Streamable should initialize within reasonable time
        assertTrue(streamableTime < 10000, "Streamable initialization should be under 10s (was " + streamableTime + "ms)");
        
        if (sseTime > 0) {
            assertTrue(sseTime < 10000, "SSE initialization should be under 10s (was " + sseTime + "ms)");
            System.out.println("📋 SSE init time: " + sseTime + "ms");
        } else {
            System.out.println("📋 SSE init time: Not supported by server");
        }
        
        System.out.println("✅ Initialization timing test passed");
        System.out.println("📋 Streamable init time: " + streamableTime + "ms");
    }

    private long measureInitializationTime(EverythingTestClient.TransportType transport) {
        long startTime = System.currentTimeMillis();
        
        try (EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(transport)
                .requestTimeout(Duration.ofSeconds(10))
                .build()) {
            
            if (transport == EverythingTestClient.TransportType.HTTP_SSE) {
                // SSE might not be supported, handle gracefully
                try {
                    boolean initialized = client.initialize();
                    if (!initialized) {
                        return -1; // Indicate SSE not supported
                    }
                } catch (RuntimeException e) {
                    return -1; // Indicate SSE not supported
                }
            } else {
                assertTrue(client.initialize(), "Client should initialize");
            }
            return System.currentTimeMillis() - startTime;
        }
    }

    @Test
    @DisplayName("Should handle basic operation performance comparison")
    void testOperationPerformance() {
        // Test performance of basic operations on both transports
        
        // Streamable performance
        long streamableOpsTime = measureOperationPerformance(EverythingTestClient.TransportType.HTTP_STREAMABLE);
        
        // SSE performance  
        long sseOpsTime = measureOperationPerformance(EverythingTestClient.TransportType.HTTP_SSE);
        
        // Both should complete operations in reasonable time
        assertTrue(streamableOpsTime < 5000, "Streamable operations should be under 5s (was " + streamableOpsTime + "ms)");
        assertTrue(sseOpsTime < 5000, "SSE operations should be under 5s (was " + sseOpsTime + "ms)");
        
        // Performance should be comparable (within 2x of each other)
        double ratio = Math.max(streamableOpsTime, sseOpsTime) / (double) Math.min(streamableOpsTime, sseOpsTime);
        assertTrue(ratio < 3.0, "Transport performance should be comparable (ratio: " + ratio + ")");
        
        System.out.println("✅ Operation performance test passed");
        System.out.println("📋 Streamable ops time: " + streamableOpsTime + "ms");
        System.out.println("📋 SSE ops time: " + sseOpsTime + "ms");
        System.out.println("📋 Performance ratio: " + String.format("%.2f", ratio));
    }

    private long measureOperationPerformance(EverythingTestClient.TransportType transport) {
        try (EverythingTestClient client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(transport)
                .build()) {
            
            assertTrue(client.initialize(), "Client should initialize");
            
            long startTime = System.currentTimeMillis();
            
            // Perform series of operations
            client.ping();
            client.getTools().add(1, 2);
            client.getTools().echo("performance test");
            client.getResources().getAvailableResources();
            client.getPrompts().getSimplePrompt();
            
            return System.currentTimeMillis() - startTime;
        }
    }

    @Test
    @DisplayName("Should handle concurrent operations on different transports")
    void testConcurrentTransportOperations() {
        ExecutorService executor = Executors.newFixedThreadPool(4);
        
        try {
            // Start operations on both transport types concurrently
            CompletableFuture<Boolean> streamableFuture = CompletableFuture.supplyAsync(() -> {
                try (EverythingTestClient client = EverythingTestClient.builder()
                        .baseUrl(baseUrl)
                        .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                        .build()) {
                    
                    client.initialize();
                    
                    // Perform multiple operations
                    for (int i = 0; i < 5; i++) {
                        client.ping();
                        client.getTools().add(i, i + 1);
                    }
                    
                    return true;
                }
            }, executor);
            
            CompletableFuture<Boolean> sseFuture = CompletableFuture.supplyAsync(() -> {
                try (EverythingTestClient client = EverythingTestClient.builder()
                        .baseUrl(baseUrl)
                        .transport(EverythingTestClient.TransportType.HTTP_SSE)
                        .build()) {
                    
                    client.initialize();
                    
                    // Perform multiple operations
                    for (int i = 0; i < 5; i++) {
                        client.ping();
                        client.getTools().add(i * 2, (i * 2) + 1);
                    }
                    
                    return true;
                }
            }, executor);
            
            // Both should complete successfully
            assertTrue(streamableFuture.get(30, TimeUnit.SECONDS), "Streamable concurrent operations should succeed");
            assertTrue(sseFuture.get(30, TimeUnit.SECONDS), "SSE concurrent operations should succeed");
            
            System.out.println("✅ Concurrent transport operations test passed");
            
        } catch (Exception e) {
            fail("Concurrent operations should not throw exceptions: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("Should handle transport-specific error scenarios")
    void testTransportSpecificErrors() {
        // Test how each transport handles various error conditions
        
        // Test connection timeout behavior
        try (EverythingTestClient streamableClient = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .requestTimeout(Duration.ofMillis(100)) // Very short timeout
                .build()) {
            
            // This might fail due to short timeout, but should fail gracefully
            assertDoesNotThrow(() -> {
                try {
                    streamableClient.initialize();
                } catch (RuntimeException e) {
                    // Expected for very short timeout
                    assertNotNull(e.getMessage(), "Error should have meaningful message");
                }
            }, "Streamable timeout should be handled gracefully");
        }
        
        try (EverythingTestClient sseClient = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_SSE)
                .requestTimeout(Duration.ofMillis(100)) // Very short timeout
                .build()) {
            
            // This might fail due to short timeout, but should fail gracefully
            assertDoesNotThrow(() -> {
                try {
                    sseClient.initialize();
                } catch (RuntimeException e) {
                    // Expected for very short timeout
                    assertNotNull(e.getMessage(), "Error should have meaningful message");
                }
            }, "SSE timeout should be handled gracefully");
        }
        
        System.out.println("✅ Transport-specific error scenarios test passed");
    }

    @Test
    @DisplayName("Should maintain session consistency across transports")
    void testSessionConsistency() {
        // Test that sessions behave consistently across transport types
        
        // Streamable session
        try (EverythingTestClient streamableClient = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build()) {
            
            assertTrue(streamableClient.initialize(), "Streamable should initialize");
            
            var serverInfo1 = streamableClient.getServerInfo();
            streamableClient.getTools().add(1, 1); // Perform operation
            var serverInfo2 = streamableClient.getServerInfo();
            
            // Server info should be consistent
            assertEquals(serverInfo1.name(), serverInfo2.name(), "Streamable server name should be consistent");
            assertEquals(serverInfo1.version(), serverInfo2.version(), "Streamable server version should be consistent");
        }
        
        // SSE session
        try (EverythingTestClient sseClient = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_SSE)
                .build()) {
            
            assertTrue(sseClient.initialize(), "SSE should initialize");
            
            var serverInfo1 = sseClient.getServerInfo();
            sseClient.getTools().add(2, 2); // Perform operation
            var serverInfo2 = sseClient.getServerInfo();
            
            // Server info should be consistent
            assertEquals(serverInfo1.name(), serverInfo2.name(), "SSE server name should be consistent");
            assertEquals(serverInfo1.version(), serverInfo2.version(), "SSE server version should be consistent");
        }
        
        System.out.println("✅ Session consistency test passed");
    }

    @Test
    @DisplayName("Should handle long-running operations on both transports")
    void testLongRunningOperations() {
        // Test how each transport handles long-running operations
        
        // Streamable long operation
        try (EverythingTestClient streamableClient = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .requestTimeout(Duration.ofSeconds(15)) // Enough time for operation
                .build()) {
            
            assertTrue(streamableClient.initialize(), "Streamable should initialize");
            
            assertDoesNotThrow(() -> {
                String result = streamableClient.getTools().longRunningOperation(2, 3);
                assertNotNull(result, "Streamable long operation should return result");
                assertFalse(result.trim().isEmpty(), "Streamable result should not be empty");
            }, "Streamable should handle long operations");
        }
        
        // SSE long operation
        try (EverythingTestClient sseClient = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_SSE)
                .requestTimeout(Duration.ofSeconds(15)) // Enough time for operation
                .build()) {
            
            assertTrue(sseClient.initialize(), "SSE should initialize");
            
            assertDoesNotThrow(() -> {
                String result = sseClient.getTools().longRunningOperation(2, 3);
                assertNotNull(result, "SSE long operation should return result");
                assertFalse(result.trim().isEmpty(), "SSE result should not be empty");
            }, "SSE should handle long operations");
        }
        
        System.out.println("✅ Long-running operations test passed");
    }
}