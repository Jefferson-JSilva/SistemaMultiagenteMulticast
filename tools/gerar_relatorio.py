"""Render docs/relatorio.md using ReportLab and actual code/test artifacts.

Requirements for report generation only: Python 3, reportlab. Run from any cwd.
Custom directives insert vector diagrams, source excerpts and execution results.
"""
from pathlib import Path
import csv
import re
import textwrap
from html import escape

from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, PageBreak, Table, TableStyle,
    Preformatted, KeepTogether, Flowable,
)
from reportlab.lib import colors
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "output/pdf/relatorio-arquitetura.pdf"
OUT.parent.mkdir(parents=True, exist_ok=True)
FONTS = Path("C:/Windows/Fonts")
if FONTS.exists():
    pdfmetrics.registerFont(TTFont("Body", str(FONTS / "arial.ttf")))
    pdfmetrics.registerFont(TTFont("BodyBold", str(FONTS / "arialbd.ttf")))
    pdfmetrics.registerFont(TTFont("Mono", str(FONTS / "consola.ttf")))
    pdfmetrics.registerFont(TTFont("Symbols", str(FONTS / "ARIALUNI.TTF")))
else:
    # Install DejaVu fonts on POSIX to retain Greek and math glyphs.
    font_root = Path("/usr/share/fonts/truetype/dejavu")
    pdfmetrics.registerFont(TTFont("Body", str(font_root / "DejaVuSans.ttf")))
    pdfmetrics.registerFont(TTFont("BodyBold", str(font_root / "DejaVuSans-Bold.ttf")))
    pdfmetrics.registerFont(TTFont("Mono", str(font_root / "DejaVuSansMono.ttf")))
    pdfmetrics.registerFont(TTFont("Symbols", str(font_root / "DejaVuSans.ttf")))
pdfmetrics.registerFontFamily("Body", normal="Body", bold="BodyBold", italic="Body", boldItalic="BodyBold")
INK = colors.HexColor("#173042")
TEAL = colors.HexColor("#087D83")
LIGHT = colors.HexColor("#ECF5F5")
MUTED = colors.HexColor("#506573")
WIDTH = A4[0] - 100
STYLES = {
    "body": ParagraphStyle("body", fontName="Body", fontSize=9.5, leading=14.2,
                           textColor=INK, spaceAfter=10),
    "h1": ParagraphStyle("h1", fontName="BodyBold", fontSize=22, leading=27,
                         textColor=INK, spaceAfter=17),
    "h2": ParagraphStyle("h2", fontName="BodyBold", fontSize=13, leading=18,
                         textColor=TEAL, spaceAfter=14),
    "small": ParagraphStyle("small", fontName="Body", fontSize=8, leading=11,
                            textColor=MUTED, spaceAfter=7),
    "cell": ParagraphStyle("cell", fontName="Body", fontSize=8.2, leading=11, textColor=INK),
    "head": ParagraphStyle("head", fontName="BodyBold", fontSize=8.2, leading=11, textColor=colors.white),
}


def rich(text):
    text = escape(text)
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = text.replace("⊥", '<font name="Symbols">⊥</font>')
    return text


def para(text, style="body"):
    return Paragraph(rich(text), STYLES[style])


def table(rows, widths=None, font=8.2):
    style = ParagraphStyle("table-custom", parent=STYLES["cell"], fontSize=font, leading=font+3)
    cells = [[Paragraph(rich(str(value)), STYLES["head"] if i == 0 else style)
              for value in row] for i, row in enumerate(rows)]
    if widths is None:
        if len(rows[0]) == 2:
            widths = [WIDTH * .29, WIDTH * .71]
        elif len(rows[0]) == 3:
            widths = [WIDTH * .35, WIDTH * .22, WIDTH * .43]
        else:
            widths = [WIDTH / len(rows[0])] * len(rows[0])
    result = Table(cells, colWidths=widths, hAlign="LEFT", repeatRows=1)
    result.setStyle(TableStyle([
        ("BACKGROUND", (0,0), (-1,0), TEAL),
        ("ROWBACKGROUNDS", (0,1), (-1,-1), [colors.white, LIGHT]),
        ("VALIGN", (0,0), (-1,-1), "TOP"),
        ("LEFTPADDING", (0,0), (-1,-1), 8),
        ("RIGHTPADDING", (0,0), (-1,-1), 8),
        ("TOPPADDING", (0,0), (-1,-1), 6),
        ("BOTTOMPADDING", (0,0), (-1,-1), 6),
        ("LINEBELOW", (0,-1), (-1,-1), .5, colors.HexColor("#CADBDC")),
    ]))
    return [result, Spacer(1,12)]


