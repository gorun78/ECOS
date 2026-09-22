package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.ValidationException;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import com.chinacreator.gzcm.engine.data.dto.DataResourceVO;
import com.chinacreator.gzcm.engine.data.dto.FolderCollectDTO;
import com.chinacreator.gzcm.engine.data.dto.FolderCollectResult;
import com.chinacreator.gzcm.engine.data.dto.FolderFileVO;
import com.chinacreator.gzcm.engine.data.dto.UnstructuredRegisterDTO;
import com.chinacreator.gzcm.engine.data.dto.UnstructuredUploadDTO;
import com.chinacreator.gzcm.engine.data.service.DataLakeResourceService;
import com.chinacreator.gzcm.engine.data.service.DataSourceRegistryService;
import com.chinacreator.gzcm.engine.data.service.LakeObjectKeys;
import com.chinacreator.gzcm.runtime.access.storage.MinioStorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 数据湖（MinIO 近源库）初始化与状态 REST API。
 * <p>
 * 端点：
 * <ul>
 *   <li>GET  /api/v1/datanet/datalake/status          — 健康状态 + 配置（secretKey 脱敏）</li>
 *   <li>POST /api/v1/datanet/datalake/init            — 初始化：确保 bucket 存在</li>
 *   <li>GET  /api/v1/datanet/datalake/objects         — 列出数据湖对象（prefix 过滤，如 datalake/）</li>
 *   <li>POST /api/v1/datanet/datalake/unstructured    — 登记非结构化原文对象（B5-1，D5 生产者侧）</li>
 * </ul>
 * <p>对应需求：设置好数据湖 MinIO 初始参数，并作为数据采集（采集型管道）的默认近源库目标。
 */
@RestController
@RequestMapping("/api/v1/datanet/datalake")
public class DatalakeController {

    private static final Logger log = LoggerFactory.getLogger(DatalakeController.class);

    /** FILESYSTEM 数据源类型标识（与 DataSourceServiceImpl.SUPPORTED_TYPES 对齐）。 */
    private static final String DATASOURCE_TYPE_FILESYSTEM = "FILESYSTEM";

