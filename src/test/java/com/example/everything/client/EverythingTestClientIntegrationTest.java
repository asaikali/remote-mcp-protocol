package com.example.everything.client;

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
 * Integration test for the EverythingTestClient library demonstrating
 * complete functionality with the MCP Everything Server.
 */
@SpringBootTest
@Testcontainers
class EverythingTestClientIntegrationTest {

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
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            client.close();
        }
    }

    @Test
    void shouldCreateClientWithBuilderPattern() {
        // When
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .requestTimeout(Duration.ofSeconds(30))
                .build();

        // Then
        assertNotNull(client, "Client should be created successfully");
        assertFalse(client.isInitialized(), "Client should not be initialized yet");
    }

    @Test
    void shouldInitializeSuccessfully() {
        // Given
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build();

        // When
        boolean initialized = client.initialize();

        // Then
        assertTrue(initialized, "Client should initialize successfully");
        assertTrue(client.isInitialized(), "Client should report as initialized");
        
        // Server info should be available
        assertNotNull(client.getServerInfo(), "Server info should be available");
        assertNotNull(client.getServerInfo().name(), "Server name should be present");
        assertNotNull(client.getServerInfo().version(), "Server version should be present");
        assertNotNull(client.getProtocolVersion(), "Protocol version should be present");
        
        System.out.println("✅ Client initialized successfully");
        System.out.println("📋 Server: " + client.getServerInfo().name() + " v" + client.getServerInfo().version());
        System.out.println("📋 Protocol: " + client.getProtocolVersion());
    }

    @Test
    void shouldPrintServerInfoToConsole() {
        // Given
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .build();
        
        assertTrue(client.initialize(), "Client should initialize");

        // When & Then
        assertDoesNotThrow(() -> client.printServerInfo(), "Should print server info without errors");
    }

    @Test
    void shouldTestToolsHelper() {
        // Given
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .build();
        
        assertTrue(client.initialize(), "Client should initialize");
        EverythingTools tools = client.getTools();
        assertNotNull(tools, "Tools helper should be available");

        // Test add tool
        int result = tools.add(15, 27);
        assertEquals(42, result, "Add tool should return correct sum");
        System.out.println("✅ Add tool: 15 + 27 = " + result);

        // Test echo tool
        String message = "Hello from EverythingTestClient!";
        String echo = tools.echo(message);
        assertNotNull(echo, "Echo should return a response");
        assertTrue(echo.contains(message), "Echo should contain the input message");
        System.out.println("✅ Echo tool: " + echo);

        // Test available tools
        assertTrue(tools.getAvailableToolNames().contains("add"), "Add tool should be available");
        assertTrue(tools.getAvailableToolNames().contains("echo"), "Echo tool should be available");
        System.out.println("✅ Available tools: " + tools.getAvailableToolNames());
    }

    @Test
    void shouldTestResourcesHelper() {
        // Given
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .build();
        
        assertTrue(client.initialize(), "Client should initialize");
        EverythingResources resources = client.getResources();
        assertNotNull(resources, "Resources helper should be available");

        // Test listing resources
        List<io.modelcontextprotocol.spec.McpSchema.Resource> availableResources = resources.getAvailableResources();
        assertNotNull(availableResources, "Resources list should not be null");
        System.out.println("✅ Found " + availableResources.size() + " resources");

        // Test resource operations if resources are available
        if (!availableResources.isEmpty()) {
            io.modelcontextprotocol.spec.McpSchema.Resource firstResource = availableResources.get(0);
            
            // Test resource info
            assertTrue(resources.isResourceAvailable(firstResource.uri()), "First resource should be available");
            assertNotNull(resources.getResourceInfo(firstResource.uri()), "Resource info should be available");
            
            // Test reading resource
            assertDoesNotThrow(() -> resources.readResource(firstResource), 
                    "Should be able to read resource");
            System.out.println("✅ Successfully read resource: " + firstResource.name());
            
            // Test resource statistics
            EverythingResources.ResourceStats stats = resources.getResourceStats();
            assertNotNull(stats, "Resource stats should be available");
            assertTrue(stats.totalResources() >= 0, "Total resources should be non-negative");
            System.out.println("✅ Resource stats: " + stats.totalResources() + " total resources");
        }
    }

    @Test
    void shouldTestAdvancedToolOperations() {
        // Given
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .build();
        
        assertTrue(client.initialize(), "Client should initialize");
        EverythingTools tools = client.getTools();

        // Test environment variables tool
        String envOutput = tools.printEnv();
        assertNotNull(envOutput, "Environment output should not be null");
        System.out.println("✅ Environment tool executed successfully");

        // Test long running operation
        String progressResult = tools.longRunningOperation(1, 2);
        assertNotNull(progressResult, "Long running operation should return result");
        System.out.println("✅ Long running operation completed: " + progressResult);

        // Test tool availability checks
        assertTrue(tools.isToolAvailable("add"), "Add tool should be available");
        assertTrue(tools.isToolAvailable("echo"), "Echo tool should be available");
        assertFalse(tools.isToolAvailable("nonexistent"), "Non-existent tool should not be available");
    }

    @Test
    void shouldHandleHttpStreamableTransport() {
        // Test HTTP Streamable (primary transport)
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .build();
        
        assertTrue(client.initialize(), "HTTP Streamable should initialize");
        assertNotNull(client.getServerInfo(), "Server info should be available via HTTP Streamable");
        
        // Test basic functionality
        int result = client.getTools().add(10, 15);
        assertEquals(25, result, "Tools should work with HTTP Streamable transport");
        
        System.out.println("✅ HTTP Streamable transport working correctly");
    }

    @Test
    void shouldTestPingOperation() {
        // Given
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .build();
        
        assertTrue(client.initialize(), "Client should initialize");

        // When & Then
        assertTrue(client.ping(), "Ping should succeed");
        System.out.println("✅ Ping operation successful");
    }

    @Test
    void shouldHandleResourceOperations() {
        // Given
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .build();
        
        assertTrue(client.initialize(), "Client should initialize");
        EverythingResources resources = client.getResources();

        // Test resource templates
        assertDoesNotThrow(() -> resources.listResourceTemplates(), 
                "Should be able to list resource templates");
        
        // Test resource summary
        assertDoesNotThrow(() -> resources.printResourceSummary(), 
                "Should be able to print resource summary");
        
        System.out.println("✅ Resource operations completed successfully");
    }

    @Test
    void shouldPerformComprehensiveClientLibraryTest() {
        System.out.println("🚀 Starting comprehensive EverythingTestClient library test");

        // Step 1: Create and initialize client
        client = EverythingTestClient.builder()
                .baseUrl(baseUrl)
                .transport(EverythingTestClient.TransportType.HTTP_STREAMABLE)
                .requestTimeout(Duration.ofSeconds(30))
                .build();
        
        assertTrue(client.initialize(), "Client should initialize successfully");
        System.out.println("✓ Step 1: Client initialization completed");

        // Step 2: Test server info and capabilities
        assertNotNull(client.getServerInfo());
        assertNotNull(client.getServerCapabilities());
        client.printServerInfo();
        System.out.println("✓ Step 2: Server information validated");

        // Step 3: Test tools functionality
        EverythingTools tools = client.getTools();
        assertNotNull(tools);
        
        // Mathematical operations
        assertEquals(42, tools.add(20, 22));
        
        // Text operations
        String testMessage = "Comprehensive test message";
        assertTrue(tools.echo(testMessage).contains(testMessage));
        
        // System operations
        assertNotNull(tools.printEnv());
        
        System.out.println("✓ Step 3: Tools functionality validated");

        // Step 4: Test resources functionality
        EverythingResources resources = client.getResources();
        assertNotNull(resources);
        
        List<io.modelcontextprotocol.spec.McpSchema.Resource> resourceList = resources.getAvailableResources();
        assertNotNull(resourceList);
        
        if (!resourceList.isEmpty()) {
            assertDoesNotThrow(() -> resources.readResource(resourceList.get(0)));
        }
        
        resources.printResourceSummary();
        System.out.println("✓ Step 4: Resources functionality validated");

        // Step 5: Test connectivity
        assertTrue(client.ping());
        System.out.println("✓ Step 5: Connectivity test passed");

        // Step 6: Test resource cleanup
        assertDoesNotThrow(() -> client.close());
        System.out.println("✓ Step 6: Resource cleanup completed");

        System.out.println("🎉 Comprehensive EverythingTestClient library test completed successfully!");
        System.out.println("📊 Test Summary:");
        System.out.println("   - Client Library: ✅ PASSED");
        System.out.println("   - Tools Helper: ✅ PASSED"); 
        System.out.println("   - Resources Helper: ✅ PASSED");
        System.out.println("   - HTTP Streamable Transport: ✅ PASSED");
        System.out.println("   - Server Integration: ✅ PASSED");
        System.out.println("   - All operations: ✅ PASSED");
    }
}