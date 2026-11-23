# 简账 MiniLedger

自用的安卓记账本：监听微信 / 支付宝的付款通知自动记账，也可以直接说一句话记一笔。

- 包名 `com.miniledger.app`　版本 1.0　minSdk 26 / targetSdk 35
- 产物：`dist/miniledger.apk`（约 73 KB）
- 纯 framework Java + 代码搭 UI，**不依赖 androidx / Gradle**，用仓库现成的 WSL 工具链直接 aapt2 + javac + d8 编译
- 通知监听自动记账 + 自然语言助手（本地规则 / 可选大模型）
- 两套界面皮肤：玻璃 / 极简，设置里一键切换
- 纯本地存储；不开大模型时完全离线

## 构建

```bash
wsl -d Ubuntu -- bash "$(pwd)/MiniLedger/build.sh"
```

依赖 `%LOCALAPPDATA%\DayFlowBuild`（JDK 17 + build-tools 35 + platform 35）。
签名证书 `keystore.jks` 不入库；构建前设置密码环境变量（别名 `miniledger`）：

```bash
export MINILEDGER_STORE_PASS='你的 store 密码'
export MINILEDGER_KEY_PASS='你的 key 密码'
```

```bash
# 安装
adb install -r MiniLedger/dist/miniledger.apk
```

## 首次使用的 4 步

1. 打开 App，按提示进 **通知使用权** 开启简账（系统会问「允许读取所有通知」）。
2. **设置 → 电池优化白名单 → 去加入**。
3. **设置 → 自启动 / 后台管理 → 去设置**，把简账加进白名单（各 ROM 路径不同，代码里已内置 MIUI/华为/荣耀/OPPO/vivo/iQOO/三星/魅族 的入口）。
4. 最近任务里把简账 **下拉锁定 / 加锁**，别被一键清理。

做完这 4 步，`设置` 页的检查清单会显示「已开启 / 已加入」。

## 自动记账怎么工作

```
微信/支付宝推送 → NotificationListenerService
      → Parser 抽取（方向 + 金额 + 商户）
      → 去重（同源同金额 2.5 分钟内视为重复推送）
      → 分类规则（商户关键词 → 分类）
      → 直接入账，或进「待确认」
```

- **只认有明确金额 + 有收支方向的通知**，宁漏不误。
- 微信只接受 `微信支付/微信收款/收款助手/零钱/微信转账` 这类消息，好友聊天里说「转你 100 元」不会被记账。
- 「余额 / 额度 / 优惠 / 立减 / 抵扣」后面的数字不会被当成金额。
- 不同来源同金额 2.5 分钟内出现 → 标记「疑似重复」进待确认。
- **自动入账** 默认开；关掉后所有抓到的记录先进「待确认」。

### 微信通知经常不带金额
微信支付的服务号推送经常只有「微信支付凭证」，金额要点开才看得见，这类通知抓不到。
补的办法是导账单：微信 `我 → 服务 → 钱包 → 账单 → 常见问题 → 下载账单 → 用于个人对账`，
然后 `设置 → 导入账单 CSV`。支付宝同理（`我的 → 账单 → 开具交易流水证明`）。
导入按交易单号去重，重复导入不会产生重复账。

## 记账助手（自然语言）

第二个页签「助手」。说一句话，agent 出草稿卡片，你点「记入」才写进账本——不会自作主张。

```
午饭 25 微信              → 餐饮 ¥25.00 · 今天 · 微信
昨天打车 38 支付宝         → 交通 ¥38.00 · 昨天 · 支付宝
午饭25、打车38、咖啡19      → 三笔，自动按标点拆开
11月3号充话费100           → 通讯 ¥100.00 · 11月3日
上周五买鞋 399             → 购物 ¥399.00 · 上周五
收到工资 8000              → 工资 +¥8,000.00
AA 后我付 64，一共 128      → 只记 64（「一共」后面的是总数）
这个月花了多少 / 预算还剩多少 / 最近几笔 / 删掉刚才那笔
把昨天打车那笔改成 45      → 改金额
把打车那笔记成交通         → 改分类（改完顺便学会这个商户）
这笔算餐饮 / 把昨天那笔改成支付宝
删掉昨天打车那笔           → 删指定的一笔
```

架构和 DayFlow 的 `agent.js` 一致：

```
respond(text)
  ├─ 意图识别：改账 / 删账 / 查支出 / 查预算 / 看最近 —— 直接查库处理
  └─ 记账：大模型开着就走接口，失败 catch → 本地规则补上，并说明降级原因
```

**改账怎么找目标**：`findTarget` 给每笔候选打分——商户命中 +6（前 2-4 字部分命中 +4）、
日期命中 +4、分类命中 +3、金额命中 +2，取最高分且 ≥3 的。所以
「把昨天打车那笔改成 45」能精确落到昨天那笔打车上，而不是今天的午饭。
「刚才 / 上一笔 / 最近」这类没有具体线索的说法，直接取最后一笔。

