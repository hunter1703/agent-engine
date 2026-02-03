package com.agentengine.interfaces.rest;

import com.agentengine.interfaces.rest.requests.ResponsesApiRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

public class ResponsesApiRequestTest {

  @Test
  public void testResponsesApiRequestStructure() {
    // Create a sample input message with content parts
    ResponsesApiRequest.ContentPart contentPart1 = new ResponsesApiRequest.ContentPart("input_text", "Hello world");
    ResponsesApiRequest.ContentPart contentPart2 = new ResponsesApiRequest.ContentPart("input_text", "Bye world");

    List<ResponsesApiRequest.ContentPart> contentParts = Arrays.asList(contentPart1, contentPart2);
    ResponsesApiRequest.InputMessage inputMessage = new ResponsesApiRequest.InputMessage("message", "user",
        contentParts);

    List<ResponsesApiRequest.InputMessage> input = Arrays.asList(inputMessage);

    // Create the main request object
    ResponsesApiRequest request = new ResponsesApiRequest("shell_agent", input);

    // Test the structure
    assertEquals("shell_agent", request.getModel());
    assertNotNull(request.getInput());
    assertEquals(1, request.getInput().size());

    // Test the input message
    ResponsesApiRequest.InputMessage msg = request.getInput().get(0);
    assertEquals("message", msg.getType());
    assertEquals("user", msg.getRole());
    assertEquals(2, msg.getContent().size());

    // Test the content parts
    assertEquals("input_text", msg.getContent().get(0).getType());
    assertEquals("Hello world", msg.getContent().get(0).getText());
    assertEquals("input_text", msg.getContent().get(1).getType());
    assertEquals("Bye world", msg.getContent().get(1).getText());
  }

  @Test
  public void testEmptyInput() {
    ResponsesApiRequest request = new ResponsesApiRequest("shell_agent", null);

    assertEquals("shell_agent", request.getModel());
    assertNull(request.getInput());
  }
}