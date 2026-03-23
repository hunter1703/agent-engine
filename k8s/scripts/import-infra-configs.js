const fs = require("fs");
const path = require("path");

const CONFIG_FILES = [
  "default-model-configs.json",
  "pekko-configs.json",
  "sql-infra-configs.json",
  "microservice-infra-configs.json"
];

function loadConfigs() {
  const configDir = "/configs";
  return CONFIG_FILES.flatMap((fileName) => JSON.parse(fs.readFileSync(path.join(configDir, fileName), "utf8")));
}

function applyDeploymentOverrides(config) {
  const next = { ...config };

  if (next.type === "default_model") {
    next.titleModelId = process.env.TITLE_MODEL_ID || next.titleModelId;
    next.compactionModelId = process.env.COMPACTION_MODEL_ID || next.compactionModelId;
    next.evaluatorModelId = process.env.EVALUATOR_MODEL_ID || next.evaluatorModelId;
    return next;
  }

  if (next.type === "PEKKO") {
    next.hostname = process.env.RUNTIME_POD_FQDN_TEMPLATE || next.hostname;
    next.port = Number(process.env.RUNTIME_PEKKO_PORT || next.port);
    next.seedNodes = JSON.parse(process.env.RUNTIME_SEED_NODES_JSON || JSON.stringify(next.seedNodes || []));
    next.clusterName = process.env.RUNTIME_CLUSTER_NAME || next.clusterName;
    next.snapshotThreshold = Number(process.env.PEKKO_SNAPSHOT_THRESHOLD || next.snapshotThreshold);
    return next;
  }

  if (next.type === "sql") {
    next.jdbcUrl = process.env.SQL_JDBC_URL || next.jdbcUrl;
    next.jdbcUser = process.env.SQL_JDBC_USER || next.jdbcUser;
    next.jdbcPassword = process.env.SQL_JDBC_PASSWORD || next.jdbcPassword;
    return next;
  }

  if (next.type === "microservice" && next.serverId === "agent") {
    const namespace = process.env.NAMESPACE || "agent-engine";
    const serviceName = process.env.CORE_SERVICE_NAME || "agent-engine-core";
    next.host = `${serviceName}.${namespace}.svc.cluster.local`;
    next.port = Number(process.env.CORE_GRPC_PORT || next.port);
    return next;
  }

  if (next.type === "microservice" && next.serverId === "runtime") {
    const namespace = process.env.NAMESPACE || "agent-engine";
    const serviceName = process.env.RUNTIME_SERVICE_NAME || "agent-engine-runtime";
    next.host = `${serviceName}.${namespace}.svc.cluster.local`;
    next.port = Number(process.env.RUNTIME_GRPC_PORT || next.port);
    return next;
  }

  return next;
}

function upsertConfig(collection, doc) {
  const id = doc.id || doc._id;
  if (!id) {
    throw new Error(`Config is missing id: ${JSON.stringify(doc)}`);
  }

  const existing = collection.findOne({ _id: id });
  const now = Date.now();
  const payload = {
    ...doc,
    _id: id,
    createdTime: existing && typeof existing.createdTime === "number" ? existing.createdTime : now,
    updatedTime: now
  };
  delete payload.id;
  collection.replaceOne({ _id: id }, payload, { upsert: true });
  print(`Upserted infra config ${id} (${payload.type})`);
}

const infraDb = db.getSiblingDB("INFRA");
const collection = infraDb.getCollection("InfraConfig");
const configs = loadConfigs().map(applyDeploymentOverrides);

for (const config of configs) {
  upsertConfig(collection, config);
}