    /** 非结构化近源层允许采集的扩展名白名单（分层规范 §四 消费方约束）。 */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".pdf", ".docx", ".doc", ".txt", ".md");

    /** 单文件大小上限（50MB），超限该文件记为 failed。 */
    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;

    /** connectionConfig 中文件夹根目录候选键（前端 fs 连接保存为 rootPath 或 path）。 */
    private static final String[] ROOT_PATH_KEYS = {"rootPath", "path", "basePath"};

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final MinioStorageService minioStorageService;

    /** 数据湖分层资源登记服务（B5-1：非结构化原文登记）。 */
    private final DataLakeResourceService dataLakeResourceService;

    /** 数据源注册服务（FILESYSTEM 数据源读取，不落库）。 */
    private final DataSourceRegistryService dataSourceRegistryService;

    public DatalakeController(MinioStorageService minioStorageService,
                              DataLakeResourceService dataLakeResourceService,
                              DataSourceRegistryService dataSourceRegistryService) {
        this.minioStorageService = minioStorageService;
        this.dataLakeResourceService = dataLakeResourceService;
        this.dataSourceRegistryService = dataSourceRegistryService;
    }

    /**
     * 登记非结构化原文对象（数据湖存储分层规范 §三/§五/§六）。
     *
     * <p>组装对象 key {@code raw/unstructured/{source}/{docId}/{originalFileName}} 并登记
     * {@code td_data_resource}（layer=RAW, zone=UNSTRUCTURED, resource_type=LAKE_OBJECT）。
     *
     * @param req 登记入参（强类型）
     * @return 登记结果（ApiResponse&lt;DataResourceVO&gt;）
     */
    @PostMapping("/unstructured")
    public ApiResponse<DataResourceVO> registerUnstructured(@RequestBody UnstructuredRegisterDTO req) {
        try {
            return ApiResponse.success(dataLakeResourceService.registerUnstructured(req));
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
    }

    /**
     * 上传非结构化原文到近源层并登记（数据侧前端打通 step ②③）。
     *
     * <p>流程：校验入参 → 从 MultipartFile 取二进制 → 经 {@code LakeObjectKeys.unstructuredObjectKey}
     * 组装对象 key（{@code raw/unstructured/{source}/{docId}/{originalFileName}}，分层规范 §三）→
     * 经 runtime-access {@link MinioStorageService#putObject(String, byte[], String)} 写入默认数据湖 bucket →
     * 复用 {@code DataLakeResourceService.registerUnstructured} 登记 td_data_resource
     * （layer=RAW, zone=UNSTRUCTURED, resource_type=LAKE_OBJECT）。
     *
     * <p>安全：文件二进制走 multipart {@code @RequestPart("file")}（不走 base64，避免大文件内存膨胀）；
     * 受 Spring multipart 默认 10MB 限制约束（超限可调 {@code spring.servlet.multipart.max-file-size}，
     * 本端点不自行放宽）。上传与登记操作应走 §2.4 审计 Kafka {@code ecos.audit}（schema 见
     * docs/plans/api-contract.md §5.2），当前批次未实际发送，后续批次接入。
     *
     * @param resp  响应（预留，当前不使用）
     * @param req   上传元数据（source/docId 必填，originalFileName 可选）
     * @param file  文件二进制（multipart）
     * @return 登记结果（ApiResponse&lt;DataResourceVO&gt;）
     */
    @PostMapping(value = "/unstructured/upload", consumes = "multipart/form-data")
    public ApiResponse<DataResourceVO> uploadUnstructured(HttpServletResponse resp,
                                                          @RequestPart UnstructuredUploadDTO req,
                                                          @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.badRequest("file: 文件不能为空");
        }
        if (req == null || isBlank(req.getSource()) || isBlank(req.getDocId())) {
            return ApiResponse.badRequest("source/docId: 不能为空");
        }
        try {
            String fileName = !isBlank(req.getOriginalFileName())
                    ? req.getOriginalFileName() : file.getOriginalFilename();
            if (isBlank(fileName)) {
                return ApiResponse.badRequest("originalFileName: 文件名不能为空");
            }
            String objectKey = LakeObjectKeys.unstructuredObjectKey(req.getSource(), req.getDocId(), fileName);
            byte[] bytes = file.getBytes();
            String contentType = (file.getContentType() == null || file.getContentType().isBlank())
                    ? "application/octet-stream" : file.getContentType();
            Map<String, Object> uploadResult = minioStorageService.putObject(objectKey, bytes, contentType);
            Object status = uploadResult != null ? uploadResult.get("status") : null;
            if (!"success".equals(status)) {
                String message = uploadResult != null ? String.valueOf(uploadResult.get("message")) : "未知原因";
                return ApiResponse.badRequest("MinIO 上传失败: " + message);
            }
            UnstructuredRegisterDTO register = new UnstructuredRegisterDTO();
            register.setSource(req.getSource());
            register.setDocId(req.getDocId());
            register.setOriginalFileName(fileName);
            register.setResourceName(req.getResourceName());
            register.setDatasourceId(req.getDatasourceId());
            register.setSize((long) bytes.length);
            register.setContentType(contentType);
            DataResourceVO vo = dataLakeResourceService.registerUnstructured(register);
            return ApiResponse.success(vo);
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("非结构化上传失败: source={}, docId={}, err={}",
                    req.getSource(), req.getDocId(), e.getMessage(), e);
            return ApiResponse.error(ApiResponse.CODE_INTERNAL_ERROR, "上传失败: " + e.getMessage());
        }
    }

    /** 判空（null 或 trim 后为空），用于 multipart 文本字段校验。 */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 列出 FILESYSTEM 数据源根目录下的可采集文件（非结构化文件夹模式 step ①）。
     *
     * <p>流程：按 datasourceId 取数据源实体并校验 {@code FILESYSTEM} 类型 →
     * 从 {@code connectionConfig} 解析文件夹根目录（rootPath/path/basePath 优先）→
     * {@link Files#list(Path)} 只列一层文件，按扩展名白名单（.pdf/.docx/.doc/.txt/.md）过滤 →
     * 可选按 docId 前缀过滤文件名。目录不存在/不可读/配置缺根目录均返回 badRequest。
     *
     * <p>安全：不信任前端任何 path 参数，根目录只由数据源 connectionConfig 持有；
     * VO 仅暴露 name/size/lastModified，不返回服务端绝对路径（防路径探测）。
     *
     * @param datasourceId FILESYSTEM 数据源 ID（必填）
     * @param docId        可选文件名前缀过滤（可选）
     * @return 文件列表（ApiResponse&lt;List&lt;FolderFileVO&gt;&gt;，按文件名排序）
     */
    @GetMapping("/unstructured/folder/files")
    public ApiResponse<List<FolderFileVO>> listFolderFiles(
            @RequestParam String datasourceId,
            @RequestParam(required = false) String docId) {
        if (isBlank(datasourceId)) {
            return ApiResponse.badRequest("datasourceId: 不能为空");
        }
        DataSourceEntity ds = dataSourceRegistryService.getById(datasourceId.trim());
        if (ds == null) {
            return ApiResponse.badRequest("数据源不存在: " + datasourceId);
        }
        if (!DATASOURCE_TYPE_FILESYSTEM.equals(ds.getDatasourceType())) {
            return ApiResponse.badRequest("数据源类型不是 FILESYSTEM");
        }
        Path root;
        try {
            root = resolveRootDir(ds.getConnectionConfig());
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
        if (!Files.exists(root) || !Files.isDirectory(root) || !Files.isReadable(root)) {
            return ApiResponse.badRequest("目录不存在或不可读");
        }
        String prefix = isBlank(docId) ? null : docId.trim();
        try (Stream<Path> stream = Files.list(root)) {
            List<FolderFileVO> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> prefix == null
                            || p.getFileName().toString().startsWith(prefix))
                    .filter(p -> isAllowedExtension(p.getFileName().toString()))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .map(this::toFolderFileVO)
                    .toList();
            return ApiResponse.success(files);
        } catch (IOException e) {
            log.error("列出文件夹数据源失败：datasourceId={}, err={}", datasourceId, e.getMessage(), e);
            return ApiResponse.error(ApiResponse.CODE_INTERNAL_ERROR, "读取目录失败: " + e.getMessage());
        }
    }

    /**
     * 将 FILESYSTEM 数据源目录中的文件采集到非结构化近源层并登记（文件夹模式 step ②③）。
     *
     * <p>逐文件处理（单文件失败不中断）：扩展名白名单校验 →
     * {@code rootPath.resolve(name).normalize()} 后 toRealPath 前缀校验（防 {@code ../} 越权）→
     * 50MB 大小上限 → {@code Files.readAllBytes} →
     * 经 runtime-access {@link MinioStorageService#putObject(String, byte[], String)} 写入
     * {@code raw/unstructured/{datasourceId}/{docId}/{fileName}}（LakeObjectKeys 规范 §三）→
     * 复用 {@code DataLakeResourceService.registerUnstructured} 登记
     * （layer=RAW, zone=UNSTRUCTURED, resource_type=LAKE_OBJECT）。
     *
     * <p>安全：不信任前端 path 参数，根目录只由数据源 connectionConfig 持有；
     * 越权路径跳过该文件并 log.warn，记入结果 items（status=FAILED）。
     *
     * @param req 采集入参（datasourceId/docId/fileNames 均必填，强类型 DTO）
     * @return 采集结果（ApiResponse&lt;FolderCollectResult&gt;：collected/failed/items 明细）
     */
    @PostMapping("/unstructured/collect")
    public ApiResponse<FolderCollectResult> collectFolderFiles(@RequestBody FolderCollectDTO req) {
        if (req == null || isBlank(req.getDatasourceId()) || isBlank(req.getDocId())
                || req.getFileNames() == null || req.getFileNames().isEmpty()) {
            return ApiResponse.badRequest("datasourceId/docId/fileNames: 不能为空");
        }
        String datasourceId = req.getDatasourceId().trim();
        String docId = req.getDocId().trim();
        DataSourceEntity ds = dataSourceRegistryService.getById(datasourceId);
        if (ds == null) {
            return ApiResponse.badRequest("数据源不存在: " + datasourceId);
        }
        if (!DATASOURCE_TYPE_FILESYSTEM.equals(ds.getDatasourceType())) {
            return ApiResponse.badRequest("数据源类型不是 FILESYSTEM");
        }
        Path root;
        try {
            root = resolveRootDir(ds.getConnectionConfig());
        } catch (ValidationException e) {
            return ApiResponse.badRequest(e.getMessage());
        }
        if (!Files.exists(root) || !Files.isDirectory(root) || !Files.isReadable(root)) {
            return ApiResponse.badRequest("目录不存在或不可读");
        }
        Path realRoot;
        try {
            realRoot = root.toRealPath();
        } catch (IOException e) {
            log.error("解析文件夹根目录失败：datasourceId={}, err={}", datasourceId, e.getMessage(), e);
            return ApiResponse.badRequest("目录不存在或不可读");
        }

        FolderCollectResult result = new FolderCollectResult();
        for (String rawName : req.getFileNames()) {
            String fileName = rawName == null ? "" : rawName.trim();
            result.getItems().add(collectOneFile(realRoot, datasourceId, docId, fileName));
        }
        long collected = result.getItems().stream()
                .filter(i -> "SUCCESS".equals(i.getStatus())).count();
        result.setCollected((int) collected);
        result.setFailed(result.getItems().size() - (int) collected);
        return ApiResponse.success(result);
    }

    /**
     * 采集单个文件到近源层：越权/扩展名/尺寸逐层校验，失败记入 item 并继续。
     *
     * @param realRoot     已解析的真实根目录（toRealPath）
     * @param datasourceId 数据源 ID（对象 key 第二段）
     * @param docId        文档 ID（对象 key 第三段）
     * @param fileName     文件名（仅文件名，不含路径分隔符）
     * @return 单文件结果（SUCCESS/FAILED + error）
     */
    private FolderCollectResult.CollectItem collectOneFile(Path realRoot, String datasourceId,
                                                           String docId, String fileName) {
        if (fileName.isEmpty()) {
            return FolderCollectResult.CollectItem.of("", "FAILED", "文件名为空");
        }
        // 防越权：仅允许纯文件名（不含路径分隔符 + normalize 后仍位于根目录内）
        if (fileName.contains("/") || fileName.contains("\\")) {
            log.warn("非法文件名（含路径分隔符），跳过：docId={}, name={}", docId, fileName);
            return FolderCollectResult.CollectItem.of(fileName, "FAILED", "非法文件名（不允许路径穿越）");
        }
        Path target = realRoot.resolve(fileName).normalize();
        if (!target.startsWith(realRoot)) {
            log.warn("文件路径越出根目录，跳过：docId={}, name={}", docId, fileName);
            return FolderCollectResult.CollectItem.of(fileName, "FAILED", "非法文件路径（不允许路径穿越）");
        }
        if (!Files.isRegularFile(target)) {
            return FolderCollectResult.CollectItem.of(fileName, "FAILED", "文件不存在或不是普通文件");
        }
        if (!isAllowedExtension(fileName)) {
            return FolderCollectResult.CollectItem.of(fileName, "FAILED", "扩展名不在白名单（.pdf/.docx/.doc/.txt/.md）");
        }
        try {
            long size = Files.size(target);
            if (size > MAX_FILE_SIZE_BYTES) {
                log.warn("文件超过大小上限，跳过：docId={}, name={}, size={}MB",
                        docId, fileName, size / 1024 / 1024);
                return FolderCollectResult.CollectItem.of(fileName, "FAILED",
                        "文件超过 " + (MAX_FILE_SIZE_BYTES / 1024 / 1024) + "MB 上限");
            }
            byte[] bytes = Files.readAllBytes(target);
            String contentType = contentTypeOf(fileName);
            String objectKey = LakeObjectKeys.unstructuredObjectKey(datasourceId, docId, fileName);
            Map<String, Object> upload = minioStorageService.putObject(objectKey, bytes, contentType);
            Object status = upload != null ? upload.get("status") : null;
            if (!"success".equals(status)) {
                String message = upload != null ? String.valueOf(upload.get("message")) : "未知原因";
                return FolderCollectResult.CollectItem.of(fileName, "FAILED", "MinIO 写入失败: " + message);
            }
            UnstructuredRegisterDTO register = new UnstructuredRegisterDTO();
            register.setSource(datasourceId);
            register.setDocId(docId);
            register.setOriginalFileName(fileName);
            register.setDatasourceId(datasourceId);
            register.setSize(size);
            register.setContentType(contentType);
            dataLakeResourceService.registerUnstructured(register);
            return FolderCollectResult.CollectItem.of(fileName, "SUCCESS", null);
        } catch (ValidationException e) {
            log.warn("近源层登记失败：docId={}, name={}, err={}", docId, fileName, e.getMessage());
            return FolderCollectResult.CollectItem.of(fileName, "FAILED", e.getMessage());
        } catch (Exception e) {
            log.warn("文件采集失败：docId={}, name={}, err={}", docId, fileName, e.getMessage());
            return FolderCollectResult.CollectItem.of(fileName, "FAILED", "采集失败: " + e.getMessage());
        }
    }

    /**
     * 从 FILESYSTEM 数据源 connectionConfig JSON 解析文件夹根目录。
     *
     * @param connectionConfig connectionConfig JSON 原文
     * @return 绝对规范化根目录 Path
     * @throws ValidationException 配置缺失、JSON 非法或根目录键为空
     */
    private Path resolveRootDir(String connectionConfig) {
        if (isBlank(connectionConfig)) {
            throw new ValidationException("connectionConfig 缺失：数据源连接配置为空，无法定位文件目录");
        }
        try {
            JsonNode json = OBJECT_MAPPER.readTree(connectionConfig);
            for (String key : ROOT_PATH_KEYS) {
                JsonNode node = json.get(key);
                if (node != null && !node.asText().isBlank()) {
                    return Paths.get(node.asText()).toAbsolutePath().normalize();
                }
            }
            throw new ValidationException("FILESYSTEM 数据源缺少根目录配置（rootPath/path/basePath）");
        } catch (ValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new ValidationException("连接配置 JSON 解析失败: " + e.getMessage());
        }
    }

    /** 扩展名白名单校验（小写后缀匹配）。 */
    private static boolean isAllowedExtension(String fileName) {
        String name = fileName.toLowerCase();
        for (String ext : ALLOWED_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    /** 按扩展名推断 Content-Type（未知类型回退 application/octet-stream）。 */
    private static String contentTypeOf(String fileName) {
        String name = fileName.toLowerCase();
        if (name.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (name.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (name.endsWith(".doc")) {
            return "application/msword";
        }
        if (name.endsWith(".txt")) {
            return "text/plain";
        }
        if (name.endsWith(".md")) {
            return "text/markdown";
        }
        return "application/octet-stream";
    }

    /** 文件 Path → FolderFileVO（size 读取失败回退 0，时间戳取文件最后修改时刻）。 */
    private FolderFileVO toFolderFileVO(Path file) {
        FolderFileVO vo = new FolderFileVO();
        vo.setName(file.getFileName().toString());
        try {
            vo.setSize(Files.size(file));
            vo.setLastModified(Instant.ofEpochMilli(Files.getLastModifiedTime(file).toMillis()).toString());
        } catch (IOException e) {
            log.warn("读取文件元数据失败（记 0 值）：name={}, err={}", file.getFileName(), e.getMessage());
            vo.setSize(0L);
        }
        return vo;
    }

    /**
     * 数据湖健康状态（secretKey 脱敏，不暴露凭据）。
     */
    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        Map<String, Object> health = minioStorageService.healthCheck();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("endpoint", health.get("endpoint"));
        data.put("bucket", health.get("bucket"));
        data.put("status", health.get("status"));
        data.put("initialized", health.get("initialized"));
        data.put("accessKey", maskSecret(String.valueOf(minioStorageService.getAccessKey())));
        if (health.containsKey("message")) {
            data.put("message", health.get("message"));
        }
        return ApiResponse.success(data);
    }

    /**
     * 初始化数据湖：确保 bucket 存在（幂等）。
     */
    @PostMapping("/init")
    public ApiResponse<Map<String, Object>> init() {
        minioStorageService.ensureBucket();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bucket", minioStorageService.getBucketName());
        data.put("endpoint", minioStorageService.getEndpoint());
        data.put("status", minioStorageService.healthCheck().get("status"));
        return ApiResponse.success(data);
    }

    /**
     * 列出数据湖对象（默认列全部，可用 prefix 过滤）。
     */
    @GetMapping("/objects")
    public ApiResponse<Map<String, Object>> listObjects(@RequestParam(defaultValue = "") String prefix) {
        List<Map<String, Object>> items = minioStorageService.listObjects(prefix);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bucket", minioStorageService.getBucketName());
        data.put("prefix", prefix);
        data.put("items", items);
        data.put("total", items.size());
        return ApiResponse.success(data);
    }

    /** 脱敏：保留前 3 位，其余打码。 */
    private String maskSecret(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        if (value.length() <= 3) {
            return "***";
        }
        return value.substring(0, 3) + "***";
    }
}
