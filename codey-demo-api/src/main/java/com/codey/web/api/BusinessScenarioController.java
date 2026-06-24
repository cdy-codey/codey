package com.codey.web.api;

import com.codey.web.common.ApiResponse;
import com.codey.web.service.BusinessScenarioService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 业务示例查询接口。
 * 当前先返回固定样例数据，供前端页面和 AI 自动填写流程联调。
 */
@RestController
@RequestMapping("/api/business-demo")
public class BusinessScenarioController {
    private final BusinessScenarioService businessScenarioService;

    public BusinessScenarioController(BusinessScenarioService businessScenarioService) {
        this.businessScenarioService = businessScenarioService;
    }

    @GetMapping("/procurement/context")
    public ApiResponse<BusinessScenarioService.ProcurementFormContext> getProcurementFormContext() {
        return ApiResponse.success(
                "采购申请示例上下文查询成功",
                businessScenarioService.getProcurementFormContext()
        );
    }
}
