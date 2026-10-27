// 标签绘制。二维码和文字都来自服务端冻结的快照，编译进 ZhubaoPrintReceiver.exe。
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Printing;
using System.Drawing.Text;
using System.Text;

namespace Zhubao.PrintReceiver
{
    public sealed class StorageSourceSpec
    {
        public string SourceLabel;
        public string[] Marks;
        public string Instruction;
    }

    public sealed class LabelSpec
    {
        public string TypeLabel;
        public string Code;
        public string Qr;
        public int WidthMm;
        public int HeightMm;
        public int Dpi;
        public string[] Lines;
        public string FooterLeft;
        public string FooterRight;
        public StorageSourceSpec[] StorageSources;
    }

    public static class LabelPrinter
    {
        public static Bitmap Render(LabelSpec spec)
        {
            if (spec == null) throw new ArgumentException("标签快照无效");
            if (spec.WidthMm < 10 || spec.HeightMm < 10 || spec.WidthMm > 200 || spec.HeightMm > 200)
                throw new ArgumentException("标签尺寸无效");
            int dpi = spec.Dpi >= 100 && spec.Dpi <= 600 ? spec.Dpi : 300;
            int width = Math.Max(1, (int)Math.Round(spec.WidthMm * dpi / 25.4));
            int height = Math.Max(1, (int)Math.Round(spec.HeightMm * dpi / 25.4));
            var image = new Bitmap(width, height);
            image.SetResolution(dpi, dpi);
            using (var canvas = Graphics.FromImage(image))
            {
                canvas.Clear(Color.White);
                canvas.TextRenderingHint = TextRenderingHint.AntiAliasGridFit;
                int pad = Math.Max(2, (int)Math.Round(0.8 * dpi / 25.4));
                int storageHeight = StorageHeight(spec, dpi);
                int contentBottom = height - pad - storageHeight;
                if (storageHeight > 0 && (spec.WidthMm < 35 || storageHeight + (int)Math.Round(24 * dpi / 25.4) > height))
                    throw new ArgumentException("保存标识与来源说明空间不足，请调整标签尺寸或按来源明细打印");
                float y = pad;
                using (var title = LabelFont((float)(7.2 * dpi / 72.0), true))
                {
                    string head = ((spec.TypeLabel ?? "") + " " + (spec.Code ?? "")).Trim();
                    y = DrawLine(canvas, head, title, pad, y, width - 2 * pad, contentBottom);
                }
                int qrTop = (int)Math.Ceiling(y) + 2;
                int qrBox = Math.Min(contentBottom - qrTop, (int)(width * 0.42));
                qrBox = Math.Max(21, qrBox);
                if (!string.IsNullOrWhiteSpace(spec.Qr))
                    DrawQr(canvas, spec.Qr, pad, qrTop, qrBox);
                int textX = pad + qrBox + pad;
                int textWidth = Math.Max(1, width - textX - pad);
                float textY = qrTop;
                if (spec.Lines != null)
                {
                    using (var body = LabelFont((float)(6.0 * dpi / 72.0), false))
                    {
                        foreach (string line in spec.Lines)
                        {
                            if (string.IsNullOrWhiteSpace(line) || textY >= contentBottom) continue;
                            textY = DrawWrapped(canvas, line, body, textX, textY, textWidth, contentBottom, 2);
                        }
                    }
                }
                string footer = ((spec.FooterLeft ?? "") + " " + (spec.FooterRight ?? "")).Trim();
                if (footer.Length > 0)
                {
                    using (var footerFont = LabelFont((float)(5.5 * dpi / 72.0), false))
                    {
                        float footerY = contentBottom - footerFont.Height;
                        if (footerY > textY)
                            DrawLine(canvas, footer, footerFont, pad, footerY, width - 2 * pad, height);
                    }
                }
                DrawStorage(canvas, spec, dpi, pad, height - pad - storageHeight, width - 2 * pad);
                canvas.DrawRectangle(Pens.Black, 0, 0, width - 1, height - 1);
            }
            return image;
        }

        private static int StorageHeight(LabelSpec spec, int dpi)
        {
            double mm = 0;
            int chars = Math.Max(1, (int)((spec.WidthMm - 3) / 2.2));
            foreach (var source in spec.StorageSources ?? new StorageSourceSpec[0])
            {
                if ((source.Marks == null || source.Marks.Length == 0) && string.IsNullOrWhiteSpace(source.Instruction)) continue;
                mm += 5 + (string.IsNullOrWhiteSpace(source.Instruction) ? 0 : Math.Ceiling((double)source.Instruction.Length / chars) * 3);
            }
            return (int)Math.Round(mm * dpi / 25.4);
        }

