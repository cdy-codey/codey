# 业务架构图（大模型调度 / 工具编排 / 技能编排 / 安全机制）

本文用于面向开源读者说明 Codey 的核心业务运行机制：如何把“大模型能力”组织成可执行的任务，并通过工具、技能与安全机制把能力落到真实工作区与业务场景中。

## 一次 Turn 的主流程

1. 调用方提交任务（goal / skillName / identities / modelConfig 等）到 `AgentClient`
2. 调度器对同一 `sessionId` 的 Turn 串行化，生成并启动任务
3. Loop 编排器驱动循环：组装 Prompt → 调用模型 → 解析 `tool_calls`
4. 工具编排器对工具调用做权限校验、并行批处理、执行与结果回灌
5. 触发安全链路：路径沙箱、写操作人工确认、写后校验与再规划
6. 事件流持续输出执行过程，供 UI/CLI 展示与审计

## 业务架构图

```mermaid
flowchart LR
    %% 全局方向从左到右，模拟侧边栏（安全） + 主体层级的布局

    %% 左侧：侧边栏（贯穿全局的安全与审计机制）
    subgraph S_Security [安全与观测层]
        direction TB
        WPS[路径沙箱越界防护]
        TAC[运行时越权拦截]
        HCS[高风险写操作确认]
        VF[写后结果校验]
        SEP[全链路事件发布]
        
        WPS --- TAC --- HCS --- VF --- SEP
    end

    %% 右侧：主体分层架构（自上而下）
    subgraph S_Main [Codey 核心业务架构]
        direction TB

        subgraph L1 [1. 业务接入层]
            direction LR
            Client[调用方: Web / CLI / SDK]
            AC[AgentClient 统一入口]
            Client -->|注入业务工具与身份| AC
        end

        subgraph L2 [2. 会话调度层]
            direction LR
            TR[TaskRunnerAgentClient<br/>Turn 串行与线程池调度]
            TF[TaskRunnerFactory<br/>会话与任务构建]
            TR --> TF
        end

        subgraph L3 [3. 核心编排层]
            direction LR
            LO{LoopOrchestrator<br/>循环驱动器}
            
            subgraph L3_Skill [技能编排与权限隔离]
                direction TB
                SS[基于身份隔离]
                SR[工具白名单]
                TEP[按边界隔离可见工具]
                SS --> SR --> TEP
            end
            
            L3_Skill -.->|注入受限Schema| LO
        end

        subgraph L4 [4. 执行底座]
            direction LR
            
            subgraph L4_Tool [工具执行]
                direction TB
                TCP[并行批处理与结果回灌]
                TEX[ToolExecutor 实际执行]
                TRG[工具池_含注入业务工具]
                TCP --> TEX --> TRG
            end
            
            subgraph L4_Model [大模型网关]
                direction TB
                MG[解析运行时配置]
                HG[HttpModelGateway]
                LLM[(LLM Provider)]
                MG --> HG --> LLM
            end
        end

        %% 主体层级内部的上下连接
        L1 --> L2
        L2 --> L3
        L3 --> L4
    end

    %% 侧边栏与主体层的横向关联（体现安全机制对各层的拦截与观测）
    S_Main -.->|触发拦截/发布事件| S_Security

    %% 主题配色（采用暗黑赛博风，青色/蓝绿点缀，模仿参考图）
    classDef default fill:#0b192c,stroke:#00e5ff,stroke-width:2px,color:#ffffff
    classDef security fill:#0a1128,stroke:#1de9b6,stroke-width:2px,color:#a7ffeb
    classDef layer fill:#12233a,stroke:#00b8d4,stroke-width:1px,color:#e0f7fa
    
    class S_Security,WPS,TAC,HCS,VF,SEP security
    class L1,L2,L3,L4,L4_Tool,L4_Model,L3_Skill layer
    class Client,AC,TR,TF,LO,SS,SR,TEP,TCP,TEX,TRG,MG,HG,LLM default
```

## 四个关注点的落点说明

### 1) 大模型调度

- 目标：保证同一会话的状态一致性与可追踪性，对 Turn 做串行调度，输出任务状态事件。
- 代码落点：
  - `TaskRunnerAgentClient`：`submitTurn()` 对同 `sessionId` 进行 Turn 串行化与线程池执行  
    - 源码：[`TaskRunnerAgentClient.java`](../codey-core/src/main/java/com/codey/task/TaskRunnerAgentClient.java)
  - `TaskRunnerFactory`：组装并创建 `TaskRunner`  
    - 源码：[`TaskRunnerFactory.java`](../codey-core/src/main/java/com/codey/task/TaskRunnerFactory.java)

### 2) 工具编排

- 目标：把模型输出的 `tool_calls` 变成可控、可并行、可审计的真实执行过程，并把结果回灌模型进入下一轮推理。
- 代码落点：
  - 工具调用处理：[`ToolCallProcessor.java`](../codey-core/src/main/java/com/codey/loop/ToolCallProcessor.java)
  - 工具执行器：[`ToolExecutor.java`](../codey-core/src/main/java/com/codey/tools/ToolExecutor.java)
  - 工具注册中心：[`ToolRegistry.java`](../codey-core/src/main/java/com/codey/tools/ToolRegistry.java)
  - 内置工具集（MCP）：[`BuiltinToolRegistryFactory.java`](../codey-core/src/main/java/com/codey/tools/BuiltinToolRegistryFactory.java)

### 3) 技能编排

- 目标：让业务系统以“技能”为单位组织 Prompt 与工具能力边界，并用 `identity` 控制可用范围。
- 代码落点：
  - 技能选择：[`SkillSelector.java`](../codey-core/src/main/java/com/codey/skill/SkillSelector.java)
  - 技能注册表：[`SkillRegistry.java`](../codey-core/src/main/java/com/codey/skill/SkillRegistry.java)
  - YAML 加载：[`YamlSkillLoader.java`](../codey-core/src/main/java/com/codey/skill/YamlSkillLoader.java)
  - 技能定义（约束字段）：[`SkillDefinition.java`](../codey-core/src/main/java/com/codey/skill/SkillDefinition.java)

### 4) 安全机制（工具、技能权限）

- 目标：减少模型误用工具与越界风险，把“允许什么工具/允许对什么路径操作/哪些操作需要审批/写完如何校验”收敛到可治理的链路里。
- 代码落点：
  - 工具权限校验：[`ToolAccessController.java`](../codey-core/src/main/java/com/codey/loop/ToolAccessController.java)
  - 工具暴露规划（只暴露允许的工具）：[`ToolExposurePlanner.java`](../codey-core/src/main/java/com/codey/loop/ToolExposurePlanner.java)
  - 工作区路径沙箱：[`WorkspacePathSupport.java`](../codey-core/src/main/java/com/codey/infra/WorkspacePathSupport.java)
  - 写操作人工确认：[`HumanConfirmationService.java`](../codey-core/src/main/java/com/codey/loop/HumanConfirmationService.java)
  - 写后校验与再规划：[`Verifier.java`](../codey-core/src/main/java/com/codey/verify/Verifier.java)
  - 注入过滤（伪工具包装）：[`FakeToolWrapperFilter.java`](../codey-core/src/main/java/com/codey/infra/FakeToolWrapperFilter.java)
