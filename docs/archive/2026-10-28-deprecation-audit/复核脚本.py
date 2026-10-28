"""生成瘦身调查的静态候选证据，不修改业务源码，也不自动判定删除。"""

from pathlib import Path
import collections
import csv
import json
import os
import re
import subprocess


目录 = Path(__file__).resolve().parent
根目录 = 目录.parents[2]
源码目录 = 根目录 / "frontend/src"
前端文件 = sorted(p for p in 源码目录.rglob("*") if p.suffix in {".ts", ".vue", ".js"})
后端文件 = sorted((根目录 / "backend/src/main/java").rglob("*.java"))
后端测试 = sorted((根目录 / "backend/src/test").rglob("*.java"))


def 路径(p):
    return p.relative_to(根目录).as_posix()


def 读取(p):
    return p.read_text(encoding="utf-8-sig")


def 写表(文件名, 列名, 行):
    with (目录 / 文件名).open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.writer(f)
        writer.writerow(列名)
        writer.writerows(行)


def 是测试(p):
    return bool(re.search(r"\.(test|spec)\.", p.name)) or "/test/" in 路径(p)


文本 = {p: 读取(p) for p in 前端文件}
引用 = collections.defaultdict(set)
词频 = {}
for p, t in 文本.items():
    词频[p] = collections.Counter(re.findall(r"\b[A-Za-z_$][\w$]*\b", t))
    for word in 词频[p]:
        引用[word].add(p)

# 导入图同时读取静态和动态相对导入；不把框架声明、测试夹具当成业务入口。
导入图 = {p: set() for p in 前端文件}
for p, t in 文本.items():
    for m in re.finditer(r'''(?:from\s*|import\s*\(\s*|import\s*)['"](\.[^'"]+)['"]''', t):
        base = p.parent / m[1]
        for q in [base, Path(str(base) + ".ts"), Path(str(base) + ".vue"), base / "index.ts"]:
            q = Path(os.path.normpath(q))
            if q in 文本:
                导入图[p].add(q)
                break
可达 = set()
待访问 = [源码目录 / "main.ts"]
while 待访问:
    p = 待访问.pop()
    if p not in 可达:
        可达.add(p)
        待访问.extend(导入图.get(p, []))
非测试命名文件 = [p for p in 前端文件 if not re.search(r"\.(test|spec)\.", p.name)]
写表("前端入口可达性.csv", ["文件", "入口可达", "直接消费者"], [
    [路径(p), "是" if p in 可达 else "否", "；".join(路径(q) for q in 前端文件 if p in 导入图[q])]
    for p in 非测试命名文件
])

# 标识符出现不等于真实调用。类内使用、类型引用和测试引用分别展示，由人工复核闭包。
导出候选 = []
局部候选 = []
for p in 前端文件:
    if 是测试(p):
        continue
    t = 文本[p]
    for m in re.finditer(r"export\s+(?:(?:async|declare)\s+)?(function|const|class|interface|type|enum)\s+(\w+)", t):
        名称 = m[2]
        外部 = 引用[名称] - {p}
        if not any(not 是测试(q) for q in 外部):
            导出候选.append([路径(p), t[:m.start()].count("\n") + 1, 名称, m[1], 词频[p][名称],
                             "；".join(路径(q) for q in sorted(外部))])
    for m in re.finditer(r"(?:async\s+)?function\s+(\w+)\s*\(", t):
        if 词频[p][m[1]] == 1 and not re.search(r"export\s+(?:async\s+)?function\s+" + m[1], t):
            局部候选.append([路径(p), t[:m.start()].count("\n") + 1, m[1]])
写表("前端无外部生产引用导出.csv", ["文件", "起始行", "标识符", "声明类型", "同文件词频", "其他测试引用"], 导出候选)
写表("前端局部函数单次出现.csv", ["文件", "起始行", "函数"], 局部候选)

后端文本 = {p: 读取(p) for p in 后端文件 + 后端测试}
后端词频 = {p: collections.Counter(re.findall(r"\b[A-Za-z_$][\w$]*\b", t)) for p, t in 后端文本.items()}
私有候选 = []
for p in 后端文件:
    t = 后端文本[p]
    for m in re.finditer(r"^\s*private\s+(?:static\s+)?(?:synchronized\s+)?[\w<>.,?\[\] ]+\s+(\w+)\s*\(", t, re.M):
        if 后端词频[p][m[1]] == 1:
            私有候选.append([路径(p), t[:t.index("private", m.start(), m.end())].count("\n") + 1, m[1],
                             "；".join(路径(q) for q in 后端测试 if 后端词频[q][m[1]])])
写表("后端私有方法单次出现.csv", ["文件", "定位行", "方法", "测试同名引用"], 私有候选)

# 类型名无显式引用时仍可能由 Spring、Jackson、配置绑定或脚本加载，保留注解线索。
类型候选 = []
for p in 后端文件:
    if not any(q != p and 后端词频[q][p.stem] for q in 后端文件):
        类型候选.append([路径(p), "；".join(re.findall(r"^@(\w+)", 后端文本[p], re.M)),
                         "；".join(路径(q) for q in 后端测试 if 后端词频[q][p.stem])])
写表("后端无显式类型消费者.csv", ["文件", "类级注解", "测试消费者"], 类型候选)

生产文本 = "\n".join(t for p, t in 文本.items() if not 是测试(p))
样式候选 = []
for p in sorted(源码目录.rglob("*.css")):
    t = 读取(p)
    无注释 = re.sub(r"/\*[\s\S]*?\*/", "", t)
    名称 = sorted({n for s in re.findall(r"([^{}]+)\{", 无注释) for n in re.findall(r"\.([_a-zA-Z][\w-]*)", s)})
    for n in 名称:
        if n not in 生产文本:
            样式候选.append([路径(p), n, "动态生成，保留" if n == "tone-gray" else "需核对动态类与组合选择器"])
写表("样式候选.csv", ["文件", "类名", "初步边界"], 样式候选)

摘要 = {
    "基准提交": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=根目录, text=True).strip(),
    "前端非测试命名文件": len(非测试命名文件),
    "前端入口可达文件": len(set(非测试命名文件) & 可达),
    "后端生产Java文件": len(后端文件),
    "后端测试Java文件": len(后端测试),
    "前端无外部生产引用导出候选": len(导出候选),
    "前端局部单次函数": len(局部候选),
    "后端单次私有方法": len(私有候选),
    "样式候选不同类名": len({row[1] for row in 样式候选}),
    "限制": "正则导入图与词频用于生成候选，不能证明反射、动态装配、外部客户端或现场程序已经退出；人工结论见调查报告。",
}
(目录 / "扫描摘要.json").write_text(json.dumps(摘要, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(摘要, ensure_ascii=False, indent=2))
