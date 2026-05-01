# Graph Report - agent-engine  (2026-05-01)

## Corpus Check
- 571 files · ~175,617 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 3665 nodes · 9152 edges · 64 communities detected
- Extraction: 50% EXTRACTED · 50% INFERRED · 0% AMBIGUOUS · INFERRED: 4581 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 22|Community 22]]
- [[_COMMUNITY_Community 23|Community 23]]
- [[_COMMUNITY_Community 24|Community 24]]
- [[_COMMUNITY_Community 25|Community 25]]
- [[_COMMUNITY_Community 26|Community 26]]
- [[_COMMUNITY_Community 27|Community 27]]
- [[_COMMUNITY_Community 28|Community 28]]
- [[_COMMUNITY_Community 29|Community 29]]
- [[_COMMUNITY_Community 30|Community 30]]
- [[_COMMUNITY_Community 31|Community 31]]
- [[_COMMUNITY_Community 32|Community 32]]
- [[_COMMUNITY_Community 33|Community 33]]
- [[_COMMUNITY_Community 34|Community 34]]
- [[_COMMUNITY_Community 35|Community 35]]
- [[_COMMUNITY_Community 36|Community 36]]
- [[_COMMUNITY_Community 37|Community 37]]
- [[_COMMUNITY_Community 38|Community 38]]
- [[_COMMUNITY_Community 39|Community 39]]
- [[_COMMUNITY_Community 40|Community 40]]
- [[_COMMUNITY_Community 41|Community 41]]
- [[_COMMUNITY_Community 42|Community 42]]
- [[_COMMUNITY_Community 43|Community 43]]
- [[_COMMUNITY_Community 44|Community 44]]
- [[_COMMUNITY_Community 45|Community 45]]
- [[_COMMUNITY_Community 46|Community 46]]
- [[_COMMUNITY_Community 47|Community 47]]
- [[_COMMUNITY_Community 48|Community 48]]
- [[_COMMUNITY_Community 49|Community 49]]
- [[_COMMUNITY_Community 50|Community 50]]
- [[_COMMUNITY_Community 51|Community 51]]
- [[_COMMUNITY_Community 52|Community 52]]
- [[_COMMUNITY_Community 53|Community 53]]
- [[_COMMUNITY_Community 54|Community 54]]
- [[_COMMUNITY_Community 55|Community 55]]
- [[_COMMUNITY_Community 56|Community 56]]
- [[_COMMUNITY_Community 57|Community 57]]
- [[_COMMUNITY_Community 58|Community 58]]
- [[_COMMUNITY_Community 59|Community 59]]
- [[_COMMUNITY_Community 60|Community 60]]
- [[_COMMUNITY_Community 61|Community 61]]
- [[_COMMUNITY_Community 62|Community 62]]
- [[_COMMUNITY_Community 67|Community 67]]

## God Nodes (most connected - your core abstractions)
1. `of()` - 252 edges
2. `Builder` - 97 edges
3. `BuilderDefinitionUtils` - 54 edges
4. `SessionEvent` - 43 edges
5. `PlanningUtils` - 41 edges
6. `BaseAgentConfig` - 39 edges
7. `SessionActor` - 39 edges
8. `ImageUtils` - 37 edges
9. `AGUIMapperState` - 36 edges
10. `resolve()` - 36 edges

## Surprising Connections (you probably didn't know these)
- `disabled()` --calls--> `of()`  [INFERRED]
  connectors/core/src/main/java/com/agentengine/connectors/core/config/RetryPolicyConfig.java → util/common/src/main/java/com/agentengine/util/common/update/Update.java
- `empty()` --calls--> `of()`  [INFERRED]
  connectors/core/src/main/java/com/agentengine/connectors/core/runtime/RequestContext.java → util/common/src/main/java/com/agentengine/util/common/update/Update.java
- `of()` --calls--> `escalate()`  [INFERRED]
  util/common/src/main/java/com/agentengine/util/common/update/Update.java → agent/infra/src/main/java/com/agentengine/agent/infra/guardrails/GuardrailDecision.java
- `empty()` --calls--> `of()`  [INFERRED]
  connectors/core/src/main/java/com/agentengine/connectors/core/pagination/PaginationDirective.java → util/common/src/main/java/com/agentengine/util/common/update/Update.java
- `methodEnum()` --calls--> `valueOfOrDefault()`  [INFERRED]
  connectors/core/src/main/java/com/agentengine/connectors/core/config/EndpointConfig.java → knowledge/api/src/main/java/com/agentengine/knowledge/api/beans/SourceType.java

