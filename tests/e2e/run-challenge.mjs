#!/usr/bin/env node
import { spawnSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const __dirname = dirname(fileURLToPath(import.meta.url))
const rootDir = resolve(__dirname, '../..')
const frontendDir = resolve(rootDir, 'frontend')
const runnerPath = resolve(frontendDir, 'tests/e2e/run-challenge.ts')
const args = process.argv.slice(2)

const isWin = process.platform === 'win32'
const command = isWin ? 'npx.cmd' : 'npx'

const res = spawnSync(command, ['tsx', runnerPath, ...args], {
  cwd: frontendDir,
  stdio: 'inherit',
  shell: isWin
})

process.exit(res.status ?? 0)
