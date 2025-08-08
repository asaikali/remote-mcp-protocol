package com.example.sdk.client;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper class for invoking tools on the MCP Everything Server.
 * 
 * This class provides type-safe, convenient methods for calling specific tools
 * available on the Everything Server, handling the underlying MCP protocol details
 * and result parsing.
 * 
 * Supported tools:
 * - add: Mathematical addition of two numbers
 * - echo: Echo back a message
 * - printEnv: Print environment variables
 * - longRunningOperation: Execute a long-running operation with progress updates
 * - annotatedMessage: Generate messages with different annotation types
 * - structuredContent: Generate structured content responses
 * - sampleLLM: Sample from language models (if sampling capability available)
 * - startElicitation: Start user elicitation flows
 * - getTinyImage: Get sample image data
 * - getResourceReference: Get embedded resource references
 * - getResourceLinks: Get resource link references
 */
public class EverythingTools {
    
    private static final Logger logger = LoggerFactory.getLogger(EverythingTools.class);
    
    private final McpSyncClient mcpClient;
    private List<Tool> availableTools;
    
    public EverythingTools(McpSyncClient mcpClient) {
        this.mcpClient = mcpClient;
        refreshToolsList();
    }
    
    /**
     * Refresh the list of available tools from the server.
     */
    public void refreshToolsList() {
        try {
            ListToolsResult result = mcpClient.listTools();
            this.availableTools = result.tools();
            logger.debug("Refreshed tools list: {} tools available", availableTools.size());
        } catch (Exception e) {
            logger.error("Failed to refresh tools list", e);
            this.availableTools = List.of();
        }
    }
    
    /**
     * Get the list of all available tools.
     * 
     * @return list of available tools
     */
    public List<Tool> getAvailableTools() {
        return List.copyOf(availableTools);
    }
    
    /**
     * Get the names of all available tools.
     * 
     * @return set of tool names
     */
    public Set<String> getAvailableToolNames() {
        return availableTools.stream()
                .map(Tool::name)
                .collect(Collectors.toSet());
    }
    
    /**
     * Check if a specific tool is available.
     * 
     * @param toolName name of the tool to check
     * @return true if tool is available, false otherwise
     */
    public boolean isToolAvailable(String toolName) {
        return getAvailableToolNames().contains(toolName);
    }
    
    /**
     * Get detailed information about a specific tool.
     * 
     * @param toolName name of the tool
     * @return tool information or null if not found
     */
    public Tool getToolInfo(String toolName) {
        return availableTools.stream()
                .filter(tool -> toolName.equals(tool.name()))
                .findFirst()
                .orElse(null);
    }
    
    // ========================================
    // Core Tool Methods
    // ========================================
    
    /**
     * Add two numbers using the server's add tool.
     * 
     * @param a first number
     * @param b second number
     * @return sum of the two numbers
     * @throws EverythingToolException if the tool call fails or result cannot be parsed
     */
    public int add(int a, int b) {
        logger.debug("Calling add tool: {} + {}", a, b);
        
        try {
            CallToolRequest request = new CallToolRequest("add", Map.of("a", a, "b", b));
            CallToolResult result = mcpClient.callTool(request);
            
            if (result.isError() != null && result.isError()) {
                throw new EverythingToolException("Add tool returned error: " + getErrorMessage(result));
            }
            
            String text = extractTextContent(result);
            int sum = parseAddResult(text, a, b);
            
            logger.debug("Add tool result: {} + {} = {}", a, b, sum);
            return sum;
            
        } catch (Exception e) {
            throw new EverythingToolException("Failed to execute add tool", e);
        }
    }
    
    /**
     * Echo a message using the server's echo tool.
     * 
     * @param message message to echo
     * @return echoed message from server
     * @throws EverythingToolException if the tool call fails
     */
    public String echo(String message) {
        logger.debug("Calling echo tool with message: {}", message);
        
        try {
            CallToolRequest request = new CallToolRequest("echo", Map.of("message", message));
            CallToolResult result = mcpClient.callTool(request);
            
            if (result.isError() != null && result.isError()) {
                throw new EverythingToolException("Echo tool returned error: " + getErrorMessage(result));
            }
            
            String response = extractTextContent(result);
            logger.debug("Echo tool result: {}", response);
            return response;
            
        } catch (Exception e) {
            throw new EverythingToolException("Failed to execute echo tool", e);
        }
    }
    
    /**
     * Get environment variables using the printEnv tool.
     * 
     * @return environment variables as formatted string
     * @throws EverythingToolException if the tool call fails
     */
    public String printEnv() {
        logger.debug("Calling printEnv tool");
        
        try {
            CallToolRequest request = new CallToolRequest("printEnv", Map.of());
            CallToolResult result = mcpClient.callTool(request);
            
            if (result.isError() != null && result.isError()) {
                throw new EverythingToolException("PrintEnv tool returned error: " + getErrorMessage(result));
            }
            
            String response = extractTextContent(result);
            logger.debug("PrintEnv tool executed successfully");
            return response;
            
        } catch (Exception e) {
            throw new EverythingToolException("Failed to execute printEnv tool", e);
        }
    }
    
