package com.agentengine.util.ms.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.exception.ConfigurationException;
import com.agentengine.util.ms.client.MicroService;
import com.agentengine.util.ms.grpc.Request;
import com.agentengine.util.ms.grpc.Response;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import io.reactivex.rxjava3.core.Flowable;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GRPCServerImplTest {

  @Test
  void shouldMapIllegalArgumentExceptionToInvalidArgumentStatus() throws Exception {
    final GRPCServerImpl grpcServer = new GRPCServerImpl(List.of(new ThrowingServiceImpl()));
    final Request request =
        Request.newBuilder()
            .setService(ThrowingService.class.getSimpleName())
            .setMethod("fail#")
            .build();
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Throwable> errorRef = new AtomicReference<>();

    grpcServer.execute(
        request,
        new StreamObserver<>() {
          @Override
          public void onNext(final Response value) {}

          @Override
          public void onError(final Throwable throwable) {
            errorRef.set(throwable);
            latch.countDown();
          }

          @Override
          public void onCompleted() {
            latch.countDown();
          }
        });

    final boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertThat(completed).isTrue();
    assertThat(errorRef.get()).isInstanceOf(StatusRuntimeException.class);
    final StatusRuntimeException statusException = (StatusRuntimeException) errorRef.get();
    assertThat(statusException.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  void shouldMapAssetNotFoundExceptionToNotFoundStatus() throws Exception {
    final GRPCServerImpl grpcServer = new GRPCServerImpl(List.of(new AssetNotFoundServiceImpl()));
    final Request request =
        Request.newBuilder()
            .setService(AssetNotFoundService.class.getSimpleName())
            .setMethod("fail#")
            .build();
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Throwable> errorRef = new AtomicReference<>();

    grpcServer.execute(
        request,
        new StreamObserver<>() {
          @Override
          public void onNext(final Response value) {}

          @Override
          public void onError(final Throwable throwable) {
            errorRef.set(throwable);
            latch.countDown();
          }

          @Override
          public void onCompleted() {
            latch.countDown();
          }
        });

    final boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertThat(completed).isTrue();
    assertThat(errorRef.get()).isInstanceOf(StatusRuntimeException.class);
    final StatusRuntimeException statusException = (StatusRuntimeException) errorRef.get();
    assertThat(statusException.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
  }

  @Test
  void shouldMapConfigurationExceptionToInvalidArgumentStatus() throws Exception {
    final GRPCServerImpl grpcServer =
        new GRPCServerImpl(List.of(new ConfigurationExceptionServiceImpl()));
    final Request request =
        Request.newBuilder()
            .setService(ConfigurationExceptionService.class.getSimpleName())
            .setMethod("fail#")
            .build();
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Throwable> errorRef = new AtomicReference<>();

    grpcServer.execute(
        request,
        new StreamObserver<>() {
          @Override
          public void onNext(final Response value) {}

          @Override
          public void onError(final Throwable throwable) {
            errorRef.set(throwable);
            latch.countDown();
          }

          @Override
          public void onCompleted() {
            latch.countDown();
          }
        });

    final boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertThat(completed).isTrue();
    assertThat(errorRef.get()).isInstanceOf(StatusRuntimeException.class);
    final StatusRuntimeException statusException = (StatusRuntimeException) errorRef.get();
    assertThat(statusException.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT);
  }

  @Test
  void shouldMapFlowableAssetNotFoundExceptionToNotFoundStatus() throws Exception {
    final GRPCServerImpl grpcServer =
        new GRPCServerImpl(List.of(new FlowableAssetNotFoundServiceImpl()));
    final Request request =
        Request.newBuilder()
            .setService(FlowableAssetNotFoundService.class.getSimpleName())
            .setMethod("stream#")
            .build();
    final CountDownLatch latch = new CountDownLatch(1);
    final AtomicReference<Throwable> errorRef = new AtomicReference<>();

    grpcServer.execute(
        request,
        new StreamObserver<>() {
          @Override
          public void onNext(final Response value) {}

          @Override
          public void onError(final Throwable throwable) {
            errorRef.set(throwable);
            latch.countDown();
          }

          @Override
          public void onCompleted() {
            latch.countDown();
          }
        });

    final boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertThat(completed).isTrue();
    assertThat(errorRef.get()).isInstanceOf(StatusRuntimeException.class);
    final StatusRuntimeException statusException = (StatusRuntimeException) errorRef.get();
    assertThat(statusException.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
  }

  @Test
  void shouldEmbedTypeInfoAndPreserveOrderForHeterogeneousBatch() throws Exception {
    final GRPCServerImpl grpcServer = new GRPCServerImpl(List.of(new MixedStreamServiceImpl()));
    final Request request =
        Request.newBuilder()
            .setService(MixedStreamService.class.getSimpleName())
            .setMethod("stream#")
            .build();
    final CountDownLatch latch = new CountDownLatch(1);
    final List<Response> responses = new CopyOnWriteArrayList<>();

    grpcServer.execute(
        request,
        new StreamObserver<>() {
          @Override
          public void onNext(final Response value) {
            responses.add(value);
          }

          @Override
          public void onError(final Throwable throwable) {
            latch.countDown();
          }

          @Override
          public void onCompleted() {
            latch.countDown();
          }
        });

    final boolean completed = latch.await(5, TimeUnit.SECONDS);
    assertThat(completed).isTrue();
    assertThat(responses).hasSize(1);
    final Response response = responses.getFirst();
    assertThat(response.getClassName()).isEmpty();

    final Type batchType =
        TypeFactory.defaultInstance().constructCollectionType(List.class, Parent.class);
    final List<?> batch = JsonUtils.fromJson(response.getPayload().toStringUtf8(), batchType, true);
    assertThat(batch)
        .containsExactly(new Child1("child1"), new Child2("child2"), new Child1("child1"));
  }

  @MicroService("agent")
  interface MixedStreamService {
    Flowable<Parent> stream();
  }

  static final class MixedStreamServiceImpl implements MixedStreamService {
    @Override
    public Flowable<Parent> stream() {
      return Flowable.just(new Child1(), new Child2(), new Child1());
    }
  }

  interface Parent {}

  record Child1(String value) implements Parent {
    Child1() {
      this("child1");
    }
  }

  record Child2(String value) implements Parent {
    Child2() {
      this("child2");
    }
  }

  @MicroService("agent")
  interface FlowableAssetNotFoundService {
    Flowable<String> stream();
  }

  static final class FlowableAssetNotFoundServiceImpl implements FlowableAssetNotFoundService {
    @Override
    public Flowable<String> stream() {
      return Flowable.error(new AssetNotFoundException("Agent", "agent-456"));
    }
  }

  @MicroService("agent")
  interface ThrowingService {
    String fail();
  }

  static final class ThrowingServiceImpl implements ThrowingService {
    @Override
    public String fail() {
      throw new IllegalArgumentException("bad request");
    }
  }

  @MicroService("agent")
  interface AssetNotFoundService {
    String fail();
  }

  static final class AssetNotFoundServiceImpl implements AssetNotFoundService {
    @Override
    public String fail() {
      throw new AssetNotFoundException("Agent", "agent-123");
    }
  }

  @MicroService("agent")
  interface ConfigurationExceptionService {
    String fail();
  }

  static final class ConfigurationExceptionServiceImpl implements ConfigurationExceptionService {
    @Override
    public String fail() {
      throw new ConfigurationException("invalid config");
    }
  }
}
