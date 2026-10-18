package com.zhubao.erp.document.infra;

import com.zhubao.erp.common.error.BusinessException;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 在受限时间内提取首帧，派生失败不改变视频原件。 */
final class VideoPosterGenerator {

    private static final long TIMEOUT_SECONDS = 15;
    private static final long MAX_POSTER_BYTES = 2 * 1024 * 1024;

    private VideoPosterGenerator() {
    }

    static void write(Path source, Path target) {
        write(source, target, "ffmpeg");
    }

    static void write(Path source, Path target, String executable) {
        Path errorLog = null;
        try {
            errorLog = Files.createTempFile(target.getParent(), ".poster-error-", ".log");
            Process process = new ProcessBuilder(List.of(
                    executable, "-nostdin", "-hide_banner", "-loglevel", "error",
                    "-threads", "1", "-filter_threads", "1", "-protocol_whitelist", "file",
                    "-i", source.toString(), "-frames:v", "1",
                    "-vf", "scale=320:320:force_original_aspect_ratio=decrease",
                    "-q:v", "5", "-y", target.toString()
            )).redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(errorLog.toFile()).start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                throw new BusinessException("视频封面提取超时");
            }
            if (process.exitValue() != 0 || !Files.isRegularFile(target)
                    || Files.size(target) == 0 || Files.size(target) > MAX_POSTER_BYTES
                    || ImageIO.read(target.toFile()) == null) {
                throw new BusinessException("视频封面无法从原件提取");
            }
        } catch (IOException exception) {
            throw new BusinessException("视频封面提取失败：" + exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("视频封面提取已中断");
        } finally {
            if (errorLog != null) {
                try {
                    Files.deleteIfExists(errorLog);
                } catch (IOException ignored) {
                    // 临时错误日志留待存储对账，不覆盖原始失败。
                }
            }
        }
    }
}
