package com.agentengine.util.distributed;

import com.agentengine.util.common.CollectionUtils;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import org.jgroups.BytesMessage;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.blocks.locking.LockService;
import org.jgroups.fork.ForkChannel;
import org.jgroups.protocols.FORK;
import org.jgroups.stack.ProtocolStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
@Singleton
public class JgroupsService implements Receiver {

  private static final Logger LOG = LoggerFactory.getLogger(JgroupsService.class);

  private JChannel mainChannel;
  private LockService lockService;
  private final Map<String, List<Consumer<String>>> listeners = new ConcurrentHashMap<>();

  @PostConstruct
  public void start() {
    try {
      LOG.info("Initializing JgroupsService with jgroups.xml");
      this.mainChannel = new JChannel("jgroups.xml");
      this.mainChannel.setReceiver(this);

      // Ensure FORK protocol exists in your parent stack to support ForkChannels
      final ProtocolStack stack = mainChannel.getProtocolStack();
      if (stack.findProtocol(FORK.class) == null) {
        // Inserts FORK dynamically right below the top program-facing layer if not present in XML
        stack.addProtocol(new FORK());
      }

      // Create a virtual ForkChannel for locking.
      // It shares the physical thread pools and sockets of mainChannel (0% resource inflation)
      final ForkChannel lockChannel =
          new ForkChannel(mainChannel, "lock-stack", "lock-rpc-channel");
      this.lockService = new LockService(lockChannel);

      // Connect the main channel (this automatically activates the fork channels)
      this.mainChannel.connect("coordination-channel");
      LOG.info(
          "Successfully connected to JGroups cluster: coordination-channel via isolated channels");
    } catch (Exception e) {
      LOG.error("Failed to initialize JgroupsService", e);
      throw new RuntimeException("Could not start JgroupsService", e);
    }
  }

  public void registerListener(String category, Consumer<String> listener) {
    listeners.computeIfAbsent(category, _ -> new CopyOnWriteArrayList<>()).add(listener);
  }

  public Lock getDistributedLock(String lockKey) {
    return this.lockService.getLock(lockKey);
  }

  public void broadcast(String category, String payload) {
    try {
      // High-performance string concatenation converted to ultra-lean raw bytes
      payload = category + "::" + payload;
      final byte[] data = payload.getBytes(StandardCharsets.UTF_8);
      mainChannel.send(new BytesMessage(null, data));
    } catch (Exception e) {
      LOG.error("Failed to broadcast for category: {}", category, e);
    }
  }

  @Override
  public void receive(Message msg) {
    // Only process standard byte arrays from our invalidation stream
    if (!(msg instanceof BytesMessage bytesMessage)) {
      return;
    }

    try {
      // JGroups reuses large network buffers; we must read only the specific slice
      // containing our data using offset and length to avoid reading trailing garbage.
      final String payload =
          new String(
              bytesMessage.getArray(),
              bytesMessage.getOffset(),
              bytesMessage.getLength(),
              StandardCharsets.UTF_8);
      final int separatorIdx = payload.indexOf("::");
      if (separatorIdx == -1) {
        return;
      }

      final String category = payload.substring(0, separatorIdx);
      final String actualPayload = payload.substring(separatorIdx + 2);

      final List<Consumer<String>> listeners = this.listeners.get(category);
      for (final Consumer<String> listener : CollectionUtils.nullSafeList(listeners)) {
        listener.accept(actualPayload);
      }
    } catch (Exception e) {
      LOG.error("Error processing incoming cluster message", e);
    }
  }

  @PreDestroy
  public void stop() {
    // Closing the main channel cleanly tears down the associated fork channels
    if (mainChannel != null) {
      LOG.info("Closing JGroups channels");
      mainChannel.close();
    }
  }
}