def code_block(text):
    lines = []
    for line in text.splitlines():
        lines.extend(textwrap.wrap(line, width=114, subsequent_indent="    ",
                                   replace_whitespace=False, drop_whitespace=False) or [""])
    style = ParagraphStyle("code", fontName="Mono", fontSize=7.0, leading=9.5,
                           backColor=colors.HexColor("#F1F4F6"), borderPadding=8,
                           textColor=INK, spaceBefore=5, spaceAfter=12)
    return Preformatted("\n".join(lines), style)


class Diagram(Flowable):
    def __init__(self, kind):
        super().__init__()
        self.kind = kind
        self.width = WIDTH
        self.height = 244 if kind == "architecture" else 172

    def box(self, x, y, w, h, title, lines=(), fill=LIGHT):
        c = self.canv
        c.setFillColor(fill); c.setStrokeColor(TEAL)
        c.roundRect(x,y,w,h,8,fill=1,stroke=1)
        c.setFillColor(INK); c.setFont("BodyBold",10)
        c.drawCentredString(x+w/2,y+h-20,title)
        c.setFont("Body",9)
        for n,line in enumerate(lines):
            c.drawCentredString(x+w/2,y+h-39-n*15,line)

    def arrow(self, x1,y1,x2,y2, color=TEAL):
        import math
        c=self.canv; c.setStrokeColor(color); c.setLineWidth(1.3)
        c.line(x1,y1,x2,y2)
        angle=math.atan2(y2-y1,x2-x1)
        for off in [-.5,.5]:
            c.line(x2,y2,x2-6*math.cos(angle+off),y2-6*math.sin(angle+off))

    def draw(self):
        c = self.canv
        if self.kind == "architecture":
            w=(WIDTH-24)/3
            self.box(0,150,w,84,"Máquina A",["Coordenador","Coleta e consolidação"])
            self.box(w+12,150,w,84,"Máquina B",["Tabagismo","Sedentarismo"])
            self.box(2*(w+12),150,w,84,"Máquina C",["Pressão arterial","IMC"])
            self.box(18,31,WIDTH-36,72,"Grupo multicast",["239.0.0.G : (5000 + G)  |  TTL = 1",
                                                       "REQ, RESP e ERRO passam pelo grupo"])
            for x in [w/2,w*1.5+12,w*2.5+24]:
                self.arrow(x-4,150,x-4,103)
                self.arrow(x+4,103,x+4,150)
            c.setFont("Body",8); c.setFillColor(MUTED)
            c.drawString(0,12,"Proposta de implantação. Cada agente é uma JVM independente.")
        else:
            x=[65,246,430]
            for xx,label in zip(x,["Coordenador","Grupo multicast","Quatro agentes"]):
                c.setFont("BodyBold",9); c.setFillColor(INK); c.drawCentredString(xx,156,label)
                c.setStrokeColor(colors.HexColor("#BACDCE")); c.setDash(2,3); c.line(xx,14,xx,145); c.setDash()
            self.arrow(x[0],130,x[1],130); self.arrow(x[1],116,x[2],116)
            self.arrow(x[2],81,x[1],81); self.arrow(x[1],67,x[0],67)
            c.setFont("Body",8); c.setFillColor(INK)
            c.drawString(83,135,"4 × REQ, mesmo UUID")
            c.drawString(260,121,"Filtrar e calcular em pool")
            c.drawString(260,86,"RESP ou ERRO por fator")
            c.drawString(83,72,"Até 4 respostas / timeout")
            c.setFont("BodyBold",8); c.drawString(16,32,"LPA2v somente se completa")


def source_excerpt(directive):
    path, start, end = directive.split("|",2)
    lines = (ROOT/path).read_text(encoding="utf-8").splitlines()
    a = next(i for i,line in enumerate(lines) if start in line)
    b = next(i for i in range(a,len(lines)) if end in lines[i])
    chosen = lines[a:b+1]
    if len(chosen)>25:
        raise ValueError(f"Excerpt exceeds 25 lines: {path}:{a+1}-{b+1}")
    snippet = textwrap.dedent("\n".join(chosen))
    return [para(f"{path}:{a+1}-{b+1}","small"),code_block(snippet)]


