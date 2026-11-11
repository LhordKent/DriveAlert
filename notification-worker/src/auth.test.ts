import { beforeAll, afterAll, describe, expect, it, vi } from 'vitest'
import { verifyFirebaseToken } from './auth'
import type { Env } from './types'

const project = 'verification-test'
const env = { FIREBASE_PROJECT_ID: project } as Env
let keys: CryptoKeyPair
function encode(value: Uint8Array) { return Buffer.from(value).toString('base64url') }
async function token(emailVerified?: unknown) {
  const now = Math.floor(Date.now() / 1000)
  const header = encode(new TextEncoder().encode(JSON.stringify({ alg: 'RS256', kid: 'test-key' })))
  const payload = encode(new TextEncoder().encode(JSON.stringify({ sub: 'user-1', aud: project,
    iss: `https://securetoken.google.com/${project}`, exp: now + 3600, iat: now, email_verified: emailVerified })))
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', keys.privateKey, new TextEncoder().encode(`${header}.${payload}`))
  return `Bearer ${header}.${payload}.${encode(new Uint8Array(signature))}`
}
beforeAll(async () => {
  keys = await crypto.subtle.generateKey({ name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['sign', 'verify'])
  const publicKey = await crypto.subtle.exportKey('jwk', keys.publicKey)
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(JSON.stringify({ keys: [{ ...publicKey, kid: 'test-key' }] }), {
    headers: { 'cache-control': 'max-age=3600' },
  })))
})
afterAll(() => vi.unstubAllGlobals())
describe('verified email backend gate', () => {
  it('accepts a valid signed token with verified email', async () => {
    await expect(verifyFirebaseToken(await token(true), env)).resolves.toEqual({ uid: 'user-1' })
  })
  it.each([false, undefined, 'true'])('rejects unverified or invalid verification claims (%s)', async claim => {
    await expect(verifyFirebaseToken(await token(claim), env)).rejects.toThrow('AUTH_EMAIL_UNVERIFIED')
  })
})
