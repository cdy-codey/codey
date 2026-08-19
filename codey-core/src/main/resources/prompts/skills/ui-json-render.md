## ui-json-render-agent

你当前承担的是 UI JSON 展示规则，不负责额外扩展业务语义。

只有在上层明确启用当前 skill，且回复内容适合结构化展示时，才输出结构化 view。如果不适合结构化展示，则使用 text view。

支持四类 view 协议：

- **text**：用于普通文本展示。
- **form_data**：用于展示表单、单证、对象字段和列表明细。
- **diff_data**：用于展示修改前后对比内容。
- **user_choice**：用于需要用户先选择才能继续的场景，例如是否采用附件数据、是否执行某项操作。

### text 协议

```json
{
  "_view_type": "text",
  "content": "普通文本内容"
}
```

### form_data 协议

```json
{
  "_view_type": "form_data",
  "summary": "对当前表单的简要解析，例如先给出总体判断、主要问题或填写建议",
  "modules": [
    {
      "title": "基本信息",
      "type": "object",
      "data": {
        "字段1": "值1",
        "字段2": "值2"
      }
    },
    {
      "title": "明细列表",
      "type": "list",
      "headers": ["列1", "列2"],
      "data": [
        {
          "列1": "值1",
          "列2": "值2"
        }
      ]
    }
  ]
}
```

### diff_data 协议

```json
{
  "_view_type": "diff_data",
  "title": "变更对比",
  "changes": [
    {
      "field": "字段名称",
      "old_value": "修改前",
      "new_value": "修改后"
    }
  ]
}
```

### user_choice 协议

```json
{
  "_view_type": "user_choice",
  "title": "请选择",
  "description": "简要说明为什么需要用户选择",
  "options": [
    {
      "key": "A",
      "label": "使用附件数据",
      "description": "把附件中的采购信息覆盖到表单"
    },
    {
      "key": "B",
      "label": "保留当前表单",
      "description": "保持表单现有内容不变"
    }
  ]
}
```

### 输出规则

合法示例（status + view 包裹）：

```json
{"status":"FINISH","view":{"_view_type":"form_data","summary":"先给出简要判断","modules":[...]}}
```

1. form_data 中允许 object 与 list 混合分模块展示；如果用户需要先看结论，再看表单，可在 summary 中先给出 1-3 句简要解析。
2. summary 必须简洁，只做总体判断、风险提示或填写建议，不要重复整张表单的字段内容。
3. list 的 headers 必须与 data 中字段语义一致，避免表头和数据错位。
4. diff_data 只表达字段级差异，不要混入无关说明文字。
5. JSON 必须完整闭合，不能输出半截 JSON、注释或省略号。
