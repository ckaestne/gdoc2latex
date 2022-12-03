FROM eclipse-temurin:17-jdk

RUN apt-get update \
    && apt-get upgrade -y \
    && DEBIAN_FRONTEND=noninteractive TZ=US/Eastern apt-get install texlive-full texlive-latex-extra texlive-fonts-recommended xzdec -y 

# securing latex
ADD ./config/texmf.cnf /etc/texmf/texmf.d/my.cnf
RUN update-texmf

# installing sbt
RUN wget https://github.com/sbt/sbt/releases/download/v1.8.0/sbt-1.8.0.tgz
RUN tar -zxvf sbt-1.8.0.tgz



RUN pdflatex --version
RUN java --version


# Adding source and credentials
ADD . /opt/webapp/
ADD ./api.json /opt/webapp/credentials/
WORKDIR /opt/webapp


# compiling the project
RUN /sbt/bin/sbt package


# Expose is NOT supported by Heroku
EXPOSE 3000 		

# Run the image as a non-root user

# Run the app.  CMD is required to run on Heroku
# $PORT is set by Heroku			
CMD target/universal/stage/bin/server $PORT

