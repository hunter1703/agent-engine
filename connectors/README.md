# Agent Engine REST Connectors

The Config-Driven REST Connector Framework provides a powerful, secure, and extensible way to define and execute HTTP connectors using JSON or YAML configurations. It is designed to be highly portable as a Java library or embedded natively within a Quarkus application.

## Quick Start
Add the dependency to your project:
```gradle
implementation project(':connectors:core')
```
For Quarkus applications, the core components such as `ConnectorExecutor`, `RequestMaterializer`, and `TemplateResolver` are automatically produced as CDI beans via the `ConnectorCoreProducer` and can be injected directly without additional configuration.

## Execution API
The core entry point is `ConnectorExecutor`, providing two execution paths:
* `executeOnce(definition, context)`: Materializes exactly one request, executes it, and returns a single `ConnectorExecutionResult`.
* `executeAllPages(definition, context)`: Evaluates pagination directives and continuously fetches all pages up to the defined limit, returning a `PaginatedExecutionResult` containing all aggregated list items.

## Context Variables
When resolving configuration templates, the framework makes the following root variables available in the `RequestContext`:
* `input`: A user-supplied `Map` of inputs for the connector.
* `rawPayload`: The original triggering event or raw message payload.
* `auth`: A `Map` of resolved authentication tokens or credentials securely merged at runtime.
* `previous`: A `Map` containing data and metadata from the previous page execution. This is primarily used for pagination template expressions.
* `vars`: Any custom dynamic variables injected during evaluation or materialization.

## Template Syntax & Directives
Templates evaluate via a deeply protected embedded Groovy Sandbox that prevents unauthorized classloading, reflection, or filesystem interaction. By default, missing variables will intentionally throw an execution exception to prevent silently malformed API calls.

* **Inline Expressions**: Use `{{ expression }}` or `${expression}` syntax to combine text and logic.
  ```json
  { "url": "https://{{ vars.env }}.api.example.com" }
  ```
* **`$expr` Directive**: Evaluates a pure data expression, returning the output type.
  ```json
  { "id": { "$expr": "vars.id * 10" } }
  ```
* **`$optional` Directive**: Safely omits the JSON key entirely if the target variable is missing.
  ```json
  { "metadata_tag": { "$optional": "vars.optionalTag" } }
  ```
* **`$includeIf` Directive**: Conditionally includes the entire nested template map if the expression evaluates to true.
  ```json
  {
     "$includeIf": "vars.includeMetadata",
     "timestamp": "2026-01-01T00:00:00Z"
  }
  ```

## Extensibility Points (SPI)
The framework defaults are extensive, but you can override or extend capability by implementing its SPIs:
* `AuthStrategy`: Implement specialized authentication flows (e.g. mutual TLS, OAuth signature generation).
* `PaginationStrategy`: Add custom server-specific pagination patterns.
* `ResponseExtractor`: Implement custom, high-performance payload parsing.

## Security Restrictions
The `GroovySandboxEvaluator` uses an aggressive AST Customizer to secure execution:
* Blocks system, environment, runtime, reflective, internal, and classloader Java mechanisms.
* Blocks external script imports and closures.
* Strictly bounds execution time via Virtual Thread timeouts to prevent infinite regex loops or thread starvation.
* Rejects multi-line evaluation and expressions over configured length limits.

## Examples
Runnable configurations can be found in the [examples](./examples) director:
* [Simple Static GET](examples/example1.json)
* [Dynamic JSON POST](examples/example2.json)
* [Cursor Pagination](examples/example3.json)
