package com.example.everything.client;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper class for interacting with resources on the MCP Everything Server.
 * 
 * This class provides type-safe, convenient methods for managing resources
 * available on the Everything Server, handling the underlying MCP protocol details
 * and result parsing.
 * 
 * Supported operations:
 * - List resources available on the server
 * - Read resource content (text or binary)
 * - List resource templates
 * - Subscribe/unsubscribe to resource changes (if server supports it)
 * - Get resource metadata and information
 */
public class EverythingResources {
    
    private static final Logger logger = LoggerFactory.getLogger(EverythingResources.class);
    
    private final McpSyncClient mcpClient;
    private List<Resource> availableResources;
    
    public EverythingResources(McpSyncClient mcpClient) {
        this.mcpClient = mcpClient;
        refreshResourcesList();
    }
    
    /**
     * Refresh the list of available resources from the server.
     */
    public void refreshResourcesList() {
        try {
            ListResourcesResult result = mcpClient.listResources();
            this.availableResources = result.resources();
            logger.debug("Refreshed resources list: {} resources available", availableResources.size());
        } catch (Exception e) {
            logger.error("Failed to refresh resources list", e);
            this.availableResources = List.of();
        }
    }
    
    /**
     * Get the list of all available resources.
     * 
     * @return list of available resources
     */
    public List<Resource> getAvailableResources() {
        return List.copyOf(availableResources);
    }
    
    /**
     * Get the URIs of all available resources.
     * 
     * @return set of resource URIs
     */
    public Set<String> getAvailableResourceUris() {
        return availableResources.stream()
                .map(Resource::uri)
                .collect(Collectors.toSet());
    }
    
    /**
     * Check if a specific resource is available.
     * 
     * @param resourceUri URI of the resource to check
     * @return true if resource is available, false otherwise
     */
    public boolean isResourceAvailable(String resourceUri) {
        return getAvailableResourceUris().contains(resourceUri);
    }
    
