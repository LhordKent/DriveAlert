import type { Config } from 'tailwindcss'

export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#121011',
        raised: '#1B1819',
        control: '#242021',
        strong: '#2D2729',
        border: '#4B4144',
        'border-soft': '#352F31',
        primary: '#F5EFF0',
        secondary: '#C9BFC1',
        muted: '#9E9295',
        signal: '#EF3340',
        'signal-pressed': '#C91F2D',
        'signal-soft': '#4B2025',
        success: '#55D68B',
        'success-soft': '#163C2A',
        warning: '#F2BE4D',
        'warning-soft': '#443719',
        info: '#74B7FF',
        'info-soft': '#183650',
      },
      borderRadius: { control: '0.75rem', panel: '1rem' },
      boxShadow: { focus: '0 0 0 3px rgba(239, 51, 64, 0.28)' },
    },
  },
  plugins: [],
} satisfies Config