        private static void DrawStorage(Graphics canvas, LabelSpec spec, int dpi, int left, int top, int width)
        {
            float y = top;
            float row = (float)(5 * dpi / 25.4);
            float diameter = (float)(4 * dpi / 25.4);
            using (var font = LabelFont((float)(6 * dpi / 72.0), false))
            using (var markFont = LabelFont((float)(8 * dpi / 72.0), true))
            using (var pen = new Pen(Color.Black, (float)(0.25 * dpi / 25.4)))
            using (var center = new StringFormat { Alignment = StringAlignment.Center, LineAlignment = StringAlignment.Center })
            {
                foreach (var source in spec.StorageSources ?? new StorageSourceSpec[0])
                {
                    if ((source.Marks == null || source.Marks.Length == 0) && string.IsNullOrWhiteSpace(source.Instruction)) continue;
                    canvas.DrawString(source.SourceLabel ?? "", font, Brushes.Black, left, y + (row - font.Height) / 2);
                    float x = left + (float)(10 * dpi / 25.4);
                    foreach (var mark in source.Marks ?? new string[0])
                    {
                        if (mark.Length != 1 || "冷冻真光潮".IndexOf(mark, StringComparison.Ordinal) < 0) throw new ArgumentException("保存标识无效");
                        canvas.DrawEllipse(pen, x, y + (row - diameter) / 2, diameter, diameter);
                        canvas.DrawString(mark, markFont, Brushes.Black, new RectangleF(x, y + (row - diameter) / 2, diameter, diameter), center);
                        x += diameter + (float)(0.5 * dpi / 25.4);
                    }
                    y += row;
                    if (!string.IsNullOrWhiteSpace(source.Instruction))
                    {
                        int chars = Math.Max(1, (int)((spec.WidthMm - 3) / 2.2));
                        int count = (int)Math.Ceiling((double)source.Instruction.Length / chars);
                        float noteHeight = (float)(count * 3 * dpi / 25.4);
                        DrawWrapped(canvas, System.Text.RegularExpressions.Regex.Replace(source.Instruction, @"\s+", " "), font, left, y, width, y + noteHeight, count);
                        y += noteHeight;
                    }
                }
            }
        }

        public static void Print(string printerName, Bitmap image, int copies, int widthMm, int heightMm)
        {
            if (copies < 1 || copies > 20) throw new ArgumentException("打印份数无效");
            string installed = ResolveInstalledPrinter(printerName);
            if (installed == null)
                throw new InvalidOperationException("本机没有名为 " + printerName + " 的打印队列");
            using (var document = new PrintDocument())
            {
                document.PrinterSettings.PrinterName = installed;
                if (!document.PrinterSettings.IsValid)
                    throw new InvalidOperationException("本机没有名为 " + printerName + " 的打印队列");
                document.DocumentName = "珠宝标签";
                document.PrintController = new StandardPrintController();
                document.OriginAtMargins = false;
                PaperSize paper = LabelPaper(document.PrinterSettings, widthMm, heightMm);
                document.PrinterSettings.DefaultPageSettings.PaperSize = paper;
                document.DefaultPageSettings.PaperSize = paper;
                document.DefaultPageSettings.Margins = new Margins(0, 0, 0, 0);
                document.PrintPage += (sender, args) =>
                {
                    args.PageSettings.PaperSize = paper;
                    args.PageSettings.Margins = new Margins(0, 0, 0, 0);
                    args.Graphics.TranslateTransform(-args.PageSettings.HardMarginX, -args.PageSettings.HardMarginY);
                    float dpiX = args.Graphics.DpiX <= 1 ? 203 : args.Graphics.DpiX;
                    float dpiY = args.Graphics.DpiY <= 1 ? 203 : args.Graphics.DpiY;
                    int destWidth = Math.Max(1, (int)Math.Round(widthMm / 25.4 * dpiX));
                    int destHeight = Math.Max(1, (int)Math.Round(heightMm / 25.4 * dpiY));
                    args.Graphics.DrawImage(image, new Rectangle(0, 0, destWidth, destHeight));
                    args.HasMorePages = false;
                };
                // 部分标签机驱动把最大份数报成 1。超过上限时逐张送入，避免份数被悄悄丢掉。
                int maximum = 1;
                try { maximum = document.PrinterSettings.MaximumCopies; }
                catch (InvalidOperationException) { maximum = 1; }
                if (maximum < 1) maximum = 1;
                if (copies <= maximum)
                {
                    document.PrinterSettings.Copies = (short)copies;
                    document.Print();
                }
                else
                {
                    document.PrinterSettings.Copies = 1;
                    for (int index = 0; index < copies; index++) document.Print();
                }
            }
        }

