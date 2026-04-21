// src/vite-env.d.ts

/// <reference types="vite/client" />

// 声明 CSS 模块，允许 import '*.css'
declare module "*.css" {
  const content: Record<string, string>;
  export default content;
}

// 如果项目中还导入了其他静态资源（如图片、svg等），也可以一并声明
declare module "*.svg" {
  const content: string;
  export default content;
}

declare module "*.png" {
  const content: string;
  export default content;
}
