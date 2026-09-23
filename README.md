# Controller Radial Menu

> 作者：**Dcmwfg** · 许可证：MIT · 需要 Minecraft 1.20.1 (Forge) + Controlify

独立的手柄功能轮盘，用于 **Minecraft 1.20.1 + Forge**。
按住扳机 → 轮盘出现 → 右摇杆选功能 → **松开摇杆**执行、轮盘消失；按住时可以 **LB / RB 循环切换多套轮盘**。

轮盘的 UI、选中逻辑、动作执行全部由本 mod 自己实现；只把「读手柄」这件事交给 [Controlify](https://github.com/echoEllet/controlify)（Forge 1.20.1 版）。
格子编辑方式模仿 [MineMenu](https://github.com/GirafiStudios/MineMenu)，图形化编辑界面挂在 [neo_mod_menu](https://github.com/1foxy2/neo_mod_menu)（Mod Menu 的 Forge 移植版）里。

## 特性
3. **LB / RB 循环切换多套预设轮盘**：默认 3 套，最多 12 套，编辑界面里随时增删；每套最多 12 格（理论最大 144 个功能位）。
4. 每格可绑定一个**功能**（任意 `KeyMapping`：原版的、以及整合包里各个模组的），**该功能本身没绑键盘键位也能用**；触发方式可选 **一下 / 定时长按（0.5·1·2·3·5 秒）/ 切换保持**，也支持把格子做成**跳转轮盘**（选中后直接换到另一套轮盘）。

1. **按住扳机才出现**，松开立刻消失（不是开关式）。
2. **右摇杆选择**方向，推到哪里哪格高亮，**高亮会一直保持**（摇杆回中也不会自己取消）。
5. 每格也可执行**聊天命令**（支持 `@p`、WorldEdit 的 `//` 写法，也可只复制到剪贴板）。
6. 每格可自定义**显示文字**（自适应缩放）和**物品图标**（内置可搜索的物品选择器）。
7. 两个选择器都带**搜索栏**，按名称 / id / 键位 / 分组过滤（原版风格的普通搜索，接得到中文输入法就能搜中文；用拼音等第三方搜索请用输入法本身）。
7. **图形化编辑**：**不用装任何操作菜单类 mod** —— 游戏内 选项 → 控制 → 「**轮盘配置**」按钮直接打开（我们往原版控制界面里加了一个按钮）；装了 neo_mod_menu 的话，模组列表里的「配置」按钮也能开。
8. **轮盘内直接编辑**：轮盘开着时**长按 Y/△**（约 0.4 秒）→ 当场打开**当前这套轮盘的设置页**，改完关掉就回到还开着的轮盘；底下一行灰字会提示你按哪个键。
9. 格子内容带足内边距（图标 12px + 小字幕），不会把格子挤满。
10. **任何界面都能用手柄鼠标**：Controlify 自带的虚拟鼠标（左摇杆光标 / A 左键 / X 右键 / 右摇杆滚动 / 方向键跳控件）+ 本 mod 补的**拖拽**（RS 按下按住左键），模组界面里拖滑块、拖物品都能用。

## 依赖

| 组件 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.x（开发用 47.4.10，实测 47.4.22 可加载） |
| Controlify（Forge 1.20.1 forgified） | 2.1.9 及以上 |
| neo_mod_menu（可选） | 任意版本，装了就有图形编辑入口 |
| Java | 17 |

## 安装

1. 把 `controlify-forgified-2.1.9-mc1.20.1-forge.jar` 放进 `mods/`。
2. 把本 mod 的 `controllerradial-forge-1.20.1+mc1.20.1.jar` 放进 `mods/`。
3. 想用图形化编辑就再装一个 `neo_mod_menu`（不装也能用，Forge 自带模组列表的「配置」按钮同样能开）。
4. 启动游戏（客户端 mod，服务端不需要装）。

## 默认操作

1. 按住 **方向键右（D-Pad 右）** → 轮盘出现。
2. 推 **右摇杆** 选方向，对应格子会高亮（带音效和震动），**推一下就可以松掉，高亮不会消失**。
3. **确认 = 松开摇杆**：推一下选中、把摇杆放回中间就执行，轮盘同时关闭。
4. 没碰摇杆就把扳机松掉 → 什么都不触发（等于取消）。
5. 按住扳机时按 **LB / RB** → 切换上一套 / 下一套轮盘（顶部显示轮盘名和页码点）；换页时高亮会清掉。
6. 按 **B**（或 ESC）→ 取消并关闭。
7. **长按 Y/△（约 0.4 秒）** → 直接打开当前轮盘的设置页（轮盘底部会提示“长按 [Y] 编辑这套轮盘”）；完成/保存时会回到还开着的轮盘。（这个键可在 Controlify 绑定里改，名字叫「轮盘：打开这套轮盘的设置」；组合键做不到——Controlify 一个绑定只能对应一个输入。）

默认键位写在 `assets/controlify/controllers/default_bind/default.json`，只对**还没改过绑定**的存档生效。
改键位：游戏内 选项 → 控制 → Controlify 的手柄绑定 → 分类 **手柄轮盘菜单**（`wheel_open`、`wheel_axis_*`、`wheel_prev`、`wheel_next`）。

> ⚠️ 默认用 **D-Pad 右**而不是扳机，就是为了避开「使用」——按住开轮盘不会再顺势挥一剑。另一个注意点：Controlify 自带的轮盘（Radial Menu）默认也在 **D-Pad 右**，两个碰一起时**我们抢先打开**（我们的钩子跑在它处理游戏内按键之前）；想两个都能用，就把其中一个改绑到别的键（启动时若检测到同键，日志里会有一行含 `are both bound to` 的提醒）。

## 默认三套轮盘

1. **常用**（8 格）：聊天、玩家列表、进度、社交、旁观高亮、切换视角、切换副手、选取方块。
2. **快捷栏**（9 格）：栏 1 ~ 栏 9。
3. **命令**（8 格）：生存、创造、旁观、白天、夜晚、晴天、雨天、清背包。

## 图形化编辑

模组列表（装了 neo_mod_menu 的话就是那个更好看的列表）→ 找到 **Controller Radial Menu** → 点 **配置**。

1. 顶部是轮盘标签页（默认 3 个），点哪个就编辑哪套；轮盘多了会自动换行。
2. 中间是格子列表，每行显示「序号. 文字」和它的动作（`按键 跳跃 [空格]` / `命令 /xxx`）。**点任意一行进入编辑**。
3. 底部按钮：「添加功能格」「添加轮盘」（默认 3、最多 12 套）「**重命名轮盘**」（改当前轮盘的名字，回车或「保存」生效，留空则回退为「轮盘 N」）「删除当前轮盘」「完成」（退出时自动写盘）。

编辑一个格子：

1. **显示文字**：格子上的字，会自动缩放；留空则显示图标，图标也留空则显示绑的键名。
2. **动作类型**：`功能` / `聊天命令` / `跳转轮盘` 三选一。
3. 选 `功能` → 点「选择功能…」打开**功能选择器**：上方是**搜索框**（框内我们自己画了灰字提示，不会看不到），列表先给一节**常用功能**（跳跃/潜行/疾跑/背包/丢弃/切换副手/切换视角/聊天/玩家列表/使用/攻击/选取方块），后面是**全部功能**。每行是**功能名 + [绑定键位]**（如「跳跃 [空格]」），没绑键位的显示 `[未绑定]`——这也能选，一样能触发。后面灰色小字是原始 id；搜索按普通子串匹配（功能名、原始 id、键位、分组名），列表按分组 + 名称排序。下面的「触发方式」按钮**点一下循环切换**：**一下** → **长按 0.5 / 1 / 2 / 3 / 5 秒** → **切换（保持按住）** → 回到一下，按钮下方有灰字说明。【一下】= 选中时模拟按一下键；【长按 N 秒】= 选中时把键按住 N 秒再松开（适合蓄力、持续挖掘、长按类技能）；【切换】= 选中时按下不放，再选一次才松开（适合潜行/疾跑/持续攻击/开镜）。
4. 选 `聊天命令` → 填命令（`/` 可有可无，`@p` 会替换成你的名字，`//wand` 这种 WorldEdit 写法保留）。下面的「只复制到剪贴板」开关：开 = 只把这条命令复制到剪贴板，不执行。
5. 「选择图标…」→ 打开**物品选择器**（同样带搜索栏，按物品名 / id 搜索，顶部有「不要图标」）。
6. **保存** / **删除这一格** / **取消**。

## 通用手柄鼠标（任何界面）

“让手柄能操控模组界面”这件事，**Controlify 本身就已经做了**，不用逐个模组适配。它的“虚拟鼠标”分类里默认键位是：

| 功能 | 默认键 |
| --- | --- |
| 移动光标 | 左摇杆 |
| 左键 | A |
| 右键 | X |
| 滚动 / 缩放 | 右摇杆上下 |
| 光标跳到下一个控件 | 方向键（snap） |
| 上一页 / 下一页 | LB / RB |
| PageUp / PageDown | 左 / 右扳机 |
| Shift / Shift+点击 | LS 按下 / Y |
| 开关虚拟鼠标 | 返回键 |

本 mod 只补了它没有的一项：**拖拽**。

1. `界面：拖拽（按住左键）` 默认绑 **右摇杆按下（RS）**：按住时保持左键按下并随光标拖动。
2. 用途：模组界面里的拖拽（拖滑块、拖物品之类）。任何界面都生效。
3. 它通过调用界面的 `mouseClicked / mouseDragged / mouseReleased` 实现，所以不依赖 MC 内部的鼠标状态。

> mod 会为每个见过的界面记一行日志（`screen xxx -> Controlify virtual mouse: ...`）；若某个界面里手柄鼠标就是不动，把这行发我。

## 配置文件

`config/controllerradial.json`，每次启动都会按当前内容重写一遍（所以图形界面改完存盘，文件也会同步更新）：

```json
{
  "presets": [
    {
      "name": "常用",
      "slots": [
        {
          "text": "聊天",
          "type": "key",
          "key": "key.chat",
          "toggle": false,
          "command": "",
          "clipboard": false,
          "icon": "minecraft:paper"
        }
      ]
    },
    { "name": "快捷栏", "slots": [] },
    { "name": "命令", "slots": [] }
  ],
  "activationThreshold": 0.5,
  "haptics": true,
  "focusTimeoutTicks": 10
}
```

字段说明：

1. `presets` 就是各个轮盘（默认 3 套，最多 12 套）；`name` 显示在轮盘顶部。
2. 每套 `slots` 从**正上方**开始**顺时针**排列，最多 12 格。
3. `text` 格子上的文字（自动缩放，可用 `\n`／JSON 里 `\\n` 换行）；留空看 `icon`，再留空看键名。
4. `type` = `key` 或 `command`；留空会按填了哪个字段自动判断。
5. `key` + `toggle` / `hold`：功能名 + 触发方式（一下 / 长按 N 秒 / 切换保持）。
6. `command` + `clipboard`：命令 + 是否只复制。
7. `icon`：物品 id，画在格子里。
8. `activationThreshold` 摇杆推多远算选中（0.15–0.95）。
9. `focusTimeoutTicks` 已废弃（高亮现在一直保持，不再自动清除），保留字段只是为了不破坏旧配置。
10. `haptics` 选中/换页时是否震动。

配置写坏也不会崩，只会打日志并用默认值。

### 怎么找模组的按键名

1. 图形编辑里的**功能选择器**直接搜就行（推荐），空搜索时先看「常用功能」那一节。
2. 或者看游戏目录 `options.txt` 里 `key_` 开头的行，冒号左边那串就是名字。
3. 手写错了不会崩，日志里会警告 `No key mapping named '...'`。

## 和 MineMenu 的异同

相同的部分：

1. 格子 = `文字 + 图标 + 一个动作`，动作分**功能**（绑定到某个游戏功能，背后就是一个键位）和**聊天命令**。
2. 按键可**切换模式**；命令支持 `@p`、`//` WorldEdit 写法、只复制到剪贴板。
3. 图标用**可搜索的物品选择器**来选。
4. 选中后**松手才算确定**。

不同的部分：

1. MineMenu 用鼠标角度选，这里用**右摇杆**。
2. MineMenu 的 `CATEGORY`（选中后跳到另一套轮盘）**已做**：动作类型选 `跳转轮盘`，再点一下「目标轮盘」循环选择目标；选中后**轮盘不关闭**，直接换到目标轮盘（和 MineMenu 一致），要退出就松开扳机。
3. MineMenu 的 `ITEM_USE`（它自己的服务端发包使用物品）没做——那是需要服务端装 mod 的协议；等价需求直接用原版「使用」功能就行。

## 从源码构建

```bash
# 需要 JDK 17
gradlew.bat build          # 产出 jar
gradlew.bat smokeTest      # 无头自检（不用开游戏、不用手柄）
```

产物：`build/libs/controllerradial-forge-1.0.0+mc1.20.1.jar`（还有个 `-sources.jar`）

构建前需要把 **Controlify（Forge 1.20.1 社区回移版）** 的 jar 放进 `libs/`（它只是编译期依赖，**不会被打进产物**，也不随仓库分发）：

1. 从 [echoEllet/controlify releases](https://github.com/EchoEllet/Controlify/releases) 下载 `controlify-forgified-2.1.9-mc1.20.1-forge.jar`
2. 放进 `ControllerRadial/libs/`（文件名保持 
`controlify-forgified-2.1.9-mc1.20.1-forge.jar`，或者改 `build.gradle` 里 `compileOnly files(...)` 那一行）
3. 运行时由玩家自己在 `mods/` 里装 Controlify

编译时对着 `libs/controlify-forgified-2.1.9-mc1.20.1-forge.jar` 编译（`compileOnly`），运行时由玩家自己安装 Controlify。

> 构建环境说明：本机 `maven.neoforged.net` 不可达（TLS 被重置），所以这里用的是 **Gradle 8.8 + ForgeGradle 6**，而不是 Controlify 仓库用的 ModDevGradle（后者必须访问 neoforged maven）。首次构建会下载 Forge userdev 并反编译 Minecraft，约 3 分钟。

## 自检（`gradlew smokeTest`）

`smoketest/SmokeTest.java` 是无需启动游戏、无需手柄的检查，当前 **324 项全过**：

1. Controlify 的 `ServiceLoader` 能发现本 mod 的 entrypoint（`META-INF/services` 接线正确）。
2. 7 个绑定的默认键位：LT 开轮盘、**右摇杆**四方向、LB/RB 换页。
3. `en_us` / `zh_cn` 语言键齐全（含图形编辑界面的全部文案）。
4. 默认三套轮盘的名称、格子数、摘要文案，且每格都有文字和动作；轮盘可增删（上限 12、至少保留 1 套）、多套配置不会被裁掉。
5. 旧版单轮盘配置能迁移成一套；`slots` 字段被清空、阈值被夹紧。
6. 编辑辅助：`preset()` 环绕、添加/删除格子、12 格上限。
7. 格子动作模型：动作类型推断（功能/命令/跳转轮盘）、跳转目标夹紧、`resolveCommand` 的 `@p`／`//`／前导斜杠处理。
8. 按键显示名：`keyDisplayName`／`keyGlyph`／`keyLabel`／`renderLabel` 在没有客户端时也能安全回退到原始 id。
8. 文字自适应缩放的边界情况。
9. 摇杆方向 → 格子索引的映射（4 / 8 / 9 / 12 格，含阈值边界与回中）。
10. 换页的环绕与无轮盘时的兜底。
11. 确认时机：`confirmsOnRelease` 只在「摇杆推过一下、且当前有高亮、摇杆已回中」时返回 true。
11. 打出来的 jar 里该有的文件都在，且没有把 Controlify 打进去。

注意：Controlify 发布的 jar 是 SRG 名字，而开发环境是 official 名字，所以自检只做 entrypoint 发现与资源/纯函数校验，不执行 Controlify 的类体（正式游戏里两者都是 SRG，一致）。

## 测试状态

1. 已通过：编译、打包、`smokeTest` 324 项、以及对 Controlify 2.1.9 全部 API 的编译期校验。
3. 轮盘布局由 `WheelLayout` 统一计算：无论多少格、屏幕多小，环的外沿都不会进入顶部（46px）/ 底部（50px）的文字区；放不下时先缩半径、再缩格子（最小 9px），文字自适应缩放兵底。
2. 已修复：1.1.0 在构造函数里读了已被迁移清空的 `WheelConfig.slots`，导致启动 NPE（`Mod loading error`）。此后配置加载整体 try/catch，坏配置不再阻止启动。
3. **未做**：实机手柄测试（这台机器接不了手柄）。手感、震动强度、以及默认键位（D-Pad 右）是否合你的习惯需要你进游戏确认。
4. 1.5.1 起轮盘的绘制/输入处理都包了 try/catch：mod 内部出错只会记日志 + 关掉轮盘，不会把游戏拖坑；日志里有 `[ControllerRadial] wheel opened` / `running cell N of wheel M` 方便定位。卡死时可用 `dump-mc-threads.ps1`（在项目目录）抓线程转储。

## 许可与致谢

1. 本模组使用 **MIT 许可证**，由 **Dcmwfg** 维护，见 [LICENSE](LICENSE)。
2. 手柄输入层用的是 **[Controlify](https://github.com/echoEllet/controlify)**（上游 [isXander/controlify](https://github.com/isXander/controlify)，Forge 1.20.1 版为非官方回移）——本模组**不包含也不重新分发**它的任何代码或二进制，只在编译时和运行时依赖它。
3. 轮盘格的交互与绑定模型参考了 **[MineMenu](https://github.com/GirafiStudios/MineMenu)**（同为独立实现，未拷代码）。
4. 图形化配置入口的发现方式兼容 **[neo_mod_menu](https://github.com/1foxy2/neo_mod_menu)**（用 Forge 自带的扩展点，不属于依赖）。

## 实现要点

1. `ControlifyIntegration` 通过 `META-INF/services/dev.isxander.controlify.api.entrypoint.ControlifyEntrypoint` 被 Controlify 的 `ServiceLoader` 发现，因此 Controlify 不存在时这个类根本不会被加载。
2. 图形编辑入口用的是 **Forge 自己的 `ConfigScreenHandler.ConfigScreenFactory` 扩展点**——Forge 模组列表和 neo_mod_menu 都读它，所以不用依赖任何一个。
3. 注册 7 个绑定：`wheel_open`（游戏内 + 轮盘内生效）、4 个方向轴、`wheel_prev` / `wheel_next`（只在轮盘内生效，用自定义 `BindContext` 限定）。
4. `WheelManager` 监听 `ControlifyEvents.ACTIVE_CONTROLLER_TICKED`，在游戏内检测到 `wheel_open` 按住就开屏，并记住上次用的那套轮盘。
5. `WheelScreen` 实现 `ScreenControllerEventListener` + `ScreenProcessorProvider`，用 `VirtualMouseBehaviour.DISABLED` 关掉虚拟鼠标，自己读摇杆角度算格子；格子是环形排布的圆格 + 辐条，文字用 `TextFit` 算缩放比。
6. 用 `Screen` 承载而不是 HUD 覆盖层：开着屏幕时 Controlify 会停掉移动/视角/游戏内按键（`ControllerPlayerMovement` 与 `canProcessLookInput()` 都有 `minecraft.screen != null` 判断），所以按住扳机时人物不会乱跑，右摇杆也能安全地拿来选功能。
7. 触发功能不靠“按键名查找”：`clickCount` 是 private、公开的 `KeyMapping.click(key)` 又是按**键位**查找，所以**没绑键位的功能**根本点不到。`WheelKeyPresses` 的做法是：先向系统借一把没人用的键位临时绑上、`resetMapping()` 后 `click()`、马上把原键位还回去（try/finally），同时补上 `setDown(true)` 并在 3 tick 后自动松开——等价于一次真实按键（真实按键同时产生 clickCount 和 isDown），所以不影响需要长按类动作。
