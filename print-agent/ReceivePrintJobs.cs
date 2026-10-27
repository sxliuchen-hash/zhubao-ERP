// 厂内打印接收程序。用工位凭据领取任务，绘制标签后送入本机 Windows 队列，再回报结果。
// 配置文件 print-agent.properties 放在 exe 旁边。同一工位不要同时运行 Java 打印程序。
using System;
using System.Collections;
using System.Collections.Generic;
using System.Diagnostics;
using System.Drawing;
using System.Drawing.Imaging;
using System.IO;
using System.Management;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Text;
using System.Text.RegularExpressions;
using System.Threading;
using System.Web.Script.Serialization;

namespace Zhubao.PrintReceiver
{
    sealed class AgentConfig
    {
        public string BaseUrl;
        public string StationId;
        public string Token;
        public bool AllowUntrusted;
        public int PollSeconds;
        public Dictionary<string, string> Queues;
    }

    sealed class ClaimedJob
    {
        public string Id;
        public string QueueId;
        public int Copies;
        public string SnapshotText;
    }

    sealed class Poll
    {
        public string Kind;
        public int Status;
        public string Body;
        public ClaimedJob Job;
    }

    sealed class HttpResult
    {
        public int Status;
        public string Body;
    }

    sealed class BuiltLabel
    {
        public LabelSpec Spec;
        public int Copies;
    }

    static class Program
    {
        [STAThread]
        static int Main(string[] args)
        {
            try
            {
                int code = Run(args);
                if (code != 0) PauseIfDoubleClicked();
                return code;
            }
            catch (Exception ex)
            {
                Console.WriteLine(ex.Message);
                PauseIfDoubleClicked();
                return 1;
            }
        }

