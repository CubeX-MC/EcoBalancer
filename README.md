# EcoBalancer: 智能经济平衡插件

[English](README_en.md) | 简体中文

EcoBalancer 是一个智能的 Minecraft 经济插件，通过对不活跃玩家实施智能税收系统来优化服务器经济。它促进公平竞争，创造活跃的游戏环境，并为服务器经济提供智能管理解决方案。

## 主要特点

- 自动化且可配置的税收设置
- 税收收入存入公共账户
- 财富分配直方图
- 玩家余额的基本统计数据（如平均值和标准差）

![Imgur](https://i.imgur.com/0eXcPeO.gif)

![Imgur](https://imgur.com/L7wagZ9.gif)

## 功能特性

- 自动定期（每日/每周/每月）对不活跃玩家账户征税
- 根据余额等级和活跃度自定义扣除率
- 手动征税命令，可按需调整

**注意**：\
EcoBalancer 目前正在测试中。我们建议在部署前进行严格评估。如有bug反馈或建议，请提交 issue。
另外，更新时请确保删除旧的配置文件和语言文件。

**前置插件**：Vault

## 命令

- `/ecobal help`：显示帮助信息
- `/ecobal reload`：重新加载配置文件
- `/checkall`：根据配置设置更新所有离线玩家的余额
- `/checkplayer <player>`：根据配置设置更新指定离线玩家的余额
- `/stats`：显示描述性统计
- `/interval`：列出特定区间内玩家的余额
- `/perc`：显示玩家余额的百分位数
- `/checkrecords`：显示所有操作记录
- `/checkrecord`：显示特定操作的详细信息
- `/restore`：恢复特定操作

## 配置 (config.yml)

```yaml
language: 'en_US' # 语言 en_US/zh_CN
info-on-login: true # 登录时显示用户信息
record-retention-days: 30 # 记录保留天数
check-time: "20:00" # 时间格式为 HH:mm
check-schedule:
 type: 'weekly' # 选项：'daily'（每日）, 'weekly'（每周）, 'monthly'（每月）
 days-of-week: [2, 4, 6] # 周一、周三、周五（7 = 周六，1 = 周日）
 dates-of-month: [1] # 每月1号
deduct-based-on-time: true
# 以下两个选项仅在 deduct-based-on-time 为 true 时生效
inactive-days-to-deduct: 50 # 开始扣除前的不活跃天数
inactive-days-to-clear: 500 # 清除余额前的不活跃天数
# 扣除的税收等级
tax-brackets:
 - threshold: 100000
   rate: 0.001 # 税率
 - threshold: 1000000
   rate: 0.01 # 税率
 - threshold: null # 无限制
   rate: 0.02 # 税率
tax-account: true # 是否使用税收账户
tax-account-name: 'tax' # 税收账户名称
```

[![Forkers repo roster for @CubeX-MC/EcoBalancer](https://reporoster.com/forks/CubeX-MC/EcoBalancer)](https://github.com/CubeX-MC/EcoBalancer/network/members)
[![Stargazers repo roster for @CubeX-MC/EcoBalancer](https://reporoster.com/stars/CubeX-MC/EcoBalancer)](https://github.com/CubeX-MC/EcoBalancer/stargazers) 