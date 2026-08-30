# ChatColor deprecation 警告清理任务书

GetMeHome 分支 feat/brigadier-26.2 上，CI/构建有 18 个 `ChatColor in org.bukkit has been deprecated` 警告。目标：**消除全部 ChatColor 类引用**，零行为变化。

**核心策略**：`org.bukkit.ChatColor.X` → legacy 码字符串（`"§x"`），config 颜色字符 → `"§" + ch`，`translateAlternateColorCodes('&', s)` → `s.replace('&', '§')`。**不改消息输出链路**（sendMessage(String) 保留，paper-api 26.2 未 deprecated，构建已验证）。

## 1. GetMeHome.java

- 删 `import org.bukkit.ChatColor;`
- L29-30 字段：`private ChatColor focusColor;` / `private ChatColor contentColor;` → `private String focusColor;` / `private String contentColor;`
- L54/L58 getter 返回类型 `ChatColor` → `String`
- L174：`ChatColor.translateAlternateColorCodes('&', getConfig().getString(...))` → `getConfig().getString(...).replace('&', '§')`
- L175-176：`ChatColor.getByChar(getConfig().getString(...))` → `"§" + getConfig().getString(...)`（注意 config 值如 "e" → "§e"；若值本身带 § 则重复——保持与原 getByChar 语义等价即可，getByChar 取首个字符，`"§" + ch` 用第一个字符）

## 2. MessageTool.java

- 删 `import org.bukkit.ChatColor;`
- `prefixed()`：`ChatColor focus = GetMeHome.getInstance().getFocusColor();` → `String focus = ...`；content 同理。后续拼接逻辑不变（String 拼接）
- `error()`：`ChatColor.RED` → `"§c"`

## 3. ListHomesCommand.java

- 删 `import org.bukkit.ChatColor;`
- L118-119：`ChatColor f = plugin.getFocusColor();` → `String f = ...`；`ChatColor c = ...` → `String c = ...`
- L126：`ChatColor.ITALIC.toString()` → `"§o"`
- L159：`ret.append(ChatColor.BOLD)` → `ret.append("§l")`
- L160：`ret.append(ChatColor.ITALIC)` → `ret.append("§o")`
- L161：`ret.append(ChatColor.RESET)` → `ret.append("§r")`

## 验收

1. `grep -rn "ChatColor" src/main/java` 零结果（TempUtils.java 是既有例外？检查——TempUtils 用的是 Adventure TextColor，不含 org.bukkit.ChatColor，但 grep 会命中类名，验收以 `org.bukkit.ChatColor` 引用清零为准）
2. 构建通过（若权限被拦报告即可）

完成后输出 git diff --stat。