    /**
     * Get detailed information about a specific resource.
     * 
     * @param resourceUri URI of the resource
     * @return resource information or null if not found
     */
    public Resource getResourceInfo(String resourceUri) {
        return availableResources.stream()
                .filter(resource -> resourceUri.equals(resource.uri()))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * Find resources by name pattern.
     * 
     * @param namePattern pattern to match (case-insensitive substring)
     * @return list of matching resources
     */
    public List<Resource> findResourcesByName(String namePattern) {
        String pattern = namePattern.toLowerCase();
        return availableResources.stream()
                .filter(resource -> resource.name().toLowerCase().contains(pattern))
                .collect(Collectors.toList());
    }
    
    /**
     * Read the content of a specific resource by URI.
     * 
     * @param resourceUri URI of the resource to read
     * @return resource content
     * @throws EverythingResourceException if the resource cannot be read or is not found
     */
    public ReadResourceResult readResource(String resourceUri) {
        logger.debug("Reading resource: {}", resourceUri);
        
        try {
            Resource resource = getResourceInfo(resourceUri);
            if (resource == null) {
                throw new EverythingResourceException("Resource not found: " + resourceUri);
            }
            
            ReadResourceResult result = mcpClient.readResource(resource);
            logger.debug("Successfully read resource: {} ({} content items)", 
                    resourceUri, result.contents().size());
            
            return result;
            
        } catch (Exception e) {
            throw new EverythingResourceException("Failed to read resource: " + resourceUri, e);
        }
    }
    
    /**
     * Read the content of a resource.
     * 
     * @param resource the resource to read
     * @return resource content
     * @throws EverythingResourceException if the resource cannot be read
     */
    public ReadResourceResult readResource(Resource resource) {
        logger.debug("Reading resource: {} ({})", resource.name(), resource.uri());
        
        try {
            ReadResourceResult result = mcpClient.readResource(resource);
            logger.debug("Successfully read resource: {} ({} content items)", 
                    resource.name(), result.contents().size());
            
            return result;
            
        } catch (Exception e) {
            throw new EverythingResourceException("Failed to read resource: " + resource.uri(), e);
        }
    }
    
    /**
     * Get text content from a resource (if it contains text).
     * 
     * @param resourceUri URI of the resource
     * @return text content as string
     * @throws EverythingResourceException if resource is not found or doesn't contain text
     */
    public String getResourceText(String resourceUri) {
        ReadResourceResult result = readResource(resourceUri);
        
        for (ResourceContents content : result.contents()) {
            if (content instanceof TextResourceContents textContent) {
                return textContent.text();
            }
        }
        
        throw new EverythingResourceException("Resource does not contain text content: " + resourceUri);
    }
    
    /**
     * Get binary content from a resource (if it contains binary data).
     * 
     * @param resourceUri URI of the resource
     * @return base64-encoded binary content
     * @throws EverythingResourceException if resource is not found or doesn't contain binary data
     */
    public String getResourceBlob(String resourceUri) {
        ReadResourceResult result = readResource(resourceUri);
        
        for (ResourceContents content : result.contents()) {
            if (content instanceof BlobResourceContents blobContent) {
                return blobContent.blob();
            }
        }
        
        throw new EverythingResourceException("Resource does not contain binary content: " + resourceUri);
    }
    
    /**
     * List all available resource templates.
     * 
     * @return resource templates result
     * @throws EverythingResourceException if templates cannot be retrieved
     */
    public ListResourceTemplatesResult listResourceTemplates() {
        logger.debug("Listing resource templates");
        
        try {
            ListResourceTemplatesResult result = mcpClient.listResourceTemplates();
            logger.debug("Found {} resource templates", result.resourceTemplates().size());
            return result;
            
        } catch (Exception e) {
            throw new EverythingResourceException("Failed to list resource templates", e);
        }
    }
    
    /**
     * Subscribe to resource change notifications (if supported by server).
     * 
     * @param resourceUri URI of the resource to subscribe to
     * @throws EverythingResourceException if subscription fails
     */
    public void subscribeToResource(String resourceUri) {
        logger.debug("Subscribing to resource changes: {}", resourceUri);
        
        try {
            SubscribeRequest request = new SubscribeRequest(resourceUri);
            mcpClient.subscribeResource(request);
            logger.debug("Successfully subscribed to resource: {}", resourceUri);
            
        } catch (Exception e) {
            throw new EverythingResourceException("Failed to subscribe to resource: " + resourceUri, e);
        }
    }
    
    /**
     * Unsubscribe from resource change notifications.
     * 
     * @param resourceUri URI of the resource to unsubscribe from
     * @throws EverythingResourceException if unsubscription fails
     */
    public void unsubscribeFromResource(String resourceUri) {
        logger.debug("Unsubscribing from resource changes: {}", resourceUri);
        
        try {
            UnsubscribeRequest request = new UnsubscribeRequest(resourceUri);
            mcpClient.unsubscribeResource(request);
            logger.debug("Successfully unsubscribed from resource: {}", resourceUri);
            
        } catch (Exception e) {
            throw new EverythingResourceException("Failed to unsubscribe from resource: " + resourceUri, e);
        }
    }
    
    /**
     * Get resource statistics and summary information.
     * 
     * @return resource statistics
     */
    public ResourceStats getResourceStats() {
        refreshResourcesList();
        
        int textResourceCount = 0;
        int binaryResourceCount = 0;
        int totalSize = 0;
        
        for (Resource resource : availableResources) {
            try {
                ReadResourceResult result = readResource(resource);
                for (ResourceContents content : result.contents()) {
                    if (content instanceof TextResourceContents textContent) {
                        textResourceCount++;
                        totalSize += textContent.text().length();
                    } else if (content instanceof BlobResourceContents blobContent) {
                        binaryResourceCount++;
                        totalSize += blobContent.blob().length();
                    }
                }
            } catch (Exception e) {
                logger.warn("Failed to read resource for stats: {}", resource.uri());
            }
        }
        
        return new ResourceStats(
                availableResources.size(),
                textResourceCount,
                binaryResourceCount,
                totalSize
        );
    }
    
    /**
     * Print a summary of all available resources to console.
     */
    public void printResourceSummary() {
        if (!availableResources.isEmpty()) {
            System.out.println("\n📂 Available Resources:");
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            
            for (int i = 0; i < availableResources.size(); i++) {
                Resource resource = availableResources.get(i);
                System.out.printf("📄 %d. %s%n", i + 1, resource.name());
                System.out.printf("   URI: %s%n", resource.uri());
                if (resource.description() != null && !resource.description().trim().isEmpty()) {
                    System.out.printf("   Description: %s%n", resource.description());
                }
                if (resource.mimeType() != null) {
                    System.out.printf("   MIME Type: %s%n", resource.mimeType());
                }
                System.out.println();
            }
            
            ResourceStats stats = getResourceStats();
            System.out.println("📊 Resource Statistics:");
            System.out.printf("   • Total Resources: %d%n", stats.totalResources());
            System.out.printf("   • Text Resources: %d%n", stats.textResources());
            System.out.printf("   • Binary Resources: %d%n", stats.binaryResources());
            System.out.printf("   • Total Content Size: %d characters/bytes%n", stats.totalSize());
            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n");
        } else {
            System.out.println("📂 No resources available on the server\n");
        }
    }
    
    /**
     * Record containing resource statistics.
     */
    public record ResourceStats(
            int totalResources,
            int textResources, 
            int binaryResources,
            int totalSize
    ) {}
    
    /**
     * Exception thrown when resource operations fail.
     */
    public static class EverythingResourceException extends RuntimeException {
        public EverythingResourceException(String message) {
            super(message);
        }
        
        public EverythingResourceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}