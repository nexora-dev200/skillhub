#!/usr/bin/env node
import { cac } from 'cac'
import { doctorCommand } from './commands/doctor'
import { helpCommand } from './commands/help'
import { installCommand, type InstallCommandOptions } from './commands/install'
import { listCommand, type ListCommandOptions } from './commands/list'
import { loginCommand } from './commands/login'
import { logoutCommand } from './commands/logout'
import { publishCommand, type PublishCommandOptions } from './commands/publish'
import { removeCommand, type RemoveCommandOptions } from './commands/remove'
import { searchCommand } from './commands/search'
import { updateCommand } from './commands/update'
import { versionCommand } from './commands/version'
import { whoamiCommand } from './commands/whoami'
import { CliError } from './shared/errors'
import { renderError } from './shared/output'

const cli = cac('skillhub')

/** Normalize cac's repeatable option: string | string[] | undefined -> string[] | undefined */
function toArray(val: string | string[] | undefined): string[] | undefined {
  if (val === undefined) return undefined
  return Array.isArray(val) ? val : [val]
}

async function runCommand(action: () => Promise<string>, json = false): Promise<void> {
  try {
    const output = await action()
    if (output) {
      process.stdout.write(`${output}\n`)
    }
  } catch (error) {
    const exitCode = error instanceof CliError ? error.exitCode : 1
    process.stderr.write(`${renderError(error, json)}\n`)
    process.exit(exitCode)
  }
}

cli
  .command('', 'Show help')
  .action(() => {
    return runCommand(() => helpCommand([]))
  })

cli
  .command('help [command]', 'Show help')
  .option('--json', 'Output JSON')
  .action((command: string | undefined, options: { json?: boolean }) => {
    return runCommand(() => helpCommand(command ? [command] : []), Boolean(options.json))
  })

cli
  .command('version', 'Show CLI version')
  .option('--json', 'Output JSON')
  .action((options: { json?: boolean }) => {
    return runCommand(() => versionCommand(options.json ? ['--json'] : []), Boolean(options.json))
  })

cli
  .command('update', 'Update CLI to latest version')
  .option('--check', 'Check for updates without installing')
  .option('--json', 'Output JSON')
  .action((options: { check?: boolean; json?: boolean }) => {
    return runCommand(() => updateCommand(options), Boolean(options.json))
  })

cli
  .command('login', 'Save registry and token')
  .option('--registry <url>', 'Registry URL')
  .option('--token <token>', 'API token')
  .option('--json', 'Output JSON')
  .action((options: { registry?: string; token?: string; json?: boolean }) => {
    return runCommand(() => loginCommand(options), Boolean(options.json))
  })

cli
  .command('logout', 'Remove local token')
  .option('--registry <url>', 'Registry URL')
  .option('--json', 'Output JSON')
  .action((options: { registry?: string; json?: boolean }) => {
    return runCommand(() => logoutCommand(options), Boolean(options.json))
  })

cli
  .command('whoami', 'Verify current token')
  .option('--registry <url>', 'Registry URL')
  .option('--token <token>', 'API token')
  .option('--json', 'Output JSON')
  .action((options: { registry?: string; token?: string; json?: boolean }) => {
    return runCommand(() => whoamiCommand(options), Boolean(options.json))
  })

cli
  .command('search <query>', 'Search published skills')
  .option('--registry <url>', 'Registry URL')
  .option('--limit <n>', 'Max results', { default: 20 })
  .option('--json', 'Output JSON')
  .action((query: string, options: { registry?: string; limit?: number; json?: boolean }) => {
    return runCommand(() => searchCommand(query, options), Boolean(options.json))
  })

cli
  .command('install <slug>', 'Install a skill locally')
  .option('--namespace <slug>', 'Namespace', { default: 'global' })
  .option('--version <v>', 'Version')
  .option('--agent <profile>', 'Agent profile (repeatable)')
  .option('--dir <path>', 'Install directory')
  .option('--force', 'Overwrite existing')
  .option('--registry <url>', 'Registry URL')
  .option('--token <token>', 'API token')
  .option('--json', 'Output JSON')
  .action((slug: string, options: InstallCommandOptions & { agent?: string | string[] }) => {
    return runCommand(() => installCommand(slug, { ...options, agent: toArray(options.agent) }), Boolean(options.json))
  })

cli
  .command('list', 'List local installs')
  .option('--agent <profile>', 'Filter by agent (repeatable)')
  .option('--dir <path>', 'Filter by directory')
  .option('--registry <url>', 'Registry URL')
  .option('--json', 'Output JSON')
  .action((options: ListCommandOptions & { agent?: string | string[] }) => {
    return runCommand(() => listCommand({ ...options, agent: toArray(options.agent) }), Boolean(options.json))
  })

cli
  .command('remove <slug>', 'Remove local or remote skill')
  .option('--agent <profile>', 'Filter by agent (repeatable)')
  .option('--all', 'Remove all targets')
  .option('--remote', 'Delete remote skill')
  .option('--hard', 'Skip confirmation for remote delete')
  .option('--namespace <slug>', 'Namespace for remote delete')
  .option('--registry <url>', 'Registry URL')
  .option('--token <token>', 'API token')
  .option('--json', 'Output JSON')
  .action((slug: string, options: RemoveCommandOptions & { agent?: string | string[] }) => {
    return runCommand(() => removeCommand(slug, { ...options, agent: toArray(options.agent) }), Boolean(options.json))
  })

cli
  .command('doctor', 'Rebuild local inventory')
  .option('--json', 'Output JSON')
  .action((options: { json?: boolean }) => {
    return runCommand(() => doctorCommand(options), Boolean(options.json))
  })

cli
  .command('publish <path>', 'Publish a local skill package')
  .option('--namespace <slug>', 'Namespace')
  .option('--visibility <v>', 'Visibility (public|namespace-only|private)')
  .option('--registry <url>', 'Registry URL')
  .option('--token <token>', 'API token')
  .option('--json', 'Output JSON')
  .action((path: string, options: PublishCommandOptions) => {
    return runCommand(() => publishCommand(path, options), Boolean(options.json))
  })

cli.help()

if (import.meta.main) {
  cli.parse(process.argv)
}
