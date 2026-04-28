package com.agentengine.agent.infra.artifacts;

import static java.util.Collections.max;

import com.agentengine.util.common.service.CloudStorageService;
import com.google.adk.artifacts.BaseArtifactService;
import com.google.adk.artifacts.ListArtifactsResponse;
import com.google.common.collect.ImmutableList;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link BaseArtifactService} backed by the project's {@link CloudStorageService}.
 *
 * <p>Object key layout:
 * <pre>
 *   session-scoped:  {appName}/{userId}/{sessionId}/{filename}/{version}
 *   user-scoped:     {appName}/user/{userId}/{filename}/{version}   (filename prefixed with "user:")
 * </pre>
 *
 * <p>Version numbers start at 0 and increment by 1 on each save.
 */
@Singleton
public class CloudStorageArtifactService implements BaseArtifactService {

    private final CloudStorageService cloudStorageService;

    @Inject
    public CloudStorageArtifactService(final CloudStorageService cloudStorageService) {
        this.cloudStorageService = cloudStorageService;
    }

    @Override
    public Single<Integer> saveArtifact(
            String appName, String userId, String sessionId, String filename, Part artifact) {
        return listVersions(appName, userId, sessionId, filename)
                .map(versions -> versions.isEmpty() ? 0 : max(versions) + 1)
                .flatMap(nextVersion -> Single.fromCallable(() -> {
                    if (artifact.inlineData().isEmpty()) {
                        throw new IllegalArgumentException("Artifact must have inline data.");
                    }
                    final byte[] data = artifact.inlineData()
                            .get()
                            .data()
                            .orElseThrow(() -> new IllegalArgumentException("Artifact data must not be empty."));
                    final String mimeType =
                            artifact.inlineData().get().mimeType().orElse("application/octet-stream");
                    cloudStorageService.upload(
                            objectKey(appName, userId, sessionId, filename, nextVersion),
                            filename,
                            new ByteArrayInputStream(data),
                            data.length,
                            mimeType);
                    return nextVersion;
                }));
    }

    @Override
    public Maybe<Part> loadArtifact(
            String appName, String userId, String sessionId, String filename, Optional<Integer> version) {
        final Maybe<Integer> resolved = version.map(Maybe::just)
                .orElseGet(() -> listVersions(appName, userId, sessionId, filename)
                        .flatMapMaybe(vs -> vs.isEmpty() ? Maybe.empty() : Maybe.just(max(vs))));
        return resolved.observeOn(Schedulers.io()).map(v -> {
            final CloudStorageService.Content content =
                    cloudStorageService.download(objectKey(appName, userId, sessionId, filename, v));
            try (final InputStream inputStream = content.stream()) {
                return Part.fromBytes(inputStream.readAllBytes(), content.mimeType());
            }
        });
    }

    @Override
    public Single<ListArtifactsResponse> listArtifactKeys(String appName, String userId, String sessionId) {
        return Single.fromCallable(() -> {
            final Set<String> filenames = new HashSet<>();
            filenames.addAll(getFileNamesByPrefix(appName + "/" + userId + "/" + sessionId + "/"));
            filenames.addAll(getFileNamesByPrefix(appName + "/user/" + userId + "/"));
            return ListArtifactsResponse.builder()
                    .filenames(ImmutableList.sortedCopyOf(filenames))
                    .build();
        });
    }

    @Override
    public Completable deleteArtifact(String appName, String userId, String sessionId, String filename) {
        return listVersions(appName, userId, sessionId, filename).flatMapCompletable(versions -> {
            if (versions.isEmpty()) {
                return Completable.complete();
            }
            return Completable.fromAction(() -> versions.forEach(
                    v -> cloudStorageService.delete(objectKey(appName, userId, sessionId, filename, v))));
        });
    }

    @Override
    public Single<ImmutableList<Integer>> listVersions(
            String appName, String userId, String sessionId, String filename) {
        return Single.fromCallable(
                () -> cloudStorageService.list(keyPrefix(appName, userId, sessionId, filename)).stream()
                        .map(key -> key.substring(key.lastIndexOf('/') + 1))
                        .map(Integer::parseInt)
                        .sorted()
                        .collect(ImmutableList.toImmutableList()));
    }

    /**
     * Collects filenames from keys under {@code prefix}.
     * Session keys: {@code appName/userId/sessionId/filename/version} — filename at index 3.
     * User keys: {@code appName/user/userId/filename/version} — filename at index 3.
     */
    private Set<String> getFileNamesByPrefix(String prefix) {
        return cloudStorageService.list(prefix).stream()
                .map(key -> key.split("/"))
                .filter(segments -> segments.length > 3)
                .map(segments -> segments[3])
                .collect(Collectors.toSet());
    }

    public static String objectKey(String appName, String userId, String sessionId, String filename, int version) {
        return keyPrefix(appName, userId, sessionId, filename) + version;
    }

    private static String keyPrefix(String appName, String userId, String sessionId, String filename) {
        return filename != null && filename.startsWith("user:")
                ? appName + "/user/" + userId + "/" + filename + "/"
                : appName + "/" + userId + "/" + sessionId + "/" + filename + "/";
    }
}
