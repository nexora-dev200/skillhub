import { stat, readFile } from 'node:fs/promises'
import { basename } from 'node:path'
import { ConfigStore } from '../stores/config-store'
import { CredentialsStore } from '../stores/credentials-store'
import { SkillHubClient } from '../clients/skillhub-client'
import { resolveRegistry, resolveToken } from '../services/registry-service'
import { CliError } from '../shared/errors'
import { EXIT } from '../shared/constants'
import { createZip, isZipFile } from '../platform/archive'

export interface PublishCommandOptions {
  namespace?: string
  visibility?: string
  registry?: string
  token?: string
  json?: boolean
}

export async function publishCommand(path: string, options: PublishCommandOptions): Promise<string> {
  // Validate local path
  let pathStat
  try {
    pathStat = await stat(path)
  } catch {
    throw new CliError(`path not found: ${path}`, EXIT.filesystem, { path })
  }

  const configStore = new ConfigStore()
  const credentialsStore = new CredentialsStore()
  const registry = resolveRegistry(options, process.env, await configStore.read())
  const token = resolveToken(options, process.env, await credentialsStore.getToken(registry))
  const namespace = options.namespace ?? 'global'
  const visibility = options.visibility ?? 'public'

  if (!token) {
    throw new CliError('authentication required for publish', EXIT.auth, { next: 'run `skillhub login`' })
  }

  // Create or read archive
  let archiveBlob: Blob
  let archiveName: string
  if (pathStat.isFile()) {
    // If input is a file, check if it's already a zip
    if (await isZipFile(path)) {
      const buffer = await readFile(path)
      archiveBlob = new Blob([buffer], { type: 'application/zip' })
      archiveName = basename(path)
    } else {
      throw new CliError(`file must be a zip archive: ${path}`, EXIT.filesystem, { path })
    }
  } else if (pathStat.isDirectory()) {
    // If input is a directory, create zip from it
    archiveBlob = await createZip(path)
    archiveName = `${basename(path)}.zip`
  } else {
    throw new CliError(`path must be a file or directory: ${path}`, EXIT.filesystem, { path })
  }

  const client = new SkillHubClient(registry, token)
  const result = await client.publish(namespace, archiveBlob, toServerVisibility(visibility), archiveName)

  const detailUrl = `${registry}/space/${result.namespace}/${encodeURIComponent(result.slug)}`

  if (options.json) {
    return JSON.stringify({
      ok: true,
      namespace: result.namespace,
      slug: result.slug,
      version: result.version,
      visibility: result.visibility.toLowerCase(),
      detailUrl
    })
  }
  return `Published successfully: ${result.namespace}/${result.slug}@${result.version}\nDetail: ${detailUrl}`
}

/**
 * Convert kebab-case visibility to UPPER_SNAKE_CASE for server enum.
 */
function toServerVisibility(visibility: string): string {
  return visibility.toUpperCase().replace(/-/g, '_')
}
