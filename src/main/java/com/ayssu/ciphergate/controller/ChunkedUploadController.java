package com.ayssu.ciphergate.controller;

import com.ayssu.ciphergate.annotation.RequirePermission;
import com.ayssu.ciphergate.common.Result;
import com.ayssu.ciphergate.service.ChunkedUploadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@Tag(name = "分片上传", description = "支持大文件分片上传和断点续传")
public class ChunkedUploadController {

    private final ChunkedUploadService chunkedUploadService;

    @PostMapping("/init")
    @RequirePermission("FILE_UPLOAD")
    @Operation(summary = "初始化分片上传", description = "创建上传会话，返回uploadId和分片信息")
    public Result<ChunkedUploadService.InitUploadResponse> initUpload(
            @RequestBody ChunkedUploadService.InitUploadRequest request) {
        try {
            ChunkedUploadService.InitUploadResponse response = chunkedUploadService.initUpload(request);
            return Result.success("上传会话创建成功", response);
        } catch (Exception e) {
            log.error("初始化分片上传失败", e);
            return Result.error("初始化上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/chunk")
    @RequirePermission("FILE_UPLOAD")
    @Operation(summary = "上传分片", description = "上传单个分片文件")
    public Result<ChunkedUploadService.UploadChunkResponse> uploadChunk(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("chunkNumber") int chunkNumber,
            @RequestParam("file") MultipartFile file) {
        try {
            ChunkedUploadService.UploadChunkRequest request = 
                new ChunkedUploadService.UploadChunkRequest(uploadId, chunkNumber, file);
            ChunkedUploadService.UploadChunkResponse response = chunkedUploadService.uploadChunk(request);
            return Result.success("分片上传成功", response);
        } catch (Exception e) {
            log.error("分片上传失败: uploadId={}, chunkNumber={}", uploadId, chunkNumber, e);
            return Result.error("分片上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/complete")
    @RequirePermission("FILE_UPLOAD")
    @Operation(summary = "完成上传", description = "合并所有分片，完成文件上传")
    public Result<Void> completeUpload(@RequestBody ChunkedUploadService.CompleteUploadRequest request) {
        try {
            chunkedUploadService.completeUpload(request);
            return Result.success("文件上传完成", null);
        } catch (Exception e) {
            log.error("完成上传失败: uploadId={}", request.uploadId(), e);
            return Result.error("完成上传失败: " + e.getMessage());
        }
    }

    @GetMapping("/info/{uploadId}")
    @RequirePermission("FILE_UPLOAD")
    @Operation(summary = "查询上传进度", description = "获取分片上传的当前进度")
    public Result<ChunkedUploadService.UploadInfo> getUploadInfo(@PathVariable String uploadId) {
        try {
            ChunkedUploadService.UploadInfo info = chunkedUploadService.getUploadInfo(uploadId);
            if (info == null) {
                return Result.error("上传会话不存在或已过期");
            }
            return Result.success(info);
        } catch (Exception e) {
            log.error("查询上传进度失败: uploadId={}", uploadId, e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{uploadId}")
    @RequirePermission("FILE_UPLOAD")
    @Operation(summary = "取消上传", description = "取消分片上传并清理已上传的分片")
    public Result<Void> abortUpload(@PathVariable String uploadId) {
        try {
            chunkedUploadService.abortUpload(uploadId);
            return Result.success("上传已取消", null);
        } catch (Exception e) {
            log.error("取消上传失败: uploadId={}", uploadId, e);
            return Result.error("取消失败: " + e.getMessage());
        }
    }
}