大模型模式下不用找目标：system prompt 里带了最近 8 笔的 `id=`，
模型直接回 `actions`（update / delete），和 DayFlow 的 `applyActions` 一个形式。

**本地规则**（`Agent.java`，纯 Java、离线、零依赖）：抽时间 → 切子句 → 抽金额 → 判收支方向 →
认分类（15 类关键词表）→ 认账户 → 洗商户名。金额前面是「一共/总价/人均」就跳过，
后面跟「杯个斤份」这类量词也跳过，所以「买了2杯咖啡 40」只记 40。

**大模型**（`Llm.java`）：OpenAI 兼容接口，`temperature 0.2`，system prompt 里带今天日期、
本月支出、预算和最近 8 笔做上下文，要求只回一个 JSON：
```json
{"reply":"给用户看的一句话","items":[{"amount":25.5,"kind":"out","category":"餐饮","merchant":"美团","account":"微信","date":"2026-11-13"}]}
```
返回内容会被宽松解析（去 markdown 围栏、截取首尾大括号），分类不在白名单里就丢掉让规则表接手。

配置在 `设置 → 大模型（可选）`：接口地址（填到 `/v1`）、API Key、模型名，有「测试连接」。
**默认关闭**，关着的时候完全离线——`INTERNET` 权限只在开启后才会用到。


**月度预算**：`设置 → 月度预算`，输入一个数。账页顶部会出现一条进度；花到 80% 和 100% 各发一次通知，
按月去重（同一个阈值一个月只响一次）。超支后进度条变红。

**商户记忆**：你只要在某一笔上改过分类（长按账单 → 编辑，或在待确认里点「改分类」），
系统就把「商户 → 分类」写进规则表，之后同一个商户自动落这个分类。
规则就是 `rule` 表里的 `kw → cat`，可以随手清：删掉 `ledger.db` 重装即可。

## 界面：两套皮肤

`设置 → 外观` 切换，选完立即重建界面，当前页签不丢。

| | 玻璃（默认） | 极简 |
| --- | --- | --- |
| 卡片 | 55% 白 + 1px 高光边 + 柔和投影 | 纯白 + 1px 边线，无投影 |
| 背景 | 极光底（4 个柔色团，`Aurora.java` 手绘） | 单一浅灰 |
| 底栏 | 半透明 + 模糊感 | 纯白 + 顶边线 |

设计取自 `design-taste-frontend` 的 `pastel-consumer` 方向，两条硬规则贯穿两套皮肤：

- **支出用墨色，收入才用绿**（`+¥59.00` 带正号），红色只留给超支和服务故障。全屏没有"逢支出必红"。
- **一屏一个强调色**：品牌青只出现在数据（预算条、分类条、今日柱）和当前页签上，导航不做彩色药丸。

配色/字号只有一套：12 / 13 / 15 / 16 / 20 / 28，灰色两级 `#4B5058` / `#5F6368`，都过 4.5:1。
改色只改 `Ui.java` 顶部的常量，两套皮肤同时生效。

预览图：`design/glass.png`、`design/minimal.png`（`design/mock.html` 是同一套 token 的浏览器稿，
`#minimal` 锚点看极简版）。

> 说明：这个 skill 的 house rule 里是禁玻璃/毛玻璃的。玻璃是你明确要的，所以只在这套皮肤里破例；
> 极简那套是严格按规范来的版本。破例只在「表面材质」这一层，排版、间距、配色预算仍然按规范走。

## 保活三层

| 层 | 实现 | 作用 |
| --- | --- | --- |
| 前台服务 | `GuardService`（`specialUse` 类型，常驻通知显示今日支出） | 抬高进程优先级，不易被回收 |
| 周期自检 | `GuardJob` JobScheduler 每 15 分钟，`setPersisted(true)` | 检查授权、`requestRebind` 重新绑定监听、拉起服务 |
| 闹钟兜底 | `HeartbeatReceiver` AlarmManager `setAndAllowWhileIdle` | JobScheduler 被 ROM 掐掉时的后手 |
| 开机恢复 | `BootReceiver`（BOOT_COMPLETED / MY_PACKAGE_REPLACED） | 重启后自动恢复 |

监听被系统解绑时 `onListenerDisconnected` 会立刻 `requestRebind`；授权被关掉会发高优先级通知提醒。

## 解析规则想调在哪

`app/src/main/java/com/miniledger/app/Parser.java`

- `KW_IN` / `KW_OUT`：收支方向关键词
- `BAD_BEFORE`：金额前面的排除词（余额、优惠、立减…）
- `P_MERCHANT`：商户名抽取
- 分类关键词在 `Db.seed()`