    /**
     * Execute a long-running operation with optional progress tracking.
     * 
     * @param durationSeconds duration in seconds
     * @param steps number of steps to execute
     * @return operation result message
     * @throws EverythingToolException if the tool call fails
     */
    public String longRunningOperation(int durationSeconds, int steps) {
        return longRunningOperation(durationSeconds, steps, null);
    }
    
    /**
     * Execute a long-running operation with progress tracking.
     * 
     * @param durationSeconds duration in seconds
     * @param steps number of steps to execute
     * @param progressToken optional token for progress tracking
     * @return operation result message
     * @throws EverythingToolException if the tool call fails
     */
    public String longRunningOperation(int durationSeconds, int steps, String progressToken) {
        logger.debug("Calling longRunningOperation tool: duration={}, steps={}, token={}", 
                durationSeconds, steps, progressToken);
        
        try {
            Map<String, Object> args = Map.of(
                    "duration", durationSeconds,
                    "steps", steps
            );
            
            CallToolRequest.Builder requestBuilder = CallToolRequest.builder()
                    .name("longRunningOperation")
                    .arguments(args);
            
            if (progressToken != null) {
                requestBuilder.progressToken(progressToken);
            }
            
            CallToolResult result = mcpClient.callTool(requestBuilder.build());
            
            if (result.isError() != null && result.isError()) {
                throw new EverythingToolException("LongRunningOperation tool returned error: " + getErrorMessage(result));
            }
            
            String response = extractTextContent(result);
            logger.debug("LongRunningOperation completed successfully");
            return response;
            
        } catch (Exception e) {
            throw new EverythingToolException("Failed to execute longRunningOperation tool", e);
        }
    }
    
    /**
     * Generate an annotated message with specific message type.
     * 
     * @param messageType type of message (success, error, debug)
     * @param includeImage whether to include image content
     * @return list of content items (text and optionally image)
     * @throws EverythingToolException if the tool call fails
     */
    public List<Content> annotatedMessage(String messageType, boolean includeImage) {
        logger.debug("Calling annotatedMessage tool: type={}, includeImage={}", messageType, includeImage);
        
        try {
            CallToolRequest request = new CallToolRequest("annotatedMessage", Map.of(
                    "messageType", messageType,
                    "includeImage", includeImage
            ));
            CallToolResult result = mcpClient.callTool(request);
            
            if (result.isError() != null && result.isError()) {
                throw new EverythingToolException("AnnotatedMessage tool returned error: " + getErrorMessage(result));
            }
            
            List<Content> content = result.content();
            logger.debug("AnnotatedMessage tool returned {} content items", content.size());
            return content;
            
        } catch (Exception e) {
            throw new EverythingToolException("Failed to execute annotatedMessage tool", e);
        }
    }
    
    /**
     * Get structured content response.
     * 
     * @return structured content as CallToolResult
     * @throws EverythingToolException if the tool call fails
     */
    public CallToolResult structuredContent() {
        logger.debug("Calling structuredContent tool");
        
        try {
            CallToolRequest request = new CallToolRequest("structuredContent", Map.of());
            CallToolResult result = mcpClient.callTool(request);
            
            if (result.isError() != null && result.isError()) {
                throw new EverythingToolException("StructuredContent tool returned error: " + getErrorMessage(result));
            }
            
            logger.debug("StructuredContent tool executed successfully");
            return result;
            
        } catch (Exception e) {
            throw new EverythingToolException("Failed to execute structuredContent tool", e);
        }
    }
    
    /**
     * Get a tiny image for testing purposes.
     * 
     * @return image content
     * @throws EverythingToolException if the tool call fails
     */
    public Content getTinyImage() {
        logger.debug("Calling getTinyImage tool");
        
        try {
            CallToolRequest request = new CallToolRequest("getTinyImage", Map.of());
            CallToolResult result = mcpClient.callTool(request);
            
            if (result.isError() != null && result.isError()) {
                throw new EverythingToolException("GetTinyImage tool returned error: " + getErrorMessage(result));
            }
            
            if (result.content().isEmpty()) {
                throw new EverythingToolException("GetTinyImage tool returned no content");
            }
            
            Content imageContent = result.content().get(0);
            logger.debug("GetTinyImage tool returned content type: {}", imageContent.type());
            return imageContent;
            
        } catch (Exception e) {
            throw new EverythingToolException("Failed to execute getTinyImage tool", e);
        }
    }
    
    // ========================================
    // Advanced Tool Methods
    // ========================================
    
