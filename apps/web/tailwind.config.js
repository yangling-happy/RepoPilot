/** @type {import('tailwindcss').Config} */
export default {
  darkMode: "class",
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      keyframes: {
        "hero-tilt": {
          "0%, 100%": {
            transform:
              "rotateX(56deg) rotateY(-14deg) rotateZ(-22deg) translateZ(0px)",
          },
          "50%": {
            transform:
              "rotateX(60deg) rotateY(10deg) rotateZ(-18deg) translateZ(20px)",
          },
        },
        "hero-glow": {
          "0%, 100%": { opacity: "0.35" },
          "50%": { opacity: "0.55" },
        },
        "home-rise": {
          "0%": {
            opacity: "0",
            transform: "translateY(22px) scale(0.97)",
          },
          "100%": {
            opacity: "1",
            transform: "translateY(0) scale(1)",
          },
        },
      },
      animation: {
        "hero-tilt": "hero-tilt 16s ease-in-out infinite",
        "hero-glow": "hero-glow 8s ease-in-out infinite",
        "home-rise": "home-rise 0.75s cubic-bezier(0.22, 1, 0.36, 1) forwards",
      },
    },
  },
  plugins: [],
};
