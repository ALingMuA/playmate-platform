/**
 * E2E Master Test Runner
 * Executes Tier 1-4 test suites, prints detailed summary report, and signals status.
 */

import { runTier1Tests, type TestResult } from './tier1-feature-coverage.test'
import { runTier2Tests } from './tier2-boundary-corner.test'
import { runTier3Tests } from './tier3-cross-feature.test'
import { runTier4Tests } from './tier4-real-world-scenarios.test'
import { runTier5Tests } from './tier5-adversarial-challenger.test'

async function main() {
  const args = process.argv.slice(2)
  const tierArg = args.find(a => a.startsWith('--tier='))?.split('=')[1]
  const targetTier = tierArg ? parseInt(tierArg, 10) : null

  console.log('='.repeat(78))
  console.log('  E2E TEST SUITE: Unified Message Center (/messages) Integration')
  console.log('  Architecture: Vue 3 + TypeScript + Pinia + Vue Router 4 (Opaque-Box)')
  console.log('='.repeat(78))

  const allResults: TestResult[] = []
  const startTime = Date.now()

  // Execute Tier 1
  if (!targetTier || targetTier === 1) {
    console.log('\n--- [TIER 1: Feature Coverage (R1, R2, R3)] ---')
    const t1 = await runTier1Tests()
    for (const r of t1) {
      printResult(r)
    }
    allResults.push(...t1)
  }

  // Execute Tier 2
  if (!targetTier || targetTier === 2) {
    console.log('\n--- [TIER 2: Boundary & Corner Cases (R1, R2, R3)] ---')
    const t2 = await runTier2Tests()
    for (const r of t2) {
      printResult(r)
    }
    allResults.push(...t2)
  }

  // Execute Tier 3
  if (!targetTier || targetTier === 3) {
    console.log('\n--- [TIER 3: Cross-Feature Combinations & State Isolation] ---')
    const t3 = await runTier3Tests()
    for (const r of t3) {
      printResult(r)
    }
    allResults.push(...t3)
  }

  // Execute Tier 4
  if (!targetTier || targetTier === 4) {
    console.log('\n--- [TIER 4: Real-World Application User Scenarios] ---')
    const t4 = await runTier4Tests()
    for (const r of t4) {
      printResult(r)
    }
    allResults.push(...t4)
  }

  // Execute Tier 5
  if (!targetTier || targetTier === 5) {
    console.log('\n--- [TIER 5: Adversarial Coverage Hardening & Empirical Challenge] ---')
    const t5 = await runTier5Tests()
    for (const r of t5) {
      printResult(r)
    }
    allResults.push(...t5)
  }

  const durationTotal = Date.now() - startTime
  const passedCount = allResults.filter(r => r.passed).length
  const failedCount = allResults.filter(r => !r.passed).length

  console.log('\n' + '='.repeat(78))
  console.log('  EXECUTION SUMMARY')
  console.log('='.repeat(78))
  console.log(`  Total Test Cases : ${allResults.length}`)
  console.log(`  Passed           : ${passedCount}`)
  console.log(`  Failed           : ${failedCount}`)
  console.log(`  Duration         : ${durationTotal} ms`)
  console.log('='.repeat(78))

  // Tier-by-Tier breakdown
  const tiers = [1, 2, 3, 4, 5]
  for (const t of tiers) {
    const tierTests = allResults.filter(r => r.tier === t)
    if (tierTests.length > 0) {
      const p = tierTests.filter(r => r.passed).length
      const f = tierTests.filter(r => !r.passed).length
      console.log(`  Tier ${t} Results: ${p} passed, ${f} failed (total: ${tierTests.length})`)
    }
  }
  console.log('='.repeat(78))

  if (failedCount > 0) {
    console.error('\nFAILED TESTS:')
    for (const f of allResults.filter(r => !r.passed)) {
      console.error(`  - [${f.id}] ${f.name} (Tier ${f.tier}, ${f.area}): ${f.error}`)
    }
    process.exit(1)
  } else {
    console.log('\nALL E2E TESTS PASSED SUCCESSFULLY! (100% PASS RATE)')
    process.exit(0)
  }
}

function printResult(r: TestResult) {
  const status = r.passed ? '\x1b[32mPASS\x1b[0m' : '\x1b[31mFAIL\x1b[0m'
  console.log(`  [${status}] ${r.id.padEnd(14)} : ${r.name} (${r.durationMs}ms)`)
  if (!r.passed && r.error) {
    console.log(`         \x1b[31mError: ${r.error}\x1b[0m`)
  }
}

main().catch(err => {
  console.error('Fatal Runner Error:', err)
  process.exit(1)
})
