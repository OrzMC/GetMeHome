# PR #7 Review 修复任务书

修复 GetMeHome 分支 feat/brigadier-26.2 上 review 发现的问题。逐项执行，**只改下面列出的内容**，不要动其他逻辑。

## M1. /setdefaulthome 丢失 Tab 补全（明确回归）

`src/main/java/com/simonorj/mc/getmehome/command/CommandRegistrar.java` 中 setdefaulthome 的 `home` 参数节点（约 L86-88）没有 `.suggests(...)`。
旧版 Bukkit onTabComplete 在 args.length==1 时建议**玩家自己的家名**。
修复：给该参数加 suggests，复用 `suggestFirstArg` 的家名部分（不依赖 `.other` 权限）。可抽一个 `suggestOwnHomes(CommandContext, SuggestionsBuilder)` 方法（只建议 sender 自己的家名），home/sethome/delhome/setdefaulthome 的第一参都复用它。

## M2. 控制台 /home <player> <home> 触发 ClassCastException

`HomeCommands.java` L83 `home((Player) sender, ...)` 和 L86 `setHome((Player) sender, ...)`：控制台 sender（非 Player）带 other 权限走 args>=2 分支后转型崩溃。
修复：在 `onCommand` 的 switch 里 `case "home"` 和 `case "sethome"` 分支前，若 `!(sender instanceof Player)` 则 `consoleCommand(sender, command); return true;`（保持 delhome/setdefaulthome 现有行为不变）。

## M4. word() 参数类型拒绝特殊字符家名

`CommandRegistrar.java` 所有 `StringArgumentType.word()` 改 `StringArgumentType.string()`（家名可能含 `:` `#` `,` 等；string 接受非空格字符，与原版 Bukkit args 语义一致）。涉及：home/sethome/delhome 的 target 与 home 参数、setdefaulthome 的 home 参数、listhomes 的 arg1/arg2、getmehome 的 action。

## M5. /listhomes -g 短旗标

`suggestListHomesSecondArg` 只认 `-global`，扩为也认 `-g`（`GLOBAL_SHORT_FLAG`）。把 `GLOBAL_SHORT_FLAG` 常量提到 CommandRegistrar 或复用 ListHomesCommand 的。

## L2. gradle.properties 支持版本声明

`plugin_support_paper_versions=26.1,26.2` 改为 `26.2`（api 已 26.2-only，Hangar 元数据一致）。

## H2. 明示 Paper-only

- `CLAUDE.md` 第一段 "Paper/Spigot home plugin" 改为 "Paper-only home plugin"（并注明 Paper 26.2+，命令经 LifecycleEvents.COMMANDS + Brigadier 注册，原因：Bukkit CommandMap 声明在 26.x 会产生无 executor 幽灵命令且补全被原生 Brigadier 注册抢占）。
- `plugin.yml` 顶部注释加一行：`# Paper 26.2+ only（Brigadier 命令注册，非 Spigot 兼容）`。

## H1 决策（不修代码，只加注释）

`.requires()` 导致无权限玩家看到 "Unknown or incomplete command" 而非明确拒绝——这是**有意的安全实践**（隐藏命令存在性，与 Paper 26.x 生态一致）。在 CommandRegistrar 类 Javadoc 里补一句说明。

## 完成后

1. 输出 git diff --stat
2. 运行构建：`JAVA_HOME=/Library/Java/JavaVirtualMachines/microsoft-25.jdk/Contents/Home ./gradlew shadowJar`（若权限被拦，报告即可，构建由编排方执行）
