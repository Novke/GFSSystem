# Render servis gfs-latex (spec banke 3.1): LaTeX -> PDF (pdflatex) -> SVG i PNG (pdftocairo), HTTP server iz Python stdlib-a.
# Bazna slika sa AWS ogledala zvaničnih Docker slika (ista kao na Docker Hub-u): anonimni pull sa Docker Hub-a deli
# limit po IP adresi GitHub runnera i ume da obori CI pre build-a.
FROM public.ecr.aws/docker/library/debian:bookworm-slim

# TeX Live iz Debiana bez preporučenih paketa (bez JRE, Ruby, Tk). cm-super: Type1 EC fontovi u svim veličinama za
# \usepackage[T1]{fontenc} iz podrazumevane preambule (11pt traži ecrm1095; cm-super-minimal ima samo 10pt), jer
# server isključuje mktexpk i bitmap fontove.
RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        texlive-latex-base \
        texlive-latex-recommended \
        texlive-latex-extra \
        texlive-pictures \
        texlive-fonts-recommended \
        texlive-lang-european \
        texlive-science \
        cm-super \
        poppler-utils \
        python3 \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -g 1000 render && useradd -u 1000 -g render -M -d /nonexistent -s /usr/sbin/nologin render
WORKDIR /srv
COPY server.py /srv/
USER render

ENV RENDER_PORT=8800 \
    RENDER_MAX_TIMEOUT=60 \
    RENDER_MAX_OUTPUT=5242880 \
    RENDER_MAX_BODY=62914560
EXPOSE 8800
HEALTHCHECK --interval=30s --timeout=3s --start-period=10s \
    CMD python3 -I -c "import os,sys,urllib.request; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:%s/zdravlje' % os.environ.get('RENDER_PORT', '8800'), timeout=2).status == 200 else 1)"
CMD ["python3", "-I", "/srv/server.py"]
