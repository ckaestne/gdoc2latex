sbt "run paper/main.tex"
cd paper
latexmk --pdf icse2022.tex
cd ..