RUN = ROOT / (ROOT/"evidencias/local/ultimo-teste.txt").read_text(encoding="utf-8").strip().replace("\\", "/")
NORMAL = sorted(RUN.glob("*-Coordenador-console.log"))[0]
NORMAL_TEXT = NORMAL.read_text(encoding="utf-8")
CALCULATIONS = list(csv.DictReader((ROOT/"evidencias/local/calculos.csv").open(encoding="utf-8")))


def directive(name):
    if name.startswith("@code "):
        return source_excerpt(name[6:])
    if name in ("@architecture","@sequence"):
        return [Diagram(name[1:]),Spacer(1,8)]
    if name=="@summary":
        return table([
            ["Entrega preparada","Verificação disponível"],
            ["Projeto Java completo","5 processos, multicast exclusivo, sem frameworks"],
            ["Cálculos e validação","210 verificações unitárias aprovadas"],
            ["Comunicação real local","6 casos e cenários de falha/concorrência aprovados"],
            ["Demonstração distribuída","Pendente: laboratório, três máquinas e capturas reais"],
        ])
    if name=="@results":
        expected = [
            ["0.000","0.000","0.000","0.000","-1.000","0.000"],
            ["1.000","1.000","1.000","1.000","1.000","0.000"],
            ["0.615","0.778","0.500","0.336","0.115","0.279"],
            ["1.000","1.000","0.000","0.000","0.000","1.000"],
            ["0.000","0.000","1.000","1.000","0.000","-1.000"],
            ["0.308","0.333","0.250","0.179","-0.465","0.106"],
        ]
        rows1=[["Caso","μT E / O","μS E / O","μPA E / O","μIMC E / O"]]
        rows2=[["Caso","Gc E / O","Gct E / O","Estado obtido"]]
        result_lines=[line for line in NORMAL_TEXT.splitlines() if " RESULTADO " in line]
        for i,line in enumerate(result_lines):
            evaluation=re.search(r"id=([\w-]+)",line).group(1)
            observed={}
            for ev in NORMAL_TEXT.splitlines():
                if f" EVIDENCIA {evaluation} " in ev:
                    m=re.search(r" (TABAGISMO|SEDENTARISMO|PRESSAO|IMC) mu=([\d.]+)",ev)
                    observed[m.group(1)]=m.group(2)
            values=[observed[f] for f in ["TABAGISMO","SEDENTARISMO","PRESSAO","IMC"]]
            rows1.append([str(i+1)]+[f"{e} / {o}" for e,o in zip(expected[i][:4],values)])
            gc=re.search(r" Gc=([-\d.]+)",line).group(1)
            gct=re.search(r" Gct=([-\d.]+)",line).group(1)
            state=re.search(r"estado=(\w+)",line).group(1).replace("NAO_EXTREMO","Não extremo").replace("PARACOMPLETO","Paracompleto")
            rows2.append([str(i+1),expected[i][4]+" / "+gc,expected[i][5]+" / "+gct,state])
        return (table(rows1,[42]+[(WIDTH-42)/4]*4,7.5)+
                table(rows2,[42,122,122,WIDTH-286],8))
    if name=="@test-summary":
        lines=(RUN/"resumo.txt").read_text(encoding="utf-8").splitlines()
        passing=[line for line in lines if " PASS " in line]
        if len(passing)!=13:
            raise ValueError("Expected 13 successful integration entries")
        rows=[["Cenário","Resultado observado"],
              ["Seis casos normais","6/6 aprovados; valores dentro de 0,001"],
              ["Agente ausente","IMC não respondeu; timeout de 3000 ms; incompleta"],
              ["Reingresso","IMC reiniciou; caso 3 voltou a consolidar"],
              ["Dois coordenadores","Casos 1 e 2 separados por UUID e destino"],
              ["Tabagismo duplicado","Segunda resposta descartada e registrada"],
              ["Altura zero","ERRO do IMC; sem consolidação"],
              ["Pool e saída limpa","Tarefas concorrentes; sair gerou LEAVE/CLOSE"]]
        return [para(lines[1],"small")]+table(rows,font=7.7)
    if name=="@log-excerpts":
        excerpts=[]
        uuid_pattern=r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
        for fragment in [" AUSENTE IMC"," DESCARTADA_DUPLICADA "," ERRO_AGENTE IMC"]:
            found=False
            for path in sorted(RUN.glob("*-Coordenador-console.log")):
                for line in path.read_text(encoding="utf-8").splitlines():
                    if fragment in line:
                        line=re.sub(uuid_pattern,"<uuid>",line)
                        line=re.sub(r" \[COORD-<uuid>\] \[[^]]+\]", " [coordenador]",line)
                        excerpts.append(line)
                        found=True; break
                if found: break
        return [code_block("\n".join(excerpts))]
    if name=="@case6":
        t,s,pa,bmi=4/13,3/9,.25,(80/(1.7**2)-25)/15
        def node(a,b):
            lam=1-b; gc=a-lam; gct=a+lam-1
            return [a,lam,gc,gct,(gc+1)/2]
        n1,n2=node(t,s),node(pa,bmi)
        n3=node(n1[-1],n2[-1])
        rows=[["Nó","μ","λ","Gc","Gct","μE"]]
        for label,n in zip(["NAP1","NAP2","NAP3"],[n1,n2,n3]):
            rows.append([label]+[f"{v:.6f}".replace(".",",") for v in n])
        return table(rows,[45]+[(WIDTH-45)/5]*5,8)
    raise ValueError("Unknown directive: "+name)


