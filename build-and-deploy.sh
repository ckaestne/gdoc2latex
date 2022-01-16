sbt stage
sudo docker build . -t gdoc
sudo heroku container:push web
sudo heroku container:release web
