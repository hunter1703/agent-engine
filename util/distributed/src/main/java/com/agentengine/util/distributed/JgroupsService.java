package com.agentengine.util.distributed;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.function.Consumer;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.blocks.locking.LockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@SuppressWarnings("deprecation")
public class JgroupsService implements Receiver {

  private static final Logger LOG = LoggerFactory.getLogger(JgroupsService.class);

  private JChannel channel;
  private LockService lockService;
  private final Map<String, List<Consumer<String>>> listeners = new ConcurrentHashMap<>();

  @PostConstruct
  public void start() {
    try {
      LOG.info("Initializing JgroupsService with jgroups-kubeping.xml");
      this.channel = new JChannel("jgroups-kubeping.xml");
      this.channel.setReceiver(this);

      // Initialize Distributed Lock Service block
      this.lockService = new LockService(channel);

      // Connect to the logic cluster name
      this.channel.connect("coordination-channel");
      LOG.info("Successfully connected to JGroups cluster: coordination-channel");
    } catch (Exception e) {
      LOG.error("Failed to initialize ClusterMeshService", e);
      throw new RuntimeException("Could not start ClusterMeshService", e);
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
      channel.send(new ObjectMessage(null, category + "::" + payload));
    } catch (Exception e) {
      LOG.error("Failed to broadcast for category: {}", category, e);
    }
  }

  @Override
  public void receive(Message msg) {
    if (!(msg instanceof ObjectMessage objectMessage)) {
      return;
    }
    final String payload = objectMessage.getPayload();
    final int separatorIdx = payload.indexOf("::");
    final String category = payload.substring(0, separatorIdx);
    final String actualPayload = payload.substring(separatorIdx + 2);
    List<Consumer<String>> categoryListeners = listeners.get(category);
    if (categoryListeners != null) {
      for (Consumer<String> listener : categoryListeners) {
        listener.accept(actualPayload);
      }
    }
  }

  @PreDestroy
  public void stop() {
    if (channel != null) {
      LOG.info("Closing JGroups channel");
      channel.close();
    }
  }
}