        static int Run(string[] args)
        {
            string configName = "print-agent.properties";
            bool once = false;
            bool selfTest = false;
            string renderSample = null;
            for (int index = 0; index < args.Length; index++)
            {
                string arg = args[index];
                if (arg == "-SelfTest") selfTest = true;
                else if (arg == "-Once") once = true;
                else if (arg == "-Config" && index + 1 < args.Length) configName = args[++index];
                else if (arg == "-RenderSample" && index + 1 < args.Length) renderSample = args[++index];
                else if (arg == "-h" || arg == "-?" || arg == "--help")
                {
                    Console.WriteLine("把 print-agent.properties 放在本程序旁边后直接运行。");
                    Console.WriteLine("ZhubaoPrintReceiver.exe [-Config 配置文件] [-Once] [-SelfTest]");
                    return 0;
                }
                else throw new InvalidOperationException("无法识别的参数：" + arg);
            }

            if (selfTest || !string.IsNullOrEmpty(renderSample))
            {
                TestConfig();
                TestHttp();
                TestSnapshot();
                string samplePath = string.IsNullOrEmpty(renderSample)
                    ? Path.Combine(Path.GetTempPath(), "zhubao-label-sample.png")
                    : renderSample;
                Console.WriteLine(LabelPrinter.SelfCheck(samplePath));
                Console.WriteLine(samplePath);
                return 0;
            }

            string configPath = ResolveConfig(configName);
            if (configPath == null)
            {
                string expected = Path.Combine(ExeDirectory(), configName);
                Console.WriteLine("缺少配置文件：" + expected);
                Console.WriteLine("请把 config.example.properties 复制成这个文件名，和程序放在同一文件夹。");
                return 2;
            }
            AgentConfig agent = LoadConfig(File.ReadAllText(configPath, Encoding.UTF8));
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Tls12;
            ServicePointManager.Expect100Continue = false;
            if (agent.AllowUntrusted)
            {
                Console.WriteLine("已按配置忽略 HTTPS 证书校验，只应用于本厂测试地址。");
                ServicePointManager.ServerCertificateValidationCallback = delegate { return true; };
            }

            Console.WriteLine("打印接收已启动：" + agent.StationId + "，队列 " + agent.Queues.Count + " 条");
            var installed = new List<string>();
            foreach (string printer in System.Drawing.Printing.PrinterSettings.InstalledPrinters)
            {
                installed.Add(printer);
                Console.WriteLine("本机打印队列：" + printer);
            }
            var resolved = new Dictionary<string, string>(StringComparer.Ordinal);
            foreach (KeyValuePair<string, string> pair in agent.Queues)
            {
                string actual = LabelPrinter.ResolveInstalledPrinter(pair.Value);
                if (actual == null)
                    Console.WriteLine("本机没有队列 " + pair.Key + " 对应的打印机：" + pair.Value);
                else resolved[pair.Key] = actual;
            }
            if (resolved.Count == 0)
            {
                Console.WriteLine("配置的打印机在本机都不存在，未开始领取，避免把任务报成发送前失败。请按上面列出的名称修改 queue 配置。");
                return 2;
            }
            agent.Queues = resolved;

            string inbox = Path.Combine(Path.GetDirectoryName(configPath), "received");
            while (true)
            {
                Poll poll = Receive(agent);
                if (poll.Kind == "empty")
                {
                    if (once) return 0;
                    Thread.Sleep(agent.PollSeconds * 1000);
                    continue;
                }
                if (poll.Kind == "denied")
                {
                    Console.WriteLine("工位凭据无效。请核对 stationId、token，或在标签页重新轮换凭据后改配置。");
                    return 1;
                }
                if (poll.Kind == "error")
                {
                    Console.WriteLine("领取失败，状态 " + poll.Status + "。" + poll.Body);
                    if (once) return 1;
                    Thread.Sleep(agent.PollSeconds * 1000);
                    continue;
                }
                ClaimedJob claim = poll.Job;
                if (claim == null || string.IsNullOrWhiteSpace(claim.Id))
                {
                    Console.WriteLine("领取结果没有任务编号");
                    if (once) return 1;
                    Thread.Sleep(agent.PollSeconds * 1000);
                    continue;
                }
                Console.WriteLine("已领取 " + claim.Id + "，目标队列 " + claim.QueueId);
                try
                {
                    Directory.CreateDirectory(inbox);
                    string safeId = SafeFileName(claim.Id);
                    File.WriteAllText(Path.Combine(inbox, safeId + ".json"), claim.SnapshotText, new UTF8Encoding(false));
                    BuiltLabel built = ToLabel(claim.SnapshotText, claim.Copies);
                    if (!agent.Queues.ContainsKey(claim.QueueId))
                        throw new InvalidOperationException("本机未映射打印队列 " + claim.QueueId);
                    string printerName = agent.Queues[claim.QueueId];
                    using (Bitmap bitmap = LabelPrinter.Render(built.Spec))
                    {
                        bitmap.Save(Path.Combine(inbox, safeId + ".png"), ImageFormat.Png);
                        LabelPrinter.Print(printerName, bitmap, built.Copies, built.Spec.WidthMm, built.Spec.HeightMm);
                    }
                    Report(agent, claim.Id, "SPOOLED", "");
                    Console.WriteLine("已送入系统队列 " + claim.Id + " → " + printerName + "。请看标签机是否出纸。");
                    if (once) return 0;
                }
                catch (Exception ex)
                {
                    string reason = ShortReason(ex.Message);
                    try { Report(agent, claim.Id, "FAILED", reason); }
                    catch (Exception)
                    {
                        Console.WriteLine("失败：" + reason + "。结果没能回报服务器。");
                    }
                    Console.WriteLine("失败：" + reason);
                    if (once) return 1;
                }
            }
        }

        static void TestConfig()
        {
            AgentConfig config = LoadConfig("baseUrl=https://erp.example.com\r\nstationId=STATION-1\r\ntoken=PS-example\r\nqueue.Q-40x30=Gprinter GP-9134T\r\n");
            if (config.BaseUrl != "https://erp.example.com" || config.Queues["Q-40x30"] != "Gprinter GP-9134T")
                throw new InvalidOperationException("配置解析失败");
            bool rejected = false;
            try { LoadConfig("baseUrl=http://127.0.0.1\r\nstationId=STATION-1\r\ntoken=x\r\nqueue.Q=P\r\n"); }
            catch (InvalidOperationException) { rejected = true; }
            if (!rejected) throw new InvalidOperationException("非 HTTPS 地址未被拒绝");
            string report = ReportJson("FAILED", "队列 \"A\" 不存在");
            if (report != "{\"outcome\":\"FAILED\",\"failureReason\":\"队列 \\\"A\\\" 不存在\"}")
                throw new InvalidOperationException("回报报文错误：" + report);
        }

