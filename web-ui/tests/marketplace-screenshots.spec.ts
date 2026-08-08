import {expect, test} from '@playwright/test'
import path from 'node:path'
import {fileURLToPath} from 'node:url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const outDir = path.resolve(__dirname, '../../../plugtrace-docs/marketplace/assets')

test.beforeEach(async ({page}) => {
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const pathName = url.pathname
    const json = (value: unknown, status = 200) => route.fulfill({
      status, contentType: 'application/json', body: JSON.stringify(value)
    })
    if (pathName.endsWith('/status')) {
      const failing = url.searchParams.get('mode') === 'failing'
      return json({
        deployment: {
          id: failing ? 'd43' : 'd42',
          localSequence: failing ? 43 : 42,
          nodeId: 'survival-1',
          health: failing ? 'FAILING' : 'HEALTHY',
          serverImplementation: 'Paper'
        },
        baseline: 'Last healthy deployment #41',
        changes: failing ? 3 : 0,
        issues: failing ? 2 : 0,
        verification: {
          checks: failing
            ? [
                {checkId: 'server-ready', displayName: 'Server ready', status: 'PASS', summary: 'Passed'},
                {checkId: 'expected-plugins', displayName: 'Expected plugins', status: 'FAIL', summary: 'Vault missing'}
              ]
            : [{checkId: 'server-ready', displayName: 'Server ready', status: 'PASS', summary: 'Passed'}]
        },
        platform: {artifact: 'paper-modern'},
        localOnly: true,
        web: {bind: '127.0.0.1', port: 9465, allowRemote: false, address: 'http://127.0.0.1:9465'},
        config: {'privacy.mode': 'hash-only', 'web.port': 9465},
        ritual: failing
          ? {headline: 'Update looks broken', topChanges: ['Vault removed', 'config keys lost'], nextCommand: '/plugtrace report upload'}
          : {headline: 'Update looks healthy', topChanges: [], nextCommand: '/plugtrace status'}
      })
    }
    if (pathName.endsWith('/deployments')) return json([
      {id: 'd43', localSequence: 43, health: 'FAILING', summary: 'Deployment #43'},
      {id: 'd42', localSequence: 42, health: 'HEALTHY', summary: 'Deployment #42'},
      {id: 'd41', localSequence: 41, health: 'HEALTHY', summary: 'Deployment #41'}
    ])
    if (pathName.endsWith('/diff')) return json({
      baseline: 'Last healthy deployment #41',
      changes: [
        {kind: 'PLUGIN_REMOVED', componentKey: 'Vault', summary: 'Vault removed'},
        {kind: 'CONFIG_STRUCTURE', componentKey: 'config.yml', summary: 'Structural keys lost'}
      ],
      suspects: [{componentKey: 'Vault', band: 'HIGH', changeSummary: 'Removed after update'}]
    })
    if (pathName.endsWith('/checkpoints')) return json([{id: 'cp1', name: 'before-update', deploymentId: 'd41'}])
    if (pathName.endsWith('/reports') && request.method() === 'POST') {
      return json({schemaVersion: '1.0.0', privacyMode: 'hash-only-config', shareHint: 'https://plugtrace.dev/r/demo#k=example'}, 201)
    }
    if (pathName.endsWith('/reports')) return json({formats: ['json', 'markdown', 'html'], schemaVersion: '1.0.0'})
    if (pathName.includes('/timeline') || pathName.endsWith('/events')) {
      return json([{at: '2026-07-24T12:00:00Z', kind: 'DEPLOYMENT', summary: 'Deployment #43 started'}])
    }
    return json([])
  })
})

async function openDashboard(page: import('@playwright/test').Page) {
  await page.goto('/')
  await page.getByLabel('Bearer token').fill('listing-shot-token')
  await page.getByRole('button', {name: 'Open dashboard'}).click()
}

test('capture marketplace gallery screenshots', async ({page}) => {
  await page.setViewportSize({width: 1280, height: 800})
  await openDashboard(page)
  await expect(page.getByRole('heading', {name: 'Did this update work?'})).toBeVisible()
  await page.screenshot({path: path.join(outDir, 'screenshot-overview-healthy.png'), fullPage: true})

  await page.getByRole('button', {name: 'Diff'}).click()
  await expect(page.getByText('Vault removed')).toBeVisible()
  await page.screenshot({path: path.join(outDir, 'screenshot-diff.png'), fullPage: true})

  await page.getByRole('button', {name: 'Deployments'}).click()
  await expect(page.getByText('Deployment #41', {exact: true})).toBeVisible()
  await page.screenshot({path: path.join(outDir, 'screenshot-timeline-deployments.png'), fullPage: true})

  await page.getByRole('button', {name: 'Reports'}).click()
  await page.getByRole('button', {name: 'Generate local report'}).click()
  await expect(page.getByText('Report generated')).toBeVisible()
  await page.screenshot({path: path.join(outDir, 'screenshot-report.png'), fullPage: true})
})
