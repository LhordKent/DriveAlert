import React from 'react'
import ReactDOM from 'react-dom/client'
import { AuthProvider } from './app/providers/AuthProvider'
import { PortalProvider } from './app/providers/PortalProvider'
import { AppRouter } from './app/router/AppRouter'
import './styles/index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(<React.StrictMode><AuthProvider><PortalProvider><AppRouter /></PortalProvider></AuthProvider></React.StrictMode>)
