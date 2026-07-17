import { randomUUID } from 'crypto'
import { redis } from '../redis/client'
import { populatePrices } from './populatePrices'
import { cleanupPriceHistory } from './cleanupPriceHistory'
import { AlertService } from '../services/alerts'
import type { FastifyBaseLogger } from 'fastify'

// The `pipeline:` namespace is deliberate: app.ts wipes every `prices:*` key on
// boot, which would blank this on each deploy. Same reason the /health freshness
// marker lives there.
export const REFRESH_LOCK_KEY = 'pipeline:jobs:refresh-prices:lock'
export const REFRESH_STATUS_KEY = 'pipeline:jobs:refresh-prices:status'

// The job takes minutes, so 1h is ample. The TTL is what makes a process that
// dies mid-run self-heal: the next cron 6h later finds an expired lock rather
// than a pipeline wedged forever.
const LOCK_TTL_SECONDS = 60 * 60
const STATUS_TTL_SECONDS = 48 * 60 * 60

export type RefreshState = 'running' | 'ok' | 'failed'

export interface RefreshStatus {
  runId: string
  state: RefreshState
  startedAt: string
  finishedAt?: string
  durationMs?: number
  updated?: number
  error?: string
}

export type StartResult =
  | { started: true; runId: string }
  | { started: false; reason: 'already-running' }

const alertService = new AlertService()

async function writeStatus(status: RefreshStatus): Promise<void> {
  await redis.set(REFRESH_STATUS_KEY, JSON.stringify(status), { ex: STATUS_TTL_SECONDS })
}

export async function getRefreshStatus(): Promise<RefreshStatus | null> {
  const raw = await redis.get(REFRESH_STATUS_KEY)
  if (!raw) return null
  // Upstash deserialises JSON, but a value written by a raw client can still
  // come back as a string.
  return typeof raw === 'string' ? (JSON.parse(raw) as RefreshStatus) : (raw as RefreshStatus)
}

async function executeRun(runId: string, startedAt: number, log: FastifyBaseLogger): Promise<void> {
  // Release the lock *before* publishing the terminal status, never after. A
  // poller that sees 'ok'/'failed' may trigger a retry straight away, and it
  // must not be turned away as 'already-running' by the lock of the very run it
  // just watched finish. Ordering it this way makes a terminal status a promise
  // that the lock is already gone.
  //
  // Every exit path routes through here, so there is no `finally` release: the
  // only way to miss one is Redis itself failing, and a `finally` would be
  // hitting that same broken Redis. That case is what LOCK_TTL_SECONDS covers.
  const finish = async (extra: Partial<RefreshStatus>) => {
    await redis.del(REFRESH_LOCK_KEY)
    await writeStatus({
      runId,
      state: 'ok',
      startedAt: new Date(startedAt).toISOString(),
      finishedAt: new Date().toISOString(),
      durationMs: Date.now() - startedAt,
      ...extra,
    })
  }

  try {
    const { updated } = await populatePrices(log)

    // Every fetcher swallows its own error and returns an empty map, so all three
    // marketplaces being down reaches here without throwing. Without this guard
    // the run reports 'ok' and the workflow goes green over a dead pipeline.
    if (updated === 0) {
      log.error('[JobRunner] No skins updated — aborting run')
      await finish({
        state: 'failed',
        updated: 0,
        error: 'No skins updated — every marketplace returned nothing',
      })
      return
    }

    await alertService.checkAll()
    await cleanupPriceHistory(log)
    await finish({ state: 'ok', updated })
    log.info(`[JobRunner] Run ${runId} finished — ${updated} skins updated`)
  } catch (err) {
    log.error({ err }, `[JobRunner] Run ${runId} failed`)
    await finish({ state: 'failed', error: (err as Error).message })
  }
}

export async function runRefreshPrices(log: FastifyBaseLogger): Promise<StartResult> {
  const runId = randomUUID()
  const acquired = await redis.set(REFRESH_LOCK_KEY, runId, { nx: true, ex: LOCK_TTL_SECONDS })
  if (acquired !== 'OK') {
    log.info('[JobRunner] Lock held — a refresh is already running')
    return { started: false, reason: 'already-running' }
  }

  const startedAt = Date.now()
  await writeStatus({ runId, state: 'running', startedAt: new Date(startedAt).toISOString() })

  // Deliberately not awaited: the HTTP caller gets 202 at once and polls the
  // status. executeRun captures its own failures; this .catch only covers the
  // bookkeeping writes themselves failing.
  void executeRun(runId, startedAt, log).catch((err) =>
    log.error({ err }, '[JobRunner] Status bookkeeping failed'),
  )

  return { started: true, runId }
}
