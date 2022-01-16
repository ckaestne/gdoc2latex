#Grab the latest alpine image
FROM ubuntu:latest

RUN apt-get update \
    && apt-get upgrade -y \
    && DEBIAN_FRONTEND=noninteractive TZ=US/Eastern apt-get install texlive-full texlive-latex-extra texlive-fonts-recommended xzdec -y 

RUN pdflatex --version

RUN apt-get install -y openjdk-17-jre-headless

RUN apt-get install -y latexmk

RUN java --version

# securing latex
ADD ./config/texmf.cnf /etc/texmf/texmf.d/my.cnf
RUN update-texmf

# Add our code
ADD ./target/universal/stage /opt/webapp/
ADD ./credentials /opt/webapp/credentials/
WORKDIR /opt/webapp

# Expose is NOT supported by Heroku
EXPOSE 3000 		

# Run the image as a non-root user

# Run the app.  CMD is required to run on Heroku
# $PORT is set by Heroku			
CMD bin/gdoc2latex $PORT

