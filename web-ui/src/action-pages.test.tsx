import React from 'react'
import {render, screen} from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import {BaseStyles, ThemeProvider} from '@primer/react'
import {describe, expect, it, vi} from 'vitest'
import {RecoveryPage, ReportsPage} from './main'

const renderPrimer = (node: React.ReactNode) => render(<ThemeProvider><BaseStyles>{node}</BaseStyles></ThemeProvider>)

describe('explicit web actions', () => {
  it('generates a report only after the operator clicks the action', async () => {
    const onGenerate = vi.fn().mockResolvedValue(undefined)
    renderPrimer(<ReportsPage rows={[{schemaVersion: '1.0.0'}]} actionResult={null} onGenerate={onGenerate}/>)

    await userEvent.click(screen.getByRole('button', {name: 'Generate local report'}))

    expect(onGenerate).toHaveBeenCalledOnce()
  })

  it('requires the STAGE phrase before enabling restore staging', async () => {
    const onStage = vi.fn().mockResolvedValue(undefined)
    renderPrimer(<RecoveryPage rows={[{status: 'PREVIEW'}]} actionResult={null} onStage={onStage}/>)

    await userEvent.click(screen.getByRole('button', {name: 'Stage restore'}))
    const confirm = screen.getByRole('button', {name: 'Confirm stage'})
    expect((confirm as HTMLButtonElement).disabled).toBe(true)
    await userEvent.type(screen.getByLabelText('Type STAGE to confirm'), 'STAGE')
    await userEvent.click(confirm)

    expect(onStage).toHaveBeenCalledOnce()
  })
})