    /**
     * Sample from an LLM using the server's sampleLLM tool.
     * 
     * @param prompt prompt to send to the LLM
     * @param maxTokens maximum tokens to generate
     * @return LLM response
     * @throws EverythingToolException if the tool call fails or sampling is not supported
     */
    public String sampleLLM(String prompt, int maxTokens) {
        logger.debug("Calling sampleLLM tool: prompt length={}, maxTokens={}", prompt.length(), maxTokens);
        
        try {
            CallToolRequest request = new CallToolRequest("sampleLLM", Map.of(
                    "prompt", prompt,
                    "maxTokens", maxTokens
            ));
            CallToolResult result = mcpClient.callTool(request);
            
            if (result.isError() != null && result.isError()) {
                throw new EverythingToolException("SampleLLM tool returned error: " + getErrorMessage(result));
            }
            
            String response = extractTextContent(result);
            logger.debug("SampleLLM tool completed successfully");
            return response;
            
        } catch (Exception e) {
            throw new EverythingToolException("Failed to execute sampleLLM tool", e);
        }
    }
    
    /**
     * Start an elicitation flow to request user input.
     * 
     * @param message message to show to user
     * @return elicitation result
     * @throws EverythingToolException if the tool call fails
     */
    public String startElicitation(String message) {
        logger.debug("Calling startElicitation tool: {}", message);
        
        try {
            CallToolRequest request = new CallToolRequest("startElicitation", Map.of("message", message));
            CallToolResult result = mcpClient.callTool(request);
            
            if (result.isError() != null && result.isError()) {
                throw new EverythingToolException("StartElicitation tool returned error: " + getErrorMessage(result));
            }
            
            String response = extractTextContent(result);
            logger.debug("StartElicitation tool executed successfully");
            return response;
            
        } catch (Exception e) {
            throw new EverythingToolException("Failed to execute startElicitation tool", e);
        }
    }
    
    /**
     * Get resource reference for testing resource embedding.
     * 
     * @return embedded resource reference
     * @throws EverythingToolException if the tool call fails
     */
    public Content getResourceReference() {
        logger.debug("Calling getResourceReference tool");
        
        try {
            CallToolRequest request = new CallToolRequest("getResourceReference", Map.of());
            CallToolResult result = mcpClient.callTool(request);
            
            if (result.isError() != null && result.isError()) {
                throw new EverythingToolException("GetResourceReference tool returned error: " + getErrorMessage(result));
            }
            
            if (result.content().isEmpty()) {
                throw new EverythingToolException("GetResourceReference tool returned no content");
            }
            
            Content resourceContent = result.content().get(0);
            logger.debug("GetResourceReference tool returned content type: {}", resourceContent.type());
            return resourceContent;
            
        } catch (Exception e) {
            throw new EverythingToolException("Failed to execute getResourceReference tool", e);
        }
    }
    
    /**
     * Get resource links for testing resource linking.
     * 
     * @return list of resource link content
     * @throws EverythingToolException if the tool call fails
     */
    public List<Content> getResourceLinks() {
        logger.debug("Calling getResourceLinks tool");
        
        try {
            CallToolRequest request = new CallToolRequest("getResourceLinks", Map.of());
            CallToolResult result = mcpClient.callTool(request);
            
            if (result.isError() != null && result.isError()) {
                throw new EverythingToolException("GetResourceLinks tool returned error: " + getErrorMessage(result));
            }
            
            List<Content> content = result.content();
            logger.debug("GetResourceLinks tool returned {} content items", content.size());
            return content;
            
        } catch (Exception e) {
            throw new EverythingToolException("Failed to execute getResourceLinks tool", e);
        }
    }
    
    // ========================================
    // Helper Methods
    // ========================================
    
    private String extractTextContent(CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            throw new EverythingToolException("Tool result contains no content");
        }
        
        Content content = result.content().get(0);
        if (!(content instanceof TextContent textContent)) {
            throw new EverythingToolException("Expected text content, got: " + content.getClass().getSimpleName());
        }
        
        return textContent.text();
    }
    
    private String getErrorMessage(CallToolResult result) {
        if (result.content() != null && !result.content().isEmpty()) {
            try {
                return extractTextContent(result);
            } catch (Exception e) {
                // Fall back to generic error message
                return "Tool execution failed";
            }
        }
        return "Unknown error";
    }
    
    private int parseAddResult(String text, int a, int b) {
        // Handle various possible formats from the add tool
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
            
            throw new EverythingToolException("Could not parse add result from: " + text);
            
        } catch (NumberFormatException e) {
            throw new EverythingToolException("Failed to parse number from add result: " + text, e);
        }
    }
    
    /**
     * Exception thrown when tool operations fail.
     */
    public static class EverythingToolException extends RuntimeException {
        public EverythingToolException(String message) {
            super(message);
        }
        
        public EverythingToolException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
