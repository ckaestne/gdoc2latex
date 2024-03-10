FROM texlive/texlive:latest


RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive TZ=US/Eastern apt-get install tex-common -y 

# securing latex
ADD ../../mlip-book/config/texmf.cnf /etc/texmf/texmf.d/my.cnf
RUN update-texmf

# installing java and sbt
RUN apt-get install -y openjdk-17-jre-headless


RUN pdflatex --version
RUN java --version


# Adding source and credentials
ADD ./target/universal/stage /opt/webapp/
ADD ./credentials/api.json /opt/webapp/credentials/
WORKDIR /opt/webapp




# Expose is NOT supported by Heroku
EXPOSE 3000 		

# Run the image as a non-root user

ENTRYPOINT ["/opt/webapp/bin/server", "3000"]