        static void TestHttp()
        {
            int port = FreePort();
            using (var listener = new HttpListener())
            {
                listener.Prefixes.Add("http://127.0.0.1:" + port + "/");
                listener.Start();
                try
                {
                    var agent = new AgentConfig();
                    agent.BaseUrl = "http://127.0.0.1:" + port;
                    agent.StationId = "STATION-1";
                    agent.Token = "PS-example";
                    agent.PollSeconds = 3;
                    agent.Queues = new Dictionary<string, string>();

                    Poll empty = Exchange(listener, delegate { return Receive(agent); }, delegate(HttpListenerContext context)
                    {
                        ExpectCredential(context);
                        if (context.Request.HttpMethod != "POST") throw new InvalidOperationException("领取不是 POST");
                        WriteJson(context, 400, "{\"code\":400,\"message\":\"当前没有待打印任务\",\"data\":null}");
                    });
                    if (empty.Kind != "empty") throw new InvalidOperationException("空队列被当成领取失败：" + empty.Body);

                    Poll job = Exchange(listener, delegate { return Receive(agent); }, delegate(HttpListenerContext context)
                    {
                        ExpectCredential(context);
                        WriteJson(context, 200, "{\"code\":0,\"message\":\"success\",\"data\":{\"job\":{\"id\":\"PJ-1\",\"targetQueueId\":\"Q-40x30\",\"copies\":2},\"contentSnapshot\":\"{\\\"primaryCode\\\":\\\"MD-1\\\",\\\"copies\\\":1}\"}}");
                    });
                    if (job.Kind != "job" || job.Job.Id != "PJ-1" || job.Job.QueueId != "Q-40x30" || job.Job.Copies != 2)
                        throw new InvalidOperationException("领取结果解析失败");
                    if (job.Job.SnapshotText.IndexOf("MD-1") < 0) throw new InvalidOperationException("标签快照解析失败");

                    Exchange(listener, delegate { Report(agent, "PJ-1", "FAILED", "队列不存在"); return null; }, delegate(HttpListenerContext context)
                    {
                        ExpectCredential(context);
                        string type = context.Request.ContentType ?? "";
                        if (type.IndexOf("application/json") < 0) throw new InvalidOperationException("回报不是 JSON：" + type);
                        string body = ReadRequest(context.Request);
                        if (body.IndexOf("队列不存在") < 0 || body.IndexOf("\"outcome\":\"FAILED\"") < 0)
                            throw new InvalidOperationException("回报正文错误：" + body);
                        WriteJson(context, 200, "{\"code\":0,\"message\":\"success\",\"data\":null}");
                    });
                }
                finally
                {
                    listener.Stop();
                }
            }
        }

        static Poll Exchange(HttpListener listener, Func<Poll> call, Action<HttpListenerContext> handle)
        {
            Poll result = null;
            Exception error = null;
            var thread = new Thread(delegate()
            {
                try { result = call(); }
                catch (Exception ex) { error = ex; }
            });
            thread.IsBackground = true;
            thread.Start();
            IAsyncResult pending = listener.BeginGetContext(null, null);
            if (!pending.AsyncWaitHandle.WaitOne(8000))
                throw new InvalidOperationException("测试服务器没有收到请求");
            handle(listener.EndGetContext(pending));
            if (!thread.Join(8000)) throw new InvalidOperationException("测试请求没有返回");
            if (error != null) throw error;
            return result;
        }

        static void ExpectCredential(HttpListenerContext context)
        {
            if (context.Request.Headers["X-Print-Station"] != "STATION-1" || context.Request.Headers["X-Print-Token"] != "PS-example")
                throw new InvalidOperationException("工位凭据头不正确");
            // 保活连接被服务器关掉后，.NET 不会重发 POST，窗口会报连接被关闭。
            if (context.Request.KeepAlive || context.Request.ProtocolVersion != HttpVersion.Version10)
                throw new InvalidOperationException("领取必须使用会在响应后关闭的 HTTP/1.0 连接");
        }

