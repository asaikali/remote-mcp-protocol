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
 * Helper class for interacting with prompts on the MCP Everything Server.
 * 
 * This class provides type-safe, convenient methods for working with prompts
 * available on the Everything Server, handling the underlying MCP protocol details
 * and result parsing.
 * 
 * Supported prompts:
 * - simple_prompt: A prompt without arguments
 * - complex_prompt: A prompt with temperature and style arguments
 * - resource_prompt: A prompt that includes an embedded resource reference
 */
public class EverythingPrompts {
    
    private static final Logger logger = LoggerFactory.getLogger(EverythingPrompts.class);
    
    // Prompt names as defined in the Everything server
    public static final String SIMPLE_PROMPT = "simple_prompt";
    public static final String COMPLEX_PROMPT = "complex_prompt";
    public static final String RESOURCE_PROMPT = "resource_prompt";
    
    private final McpSyncClient mcpClient;
    private List<Prompt> availablePrompts;
    
    public EverythingPrompts(McpSyncClient mcpClient) {
        this.mcpClient = mcpClient;
        refreshPromptsList();
    }
    
    /**
     * Refresh the list of available prompts from the server.
     */
    public void refreshPromptsList() {
        try {
            ListPromptsResult result = mcpClient.listPrompts();
            this.availablePrompts = result.prompts();
            logger.debug("Refreshed prompts list: {} prompts available", availablePrompts.size());
        } catch (Exception e) {
            logger.error("Failed to refresh prompts list", e);
            this.availablePrompts = List.of();
        }
    }
    
    /**
     * Get the list of all available prompts.
     * 
     * @return list of available prompts
     */
    public List<Prompt> getAvailablePrompts() {
        return List.copyOf(availablePrompts);
    }
    
    /**
     * Get the names of all available prompts.
     * 
     * @return set of prompt names
     */
    public Set<String> getAvailablePromptNames() {
        return availablePrompts.stream()
                .map(Prompt::name)
                .collect(Collectors.toSet());
    }
    
    /**
     * Check if a specific prompt is available.
     * 
     * @param promptName name of the prompt to check
     * @return true if prompt is available, false otherwise
     */
    public boolean isPromptAvailable(String promptName) {
        return getAvailablePromptNames().contains(promptName);
    }
    
    /**
     * Get detailed information about a specific prompt.
     * 
     * @param promptName name of the prompt
     * @return prompt information or null if not found
     */
    public Prompt getPromptInfo(String promptName) {
        return availablePrompts.stream()
                .filter(prompt -> promptName.equals(prompt.name()))
                .findFirst()
                .orElse(null);
    }
    
    // ========================================
    // Specific Prompt Methods
    // ========================================
    
    /**
     * Get the simple prompt without any arguments.
     * 
     * @return prompt result with messages
     * @throws EverythingPromptException if the prompt call fails
     */
    public GetPromptResult getSimplePrompt() {
        logger.debug("Calling simple prompt");
        
        try {
            GetPromptRequest request = new GetPromptRequest(SIMPLE_PROMPT, null);
            GetPromptResult result = mcpClient.getPrompt(request);
            
            logger.debug("Simple prompt returned {} messages", result.messages().size());
            return result;
            
        } catch (Exception e) {
            throw new EverythingPromptException("Failed to execute simple prompt", e);
        }
    }
    
    /**
     * Get the complex prompt with temperature and style arguments.
     * 
     * @param temperature temperature setting (required)
     * @param style output style (optional)
     * @return prompt result with messages including image content
     * @throws EverythingPromptException if the prompt call fails
     */
    public GetPromptResult getComplexPrompt(String temperature, String style) {
        logger.debug("Calling complex prompt with temperature={}, style={}", temperature, style);
        
        try {
            Map<String, Object> arguments = Map.of(
                    "temperature", temperature,
                    "style", style != null ? style : ""
            );
            
            GetPromptRequest request = new GetPromptRequest(COMPLEX_PROMPT, arguments);
            GetPromptResult result = mcpClient.getPrompt(request);
            
            logger.debug("Complex prompt returned {} messages", result.messages().size());
            return result;
            
        } catch (Exception e) {
            throw new EverythingPromptException("Failed to execute complex prompt", e);
        }
    }
    
    /**
     * Get the complex prompt with only temperature (style will be empty).
     * 
     * @param temperature temperature setting
     * @return prompt result with messages
     * @throws EverythingPromptException if the prompt call fails
     */
    public GetPromptResult getComplexPrompt(String temperature) {
        return getComplexPrompt(temperature, null);
    }
    
