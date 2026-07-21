import React, {useCallback, useEffect, useMemo, useState} from 'react'
import {createRoot} from 'react-dom/client'
import {BaseStyles, Box, Button, Flash, FormControl, Heading, Label, PageLayout, Text, TextInput, ThemeProvider} from '@primer/react'
import {CheckCircleFillIcon, DiffIcon, DotFillIcon, HistoryIcon, IssueOpenedIcon, MilestoneIcon, ShieldCheckIcon, SyncIcon} from '@primer/octicons-react'
import './styles.css'

function healthSymbol(health: string) {
  switch ((health || 'UNKNOWN').toUpperCase()) {
    case 'HEALTHY': return '+'
    case 'FAILING':
    case 'CRASHED': return 'x'
    case 'DEGRADED': return '!'
    default: return '*'
  }
}

function HealthPill({health}: {health: string}) {
  const key = (health || 'UNKNOWN').toUpperCase()
  return <span className={`health-pill ${key}`}><span aria-hidden>{healthSymbol(key)}</span>{key}</span>
}

type Tab = 'overview' | 'timeline' | 'deployments' | 'diff' | 'checkpoints' | 'checks' | 'issues' | 'incidents' | 'reports' | 'recovery' | 'settings'
type AnyRecord = Record<string, any>

const tabs: Array<{id: Tab; label: string}> = [
  {id: 'overview', label: 'Overview'},
  {id: 'timeline', label: 'Timeline'},
  {id: 'deployments', label: 'Deployments'},
  {id: 'diff', label: 'Diff'},
  {id: 'checkpoints', label: 'Checkpoints'},
  {id: 'checks', label: 'Verification'},
  {id: 'issues', label: 'Issues'},
  {id: 'incidents', label: 'Incidents'},
  {id: 'reports', label: 'Reports'},
  {id: 'recovery', label: 'Recovery'},
  {id: 'settings', label: 'Settings'}
]

