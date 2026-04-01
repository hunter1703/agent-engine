package com.agentengine.connectors.core.response;

import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.http.HttpResponseData;

public interface ErrorClassifier {

    ClassifiedError classify(ConnectorDefinition definition, HttpResponseData responseData);
}
