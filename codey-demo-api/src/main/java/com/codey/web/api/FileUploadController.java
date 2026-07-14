package com.codey.web.api;

import com.codey.web.common.ApiResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 附件上传接口。
 * 支持采购申请等业务场景上传需求附件，文件按日期分目录存储。
 */
@RestController
@RequestMapping("/api/business-demo")
public class FileUploadController {

    @Value("${upload.directory:./uploads}")
    private String uploadDir;

    /**
     * 上传附件。
     * 接收 multipart/form-data 中的 file 字段，保存到按日期分组的目录并返回文件元信息。
     */
    @PostMapping("/attachment/upload")
    public ApiResponse<Map<String, Object>> uploadAttachment(@RequestParam("file") MultipartFile file) throws IOException {
        // 校验文件不为空
        if (file.isEmpty()) {
            return ApiResponse.failure("BAD_REQUEST", "上传文件不能为空", null);
        }

        // 基于当前运行目录解析上传根目录，再按日期创建子目录
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Path uploadPath = basePath.resolve(dateDir);
        // 确保目录存在（包括所有父目录），不存在则逐级创建
        Files.createDirectories(uploadPath);

        // 生成唯一文件名，防止重名冲突
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String storedFilename = UUID.randomUUID().toString() + extension;

        // 保存文件到磁盘
        Path targetPath = uploadPath.resolve(storedFilename);
        file.transferTo(targetPath.toFile());

        // 返回文件元信息
        Map<String, Object> result = new HashMap<>();
        result.put("originalName", originalFilename);
        result.put("storedName", storedFilename);
        result.put("size", file.getSize());
        result.put("contentType", file.getContentType());
        result.put("uploadPath", targetPath.toString());
        result.put("uploadTime", new Date());

        return ApiResponse.success("附件上传成功", result);
    }
}
