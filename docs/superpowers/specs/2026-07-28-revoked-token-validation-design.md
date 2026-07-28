# Revoked API Token Validation Design

## Goal

Prove and preserve fail-closed API-token behavior across the CLI API using a
real persisted token lifecycle. Invalid Bearer credentials must return HTTP
401 before endpoint business logic runs, while requests without an
`Authorization` header retain the existing anonymous-public-read contract and
valid credentials without sufficient authorization continue to return HTTP
403.

## Scope

This change covers the following CLI routes:

- `GET /api/cli/v1/auth/whoami`
- `GET /api/cli/v1/skills/search`
- `GET /api/cli/v1/skills/{namespace}/{slug}/resolve`
- `GET /api/cli/v1/skills/{namespace}/{slug}/download`
- `GET /api/cli/v1/skills/{namespace}/{slug}/versions/{version}/download`

It also covers the authenticated-versus-forbidden boundary on an existing
scope-protected CLI route. It does not add endpoints, change response fields,
change token storage, add a database migration, or change anonymous resource
visibility rules.

## Current-State Finding

The fail-closed implementation from closed PR #511 was later included in the
single replacement PR #523 and is present in both v0.2.14 and current `main`.
`ApiTokenAuthenticationFilter` already validates Bearer credentials before
business logic and rejects empty, malformed, unknown, expired, revoked,
missing-user, and disabled-user credentials through the configured
`AuthenticationEntryPoint`.

The verified repository gap is regression coverage, not a demonstrated
production-code gap. Existing tests separately prove token lifecycle
validation and invalid-Bearer filtering, but they do not exercise persisted
token creation, revocation, and all affected CLI endpoints in one integrated
matrix. The CLI API table in `docs/03-authentication-design.md` also retains
legacy paths, and there is no dedicated OpenAPI 3.0 authentication contract in
`docs/api/`.

## Architecture

`ApiTokenAuthenticationFilter` remains the single Bearer-authentication entry
point. Controllers must not duplicate token parsing or lifecycle checks.

The regression test will boot the Spring application with MockMvc, real
`ApiTokenService`, real `ApiTokenRepository`, and real user persistence. CLI
endpoint business services may be mocked only to make successful public-read
responses deterministic; authentication and token lifecycle components remain
real. This isolates the contract boundary under test: a rejected credential
must stop in the security chain before controller business logic executes.

Production authentication code will be changed only when a new regression
test fails for the expected behavioral reason. Any fix must be the smallest
change at the shared authentication or token-validation source of the failure.
Endpoint-specific authentication patches and unrelated refactoring are out of
scope.

## Persisted Token Lifecycle

The test fixture creates an active user and issues a token through
`ApiTokenService`, retaining only the raw token returned at creation time.
Lifecycle transitions use production persistence paths:

1. Call an affected endpoint with the valid raw token and confirm successful
   authentication.
2. Revoke the token through `ApiTokenService.revokeToken`.
3. Call every affected endpoint with the same raw token.
4. Assert HTTP 401 and confirm protected endpoint business logic was not
   reached.

Expired-token coverage persists a token with an expiration timestamp earlier
than the service clock, then validates it through the same filter and
repository path. Unknown and malformed tokens exercise the same HTTP security
chain without creating a token row.

## Behavioral Matrix

| Credential state | `whoami` | Public `search` | Public `resolve` | Public `download` | Meaning |
|---|---:|---:|---:|---:|---|
| No `Authorization` header | 401 | Existing anonymous result | Existing anonymous result | Existing anonymous result | Anonymous access is preserved only where already public |
| Valid active token | 200 | Authenticated result | Authenticated result | Authenticated result | Principal and roles/scopes are projected |
| Revoked token | 401 | 401 | 401 | 401 | Credential cannot degrade to anonymous |
| Expired token | 401 | 401 | 401 | 401 | Credential cannot degrade to anonymous |
| Unknown token | 401 | 401 | 401 | 401 | Credential cannot degrade to anonymous |
| Malformed or empty Bearer | 401 | 401 | 401 | 401 | Authentication attempt is rejected before validation/business logic |
| Valid token lacking required authorization | N/A | N/A | 403 for a restricted resource or protected CLI action | 403 for a restricted resource or protected CLI action | Authenticated-but-forbidden remains distinct from invalid credentials |

The test may use the existing scope-protected delete route to make the 403
boundary deterministic without changing resource visibility or constructing a
private namespace scenario unrelated to token validation.

## Error Handling and Security

- Invalid Bearer credentials return the existing structured HTTP 401 response
  through `ApiAuthenticationEntryPoint`.
- Valid credentials that fail scope or resource authorization return the
  existing structured HTTP 403 response through the access-denied path.
- Responses must not reveal whether a token is unknown, expired, or revoked.
- Tests, logs, documentation, and commits must not contain real secrets. Test
  credentials are generated locally and exist only in the in-memory test
  database.
- Token material must never be logged.

## Documentation

Two documentation updates are required:

1. Update `docs/03-authentication-design.md` so the CLI API section uses the
   current `/api/cli/v1/...` routes and explicitly states the 401/403 and
   anonymous-access boundary.
2. Add `docs/api/authentication.openapi.yaml` using OpenAPI 3.0. The document
   must define Bearer authentication, all affected paths, query/path
   parameters, success schemas, the common response envelope, HTTP 401 and 403
   responses, examples, and the rule that absent credentials are allowed only
   on existing public-read routes.

No controller signature or response schema changes are planned. Therefore the
generated `web/src/api/generated/schema.d.ts` should remain unchanged; if a
production fix unexpectedly changes a controller contract, `make generate-api`
becomes mandatory and the generated diff must be committed.

## Verification

Verification proceeds in this order:

1. Run the new focused persisted-token matrix and record whether it fails or
   passes on unmodified `main` behavior.
2. If it fails, preserve the failure output as reproduction evidence, apply one
   minimal shared fix, and rerun the focused matrix.
3. Run auth-module and affected app integration tests.
4. Run `make test-backend-app`.
5. Run `make typecheck-web` and `make lint-web` as repository pre-PR gates.
6. Run `make staging` for containerized regression and smoke coverage.
7. Run `git diff --check` and confirm no generated OpenAPI type drift when no
   controller contract changed.
8. Perform structured security and code review before opening the single final
   pull request.

## Delivery Constraints

- Work only on `fix/auth-revoked-token-validation`.
- Keep PR #511 closed and use it only as historical reference.
- Create exactly one final pull request for GitHub issue #605.
- GitHub-facing text must not contain a Multica issue identifier.
- Do not merge `main`; merging remains the responsibility of an explicitly
  authorized human owner.
