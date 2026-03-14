package com.agentengine.engine.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.repository.AgentRepository;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgentServiceImplTest {

  @Mock
  private AgentRepository agentRepository;

  @InjectMocks
  private AgentServiceImpl agentService;

  @Test
  void shouldSanitizeBeforeInsertWhenCreateAgentCalled() {
    final DefaultAgentConfig request = new DefaultAgentConfig();
    request.setId("agent-1");
    request.setModelId("model-1");
    when(agentRepository.insert(any(BaseAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    final BaseAgentConfig created = agentService.createAgent(request);

    assertThat(created.getId()).isNull();
    verify(agentRepository).insert(any(BaseAgentConfig.class));
  }

  @Test
  void shouldDelegateFindAgentsWhenFindAgentsCalled() {
    final Query query = new Query();
    final PaginatedResult<BaseAgentConfig> expected = PaginatedResult.create(java.util.List.of());
    when(agentRepository.findByQuery(query)).thenReturn(expected);

    final PaginatedResult<BaseAgentConfig> result = agentService.findAgents(query);

    assertThat(result).isSameAs(expected);
    verify(agentRepository).findByQuery(query);
  }

  @Test
  void shouldDelegateGetAgentWhenGetAgentCalled() {
    final DefaultAgentConfig config = new DefaultAgentConfig();
    config.setId("agent-1");
    when(agentRepository.findById("agent-1")).thenReturn(Optional.of(config));

    final Optional<BaseAgentConfig> result = agentService.getAgent("agent-1");

    assertThat(result).contains(config);
    verify(agentRepository).findById("agent-1");
  }

  @Test
  void shouldDelegateDeleteAgentWhenDeleteAgentCalled() {
    when(agentRepository.deleteById("agent-1")).thenReturn(true);

    final boolean deleted = agentService.deleteAgent("agent-1");

    assertThat(deleted).isTrue();
    verify(agentRepository).deleteById("agent-1");
  }

  @Test
  void shouldDelegateUpdateAgentWhenUpdateAgentCalled() {
    final DefaultAgentConfig request = new DefaultAgentConfig();
    request.setId("agent-2");
    request.setModelId("model-1");
    when(agentRepository.update(eq("agent-1"), any(BaseAgentConfig.class))).thenAnswer(inv -> inv.getArgument(1));

    final BaseAgentConfig updated = agentService.updateAgent("agent-1", request);

    assertThat(updated.getId()).isNull();
    verify(agentRepository).update(eq("agent-1"), any(BaseAgentConfig.class));
  }

  @Test
  void shouldPreserveIdWhenSaveAgentCalled() {
    final DefaultAgentConfig request = new DefaultAgentConfig();
    request.setId("agent-3");
    request.setModelId("model-1");
    when(agentRepository.save(any(BaseAgentConfig.class))).thenAnswer(inv -> inv.getArgument(0));

    final BaseAgentConfig saved = agentService.saveAgent(request);

    assertThat(saved.getId()).isEqualTo("agent-3");
    verify(agentRepository).save(any(BaseAgentConfig.class));
  }
}
