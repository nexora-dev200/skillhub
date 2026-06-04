export const MAX_SEARCH_QUERY_LENGTH = 50

export function normalizeSearchQuery(query: string): string {
  return query.trim().slice(0, MAX_SEARCH_QUERY_LENGTH)
}

export interface NamespaceSearchInput {
  namespace: string
  query: string
}

const LEADING_NAMESPACE_PATTERN = /^@([a-zA-Z0-9][a-zA-Z0-9-]{0,63})(?:\s+|$)(.*)$/

export function parseNamespaceSearchInput(input: string): NamespaceSearchInput {
  const normalized = normalizeSearchQuery(input)
  const match = normalized.match(LEADING_NAMESPACE_PATTERN)
  if (!match) {
    return { namespace: '', query: normalized }
  }

  return {
    namespace: match[1],
    query: normalizeSearchQuery(match[2] ?? ''),
  }
}

export function formatNamespaceSearchInput(namespace: string, query: string): string {
  const normalizedNamespace = namespace.trim().replace(/^@/, '')
  const normalizedQuery = normalizeSearchQuery(query)
  if (!normalizedNamespace) {
    return normalizedQuery
  }
  return normalizedQuery ? `@${normalizedNamespace} ${normalizedQuery}` : `@${normalizedNamespace}`
}