function App() {
  const [token, setToken] = useState(() => sessionStorage.getItem('plugtraceToken') || '')
  const [draft, setDraft] = useState(token)
  const [tab, setTab] = useState<Tab>('overview')
  const [status, setStatus] = useState<AnyRecord | null>(null)
  const [rows, setRows] = useState<AnyRecord[]>([])
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [actionResult, setActionResult] = useState<AnyRecord | null>(null)
  const headers = useMemo(() => ({Authorization: `Bearer ${token}`}), [token])

  const load = useCallback(async (path = 'status') => {
    if (!token) return
    const response = await fetch(`/api/v1/${path}`, {headers})
    if (!response.ok) throw new Error(response.status === 401 ? 'That token was not accepted.' : `Request failed (${response.status}).`)
    return response.json()
  }, [token, headers])

  const write = useCallback(async (path: string) => {
    const response = await fetch(`/api/v1/${path}`, {method: 'POST', headers})
    if (!response.ok) throw new Error(`Request failed (${response.status}).`)
    return response.json()
  }, [headers])

  const refreshTab = useCallback(async (active: Tab) => {
    if (!token || active === 'overview' || active === 'settings') return
    const endpoint = active === 'checks' ? 'verification'
      : active === 'recovery' ? 'restore-plans'
      : active === 'timeline' ? 'deployments'
      : active
    const value = await load(endpoint)
    if (active === 'diff' || active === 'recovery' || active === 'reports' || active === 'checks') {
      setRows(Array.isArray(value) ? value : value == null ? [] : [value])
    } else {
      setRows(Array.isArray(value) ? value : [])
    }
  }, [token, load])

  useEffect(() => {
    setError('')
    if (!token) return
    load('status').then(setStatus).catch(e => setError(e.message))
  }, [token, load])

  useEffect(() => {
    setError('')
    setNotice('')
    refreshTab(tab).catch(e => setError(e.message))
  }, [tab, refreshTab])

  // Short poll — SSE cannot send Bearer headers from EventSource.
  useEffect(() => {
    if (!token) return
    const id = window.setInterval(() => {
      load('status').then(setStatus).catch(() => undefined)
      refreshTab(tab).catch(() => undefined)
    }, 8000)
    return () => window.clearInterval(id)
  }, [token, tab, load, refreshTab])

  if (!token || !status) return <ThemeProvider colorMode="night"><BaseStyles><main className="login">
    <Box className="login-card">
      <div className="login-brand"><span className="brand-mark" aria-hidden>*</span><span className="brand-name">PlugTrace</span></div>
      <Text as="p" sx={{color: 'fg.muted'}}>Paste a read or admin token created from the server console. Tokens stay in this browser tab.</Text>
      {error && <Flash variant="danger">{error}</Flash>}
      <FormControl><FormControl.Label>Bearer token</FormControl.Label><TextInput block type="password" value={draft} onChange={e => setDraft(e.target.value)} /></FormControl>
      <Button variant="primary" block onClick={() => {sessionStorage.setItem('plugtraceToken', draft); setToken(draft)}}>Open dashboard</Button>
      <Text as="p" sx={{color: 'fg.muted', fontSize: 0, mt: 3}}>Console: plugtrace web token create local-admin admin</Text>
    </Box>
  </main></BaseStyles></ThemeProvider>

  const deployment = status.deployment || {}
  const verification = status.verification || {}
  const health = deployment.health || 'UNKNOWN'
  return <ThemeProvider colorMode="night"><BaseStyles>
    <header className="topbar"><div className="brand"><span className="brand-mark" aria-hidden>*</span><span className="brand-name">PlugTrace</span><span className="brand-pipe">|</span><Label variant="secondary">local</Label></div><Text sx={{color: 'fg.muted'}}>{deployment.serverImplementation || 'Minecraft server'}</Text></header>
    <PageLayout containerWidth="full"><PageLayout.Pane position="start" width="medium" divider="line"><nav className="nav" aria-label="PlugTrace">
      <div className="repo"><Text sx={{fontSize: 0, color: 'fg.muted'}}>SERVER</Text><strong>{deployment.nodeId || 'Current node'}</strong></div>
      {tabs.map(item => <button key={item.id} className={tab === item.id ? 'nav-item active' : 'nav-item'} onClick={() => setTab(item.id)}>{item.label}</button>)}
    </nav></PageLayout.Pane><PageLayout.Content><main className="content">
      {error && <Flash variant="danger">{error}</Flash>}
      {notice && <Flash variant="success">{notice}</Flash>}
      {tab === 'overview' ? <Overview status={status} health={health} onVerify={async () => {
        setActionResult(await write('verification'))
        setStatus(await load('status'))
        setNotice('Verification started')
      }}/> : tab === 'timeline' ? <TimelinePage rows={rows}/>
        : tab === 'diff' ? <DiffPage rows={rows}/>
        : tab === 'checkpoints' ? <CheckpointsPage rows={rows} onCreate={async () => {
          const created = await write('checkpoints')
          setActionResult(created)
          setNotice('Checkpoint created')
          await refreshTab('checkpoints')
        }}/>
        : tab === 'reports' ? <ReportsPage rows={rows} actionResult={actionResult} onGenerate={async () => {
          const result = await write('reports')
          setActionResult(result)
          setNotice('Report generated')
          await refreshTab('reports')
          setStatus(await load('status'))
        }}/> : tab === 'recovery' ? <RecoveryPage rows={rows} actionResult={actionResult} onStage={async () => {
          const result = await write('restore-plans/stage')
          setActionResult(result)
          setNotice('Restore staged')
          await refreshTab('recovery')
        }}/> : tab === 'settings' ? <Settings status={status} onForget={() => {sessionStorage.removeItem('plugtraceToken'); setToken('')}}/>
        : <DataPage tab={tab} rows={rows}/>}
    </main></PageLayout.Content></PageLayout>
  </BaseStyles></ThemeProvider>
}

