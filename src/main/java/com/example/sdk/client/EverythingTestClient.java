package com.example.sdk.client;

import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

/**
 * A high-level test client for the MCP Everything Server built on top of the official MCP Java SDK.
 * 
 * This client provides a fluent API for testing MCP Everything Server functionality with:
 * - Builder pattern for easy configuration
 * - Helper classes for tools and resources
 * - Automatic session management
 * - Transport protocol abstraction (HTTP Streamable or SSE)
 * 
 * Usage example:
 * <pre>
 * EverythingTestClient client = EverythingTestClient.builder()
 *     .baseUrl("http://localhost:3001")
 *     .transport(TransportType.HTTP_STREAMABLE)
 *     .build();
 * 
 * if (client.initialize()) {
 *     System.out.println("Connected to: " + client.getServerInfo().name());
 *     
 *     int result = client.getTools().add(2, 3);
 *     String echo = client.getTools().echo("Hello World");
 *     
 *     client.close();
 * }
 * </pre>
 */
public class EverythingTestClient implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(EverythingTestClient.class);
    
    public enum TransportType {
        HTTP_STREAMABLE,
        HTTP_SSE
    }
    
    private final String baseUrl;
    private final String endpoint;
    private final TransportType transportType;
    private final Duration requestTimeout;
    private final Duration initializationTimeout;
    private final ClientCapabilities capabilities;
    private final Implementation clientInfo;
    
    private McpSyncClient mcpClient;
    private EverythingTools toolsHelper;
    private EverythingResources resourcesHelper;
    private boolean initialized = false;
    private InitializeResult initializeResult;
    
    private EverythingTestClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.endpoint = builder.endpoint;
        this.transportType = builder.transportType;
        this.requestTimeout = builder.requestTimeout;
        this.initializationTimeout = builder.initializationTimeout;
        this.capabilities = builder.capabilities;
        this.clientInfo = builder.clientInfo;
        
        logger.info("Creating EverythingTestClient for: {} using {}", baseUrl, transportType);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Initialize the MCP connection to the Everything Server.
     * 
     * @return true if initialization was successful, false otherwise
     */
    public boolean initialize() {
        try {
            logger.info("Initializing MCP connection to Everything Server");
            
            // Create appropriate transport based on configuration
            McpClientTransport transport = createTransport();
            
            // Create MCP client with configuration
            mcpClient = McpClient.sync(transport)
                    .requestTimeout(requestTimeout)
                    .initializationTimeout(initializationTimeout)
                    .capabilities(capabilities)
                    .clientInfo(clientInfo)
                    .toolsChangeConsumer(tools -> 
                        logger.debug("🔧 Tools changed: {} tools available", tools.size()))
                    .resourcesChangeConsumer(resources -> 
                        logger.debug("📂 Resources changed: {} resources available", resources.size()))
                    .loggingConsumer(log -> 
                        logger.debug("📝 Server log [{}]: {}", log.level(), log.data()))
                    .progressConsumer(progress -> 
                        logger.debug("⏳ Progress [{}]: {}/{}", progress.progressToken(), progress.progress(), progress.total()))
                    .build();
            
            // Initialize the connection
            initializeResult = mcpClient.initialize();
            
            // Create helper instances
            toolsHelper = new EverythingTools(mcpClient);
            resourcesHelper = new EverythingResources(mcpClient);
            
            initialized = true;
            
            logger.info("✅ Successfully connected to Everything Server: {} v{}", 
                    initializeResult.serverInfo().name(), 
                    initializeResult.serverInfo().version());
            
            return true;
            
        } catch (Exception e) {
            logger.error("❌ Failed to initialize MCP connection", e);
            initialized = false;
            return false;
        }
    }
    
    private McpClientTransport createTransport() {
        String fullUrl = baseUrl + endpoint;
        
        return switch (transportType) {
            case HTTP_STREAMABLE -> HttpClientStreamableHttpTransport.builder(baseUrl)
                    .endpoint(endpoint)
                    .build();
            case HTTP_SSE -> HttpClientSseClientTransport.builder(fullUrl)
                    .build();
        };
    }
    
    /**
     * Check if the client is initialized and ready for use.
     * 
     * @return true if initialized, false otherwise
     */
    public boolean isInitialized() {
        return initialized && mcpClient != null && mcpClient.isInitialized();
    }
    
    /**
     * Get server information for display purposes.
     * 
     * @return server implementation info
     * @throws IllegalStateException if not initialized
     */
    public Implementation getServerInfo() {
        ensureInitialized();
        return initializeResult.serverInfo();
    }
    
    /**
     * Get server capabilities.
     * 
     * @return server capabilities
     * @throws IllegalStateException if not initialized
     */
    public ServerCapabilities getServerCapabilities() {
        ensureInitialized();
        return initializeResult.capabilities();
    }
    
    /**
     * Get protocol version used in the connection.
     * 
     * @return protocol version string
     * @throws IllegalStateException if not initialized
     */
    public String getProtocolVersion() {
        ensureInitialized();
        return initializeResult.protocolVersion();
    }
    
    /**
     * Get server instructions if available.
     * 
     * @return server instructions or null if not available
     * @throws IllegalStateException if not initialized
     */
    public String getServerInstructions() {
        ensureInitialized();
        return initializeResult.instructions();
    }
    
    /**
     * Get the tools helper for invoking Everything Server tools.
     * 
     * @return tools helper instance
     * @throws IllegalStateException if not initialized
     */
    public EverythingTools getTools() {
        ensureInitialized();
        return toolsHelper;
    }
    
    /**
     * Get the resources helper for accessing Everything Server resources.
     * 
     * @return resources helper instance
     * @throws IllegalStateException if not initialized
     */
    public EverythingResources getResources() {
        ensureInitialized();
        return resourcesHelper;
    }
    
    /**
     * Test server connectivity with a ping.
     * 
     * @return true if ping successful, false otherwise
     * @throws IllegalStateException if not initialized
     */
    public boolean ping() {
        ensureInitialized();
        try {
            mcpClient.ping();
            return true;
        } catch (Exception e) {
            logger.error("Ping failed", e);
            return false;
        }
    }
    
    /**
     * Print detailed server information to console.
     */
    public void printServerInfo() {
        if (!isInitialized()) {
            System.out.println("❌ Client not initialized");
            return;
        }
        
        Implementation serverInfo = getServerInfo();
        ServerCapabilities capabilities = getServerCapabilities();
        
        System.out.println("🚀 Everything Server Information");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("📋 Server: " + serverInfo.name() + 
                          (serverInfo.title() != null ? " (" + serverInfo.title() + ")" : ""));
        System.out.println("📋 Version: " + serverInfo.version());
        System.out.println("📋 Protocol: " + getProtocolVersion());
        System.out.println("📋 Transport: " + transportType);
        System.out.println("📋 Base URL: " + baseUrl + endpoint);
        
        System.out.println("\n🔧 Server Capabilities:");
        System.out.println("   • Tools: " + (capabilities.tools() != null ? "✓" : "✗"));
        System.out.println("   • Resources: " + (capabilities.resources() != null ? "✓" : "✗"));
        System.out.println("   • Prompts: " + (capabilities.prompts() != null ? "✓" : "✗"));
        System.out.println("   • Logging: " + (capabilities.logging() != null ? "✓" : "✗"));
        System.out.println("   • Completions: " + (capabilities.completions() != null ? "✓" : "✗"));
        
        if (capabilities.resources() != null) {
            System.out.println("   • Resource Subscriptions: " + 
                    (capabilities.resources().subscribe() != null && capabilities.resources().subscribe() ? "✓" : "✗"));
        }
        
        String instructions = getServerInstructions();
        if (instructions != null && !instructions.trim().isEmpty()) {
            System.out.println("\n📖 Server Instructions:");
            System.out.println("   " + instructions.lines()
                    .map(line -> "   " + line)
                    .collect(java.util.stream.Collectors.joining("\n")));
        }
        
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
    }
    
    private void ensureInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException("EverythingTestClient must be initialized before use. Call initialize() first.");
        }
    }
    
    @Override
    public void close() {
        if (mcpClient != null) {
            logger.info("Closing EverythingTestClient connection");
            try {
                if (!mcpClient.closeGracefully()) {
                    logger.warn("Client did not close gracefully within timeout");
                }
            } catch (Exception e) {
                logger.error("Error closing MCP client", e);
            } finally {
                initialized = false;
                mcpClient = null;
                toolsHelper = null;
                resourcesHelper = null;
            }
        }
    }
    
    /**
     * Builder for EverythingTestClient with fluent API.
     */
    public static class Builder {
        private String baseUrl = "http://localhost:3001";
        private String endpoint = "/mcp";
        private TransportType transportType = TransportType.HTTP_STREAMABLE;
        private Duration requestTimeout = Duration.ofSeconds(30);
        private Duration initializationTimeout = Duration.ofSeconds(10);
        private ClientCapabilities capabilities = ClientCapabilities.builder()
                .roots(true)
                .sampling()
                .elicitation()
                .build();
        private Implementation clientInfo = new Implementation(
                "EverythingTestClient", 
                "Java SDK Test Client", 
                "1.0.0"
        );
        
        /**
         * Set the base URL of the Everything Server.
         * 
         * @param baseUrl base URL (e.g., "http://localhost:3001")
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "Base URL cannot be null");
            return this;
        }
        
        /**
         * Set the MCP endpoint path.
         * 
         * @param endpoint endpoint path (default: "/mcp")
         * @return this builder
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = Objects.requireNonNull(endpoint, "Endpoint cannot be null");
            return this;
        }
        
        /**
         * Set the transport protocol to use.
         * 
         * @param transportType transport type (HTTP_STREAMABLE or HTTP_SSE)
         * @return this builder
         */
        public Builder transport(TransportType transportType) {
            this.transportType = Objects.requireNonNull(transportType, "Transport type cannot be null");
            return this;
        }
        
        /**
         * Set the request timeout duration.
         * 
         * @param requestTimeout timeout for individual requests
         * @return this builder
         */
        public Builder requestTimeout(Duration requestTimeout) {
            this.requestTimeout = Objects.requireNonNull(requestTimeout, "Request timeout cannot be null");
            return this;
        }
        
        /**
         * Set the initialization timeout duration.
         * 
         * @param initializationTimeout timeout for initialization process
         * @return this builder
         */
        public Builder initializationTimeout(Duration initializationTimeout) {
            this.initializationTimeout = Objects.requireNonNull(initializationTimeout, "Initialization timeout cannot be null");
            return this;
        }
        
        /**
         * Set custom client capabilities.
         * 
         * @param capabilities client capabilities
         * @return this builder
         */
        public Builder capabilities(ClientCapabilities capabilities) {
            this.capabilities = Objects.requireNonNull(capabilities, "Capabilities cannot be null");
            return this;
        }
        
        /**
         * Set custom client information.
         * 
         * @param clientInfo client implementation info
         * @return this builder
         */
        public Builder clientInfo(Implementation clientInfo) {
            this.clientInfo = Objects.requireNonNull(clientInfo, "Client info cannot be null");
            return this;
        }
        
        /**
         * Build the EverythingTestClient instance.
         * 
         * @return new EverythingTestClient instance
         */
        public EverythingTestClient build() {
            return new EverythingTestClient(this);
        }
    }
}