    /**
     * Get the resource prompt that includes an embedded resource reference.
     * 
     * @param resourceId resource ID to include (1-100)
     * @return prompt result with messages including resource content
     * @throws EverythingPromptException if the prompt call fails
     */
    public GetPromptResult getResourcePrompt(int resourceId) {
        logger.debug("Calling resource prompt with resourceId={}", resourceId);
        
        if (resourceId < 1 || resourceId > 100) {
            throw new EverythingPromptException("Invalid resourceId: " + resourceId + ". Must be between 1 and 100.");
        }
        
        try {
            Map<String, Object> arguments = Map.of("resourceId", String.valueOf(resourceId));
            
            GetPromptRequest request = new GetPromptRequest(RESOURCE_PROMPT, arguments);
            GetPromptResult result = mcpClient.getPrompt(request);
            
            logger.debug("Resource prompt returned {} messages", result.messages().size());
            return result;
            
        } catch (Exception e) {
            throw new EverythingPromptException("Failed to execute resource prompt", e);
        }
    }
    
    // ========================================
    // Utility Methods
    // ========================================
    
    /**
     * Get a generic prompt with custom arguments.
     * 
     * @param promptName name of the prompt
     * @param arguments arguments to pass to the prompt
     * @return prompt result
     * @throws EverythingPromptException if the prompt call fails
     */
    public GetPromptResult getPrompt(String promptName, Map<String, Object> arguments) {
        logger.debug("Calling prompt: {} with arguments: {}", promptName, arguments);
        
        try {
            GetPromptRequest request = new GetPromptRequest(promptName, arguments);
            GetPromptResult result = mcpClient.getPrompt(request);
            
            logger.debug("Prompt {} returned {} messages", promptName, result.messages().size());
            return result;
            
        } catch (Exception e) {
            throw new EverythingPromptException("Failed to execute prompt: " + promptName, e);
        }
    }
    
    /**
     * Extract text content from prompt messages.
     * 
     * @param result prompt result
     * @return list of text content from all messages
     */
    public List<String> extractTextContent(GetPromptResult result) {
        return result.messages().stream()
                .filter(message -> message.content() instanceof TextContent)
                .map(message -> ((TextContent) message.content()).text())
                .collect(Collectors.toList());
    }
    
    /**
     * Count messages by role in a prompt result.
     * 
     * @param result prompt result
     * @return map of role to count
     */
    public Map<Role, Long> countMessagesByRole(GetPromptResult result) {
        return result.messages().stream()
                .collect(Collectors.groupingBy(
                        PromptMessage::role,
                        Collectors.counting()
                ));
    }
    
    /**
     * Check if prompt result contains image content.
     * 
     * @param result prompt result
     * @return true if any message contains image content
     */
    public boolean hasImageContent(GetPromptResult result) {
        return result.messages().stream()
                .anyMatch(message -> message.content() instanceof ImageContent);
    }
    
    /**
     * Check if prompt result contains resource content.
     * 
     * @param result prompt result
     * @return true if any message contains resource content
     */
    public boolean hasResourceContent(GetPromptResult result) {
        return result.messages().stream()
                .anyMatch(message -> message.content() instanceof EmbeddedResource);
    }
    
    /**
     * Print a summary of all available prompts to console.
     */
    public void printPromptsSummary() {
        if (!availablePrompts.isEmpty()) {
            System.out.println("\n📝 Available Prompts:");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            for (int i = 0; i < availablePrompts.size(); i++) {
                Prompt prompt = availablePrompts.get(i);
                System.out.printf("📋 %d. %s%n", i + 1, prompt.name());
                if (prompt.description() != null && !prompt.description().trim().isEmpty()) {
                    System.out.printf("   Description: %s%n", prompt.description());
                }
                
                if (prompt.arguments() != null && !prompt.arguments().isEmpty()) {
                    System.out.println("   Arguments:");
                    for (PromptArgument arg : prompt.arguments()) {
                        String required = arg.required() != null && arg.required() ? " (required)" : " (optional)";
                        System.out.printf("     - %s: %s%s%n", 
                                arg.name(), 
                                arg.description() != null ? arg.description() : "No description", 
                                required);
                    }
                }
                System.out.println();
            }
            
            System.out.println("📊 Prompt Statistics:");
            System.out.printf("   • Total Prompts: %d%n", availablePrompts.size());
            
            long promptsWithArgs = availablePrompts.stream()
                    .filter(prompt -> prompt.arguments() != null && !prompt.arguments().isEmpty())
                    .count();
            System.out.printf("   • Prompts with Arguments: %d%n", promptsWithArgs);
            System.out.printf("   • Simple Prompts: %d%n", availablePrompts.size() - promptsWithArgs);
            
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        } else {
            System.out.println("📝 No prompts available on the server\n");
        }
    }
    
    /**
     * Exception thrown when prompt operations fail.
     */
    public static class EverythingPromptException extends RuntimeException {
        public EverythingPromptException(String message) {
            super(message);
        }
        
        public EverythingPromptException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}