        static PaperSize LabelPaper(PrinterSettings settings, int widthMm, int heightMm)
        {
            int wantWidth = Hundredths(widthMm);
            int wantHeight = Hundredths(heightMm);
            PaperSize nearest = null;
            int nearestGap = int.MaxValue;
            foreach (PaperSize candidate in settings.PaperSizes)
            {
                int gap = Math.Abs(candidate.Width - wantWidth) + Math.Abs(candidate.Height - wantHeight);
                if (gap < nearestGap)
                {
                    nearestGap = gap;
                    nearest = candidate;
                }
            }
            if (nearest != null && nearestGap <= 8) return nearest;
            return new PaperSize(widthMm + "x" + heightMm + "mm", wantWidth, wantHeight);
        }

        static int Hundredths(int millimeters)
        {
            return Math.Max(1, (int)Math.Round(millimeters / 25.4 * 100));
        }

        public static string ResolveInstalledPrinter(string name)
        {
            if (string.IsNullOrWhiteSpace(name)) return null;
            foreach (string printer in PrinterSettings.InstalledPrinters)
            {
                if (string.Equals(printer, name, StringComparison.OrdinalIgnoreCase)) return printer;
            }
            return null;
        }

        public static bool PrinterExists(string name)
        {
            return ResolveInstalledPrinter(name) != null;
        }

        public static string SelfCheck(string samplePath)
        {
            int[] expected = { 0x5412, 0x5125, 0x5E7C, 0x5B4B, 0x45F9, 0x40CE, 0x4F97, 0x4AA0 };
            for (int mask = 0; mask < expected.Length; mask++)
            {
                if (Qr.FormatBits(mask) != expected[mask])
                    throw new InvalidOperationException("格式信息错误 " + mask);
            }
            bool[,] small = Qr.Encode("A");
            if (small.GetLength(0) != 21) throw new InvalidOperationException("短内容二维码尺寸异常");
            string content = "JG|mother|MD-20261020-0001|1|LIabc";
            bool[,] modules = Qr.Encode(content);
            int size = modules.GetLength(0);
            if (size != modules.GetLength(1) || size != 29)
                throw new InvalidOperationException("二维码尺寸异常 " + size);
            if (!modules[0, 0] || modules[1, 1] || !modules[3, 3] || !modules[0, size - 1] || !modules[size - 1, 0])
                throw new InvalidOperationException("定位图案异常");
            int dark = 0;
            for (int row = 0; row < size; row++)
                for (int column = 0; column < size; column++)
                    if (modules[row, column]) dark++;
            if (dark < size * size / 5 || dark > size * size * 7 / 10)
                throw new InvalidOperationException("二维码填充异常 " + dark);
            var spec = new LabelSpec
            {
                TypeLabel = "母单",
                Code = "MD-20261020-0001",
                Qr = content,
                WidthMm = 40,
                HeightMm = 30,
                Dpi = 300,
                Lines = new[] { "客户：何琴", "加工要求：抛光", "期限：2026-10-25" },
                FooterLeft = "",
                FooterRight = "",
                StorageSources = new[] { new StorageSourceSpec { SourceLabel = "明细1", Marks = new[] { "冷", "真" }, Instruction = "" } }
            };
            using (Bitmap bitmap = Render(spec))
            {
                if (bitmap.Width != 472 || bitmap.Height != 354)
                    throw new InvalidOperationException("画面尺寸异常 " + bitmap.Width + "x" + bitmap.Height);
                if (!string.IsNullOrEmpty(samplePath))
                    bitmap.Save(samplePath, System.Drawing.Imaging.ImageFormat.Png);
            }
            spec.StorageSources = new[] { new StorageSourceSpec { SourceLabel = "明细1", Marks = new[] { "冷", "真" }, Instruction = new string('测', 200) } };
            bool rejected = false;
            try { using (Bitmap unexpected = Render(spec)) { } } catch (ArgumentException) { rejected = true; }
            if (!rejected) throw new InvalidOperationException("保存说明空间不足时应拒绝打印");
            return "二维码 " + size + " 模块，画面 472×354，冷真圆章与空间校验通过";
        }