## Communities

### Community 0 - "Community 0"
Cohesion: 0.02
Nodes (41): AbstractLLM, AGUIEventMapperTest, AGUIToolCallMapper, BaseArtifactService, eventsAfter(), Builder, CloudStorageArtifactService, CollectionUtils (+33 more)

### Community 1 - "Community 1"
Cohesion: 0.02
Nodes (52): valueOfOrDefault(), BaseContextManager, valueOfOrDefault(), fromString(), valueOfOrDefault(), CompactionContextManager, CompleteTaskTool, valueOfOrDefault() (+44 more)

### Community 2 - "Community 2"
Cohesion: 0.01
Nodes (31): AbstractMongoReadRepository, AbstractMongoRepository, AgentAssetHandler, AgentRestAPI, AgentRestAPITest, AgentServiceImpl, AssetHandler, AssetRequest (+23 more)

### Community 3 - "Community 3"
Cohesion: 0.02
Nodes (63): AgentSession, AwaitAgentTool, Cache, ChunkingStage, Code, DetailKey, GuardrailConstants, ToolResultKey (+55 more)

### Community 4 - "Community 4"
Cohesion: 0.01
Nodes (46): AbstractLLM, AddEventMetadataPlugin, AgentRepository, AgentService, AgentService, AgentUtils, AssetClass, BaseAgentConfig (+38 more)

### Community 5 - "Community 5"
Cohesion: 0.02
Nodes (16): AGUIEventDecorator, AGUIEventMapper, AGUIMapperState, AGUITextMapper, BaseEvent, BaseReasoningEvent, BaseReasoningEvent, BroadcasterStateTest (+8 more)

### Community 6 - "Community 6"
Cohesion: 0.02
Nodes (52): ApiKeyHeaderAuthStrategy, AuthConfig(), typeEnum(), AuthStrategy, AuthStrategyRegistry, AuthTemplateUtils, AutoCloseable, BasicAuthStrategy (+44 more)

### Community 7 - "Community 7"
Cohesion: 0.03
Nodes (21): AdjustClarityTool, BuilderDefinitionUtils, Context, Animal, BuilderDefinitionUtilsTest, Cat, Dog, NestedConfig (+13 more)

### Community 8 - "Community 8"
Cohesion: 0.03
Nodes (19): AbstractJournalReadRepository, BaseEntity, ConnectionRepository, Filter, Filters, InfraConfig, LazyLoader, MongoUtils (+11 more)

### Community 9 - "Community 9"
Cohesion: 0.02
Nodes (29): BodyConfig(), typeEnum(), GroovySandboxEvaluatorTest, Guardrail, allow(), block(), escalate(), merge() (+21 more)

### Community 10 - "Community 10"
Cohesion: 0.03
Nodes (22): ChunkingPipeline, ChunkingStage, ChunkUtils, CosineBoundaryStage, EmbeddingStage, fromUrl(), HashUtils, IndexRequest (+14 more)

### Community 11 - "Community 11"
Cohesion: 0.02
Nodes (22): ActorSystemProviderTest, ApplicationConfig, ClasspathConnectorRegistry, CloudStorageInfraConfig, Config, ConnectorConfigLoader, ConnectorRegistry, DefaultConnectorConfigLoader (+14 more)

### Community 12 - "Community 12"
Cohesion: 0.03
Nodes (31): AbstractAgentFactory, AbstractToolsetProvider, close(), getTools(), Agent, Agent, Builder, AgentFactory (+23 more)

### Community 13 - "Community 13"
Cohesion: 0.03
Nodes (20): AgentValidator, deepCopySchema(), errors(), hasErrors(), isValid(), DefaultConnectorConfigValidatorTest, JsonMessageBodyReader, JsonMessageBodyWriter (+12 more)

### Community 14 - "Community 14"
Cohesion: 0.04
Nodes (17): AbstractJournalReadRepository, ActorSystemProvider, CompletionUtils, Confirmation, EventChannel, ExceptionUtils, InMemoryEventChannelTest, PekkoEventChannel (+9 more)

### Community 15 - "Community 15"
Cohesion: 0.03
Nodes (24): AssetNotFoundException, AuthStrategyException, ConfigurationException, ConnectorConfigLoadException, ConnectorConfigValidationException, ConnectorConfigValidator, ConnectorException, ConnectorExecutor (+16 more)

