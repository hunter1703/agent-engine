package com.agentengine.util.context;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class RequestContextProvider {

  private Context context;

  public void set(final Context context) {
    this.context = context;
  }

  public Context get() {
    return context;
  }
}
