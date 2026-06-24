package com.codey.console.cli;

import com.codey.console.common.ConsoleIo;
import com.codey.infra.ModelVerificationResult;
import com.codey.infra.ModelVerificationService;

/**
 * 使用 console 输出流打印模型校验结果。
 */
public  class CliModelVerificationRunner {
    private final ModelVerificationService verificationService;

    public  CliModelVerificationRunner(ModelVerificationService verificationService) {
        this.verificationService = verificationService;
    }

   public int run(String verifyPrompt) {
        ModelVerificationResult result = verificationService.verify(verifyPrompt);
        if (result.isSuccess()) {
            ConsoleIo.out().println("模型验证结果: 通过");
            ConsoleIo.out().println("说明: " + safe(result.getMessage()));
            ConsoleIo.out().println("模型输出: " + safe(result.getRawOutput()));
            return 0;
        }

        ConsoleIo.err().println("模型验证结果: 失败");
        ConsoleIo.err().println("说明: " + safe(result.getMessage()));
        if (!safe(result.getRawOutput()).isEmpty()) {
            ConsoleIo.err().println("模型输出: " + result.getRawOutput());
        }
        return 1;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
