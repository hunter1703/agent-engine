# TODO

- Optimize `SessionAssetHandler.findAssets` to avoid fetching fields that are not required when `includeEvents` is false.
- Make `AbstractMongoRepository.findByQuery` compute totals only when pagination metadata is requested.
