package com.agentengine.engine.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.beans.config.GuardrailRule;
import com.agentengine.engine.api.beans.config.TextContentGuardrailRule;
import com.agentengine.engine.api.beans.config.ToolSafetyGuardrailRule;
import com.mongodb.MongoClientSettings;
import java.util.List;
import org.bson.BsonDocument;
import org.bson.BsonDocumentReader;
import org.bson.BsonDocumentWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistries;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.ClassModel;
import org.bson.codecs.pojo.Convention;
import org.bson.codecs.pojo.Conventions;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.junit.jupiter.api.Test;

class GuardrailRuleMongoCodecTest {

  @Test
  void roundTripsPolymorphicRuleSubtypes() {
    final RuleContainer config = new RuleContainer();

    final TextContentGuardrailRule inputRule = new TextContentGuardrailRule();
    inputRule.setId("input-rule");
    inputRule.setMaxTextLength(128);
    inputRule.setBlockedPatterns(List.of("jailbreak"));

    final ToolSafetyGuardrailRule toolRule = new ToolSafetyGuardrailRule();
    toolRule.setId("tool-rule");
    toolRule.setToolNames(List.of("run_cmd"));

    config.setRules(List.of(inputRule, toolRule));

    final List<Convention> conventions = List.copyOf(Conventions.DEFAULT_CONVENTIONS);
    final PojoCodecProvider.Builder pojoBuilder =
        PojoCodecProvider.builder().conventions(conventions).automatic(true);
    pojoBuilder.register(
        ClassModel.builder(GuardrailRule.class).enableDiscriminator(true).conventions(conventions).build());
    pojoBuilder.register(
        ClassModel.builder(TextContentGuardrailRule.class)
            .enableDiscriminator(true)
            .conventions(conventions)
            .build());
    pojoBuilder.register(
        ClassModel.builder(ToolSafetyGuardrailRule.class)
            .enableDiscriminator(true)
            .conventions(conventions)
            .build());

    final CodecRegistry registry =
        CodecRegistries.fromRegistries(
            MongoClientSettings.getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(pojoBuilder.build()));

    final Codec<RuleContainer> codec = registry.get(RuleContainer.class);
    final BsonDocument document = new BsonDocument();
    codec.encode(new BsonDocumentWriter(document), config, EncoderContext.builder().build());

    final RuleContainer decoded =
        codec.decode(new BsonDocumentReader(document), DecoderContext.builder().build());

    assertThat(decoded.getRules()).hasSize(2);
    assertThat(decoded.getRules().get(0)).isInstanceOf(TextContentGuardrailRule.class);
    assertThat(decoded.getRules().get(1)).isInstanceOf(ToolSafetyGuardrailRule.class);
  }

  public static class RuleContainer {
    private List<GuardrailRule> rules;

    public List<GuardrailRule> getRules() {
      return rules;
    }

    public void setRules(final List<GuardrailRule> rules) {
      this.rules = rules;
    }
  }
}