        static int FreePort()
        {
            var socket = new TcpListener(IPAddress.Loopback, 0);
            socket.Start();
            int port = ((IPEndPoint)socket.LocalEndpoint).Port;
            socket.Stop();
            return port;
        }

        static void WriteJson(HttpListenerContext context, int status, string json)
        {
            byte[] bytes = Encoding.UTF8.GetBytes(json);
            context.Response.StatusCode = status;
            context.Response.ContentType = "application/json; charset=utf-8";
            context.Response.ContentLength64 = bytes.Length;
            context.Response.OutputStream.Write(bytes, 0, bytes.Length);
            context.Response.OutputStream.Close();
        }

        static string ReadRequest(HttpListenerRequest request)
        {
            using (var reader = new StreamReader(request.InputStream, Encoding.UTF8))
                return reader.ReadToEnd();
        }

        static string ResolveConfig(string configured)
        {
            if (Path.IsPathRooted(configured) && File.Exists(configured)) return Path.GetFullPath(configured);
            string beside = Path.Combine(ExeDirectory(), configured);
            if (File.Exists(beside)) return Path.GetFullPath(beside);
            string current = Path.Combine(Environment.CurrentDirectory, configured);
            if (File.Exists(current)) return Path.GetFullPath(current);
            return null;
        }

        static string ExeDirectory()
        {
            return Path.GetDirectoryName(Assembly.GetExecutingAssembly().Location);
        }

        static AgentConfig LoadConfig(string text)
        {
            var values = new Dictionary<string, string>(StringComparer.Ordinal);
            string[] lines = text.Replace("\r\n", "\n").Replace('\r', '\n').Split('\n');
            foreach (string raw in lines)
            {
                string trim = raw.Trim();
                if (trim.Length == 0 || trim.StartsWith("#")) continue;
                int split = trim.IndexOf('=');
                if (split < 1) throw new InvalidOperationException("配置行无效：" + trim);
                values[trim.Substring(0, split).Trim()] = trim.Substring(split + 1).Trim();
            }
            var agent = new AgentConfig();
            agent.BaseUrl = Value(values, "baseUrl").TrimEnd('/');
            agent.StationId = Value(values, "stationId");
            agent.Token = Value(values, "token");
            agent.PollSeconds = 3;
            string allow = Value(values, "allowUntrustedCertificate");
            agent.AllowUntrusted = allow == "1" || string.Equals(allow, "true", StringComparison.OrdinalIgnoreCase)
                || string.Equals(allow, "yes", StringComparison.OrdinalIgnoreCase);
            if (values.ContainsKey("pollSeconds"))
            {
                int seconds;
                if (!int.TryParse(values["pollSeconds"], out seconds) || seconds < 1 || seconds > 60)
                    throw new InvalidOperationException("pollSeconds 须为 1 到 60");
                agent.PollSeconds = seconds;
            }
            if (!Regex.IsMatch(agent.StationId ?? "", "^[A-Za-z0-9._:-]{1,64}$"))
                throw new InvalidOperationException("stationId 无效");
            if (string.IsNullOrWhiteSpace(agent.Token)) throw new InvalidOperationException("缺少 token");
            if (agent.BaseUrl == null || !agent.BaseUrl.StartsWith("https://"))
                throw new InvalidOperationException("baseUrl 只允许 HTTPS 地址");
            agent.Queues = new Dictionary<string, string>(StringComparer.Ordinal);
            foreach (KeyValuePair<string, string> pair in values)
            {
                if (!pair.Key.StartsWith("queue.")) continue;
                string queueId = pair.Key.Substring("queue.".Length);
                if (!Regex.IsMatch(queueId, "^[A-Za-z0-9._:-]{1,64}$") || string.IsNullOrWhiteSpace(pair.Value))
                    throw new InvalidOperationException("打印队列映射无效：" + pair.Key);
                agent.Queues[queueId] = pair.Value;
            }
            if (agent.Queues.Count < 1) throw new InvalidOperationException("请配置至少一条 queue.<队列 ID> 映射");
            return agent;
        }

