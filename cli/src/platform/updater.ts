import { spawn } from 'node:child_process'

export interface UpdaterRunResult {
  success: boolean
  output: string
}

/**
 * Execute an update command without relying on sh -c.
 * Splits the command string into program + args for cross-platform compatibility.
 */
export async function runUpdateCommand(command: string): Promise<UpdaterRunResult> {
  try {
    const [program, ...args] = command.split(/\s+/)
    if (!program) {
      return { success: false, output: 'empty update command' }
    }
    const executable = process.platform === 'win32' && !program.endsWith('.cmd') && !program.endsWith('.exe')
      ? `${program}.cmd`
      : program

    return await new Promise<UpdaterRunResult>((resolve) => {
      const proc = spawn(executable, args, { stdio: ['ignore', 'pipe', 'pipe'] })
      const chunks: Buffer[] = []

      proc.stdout.on('data', chunk => chunks.push(Buffer.from(chunk)))
      proc.stderr.on('data', chunk => chunks.push(Buffer.from(chunk)))
      proc.on('error', error => resolve({ success: false, output: error.message }))
      proc.on('close', code => resolve({
        success: code === 0,
        output: Buffer.concat(chunks).toString('utf-8')
      }))
    })
  } catch (error) {
    return {
      success: false,
      output: error instanceof Error ? error.message : String(error)
    }
  }
}
