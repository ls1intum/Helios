/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: ['selector', '[class~="dark-mode-enabled"]'],           //dark mode configuration
  content: [
    "./src/**/*.{html,ts,css,scss,sass,less,styl}"
  ],
  theme: {
    extend: {},
  },
  plugins: [require('tailwindcss-primeui')],
}