function Overview({status, health, onVerify}: {status: AnyRecord; health: string; onVerify: () => void}) {
  const verification = status.verification || {}
  const ritual = status.ritual || {}
  const topChanges: AnyRecord[] = Array.isArray(ritual.topChanges) ? ritual.topChanges : []
  const suspect = ritual.strongestSuspect || null
  const noise = ritual.noiseContext || {}
  const nextCommands: string[] = Array.isArray(ritual.nextCommands) ? ritual.nextCommands : (ritual.nextCommand ? [ritual.nextCommand] : [])
  return <><div className="page-head"><div><Text sx={{color: 'fg.muted'}}>CURRENT DEPLOYMENT</Text><h1>Did this update work?</h1></div><Button leadingVisual={SyncIcon} onClick={onVerify}>Run verification</Button></div>
    <section className="hero"><div><HealthPill health={health}/><Heading as="h2">Deployment <span className="mono">#{status.deployment?.localSequence}</span></Heading><Text as="p" sx={{color: 'fg.muted'}}>{status.baseline}</Text></div><CheckCircleFillIcon size={44}/></section>
    <div className="metric-grid"><Metric title="Checks" value={verification.checks?.length ?? ritual.verificationChecks ?? '—'} icon={<ShieldCheckIcon/>}/><Metric title="Changes" value={status.changes ?? ritual.changeCount ?? 0} icon={<HistoryIcon/>}/><Metric title="Issues" value={status.issues ?? ritual.issueCount ?? 0} icon={<IssueOpenedIcon/>}/><Metric title="Privacy" value="Local only" icon={<DotFillIcon/>}/></div>
    <section className="panel"><Heading as="h2">Ritual</Heading>
      {suspect ? <Text as="p" sx={{m: 0}}><strong>Strongest suspect:</strong> {String(suspect.component)} <Label>{String(suspect.band)}</Label>{suspect.knownChurn ? <Label variant="attention">known churn</Label> : null}<Text as="span" sx={{display:'block', color:'fg.muted', mt:1}}>{String(suspect.summary || '')}</Text></Text> : <Text as="p" sx={{color:'fg.muted'}}>No ranked suspect yet.</Text>}
      {topChanges.length > 0 && <Box sx={{mt: 3}}><Text sx={{fontSize: 0, color: 'fg.muted'}}>TOP CHANGES</Text>{topChanges.map((row, i) => (
        <div className="check" key={String(row.component) + i}><Label>{String(row.type)}</Label><div><strong>{String(row.component)}</strong>{row.knownChurn ? <Label variant="attention">known churn</Label> : null}<Text as="p" sx={{color:'fg.muted', m:0}}>{String(row.explanation || '')}</Text></div></div>
      ))}</Box>}
      {(noise.knownChurnChangeCount > 0 || noise.knownChurnIssueCount > 0 || noise.plugDevPresent) && (
        <Flash variant="warning" sx={{mt: 3}}>{String(noise.hint || 'Known PlugDev/runtime churn is present. Annotate context before treating DEGRADED as a production failure.')}</Flash>
      )}
      {nextCommands.length > 0 && <Box sx={{mt: 3}}><Text sx={{fontSize: 0, color: 'fg.muted'}}>NEXT</Text>{nextCommands.slice(0, 3).map(cmd => <Text as="p" key={cmd} className="mono" sx={{m: '0.25rem 0'}}>{cmd}</Text>)}</Box>}
    </section>
    <section className="panel"><Heading as="h2">Verification evidence</Heading>{verification.checks?.length ? verification.checks.map((check: AnyRecord) => <div className="check" key={check.checkId}><Label variant={check.status === 'PASS' ? 'success' : check.status === 'FAIL' ? 'danger' : 'attention'}>{check.status === 'PASS' ? '+' : check.status === 'FAIL' ? 'x' : '!'} {check.status}</Label><div><strong>{check.displayName}</strong><Text as="p" sx={{color:'fg.muted', m:0}}>{check.summary}</Text></div></div>) : <Empty text="Verification begins 30 seconds after server ready, or run it now."/>}</section>
  </>
}

function Metric({title, value, icon}: {title: string; value: React.ReactNode; icon: React.ReactNode}) {return <div className="metric"><span>{icon}</span><Text sx={{color:'fg.muted'}}>{title}</Text><strong>{value}</strong></div>}

