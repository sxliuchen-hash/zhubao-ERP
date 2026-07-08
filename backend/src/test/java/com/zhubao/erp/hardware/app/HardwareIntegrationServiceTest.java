package com.zhubao.erp.hardware.app;

import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.hardware.domain.HardwareDeviceStatus;
import com.zhubao.erp.hardware.infra.InMemoryHardwarePocRecordRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HardwareIntegrationServiceTest {

    @Test
    void exposesHardwareFallbackAndHealthBaseline() {
        HardwareIntegrationService service = new HardwareIntegrationService(
                new InMemoryHardwarePocRecordRepository(),
                new InMemoryDocumentManagementRepository()
        );

        assertThat(service.findDevices()).hasSize(5);
        assertThat(service.fallbackRequiredCount()).isEqualTo(4);
        assertThat(service.averageHealth()).isEqualTo(70);
        assertThat(service.findDevices())
                .anySatisfy(device -> {
                    assertThat(device.code()).isEqualTo("SCAN-PC-01");
                    assertThat(device.status()).isEqualTo(HardwareDeviceStatus.ONLINE);
                });
        assertThat(service.findTasks())
                .anySatisfy(task -> assertThat(task.nextAction()).contains("人工上传"));
        assertThat(service.findAdapterPlans()).hasSize(5);
        assertThat(service.findAdapterPlans())
                .anySatisfy(plan -> {
                    assertThat(plan.deviceCode()).isEqualTo("SCALE-01");
                    assertThat(plan.primaryAdapter()).contains("人工读数");
                    assertThat(plan.fallbackAdapter()).contains("称重照片");
                    assertThat(plan.nextIntegrationAction()).contains("串口/TCP");
                    assertThat(plan.tone()).isEqualTo("red");
                });
        assertThat(service.findProtocolSamples()).hasSize(5);
        assertThat(service.findProtocolSamples())
                .anySatisfy(sample -> {
                    assertThat(sample.deviceCode()).isEqualTo("SCALE-01");
                    assertThat(sample.protocolType()).contains("RS232", "USB", "TCP");
                    assertThat(sample.requiredParameters()).contains("baudRate");
                    assertThat(sample.samplePayload()).contains("ST,GS");
                    assertThat(sample.tone()).isEqualTo("red");
                });
        assertThat(service.findProtocolReviews()).hasSize(5);
        assertThat(service.findProtocolReviews())
                .anySatisfy(review -> {
                    assertThat(review.deviceCode()).isEqualTo("SCALE-01");
                    assertThat(review.deviceModelStatus()).contains("待冻结");
                    assertThat(review.pendingParameters()).contains("interfaceType", "baudRate", "dataBits");
                    assertThat(review.blocker()).contains("RS232/USB/TCP");
                    assertThat(review.tone()).isEqualTo("red");
                });
        assertThat(service.findProtocolExecutions()).hasSize(5);
        assertThat(service.findProtocolExecutions())
                .anySatisfy(execution -> {
                    assertThat(execution.deviceCode()).isEqualTo("SCALE-01");
                    assertThat(execution.executionStatus()).isEqualTo("阻断");
                    assertThat(execution.modelFreezeStatus()).isEqualTo("未冻结");
                    assertThat(execution.failedParameters()).contains("interfaceType", "baudRate");
                    assertThat(execution.evidenceStatus()).contains("原始报文缺失");
                    assertThat(execution.tone()).isEqualTo("red");
                });
        assertThat(service.findPocRecords()).hasSize(4);
        assertThat(service.blockedPocCount()).isEqualTo(1);
        assertThat(service.findPocRecords())
                .anySatisfy(record -> {
                    assertThat(record.code()).isEqualTo("POC-SCAN-20260706-001");
                    assertThat(record.nextAction()).contains("弱光 POC");
                });
        assertThat(service.findPocEvidence()).hasSize(4);
        assertThat(service.findPocEvidence())
                .anySatisfy(evidence -> {
                    assertThat(evidence.pocCode()).isEqualTo("POC-SCAN-20260706-001");
                    assertThat(evidence.documentCode()).isEqualTo("DOC-HW-POC-20260706-001");
                    assertThat(evidence.attachmentCodes()).contains("ATT-HW-20260706-001");
                    assertThat(evidence.status()).isEqualTo("已关联证据");
                })
                .anySatisfy(evidence -> {
                    assertThat(evidence.pocCode()).isEqualTo("POC-VID-20260706-001");
                    assertThat(evidence.status()).isEqualTo("待补证据");
                    assertThat(evidence.missingEvidence()).isEqualTo(1);
                });
    }
}
