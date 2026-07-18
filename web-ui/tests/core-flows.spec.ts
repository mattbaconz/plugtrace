import {expect, test} from '@playwright/test'

test.beforeEach(async ({page}) => {
  await page.route('**/api/v1/**', async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname
    const json = (value: unknown, status = 200) => route.fulfill({
      status, contentType: 'application/json', body: JSON.stringify(value)
    })
    if (path.endsWith('/status')) return json({
      deployment: {id: 'd42', localSequence: 42, nodeId: 'survival-1', health: 'OBSERVING', serverImplementation: 'Paper'},
      baseline: 'Last healthy deployment #41', changes: 2, issues: 1,
      verification: {checks: [{checkId: 'server-ready', displayName: 'Server ready', status: 'PASS', summary: 'Passed'}]},
      platform: {artifact: 'paper-modern'}, localOnly: true,
      web: {bind: '127.0.0.1', port: 9465, allowRemote: false, address: 'http://127.0.0.1:9465'},
      config: {'privacy.mode': 'hash-only', 'web.port': 9465}
    })
    if (path.endsWith('/deployments')) return json([
      {id: 'd42', localSequence: 42, health: 'OBSERVING', summary: 'Deployment #42'},
      {id: 'd41', localSequence: 41, health: 'HEALTHY', summary: 'Deployment #41'}
    ])
    if (path.endsWith('/diff')) return json({
      baseline: 'Last healthy deployment #41',
      changes: [{kind: 'PLUGIN_ADDED', componentKey: 'Example', summary: 'Example added'}],
      suspects: [{componentKey: 'Example', band: 'MEDIUM', changeSummary: 'New plugin'}]
    })
    if (path.endsWith('/checkpoints') && request.method() === 'POST') return json({id: 'cp1', name: 'Web checkpoint'}, 201)
    if (path.endsWith('/checkpoints')) return json([{id: 'cp1', name: 'before-update', deploymentId: 'd41'}])
    if (path.endsWith('/reports') && request.method() === 'POST') return json({schemaVersion: '1.0.0', privacyMode: 'hash-only-config'}, 201)
    if (path.endsWith('/reports')) return json({formats: ['json', 'markdown', 'html'], schemaVersion: '1.0.0'})
    if (path.endsWith('/restore-plans/stage')) return json({id: 'plan-1', status: 'STAGED'}, 202)
    if (path.endsWith('/restore-plans')) return json({id: 'plan-1', status: 'PREVIEW', warnings: ['Review migration safety']})
    return json([])
  })
})

test('first-run onboarding opens the local deployment dashboard', async ({page}) => {
  await page.goto('/')
  await page.getByLabel('Bearer token').fill('test-token')
  await page.getByRole('button', {name: 'Open dashboard'}).click()
  await expect(page.getByRole('heading', {name: 'Did this update work?'})).toBeVisible()
  await expect(page.getByText('Deployment #42')).toBeVisible()
})

test('deployment comparison, report generation, and restore staging are explicit flows', async ({page}) => {
  await page.goto('/')
  await page.getByLabel('Bearer token').fill('test-token')
  await page.getByRole('button', {name: 'Open dashboard'}).click()

  await page.getByRole('button', {name: 'Deployments'}).click()
  await expect(page.getByText('Deployment #41', {exact: true})).toBeVisible()

  await page.getByRole('button', {name: 'Diff'}).click()
  await expect(page.getByText('Example added')).toBeVisible()

  await page.getByRole('button', {name: 'Checkpoints'}).click()
  await expect(page.getByText('before-update')).toBeVisible()

  await page.getByRole('button', {name: 'Reports'}).click()
  await page.getByRole('button', {name: 'Generate local report'}).click()
  await expect(page.getByText('Report generated')).toBeVisible()
  await expect(page.getByText('hash-only-config')).toBeVisible()

  await page.getByRole('button', {name: 'Recovery'}).click()
  await page.getByRole('button', {name: 'Stage restore'}).click()
  await page.getByLabel('Type STAGE to confirm').fill('STAGE')
  await page.getByRole('button', {name: 'Confirm stage'}).click()
  await expect(page.getByText('Restore staged')).toBeVisible()
})