        private static void DrawQr(Graphics canvas, string content, int left, int top, int box)
        {
            bool[,] modules = Qr.Encode(content);
            int count = modules.GetLength(0);
            int quiet = 2;
            int cells = count + quiet * 2;
            int scale = Math.Max(1, box / cells);
            int drawn = scale * cells;
            int originX = left + (box - drawn) / 2;
            int originY = top + (box - drawn) / 2;
            canvas.FillRectangle(Brushes.White, originX, originY, drawn, drawn);
            for (int row = 0; row < count; row++)
            {
                for (int column = 0; column < count; column++)
                {
                    if (!modules[row, column]) continue;
                    canvas.FillRectangle(Brushes.Black,
                        originX + (column + quiet) * scale,
                        originY + (row + quiet) * scale,
                        scale, scale);
                }
            }
        }

        private static float TextWidth(Graphics canvas, string text, Font font)
        {
            return canvas.MeasureString(text, font, int.MaxValue, StringFormat.GenericTypographic).Width;
        }

        private static float DrawLine(Graphics canvas, string text, Font font, float x, float y, float maxWidth, float bottom)
        {
            if (string.IsNullOrWhiteSpace(text) || y + font.Height > bottom) return y;
            canvas.DrawString(Fit(canvas, text, font, maxWidth), font, Brushes.Black, x, y, StringFormat.GenericTypographic);
            return y + font.Height + 2;
        }

        private static float DrawWrapped(Graphics canvas, string text, Font font, float x, float y, float maxWidth, float bottom, int maxLines)
        {
            string remaining = text;
            for (int line = 0; line < maxLines && remaining.Length > 0 && y + font.Height <= bottom; line++)
            {
                int count = remaining.Length;
                while (count > 1 && TextWidth(canvas, remaining.Substring(0, count), font) > maxWidth) count--;
                string piece = remaining.Substring(0, count);
                if (line == maxLines - 1 && count < remaining.Length)
                    piece = Fit(canvas, remaining, font, maxWidth);
                canvas.DrawString(piece, font, Brushes.Black, x, y, StringFormat.GenericTypographic);
                y += font.Height + 1;
                if (line == maxLines - 1) break;
                remaining = remaining.Substring(Math.Max(1, count));
            }
            return y;
        }

        private static string Fit(Graphics canvas, string text, Font font, float maxWidth)
        {
            if (TextWidth(canvas, text, font) <= maxWidth) return text;
            string fitted = text;
            while (fitted.Length > 1 && TextWidth(canvas, fitted + "…", font) > maxWidth)
                fitted = fitted.Substring(0, fitted.Length - 1);
            return fitted + "…";
        }

        private static Font LabelFont(float pixels, bool bold)
        {
            return new Font("Microsoft YaHei", Math.Max(8, pixels), bold ? FontStyle.Bold : FontStyle.Regular, GraphicsUnit.Pixel);
        }
    }

    public static class Qr
    {
        private static readonly int[] Data = { 0, 16, 28, 44, 64, 86, 108 };
        private static readonly int[] Blocks = { 0, 1, 1, 1, 2, 2, 4 };
        private static readonly int[] Ecc = { 0, 10, 16, 26, 18, 24, 16 };
        private static readonly int[][] Centers = {
            null,
            null,
            new[] { 6, 18 },
            new[] { 6, 22 },
            new[] { 6, 26 },
            new[] { 6, 30 },
            new[] { 6, 34 }
        };
        private static readonly int[,] FormatCoords = {
            { 8, 0 }, { 8, 1 }, { 8, 2 }, { 8, 3 }, { 8, 4 }, { 8, 5 }, { 8, 7 }, { 8, 8 },
            { 7, 8 }, { 5, 8 }, { 4, 8 }, { 3, 8 }, { 2, 8 }, { 1, 8 }, { 0, 8 }
        };
        private static readonly int[] Exp = new int[512];
        private static readonly int[] Log = new int[256];
        private static bool Ready;

