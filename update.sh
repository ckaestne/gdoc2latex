sbt "run paper/main.tex paper/header.tex"
cd paper
latexmk --pdf icse2022.tex
cd ..
