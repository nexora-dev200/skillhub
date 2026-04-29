import { existsSync } from 'node:fs'

export async function runCli(args: string[], env: Record<string, string> = {}) {
  // Use Bun.which() to find bun in PATH, but verify it exists
  const whichBun = await Bun.which('bun')
  const bunPath = (whichBun && existsSync(whichBun)) ? whichBun : process.execPath

  const proc = Bun.spawn({
    cmd: [bunPath, 'src/index.ts', ...args],
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