### Community 16 - "Community 16"
Cohesion: 0.04
Nodes (16): ClientProducer, CommunityRegistry, Connection, ConnectorAuthMaterialProvider, ConnectorAuthMaterialProviderImpl, DefaultConnectorServiceTest, GrepFilesToolTest, GRPCServerImplTest (+8 more)

### Community 17 - "Community 17"
Cohesion: 0.04
Nodes (12): ChatModelConfig, DelegatingModelFactory, GeminiModelFactory, LangchainModelFactory, fromType(), ModelConfig, type(), valueOfOrDefault() (+4 more)

### Community 18 - "Community 18"
Cohesion: 0.03
Nodes (23): AbstractAgentTool, AddTaskTool, ApplyPatchTool, BaseAgentState, BaseFileTool, BaseFileTool, CreatePlanTool, EchoTool (+15 more)

### Community 19 - "Community 19"
Cohesion: 0.05
Nodes (9): CloudStorageService, Convention, EncryptionService, EncryptionService, EncryptionServiceImpl, ModelAndAgentEndpointsIT, MongoClientFactory, SchemaAndCatalogEndpointsIT (+1 more)

### Community 20 - "Community 20"
Cohesion: 0.05
Nodes (20): AbstractAgentTool, BroadcasterCommand, BroadcasterEntity, BroadcasterFact, canReplayFrom(), latestPublishedSequence(), oldestRetainedSequence(), withPublished() (+12 more)

### Community 21 - "Community 21"
Cohesion: 0.04
Nodes (6): ChunkingPipelineFactory, ChunkingStrategy, CompactionContextManagerFactory, CompactionContextStrategyConfig, DefaultModelsConfig, KnowledgeSettings

### Community 22 - "Community 22"
Cohesion: 0.06
Nodes (12): EventMapper, GroovySandboxEvaluator, StrictMap, GRPCServerImpl, ServiceEntry(), active(), complete(), emit() (+4 more)

### Community 23 - "Community 23"
Cohesion: 0.08
Nodes (5): ImageUtils, InPlaceTileOperation, TileOperation, SessionUtils, StructuredConcurrencyUtils

### Community 24 - "Community 24"
Cohesion: 0.04
Nodes (11): ChildStartedFact, ChildStartFailedFact, ChildStartingFact, CompletedFact, ConfirmedFact, InitializedFact, MessageEnqueuedFact, ResumingFact (+3 more)

### Community 25 - "Community 25"
Cohesion: 0.07
Nodes (26): BaseJavaConventionsPlugin, BaseJavaConventionsPlugin, detect_file(), find_java_files(), is_needlessly_abbrev(), main(), parse_csv_set(), should_skip_path() (+18 more)

### Community 26 - "Community 26"
Cohesion: 0.05
Nodes (11): AddVignetteTool, AdjustColorTool, AdjustExposureTool, AdjustSplitToneTool, AdjustTemperatureTool, ImageEditingTool, InvokeAgentRequest, RuntimeService (+3 more)

### Community 27 - "Community 27"
Cohesion: 0.06
Nodes (10): ConnectorConfigValidator, ConnectorPaginationStrategy, CursorPaginationStrategy, HttpTransport, NextPageUrlPaginationStrategy, NoPaginationStrategy, OffsetPaginationStrategy, PagePaginationStrategy (+2 more)

### Community 28 - "Community 28"
Cohesion: 0.06
Nodes (5): AttachmentEvent, BaseCustomEvent, ConfirmationRequestedEvent, ConfirmedEvent, CorrectionEvent

### Community 29 - "Community 29"
Cohesion: 0.08
Nodes (8): SchemaRequestHandler, ToolConfigsHandler, ToolAssetHandler, ToolCatalog, ToolCatalog, ToolConfigsSchemaRequestHandler, ToolService, ToolService

### Community 30 - "Community 30"
Cohesion: 0.15
Nodes (8): AssetNotFoundService, AssetNotFoundServiceImpl, ConfigurationExceptionService, ConfigurationExceptionServiceImpl, FlowableAssetNotFoundService, FlowableAssetNotFoundServiceImpl, ThrowingService, ThrowingServiceImpl

### Community 31 - "Community 31"
Cohesion: 0.22
Nodes (4): DetailKey, Message, ParallelOrchestrationConstants, ViolationCode

### Community 32 - "Community 32"
Cohesion: 0.25
Nodes (1): SessionService