class NumberedCanvas(canvas.Canvas):
    def __init__(self,*args,**kwargs):
        super().__init__(*args,**kwargs)
        self.states=[]
    def showPage(self):
        self.states.append(dict(self.__dict__))
        self._startPage()
    def save(self):
        total=len(self.states)
        for state in self.states:
            self.__dict__.update(state)
            self.draw_page_number(total)
            super().showPage()
        super().save()
    def draw_page_number(self,total):
        self.setFillColor(TEAL)
        self.rect(0,A4[1]-12,A4[0],12,fill=1,stroke=0)
        self.setFont("BodyBold",8); self.setFillColor(MUTED)
        self.drawString(50,A4[1]-37,"SISTEMAS DISTRIBUÍDOS  /  MULTICAST + LPA2v")
        self.setStrokeColor(colors.HexColor("#C7D8DB")); self.line(50,43,A4[0]-50,43)
        self.setFont("Body",8)
        self.drawString(50,29,"Relatório didático | Evidências locais e pendências de laboratório")
        self.drawRightString(A4[0]-50,29,f"{self._pageNumber} / {total}")


def build():
    content=(ROOT/"docs/relatorio.md").read_text(encoding="utf-8")
    story=[]
    pages=content.split("<!-- page -->")
    for page_no,page in enumerate(pages):
        if page_no: story.append(PageBreak())
        lines=page.strip().splitlines()
        i=0
        while i<len(lines):
            line=lines[i].strip()
            if not line: i+=1; continue
            if line.startswith("@"):
                story.extend(directive(line)); i+=1
            elif line.startswith("```"):
                i+=1; block=[]
                while i<len(lines) and not lines[i].strip().startswith("```"):
                    block.append(lines[i]); i+=1
                i+=1; story.append(code_block("\n".join(block)))
            elif line.startswith("|"):
                rows=[]
                while i<len(lines) and lines[i].strip().startswith("|"):
                    rows.append([x.strip() for x in lines[i].strip().strip("|").split("|")]); i+=1
                story.extend(table(rows))
            elif line.startswith("## "):
                story.append(para(line[3:],"h2")); i+=1
            elif line.startswith("# "):
                if page_no==0: story.append(Spacer(1,26))
                story.append(para(line[2:],"h1")); i+=1
            else:
                paragraph=[line]; i+=1
                while i<len(lines) and lines[i].strip() and not lines[i].startswith(("@","#","|","```")):
                    paragraph.append(lines[i].strip()); i+=1
                story.append(para(" ".join(paragraph)))
    doc=SimpleDocTemplate(str(OUT),pagesize=A4,leftMargin=50,rightMargin=50,topMargin=62,
                          bottomMargin=57,title="Sistema multiagente multicast para risco cardiológico",
                          author="Projeto didático - identificação do grupo pendente",
                          subject="Arquitetura Java SE, LPA2v e evidências reais de teste local")
    doc.build(story,canvasmaker=NumberedCanvas)
    print(OUT)
    print(f"Seções planejadas: {len(pages)} páginas. Verifique o PDF renderizado.")


if __name__=="__main__":
    build()
