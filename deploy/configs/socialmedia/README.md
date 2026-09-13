# `socialmedia` config — secret placeholders

`infra/SQL.json` and `../../k8s/global-properties/envs/socialmedia/values.yaml` are real,
committed files — safe to commit because the only credential-bearing fields are `${VAR}`-style
placeholders, not real values:

- `SQL.json`'s `jdbcPassword` is `${NEON_JDBC_PASSWORD}` (host and user are plain committed text
  — not secret by themselves).
- `values.yaml`'s `infra.mongodb.uri` embeds `${MONGO_ATLAS_USER}` and `${MONGO_ATLAS_PASSWORD}`
  (host and app name are plain committed text).

`.github/workflows/deploy.yml`'s "Resolve secret placeholders" step runs `envsubst`, restricted
to exactly those named variables, against both files before anything else reads them — so
nothing else in either file is touched, and no other `$`-looking text could accidentally get
substituted.

**Adding a new property**: a plain (non-secret) property needs no workflow change — edit the
file directly, it flows through untouched. A new *secret*-bearing property needs two things: a
new repo secret, and adding that secret's env var name to the relevant `envsubst 'VAR1 VAR2'`
call in the workflow.

**Running `deployae deploy` manually**, outside that workflow: export the same environment
variables yourself (`NEON_JDBC_PASSWORD`, `MONGO_ATLAS_USER`, `MONGO_ATLAS_PASSWORD`) and run the
same `envsubst` commands the workflow does before invoking `deployae` — see
`.github/workflows/deploy.yml`'s "Resolve secret placeholders" step for the exact commands.
