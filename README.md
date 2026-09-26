# Minecraft 咒术回战 · 五条悟 × 宿傩 · JJK Gojo × Sukuna

[![Minecraft](https://img.shields.io/badge/Minecraft-26.3-62B47A)](https://www.minecraft.net/)
[![Loader](https://img.shields.io/badge/Loader-Fabric-DBD0B4)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-25-ED8B00)](https://adoptium.net/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![Release](https://img.shields.io/github/v/release/RoyceBella-commits/jjk-gojo-sukuna)](https://github.com/RoyceBella-commits/jjk-gojo-sukuna/releases/latest)

**只想爽快当一回五条悟或宿傩，不想啃复杂的咒术体系？**
现在大火的一些咒术回战 Mod 内容很全，但部分设计有些过于复杂。这个 Fabric Mod 只做两个人：**五条悟**和**两面宿傩**。适合只想单机爽玩的玩家，也适合想在联机里打一场宿傩对五条悟的玩家。

> *English:* A focused Jujutsu Kaisen Fabric mod with just two playable paths — Satoru Gojo and Ryomen Sukuna — each with their signature techniques, domain expansions, a five-tier progression, and full-power NPCs. Built for quick single-player fun and Gojo-vs-Sukuna multiplayer duels.

![无量空处：身后的黑洞与星空](docs/screenshots/unlimited-void-black-hole.webp)

---

## 🖼️ 画面一览

| | |
|---|---|
| ![领域对抗](docs/screenshots/domain-clash.webp) **领域对抗**：两个领域以分界面各占一侧，斩击穿过交界 | ![伏魔御厨子](docs/screenshots/malevolent-shrine.webp) **伏魔御厨子**：血红天空、镜面地面、必中斩击 |
| ![黑闪](docs/screenshots/black-flash.webp) **黑闪**：空手满蓄力一拳，概率打出 3 倍伤害 | ![灶·开](docs/screenshots/flame-arrow.webp) **灶·开**：火焰箭引发大范围爆燃 |
| ![技能轮盘](docs/screenshots/skill-wheel.webp) **技能轮盘**：按住左 Alt 选择术式 | |

## ✨ 功能亮点

- **两条路线，二选一**：第一次使用「六眼修习凭证」成为五条悟，第一次吃下「宿傩的手指」成为宿傩。路线锁定后不能更改。
- **标志性术式齐全**：无下限、苍、赫、茈、无量空处；解、捌、灶·开、世界斩、伏魔御厨子、魔虚罗。
- **五阶成长**：完成练习后用道具升阶，金色生命、护甲、徒手伤害、黑闪概率、承伤比例逐阶提升。
- **无下限与破解**：只有宿傩一方能突破无下限，宿傩对五条悟有来有回。
- **满级 NPC**：五条悟和宿傩的刷怪蛋，会主动放技能和开领域。
- **单机联机都能玩**：服务端和客户端装同一版本即可对战。

### 技能一览

| 五条悟 | 两面宿傩 |
|---|---|
| 六眼：视线内的敌人显示发光轮廓 | 解：远程斩击，蓄力后变成网格斩 |
| 无下限：挡住攻击、摔落、岩浆，箭矢停在身前 | 捌：锁定目标连续斩击 |
| 苍：吸引并碾压敌人，按住时绕身旋转 | 咒力连打：左右交替五拳 |
| 赫：命中后大范围爆炸，远远击飞 | 灶·开：火焰箭 |
| 茈：苍与赫合成，射程约 256 格，贯穿一切 | 世界斩：切开空间，直接突破无下限 |
| 领域展开·无量空处：领域内敌人完全定身 | 领域展开·伏魔御厨子：必中斩击 |
| 空间瞬移（C）：瞬移到准星处，最远 50 格 | 魔虚罗：分阶段适应伤害，可收回 |
| 反转术式：被动回血，主动快速治疗 | 咒力远跳（C）、反转术式 |

### 成长

完成练习（命中训练靶或敌人等）后，再用一份道具升一阶，共五阶。每阶提升：

- 金色生命
- 护甲
- 徒手伤害（满级 20）
- 黑闪概率（满级 20%）
- 承伤比例（满级只承受 1% 的普通伤害；被五条悟、宿傩一方打时承受 10%）

### 无下限与破解

只有宿傩一方能突破无下限：

- 世界斩直接穿透；
- 斩击、火焰、徒手中的同一类命中 3 次，可以破解该类无下限 5 秒；
- 魔虚罗同理。

### 刷怪蛋 NPC

创造物品栏里有五条悟和宿傩的刷怪蛋，属性按满级计算，会主动放技能和领域：

- 五条悟 NPC 攻击敌对生物和宿傩一方；
- 宿傩 NPC 攻击除宿傩路线玩家以外的一切生物。

另有两个秒杀道具（狱门疆、封印咒符），仅限单人世界或管理员使用。

## 📥 安装

需要：

| 组件 | 版本 |
|---|---|
| Minecraft Java 版 | 26.3 |
| [Fabric Loader](https://fabricmc.net/use/installer/) | 0.19.5 或更高 |
| [Fabric API](https://modrinth.com/mod/fabric-api) | 0.161.0+26.3 |
| Java | 25（官方启动器会自动提供） |

步骤：

1. 用 [Fabric 安装器](https://fabricmc.net/use/installer/)为 Minecraft 26.3 安装 Fabric Loader。
2. 从 [Releases](https://github.com/RoyceBella-commits/jjk-gojo-sukuna/releases/latest) 下载 `jjk-gojo-sukuna-2.2.1+mc26.3.jar`。
3. 把它和 Fabric API 一起放进游戏目录的 `mods` 文件夹（Windows 默认是 `%APPDATA%\.minecraft\mods`）。
4. 在启动器里选择 Fabric 配置启动游戏。

**单人游戏**：装在自己电脑上即可。
**多人服务器**：服务端和所有客户端都要装同一版本。

## 🎮 使用

### 道具与配方

| 道具 | 配方 | 作用 |
|---|---|---|
| 六眼修习凭证 | 纸 + 青金石 + 糖（无序） | 成为 / 升级五条悟 |
| 宿傩的手指 | 竖排 3 块腐肉 | 成为 / 升级宿傩 |
| 训练靶 | 十字形 4 木棍 + 中间干草块 | 练习目标，永不损坏 |

也可以直接用命令获取：

```mcfunction
/give @s sukuna:six_eyes_token 8
/give @s sukuna:finger 8
/give @s sukuna:training_dummy 2
/give @s sukuna:gojo_spawn_egg
/give @s sukuna:sukuna_spawn_egg
```

### 按键

| 键 | 作用 |
|---|---|
| 按住 左 Alt | 技能轮盘 |
| G | 施放（短按 / 蓄力 / 按住） |
| B | 五条悟：切换无限；宿傩：按住反转治疗 |
| C | 五条悟：空间瞬移；宿傩：咒力远跳（都无冷却） |
| Z | 凌空踏步 / 咒力跃升 |
| J | 显示或隐藏能力面板 |
| 技能快捷键 | 每个技能一个，默认未绑定，可在「按键设置 → 咒术回战 · 技能快捷键」里设置 |

所有按键都能在 *选项 → 控制 → 按键绑定* 中修改。更完整的试玩清单见 [安装与验收说明](安装与验收说明.md)。

### 命令

| 命令 | 说明 |
|---|---|
| `/jjk terrain on\|off` | 开关技能的地形破坏，需要管理员权限 |

## ⚙️ 配置文件

客户端 `config/sukuna-client.json`：

| 字段 | 说明 |
|---|---|
| `hudVisible` | 是否显示能力面板 |
| `lowFx` | 减少特效 |
| `reduceShake` | 减弱镜头震动 |
| `noFlash` | 关闭闪光 |

## ❓ 常见问题

**选了五条悟之后还能改成宿傩吗？**
不能。路线在第一次使用道具时锁定。

**五条悟的无下限能被打穿吗？**
只有宿傩一方能突破：世界斩直接穿透；同一类攻击命中 3 次可破解该类无下限 5 秒；魔虚罗同理。

**从旧版本升级需要注意什么？**
先删掉旧 JAR 再放入新版本（Mod ID 相同，同时放两个会报错）。旧存档可以继续用。

**联机时只装服务端可以吗？**
不行。服务端和所有客户端都要装同一版本。

## 🛠️ 从源码构建

需要 JDK 25。

```bash
git clone https://github.com/RoyceBella-commits/jjk-gojo-sukuna.git
cd jjk-gojo-sukuna
./gradlew build
```

产物在 `build/libs/`，使用不带 `-sources` 后缀的那个 JAR。更新内容见 [CHANGELOG](CHANGELOG.md)。

## 🙏 致谢与声明

- 宿傩部分大幅参考了 [宿傩 Mod](https://honghemc.cn/app/mods.html?mod=m-45becf0257)，作者 B 站：[space.bilibili.com/290692230](https://space.bilibili.com/290692230)，在此致谢。该 Mod 元数据署名 BlockForge，声明 MIT 许可。本 Mod 沿用了其中的模型、贴图、部分音频和着色器。
- 无量空处与伏魔御厨子的语音 / 音乐来自爱给网；其余音效、黑洞着色器、NPC 皮肤等为本 Mod 制作。
- 本 Mod 为非官方粉丝作品，与《咒术回战》版权方及 Mojang 无关。

## 📄 许可证

[MIT](LICENSE)
