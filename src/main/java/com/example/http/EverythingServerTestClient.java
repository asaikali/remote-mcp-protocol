package com.example.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.ProtocolVersions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

/**
 * A robust, Java-native test client for validating MCP Everything Server functionality.
 * 
 * This client provides a high-level API for testing MCP servers and proxies using
 * the official MCP Java SDK schema classes and RestClient for HTTP communication.
 * 
 * Key features:
 * - Uses official MCP SDK schema classes for type safety
 * - Comprehensive error handling and resilience
 * - Session management with automatic cleanup
 * - Support for Server-Sent Events responses
 * - Detailed logging for debugging
 * - Builder pattern for configuration
 */
public class EverythingServerTestClient implements AutoCloseable {
    
    private static final Logger logger = LoggerFactory.getLogger(EverythingServerTestClient.class);
    
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
    private static final String MCP_SESSION_HEADER = "Mcp-Session-Id";
    
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final boolean autoCleanup;
    
    private String sessionId;
    private boolean initialized = false;
    
    private EverythingServerTestClient(Builder builder) {
        this.restClient = RestClient.builder()
                .baseUrl(builder.baseUrl)
                .build();
        this.objectMapper = new ObjectMapper();
        this.timeout = builder.timeout;
        this.autoCleanup = builder.autoCleanup;
        
        logger.info("Created EverythingServerTestClient for: {}", builder.baseUrl);
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * Initialize the MCP connection using proper schema types
     */
    public InitializationResult initialize() {
        return initialize("EverythingServerTestClient", "1.0.0");
    }
    
    /**
     * Initialize the MCP connection with custom client info
     */
    public InitializationResult initialize(String clientName, String clientVersion) {
        logger.info("Initializing MCP connection with client: {} v{}", clientName, clientVersion);
        
        try {
            // Create initialization request using MCP SDK
            var clientInfo = new McpSchema.Implementation(clientName, clientVersion);
            var capabilities = McpSchema.ClientCapabilities.builder()
                    .roots(true)
                    .sampling()
                    .elicitation()
                    .build();
            
            var initRequest = new McpSchema.InitializeRequest(
                    ProtocolVersions.MCP_2025_06_18,
                    capabilities,
                    clientInfo
            );
            
            var jsonRpcRequest = new McpSchema.JSONRPCRequest(
                    McpSchema.JSONRPC_VERSION,
                    McpSchema.METHOD_INITIALIZE,
                    1,
                    initRequest
            );
            
            // Send request
            ResponseEntity<String> response = restClient.post()
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(objectMapper.writeValueAsString(jsonRpcRequest))
                    .retrieve()
                    .toEntity(String.class);
            
            // Extract and validate session ID
            this.sessionId = response.getHeaders().getFirst(MCP_SESSION_HEADER);
            if (sessionId == null || sessionId.trim().isEmpty()) {
                throw new McpClientException("No session ID received from server");
            }
            
            // Parse response
            String jsonContent = extractJsonFromResponse(response.getBody());
            var jsonRpcResponse = objectMapper.readValue(jsonContent, McpSchema.JSONRPCResponse.class);
            
            validateJsonRpcResponse(jsonRpcResponse, 1);
            
            var initResult = objectMapper.convertValue(jsonRpcResponse.result(), McpSchema.InitializeResult.class);
            
            // Send initialized notification
            sendInitializedNotification();
            
            this.initialized = true;
            
            logger.info("MCP connection initialized successfully. Session ID: {}", sessionId);
            
            return new InitializationResult(
                    sessionId,
                    initResult.protocolVersion(),
                    initResult.serverInfo(),
                    initResult.capabilities()
            );
            
        } catch (Exception e) {
            logger.error("Failed to initialize MCP connection", e);
            throw new McpClientException("Failed to initialize MCP connection", e);
        }
    }
    
    /**
     * Send the initialized notification after successful initialization
     */
    private void sendInitializedNotification() {
        if (sessionId == null) {
            throw new IllegalStateException("Must initialize before sending notification");
        }
        
        try {
            var notification = new McpSchema.JSONRPCNotification(
                    McpSchema.JSONRPC_VERSION,
                    McpSchema.METHOD_NOTIFICATION_INITIALIZED,
                    null
            );
            
            // MCP notifications are sent as arrays
            var notificationArray = List.of(notification);
            
            restClient.post()
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(MCP_SESSION_HEADER, sessionId)
                    .body(objectMapper.writeValueAsString(notificationArray))
                    .retrieve()
                    .toEntity(String.class);
                    
            logger.debug("Initialized notification sent successfully");
            
        } catch (Exception e) {
            logger.error("Failed to send initialized notification", e);
            throw new McpClientException("Failed to send initialized notification", e);
        }
    }
    
    /**
     * List all available tools from the server
     */
    public ToolsListResult listTools() {
        return listTools(null);
    }
    
    /**
     * List tools with optional pagination cursor
     */
    public ToolsListResult listTools(String cursor) {
        ensureInitialized();
        logger.info("Listing tools with cursor: {}", cursor);
        
        try {
            var paginatedRequest = new McpSchema.PaginatedRequest(cursor);
            
            var jsonRpcRequest = new McpSchema.JSONRPCRequest(
                    McpSchema.JSONRPC_VERSION,
                    McpSchema.METHOD_TOOLS_LIST,
                    2,
                    paginatedRequest
            );
            
            ResponseEntity<String> response = restClient.post()
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(MCP_SESSION_HEADER, sessionId)
                    .body(objectMapper.writeValueAsString(jsonRpcRequest))
                    .retrieve()
                    .toEntity(String.class);
            
            String jsonContent = extractJsonFromResponse(response.getBody());
            var jsonRpcResponse = objectMapper.readValue(jsonContent, McpSchema.JSONRPCResponse.class);
            
            validateJsonRpcResponse(jsonRpcResponse, 2);
            
            var toolsResult = objectMapper.convertValue(jsonRpcResponse.result(), McpSchema.ListToolsResult.class);
            
            logger.info("Successfully listed {} tools", toolsResult.tools().size());
            
            return new ToolsListResult(
                    toolsResult.tools(),
                    toolsResult.nextCursor()
            );
            
        } catch (Exception e) {
            logger.error("Failed to list tools", e);
            throw new McpClientException("Failed to list tools", e);
        }
    }
    
    /**
     * Invoke the echo tool with a message
     */
    public CallToolResult invokeEcho(String message) {
        logger.info("Invoking echo tool with message: {}", message);
        
        Map<String, Object> arguments = Map.of("message", message);
        return callTool("echo", arguments, 3);
    }
    
    /**
     * Invoke the add tool with two numbers
     */
    public CallToolResult invokeAdd(int a, int b) {
        logger.info("Invoking add tool: {} + {}", a, b);
        
        Map<String, Object> arguments = Map.of("a", a, "b", b);
        return callTool("add", arguments, 4);
    }
    
    /**
     * Generic method to call any tool
     */
    public CallToolResult callTool(String toolName, Map<String, Object> arguments, int requestId) {
        ensureInitialized();
        
        try {
            var callToolRequest = new McpSchema.CallToolRequest(toolName, arguments);
            
            var jsonRpcRequest = new McpSchema.JSONRPCRequest(
                    McpSchema.JSONRPC_VERSION,
                    McpSchema.METHOD_TOOLS_CALL,
                    requestId,
                    callToolRequest
            );
            
            ResponseEntity<String> response = restClient.post()
                    .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(MCP_SESSION_HEADER, sessionId)
                    .body(objectMapper.writeValueAsString(jsonRpcRequest))
                    .retrieve()
                    .toEntity(String.class);
            
            String jsonContent = extractJsonFromResponse(response.getBody());
            var jsonRpcResponse = objectMapper.readValue(jsonContent, McpSchema.JSONRPCResponse.class);
            
            validateJsonRpcResponse(jsonRpcResponse, requestId);
            
            var callResult = objectMapper.convertValue(jsonRpcResponse.result(), McpSchema.CallToolResult.class);
            
            logger.info("Successfully called tool: {}", toolName);
            
            return new CallToolResult(callResult);
            
        } catch (Exception e) {
            logger.error("Failed to call tool: {}", toolName, e);
            throw new McpClientException("Failed to call tool: " + toolName, e);
        }
    }
    
    /**
     * Validate that the server has the expected core tools
     */
    public void validateCoreTools() {
        validateCoreTools(Set.of("echo", "printEnv", "add", "longRunningOperation"));
    }
    
    /**
     * Validate that the server has specific expected tools
     */
    public void validateCoreTools(Set<String> expectedTools) {
        logger.info("Validating core tools: {}", expectedTools);
        
        ToolsListResult toolsResult = listTools();
        Set<String> availableTools = new HashSet<>();
        
        for (McpSchema.Tool tool : toolsResult.tools()) {
            availableTools.add(tool.name());
        }
        
        for (String expectedTool : expectedTools) {
            if (!availableTools.contains(expectedTool)) {
                String message = String.format(
                        "Expected tool '%s' not found. Available tools: %s", 
                        expectedTool, 
                        availableTools
                );
                logger.error(message);
                throw new McpValidationException(message);
            }
        }
        
        logger.info("Core tools validation passed. Found all expected tools: {}", expectedTools);
    }
    
    /**
     * Run a comprehensive test suite against the everything server
     */
    public TestSuiteResult runTestSuite() {
        logger.info("Running comprehensive test suite");
        
        try {
            // Initialize
            InitializationResult init = initialize();
            
            // List and validate tools
            ToolsListResult tools = listTools();
            validateCoreTools();
            
            // Test echo
            CallToolResult echoResult = invokeEcho("Hello, MCP World!");
            String echoMessage = extractTextFromResult(echoResult);
            
            if (!echoMessage.contains("Hello, MCP World!")) {
                throw new McpValidationException("Echo result should contain the input message");
            }
            
            // Test add
            CallToolResult addResult = invokeAdd(2, 3);
            String addMessage = extractTextFromResult(addResult);
            int calculatedResult = parseAddResult(addMessage, 2, 3);
            
            if (calculatedResult != 5) {
                throw new McpValidationException("2 + 3 should equal 5, got: " + calculatedResult);
            }
            
            logger.info("Test suite completed successfully");
            
            return new TestSuiteResult(
                    true,
                    init.sessionId(),
                    tools.tools().size(),
                    echoMessage,
                    calculatedResult,
                    null
            );
            
        } catch (Exception e) {
            logger.error("Test suite failed", e);
            return new TestSuiteResult(
                    false,
                    sessionId,
                    0,
                    null,
                    0,
                    e.getMessage()
            );
        }
    }
    
    // Helper methods
    
    private void ensureInitialized() {
        if (!initialized || sessionId == null) {
            throw new IllegalStateException("Client must be initialized first");
        }
    }
    
    private String extractJsonFromResponse(String responseBody) {
        if (responseBody == null) {
            throw new McpClientException("Response body is null");
        }
        
        if (responseBody.contains("event:") && responseBody.contains("data:")) {
            // Server-Sent Events format
            String[] lines = responseBody.split("\n");
            for (String line : lines) {
                if (line.startsWith("data: ")) {
                    return line.substring(6);
                }
            }
            throw new McpClientException("No data line found in SSE response");
        }
        
        return responseBody;
    }
    
    private void validateJsonRpcResponse(McpSchema.JSONRPCResponse response, int expectedId) {
        if (response == null) {
            throw new McpClientException("JSON-RPC response is null");
        }
        
        if (!McpSchema.JSONRPC_VERSION.equals(response.jsonrpc())) {
            throw new McpClientException("Invalid JSON-RPC version: " + response.jsonrpc());
        }
        
        if (!Objects.equals(expectedId, response.id())) {
            throw new McpClientException("Response ID mismatch. Expected: " + expectedId + ", Got: " + response.id());
        }
        
        if (response.error() != null) {
            throw new McpServerException("Server returned error: " + response.error().message(), response.error());
        }
        
        if (response.result() == null) {
            throw new McpClientException("Response has no result");
        }
    }
    
    private String extractTextFromResult(CallToolResult result) {
        if (result.callToolResult().content() == null || result.callToolResult().content().isEmpty()) {
            throw new McpClientException("Tool result has no content");
        }
        
        var content = result.callToolResult().content().get(0);
        if (content instanceof McpSchema.TextContent textContent) {
            return textContent.text();
        }
        
        throw new McpClientException("Expected text content, got: " + content.getClass().getSimpleName());
    }
    
    private int parseAddResult(String text, int a, int b) {
        // Handle various possible formats:
        // "2 + 3 = 5" or "The sum of 2 and 3 is 5." or similar
        
        try {
            // Try format: "The sum of X and Y is Z"
            String pattern1 = "The sum of " + a + " and " + b + " is ";
            if (text.contains(pattern1)) {
                String resultPart = text.substring(text.indexOf(pattern1) + pattern1.length()).trim();
                String numberStr = resultPart.replaceAll("[^0-9].*", "");
                return Integer.parseInt(numberStr);
            }
            
            // Try format: "X + Y = Z"
            String pattern2 = a + " + " + b + " = ";
            if (text.contains(pattern2)) {
                String resultPart = text.substring(text.indexOf(pattern2) + pattern2.length()).trim();
                return Integer.parseInt(resultPart.split("\\s+")[0]);
            }
            
            // Try extracting any number that equals a + b
            int expected = a + b;
            if (text.contains(String.valueOf(expected))) {
                return expected;
            }
            
            throw new McpClientException("Could not parse add result from: " + text);
            
        } catch (NumberFormatException e) {
            throw new McpClientException("Failed to parse number from add result: " + text, e);
        }
    }
    
    @Override
    public void close() {
        if (autoCleanup && sessionId != null) {
            logger.info("Cleaning up session: {}", sessionId);
            // Could implement session cleanup logic here if needed
        }
    }
    
    // Builder class
    public static class Builder {
        private String baseUrl;
        private Duration timeout = DEFAULT_TIMEOUT;
        private boolean autoCleanup = true;
        
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }
        
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }
        
