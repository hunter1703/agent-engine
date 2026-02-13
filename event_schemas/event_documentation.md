# Codex Responses API Event Documentation

This document provides detailed information about each event type in the Codex Responses API, including their usage, scenarios for use and non-use, and differentiating factors between similar events.

## Event Types Overview

### 1. `response.created`

#### Description
Indicates that a response has been successfully created and is beginning processing.

#### Usage
- Sent immediately when a response is initiated
- Contains metadata about the response including ID, model, and status

#### Example Scenario When to Use
- When your server receives a new request and creates a response object
- Before any processing begins on the user's request
- Example: After receiving a request for "Summarize recent commits", send this event to acknowledge the request

#### Example Scenario When NOT to Use
- Do not send this event multiple times for the same response
- Do not send this event if the request fails validation before a response object is created
- Example: If the API key is invalid, do not send this event

#### Differentiating Factors
- Unlike `response.in_progress`, this event only signals creation, not ongoing processing
- Unlike `response.completed` or `response.done`, this only signals the start of processing

---

### 2. `response.in_progress`

#### Description
Signals that the response is actively being processed but is not yet complete.

#### Usage
- Sent periodically during long-running operations
- Indicates that processing is continuing normally

#### Example Scenario When to Use
- During complex multi-step operations that take time
- When calling external APIs or performing computation-heavy tasks
- Example: While the agent is planning how to approach a complex coding task

#### Example Scenario When NOT to Use
- For very quick responses that complete almost instantly
- After the response has been completed or failed
- Example: For simple text echo operations that complete in milliseconds

#### Differentiating Factors
- Unlike `response.created`, this indicates active processing is happening
- Unlike `response.output_item.added`, this doesn't provide actual output content
- Unlike `response.completed` or `response.done`, this indicates processing is still ongoing

---

### 3. `response.output_item.added`

#### Description
Signals that a new output item has been added to the response.

#### Usage
- Sent when a new message, function call, or other output item is ready
- Includes the complete item data

#### Example Scenario When to Use
- When the agent generates a new message to send to the user
- When the agent decides to make a function call
- Example: When the agent generates "Let me check your recent commits" as a message item

#### Example Scenario When NOT to Use
- When updating existing content (use delta events instead)
- When the response is complete and no more items will be added
- Example: Don't send this after `response.done` has been sent

#### Differentiating Factors
- Unlike `response.output_text.delta`, this sends a complete item, not incremental text
- Unlike `response.created`, this contains actual output content
- Unlike `response.output_item.done`, this signals addition, not completion of an item

---

### 4. `response.output_item.done`

#### Description
Signals that an output item has been fully processed and finalized.

#### Usage
- Sent when an output item is complete and won't receive further updates
- Often follows `response.output_item.added` for the same item

#### Example Scenario When to Use
- After a message item has received all its content through delta events
- When a function call item is fully formed and ready to execute
- Example: After sending all text deltas for a response message, send this to mark it complete

#### Example Scenario When NOT to Use
- Before the output item is fully formed
- For items that are still receiving updates
- Example: Don't send this for a message that is still streaming text

#### Differentiating Factors
- Unlike `response.output_item.added`, this signals completion of an item
- Unlike `response.output_text.delta`, this doesn't contain new content, just completion status
- Unlike `response.done`, this refers to a single item, not the entire response

---

### 5. `response.output_text.delta`

#### Description
Provides incremental text content for an output item.

#### Usage
- Sent to stream text content incrementally
- Used for real-time text display in the UI

#### Example Scenario When to Use
- When streaming a response message word by word or sentence by sentence
- For providing real-time feedback as text is generated
- Example: Sending "Hello" then "Hello!" then "Hello! How" as the agent generates text

#### Example Scenario When NOT to Use
- When sending complete structured items (use `response.output_item.added`)
- When the text content is empty
- Example: Don't send this with empty delta strings

#### Differentiating Factors
- Unlike `response.output_item.added`, this sends incremental content, not complete items
- Unlike `response.output_item.done`, this adds content rather than marking completion
- Specifically for text content, unlike other delta events for different content types

---

### 6. `response.reasoning_summary_text.delta`

#### Description
Provides incremental text for reasoning summary content.

#### Usage
- Sent to stream reasoning summary content incrementally
- Used for displaying the agent's thought process

#### Example Scenario When to Use
- When the agent is explaining its reasoning step-by-step
- For streaming internal thoughts that summarize the approach
- Example: Sending "First I'll check the current directory..." as the agent explains its plan

#### Example Scenario When NOT to Use
- For the main response to the user (use `response.output_text.delta`)
- When no reasoning summary is being generated
- Example: Don't use this for direct user-facing responses

#### Differentiating Factors
- Unlike `response.output_text.delta`, this is for internal reasoning, not user-facing output
- Unlike `response.reasoning_text.delta`, this is for summary content specifically
- Has `summary_index` instead of `content_index`

