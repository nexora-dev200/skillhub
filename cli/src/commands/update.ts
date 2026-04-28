import { CLI_VERSION, EXIT } from '../shared/constants'
import { CliError } from '../shared/errors'
import { printResult } from '../shared/output'
import { NpmRegistryClient } from '../clients/npm-registry-client'
import { UpdateService } from '../services/update-service'
import { detectInstallMode } from '../platform/package-manager'
import { runUpdateCommand } from '../platform/updater'

export interface UpdateCommandOptions {
  check?: boolean
  json?: boolean
}

export async function updateCommand(options: UpdateCommandOptions): Promise<string> {
  const json = Boolean(options.json)
  const checkOnly = Boolean(options.check)

  const npmClient = new NpmRegistryClient()
  const service = new UpdateService({
    currentVersion: CLI_VERSION,
    latestVersion: () => npmClient.latestVersion(),
    detectInstallMode: () => detectInstallMode(),
    run: runUpdateCommand
  })

  const result = await service.update({ checkOnly })

  if (!result.available) {
    return printResult(
      json
        ? { ok: true, upToDate: true, version: result.currentVersion }
        : `Already up to date (${result.currentVersion})`,
      json
    )
  }

  if (result.updated) {
    return printResult(
      json
        ? { ok: true, updated: true, from: result.currentVersion, to: result.latestVersion }
        : `Updated skillhub ${result.currentVersion} -> ${result.latestVersion}`,
      json
    )
  }

  if (result.error) {
    throw new CliError(result.error, EXIT.generic, { from: result.currentVersion, to: result.latestVersion })
  }

  // Not updated but available (npx / unknown / checkOnly)
  const lines = [`Update available: ${result.currentVersion} -> ${result.latestVersion}`]
  if (result.next) {
    lines.push(result.next)
  }

  return printResult(
    json
      ? { ok: true, available: true, from: result.currentVersion, to: result.latestVersion, next: result.next }
      : lines.join('\n'),
    json
  )
}
