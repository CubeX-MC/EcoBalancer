# EcoBalancer 新增经济指标功能总结

## 实现的三个核心命令

### 1. `/ecobal gini [days]` - 基尼系数分析
**功能**：计算服务器经济的基尼系数，衡量财富分配的不平等程度。

**参数**：
- `days`（可选）：仅统计 N 天内活跃的玩家

**输出信息**：
- 基尼系数值（0-1）
- 等级评价（高度平等 → 极度不平等）
- 统计样本数量
- 总货币量
- 根据系数自动给出警告/建议

**使用示例**：
```
/ecobal gini          # 统计所有玩家
/ecobal gini 30       # 仅统计30天内活跃玩家
```

**经济意义**：
- 0.0-0.3：高度平等，财富分配健康
- 0.3-0.4：相对平等
- 0.4-0.5：中等不平等
- 0.5-0.6：高度不平等，建议调整税率
- 0.6-1.0：极度不平等，需要紧急干预

---

### 2. `/ecobal concentration [percentages...]` - 财富集中度分析
**功能**：分析 Top N% 玩家持有的财富占比，直观展示"寡头经济"现象。

**参数**：
- `percentages`（可选，可多个）：指定要分析的百分比
- 默认：1%, 5%, 10%, 20%

**输出信息**：
- 各百分比玩家持有的财富占比
- 对应的玩家数量
- 集中度等级（分散 → 极度集中）
- 自动警告（Top 1% >60% 时触发）

**使用示例**：
```
/ecobal concentration           # 默认分析 1%, 5%, 10%, 20%
/ecobal concentration 1 10 25   # 自定义分析 1%, 10%, 25%
```

**经济意义**：
- Top 1% <30%：分散，经济健康
- Top 1% 30-50%：中度集中
- Top 1% 50-70%：高度集中，需要关注
- Top 1% >70%：极度集中，经济失衡

---

### 3. `/ecobal report [operation_id]` - 税收报告
**功能**：查看税收操作的详细报告，包括征税总额、影响人数、税阶分布等。

**参数**：
- `operation_id`（可选）：指定操作ID
- 不填参数：显示最近一次 checkAll 的报告

**输出信息**：
- 操作类型（CheckAll / CheckPlayer）
- 执行时间
- 征税总额
- 影响玩家数量
- 平均税率
- 税阶分布（每个税率档位的人数和金额）

**使用示例**：
```
/ecobal report        # 查看最近一次操作
/ecobal report 42     # 查看操作 #42 的报告
```

**与现有 checkrecord 的区别**：
- `checkrecord`：逐个玩家的扣税明细（微观）
- `report`：整体操作的汇总统计（宏观）

---

## 技术实现细节

### 新增文件
1. **utils/EconomicMetrics.java**
   - 基尼系数计算算法（洛伦兹曲线）
   - 财富集中度计算
   - 余额收集（支持活跃天数过滤）
   - 辅助方法：等级描述、大数字格式化

2. **commands/GiniCommand.java**
   - 异步计算基尼系数
   - 参数解析与验证
   - 根据结果自动给出建议

3. **commands/ConcentrationCommand.java**
   - 支持多个百分比参数
   - 去重并排序
   - 分层警告机制

4. **commands/TaxReportCommand.java**
   - 数据库查询操作信息
   - 聚合统计（总额、平均值、税阶分布）
   - 格式化时间戳

### 修改的文件
1. **commands/UtilCommand.java**
   - 新增三个命令的路由
   - 更新帮助信息

2. **commands/EcoTabCompleter.java**
   - 添加 gini/concentration/report 的自动补全
   - gini 补全建议：7, 30, 60, 90 天
   - concentration 补全建议：1, 5, 10, 20, 25, 50

3. **lang/zh_CN.yml** 和 **lang/en_US.yml**
   - 新增所有命令的提示消息
   - 帮助文本
   - 错误提示
   - 警告信息

4. **README.md** 和 **README_en.md**
   - 更新命令列表，按功能分类
   - 添加新命令说明

---

## 性能优化

### 异步计算
所有三个命令都采用异步计算，避免阻塞主线程：
```java
SchedulerUtils.runTaskAsync(plugin, () -> {
    // 计算逻辑
    SchedulerUtils.runTask(plugin, () -> {
        // 主线程发送结果
    });
});
```

### 数据库优化
- TaxReportCommand 使用聚合查询，避免逐行读取
- 利用现有的索引（operation_id）

### 内存优化
- 余额列表仅在计算时创建，计算完成后释放
- 支持活跃天数过滤，减少样本量

---

## 使用场景示例

### 场景1：定期健康检查
服主每周执行：
```
/ecobal gini 30              # 查看活跃玩家的贫富差距
/ecobal concentration        # 查看财富集中度
```
根据结果调整 config.yml 的税率。

### 场景2：税收效果验证
执行 checkAll 后：
```
/ecobal report               # 查看本次税收报告
```
分析各税阶的影响人数，判断税率设置是否合理。

### 场景3：长期趋势分析
每月记录一次：
```
/ecobal gini                 # 记录基尼系数
/ecobal concentration 1      # 记录 Top 1% 占比
```
对比历史数据，判断经济是否向健康方向发展。

---

## 下一步可扩展功能

### 已设计但未实现的功能
1. **经济健康度综合评分** (`/ecobal health`)
   - 综合基尼、集中度、通胀率等指标
   - 给出 0-100 分的健康评分
   - 自动生成调控建议

2. **税收影响对比** (`/ecobal impact <operation_id>`)
   - 显示税收前后的基尼系数变化
   - 中位数、标准差对比
   - 需要在 checkAll 前自动快照

3. **玩家分组分析** (`/ecobal players <active|new|all> [days]`)
   - 活跃玩家的财富分布
   - 新玩家的经济融入速度

4. **通胀率监控** (`/ecobal trends [days]`)
   - 总货币量变化趋势
   - 周/月通胀率
   - 需要定期快照机制

---

## 配置建议

### 经济健康的参考标准
| 指标 | 健康范围 | 警戒线 | 危险线 |
|------|---------|-------|--------|
| 基尼系数 | 0.3-0.4 | 0.5 | 0.6 |
| Top 1% 财富占比 | <30% | 50% | 60% |
| Top 5% 财富占比 | <50% | 70% | 80% |

### 税率调整建议
- 基尼系数 >0.6：高税阶税率提升 50%
- Top 1% >60%：对超高收入阶层增设更高税率
- 基尼系数 <0.3：可适当降低税率，刺激经济

---

## 构建状态
✅ 编译成功  
✅ 无错误  
✅ 已打包到 target/EcoBalancer-1.1.0-shaded.jar

## 文件清单
- ✅ utils/EconomicMetrics.java
- ✅ commands/GiniCommand.java
- ✅ commands/ConcentrationCommand.java
- ✅ commands/TaxReportCommand.java
- ✅ commands/UtilCommand.java (已更新)
- ✅ commands/EcoTabCompleter.java (已更新)
- ✅ lang/zh_CN.yml (已更新)
- ✅ lang/en_US.yml (已更新)
- ✅ README.md (已更新)
- ✅ README_en.md (已更新)
