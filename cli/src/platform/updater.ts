import { spawn } from 'node:child_process'
import type { ChildProcess, SpawnOptions } from 'node:child_process'

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

    // On Windows, use shell: true to let the OS resolve the executable
    // This handles both .exe and .cmd extensions automatically
    const spawnOptions: SpawnOptions = {
      stdio: ['ignore', 'pipe', 'pipe'],
      ...(process.platform === 'win32' && { shell: true })
    }

    return await new Promise<UpdaterRunResult>((resolve) => {
      const proc: ChildProcess = spawn(program, args, spawnOptions)
      const chunks: Buffer[] = []

      proc.stdout?.on('data', (chunk: Buffer) => chunks.push(Buffer.from(chunk)))
      proc.stderr?.on('data', (chunk: Buffer) => chunks.push(Buffer.from(chunk)))
      proc.on('error', (error: Error) => resolve({ success: false, output: error.message }))
      proc.on('close', (code: number | null) => resolve({
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
