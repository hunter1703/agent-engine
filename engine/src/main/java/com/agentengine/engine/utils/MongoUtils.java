package com.agentengine.engine.utils;

import com.agentengine.engine.api.update.Operation;
import com.agentengine.engine.api.update.Update;
import com.mongodb.client.model.Updates;
import java.util.List;
import java.util.Objects;
import org.bson.conversions.Bson;

public final class MongoUtils {

  private MongoUtils() {}

  public static Bson toBsonUpdate(final Update update) {
    Objects.requireNonNull(update, "update");
    final List<Bson> updates =
        update.operations().stream().map(MongoUtils::toBsonOperation).toList();
    return Updates.combine(updates);
  }

  private static Bson toBsonOperation(final Operation operation) {
    Objects.requireNonNull(operation, "operation");
    return switch (operation.type()) {
      case SET -> Updates.set(operation.field(), operation.value());
      case UNSET -> Updates.unset(operation.field());
        default -> throw new IllegalStateException("Unexpected value: " + operation.type());
    };
  }
}
