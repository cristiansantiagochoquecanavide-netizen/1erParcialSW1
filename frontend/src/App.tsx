import { FormEvent, useEffect, useState } from 'react'

type Role = 'PROPIETARIO' | 'EDITOR' | 'LECTOR'
type Project = { id: string; name: string; description: string | null; role: Role; createdAt: string }
type Member = { id: string; userId: string; name: string; email: string; role: Role }
type Auth = { token: string; name: string; email: string }
const base = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

async function api<T>(path: string, token?: string, options: RequestInit = {}): Promise<T> {
  const response = await fetch(`${base}${path}`, { ...options, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...options.headers } })
  if (!response.ok) { const body = await response.json().catch(() => ({})); throw new Error(body.message ?? 'No se pudo completar la operación.') }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>
}

export default function App() {
  const [auth, setAuth] = useState<Auth | null>(() => { const raw = localStorage.getItem('case.auth'); return raw ? JSON.parse(raw) : null })
  const [registering, setRegistering] = useState(false), [projects, setProjects] = useState<Project[]>([]), [selected, setSelected] = useState<Project | null>(null), [message, setMessage] = useState<string | null>(null)
  const error = (value: unknown) => setMessage(value instanceof Error ? value.message : 'Error inesperado.')
  const load = () => auth && api<Project[]>('/api/projects', auth.token).then(setProjects).catch(error)
  useEffect(() => { load() }, [auth]) // eslint-disable-line react-hooks/exhaustive-deps
  if (!auth) return <AuthScreen registering={registering} setRegistering={setRegistering} onAuth={value => { localStorage.setItem('case.auth', JSON.stringify(value)); setAuth(value) }} onError={error} message={message} />
  if (selected) return <Workspace project={selected} auth={auth} onBack={() => { setSelected(null); load() }} onError={error} />
  const token = auth.token
  async function create(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = new FormData(event.currentTarget); try { await api('/api/projects', token, { method: 'POST', body: JSON.stringify({ name: form.get('name'), description: form.get('description') }) }); event.currentTarget.reset(); load() } catch (e) { error(e) } }
  async function rename(p: Project) { const name = prompt('Nombre del proyecto:', p.name); if (!name) return; try { await api(`/api/projects/${p.id}`, token, { method: 'PUT', body: JSON.stringify({ name, description: p.description }) }); load() } catch (e) { error(e) } }
  async function remove(p: Project) { if (!confirm(`¿Eliminar “${p.name}”?`)) return; try { await api(`/api/projects/${p.id}`, token, { method: 'DELETE' }); load() } catch (e) { error(e) } }
  return <main className="app"><header><div><h1>Proyecto CASE</h1><p>{auth.name} · {auth.email}</p></div><button className="secondary" onClick={() => { localStorage.removeItem('case.auth'); setAuth(null) }}>Salir</button></header>{message && <p className="error">{message}</p>}
    <section className="card"><h2>Nuevo proyecto de modelado</h2><form className="inline" onSubmit={create}><input name="name" placeholder="Nombre" required maxLength={120} /><input name="description" placeholder="Descripción opcional" maxLength={500} /><button>Crear</button></form></section>
    <section className="card"><h2>Mis proyectos</h2>{projects.length === 0 ? <p>Aún no participas en ningún proyecto.</p> : <div className="projects">{projects.map(p => <article key={p.id}><div><strong>{p.name}</strong><span className="role">{p.role}</span><p>{p.description || 'Sin descripción'}</p></div><div className="actions"><button onClick={() => setSelected(p)}>Abrir</button>{p.role !== 'LECTOR' && <button className="secondary" onClick={() => rename(p)}>Editar</button>}{p.role === 'PROPIETARIO' && <button className="danger" onClick={() => remove(p)}>Eliminar</button>}</div></article>)}</div>}</section></main>
}

function AuthScreen({ registering, setRegistering, onAuth, onError, message }: { registering: boolean; setRegistering: (v: boolean) => void; onAuth: (v: Auth) => void; onError: (e: unknown) => void; message: string | null }) {
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const f = new FormData(event.currentTarget); try { onAuth(await api<Auth>(registering ? '/api/auth/register' : '/api/auth/login', undefined, { method: 'POST', body: JSON.stringify({ email: f.get('email'), password: f.get('password'), ...(registering ? { name: f.get('name') } : {}) }) })) } catch (e) { onError(e) } }
  return <main className="auth"><section className="card"><h1>Proyecto CASE</h1><p>{registering ? 'Crea tu cuenta para iniciar un espacio de modelado.' : 'Ingresa a tus proyectos de modelado.'}</p>{message && <p className="error">{message}</p>}<form onSubmit={submit}>{registering && <input name="name" placeholder="Nombre" required maxLength={100} />}<input name="email" type="email" placeholder="Correo" required /><input name="password" type="password" placeholder="Contraseña (mínimo 8 caracteres)" required minLength={8} /><button>{registering ? 'Registrarme' : 'Iniciar sesión'}</button></form><button className="link" onClick={() => setRegistering(!registering)}>{registering ? 'Ya tengo una cuenta' : 'Crear una cuenta'}</button></section></main>
}

function Workspace({ project, auth, onBack, onError }: { project: Project; auth: Auth; onBack: () => void; onError: (e: unknown) => void }) {
  const [members, setMembers] = useState<Member[]>([])
  const loadMembers = () => api<Member[]>(`/api/projects/${project.id}/members`, auth.token).then(setMembers).catch(onError)
  useEffect(() => { loadMembers() }, []) // eslint-disable-line react-hooks/exhaustive-deps
  async function add(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const f = new FormData(event.currentTarget); try { await api(`/api/projects/${project.id}/members`, auth.token, { method: 'POST', body: JSON.stringify({ email: f.get('email'), role: f.get('role') }) }); event.currentTarget.reset(); loadMembers() } catch (e) { onError(e) } }
  async function role(member: Member, value: string) { try { await api(`/api/projects/${project.id}/members/${member.id}`, auth.token, { method: 'PUT', body: JSON.stringify({ role: value }) }); loadMembers() } catch (e) { onError(e) } }
  async function remove(member: Member) { if (!confirm(`¿Quitar a ${member.name}?`)) return; try { await api(`/api/projects/${project.id}/members/${member.id}`, auth.token, { method: 'DELETE' }); loadMembers() } catch (e) { onError(e) } }
  return <main className="app"><header><div><h1>{project.name}</h1><p>Workspace de modelado</p></div><button className="secondary" onClick={onBack}>Volver a proyectos</button></header><section className="workspace"><h2>Editor UML</h2><p>Este espacio queda reservado para el futuro editor de diagramas de clases.</p></section>
    <section className="card"><h2>Colaboradores</h2>{project.role === 'PROPIETARIO' && <form className="inline" onSubmit={add}><input name="email" type="email" required placeholder="Correo del colaborador" /><select name="role" defaultValue="EDITOR"><option value="EDITOR">EDITOR</option><option value="LECTOR">LECTOR</option></select><button>Agregar</button></form>}<div className="members">{members.map(m => <div key={m.id} className="member"><span><strong>{m.name}</strong> · {m.email}</span>{project.role === 'PROPIETARIO' && m.role !== 'PROPIETARIO' ? <span><select value={m.role} onChange={e => role(m, e.target.value)}><option value="EDITOR">EDITOR</option><option value="LECTOR">LECTOR</option></select><button className="danger" onClick={() => remove(m)}>Quitar</button></span> : <span className="role">{m.role}</span>}</div>)}</div></section></main>
}
