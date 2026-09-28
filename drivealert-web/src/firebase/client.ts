import { getApp, getApps, initializeApp } from 'firebase/app'
import { connectAuthEmulator, getAuth } from 'firebase/auth'
import { connectFirestoreEmulator, initializeFirestore, memoryLocalCache } from 'firebase/firestore'

const usingEmulators = import.meta.env.VITE_USE_FIREBASE_EMULATORS === 'true' || import.meta.env.MODE === 'test'
const projectId = import.meta.env.VITE_FIREBASE_PROJECT_ID || (usingEmulators ? 'drivealert-lhordkent' : '')
const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY || (usingEmulators ? 'demo-key' : ''),
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN || (usingEmulators ? 'drivealert-lhordkent.firebaseapp.com' : ''),
  projectId,
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET || (usingEmulators ? 'drivealert-lhordkent.firebasestorage.app' : ''),
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID || (usingEmulators ? 'demo-sender' : ''),
  appId: import.meta.env.VITE_FIREBASE_APP_ID || (usingEmulators ? 'demo-web-app' : ''),
}

const missingConfig = Object.entries(firebaseConfig).filter(([, value]) => !value).map(([key]) => key)
if (missingConfig.length > 0) {
  throw new Error(`Missing Firebase web configuration: ${missingConfig.join(', ')}. Copy .env.example to .env.local and provide the existing project's Web App values.`)
}

const app = getApps().length > 0 ? getApp() : initializeApp(firebaseConfig)

export const auth = getAuth(app)
export const firestore = initializeFirestore(app, { localCache: memoryLocalCache() })

if (usingEmulators) {
  connectAuthEmulator(auth, 'http://127.0.0.1:9099', { disableWarnings: true })
  connectFirestoreEmulator(firestore, '127.0.0.1', 8080)
}