        static string Value(Dictionary<string, string> values, string key)
        {
            string found;
            return values.TryGetValue(key, out found) ? found : "";
        }

        static Poll Receive(AgentConfig agent)
        {
            try
            {
                HttpResult http = Post(agent, "/api/label/agents/v2/jobs/claim", new byte[0], null);
                string body = http.Body ?? "";
                if (body.IndexOf("没有待打印任务") >= 0) return Kind("empty", http.Status, body, null);
                if (http.Status == 403 || body.IndexOf("工位凭据无效") >= 0) return Kind("denied", http.Status, body, null);
                if (http.Status >= 400 || http.Status == 0) return Kind("error", http.Status, body, null);
                return Kind("job", http.Status, body, ParseClaim(body));
            }
            catch (Exception ex)
            {
                return Kind("error", 0, ex.Message, null);
            }
        }

        static void Report(AgentConfig agent, string jobId, string outcome, string reason)
        {
            byte[] bytes = Encoding.UTF8.GetBytes(ReportJson(outcome, reason));
            string path = "/api/label/agents/v2/jobs/" + Uri.EscapeDataString(jobId) + "/report";
            HttpResult http = Post(agent, path, bytes, "application/json; charset=utf-8");
            if (http.Status >= 400) throw new InvalidOperationException("回报未被接受：" + http.Body);
            var root = ParseObject(http.Body);
            if (root == null || Number(root, "code", -1) != 0)
                throw new InvalidOperationException("回报未被接受：" + (root == null ? http.Body : Text(root, "message")));
        }

        static Poll Kind(string kind, int status, string body, ClaimedJob job)
        {
            var poll = new Poll();
            poll.Kind = kind;
            poll.Status = status;
            poll.Body = body;
            poll.Job = job;
            return poll;
        }

        static HttpResult Post(AgentConfig agent, string path, byte[] body, string contentType)
        {
            var request = (HttpWebRequest)WebRequest.Create(agent.BaseUrl + path);
            request.Method = "POST";
            request.Timeout = 20000;
            request.ReadWriteTimeout = 20000;
            request.AllowAutoRedirect = false;
            // 测试服按块返回并声明保持连接，空闲后却会关掉。POST 被中断时不会自动重发。
            request.ProtocolVersion = HttpVersion.Version10;
            request.KeepAlive = false;
            request.Headers["X-Print-Station"] = agent.StationId;
            request.Headers["X-Print-Token"] = agent.Token;
            if (body == null) body = new byte[0];
            if (body.Length == 0)
            {
                request.ContentLength = 0;
            }
            else
            {
                request.ContentType = contentType;
                request.ContentLength = body.Length;
                using (Stream stream = request.GetRequestStream())
                    stream.Write(body, 0, body.Length);
            }
            try
            {
                using (var response = (HttpWebResponse)request.GetResponse())
                    return ReadResponse(response);
            }
            catch (WebException ex)
            {
                var response = ex.Response as HttpWebResponse;
                if (response == null) throw;
                using (response) return ReadResponse(response);
            }
        }

        static HttpResult ReadResponse(HttpWebResponse response)
        {
            var result = new HttpResult();
            result.Status = (int)response.StatusCode;
            Stream stream = response.GetResponseStream();
            if (stream == null) result.Body = "";
            else using (var reader = new StreamReader(stream, Encoding.UTF8)) result.Body = reader.ReadToEnd();
            return result;
        }

        static ClaimedJob ParseClaim(string body)
        {
            var root = ParseObject(body);
            if (root == null || Number(root, "code", -1) != 0) throw new InvalidOperationException("领取返回无效");
            var data = root.ContainsKey("data") ? root["data"] as Dictionary<string, object> : null;
            var job = data == null || !data.ContainsKey("job") ? null : data["job"] as Dictionary<string, object>;
            if (job == null) throw new InvalidOperationException("领取返回无效");
            var claimed = new ClaimedJob();
            claimed.Id = Text(job, "id");
            claimed.QueueId = Text(job, "targetQueueId");
            claimed.Copies = Number(job, "copies", 0);
            object snapshot = data.ContainsKey("contentSnapshot") ? data["contentSnapshot"] : null;
            claimed.SnapshotText = snapshot is string ? (string)snapshot : new JavaScriptSerializer().Serialize(snapshot);
            return claimed;
        }

