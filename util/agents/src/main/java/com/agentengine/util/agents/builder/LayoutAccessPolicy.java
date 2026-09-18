package com.agentengine.util.agents.builder;

import com.agentengine.util.agents.builder.annotations.UiAccessLevel;

public record LayoutAccessPolicy(UiAccessLevel create, UiAccessLevel edit, UiAccessLevel view) {

  public UiAccessLevel forMode(final BuilderMode mode) {
    if (mode == null) {
      return UiAccessLevel.EDITABLE;
    }
    return switch (mode) {
      case CREATE -> create;
      case EDIT -> edit;
      case VIEW -> view;
    };
  }
}