function TimelinePage({rows}: {rows: AnyRecord[]}) {
  const ordered = [...rows].sort((a, b) => (b.localSequence || 0) - (a.localSequence || 0))
  return <><div className="page-head"><div><Text sx={{color:'fg.muted'}}>DEPLOYMENT HISTORY</Text><h1>Timeline</h1></div></div>
    <section className="panel list">{ordered.length ? ordered.map(row => (
      <article key={row.id || row.localSequence} className="timeline-row">
        <MilestoneIcon/>
        <div>
          <div className="row-title"><strong>#{row.localSequence} </strong><HealthPill health={row.health || 'UNKNOWN'}/><Label>{row.startedAt || row.id}</Label></div>
          <Text as="p" sx={{color:'fg.muted', m:0}}>{row.serverImplementation || row.minecraftVersion || 'Deployment record'}</Text>
        </div>
      </article>
    )) : <Empty text="No deployments recorded yet."/>}</section></>
}

function DiffPage({rows}: {rows: AnyRecord[]}) {
  const payload = rows[0] && (rows[0].changes || rows[0].suspects) ? rows[0]
    : {changes: rows, suspects: []}
  const changes: AnyRecord[] = Array.isArray(payload.changes) ? payload.changes : []
  const suspects: AnyRecord[] = Array.isArray(payload.suspects) ? payload.suspects : []
  return <><div className="page-head"><div><Text sx={{color:'fg.muted'}}>BASELINE COMPARISON</Text><h1>Diff</h1></div></div>
    {payload.baseline && <Text as="p" sx={{color:'fg.muted'}}>{String(payload.baseline)}</Text>}
    <section className="panel"><Heading as="h2">Changes</Heading>
      {changes.length ? changes.map((row: AnyRecord, i: number) => (
        <article key={row.id || i}><div className="row-title"><DiffIcon/><strong>{String(row.type || row.kind || row.changeType || row.summary || 'Change')}</strong>{row.componentKey && <Label>{row.componentKey}</Label>}</div>
          <Text as="p" sx={{color:'fg.muted'}}>{row.explanation || row.summary || JSON.stringify(row)}</Text></article>
      )) : <Empty text="No diff against a healthy baseline yet."/>}
    </section>
    {suspects.length > 0 && <section className="panel"><Heading as="h2">Suspects</Heading>{suspects.map((s: AnyRecord, i: number) => (
      <article key={s.componentKey || i}><div className="row-title"><strong>{s.componentKey}</strong><Label>{s.band}</Label></div><Text as="p" sx={{color:'fg.muted'}}>{s.changeSummary}</Text></article>
    ))}</section>}
  </>
}

function CheckpointsPage({rows, onCreate}: {rows: AnyRecord[]; onCreate: () => Promise<void>}) {
  return <><div className="page-head"><div><Text sx={{color:'fg.muted'}}>HEALTHY REFERENCES</Text><h1>Checkpoints</h1></div><Button variant="primary" onClick={onCreate}>Create checkpoint</Button></div>
    <section className="panel list">{rows.length ? rows.map((row, i) => (
      <article key={row.id || i}><div className="row-title"><strong>{row.name || row.id}</strong><Label>{row.createdAt || 'checkpoint'}</Label></div>
        <Text as="p" sx={{color:'fg.muted'}}>Deployment {row.deploymentId || row.sourceDeploymentId || '—'}</Text></article>
    )) : <Empty text="No checkpoints yet. Mark a deployment healthy, then create one."/>}</section></>
}

function DataPage({tab, rows}: {tab: Tab; rows: AnyRecord[]}) {
  return <><div className="page-head"><div><Text sx={{color:'fg.muted'}}>EVIDENCE</Text><h1>{tabs.find(t => t.id === tab)?.label}</h1></div></div>
    <section className="panel list">{rows.length ? rows.map((row, i) => (
      <article key={row.id || i}><div className="row-title"><strong>{row.summary || row.displayName || row.normalizedType || row.id || `${tab} record`}</strong>
        {row.health && <Label>{row.health}</Label>}{row.status && <Label>{row.status}</Label>}</div>
        <Text as="p" sx={{color:'fg.muted'}}>{row.message || row.explanation || row.checkId || ''}</Text>
        <details><summary>Raw</summary><pre>{JSON.stringify(row, null, 2)}</pre></details></article>
    )) : <Empty text={`No ${tab} evidence has been recorded.`}/>}</section></>
}

