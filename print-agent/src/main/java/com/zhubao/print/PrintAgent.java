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
        String printerName = required(config, "printerName");
        if (!baseUrl.startsWith("https://")) {
            System.err.println("只允许连接 HTTPS 地址");
            System.exit(2);
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        System.out.println("打印工位已启动：" + stationId + "，队列：" + printerName);
        while (!Thread.currentThread().isInterrupted()) {
            poll(client, baseUrl, stationId, token, printerName);
            Thread.sleep(3000);
        }
    }

    static void poll(HttpClient client, String baseUrl, String stationId, String token, String printerName) {
        try {
            HttpRequest claim = HttpRequest.newBuilder(URI.create(baseUrl + "/api/label/agents/jobs/claim"))
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
            String snapshot = LabelRenderer.text(response.body(), "contentSnapshot");
            String outcome = "FAILED";
            String reason = "";
            try {
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
        PrintService[] services = PrintServiceLookup.lookupPrintServices(null, null);
        PrintService selected = null;
        for (PrintService service : services) {
            if (printerName.equals(service.getName())) {
                selected = service;
                break;
            }
        }
        if (selected == null) {
            throw new IllegalStateException("本机没有名为 " + printerName + " 的打印队列");
        }
        byte[] png = LabelRenderer.render(snapshot);
        int copies = Math.max(1, copiesOf(snapshot));
        Doc doc = new SimpleDoc(new ByteArrayInputStream(png), DocFlavor.INPUT_STREAM.PNG, null);
        HashPrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new Copies(copies));
        DocPrintJob job = selected.createPrintJob();
        job.print(doc, attributes);
    }

    private static int copiesOf(String snapshot) {
        int marker = snapshot.indexOf("\"copies\":");
        if (marker < 0) {
            return 1;
        }
        int from = marker + "\"copies\":".length();
        int end = from;
        while (end < snapshot.length() && Character.isDigit(snapshot.charAt(end))) {
            end++;
        }
        return end == from ? 1 : Integer.parseInt(snapshot.substring(from, end));
    }

    private static void report(HttpClient client, String baseUrl, String stationId, String token, String jobId, String outcome, String reason) throws Exception {
        String body = "{\"outcome\":\"" + outcome + "\",\"failureReason\":\"" + escape(reason) + "\"}";
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/label/agents/jobs/" + jobId + "/report"))
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

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
