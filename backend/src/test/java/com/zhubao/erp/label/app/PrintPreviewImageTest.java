package com.zhubao.erp.label.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.LabelPrintImage;
import com.zhubao.erp.label.domain.PrintQueue;
import com.zhubao.erp.label.domain.PrintStation;
import com.zhubao.erp.label.domain.PrintTarget;
import com.zhubao.erp.label.infra.InMemoryLabelTemplateRepository;
import com.zhubao.erp.label.infra.InMemoryPrintJobRepository;
import com.zhubao.erp.label.infra.InMemoryPrintRoutingRepository;
import com.zhubao.erp.label.web.PrintRoutingController;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrintPreviewImageTest {
    private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
    private final LabelService labels = new LabelService(documents, new InMemoryLabelTemplateRepository());
    private final InMemoryPrintRoutingRepository repository = new InMemoryPrintRoutingRepository();
    private final PrintJobService jobs = new PrintJobService(new InMemoryPrintJobRepository());
    private final PrintBatchService service = new PrintBatchService(repository, jobs);
    private final AuthUserAccount account = account("operator");
    private final List<PrintTarget> targets = List.of(new PrintTarget("A", "Q1"));
    private final ObjectMapper mapper = new ObjectMapper();

    PrintPreviewImageTest() {
        documents.saveMotherOrder(mother("旧要求"));
        jobs.setDocumentAccess(documents, null);
        jobs.setLabelService(labels);
        service.saveStation(new PrintStation("A", "收货", true, "", ""));
        service.saveStation(new PrintStation("B", "包装", true, "", ""));
        service.saveQueue(new PrintQueue("A", "Q1", "主队列", "ALL", 40, 30, true, true));
        service.saveQueue(new PrintQueue("B", "Q2", "备用队列", "ALL", 40, 30, true, true));
    }

    @Test
    void 画面字节冻结到批次且领取改派保持原图() throws Exception {
        LabelPrintImage image = image();
        var batch = service.createFromPreview("preview-original-01", "MD-PREVIEW", "FLOW", 2, targets, account, image);
        var snapshot = mapper.readTree(batch.contentSnapshot());
        assertThat(snapshot.path("previewImage").path("pngBase64").asText()).isEqualTo(image.pngBase64());
        assertThat(snapshot.path("snapshotHash").asText()).isEqualTo(image.snapshotHash());
        var moved = service.reroute(batch.jobs().get(0).id(), "preview-reroute-01", new PrintTarget("B", "Q2"), "换工位", account);
        assertThat(moved.contentSnapshot()).isEqualTo(batch.contentSnapshot());
        String token = service.rotate("B").token();
        var controller = new PrintRoutingController(service, null, null, null);
        assertThatThrownBy(() -> controller.claim("B", token, ""))
                .isInstanceOf(BusinessException.class).hasMessageContaining("版本过旧");
        assertThat(repository.job(moved.jobs().get(0).id()).orElseThrow().status().name()).isEqualTo("PENDING");
        assertThat(controller.claim("B", token, "PNG/1").data().contentSnapshot()).isEqualTo(batch.contentSnapshot());
    }

    @Test
    void 新请求拒绝过期预览但原请求在单据变化后仍可回放() throws Exception {
        LabelPrintImage image = image();
        var batch = service.createFromPreview("preview-replay-01", "MD-PREVIEW", "FLOW", 1, targets, account, image);
        documents.saveMotherOrder(mother("新要求"));
        assertThat(service.createFromPreview("preview-replay-01", "MD-PREVIEW", "FLOW", 1, targets, account, image).id())
                .isEqualTo(batch.id());
        assertThatThrownBy(() -> service.createFromPreview("preview-stale-01", "MD-PREVIEW", "FLOW", 1, targets, account, image))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已变化");
        assertThatThrownBy(() -> service.createFromPreview("preview-replay-01", "MD-PREVIEW", "FLOW", 2, targets, account, image))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不一致");
        assertThatThrownBy(() -> service.createFromPreview("preview-replay-01", null, "FLOW", 1, targets, account, image))
                .isInstanceOf(BusinessException.class).hasMessageContaining("单号");
        LabelPrintImage changed = new LabelPrintImage(image.snapshotHash(), png(472, 354, 0xff112233));
        assertThatThrownBy(() -> service.createFromPreview("preview-replay-01", "MD-PREVIEW", "FLOW", 1, targets, account, changed))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不一致");
        assertThatThrownBy(() -> service.createFromPreview("preview-replay-01", "MD-PREVIEW", "FLOW", 1, targets, account("other"), image))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(repository.batchByRequest("preview-stale-01")).isEmpty();
    }

    @Test
    void 拒绝缺图损坏图片和不匹配的纸张像素() throws Exception {
        assertThatThrownBy(() -> service.createFromPreview("preview-missing-01", "MD-PREVIEW", "FLOW", 1, targets, account, null))
                .isInstanceOf(BusinessException.class).hasMessageContaining("预览");
        String hash = image().snapshotHash();
        for (String bad : List.of("不是图片", "YWJj", png(40, 30, 0xff000000))) {
            assertThatThrownBy(() -> service.createFromPreview("preview-invalid-01", "MD-PREVIEW", "FLOW", 1, targets, account,
                    new LabelPrintImage(hash, bad))).isInstanceOf(BusinessException.class).hasMessageContaining("预览");
        }
        assertThat(repository.batchByRequest("preview-invalid-01")).isEmpty();
    }

    private LabelPrintImage image() throws Exception {
        return new LabelPrintImage(labels.generateMotherLabel("MD-PREVIEW").snapshotHash(), png(472, 354, 0xff000000));
    }

    private String png(int width, int height, int color) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(10, 10, color);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private MotherOrderRecord mother(String requirement) {
        return new MotherOrderRecord("MD-PREVIEW", 8L, 8L, "AU9999", "2026-10-29", "", "", "", "", "", "黄金原料",
                "CUSTOMER", true, "WH01", BigDecimal.ONE, "件", BigDecimal.ONE, "IN_STOCK", "LG001", null, null, null,
                "operator", "EV001", null, "ACTIVE", "2026-10-29 10:00", requirement, "2026-10-30", "恒信珠宝");
    }

    private static AuthUserAccount account(String username) {
        return new AuthUserAccount(username, "hash", "操作员", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL,
                true, false, Instant.now(), 0, null, List.of(), false, false, List.of());
    }
}
