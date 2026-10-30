#!/usr/bin/env node
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const rootDir = resolve(__dirname, '../..')
const targetScript = resolve(rootDir, 'frontend/tests/e2e/run-challenger.mjs')
const args = process.argv.slice(2)

const res = spawnSync(process.execPath, [targetScript, ...args], {
  cwd: rootDir,
  stdio: 'inherit'
})

process.exit(res.status ?? 0)
