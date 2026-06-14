# 6. Connectors Framework (`connectors:core`)

## 6.1 Purpose

`connectors:core` is a reusable config-driven HTTP integration engine.
It executes connector definitions expressed in JSON/YAML with:

- templating
- auth strategy injection
- pagination strategies
- response mapping/classification
- retry policies

It can be used standalone or via Quarkus producer wiring.

## 6.2 Core Entry Point

`ConnectorExecutor`:

- `executeOnce(definition, context)`
- `executeAllPages(definition, context)`

Default implementation: `DefaultConnectorExecutor`.

## 6.3 ConnectorDefinition Model

`ConnectorDefinition` (a record) includes:

- `id`
- `appName`
- `endpoint` (`EndpointConfig`)
- `headers`
- `query`
- `body`
- `auth`
- `retryPolicy`
- `pagination`
- `responseMapping`
- `errorMappings`
- `strictUnresolvedVariables`

Defaults are applied in record constructor for null sub-configs.

## 6.4 Request Materialization

`RequestMaterializer` resolves final request components:

- URL from endpoint base+path + pagination URL overrides
- headers/query maps from templates
- auth strategy application
- body rendering by type:
  - JSON
  - `application/x-www-form-urlencoded`
  - text

## 6.5 Template Engine

`DefaultTemplateResolver` supports:

- inline `${expr}` and `{{ expr }}`
- full-expression replacement
- map/list recursive resolution
- directives:
  - `$expr`
  - `$template`
  - `$optional`
  - `$includeIf`

Expression evaluation is handled by `GroovySandboxEvaluator`.

## 6.6 Auth Strategies

Built-ins include:

- none
- basic
- bearer token
- api-key header
- query param

Registry: `AuthStrategyRegistry`.

## 6.7 Pagination Strategies

Built-ins include:

- none
- page
- offset
- cursor
- next-page-url

Registry: `PaginationStrategyRegistry`.

`executeAllPages` loops until done or `maxPages` reached and returns:

- per-page execution results
- aggregated mapped items
- truncation flag

## 6.8 Response Mapping and Error Classification

- extractor: `JsonPathResponseExtractor`
- mapper: `DefaultResponseMapper`
- classifier: `DefaultErrorClassifier`

Each execution yields `ConnectorExecutionResult` with success/error/retryable metadata.

## 6.9 Quarkus Producer Layer

`ConnectorCoreProducer` wires CDI beans for:

- `TemplateResolver`
- `AuthStrategyRegistry`
- `PaginationStrategyRegistry`
- `ResponseExtractor`
- `ConnectorConfigLoader`
- `ConnectorConfigValidator`
- `RequestMaterializer`
- `DefaultResponseMapper`
- `ConnectorExecutor`

This lets runtime components inject connector services directly in Quarkus apps.

## 6.10 In-Engine Usage Example

`WebSearchTool` loads classpath connector definitions:

- `/connectors/duckduckgo_instant_search.json` (for quick lookup when `detailed=false`)
- `/connectors/brave_web_search.json` (for detailed search when `detailed=true` or as fallback)

Then executes it via `ConnectorExecutor` with input map containing query text.
