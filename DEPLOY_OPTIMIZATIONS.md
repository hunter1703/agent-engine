# Deployment Optimizations

This document describes the optimizations made to speed up the `deployEngine` task.

## Optimized Features

### 1. Conditional MongoDB Setup
The optimized deployment checks if MongoDB is already running before attempting to set it up again. This avoids rebuilding the Docker image and waiting for MongoDB to start if it's already available.

### 2. Smart Plugin Building
The system now checks timestamps to determine if plugins need to be rebuilt:
- Only rebuilds plugins when source files are newer than existing JARs
- Skips plugin building entirely if `--PskipPluginBuild=true` property is set
- Forces plugin rebuild if `--PforcePluginBuild=true` property is set

### 3. Faster Quarkus Development Mode
Configuration changes to speed up Quarkus startup:
- Disabled unnecessary dev services
- Optimized live reload settings
- Improved compilation flags

### 4. Gradle Performance Optimizations
Enabled various Gradle features for faster builds:
- Gradle Daemon
- Parallel execution
- Configuration caching
- Build caching
- File watching
- On-demand configuration

## Usage

### Standard optimized deployment:
```bash
./gradlew deployEngine
```

### Skip plugin building (if you haven't made plugin changes):
```bash
./gradlew deployEngine -PskipPluginBuild=true
```

### Force plugin rebuild (if needed):
```bash
./gradlew deployEngine -PforcePluginBuild=true
```

### Force MongoDB setup (if needed):
```bash
./gradlew deployEngine -PforceMongoSetup=true
```

## Performance Improvements

These optimizations should significantly reduce the time it takes to start the development environment by:
1. Avoiding unnecessary MongoDB container restarts
2. Skipping plugin rebuilds when sources haven't changed
3. Leveraging Gradle's performance features
4. Optimizing Quarkus startup configuration
5. Skipping tests and code formatting checks during deployment (Spotless, etc.)
6. Preserving configuration upserts to MongoDB while optimizing the overall process

Note: You may see configuration cache warnings during deployment, but these do not affect the functionality of the deployment. The deployment will complete successfully with all optimizations applied.