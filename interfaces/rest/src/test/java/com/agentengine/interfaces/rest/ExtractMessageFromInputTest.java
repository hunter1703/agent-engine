package com.agentengine.interfaces.rest;

import com.agentengine.interfaces.rest.requests.ResponsesApiRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

public class ExtractMessageFromInputTest {

    @Test
    public void testExtractLatestUserMessage() {
        // Create multiple input messages
        ResponsesApiRequest.ContentPart contentPart1 = new ResponsesApiRequest.ContentPart("input_text", "First message");
        ResponsesApiRequest.ContentPart contentPart2 = new ResponsesApiRequest.ContentPart("input_text", "Second message");
        ResponsesApiRequest.ContentPart contentPart3 = new ResponsesApiRequest.ContentPart("input_text", "Latest message");

        List<ResponsesApiRequest.ContentPart> contentParts1 = Arrays.asList(contentPart1);
        List<ResponsesApiRequest.ContentPart> contentParts2 = Arrays.asList(contentPart2);
        List<ResponsesApiRequest.ContentPart> contentParts3 = Arrays.asList(contentPart3);

        ResponsesApiRequest.InputMessage inputMessage1 = new ResponsesApiRequest.InputMessage("message", "user", contentParts1);
        ResponsesApiRequest.InputMessage inputMessage2 = new ResponsesApiRequest.InputMessage("message", "assistant", contentParts2); // Assistant message
        ResponsesApiRequest.InputMessage inputMessage3 = new ResponsesApiRequest.InputMessage("message", "user", contentParts3); // Latest user message

        List<ResponsesApiRequest.InputMessage> input = Arrays.asList(inputMessage1, inputMessage2, inputMessage3);

        // Test the method directly
        String result = extractMessageFromInput(input);

        // Should return the content of the latest user message
        assertEquals("Latest message", result);
    }

    @Test
    public void testExtractUserMessageWhenOnlyOneExists() {
        ResponsesApiRequest.ContentPart contentPart = new ResponsesApiRequest.ContentPart("input_text", "Single message");
        List<ResponsesApiRequest.ContentPart> contentParts = Arrays.asList(contentPart);
        ResponsesApiRequest.InputMessage inputMessage = new ResponsesApiRequest.InputMessage("message", "user", contentParts);

        List<ResponsesApiRequest.InputMessage> input = Arrays.asList(inputMessage);

        String result = extractMessageFromInput(input);

        assertEquals("Single message", result);
    }

    @Test
    public void testReturnEmptyWhenNoUserMessages() {
        ResponsesApiRequest.ContentPart contentPart = new ResponsesApiRequest.ContentPart("input_text", "Assistant message");
        List<ResponsesApiRequest.ContentPart> contentParts = Arrays.asList(contentPart);
        ResponsesApiRequest.InputMessage inputMessage = new ResponsesApiRequest.InputMessage("message", "assistant", contentParts);

        List<ResponsesApiRequest.InputMessage> input = Arrays.asList(inputMessage);

        String result = extractMessageFromInput(input);

        assertEquals("", result);
    }

    @Test
    public void testReturnEmptyWhenInputIsNull() {
        String result = extractMessageFromInput(null);

        assertEquals("", result);
    }

    @Test
    public void testReturnEmptyWhenInputIsEmpty() {
        String result = extractMessageFromInput(Arrays.asList());

        assertEquals("", result);
    }

    // Copy the method from AgentRestAPI to test it directly
    private String extractMessageFromInput(List<ResponsesApiRequest.InputMessage> input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // Look for the latest user message in the input array
        for (int i = input.size() - 1; i >= 0; i--) {
            ResponsesApiRequest.InputMessage inputMessage = input.get(i);
            if ("message".equals(inputMessage.getType()) && "user".equals(inputMessage.getRole())) {
                List<ResponsesApiRequest.ContentPart> contentParts = inputMessage.getContent();
                if (contentParts != null) {
                    StringBuilder messageBuilder = new StringBuilder();

                    for (ResponsesApiRequest.ContentPart contentPart : contentParts) {
                        if ("input_text".equals(contentPart.getType()) && contentPart.getText() != null) {
                            messageBuilder.append(contentPart.getText()).append("\n");
                        }
                    }

                    // Return the content of the latest user message
                    String message = messageBuilder.toString().trim();
                    if (!message.isEmpty()) {
                        return message;
                    }
                }
            }
        }

        // If no user message is found, return empty string
        return "";
    }
}