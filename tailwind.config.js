/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./application/src/main/resources/templates/**/*.html",
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: '#006875',
          light:   '#33a6b7',
          dark:    '#004b56',
          container: '#a5f0fc',
          50:  '#f0f9fb',
          100: '#e0f2f7',
          200: '#c1e5ef',
          300: '#a2d8e7',
          400: '#82cbdf',
          500: '#33a6b7',
          600: '#006875',
          700: '#004b56',
          900: '#001f24',
        },
        accent: {
          DEFAULT: '#7ab800',
          light:   '#9cd633',
          dark:    '#558100',
          container: '#ebf7cc',
          200: '#ebf7cc',
          600: '#7ab800',
          700: '#558100',
        },
        surface: {
          DEFAULT:        '#fcfdfd',
          dim:            '#f0f4f5',
          container:      '#e5e9ea',
          'container-low': '#f8f9fa',
        },
        secondary: {
          DEFAULT:   '#4a6266',
          container: '#cde7ec',
        },
        tertiary: {
          DEFAULT:   '#525e7d',
          container: '#d9e2ff',
        },
        error: {
          DEFAULT:   '#ba1a1a',
          container: '#ffdad6',
        },
        'on-primary':           '#ffffff',
        'on-surface':           '#191c1d',
        'on-surface-variant':   '#3f484a',
        'on-primary-container': '#001f24',
        'on-error-container':   '#410002',
        'outline':              '#6f797b',
        'outline-variant':      '#bfc8ca',
      },
      fontFamily: {
        sans:    ['Inter', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
        display: ['Outfit', 'system-ui', '-apple-system', 'BlinkMacSystemFont', 'Segoe UI', 'sans-serif'],
        mono:    ['JetBrains Mono', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
      borderRadius: {
        sm:   '6px',
        md:   '8px',
        lg:   '12px',
        pill: '99px',
      },
      boxShadow: {
        sm: '0 1px 3px rgba(0,0,0,0.07)',
        md: '0 2px 8px rgba(0,0,0,0.10)',
        lg: '0 4px 16px rgba(0,0,0,0.12)',
      },
    },
  },
  plugins: [],
}
