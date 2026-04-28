type FakeHandler = (req: Request) => Response | Promise<Response>

export function createFakeRegistry(handlers: Record<string, FakeHandler>) {
  return async function fakeFetch(input: string | URL | Request, init?: RequestInit): Promise<Response> {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url
    const path = new URL(url).pathname

    for (const [pattern, handler] of Object.entries(handlers)) {
      if (path === pattern || path.startsWith(pattern)) {
        return handler(new Request(url, init))
      }
    }

    return new Response(JSON.stringify({ error: 'not found' }), { status: 404 })
  }
}

interface FakeRegistryOptions {
  token?: string
  user?: { handle: string; displayName: string; email?: string }
  searchItems?: Array<{ namespace: string; slug: string; latestVersion: string; summary: string }>
}

export async function startFakeRegistry(options: FakeRegistryOptions = {}) {
  const server = Bun.serve({
    port: 0,
    fetch(req) {
      const url = new URL(req.url)
      const path = url.pathname

      if (path === '/api/cli/v1/auth/whoami') {
        const auth = req.headers.get('Authorization')
        if (options.token && auth !== `Bearer ${options.token}`) {
          return Response.json({ code: 401, message: 'unauthorized' }, { status: 401 })
        }
        return Response.json({
          code: 0,
          data: options.user ?? { handle: 'test-user', displayName: 'Test User', email: 'test@example.com' }
        })
      }

      if (path === '/api/cli/v1/skills/search') {
        return Response.json({
          code: 0,
          data: {
            items: options.searchItems ?? [],
            total: options.searchItems?.length ?? 0,
            limit: 20
          }
        })
      }

      return Response.json({ code: 404, message: 'not found' }, { status: 404 })
    }
  })

  return {
    url: `http://localhost:${server.port}`,
    stop: () => server.stop()
  }
}
