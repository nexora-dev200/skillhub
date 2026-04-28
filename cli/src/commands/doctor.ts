import { runDoctor } from '../services/doctor-service'

export interface DoctorCommandOptions {
  json?: boolean
}

export async function doctorCommand(options: DoctorCommandOptions): Promise<string> {
  const result = await runDoctor(process.cwd())
  if (options.json) {
    return JSON.stringify({
      ok: true,
      inventoryPath: result.inventoryPath,
      backupPath: result.backupPath,
      itemsRestored: result.itemsRestored,
      targetsRestored: result.targetsRestored,
      skipped: result.skipped,
      conflicts: result.conflicts
    })
  }
  const lines = [
    `Inventory: ${result.inventoryPath}`,
    result.backupPath ? `Backup: ${result.backupPath}` : null,
    `Restored: ${result.itemsRestored} items, ${result.targetsRestored} targets`,
    result.skipped.length > 0 ? `Skipped: ${result.skipped.length} directories` : null,
    result.conflicts.length > 0 ? `Conflicts: ${result.conflicts.length} groups` : null
  ].filter(Boolean)
  return lines.join('\n')
}