### Community 33 - "Community 33"
Cohesion: 0.33
Nodes (1): ShardedEntity

### Community 34 - "Community 34"
Cohesion: 0.33
Nodes (1): ToolsConfig

### Community 35 - "Community 35"
Cohesion: 0.5
Nodes (2): BaseCustomEvent, CustomEvent

### Community 36 - "Community 36"
Cohesion: 0.4
Nodes (1): Validator

### Community 37 - "Community 37"
Cohesion: 0.4
Nodes (1): Guardrail

### Community 38 - "Community 38"
Cohesion: 0.5
Nodes (1): AuthStrategy

### Community 39 - "Community 39"
Cohesion: 0.5
Nodes (1): ConnectorExecutor

### Community 40 - "Community 40"
Cohesion: 0.5
Nodes (2): ConnectorException, ConnectorExecutionException

### Community 41 - "Community 41"
Cohesion: 0.5
Nodes (1): TemplateFunctionProvider

### Community 42 - "Community 42"
Cohesion: 0.5
Nodes (1): ToolsetProvider

### Community 43 - "Community 43"
Cohesion: 0.67
Nodes (2): AbstractToolsetProvider, PlanningToolsetProvider

### Community 44 - "Community 44"
Cohesion: 0.5
Nodes (1): TextContentGuardrailFactory

### Community 45 - "Community 45"
Cohesion: 0.83
Nodes (3): ContainerRequestFilter, ContainerResponseFilter, RequestLoggingFilter

### Community 46 - "Community 46"
Cohesion: 0.5
Nodes (1): SchemaRequestHandler

### Community 47 - "Community 47"
Cohesion: 0.67
Nodes (1): ConnectorService

### Community 48 - "Community 48"
Cohesion: 0.67
Nodes (1): ConnectorAuthMaterialProvider

### Community 49 - "Community 49"
Cohesion: 0.67
Nodes (1): ErrorClassifier

### Community 50 - "Community 50"
Cohesion: 0.67
Nodes (1): TemplateResolver

### Community 51 - "Community 51"
Cohesion: 0.67
Nodes (1): ConnectorConfigLoader

### Community 52 - "Community 52"
Cohesion: 0.67
Nodes (1): HttpTransport

### Community 53 - "Community 53"
Cohesion: 0.67
Nodes (1): ShardedEntityDefinition

### Community 54 - "Community 54"
Cohesion: 0.67
Nodes (1): MicroServiceClientProvider

### Community 55 - "Community 55"
Cohesion: 0.67
Nodes (1): Constants

### Community 56 - "Community 56"
Cohesion: 0.67
Nodes (1): Config

### Community 57 - "Community 57"
Cohesion: 0.67
Nodes (1): InfraMongoRepository

### Community 58 - "Community 58"
Cohesion: 0.67
Nodes (1): InfraConfigService

### Community 59 - "Community 59"
Cohesion: 0.67
Nodes (1): SessionRepository

### Community 60 - "Community 60"
Cohesion: 0.67
Nodes (1): ContextManager

### Community 61 - "Community 61"
Cohesion: 0.67
Nodes (1): SessionEventChannel

### Community 62 - "Community 62"
Cohesion: 0.67
Nodes (1): KnowledgeRepository

### Community 67 - "Community 67"
Cohesion: 1.0
Nodes (1): MessagePart

## Knowledge Gaps
- **4 isolated node(s):** `SampleConfig`, `NestedConfig`, `SanitizeFixture`, `MessagePart`
  These have ≤1 connection - possible missing edges or undocumented components.
