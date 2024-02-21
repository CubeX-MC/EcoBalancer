# EcoBalancer
该插件用于伤害方块叉服务器统治阶级与玩家之间的感情<br>
前置插件：Vault<br>
理论支持1.12-1.20<br>
Brutal punishment toward inactive players of CubeX<br>
prerequisite: Vault<br>
Theoretically support 1.12-1.20

### /checkall
遍历并清洗全部离线玩家的余额<br>
check all offline players and reduce or clean balance based on configuration

### /checkplayer <playername>
针对单一玩家进行余额查处<br>
check single offline player and reduce or clean balance based on configuration

### 配置 config
```
check-time: "20:00"  # 格式为 HH:mm # Time format is HH:mm
check-schedule:
  type: 'weekly' # 选项: 'daily', 'weekly', 'monthly' # Options: 'daily', 'weekly', 'monthly'
  days-of-week: [2, 4, 6] # 周一, 周三, 周五 (7 = 周六, 1 = 周日) # Monday, Wednesday, Friday (7 = Saturday, 1 = Sunday)
  dates-of-month: [1] # 每月一号 # 1st day of each month
deduct-based-on-time: true
# 下面两个选项仅在 deduct-based-on-time 为 true 时生效 # The following two options only take effect when deduct-based-on-time is true
inactive-days-to-deduct: 50  # 未上线扣款开始的天数 # Days inactive before starting deductions
inactive-days-to-clear: 500 # 未上线清空余额的天数 # Days inactive before clearing balance
# 按阶级扣税 # Tax brackets for deductions
tax-brackets:
  - threshold: 100000
    rate: 0.001 # 税率 # Tax rate
  - threshold: 1000000
    rate: 0.01 # 税率 # Tax rate
  - threshold: null # 无上限 # No limit
    rate: 0.02 # 税率 # Tax rate
tax-account: true # 是否使用税金账户 # Whether to use tax account
tax-account-name: 'tax' # 税金账户名称 # Tax account name
```