改完跑一遍纯 JVM 自测（不碰 Android）：

```bash
wsl -d Ubuntu -- bash -lc 'cd "/mnt/c/Users/llyyt/Desktop/项目文件/MiniLedger" && \
  rm -rf /tmp/pt && mkdir -p /tmp/pt && \
  JDK=/mnt/c/Users/llyyt/AppData/Local/DayFlowBuild/jdk/bin && \
  $JDK/javac -encoding UTF-8 -d /tmp/pt \
    app/src/main/java/com/miniledger/app/{Tx,Money,Parser}.java test/ParserTest.java && \
  $JDK/java -Dfile.encoding=UTF-8 -cp /tmp/pt com.miniledger.app.ParserTest'
```

## 目录

```
MiniLedger/
  build.sh                     aapt2 + javac + d8 + apksigner
  app/src/main/
    AndroidManifest.xml        监听服务 / 前台服务 / JobService / 开机广播
    java/com/miniledger/app/
      MainActivity.java        5 个页签 + CSV 选文件
      PageBills.java           账单（月切换、预算条、手动记账 FAB）
      PageChat.java            记账助手（对话 + 草稿卡片）
      PagePending.java         待确认
      PageStats.java           统计（7 日柱状图、分类排行）
      PageSettings.java        权限、保活、预算、大模型配置
      AddDialog.java           记一笔 / 编辑
      TxAdapter.java           账单行
      Agent.java               自然语言 → 交易（纯规则，可单测）
      Llm.java                 OpenAI 兼容接口 + 宽松 JSON 解析
      NotifyListener.java      通知监听
      Parser.java              文本 → 交易
      Db.java                  SQLite（tx / rule）+ 商户记忆
      Budget.java              月度预算与阈值提醒
      CsvImport.java           微信/支付宝账单导入 + 导出
      GuardService.java        前台服务
      GuardJob.java            15 分钟自检
      HeartbeatReceiver.java   闹钟兜底
      BootReceiver.java        开机恢复
      Notifier.java            通知渠道
      Skin.java                当前皮肤
      Aurora.java              玻璃皮肤的极光底
      Ui.java                  控件工厂、配色、卡片材质
    res/                       主题、自适应图标、通知小图标
  test/ParserTest.java         通知解析自测（18 个用例）
  test/AgentTest.java          助手解析自测（36 个用例）
  test/run-tests.sh            一次跑完两个测试（纯 JVM）
  design/mock.html             设计稿（账单页，同一套 token 的浏览器版）
  design/mock-chat.html        设计稿（助手页）
  design/glass.png             玻璃皮肤预览（浏览器设计稿）
  design/minimal.png           极简皮肤预览
  design/chat.png              助手页预览
  design/device-bills.png      真机截图：账单
  design/device-chat.png       真机截图：助手
  design/device-stats.png      真机截图：统计
  design/device-settings.png   真机截图：设置
  dist/miniledger.apk
```

## 真机验证（HONOR 200 / MagicOS）

已装到真机跑过五个页签，走通了「说一句话 → 出草稿 → 点记入 → 账单里出现」的完整链路。

真机暴露并修掉的问题（模拟器和浏览器设计稿都看不出来）：

| 问题 | 原因 | 修法 |
| --- | --- | --- |
| 内容钻到状态栏底下 | targetSdk 35 在 Android 15 上强制 edge-to-edge | `applyInsets` 自己补内边距，底栏背景延伸进手势条 |
| 玻璃卡片几乎看不见边 | 极光底太淡 + 白色描边等于没画 | 提高色团不透明度、加一个中间色团、卡片改 1px 深色发丝线 |
| 草稿卡片金额只剩一个「−」 | 横向 weight 布局里右列被压窄 | 右列固定 96dp，金额单行右对齐 |
| 「午饭」记成「午」 | 商户名清洗里把「饭」当语气词删了 | 只保留「吃饭」这种词组 |
| 设置页分组卡片是空的 | `section()` 返回外层容器，行被加到了卡片外面 | 改成 `section()` 自己挂到列上并返回卡片 |
| 统计页没预算却显示「超支」 | 把「收入-支出」的负数当成了超支 | 改成「净支出」，红色只留给真的超预算 |
| 只有两笔账时列表卡片撑满整屏 | 卡片恒为 weight=1 | 6 笔以内按内容高度，超过才占满并滚动 |

## 已知限制
- 微信/支付宝的通知格式会随版本变，解析是「尽力而为」；漏掉的用账单 CSV 补。
- 无障碍自动抓取（能拿到 100% 准确的金额）没有做——UI 改版就废，维护成本太高，自用不值当。
- 记账数据在应用私有目录，卸载即清空。重要数据请定期 `设置 → 导出全部账单`。
