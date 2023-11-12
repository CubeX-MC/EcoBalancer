# EcoBalancer
该插件用于伤害方块叉服务器统治阶级与玩家之间的感情<br>
前置插件：Vault<br>
理论支持1.12-1.20（只在1.19和1.20测试过）<br>
Brutal punishment toward inactive players of CubeX<br>
prerequisite: Vault<br>
Theoretically support 1.12-1.20 (only tested on 1.19 & 1.20)

### /checkall
遍历并清洗全部离线玩家的余额<br>
check all offline players and reduce or clean balance based on configuration

### /checkplayer <playername>
针对单一玩家进行余额查处<br>
check single offline player and reduce or clean balance based on configuration

### 配置
```
check-time: "20:00"  # 格式为 HH:mm
check-schedule:
type: 'weekly' # 选项： 'daily', 'weekly', 'monthly'
days-of-week: [1, 3, 5] # 周一, 周三, 周五 (1 = 周六, 7 = 周日)
dates-of-month: [1] # 每月一号
deduct-based-on-time: true
# 下面两个选项仅在 deduct-based-on-time 为 true 时生效
inactive-days-to-deduct: 50  # 未上线扣款开始的天数
inactive-days-to-clear: 500  # 未上线清空余额的天数
# 按阶级扣税
tax-brackets:
  - threshold: 100000
    rate: 0.001
  - threshold: 1000000
    rate: 0.01
  - threshold: null  # null 表示无上限
    rate: 0.02
```