        static BuiltLabel ToLabel(string snapshotText, int copies)
        {
            var snapshot = ParseObject(snapshotText);
            if (snapshot == null) throw new InvalidOperationException("标签快照无效");
            var lines = new List<string>();
            object fields;
            IList fieldItems = snapshot.TryGetValue("fields", out fields) ? Items(fields) : null;
            if (fieldItems != null)
            {
                foreach (object item in fieldItems)
                {
                    var field = item as Dictionary<string, object>;
                    if (field == null) continue;
                    string name = Text(field, "name");
                    string value = Text(field, "value");
                    if (name.Length > 0 && value.Length > 0) lines.Add(name + "：" + value);
                }
            }
            object rawLines;
            if (lines.Count == 0 && snapshot.TryGetValue("lines", out rawLines))
            {
                IList lineItems = Items(rawLines);
                if (lineItems != null)
                {
                    foreach (object line in lineItems)
                    {
                        string text = Convert.ToString(line);
                        if (!string.IsNullOrWhiteSpace(text)) lines.Add(text);
                    }
                }
            }
            string code = Text(snapshot, "primaryCode");
            if (code.Length == 0) code = Text(snapshot, "businessCode");
            int width = Number(snapshot, "widthMm", 40);
            int height = Number(snapshot, "heightMm", 30);
            int dpi = Number(snapshot, "dpi", 300);
            if (width < 10) width = 40;
            if (height < 10) height = 30;
            // 旧模板快照是 60×40。厂内纸张是 40×30，按实际纸绘制，不再把 60×40 送进打印机。
            if (width == 60 && height == 40) { width = 40; height = 30; }
            int useCopies = copies >= 1 ? copies : Number(snapshot, "copies", 0);
            var spec = new LabelSpec();
            spec.TypeLabel = Text(snapshot, "typeLabel");
            spec.Code = code;
            spec.Qr = Text(snapshot, "qr");
            spec.WidthMm = width;
            spec.HeightMm = height;
            spec.Dpi = dpi;
            spec.Lines = lines.ToArray();
            spec.FooterLeft = Text(snapshot, "footerLeft");
            spec.FooterRight = Text(snapshot, "footerRight");
            var storageSources = new List<StorageSourceSpec>();
            object rawStorage = null;
            snapshot.TryGetValue("storageSources", out rawStorage);
            // JavaScriptSerializer 把 JSON 数组还原成 object[]，不是 ArrayList。只认 ArrayList 会把有保存要求的标签直接判失败。
            IList storageItems = Items(rawStorage);
            if (rawStorage != null && storageItems == null)
                throw new InvalidOperationException("保存标识载荷无效，不能省略打印");
            if (storageItems != null)
            {
                foreach (object rawSource in storageItems)
                {
                    var source = rawSource as Dictionary<string, object>;
                    if (source == null) throw new InvalidOperationException("保存标识来源无效");
                    var marks = new List<string>();
                    object rawMarks;
                    IList markItems = source.TryGetValue("marks", out rawMarks) ? Items(rawMarks) : null;
                    if (rawMarks != null && markItems == null) throw new InvalidOperationException("保存标识来源无效");
                    if (markItems != null)
                        foreach (object mark in markItems) marks.Add(Convert.ToString(mark));
                    storageSources.Add(new StorageSourceSpec { SourceLabel = Text(source, "sourceLabel"), Marks = marks.ToArray(), Instruction = Text(source, "instruction") });
                }
            }
            spec.StorageSources = storageSources.ToArray();
            var built = new BuiltLabel();
            built.Spec = spec;
            built.Copies = useCopies;
            return built;
        }

