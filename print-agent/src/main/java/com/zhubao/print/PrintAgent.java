package com.zhubao.print;

import javax.print.Doc;
import javax.print.DocFlavor;
import javax.print.DocPrintJob;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.SimpleDoc;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.standard.Copies;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.Map;
import java.util.HashMap;

/**
 * 厂内打印程序。主动向 ERP 领取任务，提交到本机 Windows 打印队列。
 * 成功只表示已送入队列，不表示标签已经出纸。
 */
public final class PrintAgent {

    public static void main(String[] args) throws Exception {
        Properties config = new Properties();
        Path path = Path.of(args.length > 0 ? args[0] : "print-agent.properties");
        if (!Files.exists(path)) {
            System.err.println("缺少配置文件：" + path.toAbsolutePath());
            System.exit(2);
        }
        try (var input = Files.newInputStream(path)) {
            config.load(input);
        }
        String baseUrl = required(config, "baseUrl");
        String stationId = required(config, "stationId");
        String token = required(config, "token");
        Map<String, String> queues = queueMappings(config);
        if (!baseUrl.startsWith("https://")) {
            System.err.println("只允许连接 HTTPS 地址");
            System.exit(2);
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        for (Map.Entry<String, String> queue : queues.entrySet()) {
            if (findQueue(queue.getValue()) == null) {
                System.err.println("本机缺少已配置队列：" + queue.getKey() + " → " + queue.getValue());
            }
        }
        System.out.println("打印工位已启动：" + stationId + "，已配置 " + queues.size() + " 条队列映射");
        while (!Thread.currentThread().isInterrupted()) {
            poll(client, baseUrl, stationId, token, queues);
            Thread.sleep(3000);
        }
    }

    static void poll(HttpClient client, String baseUrl, String stationId, String token, Map<String, String> queues) {
        try {
            HttpRequest claim = HttpRequest.newBuilder(URI.create(baseUrl + "/api/label/agents/v2/jobs/claim"))
                    .timeout(Duration.ofSeconds(20))
                    .header("X-Print-Station", stationId)
                    .header("X-Print-Token", token)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> response = client.send(claim, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 422 || response.body().contains("没有待打印任务")) {
                return;
            }
            if (response.statusCode() >= 400) {
                System.err.println("领取失败，状态 " + response.statusCode());
                return;
            }
            String jobId = text(response.body(), "id");
            String queueId = text(response.body(), "targetQueueId");
            String snapshot = LabelRenderer.text(response.body(), "contentSnapshot");
            String outcome = "FAILED";
            String reason = "";
            try {
                String printerName = resolvePrinterName(queues, queueId);
                submitToQueue(printerName, snapshot);
                outcome = "SPOOLED";
            } catch (Exception ex) {
                reason = ex.getMessage() == null ? "发送前失败" : ex.getMessage();
            }
            report(client, baseUrl, stationId, token, jobId, outcome, reason);
        } catch (Exception ex) {
            System.err.println("打印工位通信失败");
        }
    }

    static void submitToQueue(String printerName, String snapshot) throws Exception {
        PrintService selected = findQueue(printerName);
        if (selected == null) {
            throw new IllegalStateException("本机没有名为 " + printerName + " 的打印队列");
        }
        byte[] png = LabelRenderer.render(snapshot);
        int copies = copiesOf(snapshot);
        Doc doc = new SimpleDoc(new ByteArrayInputStream(png), DocFlavor.INPUT_STREAM.PNG, null);
        HashPrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new Copies(copies));
        DocPrintJob job = selected.createPrintJob();
        job.print(doc, attributes);
    }

    private static int copiesOf(String snapshot) {
        Object value = SnapshotJson.parse(snapshot).get("copies");
        if (!(value instanceof Number number) || number.intValue() < 1 || number.intValue() > 20) {
            throw new IllegalArgumentException("打印份数无效");
        }
        return number.intValue();
    }

    private static void report(HttpClient client, String baseUrl, String stationId, String token, String jobId, String outcome, String reason) throws Exception {
        String body = "{\"outcome\":\"" + outcome + "\",\"failureReason\":\"" + escape(reason) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/label/agents/v2/jobs/" + jobId + "/report"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("X-Print-Station", stationId)
                .header("X-Print-Token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        client.send(request, HttpResponse.BodyHandlers.discarding());
    }

    private static String text(String json, String field) {
        String needle = "\"" + field + "\":\"";
        int start = json.indexOf(needle);
        if (start < 0) {
            return "";
        }
        int from = start + needle.length();
        int end = json.indexOf('"', from);
        return end < 0 ? "" : json.substring(from, end);
    }

    private static String required(Properties config, String key) {
        String value = config.getProperty(key, "").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("缺少配置 " + key);
        }
        return value;
    }

    static Map<String, String> queueMappings(Properties config) {
        Map<String, String> mappings = new HashMap<>();
        for (String key : config.stringPropertyNames()) {
            if (!key.startsWith("queue.")) continue;
            String id = key.substring("queue.".length());
            String printer = config.getProperty(key, "").trim();
            if (!id.matches("[A-Za-z0-9._:-]{1,64}") || printer.isBlank()) {
                throw new IllegalArgumentException("打印队列映射无效：" + key);
            }
            mappings.put(id, printer);
        }
        if (mappings.isEmpty()) throw new IllegalArgumentException("请配置至少一条 queue.<队列 ID> 映射");
        return Map.copyOf(mappings);
    }

    static String resolvePrinterName(Map<String, String> queues, String queueId) {
        String printerName = queues.get(queueId);
        if (printerName == null || printerName.isBlank()) {
            throw new IllegalStateException("本机未映射打印队列 " + queueId);
        }
        return printerName;
    }

    private static PrintService findQueue(String printerName) {
        for (PrintService service : PrintServiceLookup.lookupPrintServices(null, null)) {
            if (printerName.equals(service.getName())) return service;
        }
        return null;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
