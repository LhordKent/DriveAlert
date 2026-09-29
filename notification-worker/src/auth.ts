import type { AuthenticatedUser, Env } from './types'

const encoder = new TextEncoder()
type FirebaseJsonWebKey = JsonWebKey & { kid?: string }
let firebaseKeys: { expiresAt: number; keys: FirebaseJsonWebKey[] } | undefined
let googleAccessToken: { expiresAt: number; token: string } | undefined

function base64UrlDecode(value: string): ArrayBuffer {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=')
  return Uint8Array.from(atob(normalized), (character) => character.charCodeAt(0)).buffer as ArrayBuffer
}

function base64UrlEncode(value: ArrayBuffer | Uint8Array): string {
  const bytes = value instanceof Uint8Array ? value : new Uint8Array(value)
  let binary = ''
  bytes.forEach((byte) => { binary += String.fromCharCode(byte) })
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '')
}

async function getFirebaseKeys(): Promise<FirebaseJsonWebKey[]> {
  const now = Date.now()
  if (firebaseKeys && firebaseKeys.expiresAt > now) return firebaseKeys.keys
  const response = await fetch('https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com')
  if (!response.ok) throw new Error('FIREBASE_KEYS_UNAVAILABLE')
  const value = await response.json<{ keys: FirebaseJsonWebKey[] }>()
  const maxAge = Number(response.headers.get('cache-control')?.match(/max-age=(\d+)/)?.[1] ?? 3600)
  firebaseKeys = { keys: value.keys, expiresAt: now + maxAge * 1000 }
  return value.keys
}

export async function verifyFirebaseToken(authorization: string | null, env: Env): Promise<AuthenticatedUser> {
  if (!authorization?.startsWith('Bearer ')) throw new Error('AUTH_REQUIRED')
  const token = authorization.slice(7)
  const parts = token.split('.')
  if (parts.length !== 3) throw new Error('AUTH_INVALID')
  const header = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[0]))) as { alg?: string; kid?: string }
  const payload = JSON.parse(new TextDecoder().decode(base64UrlDecode(parts[1]))) as Record<string, unknown>
  if (header.alg !== 'RS256' || !header.kid) throw new Error('AUTH_INVALID')
  const key = (await getFirebaseKeys()).find((candidate) => candidate.kid === header.kid)
  if (!key) throw new Error('AUTH_INVALID')
  const cryptoKey = await crypto.subtle.importKey('jwk', key, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify'])
  const valid = await crypto.subtle.verify(
    'RSASSA-PKCS1-v1_5',
    cryptoKey,
    base64UrlDecode(parts[2]),
    encoder.encode(`${parts[0]}.${parts[1]}`),
  )
  const nowSeconds = Math.floor(Date.now() / 1000)
  const uid = typeof payload.sub === 'string' ? payload.sub : ''
  if (!valid || !uid || payload.aud !== env.FIREBASE_PROJECT_ID ||
      payload.iss !== `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}` ||
      typeof payload.exp !== 'number' || payload.exp <= nowSeconds ||
      typeof payload.iat !== 'number' || payload.iat > nowSeconds + 60) throw new Error('AUTH_INVALID')
  return { uid }
}

function pemToPkcs8(pem: string): ArrayBuffer {
  const normalized = pem.replace(/\\n/g, '\n').replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, '')
  return Uint8Array.from(atob(normalized), (character) => character.charCodeAt(0)).buffer as ArrayBuffer
}

export async function serviceAccessToken(env: Env): Promise<string> {
  const now = Date.now()
  if (googleAccessToken && googleAccessToken.expiresAt > now + 300_000) return googleAccessToken.token
  const nowSeconds = Math.floor(now / 1000)
  const header = base64UrlEncode(encoder.encode(JSON.stringify({ alg: 'RS256', typ: 'JWT' })))
  const claim = base64UrlEncode(encoder.encode(JSON.stringify({
    iss: env.FIREBASE_CLIENT_EMAIL,
    scope: 'https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/firebase.messaging',
    aud: 'https://oauth2.googleapis.com/token',
    iat: nowSeconds,
    exp: nowSeconds + 3600,
  })))
  const key = await crypto.subtle.importKey(
    'pkcs8', pemToPkcs8(env.FIREBASE_PRIVATE_KEY), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign'],
  )
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, encoder.encode(`${header}.${claim}`))
  const assertion = `${header}.${claim}.${base64UrlEncode(signature)}`
  const response = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  })
  if (!response.ok) throw new Error('GOOGLE_OAUTH_FAILED')
  const value = await response.json<{ access_token: string; expires_in: number }>()
  googleAccessToken = { token: value.access_token, expiresAt: now + value.expires_in * 1000 }
  return value.access_token
}