        static void TestSnapshot()
        {
            string json = "{\"primaryCode\":\"MD-1\",\"copies\":1,\"widthMm\":40,\"heightMm\":30,\"dpi\":300,"
                + "\"fields\":[{\"name\":\"客户\",\"value\":\"甲\"}],\"lines\":[\"客户:甲\"],"
                + "\"storageSources\":[{\"sourceLabel\":\"明细1\",\"marks\":[\"冷\",\"真\"],\"instruction\":\"避光\"}]}";
            BuiltLabel built = ToLabel(json, 1);
            if (built.Spec.Lines.Length == 0 || built.Spec.Lines[0].IndexOf("甲") < 0)
                throw new InvalidOperationException("标签字段未被读取");
            if (built.Spec.StorageSources == null || built.Spec.StorageSources.Length != 1
                || built.Spec.StorageSources[0].Marks.Length != 2 || built.Spec.StorageSources[0].Instruction != "避光")
                throw new InvalidOperationException("保存标识未被读取");
            BuiltLabel legacy = ToLabel("{\"widthMm\":60,\"heightMm\":40,\"dpi\":203,\"primaryCode\":\"MD-1\",\"copies\":1}", 1);
            if (legacy.Spec.WidthMm != 40 || legacy.Spec.HeightMm != 30)
                throw new InvalidOperationException("旧尺寸没有改成 40×30");
            try
            {
                ToLabel("{\"storageSources\":{\"sourceLabel\":\"明细1\"}}", 1);
                throw new InvalidOperationException("对象形式的保存标识未被拒绝");
            }
            catch (InvalidOperationException ex)
            {
                if (ex.Message.IndexOf("保存标识载荷无效") < 0) throw;
            }
        }

        static string ShortReason(string message)
        {
            if (string.IsNullOrWhiteSpace(message)) return "打印没有送出";
            if (message.IndexOf("保存标识") >= 0) return "保存要求无法打印";
            if (message.IndexOf("空间不足") >= 0) return "40×30 纸放不下保存说明";
            if (message.IndexOf("没有名为") >= 0 || message.IndexOf("未映射") >= 0) return "找不到打印机";
            return message.Length > 40 ? message.Substring(0, 40) : message;
        }

        static IList Items(object value)
        {
            if (value == null || value is string || value is IDictionary) return null;
            return value as IList;
        }

        static Dictionary<string, object> ParseObject(string json)
        {
            if (string.IsNullOrWhiteSpace(json)) return null;
            var serializer = new JavaScriptSerializer();
            serializer.MaxJsonLength = 10 * 1024 * 1024;
            return serializer.DeserializeObject(json) as Dictionary<string, object>;
        }

        static string Text(Dictionary<string, object> map, string key)
        {
            object value;
            if (map == null || !map.TryGetValue(key, out value) || value == null) return "";
            return Convert.ToString(value);
        }

        static int Number(Dictionary<string, object> map, string key, int fallback)
        {
            object value;
            if (map == null || !map.TryGetValue(key, out value) || value == null) return fallback;
            try { return Convert.ToInt32(value); }
            catch (FormatException) { return fallback; }
            catch (InvalidCastException) { return fallback; }
            catch (OverflowException) { return fallback; }
        }

        static string ReportJson(string outcome, string reason)
        {
            string safe = reason ?? "";
            safe = safe.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\r", " ").Replace("\n", " ").Trim();
            if (safe.Length > 180) safe = safe.Substring(0, 180);
            return "{\"outcome\":\"" + outcome + "\",\"failureReason\":\"" + safe + "\"}";
        }

        static string SafeFileName(string name)
        {
            foreach (char bad in Path.GetInvalidFileNameChars()) name = name.Replace(bad, '_');
            return name;
        }

        static void PauseIfDoubleClicked()
        {
            try
            {
                int parentId = ParentProcessId(Process.GetCurrentProcess().Id);
                if (parentId <= 0) return;
                using (Process parent = Process.GetProcessById(parentId))
                {
                    if (!string.Equals(parent.ProcessName, "explorer", StringComparison.OrdinalIgnoreCase)) return;
                }
                Console.WriteLine("按回车关闭窗口");
                Console.ReadLine();
            }
            catch (Exception)
            {
            }
        }

        static int ParentProcessId(int processId)
        {
            using (var searcher = new ManagementObjectSearcher("SELECT ParentProcessId FROM Win32_Process WHERE ProcessId=" + processId))
            {
                foreach (ManagementObject item in searcher.Get())
                    return Convert.ToInt32(item["ParentProcessId"]);
            }
            return 0;
        }
    }
}
