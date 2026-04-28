import { readFile, rename, writeFile } from 'node:fs/promises'
import { dirname } from 'node:path'
import { joinPath, userStateDir, ensureDir, pathExists } from '../platform/paths'

export interface InventoryTarget {
  agent: string
  rootDir: string
  installDir: string
  installedAt: string
}

export interface InventoryItem {
  registry: string
  namespace: string
  slug: string
  version: string
  targets: InventoryTarget[]
}

export interface Inventory {
  items: InventoryItem[]
}

export class InventoryStore {
  readonly path: string

  constructor(home?: string) {
    this.path = joinPath(userStateDir(home), 'inventory.json')
  }

  async read(): Promise<Inventory> {
    if (!(await pathExists(this.path))) return { items: [] }
    return JSON.parse(await readFile(this.path, 'utf-8')) as Inventory
  }

  async write(inventory: Inventory): Promise<void> {
    await ensureDir(dirname(this.path))
    await writeFile(this.path, JSON.stringify(inventory, null, 2))
  }

  async writeAtomic(inventory: Inventory): Promise<void> {
    await ensureDir(dirname(this.path))
    const payload = JSON.stringify(inventory, null, 2)
    JSON.parse(payload)
    const tmpPath = `${this.path}.${process.pid}.${Date.now()}.tmp`
    await writeFile(tmpPath, payload)
    JSON.parse(await readFile(tmpPath, 'utf-8'))
    await rename(tmpPath, this.path)
  }

  async upsertTarget(
    registry: string,
    namespace: string,
    slug: string,
    version: string,
    target: InventoryTarget
  ): Promise<void> {
    const inventory = await this.read()
    let item = inventory.items.find(
      i => i.registry === registry && i.namespace === namespace && i.slug === slug
    )
    if (!item) {
      item = { registry, namespace, slug, version, targets: [] }
      inventory.items.push(item)
    }
    item.version = version
    const existingIdx = item.targets.findIndex(t => t.installDir === target.installDir)
    if (existingIdx >= 0) {
      item.targets[existingIdx] = target
    } else {
      item.targets.push(target)
    }
    await this.write(inventory)
  }

  async removeTarget(registry: string, namespace: string, slug: string, installDir: string): Promise<boolean> {
    const inventory = await this.read()
    const item = inventory.items.find(i => i.registry === registry && i.namespace === namespace && i.slug === slug)
    if (!item) return false
    const idx = item.targets.findIndex(t => t.installDir === installDir)
    if (idx < 0) return false
    item.targets.splice(idx, 1)
    if (item.targets.length === 0) {
      inventory.items = inventory.items.filter(i => i !== item)
    }
    await this.write(inventory)
    return true
  }

  async removeTargetsByInstallDir(installDir: string): Promise<number> {
    const inventory = await this.read()
    let removed = 0
    for (const item of inventory.items) {
      const before = item.targets.length
      item.targets = item.targets.filter(t => t.installDir !== installDir)
      removed += before - item.targets.length
    }
    if (removed > 0) {
      inventory.items = inventory.items.filter(item => item.targets.length > 0)
      await this.write(inventory)
    }
    return removed
  }
}