---

### 7. `response.reasoning_text.delta`

#### Description
Provides incremental text for detailed reasoning content.

#### Usage
- Sent to stream detailed reasoning content incrementally
- Used for displaying granular thought processes

#### Example Scenario When to Use
- When the agent is detailing its step-by-step thinking
- For streaming detailed analysis that isn't part of the main response
- Example: Sending "Analyzing the code structure..." as the agent performs detailed analysis

#### Example Scenario When NOT to Use
- For the main response to the user (use `response.output_text.delta`)
- For summary-level reasoning (use `response.reasoning_summary_text.delta`)
- Example: Don't use this for high-level plan summaries

#### Differentiating Factors
- Unlike `response.reasoning_summary_text.delta`, this is for detailed, granular content
- Unlike `response.output_text.delta`, this is for internal reasoning, not user-facing output
- Has `content_index` instead of `summary_index`

---

### 8. `response.reasoning_summary_part.added`

#### Description
Signals that a new reasoning summary part has been added.

#### Usage
- Sent when a new section of reasoning summary is ready
- Used to structure the reasoning process

#### Example Scenario When to Use
- When the agent begins a new phase of reasoning
- When adding a new summary section to the reasoning
- Example: When starting a new analysis phase like "Code Review Phase 2"

#### Example Scenario When NOT to Use
- For detailed content within a reasoning section (use delta events)
- When the reasoning process is complete
- Example: Don't send this after all reasoning is finished

#### Differentiating Factors
- Unlike `response.reasoning_summary_text.delta`, this signals addition of a section, not content
- Unlike `response.output_item.added`, this is specifically for reasoning sections
- Only includes index information, not content

---

### 9. `response.failed`

#### Description
Signals that the response has failed and will not complete successfully.

#### Usage
- Sent when an unrecoverable error occurs during processing
- Contains error information for the client

#### Example Scenario When to Use
- When API limits are exceeded
- When a required resource is unavailable
- When the input is invalid and cannot be processed
- Example: Sending this when the model encounters a context length exceeded error

#### Example Scenario When NOT to Use
- When processing is still ongoing (use `response.in_progress`)
- When the response completes successfully (use `response.completed` or `response.done`)
- Example: Don't send this if the response is still being generated

#### Differentiating Factors
- Unlike other events, this signals failure rather than progress
- Unlike `response.done`, this indicates an unsuccessful completion
- Contains error information that other events don't have

---

### 10. `response.completed`

#### Description
Signals that the response has been logically completed with all content.

#### Usage
- Sent when all response content has been generated
- Contains final usage statistics and complete output

#### Example Scenario When to Use
- After all output items have been sent and the response is complete
- When the agent has finished addressing the user's request
- Example: After sending all messages and function calls, send this to indicate logical completion

#### Example Scenario When NOT to Use
- When processing is still ongoing
- When an error occurs (use `response.failed`)
- Example: Don't send this before all planned output items have been sent

#### Differentiating Factors
- Unlike `response.done`, this focuses on logical completion with full output data
- Unlike `response.output_item.done`, this refers to the entire response
- Contains complete output array with all items

---

### 11. `response.done`

#### Description
Signals that the response stream is complete and no more events will follow.

#### Usage
- Sent as the final event in the response stream
- May contain final usage statistics

#### Example Scenario When to Use
- As the final event after all other events have been sent
- When the server is finished sending all events for this response
- Example: Send this as the absolute last event in the stream

#### Example Scenario When NOT to Use
- Before all intended content has been sent
- When the response fails (use `response.failed`)
- Example: Don't send this before sending `response.completed` if you plan to send it

#### Differentiating Factors
- Unlike `response.completed`, this focuses on stream completion rather than logical content completion
- Unlike all other events, this signals the end of the event stream
- May be sent with or without complete output data
- This is the definitive "last event" marker

---

## Event Flow Patterns

### Successful Response Flow
1. `response.created` - Response is created
2. `response.in_progress` - Processing begins (optional, may repeat)
3. `response.output_item.added` - First output item
4. `response.output_text.delta` - Streaming text content (may repeat)
5. `response.output_item.done` - First output item complete
6. Repeat steps 3-5 for additional items
7. `response.completed` - Logical completion with full output
8. `response.done` - Stream completion

### Failed Response Flow
1. `response.created` - Response is created
2. `response.in_progress` - Processing begins (optional)
3. `response.failed` - Failure occurs with error details
4. `response.done` - Stream ends (optional, depends on implementation)

## Important Notes

- Events should generally follow a logical progression from creation to completion
- The `response.done` event is the definitive end marker for the stream
- Usage information may appear in both `response.completed` and `response.done`
- The distinction between `response.completed` and `response.done` is subtle but important: one indicates logical completion, the other stream completion
- Index fields (`output_index`, `summary_index`, `content_index`) help maintain ordering of streamed content