# PR #7 第二轮 review 修复任务书

GetMeHome 分支 feat/brigadier-26.2，修复第二轮 review 发现的问题。逐项执行，只改列出的内容。

## #1（中）特殊字符家名的补全建议加引号

`CommandRegistrar.java` 的三处家名建议器（`suggestOwnHomes`、`suggestFirstArg` 内的家名部分、`suggestPlayerHome`）：当建议的家名含 `word()` 之外的特殊字符（正则 `[^0-9A-Za-z._-]` 命中）时，用双引号包裹建议文本（如 `"foo:bar"` → 建议 `"foo:bar"`），符合 Brigadier 补全规范，保证客户端选中后服务端 `string()` 参数能正确解析为单 token。正常家名（纯 `[0-9A-Za-z._-]`）不加引号，保持现状。

## #2（低）CLAUDE.md 同步 paper_api_version

`CLAUDE.md` 的「Key `gradle.properties`」行：`paper_api_version=26.1.2.build.74-stable` 改为 `26.2.build.119-stable`（与 gradle.properties 实际值一致）。

## #5（低）控制台防护前移

`HomeCommands.java` 的 `onCommand`：把 `case "home"`/`case "sethome"` 内的 `!(sender instanceof Player) → consoleCommand` 检查**前移到 switch 之前统一处理**：

```java
// 控制台只能 delhome（删指定玩家的家），其余命令需玩家身份
if (!(sender instanceof Player) && !command.equalsIgnoreCase("delhome")) {
    consoleCommand(sender, command);
    return true;
}
```

放 switch 前面。然后删除 case "home"/case "sethome" 内已有的重复检查（若第一轮修复是在 case 内加的）。`setdefaulthome` 的隐性转型风险一并消除；`delhome` 控制台行为保持不变（控制台可删别人家）。

## 完成后

1. 输出 git diff --stat
2. 确认编译（若 gradle 被权限拦，报告即可）