        public static int FormatBits(int mask)
        {
            int format = mask & 7;
            int value = format << 10;
            for (int shift = 4; shift >= 0; shift--)
            {
                if (((value >> (shift + 10)) & 1) == 1) value ^= 0x537 << shift;
            }
            return ((format << 10) | (value & 0x3FF)) ^ 0x5412;
        }

        public static bool[,] Encode(string text)
        {
            if (string.IsNullOrEmpty(text)) throw new ArgumentException("二维码内容为空");
            byte[] payload = Encoding.UTF8.GetBytes(text);
            int version = Choose(payload.Length);
            int size = 21 + 4 * (version - 1);
            var dark = new bool[size, size];
            var function = new bool[size, size];
            DrawFinders(dark, function, size);
            DrawAlignment(dark, function, version);
            DrawTiming(dark, function, size);
            ReserveFormat(function, size);
            dark[size - 8, 8] = true;
            function[size - 8, 8] = true;
            bool[] bits = CodewordBits(DataCodewords(payload, version), version);
            Place(dark, function, bits, size);
            WriteFormat(dark, FormatBits(0), size);
            return dark;
        }

        private static int Choose(int length)
        {
            if (length < 1 || length > 255) throw new ArgumentException("二维码内容长度无效");
            for (int version = 1; version <= 6; version++)
            {
                int bytes = (4 + 8 + length * 8 + 7) / 8;
                if (bytes <= Data[version]) return version;
            }
            throw new InvalidOperationException("二维码内容超过 106 字节，接收脚本无法绘制");
        }

        private static int[] DataCodewords(byte[] payload, int version)
        {
            int capacity = Data[version];
            var bits = new List<bool>();
            Append(bits, 0x4, 4);
            Append(bits, payload.Length, 8);
            foreach (byte item in payload) Append(bits, item, 8);
            int capacityBits = capacity * 8;
            int terminator = Math.Min(4, Math.Max(0, capacityBits - bits.Count));
            Append(bits, 0, terminator);
            while (bits.Count % 8 != 0) bits.Add(false);
            var bytes = new List<int>();
            for (int index = 0; index + 8 <= bits.Count; index += 8)
            {
                int value = 0;
                for (int bit = 0; bit < 8; bit++) if (bits[index + bit]) value |= 1 << (7 - bit);
                bytes.Add(value);
            }
            int pad = 0xEC;
            while (bytes.Count < capacity)
            {
                bytes.Add(pad);
                pad = pad == 0xEC ? 0x11 : 0xEC;
            }
            return bytes.ToArray();
        }

        private static void Append(List<bool> bits, int value, int count)
        {
            for (int shift = count - 1; shift >= 0; shift--) bits.Add(((value >> shift) & 1) == 1);
        }

        private static bool[] CodewordBits(int[] data, int version)
        {
            InitField();
            int blocks = Blocks[version];
            int perBlock = data.Length / blocks;
            int ecc = Ecc[version];
            var dataBlocks = new int[blocks][];
            var eccBlocks = new int[blocks][];
            for (int block = 0; block < blocks; block++)
            {
                dataBlocks[block] = new int[perBlock];
                Array.Copy(data, block * perBlock, dataBlocks[block], 0, perBlock);
                eccBlocks[block] = ReedSolomon(dataBlocks[block], ecc);
            }
            var words = new List<int>();
            for (int index = 0; index < perBlock; index++)
                for (int block = 0; block < blocks; block++) words.Add(dataBlocks[block][index]);
            for (int index = 0; index < ecc; index++)
                for (int block = 0; block < blocks; block++) words.Add(eccBlocks[block][index]);
            int remainder = version == 1 ? 0 : 7;
            var bits = new bool[words.Count * 8 + remainder];
            int position = 0;
            foreach (int word in words)
            {
                for (int shift = 7; shift >= 0; shift--) bits[position++] = ((word >> shift) & 1) == 1;
            }
            return bits;
        }

        private static void InitField()
        {
            if (Ready) return;
            int value = 1;
            for (int index = 0; index < 255; index++)
            {
                Exp[index] = value;
                Log[value] = index;
                value <<= 1;
                if (value >= 256) value ^= 0x11D;
            }
            for (int index = 255; index < 512; index++) Exp[index] = Exp[index - 255];
            Ready = true;
        }

        private static int Mul(int left, int right)
        {
            if (left == 0 || right == 0) return 0;
            return Exp[Log[left] + Log[right]];
        }

