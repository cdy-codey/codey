/**
 * 发布脚本 — 构建、预览、确认后发布到 npm
 *
 * 功能：
 *   - 自动切换 registry 到 npm 官方源，发布完成后切回原 registry
 *   - 未登录时自动引导 npm login
 *   - 干燥模式仅构建 + 预览，不实际发布
 *
 * 用法：
 *   node scripts/publish.js           # 完整发布流程
 *   node scripts/publish.js --dry-run  # 仅构建 + 预览，不实际发布
 *   npm run publish:dry                # 同上（快捷命令）
 *   npm run publish:run                # 完整发布流程（快捷命令）
 */

import { execSync } from 'node:child_process'
import { createInterface } from 'node:readline'
import { existsSync, readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const root = resolve(__dirname, '..')
const isDryRun = process.argv.includes('--dry-run')

// npm 官方源地址
const NPM_REGISTRY = 'https://registry.npmjs.org/'

// ============== 工具函数 ==============

/** 在项目根目录执行命令，输出实时日志 */
function run(cmd, label) {
  console.log(`\n[${label}] $ ${cmd}`)
  execSync(cmd, { cwd: root, stdio: 'inherit' })
}

/** 静默执行并返回 stdout */
function runSilent(cmd) {
  return execSync(cmd, { cwd: root, encoding: 'utf-8' }).trim()
}

/** 终端交互确认 */
function ask(question) {
  const rl = createInterface({ input: process.stdin, output: process.stdout })
  return new Promise((resolve) => {
    rl.question(question, (answer) => {
      rl.close()
      resolve(answer.trim().toLowerCase())
    })
  })
}

// ============== Registry 切换 ==============

/** 获取当前 npm registry */
function getCurrentRegistry() {
  return runSilent('npm config get registry')
}

/** 设置 npm registry */
function setRegistry(url) {
  runSilent(`npm config set registry ${url}`)
}

/**
 * 切换 registry 到官方源，返回原 registry 用于恢复
 * 如果当前已是官方源，返回 null（不需要恢复）
 */
function switchToNpmRegistry() {
  const current = getCurrentRegistry()
  console.log(`当前 registry: ${current}`)

  if (current === NPM_REGISTRY || current.replace(/\/$/, '') === NPM_REGISTRY.replace(/\/$/, '')) {
    console.log('已在使用 npm 官方源，无需切换。')
    return null
  }

  console.log(`切换到 npm 官方源: ${NPM_REGISTRY}`)
  setRegistry(NPM_REGISTRY)
  return current // 返回原 registry 用于恢复
}

/** 恢复 registry 到原来的地址 */
function restoreRegistry(originalRegistry) {
  if (originalRegistry) {
    console.log(`\n恢复 registry: ${originalRegistry}`)
    setRegistry(originalRegistry)
  }
}

// ============== 登录检查 ==============

/** 检查是否已登录 npm，未登录则引导登录 */
function ensureLoggedIn() {
  try {
    const whoami = runSilent('npm whoami')
    console.log(`当前 npm 登录用户: ${whoami}`)
    return true
  } catch {
    console.log('\n⚠️  未检测到 npm 登录状态，请先登录：')
    run('npm login', '登录')
    return true
  }
}

// ============== 主流程 ==============

async function main() {
  // 使用 readFileSync 读取 package.json（兼容 ESM）
  const pkg = JSON.parse(readFileSync(resolve(root, 'package.json'), 'utf-8'))

  console.log('╔══════════════════════════════════════════╗')
  console.log(`║  📦 ${pkg.name}@${pkg.version} 发布流程`)
  console.log('╚══════════════════════════════════════════╝')

  // 干燥模式不需要切换 registry
  if (isDryRun) {
    console.log('\n⚠️  干燥模式，跳过 registry 切换。')
  }

  // 1. 构建
  console.log('\n▶ 步骤 1/5：构建 dist/')
  if (!existsSync(resolve(root, 'node_modules'))) {
    console.log('未检测到 node_modules，正在安装依赖...')
    run('npm install', '安装依赖')
  }
  run('npm run build', '构建')

  // 2. 预览打包内容
  console.log('\n▶ 步骤 2/5：预览将发布的文件列表')
  try {
    run('npm pack --dry-run', '预览')
  } catch {
    console.error('预览失败，请检查构建产物是否正常。')
    process.exit(1)
  }

  // 3. 干燥模式到此为止
  if (isDryRun) {
    console.log('\n✅ 干燥模式完成，未实际发布。')
    console.log('确认无误后执行：npm run publish:run')
    return
  }

  // 4. 确认发布
  console.log('\n▶ 步骤 3/5：确认发布')
  const answer = await ask(`确认将 ${pkg.name}@${pkg.version} 发布到 npm？(y/n): `)
  if (answer !== 'y' && answer !== 'yes') {
    console.log('已取消发布。')
    return
  }

  // 5. 切换 registry + 登录检查 + 发布
  console.log('\n▶ 步骤 4/5：切换 Registry 并登录')
  const originalRegistry = switchToNpmRegistry()
  ensureLoggedIn()

  console.log('\n▶ 步骤 5/5：发布到 npm')
  try {
    run('npm publish --access public', '发布')
    console.log(`\n🎉 发布成功！${pkg.name}@${pkg.version} 已推送到 npm`)
    console.log(`第三方可通过以下方式安装：`)
    console.log(`  npm install ${pkg.name}`)
  } finally {
    // 无论成功失败，都要恢复 registry
    restoreRegistry(originalRegistry)
  }
}

main().catch((err) => {
  console.error('\n❌ 发布失败：', err.message)
  process.exit(1)
})
