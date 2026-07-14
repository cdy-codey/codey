package com.codey.web.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 采购业务 AI 参考数据服务。
 * 当前全部返回 demo 数据，用于验证工具编排和提示词效果。
 */
@Service
public class ProcurementReferenceDataService {
    private static final List<String> HISTORY_QUERY_FIELDS = Arrays.asList(
            "historyId",
            "department",
            "itemName",
            "category",
            "brandModel",
            "specification",
            "supplier",
            "usageScene"
    );
    private static final List<String> ASSET_CONFIGURATION_QUERY_FIELDS = Arrays.asList(
            "configCode",
            "scene",
            "category",
            "itemName",
            "specificationBaseline",
            "recommendedBrands",
            "fitFor"
    );

    /**
     * 返回采购表单字段说明，支持按字段键精确过滤。
     */
    public Map<String, Object> getFormFieldGuide(String fieldKey) {
        List<Map<String, Object>> allFields = buildFieldGuides();
        List<Map<String, Object>> matchedFields = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> field : allFields) {
            String key = String.valueOf(field.get("fieldKey"));
            if (isBlank(fieldKey) || key.equals(fieldKey)) {
                matchedFields.add(field);
            }
        }

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("scenario", "计算机采购");
        payload.put("queryFieldKey", fieldKey);
        payload.put("matchedCount", matchedFields.size());
        payload.put("fields", matchedFields);
        payload.put("summary", isBlank(fieldKey)
                ? "已返回计算机采购表单全部字段说明。"
                : (matchedFields.isEmpty()
                ? "未找到指定字段，请使用完整字段路径。"
                : "已返回指定字段说明。"));
        return payload;
    }

    /**
     * 返回历史采购明细样例，支持按关键字模糊过滤。
     */
    public Map<String, Object> searchHistoryItems(Map<String, String> queries) {
        List<Map<String, Object>> matchedItems = buildHistoryItems();
        if (queries != null && !queries.isEmpty()) {
            for (Map.Entry<String, String> entry : queries.entrySet()) {
                String field = entry.getKey();
                String value = entry.getValue();
                if (HISTORY_QUERY_FIELDS.contains(field) && value != null && !value.trim().isEmpty()) {
                    matchedItems = filterByField(matchedItems, HISTORY_QUERY_FIELDS, field, value.trim());
                }
            }
        }

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("scenario", "计算机采购");
        payload.put("queryType", "list");
        payload.put("supportedQueryFields", HISTORY_QUERY_FIELDS);
        payload.put("queries", queries);
        payload.put("matchedCount", matchedItems.size());
        payload.put("items", matchedItems);
        payload.put("summary", queries == null || queries.isEmpty()
                ? "已返回全部历史计算机采购明细 demo 数据，可按 supportedQueryFields 指定字段查询。"
                : "已返回历史采购明细列表。");
        return payload;
    }

    /**
     * 返回资产配置与价格参考样例，帮助模型判断配置是否合理。
     */
    public Map<String, Object> queryAssetConfigurations(String queryField, String queryValue) {
        List<Map<String, Object>> allConfigurations = buildAssetConfigurations();
        List<Map<String, Object>> matchedConfigurations = filterByField(
                allConfigurations,
                ASSET_CONFIGURATION_QUERY_FIELDS,
                queryField,
                queryValue
        );

        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("scenario", "计算机采购");
        payload.put("queryType", "list");
        payload.put("supportedQueryFields", ASSET_CONFIGURATION_QUERY_FIELDS);
        payload.put("queryExample", "queryField=itemName, queryValue=便携式计算机");
        payload.put("queryField", queryField);
        payload.put("queryValue", queryValue);
        payload.put("matchedCount", matchedConfigurations.size());
        payload.put("configurations", matchedConfigurations);
        payload.put("summary", isBlank(queryField) || isBlank(queryValue)
                ? "已返回全部计算机资产配置参考 demo 数据，可按 supportedQueryFields 指定字段查询。"
                : "已返回资产配置参考列表，请按 queryField/queryValue 继续筛选。");
        return payload;
    }

    private List<Map<String, Object>> buildFieldGuides() {
        List<Map<String, Object>> fields = new ArrayList<Map<String, Object>>();
        fields.add(fieldGuide(
                "header.urgencyLevel",
                "紧急度",
                "enum",
                true,
                "表示本次采购处理时效。",
                "优先使用页面给出的枚举值，不要自造新状态。",
                Arrays.asList("紧急", "一般"),
                Arrays.asList("必填", "只能从下拉选项中选择"),
                "一般"
        ));
        fields.add(fieldGuide(
                "header.relatedOrderNo",
                "关联前期申购单",
                "enum",
                true,
                "表示当前采购是否关联历史申购流程。",
                "没有前单时填写“无前期申购单”，不要编造单号。",
                Arrays.asList("无前期申购单", "关联已有申购单"),
                Arrays.asList("必填", "只能从下拉选项中选择"),
                "无前期申购单"
        ));
        fields.add(fieldGuide(
                "header.requestDate",
                "申请日期",
                "string",
                true,
                "表示业务提交采购申请的日期。",
                "必须使用 YYYY-MM-DD 格式，通常使用当前业务日期。",
                null,
                Arrays.asList("必填", "格式必须为 YYYY-MM-DD"),
                "2026-06-22"
        ));
        fields.add(fieldGuide(
                "header.requestDepartment",
                "申请部门",
                "enum",
                true,
                "表示本次采购发起的归口部门。",
                "需要结合业务背景选择真实部门，避免和历史部门冲突。",
                Arrays.asList("行政服务部", "采购部", "信息技术部"),
                Arrays.asList("必填", "优先使用页面枚举"),
                "信息技术部"
        ));
        fields.add(fieldGuide(
                "header.assetType",
                "属性",
                "enum",
                true,
                "表示采购对象属于货物还是服务。",
                "计算机采购通常为“货物”，除非明确采购运维或服务。",
                Arrays.asList("货物", "服务"),
                Arrays.asList("必填"),
                "货物"
        ));
        fields.add(fieldGuide(
                "header.purchaseType",
                "采购类别",
                "enum",
                true,
                "表示是否纳入政府采购流程。",
                "需要结合预算、目录属性和业务规则判断，只能填固定枚举。",
                Arrays.asList("非政府采购", "政府采购"),
                Arrays.asList("必填", "只能填写系统预设值"),
                "政府采购"
        ));
        fields.add(fieldGuide(
                "header.requestDescription",
                "采购内容描述",
                "string",
                true,
                "概述采购对象、数量、用途和预算信息。",
                "建议同时写清计算机类别、数量、主要配置和预算金额。",
                null,
                Arrays.asList("必填", "建议包含设备名称、数量、预算"),
                "采购 2 台办公笔记本电脑，用于采购部日常办公，总预算 11000 元。"
        ));
        fields.add(fieldGuide(
                "detail.budgetAmount",
                "申请金额（元）",
                "number",
                true,
                "表示本次采购预算总金额。",
                "必须等于 items 中每行数量乘单价的合计，不能写 0 或失真金额。",
                null,
                Arrays.asList("必填", "必须大于 0", "必须等于明细合计"),
                "11000"
        ));
        fields.add(fieldGuide(
                "detail.requestReason",
                "需求原因说明",
                "string",
                true,
                "说明为什么需要采购该批计算机。",
                "建议包含现状问题、使用场景、预期收益和必要性。",
                null,
                Arrays.asList("必填", "需要体现采购必要性"),
                "现有办公电脑老化严重，无法满足报表处理和日常办公需求。"
        ));
        fields.add(fieldGuide(
                "detail.exceedReason",
                "超标或目录外说明",
                "string",
                false,
                "用于补充超预算、超配置或目录外采购的理由。",
                "当目录外采购或价格偏高时，建议补充合规依据和业务必要性。",
                null,
                Arrays.asList("选填", "目录外或超标场景建议填写"),
                "因现有目录内型号无法满足双屏与大数据报表处理要求，申请目录外采购。"
        ));
        fields.add(fieldGuide(
                "items[].demandType",
                "需求类别",
                "string",
                true,
                "表示本行明细所属需求类型。",
                "计算机采购通常填写办公类或信息化设备类，保持同类口径一致。",
                null,
                Arrays.asList("必填"),
                "办公类"
        ));
        fields.add(fieldGuide(
                "items[].category",
                "目录类型",
                "enum",
                true,
                "表示该资产是否属于政府采购统一采购目录。",
                "只能填写“目录内”或“目录外”，不能写其他口径。",
                Arrays.asList("目录内", "目录外"),
                Arrays.asList("必填", "只能填写目录内/目录外"),
                "目录内"
        ));
        fields.add(fieldGuide(
                "items[].itemName",
                "标的名称",
                "string",
                true,
                "表示采购明细的标准名称。",
                "优先使用规范名称，例如台式计算机、便携式计算机，不建议只写“电脑”。",
                null,
                Arrays.asList("必填", "建议使用标准资产名称"),
                "台式计算机"
        ));
        fields.add(fieldGuide(
                "items[].quantity",
                "数量",
                "number",
                true,
                "表示本行明细的采购数量。",
                "必须是大于等于 1 的整数。",
                null,
                Arrays.asList("必填", "必须为整数", "必须大于等于 1"),
                "2"
        ));
        fields.add(fieldGuide(
                "items[].unitPrice",
                "单价（元）",
                "number",
                true,
                "表示单台或单件资产的采购单价。",
                "必须大于 0，并且与配置、品牌和历史价格相匹配。",
                null,
                Arrays.asList("必填", "必须大于 0", "应与规格品牌匹配"),
                "5500"
        ));
        fields.add(fieldGuide(
                "items[].specification",
                "规格型号",
                "string",
                true,
                "描述计算机的核心配置，如 CPU、内存、硬盘等。",
                "建议写清 CPU、内存、硬盘、屏幕或显卡等关键指标。",
                null,
                Arrays.asList("必填", "建议包含核心硬件配置"),
                "Intel i5 / 16G / 512G SSD / 14 英寸"
        ));
        fields.add(fieldGuide(
                "items[].referenceBrand",
                "参考品牌",
                "string",
                true,
                "表示可供比价和选型参考的品牌型号。",
                "建议填写主流品牌或已采购品牌，便于做价格合理性判断。",
                null,
                Arrays.asList("必填", "建议填写品牌或品牌型号"),
                "联想 ThinkPad E14"
        ));
        fields.add(fieldGuide(
                "items[].recommended",
                "是否推荐",
                "boolean",
                false,
                "表示该条明细是否为当前优先推荐方案。",
                "主推荐方案可标记 true，其余候选方案可标记 false。",
                Arrays.asList("true", "false"),
                Arrays.asList("选填", "布尔值"),
                "true"
        ));
        return fields;
    }

    private List<Map<String, Object>> buildHistoryItems() {
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        // 这些样例故意覆盖办公台式机、笔记本和高配工作站三类计算机场景。
        items.add(historyItem(
                "HIS-PC-20260318-01",
                "2026-03-18",
                "信息技术部",
                "台式计算机",
                "目录内",
                "联想 ThinkCentre M75t",
                "R7-8700G / 16G / 512G SSD / 集显",
                3,
                4850,
                "华北政采电子商城",
                "办公终端替换",
                "与当前办公台式机采购场景最接近，适合作为价格基准。"
        ));
        items.add(historyItem(
                "HIS-PC-20260409-02",
                "2026-04-09",
                "采购部",
                "便携式计算机",
                "目录内",
                "联想 ThinkPad E14",
                "Intel i5 / 16G / 512G SSD / 14 英寸",
                2,
                5499,
                "市级框架协议供应商 A",
                "移动办公",
                "常规办公笔记本成交价，适合做目录内价格参考。"
        ));
        items.add(historyItem(
                "HIS-PC-20260521-03",
                "2026-05-21",
                "宣传策划部",
                "图形工作站",
                "目录外",
                "戴尔 Precision 3680",
                "Intel i7 / 32G / 1T SSD / RTX 4060",
                1,
                12800,
                "品牌直采渠道 B",
                "图文设计与视频剪辑",
                "高配图形工作站价格明显高于普通办公机，需结合业务必要性说明。"
        ));
        return items;
    }

    private List<Map<String, Object>> buildAssetConfigurations() {
        List<Map<String, Object>> configurations = new ArrayList<Map<String, Object>>();
        // 该参考配置只用于给模型估价，不代表真实采购目录标准。
        configurations.add(assetConfiguration(
                "CFG-OFFICE-DESKTOP",
                "日常办公台式机",
                "目录内",
                "台式计算机",
                "Intel i5 或同档次 / 16G / 512G SSD / 集成显卡",
                4200,
                5200,
                Arrays.asList("联想 ThinkCentre", "惠普 Pro Tower", "戴尔 OptiPlex"),
                "适用于公文处理、报表、浏览器和 OA 办公。",
                "如果单价明显低于 4000 或高于 5500，建议再次核对真实性。"
        ));
        configurations.add(assetConfiguration(
                "CFG-OFFICE-LAPTOP",
                "移动办公笔记本",
                "目录内",
                "便携式计算机",
                "Intel i5 或同档次 / 16G / 512G SSD / 14 英寸",
                5000,
                6500,
                Arrays.asList("联想 ThinkPad E14", "华为 MateBook B5", "惠普 ProBook 440"),
                "适用于外出办公、会议演示和通勤携带。",
                "建议结合便携性和续航，不要使用明显偏低的占位价格。"
        ));
        configurations.add(assetConfiguration(
                "CFG-HIGH-WORKSTATION",
                "高性能图形工作站",
                "目录外",
                "图形工作站",
                "Intel i7 或同档次 / 32G / 1T SSD / 独立显卡",
                11000,
                16000,
                Arrays.asList("戴尔 Precision", "联想 天逸工作站", "惠普 Z 系列"),
                "适用于三维设计、视频剪辑、建模渲染等高负载场景。",
                "若使用目录外配置，需在理由中说明目录内机型无法满足需求。"
        ));
        return configurations;
    }

    private List<Map<String, Object>> filterByField(List<Map<String, Object>> source,
                                                    List<String> supportedFields,
                                                    String queryField,
                                                    String queryValue) {
        if (isBlank(queryField) || isBlank(queryValue)) {
            return source;
        }
        String normalizedField = queryField.trim();
        if (!containsIgnoreCase(supportedFields, normalizedField)) {
            return new ArrayList<Map<String, Object>>();
        }
        String normalizedValue = queryValue.trim().toLowerCase(Locale.ROOT);
        List<Map<String, Object>> matched = new ArrayList<Map<String, Object>>();
        for (Map<String, Object> item : source) {
            if (fieldContains(item, normalizedField, normalizedValue)) {
                matched.add(item);
            }
        }
        return matched;
    }

    private boolean fieldContains(Map<String, Object> item, String queryField, String queryValueLower) {
        Object value = item.get(queryField);
        if (value == null) {
            return false;
        }
        if (value instanceof Iterable) {
            for (Object subValue : (Iterable<?>) value) {
                if (subValue != null && String.valueOf(subValue).toLowerCase(Locale.ROOT).contains(queryValueLower)) {
                    return true;
                }
            }
            return false;
        }
        return String.valueOf(value).toLowerCase(Locale.ROOT).contains(queryValueLower);
    }

    private boolean containsIgnoreCase(List<String> values, String target) {
        for (String value : values) {
            if (value != null && value.equalsIgnoreCase(target)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, Object> fieldGuide(String fieldKey,
                                           String label,
                                           String fieldType,
                                           boolean required,
                                           String meaning,
                                           String aiAdvice,
                                           List<String> allowedValues,
                                           List<String> constraints,
                                           String exampleValue) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("fieldKey", fieldKey);
        item.put("label", label);
        item.put("fieldType", fieldType);
        item.put("required", Boolean.valueOf(required));
        item.put("meaning", meaning);
        item.put("aiAdvice", aiAdvice);
        item.put("allowedValues", allowedValues == null ? new ArrayList<String>() : allowedValues);
        item.put("constraints", constraints);
        item.put("exampleValue", exampleValue);
        return item;
    }

    private Map<String, Object> historyItem(String historyId,
                                            String purchaseDate,
                                            String department,
                                            String itemName,
                                            String category,
                                            String brandModel,
                                            String specification,
                                            int quantity,
                                            int unitPrice,
                                            String supplier,
                                            String usageScene,
                                            String referenceNote) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("historyId", historyId);
        item.put("purchaseDate", purchaseDate);
        item.put("department", department);
        item.put("itemName", itemName);
        item.put("category", category);
        item.put("brandModel", brandModel);
        item.put("specification", specification);
        item.put("quantity", Integer.valueOf(quantity));
        item.put("unitPrice", Integer.valueOf(unitPrice));
        item.put("totalAmount", Integer.valueOf(quantity * unitPrice));
        item.put("supplier", supplier);
        item.put("usageScene", usageScene);
        item.put("referenceNote", referenceNote);
        return item;
    }

    private Map<String, Object> assetConfiguration(String configCode,
                                                   String scene,
                                                   String category,
                                                   String itemName,
                                                   String specificationBaseline,
                                                   int minPrice,
                                                   int maxPrice,
                                                   List<String> recommendedBrands,
                                                   String fitFor,
                                                   String priceAdvice) {
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("configCode", configCode);
        item.put("scene", scene);
        item.put("category", category);
        item.put("itemName", itemName);
        item.put("specificationBaseline", specificationBaseline);
        item.put("minPrice", Integer.valueOf(minPrice));
        item.put("maxPrice", Integer.valueOf(maxPrice));
        item.put("recommendedBrands", recommendedBrands);
        item.put("fitFor", fitFor);
        item.put("priceAdvice", priceAdvice);
        return item;
    }

    private boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