        private static int[] ReedSolomon(int[] data, int ecc)
        {
            int[] generator = { 1 };
            for (int index = 0; index < ecc; index++)
            {
                var next = new int[generator.Length + 1];
                for (int term = 0; term < generator.Length; term++)
                {
                    next[term] ^= generator[term];
                    next[term + 1] ^= Mul(generator[term], Exp[index]);
                }
                generator = next;
            }
            var remainder = new int[data.Length + ecc];
            Array.Copy(data, remainder, data.Length);
            for (int index = 0; index < data.Length; index++)
            {
                int coefficient = remainder[index];
                if (coefficient == 0) continue;
                for (int term = 0; term < generator.Length; term++)
                    remainder[index + term] ^= Mul(generator[term], coefficient);
            }
            var result = new int[ecc];
            Array.Copy(remainder, data.Length, result, 0, ecc);
            return result;
        }

        private static void DrawFinders(bool[,] dark, bool[,] function, int size)
        {
            StampFinder(dark, function, size, 0, 0);
            StampFinder(dark, function, size, size - 7, 0);
            StampFinder(dark, function, size, 0, size - 7);
        }

        private static void StampFinder(bool[,] dark, bool[,] function, int size, int column, int row)
        {
            for (int dy = -1; dy <= 7; dy++)
            {
                for (int dx = -1; dx <= 7; dx++)
                {
                    int y = row + dy;
                    int x = column + dx;
                    if (y < 0 || x < 0 || y >= size || x >= size) continue;
                    function[y, x] = true;
                    bool inside = dx >= 0 && dx <= 6 && dy >= 0 && dy <= 6;
                    bool border = dx == 0 || dx == 6 || dy == 0 || dy == 6;
                    bool core = dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4;
                    dark[y, x] = inside && (border || core);
                }
            }
        }

        private static void DrawAlignment(bool[,] dark, bool[,] function, int version)
        {
            if (version < 2) return;
            foreach (int row in Centers[version])
            {
                foreach (int column in Centers[version])
                {
                    if (function[row, column]) continue;
                    for (int dy = -2; dy <= 2; dy++)
                    {
                        for (int dx = -2; dx <= 2; dx++)
                        {
                            function[row + dy, column + dx] = true;
                            dark[row + dy, column + dx] = Math.Max(Math.Abs(dx), Math.Abs(dy)) != 1;
                        }
                    }
                }
            }
        }

        private static void DrawTiming(bool[,] dark, bool[,] function, int size)
        {
            for (int index = 0; index < size; index++)
            {
                if (!function[6, index])
                {
                    function[6, index] = true;
                    dark[6, index] = index % 2 == 0;
                }
                if (!function[index, 6])
                {
                    function[index, 6] = true;
                    dark[index, 6] = index % 2 == 0;
                }
            }
        }

        private static void ReserveFormat(bool[,] function, int size)
        {
            for (int index = 0; index < 9; index++)
            {
                function[8, index] = true;
                function[index, 8] = true;
            }
            for (int index = 0; index < 8; index++) function[8, size - 1 - index] = true;
            for (int index = 0; index < 7; index++) function[size - 7 + index, 8] = true;
        }

        private static void Place(bool[,] dark, bool[,] function, bool[] bits, int size)
        {
            int bitIndex = 0;
            int direction = -1;
            int x = size - 1;
            int y = size - 1;
            while (x > 0)
            {
                if (x == 6) x--;
                while (y >= 0 && y < size)
                {
                    for (int offset = 0; offset < 2; offset++)
                    {
                        int column = x - offset;
                        if (function[y, column]) continue;
                        bool bit = false;
                        if (bitIndex < bits.Length)
                        {
                            bit = bits[bitIndex];
                            bitIndex++;
                        }
                        if ((column + y) % 2 == 0) bit = !bit;
                        dark[y, column] = bit;
                    }
                    y += direction;
                }
                direction = -direction;
                y += direction;
                x -= 2;
            }
            if (bitIndex != bits.Length)
                throw new InvalidOperationException("二维码位数未放完：" + bitIndex + "/" + bits.Length);
        }

        private static void WriteFormat(bool[,] dark, int bits, int size)
        {
            for (int index = 0; index < 15; index++)
            {
                bool bit = ((bits >> index) & 1) == 1;
                dark[FormatCoords[index, 1], FormatCoords[index, 0]] = bit;
                if (index < 8) dark[8, size - 1 - index] = bit;
                else dark[size - 7 + (index - 8), 8] = bit;
            }
        }
    }
}