- **Thin community `Community 32`** (8 nodes): `SessionService.java`, `SessionService`, `.create()`, `.deleteSession()`, `.findSessions()`, `.getSession()`, `.getSessions()`, `.updateSession()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 33`** (6 nodes): `ShardedEntity`, `.commandHandler()`, `.emptyState()`, `.eventHandler()`, `.ShardedEntity()`, `ShardedEntity.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 34`** (6 nodes): `ToolsConfig`, `.getConfigs()`, `.setConfigs()`, `.setToolName()`, `.ToolsConfig()`, `ToolsConfig.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 35`** (5 nodes): `BaseCustomEvent`, `.BaseCustomEvent()`, `.getName()`, `CustomEvent`, `BaseCustomEvent.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 36`** (5 nodes): `Validator.java`, `Validator`, `.order()`, `.targetType()`, `.validate()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 37`** (5 nodes): `Guardrail.java`, `Guardrail`, `.evaluate()`, `.id()`, `.stage()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 38`** (4 nodes): `AuthStrategy`, `.apply()`, `.type()`, `AuthStrategy.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 39`** (4 nodes): `ConnectorExecutor`, `.executeAllPages()`, `.executeOnce()`, `ConnectorExecutor.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 40`** (4 nodes): `ConnectorException`, `ConnectorExecutionException`, `.ConnectorExecutionException()`, `ConnectorExecutionException.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 41`** (4 nodes): `TemplateFunctionProvider.java`, `TemplateFunctionProvider`, `.apply()`, `.name()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 42`** (4 nodes): `ToolsetProvider.java`, `ToolsetProvider`, `.create()`, `.descriptor()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 43`** (4 nodes): `AbstractToolsetProvider`, `PlanningToolsetProvider.java`, `PlanningToolsetProvider`, `.PlanningToolsetProvider()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 44`** (4 nodes): `TextContentGuardrailFactory.java`, `TextContentGuardrailFactory`, `.create()`, `.type()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 46`** (4 nodes): `SchemaRequestHandler.java`, `SchemaRequestHandler`, `.getAssetType()`, `.handle()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 47`** (3 nodes): `ConnectorService.java`, `ConnectorService`, `.execute()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 48`** (3 nodes): `ConnectorAuthMaterialProvider`, `.resolve()`, `ConnectorAuthMaterialProvider.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 49`** (3 nodes): `ErrorClassifier.java`, `ErrorClassifier`, `.classify()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 50`** (3 nodes): `TemplateResolver.java`, `TemplateResolver`, `.resolve()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 51`** (3 nodes): `ConnectorConfigLoader`, `.load()`, `ConnectorConfigLoader.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 52`** (3 nodes): `HttpTransport.java`, `HttpTransport`, `.execute()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 53`** (3 nodes): `ShardedEntityDefinition`, `.entity()`, `ShardedEntityDefinition.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 54`** (3 nodes): `MicroServiceClientProvider`, `.get()`, `MicroServiceClientProvider.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 55`** (3 nodes): `Constants`, `.Constants()`, `Constants.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 56`** (3 nodes): `Config`, `.getType()`, `Config.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 57`** (3 nodes): `InfraMongoRepository`, `.InfraMongoRepository()`, `InfraMongoRepository.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 58`** (3 nodes): `InfraConfigService`, `.findById()`, `InfraConfigService.java`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 59`** (3 nodes): `SessionRepository.java`, `SessionRepository`, `.SessionRepository()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 60`** (3 nodes): `ContextManager.java`, `ContextManager`, `.buildPrompt()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 61`** (3 nodes): `SessionEventChannel.java`, `SessionEventChannel`, `.SessionEventChannel()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 62`** (3 nodes): `KnowledgeRepository.java`, `KnowledgeRepository`, `.KnowledgeRepository()`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.
- **Thin community `Community 67`** (2 nodes): `MessagePart.java`, `MessagePart`
  Too small to be a meaningful cluster - may be noise or needs more connections extracted.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `of()` connect `Community 0` to `Community 1`, `Community 2`, `Community 3`, `Community 4`, `Community 5`, `Community 6`, `Community 7`, `Community 8`, `Community 9`, `Community 10`, `Community 11`, `Community 12`, `Community 13`, `Community 14`, `Community 15`, `Community 16`, `Community 18`, `Community 21`, `Community 22`, `Community 23`, `Community 24`, `Community 25`, `Community 26`, `Community 27`, `Community 29`, `Community 33`, `Community 43`?**
  _High betweenness centrality (0.170) - this node is a cross-community bridge._
- **Why does `ChatModelConfig` connect `Community 17` to `Community 4`?**
  _High betweenness centrality (0.024) - this node is a cross-community bridge._
- **Are the 251 inferred relationships involving `of()` (e.g. with `.injectsAuthMaterialsIntoContextAuthAndInputAuth()` and `.preservesExistingInputAuthAndOverlaysRepositoryAuth()`) actually correct?**
  _`of()` has 251 INFERRED edges - model-reasoned connections that need verification._
- **Are the 94 inferred relationships involving `Builder` (e.g. with `.init()` and `.upload()`) actually correct?**
  _`Builder` has 94 INFERRED edges - model-reasoned connections that need verification._
- **What connects `SampleConfig`, `NestedConfig`, `SanitizeFixture` to the rest of the system?**
  _4 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.02 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.02 - nodes in this community are weakly interconnected._