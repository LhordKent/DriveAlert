// Test-only declarations for the Node built-ins used by dispatch.test.ts.
// The worker targets Cloudflare Workers types, so @types/node is intentionally not installed.
declare module 'node:fs' {
  export function readFileSync(path: URL | string, encoding: 'utf8'): string
}

declare module 'node:sqlite' {
  interface StatementSync {
    get(...args: unknown[]): unknown
    all(...args: unknown[]): unknown[]
    run(...args: unknown[]): unknown
  }
  export class DatabaseSync {
    constructor(path: string)
    exec(sql: string): void
    prepare(sql: string): StatementSync
  }
}