export function ReportsPage({rows, actionResult, onGenerate}: {rows: AnyRecord[]; actionResult?: AnyRecord | null; onGenerate: () => Promise<void>}) {
  return <><div className="page-head"><div><Text sx={{color:'fg.muted'}}>LOCAL EXPORT</Text><h1>Reports</h1></div><Button variant="primary" onClick={onGenerate}>Generate local report</Button></div>
    <section className="panel"><Heading as="h2">Preview</Heading><Text as="p" sx={{color:'fg.muted'}}>Reports stay on this server and are never uploaded automatically.</Text>
      <pre>{JSON.stringify(actionResult || rows[0] || {}, null, 2)}</pre></section></>
}

export function RecoveryPage({rows, actionResult, onStage}: {rows: AnyRecord[]; actionResult?: AnyRecord | null; onStage: () => Promise<void>}) {
  const [confirming, setConfirming] = useState(false)
  const [confirmation, setConfirmation] = useState('')
  return <><div className="page-head"><div><Text sx={{color:'fg.muted'}}>CONSERVATIVE RECOVERY</Text><h1>Recovery</h1></div><Button variant="danger" onClick={() => setConfirming(true)}>Stage restore</Button></div>
    <section className="panel"><Heading as="h2">Restore preview</Heading><pre>{JSON.stringify(actionResult || rows[0] || {}, null, 2)}</pre>
      {confirming && <Box sx={{display:'grid', gap:2, maxWidth: 420}}><Flash variant="warning">Staging copies files for the next clean restart. It does not modify databases or auto-rollback.</Flash><FormControl><FormControl.Label>Type STAGE to confirm</FormControl.Label><TextInput value={confirmation} onChange={e => setConfirmation(e.target.value)}/></FormControl><Button variant="danger" disabled={confirmation !== 'STAGE'} onClick={onStage}>Confirm stage</Button></Box>}
    </section></>
}

function Settings({status, onForget}: {status: AnyRecord; onForget: () => void}) {
  const web = status.web || {}
  const config = status.config || {}
  return <><div className="page-head"><div><Text sx={{color:'fg.muted'}}>LOCAL CONTROL PLANE</Text><h1>Settings</h1></div></div>
    <section className="panel"><Heading as="h2">Web</Heading>
      <Text as="p">Listening at <strong>{web.address || 'unknown'}</strong> (bind {web.bind}:{web.port}).</Text>
      <Text as="p" sx={{color:'fg.muted'}}>Create tokens from console: <code>plugtrace web token create local-admin admin</code></Text>
      <Text as="p" sx={{color:'fg.muted'}}>Live updates poll every 8s. SSE is API-only (EventSource cannot send Bearer tokens).</Text>
    </section>
    <section className="panel"><Heading as="h2">Effective config</Heading>
      <Text as="p" sx={{color:'fg.muted'}}>Edit plugins/PlugTrace/config.yml then run /plugtrace reload.</Text>
      <pre>{JSON.stringify(config, null, 2)}</pre>
    </section>
    <section className="panel"><Heading as="h2">Capability declaration</Heading><pre>{JSON.stringify(status.platform, null, 2)}</pre>
      <Heading as="h2">Privacy</Heading><Text as="p">Config values are excluded by default ({String(config['privacy.mode'] || 'hash-only')}). Nothing is uploaded automatically.</Text>
      <Button variant="danger" onClick={onForget}>Forget browser token</Button></section></>
}

function Empty({text}: {text: string}) {return <div className="empty"><Text sx={{color:'fg.muted'}}>{text}</Text></div>}

const rootElement = document.getElementById('root')
if (rootElement) createRoot(rootElement).render(<React.StrictMode><App/></React.StrictMode>)
