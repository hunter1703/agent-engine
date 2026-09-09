package com.agentengine.util.ms.server;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.util.common.builder.annotations.*;
import com.agentengine.util.ms.client.GrpcJsonCodec;
import com.agentengine.util.ms.client.MicroService;
import com.agentengine.util.ms.client.MicroServiceInvocationHandler;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.reactivex.rxjava3.core.Flowable;
import java.util.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class GRPCServerImplTest {

  private static GrpcJsonCodec grpcCodec() {
    return new GrpcJsonCodec(List.of());
  }

  private io.grpc.Server server;
  private io.grpc.ManagedChannel channel;
  private MicroServiceInvocationHandler handler;
  private Service client;

  @org.junit.jupiter.api.BeforeEach
  public void setup() throws Exception {
    GrpcJsonCodec jsonCodec = grpcCodec();
    GRPCServerImpl serverImpl = new GRPCServerImpl(List.of(new ServiceImpl()), jsonCodec);

    String serverName = io.grpc.inprocess.InProcessServerBuilder.generateName();
    server =
        io.grpc.inprocess.InProcessServerBuilder.forName(serverName)
            .directExecutor()
            .addService(serverImpl)
            .build()
            .start();
    channel =
        io.grpc.inprocess.InProcessChannelBuilder.forName(serverName).directExecutor().build();

    handler = new MicroServiceInvocationHandler(Service.class, () -> channel, jsonCodec, false);
    client =
        (Service)
            java.lang.reflect.Proxy.newProxyInstance(
                Service.class.getClassLoader(), new Class<?>[] {Service.class}, handler);
  }

  @AfterEach
  public void teardown() throws Exception {
    if (channel != null) {
      channel.shutdownNow();
    }
    if (server != null) {
      server.shutdownNow();
    }
  }

  @Test
  public void testPolymorphic() throws Exception {
    Child3 child3Arg = new Child3(new Child1("test"));

    // Test that the method successfully invokes and deserializes via proxy
    Parent<?> result = client.polymorphic(child3Arg);
    assertThat(result).isEqualTo(new Child3(new Child1("alpha")));
  }

  @Test
  public void testStream() throws Exception {
    Flowable<Parent<?>> flowable = client.stream();
    List<Parent<?>> allItems = flowable.toList().blockingGet();

    assertThat(allItems).hasSize(4);
    assertThat(allItems.get(0)).isEqualTo(new Child1("child1"));
    assertThat(allItems.get(1)).isEqualTo(new Child2(2));
    assertThat(allItems.get(3)).isEqualTo(new Child3(new Child1("nested")));
  }

  @Test
  public void testList() throws Exception {
    List<Parent<?>> result = client.list();

    assertThat(result).isInstanceOf(List.class);
    assertThat(result).hasSize(4);
    assertThat(result.get(0)).isEqualTo(new Child1("child1"));
  }

  @Test
  public void testLinkedList() throws Exception {
    LinkedList<Parent<?>> result = client.linkedList();

    assertThat(result).isInstanceOf(LinkedList.class);
    assertThat(result).hasSize(4);
  }

  @Test
  public void testBasic() throws Exception {
    Child3 child3Arg = new Child3(new Child1("test"));
    Boolean result = client.basic(child3Arg);

    assertThat(result).isEqualTo(false);
  }

  @Test
  public void testStreamNestedGenerics() throws Exception {
    final List<GenericParent<InnerParent>> result =
        client.streamNestedGenerics().toList().blockingGet();

    assertThat(result).hasSize(3);

    assertThat(result.get(0)).isInstanceOf(GenericChild1.class);
    assertThat(result.get(0).value).isInstanceOf(InnerChild1.class);
    assertThat(((InnerChild1) result.get(0).value).name).isEqualTo("inner1");

    assertThat(result.get(1)).isInstanceOf(GenericChild2.class);
    assertThat(result.get(1).value).isInstanceOf(InnerChild2.class);
    assertThat(((InnerChild2) result.get(1).value).name).isEqualTo("inner2");

    assertThat(result.get(2)).isInstanceOf(GenericChild1.class);
    assertThat(result.get(2).value).isInstanceOf(InnerChild2.class);
    assertThat(((InnerChild2) result.get(2).value).name).isEqualTo("inner3");
  }

  private abstract static class Parent<T> {

    private T value;

    public Parent() {}

    public Parent(T value) {
      this.value = value;
    }

    public T getValue() {
      return value;
    }

    public void setValue(T value) {
      this.value = value;
    }

    @Override
    public boolean equals(Object o) {
      if (o == null || getClass() != o.getClass()) return false;
      Parent<?> parent = (Parent<?>) o;
      return Objects.equals(value, parent.value);
    }

    @Override
    public int hashCode() {
      return Objects.hashCode(value);
    }
  }

  private static class Child1 extends Parent<String> {

    public Child1() {}

    public Child1(String value) {
      super(value);
    }
  }

  private static class Child2 extends Parent<Integer> {
    public Child2() {}

    public Child2(Integer value) {
      super(value);
    }
  }

  private static class Child3 extends Parent<Child1> {
    public Child3() {}

    public Child3(Child1 value) {
      super(value);
    }
  }

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.CLASS,
      include = JsonTypeInfo.As.PROPERTY,
      property = "@class")
  private static class GenericParent<T> {
    public T value;

    public GenericParent() {}

    public GenericParent(T value) {
      this.value = value;
    }
  }

  private static class GenericChild1<T> extends GenericParent<T> {
    public GenericChild1() {}

    public GenericChild1(T value) {
      super(value);
    }
  }

  private static class GenericChild2<T> extends GenericParent<T> {
    public GenericChild2() {}

    public GenericChild2(T value) {
      super(value);
    }
  }

  @JsonTypeInfo(
      use = JsonTypeInfo.Id.CLASS,
      include = JsonTypeInfo.As.PROPERTY,
      property = "@class")
  private static class InnerParent {
    public String name;

    public InnerParent() {}

    public InnerParent(String name) {
      this.name = name;
    }
  }

  private static class InnerChild1 extends InnerParent {
    public InnerChild1() {}

    public InnerChild1(String name) {
      super(name);
    }
  }

  private static class InnerChild2 extends InnerParent {
    public InnerChild2() {}

    public InnerChild2(String name) {
      super(name);
    }
  }

  @MicroService("agent")
  private interface Service {
    Flowable<Parent<?>> stream();

    List<Parent<?>> list();

    LinkedList<Parent<?>> linkedList();

    Parent<?> polymorphic(Child3 arg);

    boolean basic(Child3 arg);

    Flowable<GenericParent<InnerParent>> streamNestedGenerics();
  }

  private static final class ServiceImpl implements Service {
    @Override
    public Flowable<Parent<?>> stream() {
      return Flowable.just(
          new Child1("child1"),
          new Child2(2),
          new Child1("child1"),
          new Child3(new Child1("nested")));
    }

    @Override
    public List<Parent<?>> list() {
      return new ArrayList<>(
          List.of(
              new Child1("child1"),
              new Child2(2),
              new Child1("child1"),
              new Child3(new Child1("nested"))));
    }

    @Override
    public LinkedList<Parent<?>> linkedList() {
      return new LinkedList<>(list());
    }

    @Override
    public Parent<?> polymorphic(Child3 arg) {
      return new Child3(new Child1("alpha"));
    }

    @Override
    public boolean basic(Child3 arg) {
      return false;
    }

    @Override
    public Flowable<GenericParent<InnerParent>> streamNestedGenerics() {
      return Flowable.just(
          new GenericChild1<>(new InnerChild1("inner1")),
          new GenericChild2<>(new InnerChild2("inner2")),
          new GenericChild1<>(new InnerChild2("inner3")));
    }
  }
}
