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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

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
                "下载附件",
                "将已上传的附件文件读取并转换为文本，同时保存到当前工作目录中。"
                        + "返回结果的 content 字段即为附件全文，可直接读取使用，无需再单独读取文件。"
                        + "支持纯文本文件直接复制，以及常见二进制文件的内容提取。",
                buildParameters()
        );
    }

    @Override
    public ToolCapability capability() {
        // 本质是“读取附件并转存文本”，只产生派生缓存，不修改用户表单/源文件，按只读处理、无需人工确认。
        return ToolCapability.readOnlyParallel();
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

            // 组装返回结果：直接返回全文，避免 AI 再读一次文件，加快读取速度。
            // 不再重复返回 contentPreview：content 已是全文，重复字段只会放大模型输入体积、拖慢生成。
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("sourcePath", sourcePath.toString());
            result.put("outputPath", workspaceTargetPath.toString());
            result.put("outputFileName", outputFileName);
            result.put("contentLength", extractedText.length());
            result.put("content", extractedText);

            return ToolResult.ok(
                    toPrettyJson(result),
                    "附件已读取并转换为文本（content 字段为全文），同时保存到: " + outputFileName
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
     * 解压 DOCX（本质是 ZIP）并读取 word/document.xml 提取文本，
     * 避免 POI 对部分 docx 解析卡死。
     */
    private String extractDocxText(Path filePath) {
        try (ZipFile zipFile = new ZipFile(filePath.toFile())) {
            ZipEntry entry = zipFile.getEntry("word/document.xml");
            if (entry == null) {
                return "[DOCX 缺少 word/document.xml。\n源文件路径: " + filePath + "]";
            }
            try (InputStream in = zipFile.getInputStream(entry)) {
                String xml = readAllText(in);
                return docxXmlToText(xml);
            }
        } catch (Exception e) {
            LOGGER.warn("DOCX text extraction failed: {}", filePath, e);
            return "[DOCX 解析失败: " + e.getMessage()
                    + "\n源文件路径: " + filePath + "]";
        }
    }

    /**
     * 读取输入流全部文本（Java 8 兼容，避免使用 Java 9 才有的 readAllBytes）。
     */
    private String readAllText(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /**
     * 将 docx 的 word/document.xml 转为纯文本：单元格用制表符分隔、表格行用换行分隔。
     * 顺序：先标记行/单元格边界，再标记段落，避免表格行之间粘连导致明细行错位。
     */
    private String docxXmlToText(String xml) {
        String text = xml;
        // 单元格内的制表符（技术参数项之间）用换行表达，避免与“列分隔制表符”混淆导致字段错位。
        text = text.replaceAll("<w:tab[^>]*/>", "\n");
        text = text.replaceAll("<w:br[^>]*/>", "\n");
        // 表格行结束必须换行，否则多行明细会全部挤在同一行，导致“提取不完整/字段错乱”。
        text = text.replaceAll("</w:tr>", "\n");
        // 单元格结束用制表符作为“列分隔符”。
        text = text.replaceAll("</w:tc>", "\t");
        text = text.replaceAll("</w:p>", "\n");
        text = text.replaceAll("<[^>]+>", "");
        text = text.replace("&lt;", "<").replace("&gt;", ">")
                   .replace("&quot;", "\"").replace("&apos;", "'")
                   .replace("&amp;", "&");
        return normalizeWhitespace(text);
    }

    /**
     * 规整提取后的空白：单元格内段落换行与单元格/行分隔符重叠时，只保留分隔符，
     * 避免每个字段被拆成多行、字段错位；同时压缩连续空行与多余空格，保留制表符分隔。
     */
    private String normalizeWhitespace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String normalized = text
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                // 单元格内容后的段落换行紧跟单元格分隔符 → 只保留 tab（字段不被拆行）
                .replace("\n\t", "\t")
                // 行末单元格分隔符紧跟换行 → 只保留换行
                .replace("\t\n", "\n");
        // 压缩连续空行，保留单行换行与制表符分隔。
        normalized = normalized.replaceAll("\n{3,}", "\n\n");
        // 压缩行内连续空格，保留制表符。
        normalized = normalized.replaceAll(" {2,}", " ");
        return normalized.trim();
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
