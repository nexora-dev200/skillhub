export async function runCli(args: string[], env: Record<string, string> = {}) {
  const proc = Bun.spawn({
    cmd: ['bun', 'src/index.ts', ...args],
    cwd: new URL('../../', import.meta.url).pathname,
    env: { ...process.env, ...env },
    stdout: 'pipe',
    stderr: 'pipe'
  })
  const [stdout, stderr, exitCode] = await Promise.all([
    new Response(proc.stdout).text(),
    new Response(proc.stderr).text(),
    proc.exited
  ])
  return { stdout: stdout.trim(), stderr: stderr.trim(), exitCode }
}
