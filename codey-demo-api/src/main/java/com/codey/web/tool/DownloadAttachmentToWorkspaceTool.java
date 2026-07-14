package com.codey.web.tool;

import com.codey.infra.WorkspacePathSupport;
import com.codey.meta.IdentityMatchMode;
import com.codey.tool.AbstractTool;
import com.codey.tool.ToolCapability;
import com.codey.tool.ToolContext;
import com.codey.tool.ToolDescriptor;
import com.codey.tool.ToolInvocation;
import com.codey.tool.ToolMetadata;
import com.codey.tool.ToolResult;
import com.codey.tools.WorkspaceToolContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

/**
 * 附件下载工具，将已上传的附件下载后转换为 TXT 格式放入工作目录，
 * 方便 AI 直接读取附件内容进行分析。
 */
@Component
public class DownloadAttachmentToWorkspaceTool extends AbstractTool {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadAttachmentToWorkspaceTool.class);

    @Value("${upload.directory:./uploads}")
    private String uploadDir;

    @Override
    public ToolDescriptor descriptor() {
        return new ToolDescriptor(
                "download_attachment_to_workspace",
                "下载附件到工作目录",
                "将已上传的附件文件读取并转换为 TXT 格式，保存到当前工作目录中，供 AI 读取分析。"
                        + "支持纯文本文件直接复制，以及常见二进制文件的内容提取。",
                buildParameters()
        );
    }

    @Override
    public ToolCapability capability() {
        // 该工具读取上传文件并写入工作目录，属于读写混合操作
        return ToolCapability.standard();
    }

    @Override
    public ToolMetadata metadata() {
        ToolMetadata metadata = ToolMetadata.standard();
        metadata.setSupportedIdentities(Arrays.asList("programming"));
        metadata.setIdentityMatchMode(IdentityMatchMode.ANY);
        metadata.setGroup("procurement");
        metadata.setBundle("procurement");
        return metadata;
    }

    @Override
    public ToolResult execute(ToolInvocation invocation, ToolContext context) {
        try {
            String filePath = requireString(invocation, "filePath");
            String outputFileName = readOptionalString(invocation, "outputFileName");

            // 解析源文件路径（上传目录中的文件）
            Path sourcePath = resolveSourcePath(filePath);

            // 校验源文件是否存在
            if (!Files.exists(sourcePath)) {
                return ToolResult.fail("附件文件不存在: " + sourcePath);
            }
            if (!Files.isRegularFile(sourcePath)) {
                return ToolResult.fail("路径不是有效文件: " + sourcePath);
            }

            // 读取文件内容并转换为文本
            String extractedText = extractTextContent(sourcePath);

            // 确定输出文件名
            if (outputFileName == null || outputFileName.trim().isEmpty()) {
                String sourceName = sourcePath.getFileName().toString();
                // 去掉原始扩展名，追加 .txt
                int dotIndex = sourceName.lastIndexOf('.');
                outputFileName = (dotIndex > 0 ? sourceName.substring(0, dotIndex) : sourceName) + ".txt";
            } else if (!outputFileName.endsWith(".txt")) {
                outputFileName = outputFileName + ".txt";
            }

            // 写入工作目录
            Path workspaceTargetPath = resolveWorkspacePath(outputFileName, context);
            Files.createDirectories(workspaceTargetPath.getParent());
            Files.write(workspaceTargetPath, extractedText.getBytes(StandardCharsets.UTF_8));

            LOGGER.info(
                    "Attachment downloaded to workspace: source={}, target={}, size={}",
                    sourcePath, workspaceTargetPath, extractedText.length()
            );

            // 组装返回结果
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sourcePath", sourcePath.toString());
            result.put("outputPath", workspaceTargetPath.toString());
            result.put("outputFileName", outputFileName);
            result.put("contentLength", extractedText.length());
            result.put("contentPreview", truncatePreview(extractedText, 500));

            return ToolResult.ok(
                    toPrettyJson(result),
                    "附件已下载并转换为 TXT 格式，保存到: " + outputFileName
            );
        } catch (IllegalArgumentException exception) {
            return ToolResult.fail("下载附件失败: " + exception.getMessage());
        } catch (Exception exception) {
            return ToolResult.fail("下载附件失败: " + exception.getMessage());
        }
    }

    /**
     * 解析上传目录中的源文件路径。
     * 支持绝对路径和相对路径（相对于 upload.directory）。
     */
    private Path resolveSourcePath(String filePath) {
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path.normalize();
        }
        // 相对路径：基于 upload.directory 解析
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        return basePath.resolve(filePath).normalize();
    }

    /**
     * 读取文件内容并提取文本。
     * 对于纯文本文件直接读取；对于二进制文件尝试常见编码解码。
     */
    private String extractTextContent(Path filePath) throws IOException {
        String fileName = filePath.getFileName().toString().toLowerCase();

        // 纯文本文件直接读取
        if (fileName.endsWith(".txt") || fileName.endsWith(".csv") || fileName.endsWith(".json")
                || fileName.endsWith(".xml") || fileName.endsWith(".html") || fileName.endsWith(".htm")
                || fileName.endsWith(".md") || fileName.endsWith(".log") || fileName.endsWith(".yml")
                || fileName.endsWith(".yaml") || fileName.endsWith(".properties")) {
            // 尝试 UTF-8 解码
            return readFileAsText(filePath);
        }

        // PDF 文件：使用 PDFBox 提取文本
        if (fileName.endsWith(".pdf")) {
            return extractPdfText(filePath);
        }

        // Word 文档：使用 POI 提取文本
        if (fileName.endsWith(".docx")) {
            return extractDocxText(filePath);
        }

        // Excel / PPT 暂不支持，提示用户
        if (fileName.endsWith(".xlsx") || fileName.endsWith(".pptx")) {
            return "[该文件为 Office 格式（" + getExtension(fileName) + "），Demo 环境暂不支持解析，"
                    + "请上传 TXT/CSV/JSON/DOCX/PDF 等格式。"
                    + "\n源文件路径: " + filePath + "]";
        }

        // 其他文件类型：尝试按文本读取
        try {
            return readFileAsText(filePath);
        } catch (IOException e) {
            return "[无法解析该文件类型的文本内容（" + getExtension(fileName) + "），"
                    + "文件可能为二进制格式。\n源文件路径: " + filePath + "]";
        }
    }

    /**
     * 以 UTF-8 解码文件内容，遇到乱码时回退到 GBK。
     */
    private String readFileAsText(Path filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(filePath);
        String content = new String(bytes, StandardCharsets.UTF_8);
        // UTF-8 解码后若包含替换字符（\uFFFD），说明编码不匹配，回退到 GBK
        if (content.indexOf('\uFFFD') >= 0) {
            content = new String(bytes, Charset.forName("GBK"));
        }
        return content;
    }

    /**
     * 使用 PDFBox 提取 PDF 文件文本内容。
     */
    private String extractPdfText(Path filePath) {
        PDDocument document = null;
        try {
            document = PDDocument.load(filePath.toFile());
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text == null || text.trim().isEmpty()) {
                return "[PDF 文件内容为空或无法提取文字。\n源文件路径: " + filePath + "]";
            }
            return text;
        } catch (Exception e) {
            LOGGER.warn("PDF text extraction failed: {}", filePath, e);
            return "[PDF 解析失败: " + e.getMessage()
                    + "\n源文件路径: " + filePath + "]";
        } finally {
            closeQuietly(document);
        }
    }

    /**
     * 使用 POI 提取 DOCX 文件文本内容。
     */
    private String extractDocxText(Path filePath) {
        InputStream inputStream = null;
        XWPFDocument document = null;
        XWPFWordExtractor extractor = null;
        try {
            inputStream = Files.newInputStream(filePath);
            document = new XWPFDocument(inputStream);
            extractor = new XWPFWordExtractor(document);
            String text = extractor.getText();
            if (text == null || text.trim().isEmpty()) {
                return "[DOCX 文件内容为空。\n源文件路径: " + filePath + "]";
            }
            return text;
        } catch (Exception e) {
            LOGGER.warn("DOCX text extraction failed: {}", filePath, e);
            return "[DOCX 解析失败: " + e.getMessage()
                    + "\n源文件路径: " + filePath + "]";
        } finally {
            closeQuietly(extractor);
            closeQuietly(document);
            closeQuietly(inputStream);
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 将输出文件路径解析到工作目录下。
     */
    private Path resolveWorkspacePath(String fileName, ToolContext context) {
        if (!(context instanceof WorkspaceToolContext)) {
            throw new IllegalStateException("workspace tool context is required");
        }
        WorkspaceToolContext workspaceContext = (WorkspaceToolContext) context;
        return WorkspacePathSupport.resolveToolPath(
                workspaceContext.getWorkspaceRoot(),
                context.getWorkingDirectory(),
                fileName
        );
    }

    /**
     * 截取文本预览，避免返回内容过长。
     */
    private String truncatePreview(String text, int maxLength) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength) + "...(共 " + text.length() + " 字符)";
    }

    private String getExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toUpperCase() : "未知";
    }

    private Map<String, Object> buildParameters() {
        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("filePath", stringProperty(
                "必填。附件文件的路径，支持绝对路径或相对于 upload 目录的相对路径（含日期子目录和文件名）。"
                        + "例如: 2026-07-10/uuid.pdf 或 /absolute/path/to/file.pdf"
        ));
        properties.put("outputFileName", stringProperty(
                "可选。输出 TXT 文件名（不含扩展名则自动追加 .txt），留空则使用原始文件名。"
        ));

        parameters.put("properties", properties);
        parameters.put("required", Arrays.asList("filePath"));
        parameters.put("additionalProperties", Boolean.FALSE);
        return parameters;
    }

    private String readOptionalString(ToolInvocation invocation, String key) {
        if (invocation == null || invocation.getArguments() == null) {
            return null;
        }
        Object value = invocation.getArguments().get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private Map<String, Object> stringProperty(String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", "string");
        property.put("description", description);
        return property;
    }

    private String toPrettyJson(Map<String, Object> payload) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload);
        } catch (Exception e) {
            return payload.toString();
        }
    }
}
