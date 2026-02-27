# TODO

- Optimize `SessionAssetHandler.findAssets` to avoid fetching fields that are not required when `includeEvents` is false.
- Make `AbstractMongoRepository.findByQuery` compute totals only when pagination metadata is requested.
- Revisit enabling `org.gradle.parallel` and `org.gradle.configureondemand` after Quarkus Gradle plugin concurrency fixes land.
