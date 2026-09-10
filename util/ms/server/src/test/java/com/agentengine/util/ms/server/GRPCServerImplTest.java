package com.agentengine.util.ms.server;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agentengine.util.common.DefaultJsonCodec;
import com.agentengine.util.common.testfixtures.Child1;
import com.agentengine.util.common.testfixtures.Child1Variant;
import com.agentengine.util.common.testfixtures.Child3;
import com.agentengine.util.common.testfixtures.Child4;
import com.agentengine.util.common.testfixtures.ConcreteGrandchild;
import com.agentengine.util.common.testfixtures.ConcreteGreatGrandchild;
import com.agentengine.util.common.testfixtures.InnerChild1;
import com.agentengine.util.common.testfixtures.InnerChild2;
import com.agentengine.util.common.testfixtures.InnerParent;
import com.agentengine.util.common.testfixtures.Parent;
import com.agentengine.util.ms.client.MicroService;
import com.agentengine.util.ms.client.MicroServiceInvocationHandler;
import io.reactivex.rxjava3.core.Flowable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end round trip through the real gRPC transport (in-process server + {@link
 * DefaultJsonCodec}'s {@code JAVA_LANG_OBJECT} default typing — gRPC uses the same codec as every
 * other internal wire format now), covering the shapes that matter: {@code Object}/erased-generic
 * slots recovered via default typing, explicit {@code @JsonTypeInfo}-annotated polymorphism, and
 * both nested inside each other, across both streaming ({@link Flowable}) and blocking ({@link
 * List}) returns, plus plain scalars.
 *
 * <p>Container-shape tests (List/Set/Map/Optional/nested-collection mechanics) use the
 * self-describing {@link InnerParent}/{@link InnerChild1}/{@link InnerChild2} hierarchy — under
 * {@code JAVA_LANG_OBJECT} a declared-{@code Parent<?>} slot does NOT recover an unannotated
 * subtype (proven by {@link com.agentengine.util.common.DefaultJsonCodecTest
 * #listElementsDeclaredAsAbstractParentAreNotRecovered} and, initially, the hard way here — this
 * file used to rely on {@code GrpcJsonCodec}'s {@code NON_FINAL} for exactly that, which no longer
 * exists). {@link Parent}/{@link Child1}/{@link Child3} stay in use only where a test's actual
 * point is that gap itself, not container mechanics.
 */
class GRPCServerImplTest {

  private static DefaultJsonCodec grpcCodec() {
    return new DefaultJsonCodec(List.of());
  }

  private io.grpc.Server server;
  private io.grpc.ManagedChannel channel;
  private MicroServiceInvocationHandler handler;
  private Service client;

  @org.junit.jupiter.api.BeforeEach
  public void setup() throws Exception {
    DefaultJsonCodec jsonCodec = grpcCodec();
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
    InnerChild1<String> arg = new InnerChild1<>("test");

    InnerParent<?> result = client.polymorphic(arg);
    assertThat(result).isEqualTo(new InnerChild2<>("alpha"));
  }

  @Test
  public void testStream() throws Exception {
    Flowable<InnerParent<?>> flowable = client.stream();
    List<InnerParent<?>> allItems = flowable.toList().blockingGet();

    assertThat(allItems).hasSize(4);
    assertThat(allItems.get(0)).isEqualTo(new InnerChild1<>("child1"));
    assertThat(allItems.get(1)).isEqualTo(new InnerChild2<>(2));
    assertThat(allItems.get(3)).isEqualTo(new InnerParent<>("plain"));
  }

  @Test
  public void testList() throws Exception {
    List<InnerParent<?>> result = client.list();

    assertThat(result).isInstanceOf(List.class);
    assertThat(result).hasSize(4);
    assertThat(result.get(0)).isEqualTo(new InnerChild1<>("child1"));
  }

  @Test
  public void testLinkedList() throws Exception {
    LinkedList<InnerParent<?>> result = client.linkedList();

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
  public void booleanBoxedRoundTrips() throws Exception {
    assertThat(client.booleanBoxed()).isTrue();
  }

  @Test
  public void booleanPrimitiveRoundTrips() throws Exception {
    assertThat(client.booleanPrimitive()).isTrue();
  }

  @Test
  public void stringRoundTrips() throws Exception {
    assertThat(client.string()).isEqualTo("hello");
  }

  /**
   * {@code Publisher<Parent<InnerChild>>}: outer wrapper (Child4) is fixed, but its declared-type
   * ({@code InnerParent<String>}) field varies at runtime between {@code InnerParent}, {@link
   * InnerChild1} and {@link InnerChild2} — exercises the explicitly-annotated inner hierarchy
   * nested inside the outer one, in a heterogeneous stream.
   */
  @Test
  public void streamOfOuterWrappingHeterogeneousAnnotatedInner() throws Exception {
    final List<Child4> result = client.streamAnnotatedInner().toList().blockingGet();

    assertThat(result).hasSize(3);
    assertThat(result.get(0).getValue())
        .isInstanceOf(InnerParent.class)
        .isEqualTo(new InnerParent<>("plain"));
    assertThat(result.get(1).getValue())
        .isInstanceOf(InnerChild1.class)
        .isEqualTo(new InnerChild1<>("one"));
    assertThat(result.get(2).getValue())
        .isInstanceOf(InnerChild2.class)
        .isEqualTo(new InnerChild2<>("two"));
  }

  /** {@code List<Parent<InnerParent>>}: the same shape, but as a single blocking value. */
  @Test
  public void listOfOuterWrappingAnnotatedInner() throws Exception {
    final List<Child4> result = client.listAnnotatedInner();

    assertThat(result).hasSize(2);
    assertThat(result.get(0).getValue()).isEqualTo(new InnerParent<>("plain"));
    assertThat(result.get(1).getValue()).isEqualTo(new InnerChild1<>("one"));
  }

  /**
   * {@code Publisher<Parent<InnerParent<String>>>}: the Publisher's own element type is itself
   * parameterized (not a plain named class like {@link Child4}). {@code
   * MicroServiceInvocationHandler.firstTypeArgument} used to collapse this down to raw {@code
   * Object.class} on the client while {@link GRPCServerImpl} serialized against the fully-resolved
   * {@code Parent<InnerParent<String>>} on the server -- a real client/server declared-type
   * mismatch that {@code NON_FINAL} used to paper over (it tagged {@code Child4} regardless of
   * which side's notion of "declared type" was used) and {@code JAVA_LANG_OBJECT} does not. Fixed
   * by making {@code firstTypeArgument} preserve the full {@link java.lang.reflect.Type} instead of
   * reducing it to a {@code Class}, so both sides now agree -- but {@code Child4}/{@link Parent}
   * are still deliberately unannotated, and {@code Parent} is abstract, so the now-consistent
   * declared type still can't be recovered without self-description. Proves the client/server
   * resolution is at least consistent now (a clean {@code InvalidDefinitionException}, not the
   * confusing {@code InvalidTypeIdException} the asymmetry used to produce).
   */
  @Test
  public void streamNestedGenericsIsNotRecoveredWithoutSelfDescribing() throws Exception {
    assertThatThrownBy(() -> client.streamNestedGenerics().toList().blockingGet())
        .isInstanceOf(RuntimeException.class);
  }

  /**
   * {@code Child1} is a concrete, unannotated class declared directly (not {@code Object} or
   * abstract) — under {@code JAVA_LANG_OBJECT}, its declared slot gets no {@code @class} tag, so a
   * runtime {@link Child1Variant} subclass silently loses its extra field and comes back as a plain
   * {@code Child1} (unknown properties are dropped, not rejected — see {@code
   * FAIL_ON_UNKNOWN_PROPERTIES=false} in {@code JsonUtils}). A concrete type used as an extension
   * point must self-describe at the class level to survive this; see {@link Child1Variant}'s
   * javadoc.
   */
  @Test
  public void concreteDeclaredTypeDoesNotPreserveARuntimeSubclassWithoutSelfDescribing()
      throws Exception {
    final Child1 result = client.concreteDeclaredReturn();

    assertThat(result).isExactlyInstanceOf(Child1.class);
    assertThat(result.getValue()).isEqualTo("base-value");
  }

  /**
   * Same gap as {@link #concreteDeclaredTypeDoesNotPreserveARuntimeSubclassWithoutSelfDescribing},
   * but for an arg.
   */
  @Test
  public void concreteDeclaredArgumentDoesNotPreserveARuntimeSubclassWithoutSelfDescribing()
      throws Exception {
    final Child1 echoed = client.concreteDeclaredArg(new Child1Variant("base", "round-tripped"));

    assertThat(echoed).isExactlyInstanceOf(Child1.class);
    assertThat(echoed.getValue()).isEqualTo("base");
  }

  /**
   * A heterogeneous {@code List<InnerParent<?>>} passed as a method ARGUMENT, not just returned.
   */
  @Test
  public void listArgumentPreservesEachElementsConcreteType() throws Exception {
    final List<InnerParent<?>> arg =
        List.of(new InnerChild1<>("a"), new InnerChild2<>(2), new InnerParent<>("plain"));

    final int subtypeCount = client.countSubtypes(arg);

    assertThat(subtypeCount).isEqualTo(3);
  }

  /** {@code Set<InnerParent<?>>} (interface-declared) as a return type. */
  @Test
  public void setReturnTypePreservesElementConcreteTypes() throws Exception {
    final Set<InnerParent<?>> result = client.setReturn();

    assertThat(result).hasSize(2);
    assertThat(result).contains(new InnerChild1<>("s1"), new InnerParent<>("s2"));
  }

  /**
   * {@code HashSet<InnerParent<?>>} — a concrete Set implementation declared directly, not via
   * {@code Set}.
   */
  @Test
  public void concreteSetReturnTypePreservesElementConcreteTypes() throws Exception {
    final HashSet<InnerParent<?>> result = client.concreteSetReturn();

    assertThat(result).isInstanceOf(HashSet.class).hasSize(2);
    assertThat(result).contains(new InnerChild1<>("h1"), new InnerChild2<>(9));
  }

  /** A heterogeneous {@code Set<InnerParent<?>>} passed as a method ARGUMENT. */
  @Test
  public void setArgumentPreservesEachElementsConcreteType() throws Exception {
    final Set<InnerParent<?>> arg =
        new LinkedHashSet<>(List.of(new InnerChild1<>("a"), new InnerParent<>("plain")));

    final int subtypeCount = client.countSubtypesFromSet(arg);

    assertThat(subtypeCount).isEqualTo(2);
  }

  @Test
  public void mapValuesPreserveConcreteTypes() throws Exception {
    final Map<String, InnerParent<?>> result = client.mapValueReturn();

    assertThat(result.get("one")).isEqualTo(new InnerChild1<>("m1"));
    assertThat(result.get("two")).isEqualTo(new InnerParent<>("m2"));
  }

  @Test
  public void presentOptionalPreservesConcreteType() throws Exception {
    final Optional<InnerParent<?>> result = client.optionalReturn(true);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(new InnerChild2<>(5));
  }

  @Test
  public void emptyOptionalRoundTrips() throws Exception {
    final Optional<InnerParent<?>> result = client.optionalReturn(false);

    assertThat(result).isEmpty();
  }

  @Test
  public void emptyListRoundTrips() throws Exception {
    assertThat(client.emptyList()).isEmpty();
  }

  @Test
  public void listContainingNullElementRoundTrips() throws Exception {
    final List<InnerParent<?>> result = client.listWithNullElement();

    assertThat(result).hasSize(3);
    assertThat(result.get(0)).isEqualTo(new InnerChild1<>("a"));
    assertThat(result.get(1)).isNull();
    assertThat(result.get(2)).isEqualTo(new InnerChild2<>(1));
  }

  /**
   * {@code List<List<InnerParent<?>>>} — a collection of collections, each element separately
   * polymorphic.
   */
  @Test
  public void nestedListOfListsPreservesEachElementsConcreteType() throws Exception {
    final List<List<InnerParent<?>>> result = client.nestedList();

    assertThat(result).hasSize(2);
    assertThat(result.get(0)).containsExactly(new InnerChild1<>("a"), new InnerChild2<>(1));
    assertThat(result.get(1)).containsExactly(new InnerParent<>("plain"));
  }

  /** {@code Map<String, List<InnerParent<?>>>} — a map whose VALUES are themselves collections. */
  @Test
  public void mapOfListsPreservesEachElementsConcreteType() throws Exception {
    final Map<String, List<InnerParent<?>>> result = client.mapOfLists();

    assertThat(result.get("first")).containsExactly(new InnerChild1<>("a"), new InnerChild2<>(1));
    assertThat(result.get("second")).containsExactly(new InnerParent<>("plain"));
  }

  /**
   * A four-level unannotated generic chain ({@code Parent<T>} -&gt; {@code GenericMiddleChild<T>}
   * -&gt; {@code StillGenericGrandchild<T>} -&gt; {@code ConcreteGreatGrandchild}) inside a
   * heterogeneous {@code List<Parent<?>>} — every level is deliberately unannotated (see {@code
   * ConcreteGreatGrandchild}'s javadoc), so under {@code JAVA_LANG_OBJECT} the declared-{@code
   * Parent<?>} slot cannot recover any of it, no matter how deep the chain. Depth doesn't change
   * the outcome — an unannotated, non-Object-declared type is unprotected either way.
   */
  @Test
  public void deepGenericChainIsNotRecoveredWithoutSelfDescribing() throws Exception {
    assertThatThrownBy(() -> client.deepGenericChain()).isInstanceOf(RuntimeException.class);
  }

  @MicroService("agent")
  private interface Service {
    Flowable<InnerParent<?>> stream();

    List<InnerParent<?>> list();

    LinkedList<InnerParent<?>> linkedList();

    InnerParent<?> polymorphic(InnerChild1<String> arg);

    boolean basic(Child3 arg);

    Boolean booleanBoxed();

    boolean booleanPrimitive();

    String string();

    Flowable<Child4> streamAnnotatedInner();

    List<Child4> listAnnotatedInner();

    Flowable<Parent<InnerParent<String>>> streamNestedGenerics();

    Child1 concreteDeclaredReturn();

    Child1 concreteDeclaredArg(Child1 arg);

    int countSubtypes(List<InnerParent<?>> items);

    Set<InnerParent<?>> setReturn();

    HashSet<InnerParent<?>> concreteSetReturn();

    int countSubtypesFromSet(Set<InnerParent<?>> items);

    /**
     * Map values (declared {@code InnerParent<?>}, not {@code Object}) — a different container
     * shape than List/Set, checked on my own initiative rather than a listed case.
     */
    Map<String, InnerParent<?>> mapValueReturn();

    /**
     * {@code Optional<InnerParent<?>>} — Jdk8Module is registered; polymorphism inside an Optional
     * slot is a real, plausible shape (e.g. an optional response field) nobody had asked about yet.
     */
    Optional<InnerParent<?>> optionalReturn(boolean present);

    List<InnerParent<?>> emptyList();

    List<InnerParent<?>> listWithNullElement();

    List<List<InnerParent<?>>> nestedList();

    Map<String, List<InnerParent<?>>> mapOfLists();

    List<Parent<?>> deepGenericChain();
  }

  private static final class ServiceImpl implements Service {
    @Override
    public Flowable<InnerParent<?>> stream() {
      return Flowable.just(
          new InnerChild1<>("child1"),
          new InnerChild2<>(2),
          new InnerChild1<>("child1"),
          new InnerParent<>("plain"));
    }

    @Override
    public List<InnerParent<?>> list() {
      return new ArrayList<>(
          List.of(
              new InnerChild1<>("child1"),
              new InnerChild2<>(2),
              new InnerChild1<>("child1"),
              new InnerParent<>("plain")));
    }

    @Override
    public LinkedList<InnerParent<?>> linkedList() {
      return new LinkedList<>(list());
    }

    @Override
    public InnerParent<?> polymorphic(InnerChild1<String> arg) {
      return new InnerChild2<>("alpha");
    }

    @Override
    public boolean basic(Child3 arg) {
      return false;
    }

    @Override
    public Boolean booleanBoxed() {
      return Boolean.TRUE;
    }

    @Override
    public boolean booleanPrimitive() {
      return true;
    }

    @Override
    public String string() {
      return "hello";
    }

    @Override
    public Flowable<Child4> streamAnnotatedInner() {
      return Flowable.just(
          new Child4(new InnerParent<>("plain")),
          new Child4(new InnerChild1<>("one")),
          new Child4(new InnerChild2<>("two")));
    }

    @Override
    public List<Child4> listAnnotatedInner() {
      return List.of(new Child4(new InnerParent<>("plain")), new Child4(new InnerChild1<>("one")));
    }

    @Override
    public Flowable<Parent<InnerParent<String>>> streamNestedGenerics() {
      return Flowable.just(
          new Child4(new InnerChild1<>("inner1")),
          new Child4(new InnerChild2<>("inner2")),
          new Child4(new InnerChild2<>("inner3")));
    }

    @Override
    public Child1 concreteDeclaredReturn() {
      return new Child1Variant("base-value", "extra-data");
    }

    @Override
    public Child1 concreteDeclaredArg(final Child1 arg) {
      return arg;
    }

    @Override
    public int countSubtypes(final List<InnerParent<?>> items) {
      int count = 0;
      for (final InnerParent<?> item : items) {
        if (item instanceof InnerChild1<?> child1 && "a".equals(child1.getValue())) {
          count++;
        } else if (item instanceof InnerChild2<?> child2
            && Integer.valueOf(2).equals(child2.getValue())) {
          count++;
        } else if (item.getClass() == InnerParent.class && "plain".equals(item.getValue())) {
          count++;
        }
      }
      return count;
    }

    @Override
    public Set<InnerParent<?>> setReturn() {
      return new LinkedHashSet<>(List.of(new InnerChild1<>("s1"), new InnerParent<>("s2")));
    }

    @Override
    public HashSet<InnerParent<?>> concreteSetReturn() {
      return new HashSet<>(List.of(new InnerChild1<>("h1"), new InnerChild2<>(9)));
    }

    @Override
    public int countSubtypesFromSet(final Set<InnerParent<?>> items) {
      return countSubtypes(new ArrayList<>(items));
    }

    @Override
    public Map<String, InnerParent<?>> mapValueReturn() {
      return Map.of("one", new InnerChild1<>("m1"), "two", new InnerParent<>("m2"));
    }

    @Override
    public Optional<InnerParent<?>> optionalReturn(final boolean present) {
      return present ? Optional.of(new InnerChild2<>(5)) : Optional.empty();
    }

    @Override
    public List<InnerParent<?>> emptyList() {
      return List.of();
    }

    @Override
    public List<InnerParent<?>> listWithNullElement() {
      final List<InnerParent<?>> withNull = new ArrayList<>();
      withNull.add(new InnerChild1<>("a"));
      withNull.add(null);
      withNull.add(new InnerChild2<>(1));
      return withNull;
    }

    @Override
    public List<List<InnerParent<?>>> nestedList() {
      return List.of(
          List.of(new InnerChild1<>("a"), new InnerChild2<>(1)),
          List.of(new InnerParent<>("plain")));
    }

    @Override
    public Map<String, List<InnerParent<?>>> mapOfLists() {
      return Map.of(
          "first",
          List.of(new InnerChild1<>("a"), new InnerChild2<>(1)),
          "second",
          List.of(new InnerParent<>("plain")));
    }

    @Override
    public List<Parent<?>> deepGenericChain() {
      return List.of(
          new ConcreteGrandchild(new Child1("leaf1"), "tag1"),
          new ConcreteGreatGrandchild(new Child3(new Child1("leaf2")), "tag2", "extra2"));
    }
  }
}
