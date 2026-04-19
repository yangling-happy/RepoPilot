/* eslint-env node */
/** @type {import("eslint").Linter.Config} */
module.exports = {
  root: true,
  ignorePatterns: ["dist", "build", "node_modules", "backend"],
  parser: "@typescript-eslint/parser",
  parserOptions: {
    ecmaVersion: "latest",
    sourceType: "module",
    project: [
      "./apps/web/tsconfig.eslint.json",
      "./apps/terminal/tsconfig.eslint.json",
    ],
  },
  plugins: ["@typescript-eslint"],
  extends: ["eslint:recommended", "plugin:@typescript-eslint/recommended"],
  rules: {
    "@typescript-eslint/no-unused-vars": [
      "warn",
      { argsIgnorePattern: "^_", varsIgnorePattern: "^_" },
    ],
  },
  overrides: [
    {
      files: ["apps/web/**/*.{ts,tsx}"],
      plugins: ["react", "react-hooks", "i18next"],
      extends: ["plugin:react/recommended", "plugin:react-hooks/recommended"],
      settings: { react: { version: "detect" } },
      rules: {
        "react/react-in-jsx-scope": "off",
        "i18next/no-literal-string": [
          "error",
          {
            mode: "jsx-text-only",
            "should-validate-template": true,
            markupOnly: true,
            ignoreAttribute: [
              "className",
              "class",
              "style",
              "key",
              "id",
              "type",
              "rel",
              "target",
              "role",
              "fill",
              "stroke",
              "d",
              "viewBox",
              "xmlns",
              "href",
              "to",
              "name",
              "htmlFor",
              "data-testid",
              "aria-hidden",
              "initial",
              "animate",
              "exit",
              "transition",
              "whileHover",
              "whileTap",
            ],
            ignoreCallee: [
              "t",
              "i18n.t",
              "require",
              "console.warn",
              "console.error",
              "console.info",
              "Error",
              "Intl",
            ],
            ignore: [
              "^[\u0000-\u007f]+$",
              "^/[a-zA-Z0-9/_-]+$",
              "^#[0-9a-fA-F]{3,8}$",
              "^rgba?\\(",
            ],
          },
        ],
      },
    },
    {
      files: ["apps/terminal/**/*.ts"],
      rules: {
        "@typescript-eslint/no-explicit-any": "warn",
      },
    },
  ],
};
