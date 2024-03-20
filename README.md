# EcoBalancer: A Smart Minecraft Economy Plugin

EcoBalancer is a smart Minecraft economy plugin that optimizes your server's economy through an intelligent tax system for inactive players. It promotes fair competition, creates an active gaming environment, and provides smart management solutions for your server's economy.

## Key Features

- Automated and configurable tax settings
- Tax revenue saved into a public account
- Wealth distribution histogram
- Basic statistics like mean and standard deviation for player balance

![Imgur](https://i.imgur.com/0eXcPeO.gif)

![Imgur](https://imgur.com/L7wagZ9.gif)

## Features

- Automated routine taxing (daily/weekly/monthly) of inactive player accounts
- Customizable deduction rates based on balance classes and activity levels
- Manual taxation commands for on-demand adjustments

**Note**: \
EcoBalancer is currently undergoing testing. We encourage rigorous evaluation before deployment. For bug reports or suggestions, please open an issue.
Also, make sure you remove the old config & language files when updating.

**Prerequisite**: Vault

## Commands

- `/ecobal help`: Display help information
- `/ecobal reload`: Reload the configuration file
- `/checkall`: Update all offline players' balances as per configuration settings
- `/checkplayer <player>`: Update a specific offline player's balance as per configuration settings
- `/stats`: Show descriptive statistics
- `/interval`: List players' balance in a specific interval
- `/perc`: Show percentile of players' balance
- `/checkrecords`: Show all operations
- `/checkrecord`: Show detail of a specific operation
- `/restore`: Restore a specific operation

## Configuration (config.yml)

```yaml
language: 'en_US' # Language en_US/zh_CN
info-on-login: true # Show user info on login
record-retention-days: 30 # Record retention days
check-time: "20:00" # Time format is HH:mm
check-schedule:
 type: 'weekly' # Options: 'daily', 'weekly', 'monthly'
 days-of-week: [2, 4, 6] # Monday, Wednesday, Friday (7 = Saturday, 1 = Sunday)
 dates-of-month: [1] # 1st day of each month
deduct-based-on-time: true
# The following two options only take effect when deduct-based-on-time is true
inactive-days-to-deduct: 50 # Days inactive before starting deductions
inactive-days-to-clear: 500 # Days inactive before clearing balance
# Tax brackets for deductions
tax-brackets:
 - threshold: 100000
   rate: 0.001 # Tax rate
 - threshold: 1000000
   rate: 0.01 # Tax rate
 - threshold: null # No limit
   rate: 0.02 # Tax rate
tax-account: true # Whether to use tax account
tax-account-name: 'tax' # Tax account name

[![Stargazers repo roster for @USERNAME/REPO_NAME](https://reporoster.com/stars/USERNAME/REPO_NAME)](https://github.com/USERNAME/REPO_NAME/stargazers)

[![Forkers repo roster for @USERNAME/REPO_NAME](https://reporoster.com/forks/USERNAME/REPO_NAME)](https://github.com/USERNAME/REPO_NAME/network/members)
