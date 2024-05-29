sbt stage
#sudo docker build . -t gdoc
#sudo heroku container:push web
#sudo heroku container:release web

# fly.io is simply, but configure fly.toml file to listen on port 3000 
fly deploy