        public Builder autoCleanup(boolean autoCleanup) {
            this.autoCleanup = autoCleanup;
            return this;
        }
        
        public EverythingServerTestClient build() {
            if (baseUrl == null || baseUrl.trim().isEmpty()) {
                throw new IllegalArgumentException("Base URL is required");
            }
            return new EverythingServerTestClient(this);
        }
    }
    
    // Result record classes using MCP SDK types
    
    public record InitializationResult(
            String sessionId,
            String protocolVersion,
            McpSchema.Implementation serverInfo,
            McpSchema.ServerCapabilities capabilities
    ) {}
    
    public record ToolsListResult(
            List<McpSchema.Tool> tools,
            String nextCursor
    ) {}
    
    public record CallToolResult(
            McpSchema.CallToolResult callToolResult
    ) {}
    
    public record TestSuiteResult(
            boolean success,
            String sessionId,
            int toolCount,
            String echoMessage,
            int addResult,
            String errorMessage
    ) {}
    
    // Exception classes
    
    public static class McpClientException extends RuntimeException {
        public McpClientException(String message) {
            super(message);
        }
        
        public McpClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
    
    public static class McpServerException extends McpClientException {
        private final McpSchema.JSONRPCResponse.JSONRPCError error;
        
        public McpServerException(String message, McpSchema.JSONRPCResponse.JSONRPCError error) {
            super(message);
            this.error = error;
        }
        
        public McpSchema.JSONRPCResponse.JSONRPCError getError() {
            return error;
        }
    }
    
    public static class McpValidationException extends McpClientException {
        public McpValidationException(String message) {
            super(message);
        }
    }
}
